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

package elastos.carrier.kademlia.messages;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import elastos.carrier.Id;
import elastos.carrier.NodeInfo;
import elastos.carrier.kademlia.messages.Message.Method;
import elastos.carrier.kademlia.messages.Message.Type;
import elastos.carrier.utils.ThreadLocals;

public class FindValueTests extends MessageTests {
	@Test
	public void testFindValueRequestSize() throws Exception {
		FindValueRequest msg = new FindValueRequest(Id.random());
		msg.setId(Id.random());
		msg.setTxid(0x87654321);
		msg.setVersion(VERSION);
		msg.setWant4(true);
		msg.setWant6(true);
		msg.setSequenceNumber(ThreadLocals.random().nextInt(1, 0x7fffffff));
		byte[] bin = msg.serialize();
		printMessage(msg, bin);
		assertTrue(bin.length <= msg.estimateSize());
	}

	@Test
	public void testFindValueRequest4() throws Exception {
		Id id = Id.random();
		Id target = Id.random();
		int txid = ThreadLocals.random().nextInt();
		int seq = ThreadLocals.random().nextInt(0, 0x7FFFFFFF);

		FindValueRequest msg = new FindValueRequest(target);
		msg.setId(id);
		msg.setTxid(txid);
		msg.setVersion(VERSION);
		msg.setWant4(true);
		msg.setWant6(false);
		msg.setSequenceNumber(seq);

		byte[] bin = msg.serialize();
		assertTrue(bin.length <= msg.estimateSize());

		printMessage(msg, bin);

		Message pm = Message.parse(bin);
		assertTrue(pm instanceof FindValueRequest);
		FindValueRequest m = (FindValueRequest)pm;

		assertEquals(Type.REQUEST, m.getType());
		assertEquals(Method.FIND_VALUE, m.getMethod());
		assertEquals(id, m.getId());
		assertEquals(txid, m.getTxid());
		assertEquals(VERSION_STR, m.getReadableVersion());
		assertEquals(target, m.getTarget());
		assertTrue(m.doesWant4());
		assertFalse(m.doesWant6());
		assertEquals(seq, m.getSequenceNumber());
	}

	@Test
	public void testFindValueRequest6() throws Exception {
		Id id = Id.random();
		Id target = Id.random();
		int txid = ThreadLocals.random().nextInt();
		int seq = ThreadLocals.random().nextInt(0, 0x7FFFFFFF);

		FindValueRequest msg = new FindValueRequest(target);
		msg.setId(id);
		msg.setTxid(txid);
		msg.setWant4(false);
		msg.setWant6(true);
		msg.setSequenceNumber(seq);

		byte[] bin = msg.serialize();
		assertTrue(bin.length <= msg.estimateSize());

		printMessage(msg, bin);

		Message pm = Message.parse(bin);
		assertTrue(pm instanceof FindValueRequest);
		FindValueRequest m = (FindValueRequest)pm;

		assertEquals(Type.REQUEST, m.getType());
		assertEquals(Method.FIND_VALUE, m.getMethod());
		assertEquals(id, m.getId());
		assertEquals(txid, m.getTxid());
		assertEquals(target, m.getTarget());
		assertFalse(m.doesWant4());
		assertTrue(m.doesWant6());
		assertEquals(seq, m.getSequenceNumber());
	}

	@Test
	public void testFindValueRequest46() throws Exception {
		Id id = Id.random();
		Id target = Id.random();
		int txid = ThreadLocals.random().nextInt();
		int seq = ThreadLocals.random().nextInt(0, 0x7FFFFFFF);

		FindValueRequest msg = new FindValueRequest(target);
		msg.setId(id);
		msg.setTxid(txid);
		msg.setWant4(true);
		msg.setWant6(true);
		msg.setSequenceNumber(seq);

		byte[] bin = msg.serialize();
		assertTrue(bin.length <= msg.estimateSize());

		printMessage(msg, bin);

		Message pm = Message.parse(bin);
		assertTrue(pm instanceof FindValueRequest);
		FindValueRequest m = (FindValueRequest)pm;

		assertEquals(Type.REQUEST, m.getType());
		assertEquals(Method.FIND_VALUE, m.getMethod());
		assertEquals(id, m.getId());
		assertEquals(txid, m.getTxid());
		assertEquals(target, m.getTarget());
		assertTrue(m.doesWant4());
		assertTrue(m.doesWant6());
		assertEquals(seq, m.getSequenceNumber());
	}

