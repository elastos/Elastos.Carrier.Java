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

package elastos.carrier.kademlia;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.cache.RemovalListener;
import com.google.common.cache.RemovalNotification;

import elastos.carrier.CarrierException;
import elastos.carrier.Configuration;
import elastos.carrier.Id;
import elastos.carrier.LookupOption;
import elastos.carrier.NodeInfo;
import elastos.carrier.NodeStatus;
import elastos.carrier.NodeStatusListener;
import elastos.carrier.PeerInfo;
import elastos.carrier.Value;
import elastos.carrier.crypto.CryptoBox;
import elastos.carrier.crypto.Signature;
import elastos.carrier.kademlia.DHT.Type;
import elastos.carrier.kademlia.exceptions.CryptoError;
import elastos.carrier.kademlia.exceptions.IOError;
import elastos.carrier.kademlia.exceptions.KadException;
import elastos.carrier.kademlia.tasks.Task;
import elastos.carrier.kademlia.tasks.TaskFuture;
import elastos.carrier.utils.AddressUtils;

public class Node implements elastos.carrier.Node {
	private Configuration config;

	private Signature.KeyPair keyPair;
	private CryptoBox.KeyPair encryptKeyPair;
	private Id id;

	private boolean persistent;
	private File storagePath;

	private static AtomicInteger schedulerThreadIndex;
	private volatile static ScheduledThreadPoolExecutor defaultScheduler;
	private ScheduledExecutorService scheduler;

	private List<ScheduledFuture<?>> scheduledActions = new ArrayList<>();

	private NetworkEngine networkEngine;

	private DHT dht4;
	private DHT dht6;
	private int numDHTs;
	private LookupOption defaultLookupOption = LookupOption.CONSERVATIVE;

	private LoadingCache<Id, CryptoContext> cryptoContexts;
	private Blacklist blacklist;

	private TokenManager tokenMan;
	private DataStorage storage;

	private NodeStatus status;
	private List<NodeStatusListener> statusListeners;

	private static final Logger log = LoggerFactory.getLogger(Node.class);

	public Node(Configuration config) throws KadException {
		if (config.IPv4Address() == null && config.IPv6Address() == null) {
			log.error("No valid IPv4 or IPv6 address specified");
			throw new IOError("No listening address");
		}

		if (Constants.DEVELOPMENT_ENVIRONMENT)
			log.info("Carrier node running in development environment.");

		storagePath = config.storagePath() != null ? config.storagePath().getAbsoluteFile() : null;
		persistent = checkPersistence(storagePath);

		File keyFile = null;
		if (persistent) {
			// Try to load the existing key
			keyFile = new File(storagePath, "key");
			if (keyFile.exists()) {
				if (keyFile.isDirectory())
					log.warn("Key file path {} is an existing directory. DHT node will not be able to persist node key", keyFile);
				else
					loadKey(keyFile);
			}
		}

		if (keyPair == null) // no existing key
			initKey(keyFile);

		encryptKeyPair = CryptoBox.KeyPair.fromSignatureKeyPair(keyPair);

		id = Id.of(keyPair.publicKey().bytes());
		if (persistent) {
			File idFile = new File(storagePath, "id");
			writeIdFile(idFile);
		}

		log.info("Carrier Kademlia node: {}", id);

		blacklist = new Blacklist();
		statusListeners = new ArrayList<>(4);
		tokenMan = new TokenManager();

		setupCryptoBoxesCache();

		status = NodeStatus.Stopped;

		this.config = config;

		this.scheduledActions = new ArrayList<>();
	}

	private boolean checkPersistence(File storagePath) {
		if (storagePath == null) {
			log.info("Storage path disabled, DHT node will not try to persist");
			return false;
		}

		if (storagePath.exists()) {
			if (!storagePath.isDirectory()) {
				log.warn("Storage path {} is not a directory. DHT node will not be able to persist state", storagePath);
				return false;
			} else {
				return true;
			}
		} else {
			return storagePath.mkdirs();
		}
	}

