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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.StandardProtocolFamily;
import java.net.StandardSocketOptions;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import elastos.carrier.kademlia.NetworkEngine.Selectable;

@TestMethodOrder(OrderAnnotation.class)
public class NetworkEngineTests {
	private NetworkEngine networkEngine = new NetworkEngine();

	class MockSelectable implements Selectable {
		private InetSocketAddress addr;
		private InetSocketAddress remote;
		private DatagramChannel channel;
		private boolean interestOnWritable;

		private Map<String, Object> stats = new HashMap<>();

		public MockSelectable(String host, int port, String remoteHost, int remotePort) {
			addr = new InetSocketAddress(host, port);
			if (remoteHost != null)
				remote = new InetSocketAddress(remoteHost, remotePort);
		}

		public MockSelectable(String host, int port) {
			this(host, port, null, 0);
		}

		public void start() throws IOException {
			channel = DatagramChannel.open(StandardProtocolFamily.INET);
			channel.configureBlocking(false);
			channel.setOption(StandardSocketOptions.SO_RCVBUF, 2 * 1024 * 1024);
			channel.setOption(StandardSocketOptions.SO_REUSEADDR, true);
			channel.bind(addr);

			networkEngine.register(this);
		}

		public void stop() throws IOException {
			channel.close();
		}

		@Override
		public SelectableChannel getChannel() {
			return channel;
		}

		@Override
		public void selectEvent(SelectionKey key) throws IOException {
			if (!key.isValid())
				return;

			if(key.isWritable()) {
				setInterestOnWritable(false);
				writeEvent();
			}
			if(key.isReadable())
				readEvent();
		}

		private int writeEventCalls = 0;

		protected void writeEvent() throws IOException {
			int size = ThreadLocalRandom.current().nextInt(16, 1400);
			byte[] data = new byte[size];
			ThreadLocalRandom.current().nextBytes(data);

			int sent = channel.send(ByteBuffer.wrap(data), remote);
			assertEquals(size, sent);

			this.setInterestOnWritable(false);

			stats.put("writeEvents", ++writeEventCalls);
			stats.put("writeEvent-data-" + writeEventCalls, data);
		}

		private int readEventCalls = 0;
		private ByteBuffer readBuffer;

		protected void readEvent() throws IOException {
			if (readBuffer == null)
				readBuffer = ByteBuffer.allocate(2048);
			else
				readBuffer.clear();

			InetSocketAddress from = (InetSocketAddress)channel.receive(readBuffer);
			assertNotNull(from);

			readBuffer.flip();
			int size = readBuffer.limit();
			assertTrue(size >= 16 && size < 1400);

			byte[] data = new byte[size];
			readBuffer.get(data);
			stats.put("readEvents", ++readEventCalls);
			stats.put("readEvent-data-" + readEventCalls, data);
		}

		private int checkStateCalls = 0;

		@Override
		public void checkState() throws IOException {
			if(!channel.isOpen() || channel.socket().isClosed())
				stop();

			stats.put("checkState", ++checkStateCalls);
		}

		public void setInterestOnWritable(boolean writable) {
			interestOnWritable = writable;
			networkEngine.updateInterestOps(this);
		}

		@Override
		public int interestOps() {
			int ops = SelectionKey.OP_READ;
			if (interestOnWritable)
				ops |= SelectionKey.OP_WRITE;

			return ops;
		}

		public Map<String, Object> getStats() {
			return stats;
		}
	}

	@BeforeAll
	public static void beforeAll(){
		Level level = Level.TRACE;

		// We use logback as the logging backend
		Logger root = (Logger)LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
		root.setLevel(level);
	}

	@Test
	@Order(1)
	public void testInitialStatus() {
		assertNotNull(networkEngine.getSelector());
		assertTrue(networkEngine.isIdle());
		assertTrue(networkEngine.getSelector().keys().isEmpty());
	}

	@Test
	@Order(2)
	public void testResiesterSelectable() throws Exception {
		MockSelectable s = new MockSelectable("127.0.0.1", 9999);

		s.start();
		Thread.sleep(100); // waiting for the selector start

		assertFalse(networkEngine.isIdle());
		assertEquals(1, networkEngine.getSelector().keys().size());
		Thread.sleep(500);

		s.stop();
		Thread.sleep(200);
		assertEquals(0, networkEngine.getSelector().keys().size());

		Thread.sleep(1600);
		Integer ci = (Integer)s.getStats().get("checkState");
		assertNotNull(ci);
		assertEquals(1, ci);

		Thread.sleep(10000);
		assertTrue(networkEngine.isIdle());
		assertEquals(0, networkEngine.getSelector().keys().size());

		s = new MockSelectable("127.0.0.1", 9999);
		s.start();
		Thread.sleep(100); // waiting for the selector start

		assertFalse(networkEngine.isIdle());
		assertEquals(1, networkEngine.getSelector().keys().size());
		Thread.sleep(500);

		s.stop();
		Thread.sleep(200);
		assertEquals(0, networkEngine.getSelector().keys().size());
	}

