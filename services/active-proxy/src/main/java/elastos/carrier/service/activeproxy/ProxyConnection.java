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

import java.io.UnsupportedEncodingException;
import java.net.InetAddress;
import java.util.Arrays;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.net.NetSocket;
import io.vertx.core.net.SocketAddress;
import io.vertx.core.net.impl.SocketAddressImpl;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import io.vertx.core.json.JsonObject;
import elastos.carrier.CarrierException;
import elastos.carrier.Id;
import elastos.carrier.crypto.CryptoBox;
import elastos.carrier.crypto.CryptoBox.Nonce;
import elastos.carrier.crypto.CryptoBox.PublicKey;
import elastos.carrier.crypto.CryptoException;
import elastos.carrier.crypto.Signature;
import elastos.carrier.utils.Hex;
import elastos.carrier.utils.ThreadLocals;

public class ProxyConnection implements AutoCloseable {
	public static final Object OBJECT = new Object();
	static final int PACKET_HEADER_BYTES = Short.BYTES + Byte.BYTES;
	private static AtomicInteger NEXT_ID = new AtomicInteger(0);

	private long id;
	private String name;

	private ProxyServer server;
	private ProxySession session;

	private NetSocket upstreamSocket;
	private NetSocket clientSocket;

	private Promise<Void> closePromise;

	private Handler<Void> clientCloseHandler;
	private boolean needSendDisconnect;

	private Buffer stickyBuffer;

	ScheduledFuture<?> scheduledHelper;

	private /* volatile */ State state;

	private static final Logger log = LoggerFactory.getLogger(ProxyConnection.class);

	public enum State {
		Authenticating,
		Idling,
		Connecting,
		Relaying,
		Closed
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

	void sendAuthAck(Id clientNodeId, PublicKey sessionPk, Nonce nonce, int port,
			Handler<AsyncResult<Void>> handler) {
		log.trace("Connection {} sending AUTH ACK to {}@{}",
				getName(), clientNodeId, upstreamSocket.remoteAddress());


		// Vert.x Buffer or ByteBuffer both are too heavy,
		// so we just use the plain byte array
		byte[] payload = new byte[PublicKey.BYTES + Nonce.BYTES + Short.BYTES];

		int pos = 0;
		System.arraycopy(sessionPk.bytes(), 0, payload, pos, PublicKey.BYTES);

		pos += PublicKey.BYTES;
		System.arraycopy(nonce.bytes(), 0, payload, pos, Nonce.BYTES);

		pos += Nonce.BYTES;
		shortToNetwork(port, payload, pos);

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
		buf.appendByte(PacketFlag.authAck());
		buf.appendBytes(cipher);
		buf.appendBytes(padding);

		state = State.Idling;
		upstreamSocket.write(buf, handler);
	}

	private void sendPacket(byte flag, byte[] payload, Handler<AsyncResult<Void>> handler) {
		if (state == State.Closed) {
			log.warn("Connection {} already closed, but try to send {} to upstream",
					getName(), PacketFlag.toString(flag));
			return;
		}

		log.trace("Connection {} sending {} to {}", getName(),
				PacketFlag.toString(flag), upstreamSocket.remoteAddress());

		int size = PACKET_HEADER_BYTES;

		byte[] cipher = null;
		if (payload != null && payload.length > 0) {
			try {
				cipher = session.encrypt(payload);
				size += cipher.length;
			} catch (CarrierException e) {
				log.error("SHOULD NEVER HAPPEN: connection {} encrypt packet failed", getName());
				if (handler != null)
					handler.handle(Future.failedFuture(e));
				return;
			}
		}

		byte[] padding = null;
		byte type = PacketFlag.getType(flag);
		if (type != PacketFlag.DATA && type != PacketFlag.ERROR && type != PacketFlag.SIGNATURE) {
			padding = randomPadding();
			size += padding.length;
		}

		// header
		Buffer buf = Buffer.buffer(size);
		buf.appendUnsignedShort(size);
		buf.appendByte(flag);

		// payload
		if (cipher != null)
			buf.appendBytes(cipher);

		// padding
		if (padding != null)
			buf.appendBytes(padding);

		upstreamSocket.write(buf, handler);
	}

