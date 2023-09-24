package elastos.carrier.kademlia;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import elastos.carrier.ConnectionStatusListener;
import elastos.carrier.DefaultConfiguration;
import elastos.carrier.Id;
import elastos.carrier.Network;
import elastos.carrier.NodeInfo;
import elastos.carrier.PeerInfo;
import elastos.carrier.Value;
import elastos.carrier.crypto.CryptoBox.Nonce;
import elastos.carrier.crypto.Signature.KeyPair;
import elastos.carrier.utils.AddressUtils;

@EnabledIfSystemProperty(named = "elastos.carrier.enviroment", matches = "development")
public class NodeStressTests {
	private static final int BOOTSTRAP_NODES = 8;
	private static final int BOOTSTRAP_NODES_PORT_START = 39001;

	private static final int TEST_NODES = 1024;
	private static final int TEST_NODES_PORT_START = 39100;

	private static final String workingDir = System.getProperty("java.io.tmpdir") + File.separator + "CarrierNodeTests";

	private final static InetAddress localAddr =
			AddressUtils.getAllAddresses().filter(Inet4Address.class::isInstance)
				.filter((a) -> AddressUtils.isAnyUnicast(a))
				.distinct().findFirst().get();

	private static List<Node> bootstrapNodes = new ArrayList<>(BOOTSTRAP_NODES);
	private static List<NodeInfo> bootstraps = new ArrayList<>(BOOTSTRAP_NODES);

	private static List<Node> testNodes = new ArrayList<>(TEST_NODES);

	private static DefaultConfiguration.Builder dcb = new DefaultConfiguration.Builder();

	private static ScheduledThreadPoolExecutor testScheduler;

	private static void deleteFile(File file) {
		if (file.isDirectory()) {
			var children = file.listFiles();
			for (var child : children)
				deleteFile(child);
		}

		file.delete();
	}

	private static void prepareWorkingDirectory() {
		var dir = new File(workingDir);
		if (dir.exists())
			deleteFile(dir);

		dir.mkdirs();
	}

	private static void createTestScheduler() {
		var index = new AtomicInteger(0);

		ThreadGroup group = new ThreadGroup("CarrierTest");
		ThreadFactory factory = (r) -> {
			Thread thread = new Thread(group, r, "KadNode-sc-" + index.getAndIncrement());
			thread.setUncaughtExceptionHandler((t, e) -> {
				System.err.println("\007❌ Scheduler thread " + t.getName() + " encounter an uncaught exception. stack trace:");
				e.printStackTrace(System.err);
			});
			thread.setDaemon(true);
			return thread;
		};

		testScheduler = new ScheduledThreadPoolExecutor(1024, factory, (r, e) -> {
			System.err.println("\007⛔ Scheduler blocked the runable: " + r.toString());
		});
		testScheduler.setKeepAliveTime(20, TimeUnit.SECONDS);
		testScheduler.allowCoreThreadTimeOut(true);
	}

	private static void startBootstraps() throws Exception {
		for (int i = 0; i < BOOTSTRAP_NODES; i++) {
			System.out.format("\n\n\007🟢 Starting the bootstrap node %d ...\n", i);

			String dir = workingDir + File.separator + "bootstraps"  + File.separator + "node-" + i;
			File d = new File(dir);
			d.mkdirs();

			dcb.setIPv4Address(localAddr);
			dcb.setListeningPort(BOOTSTRAP_NODES_PORT_START + i);
			dcb.setStoragePath(dir);

			var config = dcb.build();
			var bootstrap = new Node(config);
			bootstrap.setScheduler(testScheduler);
			bootstrap.start();

			bootstrapNodes.add(bootstrap);
			bootstraps.add(bootstrap.getNodeInfo4());
		}

		int i = 0;
		for (var node : bootstrapNodes) {
			System.out.printf("\n\n\007⌛ Bootstraping the bootstrap node %d - %s ...\n", i, node.getId());
			node.bootstrap(bootstraps);
			TimeUnit.SECONDS.sleep(20);
			System.out.printf("\007🟢 The bootstrap node %d - %s is ready ...\n", i++, node.getId());
		}
	}