	@Test
	public void testFindValueResponseSize() throws Exception {
		List<NodeInfo> nodes4 = new ArrayList<>();
		nodes4.add(new NodeInfo(Id.random(), "251.251.251.251", 65535));
		nodes4.add(new NodeInfo(Id.random(), "251.251.251.251", 65534));
		nodes4.add(new NodeInfo(Id.random(), "251.251.251.251", 65533));
		nodes4.add(new NodeInfo(Id.random(), "251.251.251.251", 65532));
		nodes4.add(new NodeInfo(Id.random(), "251.251.251.251", 65531));
		nodes4.add(new NodeInfo(Id.random(), "251.251.251.251", 65530));
		nodes4.add(new NodeInfo(Id.random(), "251.251.251.251", 65529));
		nodes4.add(new NodeInfo(Id.random(), "251.251.251.251", 65528));

		List<NodeInfo> nodes6 = new ArrayList<>();
		nodes6.add(new NodeInfo(Id.random(), "ffff:ffff:ffff:ffff:ffff:ffff:ffff:ffff", 65535));
		nodes6.add(new NodeInfo(Id.random(), "ffff:ffff:ffff:ffff:ffff:ffff:ffff:ffff", 65534));
		nodes6.add(new NodeInfo(Id.random(), "ffff:ffff:ffff:ffff:ffff:ffff:ffff:ffff", 65533));
		nodes6.add(new NodeInfo(Id.random(), "ffff:ffff:ffff:ffff:ffff:ffff:ffff:ffff", 65532));
		nodes6.add(new NodeInfo(Id.random(), "ffff:ffff:ffff:ffff:ffff:ffff:ffff:ffff", 65531));
		nodes6.add(new NodeInfo(Id.random(), "ffff:ffff:ffff:ffff:ffff:ffff:ffff:ffff", 65530));
		nodes6.add(new NodeInfo(Id.random(), "ffff:ffff:ffff:ffff:ffff:ffff:ffff:ffff", 65529));
		nodes6.add(new NodeInfo(Id.random(), "ffff:ffff:ffff:ffff:ffff:ffff:ffff:ffff", 65528));

		byte[] nonce = new byte[24];
		Arrays.fill(nonce, (byte)'N');
		byte[] sig = new byte[64];
		Arrays.fill(sig, (byte)'S');
		byte[] value = new byte[1025];
		Arrays.fill(value, (byte)'D');

		FindValueResponse msg = new FindValueResponse(0xF7654321);
		msg.setId(Id.random());
		msg.setVersion(VERSION);
		msg.setNodes4(nodes4);
		msg.setNodes6(nodes6);
		msg.setPublicKey(Id.random());
		msg.setRecipient(Id.random());
		msg.setNonce(nonce);
		msg.setSequenceNumber(0x77654321);
		msg.setSignature(sig);
		msg.setToken(0xF8765432);
		msg.setValue(value);

		byte[] bin = msg.serialize();
		printMessage(msg, bin);
		assertTrue(bin.length <= msg.estimateSize());
	}

	@Test
	public void testFindValueResponse4() throws Exception {
		Id id = Id.random();
		int txid = ThreadLocals.random().nextInt(1, 0x7FFFFFFF);
		Id pk = Id.random();
		Id recipient = Id.random();
		byte[] nonce = new byte[24];
		ThreadLocals.random().nextBytes(nonce);
		int cas = ThreadLocals.random().nextInt(0, 0x7FFFFFFF);
		int seq = cas + 1;
		byte[] sig = new byte[64];
		ThreadLocals.random().nextBytes(sig);
		int token = ThreadLocals.random().nextInt();
		byte[] value = new byte[1025];
		ThreadLocals.random().nextBytes(value);

		List<NodeInfo> nodes4 = new ArrayList<>();
		nodes4.add(new NodeInfo(Id.random(), "251.251.251.251", 65535));
		nodes4.add(new NodeInfo(Id.random(), "192.168.1.2", 1232));
		nodes4.add(new NodeInfo(Id.random(), "192.168.1.3", 1233));
		nodes4.add(new NodeInfo(Id.random(), "192.168.1.4", 1234));
		nodes4.add(new NodeInfo(Id.random(), "192.168.1.5", 1235));

		FindValueResponse msg = new FindValueResponse(txid);
		msg.setId(id);
		msg.setVersion(VERSION);
		msg.setNodes4(nodes4);
		msg.setPublicKey(pk);
		msg.setRecipient(recipient);
		msg.setNonce(nonce);
		msg.setSequenceNumber(seq);
		msg.setSignature(sig);
		msg.setToken(token);
		msg.setValue(value);

		byte[] bin = msg.serialize();
		assertTrue(bin.length <= msg.estimateSize());

		printMessage(msg, bin);

		Message pm = Message.parse(bin);
		assertTrue(pm instanceof FindValueResponse);
		FindValueResponse m = (FindValueResponse)pm;

		assertEquals(Type.RESPONSE, m.getType());
		assertEquals(Method.FIND_VALUE, m.getMethod());
		assertEquals(id, m.getId());
		assertEquals(txid, m.getTxid());
		assertEquals(VERSION_STR, m.getReadableVersion());
		assertTrue(m.getNodes6().isEmpty());
		assertFalse(m.getNodes4().isEmpty());
		assertEquals(pk, m.getPublicKey());
		assertEquals(recipient, m.getRecipient());
		assertArrayEquals(nonce, m.getNonce());
		assertEquals(seq, m.getSequenceNumber());
		assertArrayEquals(sig, m.getSignature());
		assertEquals(token, m.getToken());
		assertArrayEquals(value, m.getValue());

		List<NodeInfo> nodes = m.getNodes4();
		assertArrayEquals(nodes4.toArray(), nodes.toArray());
	}

