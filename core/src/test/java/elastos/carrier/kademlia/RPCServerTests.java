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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import elastos.carrier.DefaultConfiguration;
import elastos.carrier.Network;
import elastos.carrier.NodeInfo;
import elastos.carrier.kademlia.exceptions.KadException;
import elastos.carrier.kademlia.messages.ErrorMessage;
import elastos.carrier.kademlia.messages.Message;
import elastos.carrier.kademlia.messages.MessageException;
import elastos.carrier.kademlia.messages.PingRequest;
import elastos.carrier.kademlia.messages.PingResponse;
import elastos.carrier.utils.AddressUtils;
import elastos.carrier.utils.ByteBufferOutputStream;

//@Disabled()
@EnabledIfSystemProperty(named = "elastos.carrier.enviroment", matches = "development")
public class RPCServerTests {
	private final static InetAddress localAddr =
			AddressUtils.getAllAddresses().filter(Inet4Address.class::isInstance)
				.filter((a) -> AddressUtils.isAnyUnicast(a))
				.distinct().findFirst().get();

	private final static InetSocketAddress sa1 = new InetSocketAddress(localAddr, 8888);
	private final static InetSocketAddress sa2 = new InetSocketAddress(localAddr, 9999);

	static class InvalidMessage extends Message {
		public InvalidMessage() {
			super(Type.REQUEST, Method.UNKNOWN);
		}

		@Override
		public int estimateSize() {
			return BASE_SIZE;
		}
	}

	static abstract class TestDHT extends DHT {
		protected TestNode node;

		AtomicInteger receivedPings = new AtomicInteger();
		AtomicInteger sentPingResponses = new AtomicInteger();
		AtomicInteger receivedPingResponses = new AtomicInteger();
		AtomicInteger sentErrors = new AtomicInteger();
		AtomicInteger receivedErrors = new AtomicInteger();
		AtomicInteger timeoutMessages = new AtomicInteger();
		AtomicInteger manualTimeouts = new AtomicInteger();

		public TestDHT() {
			super(Network.IPv4, null, null);
		}

		public void setNode(TestNode node) {
			this.node = node;
		}

		@Override
		public Node getNode() {
			return node;
		}

		@Override
		public Network getType() {
			return Network.IPv4;
		}

		@Override
		public void onMessage(Message msg) {
		}

		@Override
		public void onTimeout(RPCCall call) {
		}
	}

	static abstract class TestRoutine implements Runnable {
		protected TestNode node;
		int sentPings;

		public void setNode(TestNode node) {
			this.node = node;
		}
	}

	static class TestNode extends Node {
		private InetSocketAddress addr4;
		TestNode peer;
		RPCServer rpcServer;
		TestDHT dht;
		TestRoutine testRoutine;
		private Thread testThread;
		private NetworkEngine networkEngine;

		private final static CyclicBarrier barrier = new CyclicBarrier(2);

		public TestNode(InetSocketAddress addr4) throws KadException {
			super(new DefaultConfiguration.Builder().build());
			this.addr4 = addr4;
		}

		public InetSocketAddress getAddress() {
			return addr4;
		}

		public NodeInfo getInfo() {
			return new NodeInfo(getId(), getAddress());
		}

		public void setup(TestNode peer, TestDHT dht, TestRoutine testRoutine) {
			this.peer = peer;

			dht.setNode(this);
			if (testRoutine != null)
				testRoutine.setNode(this);

			this.dht = dht;
			this.testRoutine = testRoutine;

			this.rpcServer = new RPCServer(dht, addr4, false);
		}

		@Override
		public DHT getDHT(Network type) {
			return type == Network.IPv4 ? dht : null;
		}

		public RPCServer getRPCServer(Network type) {
			return type == Network.IPv4 ? rpcServer : null;
		}

		@Override
		public void start() throws KadException {
			networkEngine = new NetworkEngine();
			super.setScheduler(new ScheduledThreadPoolExecutor(4));
			rpcServer.start();
			testThread = new Thread(this::testRouineWrapper);
			testThread.start();
		}

		@Override
		public NetworkEngine getNetworkEngine() {
			return networkEngine;
		}

		@Override
		public void stop() {
			if (rpcServer != null)
				rpcServer.stop();
		}

		public void join() throws InterruptedException {
			testThread.join();
		}

