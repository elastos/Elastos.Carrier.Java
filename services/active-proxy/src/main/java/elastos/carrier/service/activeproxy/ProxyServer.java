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

import java.util.BitSet;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.net.NetServer;
import io.vertx.core.net.NetServerOptions;
import io.vertx.core.net.NetSocket;
import io.vertx.core.net.SocketAddress;

import elastos.carrier.CarrierException;
import elastos.carrier.Id;
import elastos.carrier.Node;
import elastos.carrier.crypto.CryptoBox.Nonce;
import elastos.carrier.crypto.CryptoException;
import elastos.carrier.service.CarrierServiceException;
import elastos.carrier.service.ServiceContext;

public class ProxyServer extends AbstractVerticle {
	private static final int IDLE_CHECK_INTERVAL = 120000; // 2 minute

	@SuppressWarnings("unused")
	private ServiceContext context;
	private Node node;

	private String host;
	private int port;

	private NetServer server;

	private BitSet mappingPorts;
	private int minPort = 0;
	private int currentIndex;

	private long idleCheckTimer;

	private Map<Id, ProxySession> sessions;
	private Map<ProxyConnection, Object> connections;

	private static final Logger log = LoggerFactory.getLogger(ProxyServer.class);

	public ProxyServer(ServiceContext context) throws CarrierServiceException {
		this.context = context;
		this.node = context.getNode();
		this.sessions = new ConcurrentHashMap<>();
		this.connections = new ConcurrentHashMap<>();

		this.host = (String)context.getConfiguration().getOrDefault("host", NetServerOptions.DEFAULT_HOST);
		this.port = (int)context.getConfiguration().getOrDefault("port", ActiveProxy.DEFAULT_PORT);

		String portMappingRange = (String)context.getConfiguration().getOrDefault("portMappingRange", ActiveProxy.DEFAULT_PORT_MAPPING_RANGE);
		initMappingPorts(portMappingRange);
	}

	private void initMappingPorts(String spec) throws CarrierServiceException {
		mappingPorts = new BitSet(65536);

		String[] ranges = spec.split("\\s*,\\s*");
		if (ranges.length == 0)
			throw new CarrierServiceException("Invalid portMappingRange: " + spec);

		for (String range : ranges) {
			String[] indices = range.split("\\s*-\\s*");
			if (indices.length != 2)
				throw new CarrierServiceException("Invalid portMappingRange: " + spec);

			int fromIndex = Integer.valueOf(indices[0]);
			int toIndex = Integer.valueOf(indices[1]);
			if (fromIndex <= 1024 || toIndex <= 1024 || fromIndex > 65535 ||
					toIndex > 65535 || toIndex < fromIndex)
				throw new CarrierServiceException("Invalid portMappingRange: " + spec);

			if (fromIndex < minPort)
				minPort = fromIndex;

			// [from, to)
			mappingPorts.set(fromIndex, toIndex+1);
		}
	}

	public String getHost() {
		return host;
	}

	public int getPort() {
		return port;
	}

	public boolean isRunning() {
		return server != null;
	}

	int allocPort() throws CarrierServiceException {
		synchronized(mappingPorts) {
			currentIndex = mappingPorts.nextSetBit(currentIndex);
			if (currentIndex < 0 || currentIndex >= 65536) {
				currentIndex = mappingPorts.nextSetBit(minPort);
				if (currentIndex < 0 || currentIndex >= 65536)
					throw new CarrierServiceException("No available port for the new session.");
			}

			mappingPorts.clear(currentIndex);
			return currentIndex;
		}
	}

	void releasePort(int port) {
		synchronized(mappingPorts) {
			mappingPorts.set(port);
		}
	}

	void setPortUnavailable(int port) {
		synchronized(mappingPorts) {
			mappingPorts.clear(port);
		}
	}

	byte[] decrypt(Id nodeId, byte[] buffer) throws CarrierException {
		return node.decrypt(nodeId, buffer);
	}

	byte[] encrypt(Id nodeId, byte[] buffer) throws CarrierException {
		return node.encrypt(nodeId, buffer);
	}