	private static void stopBootstraps() {
		System.out.println("\n\n\007🟢 Stopping all the bootstrap nodes %d ...\n");

		for (var node : bootstrapNodes)
			node.stop();
	}

	private static void startTestNodes() throws Exception {
		for (int i = 0; i < TEST_NODES; i++) {
			System.out.format("\007🟢 Starting the test node %d ...\n", i);

			String dir = workingDir + File.separator + "nodes"  + File.separator + "node-" + i;
			File d = new File(dir);
			d.mkdirs();

			dcb.setIPv4Address(localAddr);
			dcb.setListeningPort(TEST_NODES_PORT_START + i);
			// dcb.setStoragePath(dir);
			dcb.addBootstrap(bootstraps);

			var config = dcb.build();
			var node = new Node(config);
			node.setScheduler(testScheduler);
			CompletableFuture<Void> future = new CompletableFuture<>();
			node.addConnectionStatusListener(new ConnectionStatusListener() {
				@Override
				public void profound(Network network) {
					future.complete(null);
				}
			});
			node.start();

			testNodes.add(node);
			System.out.printf("\007⌛ Wainting for the test node %d - %s ready ...\n", i, node.getId());
			future.get();

			System.gc();
			System.runFinalization();
		}

		System.out.println("\n\n\007⌛ Wainting for all the test nodes ready ...");
		TimeUnit.SECONDS.sleep(60);
	}

	private static void stopTestNodes() {
		System.out.println("\n\n\007🟢 Stopping all the test nodes %d ...\n");

		for (var node : testNodes)
			node.stop();
	}

	private static void dumpRoutingTables() throws IOException {
		for (int i = 0; i < bootstrapNodes.size(); i++) {
			var node = bootstrapNodes.get(i);
			System.out.format("\007🟢 Dumping the routing table of nodes %s ...\n", node.getId());
			var routingtable = node.toString();
			var file = workingDir + File.separator + "bootstraps"  + File.separator + "node-" + i + File.separator + "routingtable";
			try (var out = new FileWriter(file)) {
				out.write(routingtable);
			}
		}

		for (int i = 0; i < testNodes.size(); i++) {
			var node = testNodes.get(i);
			System.out.format("\007🟢 Dumping the routing table of nodes %s ...\n", node.getId());
			var routingtable = node.toString();
			var file = workingDir + File.separator + "nodes"  + File.separator + "node-" + i + File.separator + "routingtable";
			try (var out = new FileWriter(file)) {
				out.write(routingtable);
			}
		}
	}

	@BeforeAll
	@Timeout(value = BOOTSTRAP_NODES + TEST_NODES + 1, unit = TimeUnit.MINUTES)
	static void setup() throws Exception {
		prepareWorkingDirectory();
		createTestScheduler();
		startBootstraps();
		startTestNodes();

		System.out.println("\n\n\007🟢 All the nodes are ready!!! starting to run the test cases");

	}

	@AfterAll
	static void tearDown() throws Exception {
		dumpRoutingTables();
		stopTestNodes();
		stopBootstraps();
		testScheduler.shutdown();
	}

	@Test
	@Timeout(value = TEST_NODES, unit = TimeUnit.MINUTES)
	void testFindNode() throws Exception {
		for (int i = 0; i < TEST_NODES; i++) {
			var target = testNodes.get(i);
			System.out.format("\n\n\007🟢 Looking up node %s ...\n", target.getId());

			for (int j = 0; j < TEST_NODES; j++) {
				var node = testNodes.get(j);
				System.out.format("\n\n\007⌛ %s looking up node %s ...\n", node.getId(), target.getId());
				var nis = node.findNode(target.getId()).get();
				System.out.format("\007🟢 %s lookup node %s finished\n", node.getId(), target.getId());

				assertNotNull(nis);
				assertFalse(nis.isEmpty());
				assertEquals(target.getNodeInfo4(), nis.get(0));
			}
		}
	}