		private void testRouineWrapper() {
			try {
				barrier.await();
			} catch (BrokenBarrierException | InterruptedException e) {
				e.printStackTrace();
				return;
			}

			System.out.println("Test thread " + Thread.currentThread().getId() + " started.");
			if (testRoutine != null)
				testRoutine.run();
		}
	}

	@BeforeAll
	public static void beforeAll(){
		Level level = Level.INFO;

		// We use logback as the logging backend
		Logger root = (Logger)LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
		root.setLevel(level);
	}

	@Test
	public void testAllSuccessCalls() throws Exception {
		TestNode node1 = new TestNode(sa1);
		TestNode node2 = new TestNode(sa2);

		class MyDHT  extends TestDHT {
			@Override
			public void onMessage(Message msg) {
				if (msg.getType() == Message.Type.REQUEST) {
					Message response;
					switch (msg.getMethod()) {
					case PING:
						receivedPings.incrementAndGet();
						response = new PingResponse(msg.getTxid());
						break;

					default:
						response = new ErrorMessage(msg.getMethod(), msg.getTxid(), 1000, "Should not happen");
						System.out.format("%s: unexpected request - should never heppen - %s\n", node.getId(), msg);
						break;
					}

					int delay = ThreadLocalRandom.current().nextInt(100, 200);
					response.setRemote(msg.getId(), msg.getOrigin());
					sentPingResponses.incrementAndGet();
					node.getScheduler().schedule(() -> node.rpcServer.sendMessage(response), delay, TimeUnit.MILLISECONDS);
				} else if (msg.getType() == Message.Type.RESPONSE) {
					switch (msg.getMethod()) {
					case PING:
						receivedPingResponses.incrementAndGet();
						break;

					default:
						System.out.format("%s: unexpected response - should never heppen - %s\n", node.getId(), msg);
						break;
					}
				} else if (msg.getType() == Message.Type.ERROR) {
					System.out.format("%s: error - should never heppen - %s\n", node.getId(), msg);
				} else {
					System.out.format("%s: timeout - should never heppen - %s\n", node.getId(), msg);
				}
			}

			@Override
			public void onTimeout(RPCCall call) {
				System.out.format("%s: should never heppen - %s\n", node.getId(), call.getRequest());
			}
		};

		class MyRoutine extends TestRoutine {
			@Override
			public void run() {
				ThreadLocalRandom rnd = ThreadLocalRandom.current();

				for (int i = 0; i < rnd.nextInt(16, 32); i++) {
					Message msg = new PingRequest();
					try {
						Thread.sleep(rnd.nextInt(100, 500));
					} catch (InterruptedException e) {
						e.printStackTrace();
					}

					RPCCall call = new RPCCall(node.peer.getInfo(), msg);
					msg.setRemote(node.peer.getId(), node.peer.getAddress());
					node.rpcServer.sendCall(call);
					sentPings++;
					System.out.print(".");
				}
			}
		};


		node1.setup(node2, new MyDHT(), new MyRoutine());
		node2.setup(node1, new MyDHT(), new MyRoutine());

		node1.start();
		node2.start();

		node1.join();
		node2.join();

		System.out.println("\nWaiting for finilize the calls...");
		Thread.sleep(15000);

		System.out.println(node1.rpcServer.toString());
		System.out.println(node2.rpcServer.toString());

		System.out.println(node1.rpcServer.getStats().toString());
		System.out.println(node2.rpcServer.getStats().toString());

		assertEquals(node1.testRoutine.sentPings, node2.dht.receivedPings.get());
		assertEquals(node2.testRoutine.sentPings, node1.dht.receivedPings.get());
		assertEquals(node1.dht.sentPingResponses.get(), node2.dht.receivedPingResponses.get());
		assertEquals(node2.dht.sentPingResponses.get(), node1.dht.receivedPingResponses.get());

		assertEquals(node1.testRoutine.sentPings + node1.dht.sentPingResponses.get(), node1.rpcServer.getStats().getTotalSentMessages());
		assertEquals(node1.testRoutine.sentPings, node1.rpcServer.getStats().getSentMessages(Message.Method.PING, Message.Type.REQUEST));
		assertEquals(node1.dht.sentPingResponses.get(), node1.rpcServer.getStats().getSentMessages(Message.Method.PING, Message.Type.RESPONSE));
		assertEquals(node1.dht.receivedPings.get(), node1.rpcServer.getStats().getReceivedMessages(Message.Method.PING, Message.Type.REQUEST));
		assertEquals(node1.dht.receivedPingResponses.get(), node1.rpcServer.getStats().getReceivedMessages(Message.Method.PING, Message.Type.RESPONSE));

		assertEquals(node2.testRoutine.sentPings + node2.dht.sentPingResponses.get(), node2.rpcServer.getStats().getTotalReceivedMessages());
		assertEquals(node2.testRoutine.sentPings, node2.rpcServer.getStats().getSentMessages(Message.Method.PING, Message.Type.REQUEST));
		assertEquals(node2.dht.sentPingResponses.get(), node2.rpcServer.getStats().getSentMessages(Message.Method.PING, Message.Type.RESPONSE));
		assertEquals(node2.dht.receivedPings.get(), node2.rpcServer.getStats().getReceivedMessages(Message.Method.PING, Message.Type.REQUEST));
		assertEquals(node2.dht.receivedPingResponses.get(), node2.rpcServer.getStats().getReceivedMessages(Message.Method.PING, Message.Type.RESPONSE));

		assertEquals(node1.rpcServer.getNumberOfSentMessages(), node1.rpcServer.getStats().getTotalSentMessages());
		assertEquals(node1.rpcServer.getNumberOfReceivedMessages(), node1.rpcServer.getStats().getTotalReceivedMessages());

		assertEquals(node2.rpcServer.getNumberOfSentMessages(), node2.rpcServer.getStats().getTotalSentMessages());
		assertEquals(node2.rpcServer.getNumberOfReceivedMessages(), node2.rpcServer.getStats().getTotalReceivedMessages());

		node1.stop();
		node2.stop();
	}

