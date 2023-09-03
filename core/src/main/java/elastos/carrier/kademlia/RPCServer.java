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

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.StandardProtocolFamily;
import java.net.StandardSocketOptions;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Formatter;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import elastos.carrier.Id;
import elastos.carrier.crypto.CryptoBox;
import elastos.carrier.kademlia.NetworkEngine.Selectable;
import elastos.carrier.kademlia.exceptions.CryptoError;
import elastos.carrier.kademlia.exceptions.IOError;
import elastos.carrier.kademlia.messages.ErrorMessage;
import elastos.carrier.kademlia.messages.Message;
import elastos.carrier.kademlia.messages.MessageException;
import elastos.carrier.utils.AddressUtils;

public class RPCServer implements Selectable {
	private final static int WRITE_STATE_INITIAL = -1;
	private final static int WRITE_STATE_IDLE = 0;
	private final static int WRITE_STATE_WRITING = 1;
	private final static int WRITE_STATE_AWAITING = 2;
	private final static int WRITE_STATE_CLOSED = 3;

	private DHT dht;
	private InetSocketAddress addr;
	private DatagramChannel channel;

	private Instant startTime;
	private State state;
	private AtomicInteger writeState;

	private AtomicLong receivedMessages;
	private AtomicLong sentMessages;
	private volatile boolean isReachable;
	private long messagesAtLastReachableCheck;
	private long lastReachableCheck;

	private AtomicInteger callQueueGuard;
	private ConcurrentMap<Integer, RPCCall> calls;
	private ConcurrentLinkedQueue<RPCCall> callQueue;
	private ConcurrentLinkedQueue<Message> pipeline;

	private Throttle inboundThrottle;
	private Throttle outboundThrottle;
	private TimeoutSampler timeoutSampler;
	private RPCStatistics stats;
	private ExponentialWeightendMovingAverage unverifiedLossrate;
	private ExponentialWeightendMovingAverage verifiedEntryLossrate;

	private static final ThreadLocal<ByteBuffer> writeBuffer = ThreadLocal.withInitial(() -> ByteBuffer.allocateDirect(1500));
	private static final ThreadLocal<ByteBuffer> readBuffer = ThreadLocal.withInitial(() -> ByteBuffer.allocateDirect(Constants.RECEIVE_BUFFER_SIZE));

	private int nextTxid = ThreadLocalRandom.current().nextInt(1, 32768);

	private static final Logger log = LoggerFactory.getLogger(RPCServer.class);

	public enum State {
		INITIAL,
		RUNNING,
		STOPPED
	}

	public RPCServer(DHT dht, InetSocketAddress addr, boolean enableThrottle) {
		checkArgument(addr != null, "Invalid socket address");

		this.dht = dht;
		this.addr = addr;

		this.state = State.INITIAL;
		this.writeState = new AtomicInteger(WRITE_STATE_INITIAL);

		this.callQueueGuard = new AtomicInteger(0);
		this.calls = new ConcurrentHashMap<>(Constants.MAX_ACTIVE_CALLS);
		this.callQueue = new ConcurrentLinkedQueue<>();
		this.pipeline = new ConcurrentLinkedQueue<>();

		this.outboundThrottle = enableThrottle ? new Throttle.Eanbled() : new Throttle.Disabled();
		this.inboundThrottle = enableThrottle ? new Throttle.Eanbled() : new Throttle.Disabled();
		this.timeoutSampler = new TimeoutSampler();
		this.stats = new RPCStatistics();

		this.receivedMessages = new AtomicLong();
		this.sentMessages = new AtomicLong();

		this.unverifiedLossrate = new ExponentialWeightendMovingAverage(0.01, 0.5);
		this.verifiedEntryLossrate = new ExponentialWeightendMovingAverage(0.01, 0.5);

		dht.setRPCServer(this);
	}

	public RPCServer(DHT dht, InetSocketAddress addr) {
		this(dht, addr, true);
	}

	public RPCServer(DHT dht, InetAddress addr, int port, boolean enableThrottle) {
		this(dht, new InetSocketAddress(addr, port), enableThrottle);
	}

	public RPCServer(DHT dht, InetAddress addr, int port) {
		this(dht, addr, port, true);
	}

	public InetSocketAddress getAddress() {
		return addr;
	}

	public int getPort() {
		return addr.getPort();
	}

	public State getState() {
		return state;
	}

	public Node getNode() {
		return dht.getNode();
	}

	public DHT getDHT() {
		return dht;
	}

	public long getNumberOfReceivedMessages() {
		return receivedMessages.get();
	}

