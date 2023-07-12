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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.lang.reflect.Method;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import elastos.carrier.Id;
import elastos.carrier.PeerInfo;
import elastos.carrier.Value;
import elastos.carrier.crypto.CryptoBox;
import elastos.carrier.crypto.Signature;
import elastos.carrier.kademlia.exceptions.CasFail;
import elastos.carrier.kademlia.exceptions.SequenceNotMonotonic;
import elastos.carrier.utils.ThreadLocals;

public class DataStorageTests {
	private static ScheduledExecutorService scheduler;

	private File getStorageFile() {
		File f = new File(System.getProperty("java.io.tmpdir") + "/carrier.db");

		return f;
	}

	private ScheduledExecutorService getScheduler() {
		if (scheduler == null)
			scheduler = new ScheduledThreadPoolExecutor(4);

		return scheduler;
	}

	@SuppressWarnings("unused")
	private void increment(byte[] value) {
		short c = 1;
		for (int i = value.length - 1; i >= 0; i--) {
			c += (value[i] & 0xff);
			value[i] = (byte)(c & 0xff);
			c >>= 8;

			if (c == 0)
				break;
		}
	}

	@BeforeEach
	public void setup() {
		File f = getStorageFile();
		if (f.exists())
			f.delete();
	}

	private DataStorage open(Class<? extends DataStorage> clazz) throws Exception {
		Method open = clazz.getMethod("open", File.class, ScheduledExecutorService.class);

		Object o= open.invoke(null, getStorageFile(), getScheduler());
		return (DataStorage)o;
	}

	@ParameterizedTest
	@ValueSource(classes = { SQLiteStorage.class })
	public void testPutAndGetValue(Class<? extends DataStorage> clazz) throws Exception {
		DataStorage ds = open(clazz);

		Queue<Id> ids = new LinkedList<>();
		byte[] data = new byte[1024];

		System.out.println("Writing values...");
		for (int i = 1; i <= 256; i++) {
			ThreadLocals.random().nextBytes(data);
			data[0] = (byte)(i % (126 - 32) + 33);
			Value v = Value.of(data);

			ids.add(v.getId());
			ds.putValue(v);

			System.out.print(".");
			if (i % 16 == 0)
				System.out.println();
		}

		System.out.println("\nReading values...");
		for (int i = 1; i <= 256; i++) {
			Id id = ids.poll();

			Value v = ds.getValue(id);
			assertNotNull(v);

			assertEquals(1024, v.getData().length);
			assertEquals((byte)(i % (126 - 32) + 33), v.getData()[0]);

			boolean removed = ds.removeValue(id);
			assertTrue(removed);

			v = ds.getValue(id);
			assertNull(v);

			removed = ds.removeValue(id);
			assertFalse(removed);

			System.out.print(".");
			if (i % 16 == 0)
				System.out.println();
		}

		ds.close();
	}

	@ParameterizedTest
	@ValueSource(classes = { SQLiteStorage.class })
	public void testPutAndGetPersistentValue(Class<? extends DataStorage> clazz) throws Exception {
		DataStorage ds = open(clazz);

		Queue<Id> ids = new LinkedList<>();
		byte[] data = new byte[1024];

		System.out.println("Writing values...");
		for (int i = 1; i <= 256; i++) {
			ThreadLocals.random().nextBytes(data);
			data[0] = (byte)(i % (126 - 32) + 33);
			Value v = Value.of(data);

			ids.add(v.getId());
			ds.putValue(v, i % 2 == 0);

			System.out.print(".");
			if (i % 16 == 0)
				System.out.println();
		}

		long ts = System.currentTimeMillis();
		Stream<Value> vs = ds.getPersistentValues(ts);
		List<Value> values = vs.collect(Collectors.toList());
		assertEquals(128, values.size());

		Thread.sleep(1000);

		System.out.println("\nUpdate the last announced for values...");
		for (int i = 1; i <= 128; i++) {
			Id id = values.get(i-1).getId();

			if (i % 2 == 0)
				ds.updateValueLastAnnounce(id);
		}

		vs = ds.getPersistentValues(ts);
		values = vs.collect(Collectors.toList());
		assertEquals(64, values.size());

		System.out.println("\nReading values...");
		for (int i = 1; i <= 256; i++) {
			Id id = ids.poll();

			Value v = ds.getValue(id);
			assertNotNull(v);

			assertEquals(1024, v.getData().length);
			assertEquals((byte)(i % (126 - 32) + 33), v.getData()[0]);

			boolean removed = ds.removeValue(id);
			assertTrue(removed);

			v = ds.getValue(id);
			assertNull(v);

			removed = ds.removeValue(id);
			assertFalse(removed);

			System.out.print(".");
			if (i % 16 == 0)
				System.out.println();
		}

		ds.close();
	}