	void sendAttachAck(Handler<AsyncResult<Void>> handler) {
		state = State.Idling;
		sendPacket(PacketFlag.attachAck(), null, handler);
	}

	private void sendPingAck() {
		sendPacket(PacketFlag.pingAck(), null, ar -> {
			if (ar.failed())
				close();
		});
	}

	private void sendConnect(InetAddress address, int port) {
		byte[] addr = address.getAddress();

		byte[] payload = new byte[1 + 16 + 2];

		int pos = 0;
		payload[pos++] = (byte)(addr.length);
		System.arraycopy(addr, 0, payload, pos, addr.length);
		pos += 16;
		shortToNetwork(port, payload, pos);

		sendPacket(PacketFlag.connect(), payload, ar -> {
			if (ar.failed())
				close();
		});
	}

	private void sendDisconnect() {
		sendPacket(PacketFlag.disconnect(), null, ar -> {
			if (ar.failed())
				close();
		});
	}

	private void sendData(byte[] data) {
		sendPacket(PacketFlag.data(), data, ar -> {
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

	private void sendSignature(String alt) {
        Id clientNodeId = session.getClientNodeId();
        int port = session.getPort();

		log.trace("Connection {} sending signature to {}@{}",
				getName(), clientNodeId, upstreamSocket.remoteAddress());

        int altLength = 0;
        if (alt != null) {
            altLength = alt.getBytes().length;
        }

		byte[] payload = new byte[Short.BYTES + Signature.BYTES + altLength];

		int pos = 0;
		shortToNetwork(port, payload, pos);

		pos += Short.BYTES;
        if (alt != null) {
            System.arraycopy(alt.getBytes(), 0, payload, pos, altLength);
            pos += altLength;
        }

		//byte[] signature = server.getNode().createPeerSignature(clientNodeId, port, alt);
        byte[] signature = new byte[Signature.BYTES];
		System.arraycopy(signature, 0, payload, pos, signature.length);

		sendPacket(PacketFlag.signature(), payload, ar -> {
			if (ar.failed())
				close();
		});
	}

	private void sendSignatureFail() {
        Id clientNodeId = session.getClientNodeId();
		log.trace("Connection {} sending signature Fail to {}@{}",
				getName(), clientNodeId, upstreamSocket.remoteAddress());

		byte[] payload = new byte[1];
		payload[0] = 0; //Maybe later send the error code;

		sendPacket(PacketFlag.signature(), payload, ar -> {
			if (ar.failed())
				close();
		});
	}

	private void sendError(short code, String message) {
		byte[] msg = message.getBytes();
		byte[] payload = new byte[3 + msg.length];

		int pos = 0;
		shortToNetwork(code, payload, pos);
		pos += Short.BYTES;
		System.arraycopy(msg, 0, payload, pos, msg.length);
		pos += msg.length;
		payload[pos] = 0;

		sendPacket(PacketFlag.error(), payload, ar -> {
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
			throw new IllegalStateException("INTERNAL ERROR: Connection in illegal state!!!");
		}

		byte flag = packet.getByte(Short.BYTES);
		boolean ack = PacketFlag.isAck(flag);
		byte type = PacketFlag.getType(flag);

		log.trace("Connection {} got {} packet({} bytes) from {}",
				getName(), PacketFlag.toString(flag), size, upstreamSocket.remoteAddress());

		switch (state) {
		case Authenticating:
			if (!ack && type == PacketFlag.AUTH) {
				handleAuth(packet);
				return;
			} if (!ack && type == PacketFlag.ATTACH) {
				handleAttach(packet);
				return;
			} else {
				log.error("Connection {} got wrong packet {}, AUTH or ATTACH excepted",
						getName(), PacketFlag.toString(flag));
				close();
			}
			break;

		case Idling:
			if (!ack && type == PacketFlag.PING) {
				handleKeepAlive(packet);
				return;
			} else if (!ack && type == PacketFlag.SIGNATURE) {
				handleSignature(packet);
				return;
			} else {
				log.error("Connection {} got wrong packet {}, PING excepted",
						getName(), PacketFlag.toString(flag));
				close();
			}
			break;

		case Connecting:
			if (ack && type == PacketFlag.CONNECT) {
				handleConnectAck(packet);
				return;
			} else {
				log.error("Connection {} got wrong packet {}, CONNECT ACK excepted",
						getName(), PacketFlag.toString(flag));
				close();
			}
			break;

		case Relaying:
			if (!ack && type == PacketFlag.DATA) {
				handleData(packet);
				return;
			} else if (!ack && type == PacketFlag.DISCONNECT) {
				handleDisconnect(packet);
				return;
			} else {
				log.error("Connection {} got wrong packet {}, DATA or DISCONNECT excepted",
						getName(), PacketFlag.toString(flag));
				close();
			}
			break;

		case Closed:
			log.error("INTERNAL ERROR: Connection {} got packet after closed!!!", getName());
			break;
		}
	}

	private void handleAuth(Buffer packet) {
		int size = PACKET_HEADER_BYTES + Id.BYTES + CryptoBox.MAC_BYTES +
				CryptoBox.PublicKey.BYTES + CryptoBox.Nonce.BYTES;
		if (packet.length() < size) {
			log.error("Connection {} got invalid authentication packet from {}.",
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
			byte[] cipher = packet.getBytes(pos, size);
			payload = server.decrypt(nodeId, cipher);
		} catch (CarrierException e) {
			log.error("Connection {} decrypt the packet failed.", getName());
			close();
			return;
		}

		Id sessionId = Id.of(Arrays.copyOfRange(payload, 0, CryptoBox.PublicKey.BYTES));
		Nonce nonce = Nonce.fromBytes(Arrays.copyOfRange(payload, CryptoBox.PublicKey.BYTES,
				CryptoBox.PublicKey.BYTES + CryptoBox.Nonce.BYTES));

		server.authenticate(this, nodeId, sessionId, nonce);
	}

	private void handleAttach(Buffer packet) {
		int size = PACKET_HEADER_BYTES + CryptoBox.PublicKey.BYTES + CryptoBox.MAC_BYTES +  Short.BYTES;
		if (packet.length() < size) {
			log.error("Connection {} got invalid attach packet from {}.",
					getName(), upstreamSocket.remoteAddress());
			close();
			return;
		}

		log.trace("Connection {} got attach packet from {}.",
				getName(), upstreamSocket.remoteAddress());

		int pos = PACKET_HEADER_BYTES;
		Id sessionId = Id.of(packet.getBytes(pos, pos + Id.BYTES));
		pos += CryptoBox.PublicKey.BYTES;

		byte[] payload = packet.getBytes(pos, pos + CryptoBox.MAC_BYTES +  Short.BYTES);
		server.attach(this, sessionId, payload);
	}

	private void handleKeepAlive(Buffer packet) {
		log.trace("Connection {} got PING packet from {}.",
				getName(), upstreamSocket.remoteAddress());

		sendPingAck();
	}

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

	private void handleDisconnect(Buffer packet) {
		log.trace("Connection {} got DISCONNECT packet from {}.",
				getName(), upstreamSocket.remoteAddress());

		disconnectClient();
	}

	private void handleData(Buffer data) {
		log.trace("Connection {} got DATA packet from {}.",
				getName(), upstreamSocket.remoteAddress());

		try {
			byte[] payload = session.decrypt(data.getBytes(PACKET_HEADER_BYTES, data.length()));
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

	private void verifyDomainName(String domainName, Handler<AsyncResult<HttpResponse<Buffer>>> handler) {
        Vertx vertx = session.getVertx();
        WebClient client = WebClient.create(vertx);
        JsonObject data = new JsonObject()
            .put("nodeId", session.getClientNodeId().toBase58String())
            .put("serviceIp", server.getHost())
            .put("servicePort", session.getPort())
            .put("alt", domainName);

        client.post(server.getHelperPort(), server.getHelperDomain(), "/vhosts/verify")
            .putHeader("Authorization", "Bearer YLy7o264sNs0uPoHwSJUPlKvC2U7MfHbDgYRWPtB69E")
            .ssl(true)
            .sendJsonObject(data, handler);
    }

    private void handleSignature(Buffer packet) {
        log.trace("Connection {} got SIGNATURE packet from {}.",
				getName(), upstreamSocket.remoteAddress());

		try {
			byte[] payload = session.decrypt(packet.getBytes(PACKET_HEADER_BYTES, packet.length()));
			byte hasAlt = payload[0];

	        if (hasAlt > 0) {
	        	String domainName = new String(payload, 1, payload.length - 1, "UTF-8");

	        	//Send to helper service for verify
	        	verifyDomainName(domainName, ar -> {
	                if (ar.succeeded()) {
                        HttpResponse<Buffer> response = ar.result();
                        System.out.println("Got HTTP response with status " + response.statusCode());
                        sendSignature(domainName);
                        scheduledHelper = this.server.getNode().getScheduler().scheduleWithFixedDelay(() -> {
                            this.verifyDomainName(domainName, ar1 -> {});
                        }, ActiveProxy.HELPER_VERIFY_INTERVAL, ActiveProxy.HELPER_VERIFY_INTERVAL, TimeUnit.SECONDS);
                    } else {
                        ar.cause().printStackTrace();
                        sendSignatureFail();
                    }
	              });
	        }
	        else {
	            sendSignature(null);
	        }
		} catch (CryptoException | UnsupportedEncodingException e) {
			log.error("Connection {} decrypt the SIGNATURE payload failed.", getName());
			close();
		}
    }

	public void connectClient(NetSocket clientSocket) {
		log.debug("Connection {} assigned for client {} from {}", getName(),
				Integer.toHexString(clientSocket.hashCode()), clientSocket.remoteAddress());

		state = State.Connecting;

		this.clientSocket = clientSocket;

		needSendDisconnect = true;

		Handler<Void> clientClose = v -> {
			this.clientSocket = null;

			upstreamSocket.resume();
			upstreamSocket.drainHandler(null);

			if (needSendDisconnect)
				sendDisconnect();

			if (clientCloseHandler != null)
				clientCloseHandler.handle(v);

			log.debug("Connection {} disconnected client.", getName());
			state = State.Idling;
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
			needSendDisconnect = false;
			clientSocket.close();
			clientSocket = null;
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
			clientSocket.handler(null);
			clientSocket.closeHandler(null);
			clientSocket.close(null);
			clientSocket = null;
		}

		if (upstreamSocket != null) {
			upstreamSocket.handler(null);
			upstreamSocket.closeHandler(null);
			upstreamSocket.close();
			upstreamSocket = null;
		}

		closePromise.complete();

		if (scheduledHelper != null) {
			scheduledHelper.cancel(false);
			// the scheduled tasks should experience exceptions,
			try {
				scheduledHelper.get();
			} catch (ExecutionException e) {
				log.error("Scheduled future error", e);
			} catch (InterruptedException e) {
				log.error("Scheduled future error", e);
			} catch (CancellationException ignore) {
			}
			scheduledHelper = null;
		}

		session = null;

		log.debug("Connection {} closed.", getName());
	}

	@Override
	protected void finalize() {
		close();
	}
}