	@Test
	public void testFindValueResponse4Immutable() throws Exception {
		Id id = Id.random();
		int txid = ThreadLocals.random().nextInt(1, 0x7FFFFFFF);
		byte[] nonce = new byte[24];
		ThreadLocals.random().nextBytes(nonce);
		int token = ThreadLocals.random().nextInt();
		byte[] value = new byte[1025];
		ThreadLocals.random().nextBytes(value);

		List<NodeInfo> nodes4 = new ArrayList<>();
		nodes4.add(new NodeInfo(Id.random(), "251.251.251.251", 65535));
		nodes4.add(new NodeInfo(Id.random(), "192.168.1.2", 1232));
		nodes4.add(new NodeInfo(Id.random(), "192.168.1.3", 1233));
		nodes4.add(new NodeInfo(Id.random(), "192.168.1.4", 1234));
		nodes4.add(new NodeInfo(Id.random(), "192.168.1.5", 1235));

		FindValueResponse msg = new FindValueResponse(txid);
		msg.setId(id);
		msg.setVersion(VERSION);
		msg.setNodes4(nodes4);
		msg.setNonce(nonce);
		msg.setToken(token);
		msg.setValue(value);

		byte[] bin = msg.serialize();
		assertTrue(bin.length <= msg.estimateSize());

		printMessage(msg, bin);

		Message pm = Message.parse(bin);
		assertTrue(pm instanceof FindValueResponse);
		FindValueResponse m = (FindValueResponse)pm;

		assertEquals(Type.RESPONSE, m.getType());
		assertEquals(Method.FIND_VALUE, m.getMethod());
		assertEquals(id, m.getId());
		assertEquals(txid, m.getTxid());
		assertEquals(VERSION_STR, m.getReadableVersion());
		assertTrue(m.getNodes6().isEmpty());
		assertFalse(m.getNodes4().isEmpty());
		assertArrayEquals(nonce, m.getNonce());
		assertEquals(token, m.getToken());
		assertArrayEquals(value, m.getValue());

		List<NodeInfo> nodes = m.getNodes4();
		assertArrayEquals(nodes4.toArray(), nodes.toArray());
	}

	@Test
	public void testFindValueResponse6() throws Exception {
		Id id = Id.random();
		int txid = ThreadLocals.random().nextInt(1, 0x7FFFFFFF);
		Id pk = Id.random();
		Id recipient = Id.random();
		byte[] nonce = new byte[24];
		ThreadLocals.random().nextBytes(nonce);
		int cas = ThreadLocals.random().nextInt(0, 0x7FFFFFFF);
		int seq = cas + 1;
		byte[] sig = new byte[64];
		ThreadLocals.random().nextBytes(sig);
		int token = ThreadLocals.random().nextInt();
		byte[] value = new byte[1025];
		ThreadLocals.random().nextBytes(value);

		List<NodeInfo> nodes6 = new ArrayList<>();
		nodes6.add(new NodeInfo(Id.random(), "2001:0db8:85a3:8070:6543:8a2e:0370:7334", 65535));
		nodes6.add(new NodeInfo(Id.random(), "2001:0db8:85a3:0000:0000:8a2e:0370:7332", 1232));
		nodes6.add(new NodeInfo(Id.random(), "2001:0db8:85a3:0000:0000:8a2e:0370:7333", 1233));
		nodes6.add(new NodeInfo(Id.random(), "2001:0db8:85a3:0000:0000:8a2e:0370:7334", 1234));
		nodes6.add(new NodeInfo(Id.random(), "2001:0db8:85a3:0000:0000:8a2e:0370:7335", 1235));

		FindValueResponse msg = new FindValueResponse(txid);
		msg.setId(id);
		msg.setVersion(VERSION);
		msg.setNodes6(nodes6);
		msg.setPublicKey(pk);
		msg.setRecipient(recipient);
		msg.setNonce(nonce);
		msg.setSequenceNumber(seq);
		msg.setSignature(sig);
		msg.setToken(token);
		msg.setValue(value);

		byte[] bin = msg.serialize();
		assertTrue(bin.length <= msg.estimateSize());

		printMessage(msg, bin);

		Message pm = Message.parse(bin);
		assertTrue(pm instanceof FindValueResponse);
		FindValueResponse m = (FindValueResponse)pm;

		assertEquals(Type.RESPONSE, m.getType());
		assertEquals(Method.FIND_VALUE, m.getMethod());
		assertEquals(id, m.getId());
		assertEquals(txid, m.getTxid());
		assertEquals(VERSION_STR, m.getReadableVersion());
		assertTrue(m.getNodes4().isEmpty());
		assertFalse(m.getNodes6().isEmpty());
		assertEquals(pk, m.getPublicKey());
		assertEquals(recipient, m.getRecipient());
		assertArrayEquals(nonce, m.getNonce());
		assertEquals(seq, m.getSequenceNumber());
		assertArrayEquals(sig, m.getSignature());
		assertEquals(token, m.getToken());
		assertArrayEquals(value, m.getValue());

		List<NodeInfo> nodes = m.getNodes6();
		assertArrayEquals(nodes6.toArray(), nodes.toArray());
	}