	@Test
	public void testAllInvalidCalls() throws Exception {
		TestNode node1 = new TestNode(sa1);
		TestNode node2 = new TestNode(sa2);

		class MyDHT  extends TestDHT {
			@Override
			public void onMessage(Message msg) {
				if (msg.getType() == Message.Type.REQUEST) {
					System.out.format("%s: unexpected request - should never heppen - %s\n", node.getId(), msg);
				} else if (msg.getType() == Message.Type.RESPONSE) {
					System.out.format("%s: unexpected response - should never heppen - %s\n", node.getId(), msg);
				} else if (msg.getType() == Message.Type.ERROR) {
					receivedErrors.incrementAndGet();
				} else {
					System.out.format("%s: unknown - should never heppen - %s\n", node.getId(), msg);
				}
			}

			@Override
			public void onTimeout(RPCCall call) {
				timeoutMessages.incrementAndGet();
			}
		};

		class MyRoutine extends TestRoutine {
			@Override
			public void run() {
				ThreadLocalRandom rnd = ThreadLocalRandom.current();

				for (int i = 0; i < rnd.nextInt(16, 32); i++) {
					Message msg = new InvalidMessage();
					try {
						Thread.sleep(rnd.nextInt(100, 500));
					} catch (InterruptedException e) {
						e.printStackTrace();
					}

					RPCCall call = new RPCCall(node.peer.getInfo(), msg);
					msg.setRemote(node.peer.getId(), node.peer.getAddress());
					node.rpcServer.sendCall(call);
					sentPings++;
					System.out.print(".");
				}
			}
		};

		node1.setup(node2, new MyDHT(), new MyRoutine());
		node2.setup(node1, new MyDHT(), new MyRoutine());

		node1.start();
		node2.start();

		node1.join();
		node2.join();

		System.out.println("\nWaiting for finilize the calls...");
		Thread.sleep(15000);

		System.out.println(node1.rpcServer.toString());
		System.out.println(node2.rpcServer.toString());

		System.out.println(node1.rpcServer.getStats().toString());
		System.out.println(node2.rpcServer.getStats().toString());

		assertEquals(0, node1.dht.receivedErrors.get());
		assertEquals(node1.testRoutine.sentPings, node1.dht.timeoutMessages.get());

		assertEquals(0, node2.dht.receivedErrors.get());
		assertEquals(node2.testRoutine.sentPings, node2.dht.timeoutMessages.get());

		assertEquals(node1.testRoutine.sentPings, node1.rpcServer.getStats().getTotalSentMessages());
		assertEquals(node1.testRoutine.sentPings, node1.rpcServer.getStats().getSentMessages(Message.Method.UNKNOWN, Message.Type.REQUEST));
		assertEquals(node2.testRoutine.sentPings, node1.rpcServer.getStats().getDroppedPackets());

		assertEquals(node2.testRoutine.sentPings, node2.rpcServer.getStats().getTotalSentMessages());
		assertEquals(node2.testRoutine.sentPings, node2.rpcServer.getStats().getSentMessages(Message.Method.UNKNOWN, Message.Type.REQUEST));
		assertEquals(node1.testRoutine.sentPings, node2.rpcServer.getStats().getDroppedPackets());

		assertEquals(node1.rpcServer.getNumberOfSentMessages(), node1.rpcServer.getStats().getTotalSentMessages());
		assertEquals(node1.rpcServer.getNumberOfReceivedMessages(), node1.rpcServer.getStats().getTotalReceivedMessages());

		assertEquals(node2.rpcServer.getNumberOfSentMessages(), node2.rpcServer.getStats().getTotalSentMessages());
		assertEquals(node2.rpcServer.getNumberOfReceivedMessages(), node2.rpcServer.getStats().getTotalReceivedMessages());

		node1.stop();
		node2.stop();
	}

