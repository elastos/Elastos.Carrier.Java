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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.lang.reflect.Method;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;

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
	@ValueSource(classes = { MapDBStorage.class, SQLiteStorage.class })
	public void testPutAndGetValue(Class<? extends DataStorage> clazz) throws Exception {
		DataStorage ds = open(clazz);

		Queue<Id> ids = new LinkedList<>();
		byte[] data = new byte[1024];

		System.out.println("Writing values...");
		for (int i = 1; i <= 256; i++) {
			Arrays.fill(data, (byte)(i % (126 - 32) + 33));
			Value v = Value.of(data);

			ids.add(v.getId());
			ds.putValue(v, -1);

			System.out.print(".");
			if (i % 16 == 0)
				System.out.println();
		}

		System.out.println("\nReading values...");
		for (int i = 1; i <= 256; i++) {
			Value v = ds.getValue(ids.poll());
			assertNotNull(v);

			assertEquals(1024, v.getData().length);
			assertEquals((byte)(i % (126 - 32) + 33), v.getData()[0]);

			System.out.print(".");
			if (i % 16 == 0)
				System.out.println();
		}

		ds.close();
	}

	@ParameterizedTest
	@ValueSource(classes = { MapDBStorage.class, SQLiteStorage.class })
	public void testUpdateSignedValue(Class<? extends DataStorage> clazz) throws Exception {
		DataStorage ds = open(clazz);

		Signature.KeyPair keypair = Signature.KeyPair.random();

		Id pk = Id.of(keypair.publicKey().bytes());
		CryptoBox.Nonce nonce = CryptoBox.Nonce.random();
		byte[] data = "Hello world".getBytes();
		byte[] newData = "Foo bar".getBytes();
		int seq = 10;


		// new value: success
		Value v = Value.of(keypair, nonce, seq, data);
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
		Value v1 = Value.of(keypair, nonce, seq - 1, newData);
		assertEquals(valueId, v.getId());
		assertThrows(SequenceNotMonotonic.class, () -> {
			ds.putValue(v1, 10);
		});

		// update: CAS fail
		Value v2 = Value.of(keypair, nonce, seq + 1, newData);
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
		v = Value.of(keypair, nonce, seq + 1, newData);
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
	@ValueSource(classes = { MapDBStorage.class, SQLiteStorage.class })
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
		Value v = Value.of(keypair, recipient, nonce, seq, data);
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
		Value v1 = Value.of(keypair, recipient, nonce, seq - 1, newData);
		assertEquals(valueId, v.getId());
		assertThrows(SequenceNotMonotonic.class, () -> {
			ds.putValue(v1, 10);
		});

		// update: CAS fail
		Value v2 = Value.of(keypair, recipient, nonce, seq + 1, newData);
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
		v = Value.of(keypair, recipient, nonce, seq + 1, newData);
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
	@ValueSource(classes = { MapDBStorage.class, SQLiteStorage.class })
	public void testPutAndGetPeer(Class<? extends DataStorage> clazz) throws Exception {
		DataStorage ds = open(clazz);

		Queue<Id> ids = new LinkedList<>();

		int basePort = 8000;
		byte[] sig = new byte[64];

		System.out.println("Writing peers...");
		for (int i = 1; i <= 64; i++) {
			Id id = Id.random();
			ids.add(id);

			List<PeerInfo> peers = new ArrayList<PeerInfo>();
			for (int j = 0; j < i; j++) {
				new SecureRandom().nextBytes(sig);
				PeerInfo pi = new PeerInfo(Id.random(), basePort + i, PeerInfo.AF_IPV4,  "", sig);
				peers.add(pi);

				new SecureRandom().nextBytes(sig);
				pi = new PeerInfo(Id.random(), Id.random(), basePort + i, PeerInfo.AF_IPV6, "testPutAndGetPeer", sig);
				peers.add(pi);
			}
			ds.putPeer(id, peers);

			System.out.print(".");
			if (i % 16 == 0)
				System.out.println();
		}

		System.out.println("\nReading peers...");
		for (int i = 1; i <= 64; i++) {
			Id id = ids.poll();

			// all
			List<PeerInfo> ps = ds.getPeer(id, 10, 2 * i + 16);
			assertNotNull(ps);
			assertEquals(2 * i, ps.size());
			for (PeerInfo pi : ps)
				assertEquals(basePort + i, pi.getPort());

			ps = ds.getPeer(id, 4, i + 16);
			assertNotNull(ps);
			assertEquals(i, ps.size());
			for (PeerInfo pi : ps)
				assertEquals(basePort + i, pi.getPort());

			ps = ds.getPeer(id, 6, i + 16);
			assertNotNull(ps);
			assertEquals(i, ps.size());
			for (PeerInfo pi : ps)
				assertEquals(basePort + i, pi.getPort());

			// limited
			ps = ds.getPeer(id, 10, 32);
			assertNotNull(ps);
			assertEquals(Math.min(2 * i, 64), ps.size());
			for (PeerInfo pi : ps)
				assertEquals(basePort + i, pi.getPort());

			ps = ds.getPeer(id, 4, 32);
			assertNotNull(ps);
			assertEquals(Math.min(i, 32), ps.size());
			for (PeerInfo pi : ps)
				assertEquals(basePort + i, pi.getPort());

			ps = ds.getPeer(id, 6, 32);
			assertNotNull(ps);
			assertEquals(Math.min(i, 32), ps.size());
			for (PeerInfo pi : ps)
				assertEquals(basePort + i, pi.getPort());

			System.out.print(".");
			if (i % 16 == 0)
				System.out.println();
		}

		ds.close();
	}

	/*
	@Test
	public void testWriteToken() throws IOException {
		DataStorage ds = MapDBStorage.open(getStorageFile(true));

		Id nodeId = Id.randomId();
		Id targetId = Id.randomId();
		InetAddress addr = InetAddress.getByName("123.1.2.3");
		int port = 8000;

		int token = ds.generateToken(nodeId, addr, port, targetId);
		assertTrue(ds.verifyToken(token, nodeId, addr, port, targetId));

		assertFalse(ds.verifyToken(token, Id.randomId(), addr, port, targetId));
		assertFalse(ds.verifyToken(token, nodeId, addr, port, Id.randomId()));
		assertFalse(ds.verifyToken(token, nodeId, addr, port + 1, targetId));
		assertFalse(ds.verifyToken(token, nodeId, InetAddress.getByName("123.3.2.1"), port, targetId));

		ds.close();
	}
	*/
}