	/**
	 * @return the numSent
	 */
	public long getNumberOfSentMessages() {
		return sentMessages.get();
	}

	public int getNumberOfActiveRPCCalls() {
		return calls.size();
	}

	public RPCStatistics getStats() {
		return stats;
	}

	public boolean isReachable() {
		return isReachable;
	}

	public void checkReachability(long now) {
		// don't do pings too often if we're not receiving anything
		// (connection might be dead)
		if (receivedMessages.get() != messagesAtLastReachableCheck) {
			isReachable = true;
			lastReachableCheck = now;
			messagesAtLastReachableCheck = receivedMessages.get();
		} else if (now - lastReachableCheck > Constants.RPC_SERVER_REACHABILITY_TIMEOUT) {
			isReachable = false;
			timeoutSampler.reset();
		}
	}

	public synchronized void start() throws IOError {
		com.google.common.base.Preconditions.checkState(state == State.INITIAL, "already started");

		try {
			channel = DatagramChannel.open(StandardProtocolFamily.INET);
			channel.configureBlocking(false);
			channel.setOption(StandardSocketOptions.SO_RCVBUF, 2 * 1024 * 1024);
			channel.setOption(StandardSocketOptions.SO_REUSEADDR, true);
			channel.bind(addr);
		} catch (IOException e) {
			throw new IOError("Open and bing UDP socket error.", e);
		}

		writeState.set(WRITE_STATE_IDLE);
		state = State.RUNNING;
		startTime = Instant.now();

		getNode().getNetworkEngine().register(this);

		log.info("Started RPC server {}", AddressUtils.toString(addr));
	}

	public synchronized void stop() {
		if(state == State.STOPPED)
			return;

		state = State.STOPPED;
		writeState.set(WRITE_STATE_CLOSED);

		if (channel != null) {
			try {
				channel.close();
			} catch (IOException ignore) {
			}
		}

		Stream.of(calls.values().stream(), callQueue.stream(), pipeline.stream().map(msg -> msg.getAssociatedCall())
				.filter(Objects::nonNull)).flatMap(s -> s).forEach(r -> {
					r.cancel();
				});
		pipeline.clear();

		log.info("Stopped RPC Server {}", addr);
	}

	public void sendCall(RPCCall call) {
		//Message request = call.getRequest();
		// enqueueEventConsumers.forEach(callback -> callback.accept(c));
		callQueue.add(call);
		processCallQueue();
	}

	private void processCallQueue() {
		if(!callQueueGuard.compareAndSet(0, 1))
			return;

		outboundThrottle.decay();

		int capacity = Constants.MAX_ACTIVE_CALLS  - calls.size();
		while(capacity > 0) {
			RPCCall call = callQueue.poll();
			if(call == null) {
				/*
				Runnable r = awaitingDeclog.poll();
				if(r != null) {
					r.run();
					continue;
				}
				 */
				break;
			}

			int delay = outboundThrottle.estimateDeplayAndInc(call.getRequest().getRemoteAddress().getAddress());
			if(delay > 0) {
				delay += ThreadLocalRandom.current().nextInt(10, 50);
				log.info("Throttled(delay {}ms) the RPCCall to remote peer {}@{}, {}", delay,
						call.getTargetId(), AddressUtils.toString(call.getRequest().getRemoteAddress()), call.getRequest());
				getScheduler().schedule(() -> {
					callQueue.add(call);
					processCallQueue();
					outboundThrottle.saturatingDec(call.getRequest().getRemoteAddress().getAddress());
				}, delay, TimeUnit.MILLISECONDS);
				continue;
			}

			int txid = nextTxid++;
			if (txid == 0) // 0 is invalid txid, skip
				txid = nextTxid++;

			call.getRequest().setTxid(txid);

			if(calls.putIfAbsent(txid, call) == null) {
				capacity--;
				dispatchCall(call);
			} else {
				// Should never happen
				// Put the call back to the call queue
				callQueue.add(call);
				log.error("!!! Should never happen - put the call to call queue failed.");
			}
		}

		callQueueGuard.set(0);

		if(capacity > 0 && callQueue.peek() != null)
			getDHT().getNode().getScheduler().execute(this::processCallQueue);
	}

	private final RPCCallListener callListener = new RPCCallListener() {
		@Override
		public void onTimeout(RPCCall call) {
			calls.remove(call.getRequest().getTxid(), call);

			stats.timeoutMessage(call.getRequest());
			if(call.knownReachableAtCreationTime())
				verifiedEntryLossrate.updateAverage(1.0);
			else
				unverifiedLossrate.updateAverage(1.0);

			dht.onTimeout(call);
			processCallQueue();
		}

		@Override
		public void onResponse(RPCCall call, Message msg) {
			if(call.knownReachableAtCreationTime())
				verifiedEntryLossrate.updateAverage(0.0);
			else
				unverifiedLossrate.updateAverage(0.0);
		}
	};