	@ParameterizedTest
	@ValueSource(classes = { SQLiteStorage.class })
	public void testUpdateSignedValue(Class<? extends DataStorage> clazz) throws Exception {
		DataStorage ds = open(clazz);

		Signature.KeyPair keypair = Signature.KeyPair.random();

		Id pk = Id.of(keypair.publicKey().bytes());
		CryptoBox.Nonce nonce = CryptoBox.Nonce.random();
		byte[] data = "Hello world".getBytes();
		byte[] newData = "Foo bar".getBytes();
		int seq = 10;


		// new value: success
		Value v = Value.createSignedValue(keypair, nonce, seq, data);
		Id valueId = v.getId();
		System.out.println(valueId);
		ds.putValue(v, 0);

		v = ds.getValue(valueId);
		assertNotNull(v);
		assertEquals(pk, v.getPublicKey());
		assertArrayEquals(nonce.bytes(), v.getNonce());
		assertEquals(seq, v.getSequenceNumber());
		assertArrayEquals(data, v.getData());
		assertTrue(v.isValid());

		// update: invalid sequence number
		Value v1 = Value.createSignedValue(keypair, nonce, seq - 1, newData);
		assertEquals(valueId, v.getId());
		assertThrows(SequenceNotMonotonic.class, () -> {
			ds.putValue(v1, 10);
		});

		// update: CAS fail
		Value v2 = Value.createSignedValue(keypair, nonce, seq + 1, newData);
		assertEquals(valueId, v.getId());
		assertThrows(CasFail.class, () -> {
			ds.putValue(v2, 9);
		});

		// should be the original value
		v = ds.getValue(valueId);
		assertNotNull(v);
		assertEquals(pk, v.getPublicKey());
		assertArrayEquals(nonce.bytes(), v.getNonce());
		assertEquals(seq, v.getSequenceNumber());
		assertArrayEquals(data, v.getData());
		assertTrue(v.isValid());

		// update: success
		// v = Value.createSignedValue(keypair, nonce, seq + 1, newData);
		v = v.update(newData);
		assertEquals(valueId, v.getId());
		ds.putValue(v, 10);

		v = ds.getValue(valueId);
		assertNotNull(v);
		assertEquals(pk, v.getPublicKey());
		assertArrayEquals(nonce.bytes(), v.getNonce());
		assertEquals(seq + 1, v.getSequenceNumber());
		assertArrayEquals(newData, v.getData());
		assertTrue(v.isValid());

		ds.close();
	}

	@ParameterizedTest
	@ValueSource(classes = { SQLiteStorage.class })
	public void testUpdateEncryptedValue(Class<? extends DataStorage> clazz) throws Exception {
		DataStorage ds = open(clazz);

		Signature.KeyPair keypair = Signature.KeyPair.random();
		Signature.KeyPair recKeypair = Signature.KeyPair.random();

		Id pk = Id.of(keypair.publicKey().bytes());
		Id recipient = Id.of(recKeypair.publicKey().bytes());
		CryptoBox.Nonce nonce = CryptoBox.Nonce.random();
		byte[] data = "Hello world".getBytes();
		byte[] newData = "Foo bar".getBytes();
		int seq = 10;


		// new value: success
		Value v = Value.createEncryptedValue(keypair, recipient, nonce, seq, data);
		Id valueId = v.getId();
		System.out.println(valueId);
		ds.putValue(v, 0);

		v = ds.getValue(valueId);
		assertNotNull(v);
		assertEquals(pk, v.getPublicKey());
		assertEquals(recipient, v.getRecipient());
		assertArrayEquals(nonce.bytes(), v.getNonce());
		assertEquals(seq, v.getSequenceNumber());
		assertArrayEquals(data, v.decryptData(recKeypair.privateKey()));
		assertTrue(v.isValid());

		// update: invalid sequence number
		Value v1 = Value.createEncryptedValue(keypair, recipient, nonce, seq - 1, newData);
		assertEquals(valueId, v.getId());
		assertThrows(SequenceNotMonotonic.class, () -> {
			ds.putValue(v1, 10);
		});

		// update: CAS fail
		Value v2 = Value.createEncryptedValue(keypair, recipient, nonce, seq + 1, newData);
		assertEquals(valueId, v.getId());
		assertThrows(CasFail.class, () -> {
			ds.putValue(v2, 9);
		});

		// should be the original value
		v = ds.getValue(valueId);
		assertNotNull(v);
		assertEquals(pk, v.getPublicKey());
		assertEquals(recipient, v.getRecipient());
		assertArrayEquals(nonce.bytes(), v.getNonce());
		assertEquals(seq, v.getSequenceNumber());
		assertArrayEquals(data, v.decryptData(recKeypair.privateKey()));
		assertTrue(v.isValid());

		// update: success
		// v = Value.createEncryptedValue(keypair, recipient, nonce, seq + 1, newData);
		v = v.update(newData);
		assertEquals(valueId, v.getId());
		ds.putValue(v, 10);

		v = ds.getValue(valueId);
		assertNotNull(v);
		assertEquals(pk, v.getPublicKey());
		assertEquals(recipient, v.getRecipient());
		assertArrayEquals(nonce.bytes(), v.getNonce());
		assertEquals(seq + 1, v.getSequenceNumber());
		assertArrayEquals(newData, v.decryptData(recKeypair.privateKey()));
		assertTrue(v.isValid());

		ds.close();
	}

