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
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.cache.RemovalListener;
import com.google.common.cache.RemovalNotification;

import elastos.carrier.Configuration;
import elastos.carrier.Id;
import elastos.carrier.LookupOption;
import elastos.carrier.NodeInfo;
import elastos.carrier.NodeStatus;
import elastos.carrier.NodeStatusListener;
import elastos.carrier.PeerInfo;
import elastos.carrier.Value;
import elastos.carrier.crypto.CryptoBox;
import elastos.carrier.crypto.CryptoException;
import elastos.carrier.crypto.Signature;
import elastos.carrier.kademlia.DHT.Type;
import elastos.carrier.kademlia.exceptions.CryptoError;
import elastos.carrier.kademlia.exceptions.IOError;
import elastos.carrier.kademlia.exceptions.KadException;
import elastos.carrier.kademlia.exceptions.NotValueOwner;
import elastos.carrier.kademlia.exceptions.ValueNotExists;
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

	private NetworkEngine networkEngine;

	private DHT dht4;
	private DHT dht6;
	private int numDHTs;
	private LookupOption defaultLookupOption;

	private LoadingCache<Id, CryptoContext> cryptoContexts;

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

		statusListeners = new ArrayList<>(4);
		tokenMan = new TokenManager();

		setupCryptoBoxesCache();

		defaultLookupOption = LookupOption.CONSERVATIVE;
		status = NodeStatus.Stopped;

		this.config = config;
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
				log.error("Scheduler encounter a reject exception.", e);
			});
			// s.setCorePoolSize(corePoolSize);
			s.setKeepAliveTime(20, TimeUnit.SECONDS);
			s.allowCoreThreadTimeOut(true);
			defaultScheduler = s;
		}

		return defaultScheduler;
	}

	@Override
	public void bootstrap(NodeInfo node) throws KadException {
		checkArgument(node != null, "Invalid bootstrap node");

		if (dht4 != null)
			dht4.bootstrap(node);

		if (dht6 != null)
			dht6.bootstrap(node);
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
	}

	@Override
	public synchronized void stop() {
		if (status == NodeStatus.Stopped)
			return;

		log.info("Carrier Kademlia node {} is stopping...", id);

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
	public CompletableFuture<List<NodeInfo>> findNode(Id id, LookupOption option) {
		checkState(isRunning(), "Node not running");
		checkArgument(id != null, "Invalid node id");

		LookupOption lookupOption = option == null ? defaultLookupOption : option;

		List<NodeInfo> results = new ArrayList<NodeInfo>(2);

		if (lookupOption == LookupOption.ARBITRARY) {
			if (dht4 != null) {
				NodeInfo n = dht4.getNode(id);
				if (n != null)
					results.add(n);
			}

			if (dht6 != null) {
				NodeInfo n = dht6.getNode(id);
				if (n != null)
					results.add(n);
			}

			if (!results.isEmpty())
				return CompletableFuture.completedFuture(results);
		}

		TaskFuture<List<NodeInfo>> future = new TaskFuture<>();
		AtomicInteger completion = new AtomicInteger(0);

		Consumer<NodeInfo> completeHandler = (n) -> {
			int c = completion.incrementAndGet();

			if (n != null)
				results.add(n);

			if ((lookupOption == LookupOption.OPTIMISTIC && !results.isEmpty()) || c >= numDHTs)
				future.complete(results);
		};

		Task t4 = null, t6 = null;
		if (dht4 != null) {
			t4 = dht4.findNode(id, completeHandler);
			future.addTask(t4);
		}

		if (dht6 != null) {
			t6 = dht6.findNode(id, completeHandler);
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
	public CompletableFuture<Void> storeValue(Value value) {
		checkState(isRunning(), "Node not running");
		checkArgument(value != null, "Invalid value: null");

		try {
			getStorage().putValue(value);
		} catch(KadException e) {
			return CompletableFuture.failedFuture(e);
		}

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

		int family = 0;

		if (dht4 != null)
			family += 4;

		if (dht6 != null)
			family += 6;

		LookupOption lookupOption = option == null ? defaultLookupOption : option;

		List<PeerInfo> local;
		try {
			local = getStorage().getPeer(id, family, expected);
			if (expected > 0 && local.size() >= expected && lookupOption == LookupOption.ARBITRARY)
				return CompletableFuture.completedFuture(local);
		} catch (KadException e) {
			return CompletableFuture.failedFuture(e);
		}

		TaskFuture<List<PeerInfo>> future = new TaskFuture<>();
		AtomicInteger completion = new AtomicInteger(0);

		// NOTICE: Concurrent threads adding to ArrayList
		//
		// There is no guaranteed behavior for what happens when add is
		// called concurrently by two threads on ArrayList.
		// However, it has been my experience that both objects have been
		// added fine. Most of the thread safety issues related to lists
		// deal with iteration while adding/removing.
		HashSet<PeerInfo> results = new HashSet<>(local);

		// TODO: improve the value handler
		Consumer<Collection<PeerInfo>> completeHandler = (ps) -> {
			int c = completion.incrementAndGet();

			results.addAll(ps);

			try {
				getStorage().putPeer(id, ps);
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
	public CompletableFuture<Void> announcePeer(Id id, int port) {
		checkState(isRunning(), "Node not running");
		checkArgument(id != null, "Invalid peer id");
		checkArgument(port != 0, "Invaid peer port");

		// TODO: can not create a peer with local address.
		//       how to get the public peer address?
		/*
		PeerInfo peer4 = null, peer6 = null;
		if (dht4 != null)
			peer4 = new PeerInfo(getId(), dht4.getServer().getAddress().getAddress(), port);
		if (dht6 != null)
			peer6 = new PeerInfo(getId(), dht6.getServer().getAddress().getAddress(), port);

		try {
			if (peer4 != null)
				getStorage().putPeer(id, peer4);

			if (peer6 != null)
				getStorage().putPeer(id, peer6);
		} catch(KadException e) {
			return CompletableFuture.failedFuture(e);
		}
		*/

		TaskFuture<Void> future = new TaskFuture<>();
		AtomicInteger completion = new AtomicInteger(0);

		// TODO: improve the complete handler, check the announced nodes
		Consumer<List<NodeInfo>> completeHandler = (nl) -> {
			if (completion.incrementAndGet() >= numDHTs)
				future.complete(null);
		};

		Task t4 = null, t6 = null;
		if (dht4 != null) {
			t4 = dht4.announcePeer(id, port, completeHandler);
			future.addTask(t4);
		}

		if (dht6 != null) {
			t6 = dht6.announcePeer(id, port, completeHandler);
			future.addTask(t6);
		}

		return future;
	}

	@Override
	public Value createValue(byte[] data) {
		return Value.of(data);
	}

	@Override
	public Value createSignedValue(byte[] data) throws KadException {
		Signature.KeyPair kp = Signature.KeyPair.random();
		CryptoBox.Nonce nonce = CryptoBox.Nonce.random();

		try {
			return Value.of(kp, nonce, 0, data);
		} catch (CryptoException e) {
			log.error("INTERNAL ERROR: should never happen!!!");
			throw new CryptoError(e.getMessage(), e);
		}
	}

	@Override
	public Value createEncryptedValue(Id recipient, byte[] data) throws KadException {
		Signature.KeyPair kp = Signature.KeyPair.random();
		CryptoBox.Nonce nonce = CryptoBox.Nonce.random();

		try {
			return Value.of(kp, recipient, nonce, 0, data);
		} catch (CryptoException e) {
			log.error("INTERNAL ERROR: should never happen!!!");
			throw new CryptoError(e.getMessage(), e);
		}
	}

	@Override
	public Value updateValue(Id valueId, byte[] data) throws KadException {
		Value old = getStorage().getValue(valueId);
		if (old == null)
			throw new ValueNotExists("No exists value " + valueId);

		if (!old.hasPrivateKey())
			throw new NotValueOwner("Not the owner of the value " + valueId);

		Signature.KeyPair kp = Signature.KeyPair.fromPrivateKey(old.getPrivateKey());
		CryptoBox.Nonce nonce = CryptoBox.Nonce.fromBytes(old.getNonce());

		try {
			return Value.of(kp, old.getRecipient(), nonce, old.getSequenceNumber() + 1, data);
		} catch (CryptoException e) {
			log.error("INTERNAL ERROR: should never happen!!!");
			throw new CryptoError(e.getMessage(), e);
		}
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