	@Test
	public void testFindValueResponse46() throws Exception {
		Id id = Id.random();
		int txid = ThreadLocals.random().nextInt(1, 0x7FFFFFFF);
		Id pk = Id.random();
		Id recipient = Id.random();
		byte[] nonce = new byte[24];
		ThreadLocals.random().nextBytes(nonce);
		int cas = ThreadLocals.random().nextInt(0, 0x7FFFFFFF);
		int seq = cas + 1;
		byte[] sig = new byte[64];
		ThreadLocals.random().nextBytes(sig);
		int token = ThreadLocals.random().nextInt();
		byte[] value = new byte[1025];
		ThreadLocals.random().nextBytes(value);

		List<NodeInfo> nodes4 = new ArrayList<>();
		nodes4.add(new NodeInfo(Id.random(), "251.251.251.251", 65535));
		nodes4.add(new NodeInfo(Id.random(), "192.168.1.2", 1232));
		nodes4.add(new NodeInfo(Id.random(), "192.168.1.3", 1233));
		nodes4.add(new NodeInfo(Id.random(), "192.168.1.4", 1234));
		nodes4.add(new NodeInfo(Id.random(), "192.168.1.5", 1235));

		List<NodeInfo> nodes6 = new ArrayList<>();
		nodes6.add(new NodeInfo(Id.random(), "2001:0db8:85a3:8070:6543:8a2e:0370:7334", 65535));
		nodes6.add(new NodeInfo(Id.random(), "2001:0db8:85a3:0000:0000:8a2e:0370:7332", 1232));
		nodes6.add(new NodeInfo(Id.random(), "2001:0db8:85a3:0000:0000:8a2e:0370:7333", 1233));
		nodes6.add(new NodeInfo(Id.random(), "2001:0db8:85a3:0000:0000:8a2e:0370:7334", 1234));
		nodes6.add(new NodeInfo(Id.random(), "2001:0db8:85a3:0000:0000:8a2e:0370:7335", 1235));

		FindValueResponse msg = new FindValueResponse(txid);
		msg.setId(id);
		msg.setNodes4(nodes4);
		msg.setNodes6(nodes6);
		msg.setPublicKey(pk);
		msg.setRecipient(recipient);
		msg.setNonce(nonce);
		msg.setSequenceNumber(seq);
		msg.setSignature(sig);
		msg.setToken(token);
		msg.setValue(value);

		byte[] bin = msg.serialize();
		assertTrue(bin.length <= msg.estimateSize());

		printMessage(msg, bin);

		Message pm = Message.parse(bin);
		assertTrue(pm instanceof FindValueResponse);
		FindValueResponse m = (FindValueResponse)pm;

		assertEquals(Type.RESPONSE, m.getType());
		assertEquals(Method.FIND_VALUE, m.getMethod());
		assertEquals(id, m.getId());
		assertEquals(txid, m.getTxid());
		assertEquals(0, m.getVersion());
		assertNotNull(m.getNodes4());
		assertNotNull(m.getNodes6());
		assertEquals(pk, m.getPublicKey());
		assertEquals(recipient, m.getRecipient());
		assertArrayEquals(nonce, m.getNonce());
		assertEquals(seq, m.getSequenceNumber());
		assertArrayEquals(sig, m.getSignature());
		assertEquals(token, m.getToken());
		assertArrayEquals(value, m.getValue());

		List<NodeInfo> nodes = m.getNodes4();
		assertArrayEquals(nodes4.toArray(), nodes.toArray());

		nodes = m.getNodes6();
		assertArrayEquals(nodes6.toArray(), nodes.toArray());
	}
}
