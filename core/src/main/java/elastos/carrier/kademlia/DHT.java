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

package elastos.carrier.kademlia;

import java.io.File;
import java.io.IOException;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ProtocolFamily;
import java.net.StandardProtocolFamily;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;

import elastos.carrier.kademlia.exceptions.KadException;
import elastos.carrier.kademlia.messages.AnnouncePeerRequest;
import elastos.carrier.kademlia.messages.AnnouncePeerResponse;
import elastos.carrier.kademlia.messages.ErrorMessage;
import elastos.carrier.kademlia.messages.FindNodeRequest;
import elastos.carrier.kademlia.messages.FindNodeResponse;
import elastos.carrier.kademlia.messages.FindPeersRequest;
import elastos.carrier.kademlia.messages.FindPeersResponse;
import elastos.carrier.kademlia.messages.FindValueRequest;
import elastos.carrier.kademlia.messages.FindValueResponse;
import elastos.carrier.kademlia.messages.LookupResponse;
import elastos.carrier.kademlia.messages.Message;
import elastos.carrier.kademlia.messages.PingRequest;
import elastos.carrier.kademlia.messages.PingResponse;
import elastos.carrier.kademlia.messages.StoreValueRequest;
import elastos.carrier.kademlia.messages.StoreValueResponse;
import elastos.carrier.kademlia.tasks.ClosestSet;
import elastos.carrier.kademlia.tasks.NodeLookup;
import elastos.carrier.kademlia.tasks.PeerAnnounce;
import elastos.carrier.kademlia.tasks.PeerLookup;
import elastos.carrier.kademlia.tasks.PingRefreshTask;
import elastos.carrier.kademlia.tasks.Task;
import elastos.carrier.kademlia.tasks.TaskManager;
import elastos.carrier.kademlia.tasks.ValueAnnounce;
import elastos.carrier.kademlia.tasks.ValueLookup;
import elastos.carrier.utils.AddressUtils;

public class DHT {
	private Type type;

	private Node node;
	private InetSocketAddress addr;
	private RPCServer server;

	private boolean running;
	private List<ScheduledFuture<?>> scheduledActions = new ArrayList<>();

	private File persistFile;

	private List<NodeInfo> bootstrapNodes;
	private long lastBootstrap;

	private RoutingTable routingTable;
	private long lastSave;
	private volatile Cache<InetSocketAddress, Id> knownNodes;

	private TaskManager taskMan;

	private static final Logger log = LoggerFactory.getLogger(DHT.class);

	public static enum Type {
		IPV4("IPv4", StandardProtocolFamily.INET, Inet4Address.class, 20 + 8, 1450),
		IPV6("IPv6", StandardProtocolFamily.INET6, Inet6Address.class, 40 + 8, 1200);

		private final ProtocolFamily protocolFamily;
		private final Class<? extends InetAddress> preferredAddressType;
		private final int protocolHeaderSize;
		private final int maxPacketSize;
		private final String shortName;

		private Type(String shortName, ProtocolFamily family, Class<? extends InetAddress> addresstype, int header,
				int maxSize) {
			this.shortName = shortName;
			this.protocolFamily = family;
			this.preferredAddressType = addresstype;
			this.protocolHeaderSize = header;
			this.maxPacketSize = maxSize;
		}

		public boolean canUseSocketAddress(InetSocketAddress addr) {
			return preferredAddressType.isInstance(addr.getAddress());
		}

		public boolean canUseAddress(InetAddress addr) {
			return preferredAddressType.isInstance(addr);
		}

		public static Type of(InetSocketAddress addr) {
			return (addr.getAddress() instanceof Inet4Address) ? IPV4 : IPV6;
		}

		ProtocolFamily protocolFamily() {
			return protocolFamily;
		}

		public int protocolHeaderSize() {
			return protocolHeaderSize;
		}

		public int maxPacketSize() {
			return maxPacketSize;
		}

		@Override
		public String toString() {
			return shortName;
		}
	}

	/**
	 * @param srv
	 */
	public DHT(Type type, Node node, InetSocketAddress addr) {
		this.type = type;
		this.node = node;
		this.addr = addr;
		this.scheduledActions = new ArrayList<>();
		this.routingTable = new RoutingTable(this);
		this.bootstrapNodes = new ArrayList<>();

		this.knownNodes = CacheBuilder.newBuilder().initialCapacity(256).expireAfterAccess(5, TimeUnit.MINUTES)
				.concurrencyLevel(4).build();

		this.taskMan = new TaskManager(this);
	}