	private void loadKey(File keyFile) throws KadException {
		try (FileInputStream is = new FileInputStream(keyFile)) {
			byte[] key = is.readAllBytes();
			keyPair = Signature.KeyPair.fromPrivateKey(key);
		} catch (IOException e) {
			throw new IOError("Can not read the key file.", e);
		}
	}

	private void initKey(File keyFile) throws KadException {
		keyPair = Signature.KeyPair.random();
		if (keyFile != null) {
			try (FileOutputStream os = new FileOutputStream(keyFile)) {
				os.write(keyPair.privateKey().bytes());
			} catch (IOException e) {
				throw new IOError("Can not write the key file.", e);
			}
		}
	}

	private void writeIdFile(File idFile) throws KadException {
		if (idFile != null) {
			try (FileOutputStream os = new FileOutputStream(idFile)) {
				os.write(id.toString().getBytes());
			} catch (IOException e) {
				throw new IOError("Can not write the id file.", e);
			}
		}
	}

	private void setupCryptoBoxesCache() {
		CacheLoader<Id, CryptoContext> loader;
		loader = new CacheLoader<>() {
			@Override
			public CryptoContext load(Id id) throws CryptoError {
				return new CryptoContext(id, encryptKeyPair);
			}
		};

		RemovalListener<Id, CryptoContext> listener;
		listener = new RemovalListener<Id, CryptoContext>() {
			@Override
			public void onRemoval(RemovalNotification<Id, CryptoContext> n) {
				n.getValue().close();
			}
		};

		cryptoContexts = CacheBuilder.newBuilder()
				.expireAfterAccess(Constants.KBUCKET_OLD_AND_STALE_TIME, TimeUnit.MILLISECONDS)
				.removalListener(listener)
				.build(loader);
	}

	@Override
	public Id getId() {
		return id;
	}

	@Override
	public NodeInfo getNodeInfo4() {
		return dht4 != null ? new NodeInfo(id, dht4.getAddress()) : null;
	}

	@Override
	public NodeInfo getNodeInfo6() {
		return dht6 != null ? new NodeInfo(id, dht6.getAddress()) : null;
	}

	@Override
	public boolean isLocalId(Id id) {
		return this.id.equals(id);
	}

	@Override
	public Configuration getConfig() {
		return config;
	}

	@Override
	public void setDefaultLookupOption(LookupOption option) {
		defaultLookupOption = option != null ? option : LookupOption.CONSERVATIVE;
	}

	@Override
	public void addStatusListener(NodeStatusListener listener) {
		statusListeners.add(listener);
	}

	@Override
	public void removeStatusListener(NodeStatusListener listener) {
		statusListeners.remove(listener);
	}

	private void setStatus(NodeStatus expected, NodeStatus newStatus) {
		if (this.status.equals(expected)) {
			NodeStatus old = this.status;
			this.status = newStatus;
			if (!statusListeners.isEmpty()) {
				for (NodeStatusListener l : statusListeners)
					l.statusChanged(newStatus, old);
			}
		} else {
			log.warn("Set status failed, expected is {}, actual is {}", expected, status);
		}
	}

	@Override
	public ScheduledExecutorService getScheduler() {
		return scheduler;
	}

	@Override
	public void setScheduler(ScheduledExecutorService scheduler) {
		this.scheduler = scheduler;
	}

	private static ScheduledExecutorService getDefaultScheduler() {
		if (defaultScheduler == null) {
			schedulerThreadIndex = new AtomicInteger(0);

			int corePoolSize = Math.max(Runtime.getRuntime().availableProcessors(), 4);

			ThreadGroup group = new ThreadGroup("CarrierKadNode");
			ThreadFactory factory = (r) -> {
				Thread thread = new Thread(group, r, "KadNode-sc-" + schedulerThreadIndex.getAndIncrement());
				thread.setUncaughtExceptionHandler((t, e) -> {
					log.error("Scheduler thread " + t.getName() + " encounter an uncaught exception.", e);
				});
				thread.setDaemon(true);
				return thread;
			};

			log.info("Creating the default scheduled thread pool executor, CorePoolSize: {}, KeepAliveTime: 20s",
					corePoolSize);

			ScheduledThreadPoolExecutor s = new ScheduledThreadPoolExecutor(corePoolSize, factory, (r, e) -> {
				log.error("Scheduler rejected {} exception because the thread bounds and queue capacities are reached.",
						r.toString());
			});
			s.setKeepAliveTime(20, TimeUnit.SECONDS);
			s.allowCoreThreadTimeOut(true);
			defaultScheduler = s;
		}

		return defaultScheduler;
	}

