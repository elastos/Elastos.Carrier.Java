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

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.net.NetServer;
import io.vertx.core.net.NetServerOptions;
import io.vertx.core.net.NetSocket;
import io.vertx.core.net.SocketAddress;

import elastos.carrier.CarrierException;
import elastos.carrier.Id;
import elastos.carrier.Node;
import elastos.carrier.PeerInfo;
import elastos.carrier.crypto.CryptoBox;
import elastos.carrier.crypto.CryptoException;
import elastos.carrier.service.CarrierServiceException;
import elastos.carrier.service.ServiceContext;

public class ProxyServer extends AbstractVerticle {
	private static final int PERIODIC_CHECK_INTERVAL = 60000; // 1 minute
	private static final int RE_ANNOUNCE_INTERVAL = 60 * 60 * 1000;

	@SuppressWarnings("unused")
	private ServiceContext context;
	private Node node;

	private Configuration config;

	private NetServer server;

	private BitSet mappingPorts;
	private int minPort = 0;
	private int currentIndex;

	private long periodicCheckTimer;

	private PeerInfo peer;
	private long lastPeerAnnounce;

	private Map<Id, ProxySession> sessions;
	private Map<ProxyConnection, Object> connections;

	Cache<Id, Integer> portMappingCache;

	private static final Logger log = LoggerFactory.getLogger(ProxyServer.class);

	public ProxyServer(ServiceContext context) throws CarrierServiceException {
		this.context = context;
		this.node = context.getNode();
		this.sessions = new ConcurrentHashMap<>();
		this.connections = new ConcurrentHashMap<>();

		try {
			this.config = new Configuration(context.getConfiguration());
		} catch (Exception e) {
			throw new CarrierServiceException("Invalid configuration");
		}

		initMappingPorts(config.getPortMappingRange());

		portMappingCache = CacheBuilder.newBuilder()
			.expireAfterAccess(5, TimeUnit.MINUTES)
			.initialCapacity(128)
			.maximumSize(1024)
			.removalListener(rn -> releasePort((Integer)rn.getValue()))
			.build();

		if (config.getPeerKeypair() != null)
			peer = PeerInfo.create(config.getPeerKeypair(), node.getId(), getPort());
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
		return config.getHost();
	}

	public int getPort() {
		return config.getPort();
	}

	Configuration getConfig() {
		return config;
	}

	public boolean isRunning() {
		return server != null;
	}

	int allocPort(Id nodeId) throws CarrierServiceException {
		Integer port = portMappingCache.getIfPresent(nodeId);
		if (port != null)
			return port;

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

	private void releasePort(int port) {
		synchronized(mappingPorts) {
			mappingPorts.set(port);
		}
	}

	void releasePort(Id nodeId, int port) {
		portMappingCache.put(nodeId, port);
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

		SocketAddress localAddress = SocketAddress.inetSocketAddress(getPort(), getHost());
		server = vertx.createNetServer(options)
				.connectHandler(sock -> handleConnection(sock))
				.exceptionHandler(e -> log.error("ActiveProxy server error: " + e.getMessage(), e))
				.listen(localAddress, (asyncResult) -> {
					if (asyncResult.succeeded()) {
						periodicCheckTimer = getVertx().setPeriodic(PERIODIC_CHECK_INTERVAL, this::periodicCheck);
						if (peer != null)
							announceService();
						log.info("ActiveProxy Server started, listening on {}", localAddress);
						log.info("ActiveProxy transport: {}", vertx.isNativeTransportEnabled() ?
								"NATIVE" : "NIO");
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
			getVertx().cancelTimer(periodicCheckTimer);

			List<ProxySession> ss = new ArrayList<>(sessions.values());
			sessions.clear();
			for (ProxySession session : ss) {
				session.stop();
			}

			server.close(asyncResult -> log.info("ActiveProxy Server stopped"));
			server = null;
		}
	}

	private void announceService() {
		log.info("Announce peer: {}...", peer.getId());

		node.announcePeer(peer).whenComplete((v, e) -> {
			if (e == null) {
				lastPeerAnnounce = System.currentTimeMillis();
				log.info("Announce peer succeeded");
			} else {
				log.error("Announce peer failed: " + e.getMessage(), e);
			}
		});
	}

	private void periodicCheck(long timer) {
		if (peer != null && System.currentTimeMillis() - lastPeerAnnounce >= RE_ANNOUNCE_INTERVAL)
			announceService();

		log.info("Periodic checking for all sessions...");

		sessions.forEach((id, session) -> {
			session.periodicCheck();
		});
	}


	private void handleConnection(NetSocket socket) {
		ProxyConnection connection = new ProxyConnection(this, socket);
		log.debug("New proxy connection {} from {}", connection.getName(), socket.remoteAddress());

		connection.sendChallenge(ar -> {
			if (ar.succeeded()) {
				connection.closeHandler((v) -> connections.remove(connection));
				connections.put(connection, ProxyConnection.OBJECT);
			} else {
				connection.close();
			}
		});
	}

	void authenticate(ProxyConnection connection, Id nodeId, CryptoBox.PublicKey clientPk, String domain) {
		log.debug("Authenticating connection {} from {}...", connection.getName(), connection.upstreamAddress());

		if (sessions.containsKey(nodeId)) {
			log.error("Authenticate connection {} from {} failed - session {} already exists.",
					connection.getName(), connection.upstreamAddress(), nodeId);
			connection.close();
			return;
		}

		ProxySession session;
		try {
			session = new ProxySession(this, nodeId, clientPk, domain);
		} catch (CryptoException e) {
			log.error("Authenticate connection {} from {} failed - session id {} is invalid.",
					connection.getName(), connection.upstreamAddress(), nodeId);
			connection.close();
			return;
		}

		log.debug("Authenticating connection {} from {} success.", connection.getName(), connection.upstreamAddress());

		session.stopHandler(asyncResult -> {
			ProxySession s = sessions.remove(nodeId);
			if (s != null)
				s.close();
		});

		session.start(connection, asyncResult -> {
			connections.remove(connection);

			if (asyncResult.succeeded()) {
				sessions.put(nodeId, session);
			} else {
				connection.close();
				session.close();
			}
		});
	}

	void attach(ProxyConnection connection, Id clientNodeId, CryptoBox.PublicKey clientPk) {
		log.debug("Attaching connection {} from {} with session id {}.",
				connection.getName(), connection.upstreamAddress(), clientNodeId);

		ProxySession session = sessions.get(clientNodeId);
		if (session == null) {
			log.error("Attach connection {} from {} failed - session id {} not exists.",
					connection.getName(), connection.upstreamAddress(), clientNodeId);
			connection.close();
			return;
		} else {
			if (!session.getClientPublicKey().equals(clientPk)) {
				log.error("Attach connection {} from {} failed - invalid public key.",
						connection.getName(), connection.upstreamAddress(), clientNodeId);
				connection.close();
				return;
			}
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