	@Override
	public void start() throws Exception {
		log.debug("ActiveProxy server statring...");

		NetServerOptions options = new NetServerOptions()
				.setReceiveBufferSize(0x7FFF)
				.setSsl(false)
				.setTcpKeepAlive(true)
				.setIdleTimeout(120)
				.setIdleTimeoutUnit(TimeUnit.SECONDS)
				.setTcpFastOpen(true)
				.setReuseAddress(true);

		SocketAddress localAddress = SocketAddress.inetSocketAddress(port, host);
		server = vertx.createNetServer(options)
				.connectHandler(sock -> handleConnection(sock))
				.exceptionHandler(e -> log.error("ActiveProxy server error: " + e.getMessage(), e))
				.listen(localAddress, (asyncResult) -> {
					if (asyncResult.succeeded()) {
						idleCheckTimer = getVertx().setPeriodic(IDLE_CHECK_INTERVAL, this::idleCheck);
						log.info("ActiveProxy Server started, listening on {}", localAddress);
					} else {
						log.error("ActiveProxy Server listen failed on {} - {}", localAddress, asyncResult.cause());
						server.close();
						server = null;
					}
				});
	}

	@Override
	public void stop() {
		if (server != null) {
			log.debug("ActiveProxy server stopping...");
			getVertx().cancelTimer(idleCheckTimer);
			server.close(asyncResult -> log.info("ActiveProxy Server stopped"));
			server = null;
		}
	}

	private void idleCheck(long timer) {
		log.info("Checking the idle sessions...");

		sessions.forEach((id, session) -> {
			session.tryCloseIdleConnections();
		});
	}

	private void handleConnection(NetSocket socket) {
		ProxyConnection connection = new ProxyConnection(this, socket);
		connections.put(connection, ProxyConnection.OBJECT);
		log.debug("New proxy connection {} from {}", connection.getName(), socket.remoteAddress());
	}

	void authenticate(ProxyConnection connection, Id nodeId, Id sessionId, Nonce nonce) {
		log.debug("Authenticating connection {} from {}...", connection.getName(), connection.upstreamAddress());

		if (sessions.containsKey(sessionId)) {
			log.error("Authenticate connection {} from {} failed - session {} already exists.",
					connection.getName(), connection.upstreamAddress(), sessionId);
			connection.close();
			connections.remove(connection);
			return;
		}

		ProxySession session;
		try {
			session = new ProxySession(this, nodeId, sessionId, nonce);
		} catch (CryptoException e) {
			log.error("Authenticate connection {} from {} failed - session id {} is invalid.",
					connection.getName(), connection.upstreamAddress(), sessionId);
			connection.close();
			connections.remove(connection);
			return;
		}

		log.debug("Authenticating connection {} from {} success.", connection.getName(), connection.upstreamAddress());

		session.stopHandler(asyncResult -> {
			ProxySession s = sessions.remove(sessionId);
			if (s != null)
				s.close();
		});

		session.start(connection, asyncResult -> {
			connections.remove(connection);

			if (asyncResult.succeeded()) {
				sessions.put(sessionId, session);
			} else {
				connection.close();
				session.close();
			}
		});
	}

	void attach(ProxyConnection connection, Id sessionId, byte[] cookie) {
		log.debug("Attaching connection {} from {} with session id {}.",
				connection.getName(), connection.upstreamAddress(), sessionId);

		ProxySession session = sessions.get(sessionId);
		if (session == null) {
			log.error("Attach connection {} from {} failed - session id {} not exists.",
					connection.getName(), connection.upstreamAddress(), sessionId);
			connection.close();
			connections.remove(connection);
			return;
		}

		try {
			byte[] payload = session.decrypt(cookie);
			if (payload.length != Short.BYTES)
				throw new CryptoException("Invalid encrypted cookie");

			int port = ((payload[0] & 0x00ff) << 8) | (payload[1] & 0x00ff);
			if (port != session.getPort())
				throw new CryptoException("Invalid encrypted cookie");
		} catch (CryptoException e) {
			log.error("attach connection {} from {} failed - invalid attach cookie.",
					connection.getName(), connection.upstreamAddress());
			connection.close();
			connections.remove(connection);
			return;
		}

		connection.sendAttachAck(asyncResult -> {
			connections.remove(connection);

			if (asyncResult.succeeded()) {
				session.attachUpstreamConnection(connection);
			} else {
				connection.close();
			}
		});
	}
}