	@Test
	public void testTimeoutCalls() throws Exception {
		TestNode node1 = new TestNode(sa1);
		TestNode node2 = new TestNode(sa2);

		class MyDHT  extends TestDHT {
			@Override
			public void onMessage(Message msg) {
				if (msg.getType() == Message.Type.REQUEST) {
					Message response;
					switch (msg.getMethod()) {
					case PING:
						receivedPings.incrementAndGet();
						response = new PingResponse(msg.getTxid());
						break;

					default:
						response = new ErrorMessage(msg.getMethod(), msg.getTxid(), 1000, "Should not happen");
						System.out.format("%s: unexpected request - should never heppen - %s\n", node.getId(), msg);
						break;
					}

					int delay = 12000; // will cause the call timeout
					response.setRemote(msg.getId(), msg.getOrigin());
					sentPingResponses.incrementAndGet();
					node.getScheduler().schedule(() -> node.rpcServer.sendMessage(response), delay, TimeUnit.MILLISECONDS);
				} else if (msg.getType() == Message.Type.RESPONSE) {
					switch (msg.getMethod()) {
					case PING:
						System.out.format("%s: unexpected ping response - should never heppen - %s\n", node.getId(), msg);
						receivedPingResponses.incrementAndGet();
						break;

					default:
						System.out.format("%s: unexpected response - should never heppen - %s\n", node.getId(), msg);
						break;
					}
				} else if (msg.getType() == Message.Type.ERROR) {
					System.out.format("%s: error - should never heppen - %s\n", node.getId(), msg);
				} else {
					System.out.format("%s: unknown - should never heppen - %s\n", node.getId(), msg);
				}
			}

			@Override
			public void onTimeout(RPCCall call) {
				timeoutMessages.incrementAndGet();
			}
		};

		class MyRoutine extends TestRoutine {
			@Override
			public void run() {
				ThreadLocalRandom rnd = ThreadLocalRandom.current();

				for (int i = 0; i < rnd.nextInt(16, 32); i++) {
					Message msg = new PingRequest();
					try {
						Thread.sleep(rnd.nextInt(100, 500));
					} catch (InterruptedException e) {
						e.printStackTrace();
					}

					RPCCall call = new RPCCall(node.peer.getInfo(), msg);
					msg.setRemote(node.peer.getId(), node.peer.getAddress());
					node.rpcServer.sendCall(call);
					sentPings++;
					System.out.print(".");
				}
			}
		};


		node1.setup(node2, new MyDHT(), new MyRoutine());
		node2.setup(node1, new MyDHT(), new MyRoutine());

		node1.start();
		node2.start();

		node1.join();
		node2.join();

		System.out.println("\nWaiting for finilize the calls...");
		Thread.sleep(15000);

		System.out.println(node1.rpcServer.toString());
		System.out.println(node2.rpcServer.toString());

		System.out.println(node1.rpcServer.getStats().toString());
		System.out.println(node2.rpcServer.getStats().toString());

		assertEquals(node1.testRoutine.sentPings, node2.dht.receivedPings.get());
		assertEquals(node2.testRoutine.sentPings, node1.dht.receivedPings.get());
		assertEquals(node1.testRoutine.sentPings, node1.dht.timeoutMessages.get());
		assertEquals(node2.testRoutine.sentPings, node2.dht.timeoutMessages.get());

		assertEquals(node1.testRoutine.sentPings + node1.dht.sentPingResponses.get(), node1.rpcServer.getStats().getTotalSentMessages());
		assertEquals(node1.testRoutine.sentPings, node1.rpcServer.getStats().getSentMessages(Message.Method.PING, Message.Type.REQUEST));
		assertEquals(node1.dht.sentPingResponses.get(), node1.rpcServer.getStats().getSentMessages(Message.Method.PING, Message.Type.RESPONSE));
		assertEquals(node1.dht.receivedPings.get(), node1.rpcServer.getStats().getReceivedMessages(Message.Method.PING, Message.Type.REQUEST));
		assertEquals(node1.dht.timeoutMessages.get(), node1.rpcServer.getStats().getTimeoutMessages(Message.Method.PING));
		assertEquals(node1.dht.timeoutMessages.get(), node1.rpcServer.getStats().getTotalTimeoutMessages());

		assertEquals(node2.testRoutine.sentPings + node2.dht.sentPingResponses.get() , node2.rpcServer.getStats().getTotalSentMessages());
		assertEquals(node2.testRoutine.sentPings, node2.rpcServer.getStats().getSentMessages(Message.Method.PING, Message.Type.REQUEST));
		assertEquals(node2.dht.sentPingResponses.get(), node2.rpcServer.getStats().getSentMessages(Message.Method.PING, Message.Type.RESPONSE));
		assertEquals(node2.dht.receivedPings.get(), node2.rpcServer.getStats().getReceivedMessages(Message.Method.PING, Message.Type.REQUEST));
		assertEquals(node2.dht.timeoutMessages.get(), node2.rpcServer.getStats().getTimeoutMessages(Message.Method.PING));
		assertEquals(node2.dht.timeoutMessages.get(), node2.rpcServer.getStats().getTotalTimeoutMessages());

		assertEquals(node1.rpcServer.getNumberOfSentMessages(), node1.rpcServer.getStats().getTotalSentMessages());
		assertEquals(node1.rpcServer.getNumberOfReceivedMessages(), node1.rpcServer.getStats().getTotalReceivedMessages());

		assertEquals(node2.rpcServer.getNumberOfSentMessages(), node2.rpcServer.getStats().getTotalSentMessages());
		assertEquals(node2.rpcServer.getNumberOfReceivedMessages(), node2.rpcServer.getStats().getTotalReceivedMessages());

		node1.stop();
		node2.stop();
	}