	@Override
	public void bootstrap(NodeInfo node) throws KadException {
		checkArgument(node != null, "Invalid bootstrap node");

		bootstrap(Arrays.asList(node));
	}

	@Override
	public void bootstrap(Collection<NodeInfo> bootstrapNodes) throws KadException {
		checkArgument(bootstrapNodes != null, "Invalid bootstrap nodes");

		if (dht4 != null)
			dht4.bootstrap(bootstrapNodes);

		if (dht6 != null)
			dht6.bootstrap(bootstrapNodes);
	}

	@Override
	public synchronized void start() throws KadException {
		if (status != NodeStatus.Stopped)
			return;

		setStatus(NodeStatus.Stopped, NodeStatus.Initializing);
		log.info("Carrier node {} is starting...", id);

		try {
			networkEngine = new NetworkEngine();
			if (this.scheduler == null)
				this.scheduler = getDefaultScheduler();

			File dbFile = persistent ? new File(storagePath, "node.db") : null;
			storage = SQLiteStorage.open(dbFile, getScheduler());

			if (config.IPv4Address() != null) {
				InetSocketAddress addr4 = config.IPv4Address();

				if (!(addr4.getAddress() instanceof Inet4Address) ||
						!AddressUtils.isAnyUnicast(addr4.getAddress()))
					throw new IOError("Invalid DHT/IPv4 address: " + config.IPv4Address());

				dht4 = new DHT(DHT.Type.IPV4, this, addr4);
				if (persistent)
					dht4.enablePersistence(new File(storagePath, "dht4.cache"));

				dht4.start(config.bootstrapNodes() != null ? config.bootstrapNodes() : Collections.emptyList());
				numDHTs++;
			}

			if (config.IPv6Address() != null) {
				InetSocketAddress addr6 = config.IPv4Address();

				if (!(addr6.getAddress() instanceof Inet6Address) ||
						!AddressUtils.isAnyUnicast(addr6.getAddress()))
					throw new IOError("Invalid DHT/IPv6 address: " + config.IPv6Address());

				dht6 = new DHT(DHT.Type.IPV6, this, addr6);
				if (persistent)
					dht6.enablePersistence(new File(storagePath, "dht6.cache"));

				dht6.start(config.bootstrapNodes() != null ? config.bootstrapNodes() : Collections.emptyList());
				numDHTs++;
			}

			setStatus(NodeStatus.Initializing, NodeStatus.Running);
			log.info("Carrier Kademlia node {} started", id);
		} catch (KadException e) {
			setStatus(NodeStatus.Initializing, NodeStatus.Stopped);
			throw e;
		}

		scheduledActions.add(getScheduler().scheduleWithFixedDelay(() -> {
			persistentAnnounce();
		}, 60000, Constants.RE_ANNOUNCE_INTERVAL, TimeUnit.MILLISECONDS));
	}

	@Override
	public synchronized void stop() {
		if (status == NodeStatus.Stopped)
			return;

		log.info("Carrier Kademlia node {} is stopping...", id);

		// Cancel all scheduled actions
		for (ScheduledFuture<?> future : scheduledActions) {
			future.cancel(false);
			// none of the scheduled tasks should experience exceptions,
			// log them if they did
			try {
				future.get();
			} catch (ExecutionException e) {
				log.error("Scheduled future error", e);
			} catch (InterruptedException e) {
				log.error("Scheduled future error", e);
			} catch (CancellationException ignore) {
			}
		}

		scheduledActions.clear();

		if (dht4 != null) {
			dht4.stop();
			dht4 = null;
		}

		if (dht6 != null) {
			dht6.stop();
			dht6 = null;
		}

		networkEngine = null;
		try {
			storage.close();
		} catch (Exception e) {
			log.error("Close data storage failed", e);
		}

		storage = null;

		setStatus(NodeStatus.Running, NodeStatus.Stopped);
		log.info("Carrier Kademlia node {} stopped", id);
	}