	@Test
	@Timeout(value = TEST_NODES, unit = TimeUnit.MINUTES)
	void testAnnounceAndFindPeer() throws Exception {
		for (int i = 0; i < TEST_NODES; i++) {
			var announcer = testNodes.get(i);
			var p = PeerInfo.create(announcer.getId(), 8888);

			System.out.format("\n\n\007🟢 %s announce peer %s ...\n", announcer.getId(), p.getId());
			announcer.announcePeer(p).get();

			System.out.format("\n\n\007🟢 Looking up peer %s ...\n", p.getId());
			for (int j = 0; j < TEST_NODES; j++) {
				var node = testNodes.get(j);
				System.out.format("\n\n\007⌛ %s looking up peer %s ...\n", node.getId(), p.getId());
				var result = node.findPeer(p.getId(), 0).get();
				System.out.format("\007🟢 %s lookup peer %s finished\n", node.getId(), p.getId());

				assertNotNull(result);
				assertFalse(result.isEmpty());
				assertEquals(1, result.size());
				assertEquals(p, result.get(0));
			}
		}
	}

	@Test
	@Timeout(value = TEST_NODES, unit = TimeUnit.MINUTES)
	void testStoreAndFindValue() throws Exception {
		for (int i = 0; i < TEST_NODES; i++) {
			var announcer = testNodes.get(i);
			var v = Value.createValue(("Hello from " + announcer.getId()).getBytes());

			System.out.format("\n\n\007🟢 %s store value %s ...\n", announcer.getId(), v.getId());
			announcer.storeValue(v).get();

			System.out.format("\n\n\007🟢 Looking up value %s ...\n", v.getId());
			for (int j = 0; j < TEST_NODES; j++) {
				var node = testNodes.get(j);
				System.out.format("\n\n\007⌛ %s looking up value %s ...\n", node.getId(), v.getId());
				var result = node.findValue(v.getId()).get();
				System.out.format("\007🟢 %s lookup value %s finished\n", node.getId(), v.getId());

				assertNotNull(result);
				assertEquals(v, result);
			}
		}
	}

	@Test
	@Timeout(value = TEST_NODES, unit = TimeUnit.MINUTES)
	void testUpdateAndFindSignedValue() throws Exception {
		var values = new ArrayList<Value>(TEST_NODES);

		// initial announcement
		for (int i = 0; i < TEST_NODES; i++) {
			var announcer = testNodes.get(i);
			var peerKeyPair = KeyPair.random();
			var nonce = Nonce.random();
			var v = Value.createSignedValue(peerKeyPair, nonce, ("Hello from " + announcer.getId()).getBytes());
			values.add(v);

			System.out.format("\n\n\007🟢 %s store value %s ...\n", announcer.getId(), v.getId());
			announcer.storeValue(v).get();

			System.out.format("\n\n\007🟢 Looking up value %s ...\n", v.getId());
			for (int j = 0; j < TEST_NODES; j++) {
				var node = testNodes.get(j);
				System.out.format("\n\n\007⌛ %s looking up value %s ...\n", node.getId(), v.getId());
				var result = node.findValue(v.getId()).get();
				System.out.format("\007🟢 %s lookup value %s finished\n", node.getId(), v.getId());

				assertNotNull(result);
				assertArrayEquals(nonce.bytes(), v.getNonce());
				assertArrayEquals(peerKeyPair.publicKey().bytes(), v.getPublicKey().bytes());
				assertTrue(v.isMutable());
				assertTrue(v.isValid());
				assertEquals(v, result);
			}
		}

		// update announcement
		for (int i = 0; i < TEST_NODES; i++) {
			var announcer = testNodes.get(i);
			var v = values.get(i);
			v = v.update(("Updated value from " + announcer.getId()).getBytes());
			values.set(i, v);

			System.out.format("\n\n\007🟢 %s update value %s ...\n", announcer.getId(), v.getId());
			announcer.storeValue(v).get();

			System.out.format("\n\n\007🟢 Looking up value %s ...\n", v.getId());
			for (int j = 0; j < TEST_NODES; j++) {
				var node = testNodes.get(j);
				System.out.format("\n\n\007⌛ %s looking up value %s ...\n", node.getId(), v.getId());
				var result = node.findValue(v.getId()).get();
				System.out.format("\007🟢 %s lookup value %s finished\n", node.getId(), v.getId());

				assertNotNull(result);
				assertTrue(v.isMutable());
				assertTrue(v.isValid());
				assertEquals(v, result);
			}
		}
	}