	public Type getType() {
		return type;
	}

	public Node getNode() {
		return node;
	}

	public NodeInfo getNode(Id nodeId) {
		return routingTable.getEntry(nodeId, true);
	}

	public RoutingTable getRoutingTable() {
		return routingTable;
	}

	TaskManager getTaskManager() {
		return taskMan;
	}

	void setRPCServer(RPCServer server) {
		this.server = server;
	}

	public RPCServer getServer() {
		return server;
	}

	void enablePersistence(File persistFile) {
		this.persistFile = persistFile;
	}

	public void bootstrap() {
		List<CompletableFuture<List<NodeInfo>>> futures = new ArrayList<>(bootstrapNodes.size());

		for (NodeInfo node : bootstrapNodes) {
			CompletableFuture<List<NodeInfo>> future = new CompletableFuture<>();

			FindNodeRequest request = new FindNodeRequest(Id.random());
			RPCCall call = new RPCCall(node, request).addListener(new RPCCallListener() {
				@Override
				public void onStateChange(RPCCall c, RPCCall.State previous, RPCCall.State current) {
					if (current == RPCCall.State.RESPONDED || current == RPCCall.State.ERROR
							|| current == RPCCall.State.TIMEOUT) {
						if (c.getResponse() instanceof FindNodeResponse) {
							FindNodeResponse r = (FindNodeResponse) c.getResponse();
							future.complete(r.getNodes(getType()));
						} else {
							future.complete(Collections.emptyList());
						}
					}
				}
			});

			futures.add(future);
			getServer().sendCall(call);
		}

		CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).thenAccept((x) -> {
			Set<NodeInfo> nodes = futures.stream().map(f -> {
				List<NodeInfo> l;
				try {
					l = f.get();
				} catch (Exception e) {
					l = Collections.emptyList();
				}
				return l;
			}).flatMap(l -> l.stream()).collect(Collectors.toSet());

			lastBootstrap = System.currentTimeMillis();
			routingTable.fillHomeBucket(nodes);
		});
	}

	public void bootstrap(NodeInfo bootstrapNode) {
		if (!type.canUseAddress(bootstrapNode.getInetAddress()))
			return;

		this.bootstrapNodes.add(bootstrapNode);

		CompletableFuture<List<NodeInfo>> future = new CompletableFuture<>();

		FindNodeRequest request = new FindNodeRequest(Id.random());
		RPCCall call = new RPCCall(bootstrapNode, request).addListener(new RPCCallListener() {
			@Override
			public void onStateChange(RPCCall c, RPCCall.State previous, RPCCall.State current) {
				if (current == RPCCall.State.RESPONDED || current == RPCCall.State.ERROR
						|| current == RPCCall.State.TIMEOUT) {
					if (c.getResponse() instanceof FindNodeResponse) {
						FindNodeResponse r = (FindNodeResponse) c.getResponse();
						future.complete(r.getNodes(getType()));
					} else {
						future.complete(Collections.emptyList());
					}
				}
			}
		});

		getServer().sendCall(call);

		future.thenAccept(l -> {
			lastBootstrap = System.currentTimeMillis();
			routingTable.fillHomeBucket(l);
		});
	}

	private void update () {
		if (!isRunning())
			return;

		log.trace("DHT {} regularly update...", type);

		long now = System.currentTimeMillis();

		server.checkReachability(now);
		routingTable.maintenance();

		if (routingTable.getNumBucketEntries() < Constants.BOOTSTRAP_IF_LESS_THAN_X_PEERS ||
				now - lastBootstrap > Constants.SELF_LOOKUP_INTERVAL)
			// Regularly search for our id to update routing table
			bootstrap();

		if (persistFile != null && now - lastSave > Constants.ROUTING_TABLE_PERSIST_INTERVAL) {
			try {
				log.info("Persisting routing table ...");
				routingTable.save(persistFile);
				lastSave = now;
			} catch (IOException e) {
				log.error("Can not save the routing table", e);
			}
		}
	}

	public synchronized void start(Collection<NodeInfo> bootstrapNodes) throws KadException {
		if (running)
			return;

		if (persistFile != null && persistFile.exists() && persistFile.isFile()) {
			log.info("Loading routing table from {} ...", persistFile);
			routingTable.load(persistFile);
			// fix the first time to persist the routing table: 2 min
			lastSave = System.currentTimeMillis() - Constants.ROUTING_TABLE_PERSIST_INTERVAL + 120 * 1000;
		}

		Set<NodeInfo> bns = bootstrapNodes.stream().filter(n -> type.canUseAddress(n.getInetAddress()))
				.collect(Collectors.toSet());
		this.bootstrapNodes.addAll(bns);

		log.info("Starting DHT/{} on {}", type, AddressUtils.toString(addr));

		server = new RPCServer(this, addr);
		server.start();

		scheduledActions.add(getNode().getScheduler().scheduleWithFixedDelay(() -> {
			// tasks maintenance that should run all the time, before the first queries
			taskMan.dequeue();
		}, 5000, Constants.DHT_UPDATE_INTERVAL, TimeUnit.MILLISECONDS));

		// Ping check if the routing table loaded from cache
		for (KBucket bucket : routingTable.buckets()) {
			Task task = new PingRefreshTask(this, bucket, EnumSet.of(PingRefreshTask.Options.removeOnTimeout));
			task.setName("Bootstrap cached table ping for " + bucket.prefix());
			taskMan.add(task);
		}

		bootstrap();

		// Regularly DHT update
		scheduledActions.add(getNode().getScheduler().scheduleWithFixedDelay(() -> {
			try {
				update();
			} catch (Exception e) {
				log.error("Regularly DHT update failed", e);
			}
		}, 5000, Constants.DHT_UPDATE_INTERVAL, TimeUnit.MILLISECONDS));

		// send a ping request to a random node to check socket liveness
		scheduledActions.add(getNode().getScheduler().scheduleWithFixedDelay(() -> {
			if (server.getNumberOfActiveRPCCalls() > 0)
				return;

			KBucketEntry entry = routingTable.getRandomEntry();
			if (entry == null)
				return;

			PingRequest q = new PingRequest();
			RPCCall c = new RPCCall(entry, q);
			server.sendCall(c);
		}, Constants.RANDOM_PING_INTERVAL, Constants.RANDOM_PING_INTERVAL, TimeUnit.MILLISECONDS));

		// deep lookup to make ourselves known to random parts of the keyspace
		scheduledActions.add(getNode().getScheduler().scheduleWithFixedDelay(() -> {
			NodeLookup task = new NodeLookup(this, Id.random(), false);
			task.setName(type + ":Random Refresh Lookup");
			taskMan.add(task);
		}, Constants.RANDOM_LOOKUP_INTERVAL, Constants.RANDOM_LOOKUP_INTERVAL, TimeUnit.MILLISECONDS));

		running = true;
	}

	public void stop() {
		if (!running)
			return;

		log.info("{} initated DHT shutdown...", type);

		// Cancel the search tasks
		// Stream.concat(Arrays.stream(tman.getActiveTasks()),
		// Arrays.stream(tman.getQueuedTasks())).forEach(Task::kill);

		log.info("stopping servers");
		running = false;
		server.stop();

		// Cancel all scheduled actions
		for (ScheduledFuture<?> future : scheduledActions) {
			future.cancel(false);
			// none of the scheduled tasks should experience exceptions, log them if they
			// did
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

		if (persistFile != null) {
			try {
				log.info("Persisting routing table on shutdown...");
				routingTable.save(persistFile);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}

		taskMan.cancleAll();
	}

	public boolean isRunning() {
		return running;
	}

	protected void onMessage(Message msg) {
		if (!isRunning())
			return;

		// ignore the messages we get from ourself
		if (node.isLocalId(msg.getId()))
			return;

		switch (msg.getType()) {
		case REQUEST:
			onRequest(msg);
			break;

		case RESPONSE:
			onResponse(msg);
			break;

		case ERROR:
			onError((ErrorMessage) msg);
			break;
		}

		received(msg);
	}

	private void onRequest(Message msg) {
		switch (msg.getMethod()) {
		case PING:
			onPing((PingRequest) msg);
			break;

		case FIND_NODE:
			onFindNode((FindNodeRequest) msg);
			break;

		case FIND_VALUE:
			onFindValue((FindValueRequest) msg);
			break;

		case STORE_VALUE:
			onStoreValue((StoreValueRequest) msg);
			break;

		case FIND_PEERS:
			onFindPeers((FindPeersRequest) msg);
			break;

		case ANNOUNCE_PEER:
			onAnnouncePeer((AnnouncePeerRequest) msg);
			break;

		case UNKNOWN:
			sendError(msg, ErrorCode.ProtocolError.value(), "Invalid request method");
			break;
		}
	}

	private void onPing(PingRequest q) {
		PingResponse r = new PingResponse(q.getTxid());
		r.setRemote(r.getOrigin());
		server.sendMessage(r);
	}

	private void onFindNode(FindNodeRequest q) {
		FindNodeResponse r = new FindNodeResponse(q.getTxid());

		int want4 = q.doesWant4() ? Constants.MAX_ENTRIES_PER_BUCKET : 0;
		int want6 = q.doesWant6() ? Constants.MAX_ENTRIES_PER_BUCKET : 0;
		populateClosestNodes(r, q.getTarget(), want4, want6);
		r.setRemote(q.getOrigin());
		server.sendMessage(r);
	}

	private void onFindValue(FindValueRequest q) {
		DataStorage storage = getNode().getStorage();

		Id target = q.getTarget();
		FindValueResponse r = new FindValueResponse(q.getTxid());
		r.setToken(getNode().getTokenManager().generateToken(q.getId(), q.getOrigin(), target));

		Value v = storage.getValue(target);
		if (v != null) {
			if (q.getSequenceNumber() < 0 || v.getSequenceNumber() < 0
					|| q.getSequenceNumber() < v.getSequenceNumber()) {
				r.setValue(v.getData());
				r.setPublicKey(v.getPublicKey());
				r.setSignature(v.getSignature());
				if (v.getSequenceNumber() >= 0)
					r.setSequenceNumber(v.getSequenceNumber());
			}
		} else {
			int want4 = q.doesWant4() ? Constants.MAX_ENTRIES_PER_BUCKET : 0;
			int want6 = q.doesWant6() ? Constants.MAX_ENTRIES_PER_BUCKET : 0;
			populateClosestNodes(r, target, want4, want6);
		}

		r.setRemote(q.getOrigin());
		server.sendMessage(r);
	}

	private void onStoreValue(StoreValueRequest q) {
		DataStorage storage = getNode().getStorage();

		Id valueId = q.getValueId();
		if (!getNode().getTokenManager().verifyToken(q.getToken(), q.getId(), q.getOrigin(), valueId)) {
			log.warn("Received a store value request with invalid token from {}", AddressUtils.toString(q.getOrigin()));
			sendError(q, ErrorCode.ProtocolError.value(), "Invalid token for STORE VALUE request");
			return;
		}

		try {
			storage.putValue(valueId, Value.of(q), q.getExpectedSequenceNumber());
			StoreValueResponse r = new StoreValueResponse(q.getTxid());
			r.setRemote(q.getOrigin());
			server.sendMessage(r);
		} catch (KadException e) {
			sendError(q, e.getCode(), e.getMessage());
		}
	}

	private void onFindPeers(FindPeersRequest q) {
		DataStorage storage = getNode().getStorage();

		Id target = q.getTarget();
		FindPeersResponse r = new FindPeersResponse(q.getTxid());
		r.setToken(getNode().getTokenManager().generateToken(q.getId(), q.getOrigin(), target));

		// TODO: check the max peers
		List<PeerInfo> peers = storage.getPeers(target, q.doesWant4(), q.doesWant6(), 16);
		if (!peers.isEmpty()) {
			r.setPeers(peers);
		} else {
			int want4 = q.doesWant4() ? Constants.MAX_ENTRIES_PER_BUCKET : 0;
			int want6 = q.doesWant6() ? Constants.MAX_ENTRIES_PER_BUCKET : 0;
			populateClosestNodes(r, q.getTarget(), want4, want6);
		}

		r.setRemote(q.getOrigin());
		server.sendMessage(r);
	}

	private void onAnnouncePeer(AnnouncePeerRequest q) {
		DataStorage storage = getNode().getStorage();

		if (!getNode().getTokenManager().verifyToken(q.getToken(), q.getId(), q.getOrigin(), q.getTarget())) {
			log.warn("Received an announce peer request with invalid token from {}",
					AddressUtils.toString(q.getOrigin()));
			sendError(q, ErrorCode.ProtocolError.value(), "Invalid token for ANNOUNCE PEER request");
			return;
		}

		if (!AddressUtils.isBogon(q.getOrigin().getAddress(), q.getPort())) {
			log.debug("Received an announce peer request from bogon address {}, ignored ",
					AddressUtils.toString(q.getOrigin()));
			return;
		}

		PeerInfo peer = new PeerInfo(q.getId(), q.getOrigin().getAddress(), q.getPort());

		try {
			log.debug("Received an announce peer request from {}, saving peer {}", AddressUtils.toString(q.getOrigin()),
					q.getTarget());

			storage.putPeer(q.getTarget(), peer);
			AnnouncePeerResponse r = new AnnouncePeerResponse(q.getTxid());
			r.setRemote(q.getOrigin());
			server.sendMessage(r);
		} catch (KadException e) {
			sendError(q, e.getCode(), e.getMessage());
		}
	}

	private void onResponse(Message r) {
		// Nothing to do
	}

	private void onError(ErrorMessage e) {
		log.warn("Error from {}/{} - {}:{}, txid {}", AddressUtils.toString(e.getOrigin()), e.getReadableVersion(),
				e.getCode(), e.getMessage(), e.getTxid());
	}

	/**
	 * Increase the failed queries count of the bucket entry we sent the message to
	 */
	protected void onTimeout(RPCCall call) {
		// ignore the timeout if the DHT is stopped or the RPC server is offline
		if (!isRunning() || !server.isReachable())
			return;

		Id nodeId = call.getTargetId();
		routingTable.onTimeout(nodeId);
	}

	protected void onSend(RPCCall call) {
		if (!isRunning())
			return;

		Id nodeId = call.getTargetId();
		routingTable.onSend(nodeId);
	}

	private void sendError(Message q, int code, String msg) {
		ErrorMessage em = new ErrorMessage(q.getMethod(), q.getTxid(), code, msg);
		em.setRemote(q.getOrigin());
		server.sendMessage(em);
	}

	void received(Message msg) {
		InetSocketAddress addr = msg.getOrigin();
		if (AddressUtils.isBogon(addr))
			return;

		Id id = msg.getId();
		RPCCall call = msg.getAssociatedCall();

		// we only want remote nodes with stable ports in our routing table,
		// so apply a stricter check here
		if (call != null && (!call.matchesAddress() || !call.matchesId()))
			return;

		KBucketEntry old = routingTable.getEntry(id, true);
		if (old != null && !old.getAddress().equals(addr)) {
			// this might happen if one node changes ports (broken NAT?) or IP address
			// ignore until routing table entry times out
			return;
		}

		Id knownId = knownNodes.getIfPresent(addr);
		if (knownId != null && !knownId.equals(id)) {
			KBucketEntry knownEntry = routingTable.getEntry(knownId, true);
			if (knownEntry != null) {
				// 1. a node with that address is in our routing table
				// 2. the ID does not match our routing table entry
				//
				// That means we are certain that the node either changed its
				// node ID or does some ID-spoofing.
				// In either case we don't want it in our routing table
				log.warn("force-removing routing table entry {} because ID-change was detected; new ID {}", knownEntry,
						id);
				routingTable.remove(knownId);

				// might be pollution attack, check other entries in the same bucket too in case
				// random
				// pings can't keep up with scrubbing.
				KBucket bucket = routingTable.bucketOf(knownId);
				routingTable.tryPingMaintenance(bucket, EnumSet.of(PingRefreshTask.Options.checkAll),
						"Checking bucket " + bucket.prefix() + " after ID change was detected");
				knownNodes.put(addr, id);
				return;
			} else {
				knownNodes.invalidate(addr);
			}
		}

		knownNodes.put(addr, id);
		KBucketEntry newEntry = new KBucketEntry(id, addr);
		newEntry.setVersion(msg.getVersion());

		if (call != null) {
			newEntry.signalResponse(call.getRTT());
			newEntry.mergeRequestTime(call.getSentTime());
		}

		routingTable.put(newEntry);


		// TODO: CHECKME!!!
		// we already should have the bucket. might be an old one by now due to
		// splitting
		// but it doesn't matter, we just need to update the entry, which should stay
		// the same object across bucket splits
		// if(msg.getType() == Message.Type.RESPONSE) {
		// bucket.notifyOfResponse(msg);
		// }

	}

	private void populateClosestNodes(LookupResponse r, Id target, int v4, int v6) {
		if (v4 > 0) {
			DHT dht4 = type == Type.IPV4 ? this : getNode().getDHT(Type.IPV4);
			if (dht4 != null) {
				KClosestNodes kns = new KClosestNodes(dht4, target, v4);
				kns.fill(this == dht4);
				r.setNodes4(kns.asNodeList());
			}
		}

		if (v6 > 0) {
			DHT dht6 = type == Type.IPV6 ? this : getNode().getDHT(Type.IPV6);
			if (dht6 != null) {
				KClosestNodes kns = new KClosestNodes(dht6, target, v6);
				kns.fill(this == dht6);
				r.setNodes4(kns.asNodeList());
			}
		}
	}

	public Task findNode(Id id, Consumer<NodeInfo> completeHandler) {
		NodeLookup task = new NodeLookup(this, id);
		task.addListener(t -> {
			NodeInfo ni = routingTable.getEntry(id, true);
			completeHandler.accept(ni);
		});

		taskMan.add(task);
		return task;
	}

	public Task findValue(Id id, LookupOption option, Consumer<Value> completeHandler) {
		AtomicReference<Value> valueRef = new AtomicReference<>(null);
		ValueLookup task = new ValueLookup(this, id);
		task.setResultHandler((v) -> {
			if (valueRef.get() == null)
				valueRef.set(v);

			if (option == LookupOption.ARBITRARY) {
				task.cancel();
				return;
			}

			if (!v.isMutable() || (v.isMutable() && valueRef.get().getSequenceNumber() < v.getSequenceNumber()))
				valueRef.set(v);
		});

		task.addListener(t -> {
			completeHandler.accept(valueRef.get());
		});

		taskMan.add(task);
		return task;
	}

	public Task storeValue(Value value, Consumer<List<NodeInfo>> completeHandler) {
		ValueLookup lookup = new ValueLookup(this, value.getId());
		lookup.addListener(l -> {
			if (lookup.getState() != Task.State.FINISHED)
				return;

			ClosestSet closest = lookup.getClosestSet();
			if (closest == null || closest.size() == 0) {
				// this should never happen
				log.error(
						"!!! Value announce task not started because the value lookup task got the empty closest nodes.");
				completeHandler.accept(Collections.emptyList());
				return;
			}

			ValueAnnounce announce = new ValueAnnounce(this, closest, value);
			announce.addListener(a -> {
				completeHandler.accept(new ArrayList<>(closest.getEntries()));
			});

			lookup.setNestedTask(announce);
			taskMan.add(announce);
		});

		taskMan.add(lookup);
		return lookup;
	}

	public Task findPeers(Id id, int expected, LookupOption option, Consumer<List<PeerInfo>> completeHandler) {
		// NOTICE: Concurrent threads adding to ArrayList
		//
		// There is no guaranteed behavior for what happens when add is
		// called concurrently by two threads on ArrayList.
		// However, it has been my experience that both objects have been
		// added fine. Most of the thread safety issues related to lists
		// deal with iteration while adding/removing.
		List<PeerInfo> peers = new ArrayList<>();
		PeerLookup task = new PeerLookup(this, id);
		task.setReultHandler((p) -> {
			peers.add(p);

			if (option == LookupOption.ARBITRARY && peers.size() >= expected) {
				task.cancel();
				return;
			}
		});

		task.addListener(t -> {
			completeHandler.accept(peers);
		});

		taskMan.add(task);
		return task;
	}

	public Task announcePeer(Id peerId, int port, Consumer<List<NodeInfo>> completeHandler) {
		PeerLookup lookup = new PeerLookup(this, peerId);
		lookup.addListener(l -> {
			if (lookup.getState() != Task.State.FINISHED)
				return;

			ClosestSet closest = lookup.getClosestSet();
			if (closest == null || closest.size() == 0) {
				// this should never happen
				log.error(
						"!!! Peer announce task not started because the peer lookup task got the empty closest nodes.");
				completeHandler.accept(Collections.emptyList());
				return;
			}

			PeerAnnounce announce = new PeerAnnounce(this, closest, peerId, port);
			announce.addListener(a -> {
				completeHandler.accept(new ArrayList<>(closest.getEntries()));
			});

			lookup.setNestedTask(announce);
			taskMan.add(announce);
		});

		taskMan.add(lookup);
		return lookup;
	}

	@Override
	public String toString() {
		StringBuilder repr = new StringBuilder(10240);

		repr.append("DHT: ").append(type);
		repr.append('\n');
		repr.append("Address: ").append(AddressUtils.toString(server.getAddress()));
		repr.append('\n');
		repr.append(routingTable);

		return repr.toString();
	}
}