	/**
	 * @return the status
	 */
	@Override
	public NodeStatus getStatus() {
		return status;
	}

	@Override
	public boolean isRunning() {
		return status == NodeStatus.Running;
	}

	private void persistentAnnounce() {
		log.info("Re-announce the persistent values and peers...");

		long ts = System.currentTimeMillis() - Constants.MAX_VALUE_AGE +
				Constants.RE_ANNOUNCE_INTERVAL * 2;
		Stream<Value> vs;
		try {
			vs = storage.getPersistentValues(ts);

			vs.forEach((v) -> {
				log.debug("Re-announce the value: {}", v.getId());

				try {
					storage.updateValueLastAnnounce(v.getId());
				} catch (Exception e) {
					log.error("Can not update last announce timestamp for value", e);
				}

				doStoreValue(v).whenComplete((na, e) -> {
					if (e == null)
						log.debug("Re-announce the value {} success", v.getId());
					else
						log.error("Re-announce the value " + v.getId() + " failed", e);
				});
			});
		} catch (KadException e) {
			log.error("Can not read the persistent values", e);
		}

		ts = System.currentTimeMillis() - Constants.MAX_PEER_AGE +
				Constants.RE_ANNOUNCE_INTERVAL * 2;
		try {
			Stream<PeerInfo> ps = storage.getPersistentPeers(ts);

			ps.forEach((p) -> {
				log.debug("Re-announce the peer: {}", p.getId());

				try {
					storage.updatePeerLastAnnounce(p.getId(), p.getOrigin());
				} catch (Exception e) {
					log.error("Can not update last announce timestamp for peer", e);
				}

				doAnnouncePeer(p).whenComplete((na, e) -> {
					if (e == null)
						log.debug("Re-announce the peer {} success", p.getId());
					else
						log.error("Re-announce the peer " + p.getId() + " failed", e);
				});
			});
		} catch (KadException e) {
			log.error("Can not read the persistent peers", e);
		}
	}

	NetworkEngine getNetworkEngine() {
		return networkEngine;
	}

	DHT getDHT(Type type) {
		return type == Type.IPV4 ? dht4 : dht6;
	}

	public DataStorage getStorage() {
		return storage;
	}

	TokenManager getTokenManager() {
		return tokenMan;
	}

	Blacklist getBlacklist() {
		return blacklist;
	}

	@Override
	public byte[] encrypt(Id recipient, byte[] data) throws CryptoError {
		try {
			CryptoContext ctx = cryptoContexts.get(recipient);
			return ctx.encrypt(data);
		} catch (ExecutionException e) {
			throw new CryptoError("can not create the encryption context", e.getCause());
		}
	}

	@Override
	public byte[] decrypt(Id sender, byte[] data) throws CryptoError {
		try {
			CryptoContext ctx = cryptoContexts.get(sender);
			return ctx.decrypt(data);
		} catch (ExecutionException e) {
			throw new CryptoError("can not create the encryption context", e.getCause());
		}
	}

	@Override
	public byte[] sign(byte[] data) throws CarrierException {
		return Signature.sign(data, keyPair.privateKey());
	}

	@Override
	public boolean verify(byte[] data, byte[] signature) throws CarrierException {
		return Signature.verify(data, signature, keyPair.publicKey());
	}

