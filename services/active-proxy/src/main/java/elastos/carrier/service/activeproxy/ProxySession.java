/*
 * Copyright (c) 2022 Elastos Foundation
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

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.net.NetServer;
import io.vertx.core.net.NetServerOptions;
import io.vertx.core.net.NetSocket;
import io.vertx.core.net.SocketAddress;

import elastos.carrier.Id;
import elastos.carrier.crypto.CryptoBox;
import elastos.carrier.crypto.CryptoBox.KeyPair;
import elastos.carrier.crypto.CryptoBox.Nonce;
import elastos.carrier.crypto.CryptoBox.PublicKey;
import elastos.carrier.crypto.CryptoException;
import elastos.carrier.service.CarrierServiceException;;

public class ProxySession implements AutoCloseable {
	private static final int STOP_DELAY = 60000; // 1 minute
	private static final int MAX_IDLE_TIME = 600000; // 10 minutes

	private String name;

	private Id nodeId;
	private Id sessionId;

	private KeyPair keyPair;

	private CryptoBox box;
	private Nonce nonce;

	private ProxyServer server;

	private NetServer sessionServer;
	int port;
	private boolean ready;

	private ConcurrentLinkedQueue<NetSocket> clientSocks;
	private ConcurrentHashMap<ProxyConnection, Object> connections;
	private ConcurrentLinkedQueue<ProxyConnection> idleConnections;

	long idleTimestamp;

	private Promise<Void> stopPromise;
	private Handler<Void> stopHandler;

	private static final Logger log = LoggerFactory.getLogger(ProxySession.class);

	/**
	 * Create a ProxySession object.
	 *
	 * @param server the proxy server instance
	 * @param nodeId the client DHT node id
	 * @param sessionId the client session(encryption, not signature) public key
	 * @param nonce the encryption nonce for the new session
	 * @throws CryptoException
	 */
	public ProxySession(ProxyServer server, Id nodeId, Id sessionId, Nonce nonce) throws CryptoException {
		this.name = sessionId.toString();

		this.server = server;

		this.nodeId = nodeId;
		this.sessionId = sessionId;

		this.nonce = nonce;
		this.keyPair = CryptoBox.KeyPair.random();

		this.box = CryptoBox.fromKeys(CryptoBox.PublicKey.fromBytes(sessionId.bytes()),
				this.keyPair.privateKey());

		this.clientSocks = new ConcurrentLinkedQueue<>();
		this.connections = new ConcurrentHashMap<>();
		this.idleConnections = new ConcurrentLinkedQueue<>();

		this.ready = false;
	}

	public Id getId() {
		return sessionId;
	}

	public String getName() {
		return name;
	}

	public PublicKey getPublicKey() {
		return keyPair.publicKey();
	}

	public int getPort() {
		return port;
	}

	public Vertx getVertx() {
		return server.getVertx();
	}

	byte[] encrypt(byte[] plain) throws CryptoException {
		return box.encrypt(plain, nonce);
	}

	byte[] decrypt(byte[] cipher) throws CryptoException {
		return box.decrypt(cipher, nonce);
	}

	private void establish(ProxyConnection connection, Handler<AsyncResult<ProxySession>> startHandler) {
        connection.sendAuthAck(nodeId, keyPair.publicKey(), nonce, port, ar -> {
			if (ar.succeeded()) {
				log.info("Session {} server started.", getName());

				attachUpstreamConnection(connection);

				if (startHandler != null)
					startHandler.handle(Future.succeededFuture(this));
				ready = true;
			} else {
				log.error("Session " + getName() + " server establish failed.", ar.cause());
				sessionServer.close();
				sessionServer = null;

				if (startHandler != null)
					startHandler.handle(Future.failedFuture(ar.cause()));
			}
		});
	}

	// Should be a lambda function inside start(Connection, Handler),
	// but can not call lambda itself inside the lambda
	private void start(ProxyConnection connection, NetServer sessionServer, Handler<AsyncResult<ProxySession>> handler) {
		try {
			port = server.allocPort();
		} catch (CarrierServiceException e) {
			log.error("Session {} server can not start bacause of no port available", getName());
			handler.handle(Future.failedFuture(e));
			return;
		}

		SocketAddress localAddress = SocketAddress.inetSocketAddress(port, server.getHost());
		log.debug("Session {} server try to start on {}", getName(), localAddress);
		sessionServer.listen((localAddress), ar -> {
            if (ar.succeeded()) {
                log.info("Session {} server is listening on {}", getName(), localAddress);
                idleTimestamp = -1;
                establish(connection, handler);
            } else {
                log.warn("Session " + getName() + " listening on " + localAddress +
                		" failed, trying the next available port...", ar.cause());
                // TODO: Check the port unavailable error.
                server.setPortUnavailable(port);
                getVertx().runOnContext((v) -> start(connection, sessionServer, handler));
            }
		});
	}

	public void start(ProxyConnection connection, Handler<AsyncResult<ProxySession>> handler) {
		this.stopPromise = Promise.promise();
		stopPromise.future().onComplete(ar -> {
			if (stopHandler != null)
				stopHandler.handle(null);
		});

		NetServerOptions options = new NetServerOptions()
				.setReceiveBufferSize(0x7FFF - CryptoBox.MAC_BYTES - ProxyConnection.PACKET_HEADER_BYTES)
				.setSsl(false)
				.setIdleTimeout(60)
				.setIdleTimeoutUnit(TimeUnit.SECONDS)
				.setTcpFastOpen(true)
				.setReuseAddress(true);

		sessionServer = getVertx().createNetServer(options)
			.connectHandler(sock -> handleClientSocket(sock));

		start(connection, sessionServer, handler);
	}

	public void stop() {
		if (sessionServer != null) {
			ready = false;

			log.debug("Session {} server stopping...", getName());
			sessionServer.close(asyncResult -> log.info("Session {} server stopped", getName()));
			sessionServer = null;

			idleConnections.clear();

			connections.forEach((c, o) -> c.close());
			connections.clear();

			clientSocks.forEach(s -> s.close());
			clientSocks.clear();

			stopPromise.complete();
		}
	}

	public void stopHandler(Handler<Void> handler) {
		this.stopHandler = handler;
	}

	protected void tryCloseIdleConnections() {
		log.info("STATUS: session={}, connections={}, idle={}",
				getName(), connections.size(), idleConnections.size());

		if (idleTimestamp < 0 || connections.size() <= 1 || idleConnections.size() < connections.size() ||
				System.currentTimeMillis() - idleTimestamp < MAX_IDLE_TIME)
			return;

		log.info("Session {} closing the idle connections...", getName());
		while (idleConnections.size() > 1) {
			ProxyConnection c = idleConnections.poll();
			connections.remove(c);
			c.close();
		}
	}

	protected void attachUpstreamConnection(ProxyConnection connection) {
		log.debug("Session {} attached connection {}", getName(), connection.getName());

		connection.setSession(this);

		connection.closeHandler(v -> {
			log.trace("Session {} detached connection {}", getName(), connection.getName());
			idleConnections.remove(connection);
			connections.remove(connection);

			if (connections.isEmpty()) {
				log.debug("Session {} will stop if no new connections in {} seconds", getName(), STOP_DELAY / 1000);

				getVertx().setTimer(STOP_DELAY, (id) -> {
					if (connections.isEmpty()) {
						log.debug("Session {} stopping due to non active connections.", getName());
						stop();
					}
					getVertx().cancelTimer(id);
				});
			}
		});

		connection.clientCloseHandler(v -> {
			log.trace("Session {} add connection {} to the idle connections",
					getName(), connection.getName());
			idleConnections.add(connection);
			if (idleConnections.size() == connections.size()) // all connections are idle
				idleTimestamp = System.currentTimeMillis();
		});

		connections.put(connection, ProxyConnection.OBJECT);

		NetSocket clientSocket = clientSocks.poll();
		if (clientSocket != null) {
			connection.connectClient(clientSocket);
		} else {
			log.trace("Session {} add connection {} to the idle connections",
					getName(), connection.getName());
			idleConnections.add(connection);
		}
	}

	private void handleClientSocket(NetSocket socket) {
		log.debug("Session {} new client connection {} from {}", getName(),
				Integer.toHexString(socket.hashCode()), socket.remoteAddress());

		// Pause the socket first, will resume when the assigned connection opened the upstream
		socket.pause();

		if (!ready) {
			log.error("Session {} reject the client connection {} because of the session server not ready",
					getName(), Integer.toHexString(socket.hashCode()));
			socket.close();
			return;
		}

		idleTimestamp = -1;

		ProxyConnection connection = idleConnections.poll();
		if (connection != null) {
			connection.connectClient(socket);
		} else {
			log.debug("Session {} no connection available for the client connection {}, add it to the queue.",
					getName(), Integer.toHexString(socket.hashCode()));
			socket.closeHandler(v -> clientSocks.remove(socket));
			clientSocks.add(socket);
		}
	}

	@Override
	public void close() {
		if (server == null)
			return;

		stop();

		if (box != null) {
			box.close();
			box = null;
		}

		if (keyPair != null) {
			keyPair.publicKey().destroy();
			keyPair.privateKey().destroy();
			keyPair = null;
		}

		server = null;

		log.trace("Session {} closed and released all resources", getName());
	}

	@Override
	protected void finalize() {
		close();
	}
}