	@Test
	public void testSendThrottle() throws Exception {
		TestNode node1 = new TestNode(sa1);
		TestNode node2 = new TestNode(sa2);

		class MyDHT  extends TestDHT {
			@Override
			public void onMessage(Message msg) {
				if (msg.getType() == Message.Type.REQUEST) {
					Message response;
					switch (msg.getMethod()) {
					case PING:
						receivedPings.incrementAndGet();
						response = new PingResponse(msg.getTxid());
						break;

					default:
						response = new ErrorMessage(msg.getMethod(), msg.getTxid(), 1000, "Should not happen");
						System.out.format("%s: unexpected request - should never heppen - %s\n", node.getId(), msg);
						break;
					}

					int delay = ThreadLocalRandom.current().nextInt(100, 200);
					response.setRemote(msg.getId(), msg.getOrigin());
					sentPingResponses.incrementAndGet();
					node.getScheduler().schedule(() -> node.rpcServer.sendMessage(response), delay, TimeUnit.MILLISECONDS);
				} else if (msg.getType() == Message.Type.RESPONSE) {
					switch (msg.getMethod()) {
					case PING:
						receivedPingResponses.incrementAndGet();
						break;

					default:
						System.out.format("%s: unexpected response - should never heppen - %s\n", node.getId(), msg);
						break;
					}
				} else if (msg.getType() == Message.Type.ERROR) {
					System.out.format("%s: error - should never heppen - %s\n", node.getId(), msg);
				} else {
					System.out.format("%s: unknown - should never heppen - %s\n", node.getId(), msg);
				}
			}

			@Override
			public void onTimeout(RPCCall call) {
				System.out.format("%s: timeout - should never heppen - %s\n", node.getId(), call.getRequest());
			}
		};

		class MyRoutine extends TestRoutine {
			@Override
			public void run() {
				ThreadLocalRandom rnd = ThreadLocalRandom.current();

				for (int i = 0; i < rnd.nextInt(20, 32); i++) {
					Message msg = new PingRequest();
					RPCCall call = new RPCCall(node.peer.getInfo(), msg);
					msg.setRemote(node.peer.getId(), node.peer.getAddress());
					node.rpcServer.sendCall(call);
					sentPings++;
					System.out.print(".");
				}
			}
		};

		node1.setup(node2, new MyDHT(), new MyRoutine());
		node2.setup(node1, new MyDHT(), null);

		node1.start();
		node2.start();

		node1.join();
		node2.join();

		System.out.println("\nWaiting for finilize the calls...");
		Thread.sleep(30000);

		System.out.println(node1.rpcServer.toString());
		System.out.println(node2.rpcServer.toString());

		System.out.println(node1.rpcServer.getStats().toString());
		System.out.println(node2.rpcServer.getStats().toString());

		assertEquals(node1.testRoutine.sentPings, node2.dht.receivedPings.get());
		assertEquals(node2.dht.sentPingResponses.get(), node1.dht.receivedPingResponses.get());

		assertEquals(node1.testRoutine.sentPings + node1.dht.sentPingResponses.get(), node1.rpcServer.getStats().getTotalSentMessages());
		assertEquals(node1.testRoutine.sentPings, node1.rpcServer.getStats().getSentMessages(Message.Method.PING, Message.Type.REQUEST));
		assertEquals(node1.dht.sentPingResponses.get(), node1.rpcServer.getStats().getSentMessages(Message.Method.PING, Message.Type.RESPONSE));
		assertEquals(node1.dht.receivedPings.get(), node1.rpcServer.getStats().getReceivedMessages(Message.Method.PING, Message.Type.REQUEST));
		assertEquals(node1.dht.receivedPingResponses.get(), node1.rpcServer.getStats().getReceivedMessages(Message.Method.PING, Message.Type.RESPONSE));

		assertEquals(node2.dht.sentPingResponses.get(), node2.rpcServer.getStats().getSentMessages(Message.Method.PING, Message.Type.RESPONSE));
		assertEquals(node2.dht.receivedPings.get(), node2.rpcServer.getStats().getReceivedMessages(Message.Method.PING, Message.Type.REQUEST));
		assertEquals(node2.dht.receivedPingResponses.get(), node2.rpcServer.getStats().getReceivedMessages(Message.Method.PING, Message.Type.RESPONSE));

		assertEquals(node1.rpcServer.getNumberOfSentMessages(), node1.rpcServer.getStats().getTotalSentMessages());
		assertEquals(node1.rpcServer.getNumberOfReceivedMessages(), node1.rpcServer.getStats().getTotalReceivedMessages());

		assertEquals(node2.rpcServer.getNumberOfSentMessages(), node2.rpcServer.getStats().getTotalSentMessages());
		assertEquals(node2.rpcServer.getNumberOfReceivedMessages(), node2.rpcServer.getStats().getTotalReceivedMessages());

		node1.stop();
		node2.stop();
	}