	@ParameterizedTest
	@ValueSource(classes = { SQLiteStorage.class })
	public void testPutAndGetPeer(Class<? extends DataStorage> clazz) throws Exception {
		DataStorage ds = open(clazz);

		Map<Id, List<PeerInfo>> allPeers  = new HashMap<>();

		int basePort = 8000;
		byte[] sig = new byte[64];

		System.out.println("Writing peers...");
		for (int i = 1; i <= 64; i++) {
			Id id = Id.random();

			List<PeerInfo> peers = new ArrayList<PeerInfo>();
			for (int j = 0; j < i; j++) {
				new SecureRandom().nextBytes(sig);
				PeerInfo pi = PeerInfo.of(id, Id.random(), basePort + i, sig);
				peers.add(pi);

				new SecureRandom().nextBytes(sig);
				pi = PeerInfo.of(id, Id.random(), Id.random(), basePort + i, "https://test.pc2.net", sig);
				peers.add(pi);
			}
			ds.putPeer(peers);

			allPeers.put(id, peers);

			System.out.print(".");
			if (i % 16 == 0)
				System.out.println();
		}

		System.out.println("\nReading peers...");
		int total = 0;
		for (Map.Entry<Id, List<PeerInfo>> entry : allPeers.entrySet()) {
			total++;

			Id id = entry.getKey();
			List<PeerInfo> peers = entry.getValue();

			// all
			List<PeerInfo> ps = ds.getPeer(id, peers.size() + 8);
			assertNotNull(ps);
			assertEquals(peers.size(), ps.size());

			Comparator<PeerInfo> c = (a, b) -> {
				int r = a.getNodeId().compareTo(b.getNodeId());
				if (r != 0)
					return r;

				return a.getOrigin().compareTo(b.getOrigin());
			};

			peers.sort(c);
			ps.sort(c);
			assertArrayEquals(peers.toArray(), ps.toArray());

			// limited
			ps = ds.getPeer(id, 16);
			assertNotNull(ps);
			assertEquals(Math.min(16, peers.size()), ps.size());
			for (PeerInfo pi : ps)
				assertEquals(peers.get(0).getPort(), pi.getPort());

			for (PeerInfo p : peers) {
				PeerInfo pi = ds.getPeer(p.getId(), p.getOrigin());
				assertNotNull(pi);
				assertEquals(p, pi);

				boolean removed = ds.removePeer(p.getId(), p.getOrigin());
				assertTrue(removed);

				pi = ds.getPeer(p.getId(), p.getOrigin());
				assertNull(pi);

				removed = ds.removePeer(p.getId(), p.getOrigin());
				assertFalse(removed);
			}

			System.out.print(".");
			if (total % 16 == 0)
				System.out.println();
		}

		assertEquals(64, total);

		ds.close();
	}


	@ParameterizedTest
	@ValueSource(classes = { SQLiteStorage.class })
	public void testPutAndGetPersistentPeer(Class<? extends DataStorage> clazz) throws Exception {
		DataStorage ds = open(clazz);

		Queue<Id> ids = new LinkedList<>();
		Id nodeId = Id.random();
		int basePort = 8000;
		byte[] sig = new byte[64];

		System.out.println("Writing peers...");
		for (int i = 1; i <= 256; i++) {
			new SecureRandom().nextBytes(sig);
			PeerInfo pi = PeerInfo.of(Id.random(), nodeId, basePort + i, sig);

			ids.add(pi.getId());
			ds.putPeer(pi, i % 2 == 0);

			System.out.print(".");
			if (i % 16 == 0)
				System.out.println();
		}

		long ts = System.currentTimeMillis();
		Stream<PeerInfo> ps = ds.getPersistentPeers(ts);
		List<PeerInfo> peers = ps.collect(Collectors.toList());
		assertEquals(128, peers.size());

		Thread.sleep(1000);

		System.out.println("\nUpdate the last announced for peers...");
		for (int i = 1; i <= 128; i++) {
			Id id = peers.get(i-1).getId();

			if (i % 2 == 0)
				ds.updatePeerLastAnnounce(id, nodeId);
		}

		ps = ds.getPersistentPeers(ts);
		peers = ps.collect(Collectors.toList());
		assertEquals(64, peers.size());

		System.out.println("\nReading peers...");
		for (int i = 1; i <= 256; i++) {
			Id id = ids.poll();

			PeerInfo p = ds.getPeer(id, nodeId);
			assertNotNull(p);

			assertEquals(basePort + i, p.getPort());

			boolean removed = ds.removePeer(id, nodeId);
			assertTrue(removed);

			p = ds.getPeer(id, nodeId);
			assertNull(p);

			removed = ds.removePeer(id, nodeId);
			assertFalse(removed);

			System.out.print(".");
			if (i % 16 == 0)
				System.out.println();
		}

		ds.close();
	}
}