	static class randomIO implements Runnable {
		private String name;
		private List<MockSelectable> selectables;
		private int rolls;

		public randomIO(String name, List<MockSelectable> selectables, int rolls) {
			this.name = name;
			this.selectables = selectables;
			this.rolls = rolls;
		}

		@Override
		public void run() {
			for (int i = 0; i < rolls; i++) {
				if (i != 0 && (i & 0x0F) == 0)
					System.out.println(name + ": sent " + i + " datagrams" );

				int idx = ThreadLocalRandom.current().nextInt(0, 32);
				selectables.get(idx).setInterestOnWritable(true);

				try {
					Thread.sleep(ThreadLocalRandom.current().nextInt(1, 100));
				} catch(Exception ignore) {

				}
			}

			System.out.println(name + ": total sent " + rolls + " datagrams" );
		}
	}

	@Test
	@Order(2)
	public void testSelectEvents() throws Exception {
		String host = "127.0.0.1";
		int groupSize = 32;

		List<MockSelectable> gs1 = new ArrayList<>(groupSize);
		List<MockSelectable> gs2 = new ArrayList<>(groupSize);
		int basePort1 = 15000;
		int basePort2 = 16000;

		for (int i = 0; i < 32; i++) {
			MockSelectable s1 = new MockSelectable(host, basePort1 + i, host, basePort2 + i);
			s1.start();
			gs1.add(s1);

			MockSelectable s2 = new MockSelectable(host, basePort2 + i, host, basePort1 + i);
			s2.start();
			gs2.add(s2);
		}

		Thread.sleep(200);
		assertFalse(networkEngine.isIdle());
		assertEquals(groupSize * 2, networkEngine.getSelector().keys().size());

		int rolls = 1024;
		Thread gt1 = new Thread(new randomIO("Group1", gs1, rolls));
		Thread gt2 = new Thread(new randomIO("Group2", gs2, rolls));

		gt1.start();
		gt2.start();

		gt1.join();
		gt2.join();

		for (int i = 0; i < 32; i++) {
			gs1.get(i).stop();
			gs2.get(i).stop();
		}

		Thread.sleep(200);
		assertEquals(0, networkEngine.getSelector().keys().size());

		Thread.sleep(1600);

		int total = 0;
		for (int i = 0; i < 32; i++) {
			MockSelectable s1 = gs1.get(i);
			MockSelectable s2 = gs2.get(i);

			Integer ci1 = (Integer)s1.getStats().get("checkState");
			assertNotNull(ci1);
			assertTrue(ci1 > 1);

			Integer ci2 = (Integer)s2.getStats().get("checkState");
			assertNotNull(ci2);
			assertTrue(ci2 > 1);

			ci1 = (Integer)s1.getStats().get("writeEvents");
			ci2 = (Integer)s2.getStats().get("readEvents");
			assertEquals(ci1, ci2);

			if (ci1 != null && ci2 != null) {
				for (int c = 1; c <= ci1; c++) {
					byte[] w = (byte[])s1.getStats().get("writeEvent-data-" + c);
					byte[] r = (byte[])s2.getStats().get("readEvent-data-" + c);

					assertNotNull(w);
					assertNotNull(r);
					assertArrayEquals(w, r);
				}

				total += ci1;
			}

			ci1 = (Integer)s1.getStats().get("readEvents");
			ci2 = (Integer)s2.getStats().get("writeEvents");
			assertEquals(ci1, ci2);

			if (ci1 != null && ci2 != null) {
				for (int c = 1; c <= ci1; c++) {
					byte[] w = (byte[])s2.getStats().get("writeEvent-data-" + c);
					byte[] r = (byte[])s1.getStats().get("readEvent-data-" + c);

					assertNotNull(w);
					assertNotNull(r);
					assertArrayEquals(w, r);
				}

				total += ci1;
			}

			s1.getStats().clear();
			s2.getStats().clear();
		}

		assertEquals(rolls * 2, total);

		gs1.clear();
		gs2.clear();

		Thread.sleep(10000);
		assertTrue(networkEngine.isIdle());
		assertEquals(0, networkEngine.getSelector().keys().size());
	}

}