	@Test
	public void testReceiveThrottle() throws Exception {
		TestNode node1 = new TestNode(sa1);
		TestNode node2 = new TestNode(sa2);

		class MyDHT  extends TestDHT {
			@Override
			public void onMessage(Message msg) {
				if (msg.getType() == Message.Type.REQUEST) {
					Message response;
					switch (msg.getMethod()) {
					case PING:
						receivedPings.incrementAndGet();
						response = new PingResponse(msg.getTxid());
						break;

					default:
						response = new ErrorMessage(msg.getMethod(), msg.getTxid(), 1000, "Should not happen");
						System.out.format("%s: unexpected request - should never heppen - %s\n", node.getId(), msg);
						break;
					}

					int delay = ThreadLocalRandom.current().nextInt(100, 200);
					response.setRemote(msg.getId(), msg.getOrigin());
					sentPingResponses.incrementAndGet();
					node.getScheduler().schedule(() -> node.rpcServer.sendMessage(response), delay, TimeUnit.MILLISECONDS);
				} else if (msg.getType() == Message.Type.RESPONSE) {
					switch (msg.getMethod()) {
					case PING:
						receivedPingResponses.incrementAndGet();
						break;

					default:
						System.out.format("%s: unexpected response - should never heppen - %s\n", node.getId(), msg);
						break;
					}
				} else if (msg.getType() == Message.Type.ERROR) {
					System.out.format("%s: error - should never heppen - %s\n", node.getId(), msg);
				} else {
					System.out.format("%s: unknown - should never heppen - %s\n", node.getId(), msg);
				}
			}

			@Override
			public void onTimeout(RPCCall call) {
				System.out.format("%s: timeout - should never heppen - %s\n", node.getId(), call.getRequest());
			}
		};

		class MyRoutine extends TestRoutine {
			@Override
			public void run() {
				ThreadLocalRandom rnd = ThreadLocalRandom.current();

				ByteBuffer writeBuffer = ByteBuffer.allocate(2048);
				// oughly send messages through the raw socket
				for (int i = 0; i < rnd.nextInt(20, 32); i++) {
					Message msg = new PingRequest();
					msg.setId(node.getId());
					msg.setRemote(node.peer.getId(), node.peer.getAddress());
					msg.setTxid(i+1);

					try {
						writeBuffer.clear();
						msg.serialize(new ByteBufferOutputStream(writeBuffer));
						writeBuffer.flip();

						DatagramChannel ch = (DatagramChannel)node.rpcServer.getChannel();
						int bytesSent = ch.send(writeBuffer, msg.getRemoteAddress());
						if (bytesSent > 0)
							sentPings++;
					} catch (MessageException | IOException e) {
						e.printStackTrace();
					}
				}

				System.out.println("Total sent " + sentPings + " messages");
			}
		};

		node1.setup(node2, new MyDHT(), new MyRoutine());
		node2.setup(node1, new MyDHT(), new MyRoutine());

		node1.start();
		node2.start();

		node1.join();
		node2.join();

		System.out.println("\nWaiting for finilize the calls...");
		Thread.sleep(15000);

		System.out.println(node1.rpcServer.toString());
		System.out.println(node2.rpcServer.toString());

		System.out.println(node1.rpcServer.getStats().toString());
		System.out.println(node2.rpcServer.getStats().toString());

		assertTrue(node1.testRoutine.sentPings > node2.dht.receivedPings.get());
		assertTrue(node2.testRoutine.sentPings > node1.dht.receivedPings.get());

		assertEquals(node1.testRoutine.sentPings + node1.rpcServer.getStats().getSentMessages(Message.Method.PING, Message.Type.RESPONSE), node2.dht.receivedPings.get() + node2.rpcServer.getStats().getDroppedPackets());
		assertEquals(node2.testRoutine.sentPings + node2.rpcServer.getStats().getSentMessages(Message.Method.PING, Message.Type.RESPONSE), node1.dht.receivedPings.get() + node1.rpcServer.getStats().getDroppedPackets());

		assertEquals(node1.rpcServer.getNumberOfSentMessages(), node1.rpcServer.getStats().getTotalSentMessages());
		assertEquals(node1.rpcServer.getNumberOfReceivedMessages(), node1.rpcServer.getStats().getTotalReceivedMessages());

		assertEquals(node2.rpcServer.getNumberOfSentMessages(), node2.rpcServer.getStats().getTotalSentMessages());
		assertEquals(node2.rpcServer.getNumberOfReceivedMessages(), node2.rpcServer.getStats().getTotalReceivedMessages());

		node1.stop();
		node2.stop();
	}