	@Test
	@Timeout(value = TEST_NODES, unit = TimeUnit.MINUTES)
	void testUpdateAndFindEncryptedValue() throws Exception {
		var values = new ArrayList<Value>(TEST_NODES);
		var recipients = new ArrayList<KeyPair>(TEST_NODES);

		// initial announcement
		for (int i = 0; i < TEST_NODES; i++) {
			var announcer = testNodes.get(i);
			var recipient = KeyPair.random();
			recipients.add(recipient);

			var peerKeyPair = KeyPair.random();
			var nonce = Nonce.random();
			var data = ("Hello from " + announcer.getId()).getBytes();
			var v = Value.createEncryptedValue(peerKeyPair, Id.of(recipient.publicKey().bytes()), nonce, data);
			values.add(v);

			System.out.format("\n\n\007🟢 %s store value %s ...\n", announcer.getId(), v.getId());
			announcer.storeValue(v).get();

			System.out.format("\n\n\007🟢 Looking up value %s ...\n", v.getId());
			for (int j = 0; j < TEST_NODES; j++) {
				var node = testNodes.get(j);
				System.out.format("\n\n\007⌛ %s looking up value %s ...\n", node.getId(), v.getId());
				var result = node.findValue(v.getId()).get();
				System.out.format("\007🟢 %s lookup value %s finished\n", node.getId(), v.getId());

				assertNotNull(result);
				assertArrayEquals(nonce.bytes(), v.getNonce());
				assertArrayEquals(peerKeyPair.publicKey().bytes(), v.getPublicKey().bytes());
				assertTrue(v.isMutable());
				assertTrue(v.isEncrypted());
				assertTrue(v.isValid());
				assertEquals(v, result);

				var d = v.decryptData(recipient.privateKey());
				assertArrayEquals(data, d);
			}
		}

		// update announcement
		for (int i = 0; i < TEST_NODES; i++) {
			var announcer = testNodes.get(i);
			var recipient = recipients.get(i);

			var v = values.get(i);
			var data = ("Updated value from " + announcer.getId()).getBytes();
			v = v.update(data);
			values.set(i, v);

			System.out.format("\n\n\007🟢 %s update value %s ...\n", announcer.getId(), v.getId());
			announcer.storeValue(v).get();

			System.out.format("\n\n\007🟢 Looking up value %s ...\n", v.getId());
			for (int j = 0; j < TEST_NODES; j++) {
				var node = testNodes.get(j);
				System.out.format("\n\n\007⌛ %s looking up value %s ...\n", node.getId(), v.getId());
				var result = node.findValue(v.getId()).get();
				System.out.format("\007🟢 %s lookup value %s finished\n", node.getId(), v.getId());

				assertNotNull(result);
				assertTrue(v.isMutable());
				assertTrue(v.isEncrypted());
				assertTrue(v.isValid());
				assertEquals(v, result);

				var d = v.decryptData(recipient.privateKey());
				assertArrayEquals(data, d);
			}
		}
	}
}