	private void dispatchCall(RPCCall call) {
		Message msg = call.getRequest();
		assert(msg.getRemoteAddress() != null);

		call.addListener(callListener);

		// known nodes - routing table entries - keep track of their own RTTs
		// they are also biased towards lower RTTs compared to the general population encountered during regular lookups
		// don't let them skew the measurement of the general node population
		if(!call.knownReachableAtCreationTime())
			timeoutSampler.registerCall(call);

		long RTT = call.getExpectedRTT();
		if(RTT == -1) {
			RTT = timeoutSampler.getStallTimeout();
			call.setExpectedRTT(RTT);
		}

		msg.setAssociatedCall(call);
		fillPipeline(msg);
	}

	public void sendMessage(Message msg) {
		checkArgument(msg.getRemoteAddress() != null, "message destination can not be null");
		fillPipeline(msg);
	}

	private void fillPipeline(Message msg) {
		if(msg.getId() == null)
			msg.setId(getNode().getId());

		msg.setServer(this);
		msg.setVersion(Constants.VERSION);

		RPCCall call = msg.getAssociatedCall();
		if (call != null)
			dht.onSend(call);

		pipeline.add(msg);
		processPipeline();
	}

	private void processPipeline() {
		// simply assume nobody else is writing and attempt to do it
		// if it fails it's the current writer's job to double-check after releasing the write lock
		int currentState = WRITE_STATE_IDLE;
		if(!writeState.compareAndSet(currentState, WRITE_STATE_WRITING))
			return;

		// we are now the exclusive writer for this socket
		while(true) {
			Message msg = pipeline.poll();
			if(msg == null)
				break;

			try {
				ByteBuffer writeBuffer = RPCServer.writeBuffer.get();
				writeBuffer.clear();

				byte[] encryptedMsg = getNode().encrypt(msg.getRemoteId(), msg.serialize());
				writeBuffer.put(msg.getId().bytes());
				writeBuffer.put(encryptedMsg);

				writeBuffer.flip();

				int bytesSent = channel.send(writeBuffer, msg.getRemoteAddress());
				if(bytesSent == 0) {
					log.debug("Awaiting the socket available to send the messages.");
					pipeline.add(msg);

					writeState.set(WRITE_STATE_AWAITING);
					// wakeup -> updates selections -> will wait for write OP
					getNode().getNetworkEngine().updateInterestOps(this);
					return;
				}

				log.trace("sent {}/{} to {}: [{}]{}", msg.getMethod(), msg.getType(),
						AddressUtils.toString(msg.getRemoteAddress()), bytesSent, msg);

				if(msg.getAssociatedCall() != null) {
					msg.getAssociatedCall().sent(this);
					// when we send requests to a node we don't want their
					// replies to get stuck in the filter
					inboundThrottle.clear(msg.getRemoteAddress().getAddress());
				}

				sentMessages.incrementAndGet();
				stats.sentMessage(msg);
				stats.sentBytes(bytesSent + dht.getType().protocolHeaderSize());
			} catch (IOException e) {
				// async close
				if(!channel.isOpen())
					return;

				// BSD variants may throw an exception (ENOBUFS) instead of
				// just signaling 0 bytes sent when network queues are
				// full -> back off just like we would in the 0 bytes case.
				if(e.getMessage().equals("No buffer space available")) {
					// !!! same with above: channel.send returns 0
					log.debug("Awaiting the socket available to send the messages.");
					pipeline.add(msg);

					writeState.set(WRITE_STATE_AWAITING);
					// wakeup -> updates selections -> will wait for write OP
					getNode().getNetworkEngine().updateInterestOps(this);
					return;
				}

				log.error("Failed while attempting to send {}/{} to {}: {}", msg.getMethod(), msg.getType(),
						AddressUtils.toString(msg.getRemoteAddress()), msg);
				log.error("Stack trace", e);
				if(msg.getAssociatedCall() != null)
					msg.getAssociatedCall().failed();

				break;
			} catch (CryptoError e) {
				log.error("Failed to encrypt message {}/{} to {}: {}", msg.getMethod(), msg.getType(),
						AddressUtils.toString(msg.getRemoteAddress()), msg);
			}
		}

		// release claim on the socket
		writeState.compareAndSet(WRITE_STATE_WRITING, WRITE_STATE_IDLE);

		// check if we might have to pick it up again due to races
		// schedule async to avoid infinite stacks
		if(pipeline.peek() != null)
			getNode().getScheduler().execute(this::processPipeline);
	}