	@Test
	public void testRandomCalls() throws Exception {
		TestNode node1 = new TestNode(sa1);
		TestNode node2 = new TestNode(sa2);

		class MyDHT  extends TestDHT {
			@Override
			public void onMessage(Message msg) {
				if (msg.getType() == Message.Type.REQUEST) {
					Message response;
					switch (msg.getMethod()) {
					case PING:
						receivedPings.incrementAndGet();
						response = new PingResponse(msg.getTxid());
						break;

					default:
						response = new ErrorMessage(msg.getMethod(), msg.getTxid(), 1000, "Should not happen");
						System.out.format("%s: unexpected request - should never heppen - %s\n", node.getId(), msg);
						break;
					}

					int delay = ThreadLocalRandom.current().nextInt(100, Constants.RPC_CALL_TIMEOUT_MAX + 2000);
					if (delay >= Constants.RPC_CALL_TIMEOUT_MAX) {
						System.out.println("Delay a response to make the call timeout");
						manualTimeouts.incrementAndGet();
					}

					response.setRemote(msg.getId(), msg.getOrigin());
					sentPingResponses.incrementAndGet();
					node.getScheduler().schedule(() -> node.rpcServer.sendMessage(response), delay, TimeUnit.MILLISECONDS);
				} else if (msg.getType() == Message.Type.RESPONSE) {
					switch (msg.getMethod()) {
					case PING:
						receivedPingResponses.incrementAndGet();
						break;

					default:
						System.out.format("%s: unexpected response - should never heppen - %s\n", node.getId(), msg);
						break;
					}
				} else if (msg.getType() == Message.Type.ERROR) {
					System.out.format("%s: error - should never heppen - %s\n", node.getId(), msg);
				} else {
					System.out.format("%s: unknown - should never heppen - %s\n", node.getId(), msg);
				}
			}

			@Override
			public void onTimeout(RPCCall call) {
				timeoutMessages.incrementAndGet();
			}
		};

		class MyRoutine extends TestRoutine {
			@Override
			public void run() {
				ThreadLocalRandom rnd = ThreadLocalRandom.current();

				for (int i = 0; i < rnd.nextInt(256, 512); i++) {
					Message msg = new PingRequest();
					try {
						Thread.sleep(rnd.nextInt(200, 500));
					} catch (InterruptedException e) {
						e.printStackTrace();
					}

					RPCCall call = new RPCCall(node.peer.getInfo(), msg);
					msg.setRemote(node.peer.getId(), node.peer.getAddress());
					node.rpcServer.sendCall(call);
					sentPings++;
					System.out.print(".");
				}
			}
		};

		node1.setup(node2, new MyDHT(), new MyRoutine());
		node2.setup(node1, new MyDHT(), new MyRoutine());

		node1.start();
		node2.start();

		node1.join();
		node2.join();

		System.out.println("\nWaiting for finilize the calls...");
		Thread.sleep(15000);

		System.out.println(node1.rpcServer.toString());
		System.out.println(node2.rpcServer.toString());

		System.out.println(node1.rpcServer.getStats().toString());
		System.out.println(node2.rpcServer.getStats().toString());

		assertEquals(node1.dht.manualTimeouts.get(), node2.rpcServer.getStats().getTotalTimeoutMessages() + node2.rpcServer.getStats().getDroppedPackets());
		assertEquals(node2.dht.manualTimeouts.get(), node1.rpcServer.getStats().getTotalTimeoutMessages() + node1.rpcServer.getStats().getDroppedPackets());

		assertEquals(node1.testRoutine.sentPings, node2.dht.receivedPings.get());
		assertEquals(node2.testRoutine.sentPings, node1.dht.receivedPings.get());
		assertEquals(node1.dht.sentPingResponses.get(), node2.dht.receivedPingResponses.get() + node2.rpcServer.getStats().getTotalTimeoutMessages());
		assertEquals(node2.dht.sentPingResponses.get(), node1.dht.receivedPingResponses.get() + node1.rpcServer.getStats().getTotalTimeoutMessages());

		assertEquals(node1.rpcServer.getNumberOfSentMessages(), node1.rpcServer.getStats().getTotalSentMessages());
		assertEquals(node1.rpcServer.getNumberOfReceivedMessages(), node1.rpcServer.getStats().getTotalReceivedMessages());

		assertEquals(node2.rpcServer.getNumberOfSentMessages(), node2.rpcServer.getStats().getTotalSentMessages());
		assertEquals(node2.rpcServer.getNumberOfReceivedMessages(), node2.rpcServer.getStats().getTotalReceivedMessages());

		node1.stop();
		node2.stop();
	}
}