	@Override
	public CompletableFuture<List<NodeInfo>> findNode(Id id, LookupOption option) {
		checkState(isRunning(), "Node not running");
		checkArgument(id != null, "Invalid node id");

		LookupOption lookupOption = option == null ? defaultLookupOption : option;

		// 0: node4; 1: node6
		List<NodeInfo> results = new ArrayList<>(2);

		results.add(dht4 != null ? dht4.getNode(id) : null);
		results.add(dht6 != null ? dht6.getNode(id) : null);

		if (lookupOption == LookupOption.ARBITRARY &&
				(results.get(0) != null || results.get(1) != null)) {
			results.removeIf((ni) -> ni == null);
			return CompletableFuture.completedFuture(results);
		}

		TaskFuture<List<NodeInfo>> future = new TaskFuture<>();
		AtomicInteger completion = new AtomicInteger(0);

		Consumer<NodeInfo> completeHandler = (n) -> {
			int c = completion.incrementAndGet();

			if (n != null) {
				int index = n.getAddress().getAddress() instanceof Inet4Address ? 0 : 1;
				results.set(index, n);
			}


			if ((lookupOption == LookupOption.OPTIMISTIC && n != null) || c >= numDHTs) {
				results.removeIf((ni) -> ni == null);
				future.complete(results);
			}
		};

		Task t4 = null, t6 = null;
		if (dht4 != null) {
			t4 = dht4.findNode(id, option, completeHandler);
			future.addTask(t4);
		}

		if (dht6 != null) {
			t6 = dht6.findNode(id, option, completeHandler);
			future.addTask(t6);
		}

		return future;
	}

	@Override
	public CompletableFuture<Value> findValue(Id id, LookupOption option) {
		checkState(isRunning(), "Node not running");
		checkArgument(id != null, "Invalid value id");

		LookupOption lookupOption = option == null ? defaultLookupOption : option;

		Value local = null;
		try {
			local = getStorage().getValue(id);
			if (local != null && (lookupOption == LookupOption.ARBITRARY || !local.isMutable()))
				return CompletableFuture.completedFuture(local);
		} catch (KadException e) {
			return CompletableFuture.failedFuture(e);
		}

		TaskFuture<Value> future = new TaskFuture<>();
		AtomicInteger completion = new AtomicInteger(0);
		AtomicReference<Value> valueRef = new AtomicReference<>(local);

		// TODO: improve the value handler
		Consumer<Value> completeHandler = (v) -> {
			int c = completion.incrementAndGet();

			if (v != null) {
				synchronized(valueRef) {
					if (valueRef.get() == null) {
						valueRef.set(v);
					} else {
						if (!v.isMutable() || (v.isMutable() && valueRef.get().getSequenceNumber() < v.getSequenceNumber()))
							valueRef.set(v);
					}
				}
			}

			if ((lookupOption == LookupOption.OPTIMISTIC && v != null) || c >= numDHTs) {
				Value value = valueRef.get();
				if (value != null) {
					try {
						getStorage().putValue(value);
					} catch (KadException ignore) {
						log.error("Save value " + id + " failed", ignore);
					}
				}

				future.complete(value);
			}
		};

		Task t4 = null, t6 = null;
		if (dht4 != null) {
			t4 = dht4.findValue(id, lookupOption, completeHandler);
			future.addTask(t4);
		}

		if (dht6 != null) {
			t6 = dht6.findValue(id, lookupOption, completeHandler);
			future.addTask(t6);
		}

		return future;
	}

	@Override
	public CompletableFuture<Void> storeValue(Value value, boolean persistent) {
		checkState(isRunning(), "Node not running");
		checkArgument(value != null, "Invalid value: null");
		checkArgument(value.isValid(), "Invalid value");

		try {
			getStorage().putValue(value, persistent);
		} catch(KadException e) {
			return CompletableFuture.failedFuture(e);
		}

		return doStoreValue(value);
	}

	private CompletableFuture<Void> doStoreValue(Value value) {
		TaskFuture<Void> future = new TaskFuture<>();
		AtomicInteger completion = new AtomicInteger(0);

		// TODO: improve the complete handler, check the announced nodes
		Consumer<List<NodeInfo>> completeHandler = (nl) -> {
			if (completion.incrementAndGet() >= numDHTs)
				future.complete(null);
		};

		Task t4 = null, t6 = null;
		if (dht4 != null) {
			t4 = dht4.storeValue(value, completeHandler);
			future.addTask(t4);
		}

		if (dht6 != null) {
			t6 = dht6.storeValue(value, completeHandler);
			future.addTask(t6);
		}

		return future;
	}