	// The package format: [32 bytes id][[16 bytes mac][encrypted message]]
	private static final int MIN_PACKET_SIZE = Message.MIN_SIZE + Id.BYTES + CryptoBox.MAC_BYTES;

	private void processPackets() throws IOException {
		ByteBuffer readBuffer = RPCServer.readBuffer.get();
		inboundThrottle.decay();

		while(true) {
			readBuffer.clear();
			InetSocketAddress sa = (InetSocketAddress) channel.receive(readBuffer);
			if(sa == null)
				break;

			readBuffer.flip();
			if (readBuffer.limit() == 0)
				break;

			stats.receivedBytes(readBuffer.limit() + dht.getType().protocolHeaderSize());

			// - no conceivable DHT message is smaller than MIN_PACKET_SIZE bytes
			// - port 0 is reserved
			// - address family may mismatch due to autoconversion from v4-mapped v6 addresses to Inet4Address
			// immediately discard junk on the read loop, don't even allocate a buffer for it
			if(readBuffer.limit() < MIN_PACKET_SIZE || sa.getPort() == 0 || !dht.getType().canUseSocketAddress(sa)) {
				log.warn("Dropped an invalid packet from {}.", AddressUtils.toString(sa));
				stats.droppedPacket(readBuffer.limit() + dht.getType().protocolHeaderSize());
				continue;
			}

			if(inboundThrottle.saturatingInc(sa.getAddress())) {
				log.warn("Throttled an packet from {}", AddressUtils.toString(sa));
				stats.droppedPacket(readBuffer.limit() + dht.getType().protocolHeaderSize());
				continue;
			}

			// copy from the read buffer since we hand off to another thread
			byte[] packet = new byte[readBuffer.limit()];
			readBuffer.get(packet);
			getNode().getScheduler().execute(() -> handlePacket(packet, sa));
		}
	}

