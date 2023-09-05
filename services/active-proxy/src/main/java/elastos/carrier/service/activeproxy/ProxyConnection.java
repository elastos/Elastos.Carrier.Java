/*
 * Copyright (c) 2022 - 2023 trinity-tech.io
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package elastos.carrier.service.activeproxy;

import java.net.InetAddress;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.net.NetSocket;
import io.vertx.core.net.SocketAddress;
import io.vertx.core.net.impl.SocketAddressImpl;

import elastos.carrier.CarrierException;
import elastos.carrier.Id;
import elastos.carrier.crypto.CryptoBox;
import elastos.carrier.crypto.CryptoBox.Nonce;
import elastos.carrier.crypto.CryptoBox.PublicKey;
import elastos.carrier.crypto.CryptoException;
import elastos.carrier.crypto.Signature;
import elastos.carrier.utils.ThreadLocals;

public class ProxyConnection implements AutoCloseable {
	public static final Object OBJECT = new Object();
	static final int PACKET_HEADER_BYTES = Short.BYTES + Byte.BYTES;
	private static AtomicInteger NEXT_ID = new AtomicInteger(0);

	private long id;
	private String name;

	private ProxyServer server;
	private ProxySession session;
	private byte[] challenge;
	private Nonce nonce;

	private NetSocket upstreamSocket;
	private NetSocket clientSocket;

	private Promise<Void> closePromise;

	private Handler<Void> clientCloseHandler;

	private Buffer stickyBuffer;

	private /* volatile */ State state;
	private AtomicInteger disconnectConfirms;

	private static final Logger log = LoggerFactory.getLogger(ProxyConnection.class);

	public enum State {
		Authenticating {
			@Override
			public boolean accept(PacketType type) {
				return type == PacketType.AUTH || type == PacketType.ATTACH;
			}
		},
		Idling {
			@Override
			public boolean accept(PacketType type) {
				return type == PacketType.PING;
			}
		},
		Connecting {
			@Override
			public boolean accept(PacketType type) {
				return type == PacketType.CONNECT_ACK || type == PacketType.PING;
			}
		},
		Relaying {
			@Override
			public boolean accept(PacketType type) {
				return type == PacketType.DATA || type == PacketType.DISCONNECT;
			}
		},
		Disconnecting {
			@Override
			public boolean accept(PacketType type) {
				return type == PacketType.DISCONNECT || type == PacketType.DISCONNECT_ACK ||
						type == PacketType.DATA;
			}
		},
		Closed {
			@Override
			public boolean accept(PacketType type) {
				return false;
			}
		};

		public abstract boolean accept(PacketType type);
	}

	public ProxyConnection(ProxyServer server, NetSocket upstreamSocket) {
		this.id = Integer.toUnsignedLong(NEXT_ID.getAndIncrement());
		this.name = Long.toString(id);

		this.server = server;
		this.upstreamSocket = upstreamSocket;

		this.closePromise = Promise.promise();

		upstreamSocket.closeHandler(v -> close());
		upstreamSocket.exceptionHandler(v -> close());
		upstreamSocket.handler(this::upstreamHandler);

		this.state = State.Authenticating;
		this.disconnectConfirms = new AtomicInteger(0);
	}

	public long getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	protected void setSession(ProxySession session) {
		this.session = session;
		this.name = Long.toString(id) + "@" + session.getName();
	}

	public SocketAddress upstreamAddress() {
		return upstreamSocket.remoteAddress();
	}

	private int paddingSize() {
		return ThreadLocals.random().nextInt(0, 256);
	}


	private byte[] randomPadding() {
		byte[] padding = new byte[paddingSize()];
		ThreadLocals.random().nextBytes(padding);
		return padding;
	}

	private void shortToNetwork(int num, byte[] dest, int pos) {
		dest[pos] = (byte) ((num & 0x0000ff00) >>> 8);
		dest[pos + 1] = (byte) (num & 0x000000ff);
	}

	/*
	 * Challenge packet
	 * - plain
	 *   - Random challenge bytes
	 */
	void sendChallenge(Handler<AsyncResult<Void>> handler) {
		log.trace("Connection {} sending challenge to {}", getName(), upstreamSocket.remoteAddress());

		challenge = new byte[ThreadLocals.random().nextInt(32, 256)];
		ThreadLocals.random().nextBytes(challenge);

		int size = Short.BYTES + challenge.length;
		Buffer buf = Buffer.buffer(size);
		buf.appendUnsignedShort(size);
		buf.appendBytes(challenge);

		upstreamSocket.write(buf, handler);
	}

	/*
	 * AUTHACK packet payload:
	 * - encrypted
	 *   - sessionPk[server]
	 *   - port[uint16]
	 *   - domainEnabled[uint8]
	 */
	void sendAuthAck(Id clientNodeId, PublicKey sessionPk, int port, boolean domainEnabled,
			Handler<AsyncResult<Void>> handler) {
		log.trace("Connection {} sending AUTH ACK to {}@{}",
				getName(), clientNodeId, upstreamSocket.remoteAddress());


		// Vert.x Buffer or ByteBuffer both are too heavy,
		// so we just use the plain byte array
		byte[] payload = new byte[PublicKey.BYTES + Short.BYTES + Byte.BYTES];

		int pos = 0;
		System.arraycopy(sessionPk.bytes(), 0, payload, pos, PublicKey.BYTES);

		pos += PublicKey.BYTES;
		shortToNetwork(port, payload, pos);

		pos += Short.BYTES;
		payload[pos] = (byte)(domainEnabled ? 1 : 0);

		byte[] cipher = null;
		try {
			cipher = server.encrypt(clientNodeId, payload);
		} catch (CarrierException e) {
			log.error("Send AUTH ACK to " + clientNodeId + "@" + upstreamSocket.remoteAddress() +
					" failed - encrypt error", e);
			handler.handle(Future.failedFuture(e));
			return;
		}

		byte[] padding = randomPadding();
		int size = PACKET_HEADER_BYTES + cipher.length + padding.length;

		Buffer buf = Buffer.buffer(size);
		buf.appendUnsignedShort(size);
		buf.appendByte(PacketType.AUTH_ACK.value());
		buf.appendBytes(cipher);
		buf.appendBytes(padding);

		state = State.Idling;
		upstreamSocket.write(buf, handler);
	}

	private void sendPacket(PacketType type, byte[] payload, Handler<AsyncResult<Void>> handler) {
		if (state == State.Closed) {
			log.warn("Connection {} already closed, but try to send {} to upstream", getName(), type);
			return;
		}

		log.trace("Connection {} sending {} to {}", getName(), type, upstreamSocket.remoteAddress());

		int size = PACKET_HEADER_BYTES;

		byte[] cipher = null;
		if (payload != null && payload.length > 0) {
			try {
				cipher = session.encrypt(payload, nonce);
				size += cipher.length;
			} catch (CarrierException e) {
				log.error("SHOULD NEVER HAPPEN: connection {} encrypt packet failed", getName());
				if (handler != null)
					handler.handle(Future.failedFuture(e));
				return;
			}
		}

		byte[] padding = null;
		if (type != PacketType.DATA && type != PacketType.ERROR) {
			padding = randomPadding();
			size += padding.length;
		}

		// header
		Buffer buf = Buffer.buffer(size);
		buf.appendUnsignedShort(size);
		buf.appendByte(type.value());

		// payload
		if (cipher != null)
			buf.appendBytes(cipher);

		// padding
		if (padding != null)
			buf.appendBytes(padding);

		upstreamSocket.write(buf, handler);
	}

	/*
	 * No payload
	 */
	void sendAttachAck(Handler<AsyncResult<Void>> handler) {
		state = State.Idling;
		sendPacket(PacketType.ATTACH_ACK, null, handler);
	}

	/*
	 * No payload
	 */
	private void sendPingAck() {
		sendPacket(PacketType.PING_ACK, null, ar -> {
			if (ar.failed())
				close();
		});
	}

	/*
	 * CONNECT packet payload:
	 * - encrypted
	 *   - addrlen[uint8]
	 *   - addr[16 bytes both for IPv4 or IPv6]
	 *   - port[uint16]
	 */
	private void sendConnect(InetAddress address, int port) {
		byte[] addr = address.getAddress();

		byte[] payload = new byte[1 + 16 + 2];

		int pos = 0;
		payload[pos++] = (byte)(addr.length);
		System.arraycopy(addr, 0, payload, pos, addr.length);
		pos += 16;
		shortToNetwork(port, payload, pos);

		sendPacket(PacketType.CONNECT, payload, ar -> {
			if (ar.failed())
				close();
		});
	}

	/*
	 * No payload
	 */
	private void sendDisconnect() {
		sendPacket(PacketType.DISCONNECT, null, ar -> {
			if (ar.failed())
				close();
		});
	}

	/*
	 * No payload
	 */
	private void sendDisconnectAck() {
		sendPacket(PacketType.DISCONNECT_ACK, null, ar -> {
			if (ar.failed())
				close();
		});
	}

	/*
	 * DATA packet payload:
	 * - encrypted
	 *   - data
	 */
	private void sendData(byte[] data) {
		sendPacket(PacketType.DATA, data, ar -> {
			if (ar.failed())
				close();
		});

		// Flow control for client -> upstream
		if (upstreamSocket.writeQueueFull()) {
			log.trace("Upstream write queue full, pause client reading");
			clientSocket.pause();
			upstreamSocket.drainHandler(v -> clientSocket.resume());
		}
	}

	@SuppressWarnings("unused")
	private void sendError(short code, String message) {
		byte[] msg = message.getBytes();
		byte[] payload = new byte[3 + msg.length];

		int pos = 0;
		shortToNetwork(code, payload, pos);
		pos += Short.BYTES;
		System.arraycopy(msg, 0, payload, pos, msg.length);
		pos += msg.length;
		payload[pos] = 0;

		sendPacket(PacketType.ERROR, payload, ar -> {
			if (ar.failed())
				close();
		});
	}

	private void upstreamHandler(Buffer data) {
		log.trace("Connection {} got {} bytes data from upstream {}", getName(), data.length(), upstreamSocket.remoteAddress());

		int pos = 0;
		int remaining = data.length();

		if (stickyBuffer != null) {
			if (stickyBuffer.length() < PACKET_HEADER_BYTES) {
				int rs = PACKET_HEADER_BYTES - stickyBuffer.length();
				if (remaining < rs) {
					stickyBuffer.appendBuffer(data, pos, remaining);
					return;
				}

				stickyBuffer.appendBuffer(data, pos, rs);
				pos += rs;
				remaining -= rs;
			}

			int packetSize = stickyBuffer.getUnsignedShort(0);
			int rs = packetSize - stickyBuffer.length();
			if (remaining < rs) {
				stickyBuffer.appendBuffer(data, pos, remaining);
				return;
			}

			stickyBuffer.appendBuffer(data, pos, rs);
			pos += rs;
			remaining -= rs;

			handlePacket(stickyBuffer);
			stickyBuffer = null;
		}

		while (remaining > 0) {
			if (remaining < PACKET_HEADER_BYTES) {
				stickyBuffer = Buffer.buffer();
				stickyBuffer.appendBuffer(data, pos, remaining);
				return;
			}

			int packetSize = data.getUnsignedShort(pos);
			if (remaining < packetSize) {
				stickyBuffer = Buffer.buffer(packetSize);
				stickyBuffer.appendBuffer(data, pos, remaining);
				return;
			}

			handlePacket(data.slice(pos, pos + packetSize));
			pos += packetSize;
			remaining -= packetSize;
		}
	}

	private void handlePacket(Buffer packet) {
		int size = packet.getUnsignedShort(0);
		if (size != packet.length()) {
			log.error("Connection {} in illegal state!!!", getName());
			close();
			return;
		}

		PacketType type = PacketType.valueOf(packet.getByte(Short.BYTES));

		log.trace("Connection {} got {} packet({} bytes) from {}",
				getName(), type, size, upstreamSocket.remoteAddress());

		if (!state.accept(type)) {
			log.error("Connection {} got wrong {} packet in {} state", getName(), type, state);
			close();
			return;
		}

		switch (type) {
		case AUTH:
			handleAuth(packet);
			return;

		case ATTACH:
			handleAttach(packet);
			return;

		case PING:
			handleKeepAlive(packet);
			return;

		case CONNECT_ACK:
			handleConnectAck(packet);
			return;

		case DATA:
			handleData(packet);
			return;

		case DISCONNECT:
			handleDisconnect(packet);
			return;

		case DISCONNECT_ACK:
			handleDisconnectAck(packet);
			return;

		default:
			log.error("INTERNAL ERROR: Connection {} got wrong {} packet in {} state", getName(), type, state);
		}
	}

	/*
	 * AUTH packet payload:
	 *   - plain
	 *     - clientNodeId
	 *   - encrypted
	 *     - sessionPk[client]
	 *     - connectionNonce
	 *     - signature[challenge]
	 *     - domain length[uint8]
	 *     - domain[optional]
	 */
	private void handleAuth(Buffer packet) {
		int size = PACKET_HEADER_BYTES + Id.BYTES + CryptoBox.MAC_BYTES +
				CryptoBox.PublicKey.BYTES + CryptoBox.Nonce.BYTES + Signature.BYTES + Byte.BYTES;
		if (packet.length() < size) {
			log.error("Connection {} got invalid auth packet from {}.",
					getName(), upstreamSocket.remoteAddress());
			close();
			return;
		}

		log.trace("Connection {} got authentication packet from {}.",
				getName(), upstreamSocket.remoteAddress());

		int pos = PACKET_HEADER_BYTES;
		Id nodeId = Id.of(packet.getBytes(pos, pos + Id.BYTES));

		pos += Id.BYTES;
		byte[] payload = null;
		try {
			byte[] cipher = packet.getBytes(pos, packet.length());
			payload = server.decrypt(nodeId, cipher);
		} catch (CarrierException e) {
			log.error("Connection {} decrypt the auth packet failed.", getName());
			close();
			return;
		}

		pos = 0;
		CryptoBox.PublicKey clientPk = CryptoBox.PublicKey.fromBytes(
				Arrays.copyOfRange(payload, pos, pos + CryptoBox.PublicKey.BYTES));
		pos += CryptoBox.PublicKey.BYTES;

		nonce = Nonce.fromBytes(Arrays.copyOfRange(payload, pos, pos + CryptoBox.Nonce.BYTES));
		pos += CryptoBox.Nonce.BYTES;
		byte[] sig = Arrays.copyOfRange(payload, pos, pos + Signature.BYTES);
		if (!Signature.verify(challenge, sig, Signature.PublicKey.fromBytes(nodeId.bytes()))) {
			log.error("Connection {} verify the auth challenge signature failed.", getName());
			close();
			return;
		}

		// clear the random challenge
		this.challenge = null;

		pos += Signature.BYTES;
		int domainLength = Byte.toUnsignedInt(payload[pos++]);
		String domain = null;
		if (domainLength != 0) {
			if (payload.length-pos < domainLength) {
				log.error("Connection {} invalid auth packet", getName());
				close();
				return;

			}

			domain = new String(payload, pos, domainLength);
		}

		server.authenticate(this, nodeId, clientPk, domain);
	}

	/*
	 * ATTACH packet:
	 *   - plain
	 *     - clientNodeId
	 *   - encrypted
	 *     - sessionPk[client]
	 *     - connectionNonce
	 *     - signature[challenge]
	 */
	private void handleAttach(Buffer packet) {
		int size = PACKET_HEADER_BYTES + Id.BYTES + CryptoBox.MAC_BYTES +
				CryptoBox.PublicKey.BYTES + CryptoBox.Nonce.BYTES + Signature.BYTES;
		if (packet.length() < size) {
			log.error("Connection {} got invalid attach packet from {}.",
					getName(), upstreamSocket.remoteAddress());
			close();
			return;
		}

		log.trace("Connection {} got attach packet from {}.",
				getName(), upstreamSocket.remoteAddress());

		int pos = PACKET_HEADER_BYTES;
		Id nodeId = Id.of(packet.getBytes(pos, pos + Id.BYTES));

		pos += Id.BYTES;
		byte[] payload = null;
		try {
			byte[] cipher = packet.getBytes(pos, size);
			payload = server.decrypt(nodeId, cipher);
		} catch (CarrierException e) {
			log.error("Connection {} decrypt the attach packet failed.", getName());
			close();
			return;
		}

		pos = 0;
		CryptoBox.PublicKey clientPk = CryptoBox.PublicKey.fromBytes(
				Arrays.copyOfRange(payload, pos, pos + CryptoBox.PublicKey.BYTES));
		pos += CryptoBox.PublicKey.BYTES;

		nonce = Nonce.fromBytes(Arrays.copyOfRange(payload, pos, pos + CryptoBox.Nonce.BYTES));
		pos += CryptoBox.Nonce.BYTES;
		byte[] sig = Arrays.copyOfRange(payload, pos, pos + Signature.BYTES);
		if (!Signature.verify(challenge, sig, Signature.PublicKey.fromBytes(nodeId.bytes()))) {
			log.error("Connection {} verify the attach challenge signature failed.", getName());
			close();
			return;
		}

		// clear the random challenge
		this.challenge = null;

		server.attach(this, nodeId, clientPk);
	}

	/*
	 * No payload
	 */
	private void handleKeepAlive(Buffer packet) {
		log.trace("Connection {} got PING packet from {}.",
				getName(), upstreamSocket.remoteAddress());

		sendPingAck();
	}

	/*
	 * CONNECTACK packet payload:
	 * - plain
	 *   - success[uint8]
	 */
	private void handleConnectAck(Buffer packet) {
		int size = PACKET_HEADER_BYTES + Byte.BYTES;
		if (packet.length() < size) {
			log.error("Connection {} got invalid CONNECT ACK packet from {}.",
					getName(), upstreamSocket.remoteAddress());
			close();
			return;
		}

		boolean success = (packet.getByte(PACKET_HEADER_BYTES) & 0x01) != 0;

		log.trace("Connection {} got CONNECT ACK({}) packet from {}.",
				getName(), success, upstreamSocket.remoteAddress());

		if (success) {
			log.debug("Connection {} upstream connected", getName());
			upstreamConnected();
		} else {
			log.debug("Connection {} upstream rejected.", getName());
			disconnectClient();
		}
	}

	/*
	 * No payload
	 */
	private void handleDisconnect(Buffer packet) {
		log.trace("Connection {} got DISCONNECT packet from {}.",
				getName(), upstreamSocket.remoteAddress());

		disconnectClient();

		if (disconnectConfirms.incrementAndGet() == 2) {
			state = State.Idling;
			disconnectConfirms.set(0);

			if (clientCloseHandler != null)
				clientCloseHandler.handle(null);
		}

		sendDisconnectAck();
	}

	/*
	 * No payload
	 */
	private void handleDisconnectAck(Buffer packet) {
		log.trace("Connection {} got DISCONNECT ACK packet from {}.",
				getName(), upstreamSocket.remoteAddress());

		if (disconnectConfirms.incrementAndGet() == 2) {
			state = State.Idling;
			disconnectConfirms.set(0);

			if (clientCloseHandler != null)
				clientCloseHandler.handle(null);
		}
	}

	/*
	 * CONNECTACK packet payload:
	 * - encrypted
	 *   - data
	 */
	private void handleData(Buffer data) {
		if (state == State.Disconnecting) {
			log.trace("Connection {} got DATA packet from {} while disconnecting, ignore.",
					getName(), upstreamSocket.remoteAddress());
			return;
		}

		log.trace("Connection {} got DATA packet from {}.",
				getName(), upstreamSocket.remoteAddress());

		try {
			byte[] payload = session.decrypt(data.getBytes(PACKET_HEADER_BYTES, data.length()), nonce);
			clientSocket.write(Buffer.buffer(payload));
			// Flow control for upstream -> client
			if (clientSocket.writeQueueFull()) {
				log.trace("Client write queue full, pause upstream reading");
				upstreamSocket.pause();
				clientSocket.drainHandler(v-> upstreamSocket.resume());
			}

		} catch (CryptoException e) {
			log.error("Connection {} decrypt the DATA payload failed.", getName());
			close();
		}
	}

	public void connectClient(NetSocket clientSocket) {
		log.debug("Connection {} assigned for client {} from {}", getName(),
				Integer.toHexString(clientSocket.hashCode()), clientSocket.remoteAddress());

		state = State.Connecting;

		this.clientSocket = clientSocket;

		Handler<Void> clientClose = v -> {
			State oldState = state;
			state = State.Disconnecting;

			this.clientSocket = null;

			upstreamSocket.resume();
			upstreamSocket.drainHandler(null);

			// don't send disconnect in connecting state
			if (oldState == State.Relaying)
				sendDisconnect();

			log.debug("Connection {} disconnected client.", getName());
		};

		clientSocket.closeHandler(clientClose);
		clientSocket.exceptionHandler(t -> {
			if (log.isDebugEnabled())
				log.error("Client socket error", t);
			else
				log.error("Client socket error: {}", t.getMessage());

			clientSocket.close();
			this.clientSocket = null;
		});
		clientSocket.handler(this::handleClientData);

		// We use a internal xxxImpl class for convenient
		InetAddress clientIP = ((SocketAddressImpl)(clientSocket.remoteAddress())).ipAddress();
		int clientPort = clientSocket.remoteAddress().port();
		sendConnect(clientIP, clientPort);
	}

	private void disconnectClient() {
		if (clientSocket != null) {
			NetSocket socket = clientSocket;
			clientSocket = null;

			socket.close();
		}
	}

	private void handleClientData(Buffer data) {
		sendData(data.getBytes());
	}

	private void upstreamConnected() {
		state = State.Relaying;
		clientSocket.resume();
	}

	public void closeHandler(Handler<Void> handler) {
		closePromise.future().onComplete(ar -> handler.handle(null));
	}

	public void clientCloseHandler(Handler<Void> handler) {
		this.clientCloseHandler = handler;
	}

	@Override
	public void close() {
		synchronized (this) {
			if (state == State.Closed)
				return;
			else
				state = State.Closed;
		}

		if (clientSocket != null) {
			clientCloseHandler = null;
			clientSocket.handler((b) -> {});
			clientSocket.closeHandler(v -> {});
			clientSocket.close(v -> {});
			clientSocket = null;
		}

		if (upstreamSocket != null) {
			upstreamSocket.handler((b) -> {});
			upstreamSocket.closeHandler(v -> {});
			upstreamSocket.close(v -> {});
			upstreamSocket = null;
		}

		closePromise.complete();

		session = null;

		log.debug("Connection {} closed.", getName());
	}

	@Override
	protected void finalize() {
		close();
	}
}