	@Override
	public CompletableFuture<List<PeerInfo>> findPeer(Id id, int expected, LookupOption option) {
		checkState(isRunning(), "Node not running");
		checkArgument(id != null, "Invalid peer id");

		LookupOption lookupOption = option == null ? defaultLookupOption : option;

		List<PeerInfo> local;
		try {
			local = getStorage().getPeer(id, expected);
			if (((expected <= 0 && local.size() > 0) || (expected > 0 && local.size() >= expected)) &&
					lookupOption == LookupOption.ARBITRARY)
				return CompletableFuture.completedFuture(local);
		} catch (KadException e) {
			return CompletableFuture.failedFuture(e);
		}

		TaskFuture<List<PeerInfo>> future = new TaskFuture<>();
		AtomicInteger completion = new AtomicInteger(0);

		Set<PeerInfo> results = ConcurrentHashMap.newKeySet();
		results.addAll(local);

		// TODO: improve the value handler
		Consumer<Collection<PeerInfo>> completeHandler = (ps) -> {
			int c = completion.incrementAndGet();

			results.addAll(ps);

			try {
				getStorage().putPeer(ps);
			} catch (KadException ignore) {
				log.error("Save peer " + id + " failed", ignore);
			}

			if (c >= numDHTs) {
				ArrayList<PeerInfo> list = new ArrayList<>(results);
				Collections.shuffle(list);
				future.complete(list);
			}
		};

		Task t4 = null, t6 = null;
		if (dht4 != null) {
			t4 = dht4.findPeer(id, expected, lookupOption, completeHandler);
			future.addTask(t4);
		}

		if (dht6 != null) {
			t6 = dht6.findPeer(id, expected, lookupOption, completeHandler);
			future.addTask(t6);
		}

		return future;
	}

	@Override
	public CompletableFuture<Void> announcePeer(PeerInfo peer, boolean persistent) {
		checkState(isRunning(), "Node not running");
		checkArgument(peer != null, "Invalid peer: null");
		checkArgument(peer.getOrigin().equals(getId()), "Invaid peer: not belongs to current node");
		checkArgument(peer.isValid(), "Invalid peer");

		try {
			getStorage().putPeer(peer, persistent);
		} catch(KadException e) {
			return CompletableFuture.failedFuture(e);
		}

		return doAnnouncePeer(peer);
	}

	private CompletableFuture<Void> doAnnouncePeer(PeerInfo peer) {
		TaskFuture<Void> future = new TaskFuture<>();
		AtomicInteger completion = new AtomicInteger(0);

		// TODO: improve the complete handler, check the announced nodes
		Consumer<List<NodeInfo>> completeHandler = (nl) -> {
			if (completion.incrementAndGet() >= numDHTs)
				future.complete(null);
		};

		Task t4 = null, t6 = null;
		if (dht4 != null) {
			t4 = dht4.announcePeer(peer, completeHandler);
			future.addTask(t4);
		}

		if (dht6 != null) {
			t6 = dht6.announcePeer(peer, completeHandler);
			future.addTask(t6);
		}

		return future;
	}

	@Override
	public Value getValue(Id valueId) throws KadException {
		checkArgument(valueId != null, "Invalid value id");

		return getStorage().getValue(valueId);
	}

	@Override
	public boolean removeValue(Id valueId) throws KadException {
		checkArgument(valueId != null, "Invalid value id");

		return getStorage().removeValue(valueId);
	}

	@Override
	public PeerInfo getPeer(Id peerId) throws KadException {
		checkArgument(peerId != null, "Invalid peer id");

		return getStorage().getPeer(peerId, this.getId());
	}

	@Override
	public boolean removePeer(Id peerId) throws KadException {
		checkArgument(peerId != null, "Invalid peer id");

		return getStorage().removePeer(peerId, this.getId());
	}

	@Override
	public String toString() {
		StringBuilder repr = new StringBuilder(10240);

		repr.append("Node: ").append(id);
		repr.append('\n');
		if (dht4 != null)
			repr.append(dht4);
		if (dht6 != null)
			repr.append(dht6);

		return repr.toString();
	}
}