	private void handlePacket(byte[] packet, InetSocketAddress sa) {
		Message msg = null;
		Id sender = Id.of(packet, 0);

		Blacklist blacklist = getNode().getBlacklist();
		if (blacklist.isBanned(sa)) {
			log.warn("Ignored the message from banned address {}", AddressUtils.toString(sa));
			return;
		}
		if (blacklist.isBanned(sender)) {
			log.warn("Ignored the message from banned node {}", sender);
			return;
		}

		try {
			byte[] encryptedMsg = Arrays.copyOfRange(packet, Id.BYTES, packet.length);
			byte[] decryptedMsg = getNode().decrypt(sender, encryptedMsg);
			msg = Message.parse(decryptedMsg);
			msg.setId(sender);
		} catch (MessageException e) {
			log.warn("Got a wrong packet from {}, ignored.", AddressUtils.toString(sa));

			stats.droppedPacket(packet.length);
			blacklist.observeInvalidMessage(sa);
			return;
		} catch (CryptoError e) {
			log.warn("Decrypt packet error from {}, ignored.", AddressUtils.toString(sa));

			stats.droppedPacket(packet.length);
			blacklist.observeInvalidMessage(sa);
			return;
		}

		blacklist.observe(sa, sender);

		log.trace("Received {}/{} from {}: [{}]{}", msg.getMethod(), msg.getType(),
				AddressUtils.toString(sa), packet.length, msg);

		receivedMessages.incrementAndGet();
		stats.receivedMessage(msg);
		msg.setOrigin(sa);

		// transaction id should be a non-zero integer
		if (msg.getType() != Message.Type.ERROR && msg.getTxid() == 0) {
			log.warn("Received a message with invalid transaction id.");
			ErrorMessage err = new ErrorMessage(msg.getMethod(), 0, ErrorCode.ProtocolError.value(),
					"Received a message with an invalid transaction id, expected a non-zero transaction id");
			err.setRemote(msg.getId(), msg.getOrigin());
			sendMessage(err);
			return;
		}

		// just respond to incoming requests, no need to match them to pending requests
		if(msg.getType() == Message.Type.REQUEST) {
			handleMessage(msg);
			return;
		}

		// check if this is a response to an outstanding request
		RPCCall call = calls.get(msg.getTxid());
		if (call != null) {
			// message matches transaction ID and origin == destination
			// we only check the IP address here. the routing table applies more strict checks to also verify a stable port
			if (call.getRequest().getRemoteAddress().getAddress().equals(msg.getOrigin().getAddress())) {
				// remove call first in case of exception
				if(calls.remove(msg.getTxid(), call)) {
					msg.setAssociatedCall(call);
					call.responsed(msg);

					processCallQueue();
					// apply after checking for a proper response
					handleMessage(msg);
				}

				return;
			}

			// 1. the message is not a request
			// 2. transaction ID matched
			// 3. request destination did not match response source!!
			// this happening by chance is exceedingly unlikely
			// indicates either port-mangling NAT, a multhomed host listening on any-local address or some kind of attack
			// ignore response
			log.warn("Transaction id matched, socket address did not, ignoring message, request: {} -> response: {}, version: {}",
					call.getRequest().getRemoteAddress(), msg.getOrigin(), msg.getReadableVersion());

			if(msg.getType() == Message.Type.RESPONSE && dht.getType() == DHT.Type.IPV6) {
				// this is more likely due to incorrect binding implementation in ipv6. notify peers about that
				// don't bother with ipv4, there are too many complications
				Message err = new ErrorMessage(msg.getMethod(), msg.getTxid(), ErrorCode.ProtocolError.value(),
						"A request was sent to " + call.getRequest().getRemoteAddress() +
						" and a response with matching transaction id was received from " + msg.getOrigin() +
						" . Multihomed nodes should ensure that sockets are properly bound and responses are sent with the correct source socket address. See BEPs 32 and 45.");
				err.setRemote(msg.getId(), call.getRequest().getRemoteAddress());
				sendMessage(err);
			}

			// but expect an upcoming timeout if it's really just a misbehaving node
			call.responseSocketMismatch();
			call.stall();
			return;
		}

		// a) it's not a request
		// b) didn't find a call
		// c) up-time is high enough that it's not a stray from a restart
		// did not expect this response
		if (msg.getType() == Message.Type.RESPONSE && Duration.between(startTime, Instant.now()).getSeconds() > 2 * 60) {
			log.warn("Cannot find RPC call for {} {}", msg.getType() == Message.Type.RESPONSE
					? "response" : "error", msg.getTxid());
			ErrorMessage err = new ErrorMessage(msg.getMethod(), msg.getTxid(), ErrorCode.ProtocolError.value(),
					"Received a response message whose transaction ID did not match a pending request or transaction expired");
			err.setRemote(msg.getId(), msg.getOrigin());
			sendMessage(err);
			return;
		}

		if (msg.getType() == Message.Type.ERROR) {
			handleMessage(msg);
			return;
		}

		log.debug("Ignored message: {}", msg);
	}

	public void handleMessage(Message msg) {
		dht.onMessage(msg);
	}

	Duration age() {
		Instant start = startTime;
		if(start == null)
			return Duration.ZERO;
		return Duration.between(start, Instant.now());
	}

	ScheduledExecutorService getScheduler() {
		return getNode().getScheduler();
	}

	@Override
	public SelectableChannel getChannel() {
		return channel;
	}

	@Override
	public void selectEvent(SelectionKey key) throws IOException {
		if (!key.isValid())
			return;

		if (key.isWritable()) {
			writeState.set(WRITE_STATE_IDLE);
			getNode().getNetworkEngine().updateInterestOps(this);
			// schedule async writes first before spending thread time on reads
			getNode().getScheduler().execute(this::processPipeline);
		} else if (key.isReadable()) {
			processPackets();
		}
	}

	@Override
	public void checkState() throws IOException {
		if(!channel.isOpen() || channel.socket().isClosed())
			stop();
	}

	@Override
	public int interestOps() {
		int ops = SelectionKey.OP_READ;
		if(writeState.get() == WRITE_STATE_AWAITING)
			ops |= SelectionKey.OP_WRITE;

		return ops;
	}

	@Override
	public String toString() {
		@SuppressWarnings("resource")
		Formatter f = new Formatter();

		f.format("%s @ %s%n", getNode().getId(), AddressUtils.toString(getAddress()));
		f.format("rx: %d tx: %d active: %d baseRTT: %d loss: %f  loss (verified): %f uptime: %s%n",
				getNumberOfReceivedMessages(), getNumberOfSentMessages(), getNumberOfActiveRPCCalls(),
				timeoutSampler.getStallTimeout(), unverifiedLossrate.getAverage(), verifiedEntryLossrate.getAverage(), age());
		f.format("RTT stats (%dsamples) %s", timeoutSampler.getSampleCount(), timeoutSampler.getStats());

		return f.toString();
	}
}
