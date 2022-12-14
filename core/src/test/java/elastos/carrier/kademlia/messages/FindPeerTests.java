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
import java.util.List;

import org.junit.jupiter.api.Test;

import elastos.carrier.Id;
import elastos.carrier.NodeInfo;
import elastos.carrier.PeerInfo;
import elastos.carrier.kademlia.messages.Message.Method;
import elastos.carrier.kademlia.messages.Message.Type;
import elastos.carrier.utils.ThreadLocals;

public class FindPeerTests extends MessageTests {
	@Test
	public void testFindPeerRequestSize() throws Exception {
		FindPeerRequest msg = new FindPeerRequest(Id.random());
		msg.setId(Id.random());
		msg.setTxid(0x87654321);
		msg.setVersion(VERSION);
		msg.setWant4(true);
		msg.setWant6(true);
		byte[] bin = msg.serialize();
		printMessage(msg, bin);
		assertTrue(bin.length <= msg.estimateSize());
	}

	@Test
	public void testFindPeerRequest4() throws Exception {
		Id id = Id.random();
		Id target = Id.random();
		int txid = ThreadLocals.random().nextInt();

		FindPeerRequest msg = new FindPeerRequest(target);
		msg.setId(id);
		msg.setTxid(txid);
		msg.setVersion(VERSION);
		msg.setWant4(true);
		msg.setWant6(false);

		byte[] bin = msg.serialize();
		assertTrue(bin.length <= msg.estimateSize());

		printMessage(msg, bin);

		Message pm = Message.parse(bin);
		pm.setId(id);
		assertTrue(pm instanceof FindPeerRequest);
		FindPeerRequest m = (FindPeerRequest)pm;

		assertEquals(Type.REQUEST, m.getType());
		assertEquals(Method.FIND_PEER, m.getMethod());
		assertEquals(id, m.getId());
		assertEquals(txid, m.getTxid());
		assertEquals(VERSION_STR, m.getReadableVersion());
		assertEquals(target, m.getTarget());
		assertTrue(m.doesWant4());
		assertFalse(m.doesWant6());
	}

	@Test
	public void testFindPeerRequest6() throws Exception {
		Id id = Id.random();
		Id target = Id.random();
		int txid = ThreadLocals.random().nextInt();

		FindPeerRequest msg = new FindPeerRequest(target);
		msg.setId(id);
		msg.setTxid(txid);
		msg.setWant4(false);
		msg.setWant6(true);

		byte[] bin = msg.serialize();
		assertTrue(bin.length <= msg.estimateSize());

		printMessage(msg, bin);

		Message pm = Message.parse(bin);
		assertTrue(pm instanceof FindPeerRequest);
		pm.setId(id);
		FindPeerRequest m = (FindPeerRequest)pm;

		assertEquals(Type.REQUEST, m.getType());
		assertEquals(Method.FIND_PEER, m.getMethod());
		assertEquals(id, m.getId());
		assertEquals(txid, m.getTxid());
		assertEquals(target, m.getTarget());
		assertFalse(m.doesWant4());
		assertTrue(m.doesWant6());
	}

	@Test
	public void testFindPeerRequest46() throws Exception {
		Id id = Id.random();
		Id target = Id.random();
		int txid = ThreadLocals.random().nextInt();

		FindPeerRequest msg = new FindPeerRequest(target);
		msg.setId(id);
		msg.setTxid(txid);
		msg.setWant4(true);
		msg.setWant6(true);

		byte[] bin = msg.serialize();
		assertTrue(bin.length <= msg.estimateSize());

		printMessage(msg, bin);

		Message pm = Message.parse(bin);
		pm.setId(id);
		assertTrue(pm instanceof FindPeerRequest);
		FindPeerRequest m = (FindPeerRequest)pm;

		assertEquals(Type.REQUEST, m.getType());
		assertEquals(Method.FIND_PEER, m.getMethod());
		assertEquals(id, m.getId());
		assertEquals(txid, m.getTxid());
		assertEquals(target, m.getTarget());
		assertTrue(m.doesWant4());
		assertTrue(m.doesWant6());
	}

	@Test
	public void testFindPeerResponseSize() throws Exception {
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

		List<PeerInfo> peers4 = new ArrayList<>();
		for (int i = 0; i < 32; i++)
			peers4.add(new PeerInfo(Id.random(), "251.251.251.251", 65535 - i));

		List<PeerInfo> peers6 = new ArrayList<>();
		for (int i = 0; i < 16; i++)
			peers6.add(new PeerInfo(Id.random(), "ffff:ffff:ffff:ffff:ffff:ffff:ffff:ffff", 65535 - i));

		FindPeerResponse msg = new FindPeerResponse(0xF7654321);
		msg.setId(Id.random());
		msg.setVersion(VERSION);
		msg.setNodes4(nodes4);
		msg.setNodes6(nodes6);
		msg.setToken(0x87654321);
		msg.setPeers4(peers4);

		byte[] bin = msg.serialize();
		printMessage(msg, bin);
		assertTrue(bin.length <= msg.estimateSize());

		msg = new FindPeerResponse(0xF7654321);
		msg.setId(Id.random());
		msg.setVersion(VERSION);
		msg.setNodes4(nodes4);
		msg.setNodes6(nodes6);
		msg.setToken(0x87654321);
		msg.setPeers6(peers6);

		bin = msg.serialize();
		printMessage(msg, bin);
		assertTrue(bin.length <= msg.estimateSize());
	}

	@Test
	public void testFindPeerResponse4() throws Exception {
		Id id = Id.random();
		int txid = ThreadLocals.random().nextInt();
		int token = ThreadLocals.random().nextInt();

		List<NodeInfo> nodes4 = new ArrayList<>();
		nodes4.add(new NodeInfo(Id.random(), "251.251.251.251", 65535));
		nodes4.add(new NodeInfo(Id.random(), "192.168.1.2", 1232));
		nodes4.add(new NodeInfo(Id.random(), "192.168.1.3", 1233));
		nodes4.add(new NodeInfo(Id.random(), "192.168.1.4", 1234));
		nodes4.add(new NodeInfo(Id.random(), "192.168.1.5", 1235));

		List<PeerInfo> peers4 = new ArrayList<>();
		for (int i = 0; i < ThreadLocals.random().nextInt(8, 48); i++)
			peers4.add(new PeerInfo(Id.random(), "251.251.251.251", 65535 - i));

		FindPeerResponse msg = new FindPeerResponse(txid);
		msg.setId(id);
		msg.setVersion(VERSION);
		msg.setNodes4(nodes4);
		msg.setToken(token);
		msg.setPeers4(peers4);

		byte[] bin = msg.serialize();
		assertTrue(bin.length <= msg.estimateSize());

		printMessage(msg, bin);

		Message pm = Message.parse(bin);
		pm.setId(id);
		assertTrue(pm instanceof FindPeerResponse);
		FindPeerResponse m = (FindPeerResponse)pm;

		assertEquals(Type.RESPONSE, m.getType());
		assertEquals(Method.FIND_PEER, m.getMethod());
		assertEquals(id, m.getId());
		assertEquals(txid, m.getTxid());
		assertEquals(VERSION_STR, m.getReadableVersion());
		assertEquals(token, m.getToken());
		assertTrue(m.getNodes6().isEmpty());
		assertFalse(m.getNodes4().isEmpty());
		assertFalse(m.getPeers4().isEmpty());

		List<NodeInfo> nodes = m.getNodes4();
		assertArrayEquals(nodes4.toArray(), nodes.toArray());

		List<PeerInfo> peers = m.getPeers4();
		assertArrayEquals(peers4.toArray(), peers.toArray());
	}

	@Test
	public void testFindPeerResponse6() throws Exception {
		Id id = Id.random();
		int txid = ThreadLocals.random().nextInt();
		int token = ThreadLocals.random().nextInt();

		List<NodeInfo> nodes6 = new ArrayList<>();
		nodes6.add(new NodeInfo(Id.random(), "2001:0db8:85a3:8070:6543:8a2e:0370:7334", 65535));
		nodes6.add(new NodeInfo(Id.random(), "2001:0db8:85a3:0000:0000:8a2e:0370:7332", 1232));
		nodes6.add(new NodeInfo(Id.random(), "2001:0db8:85a3:0000:0000:8a2e:0370:7333", 1233));
		nodes6.add(new NodeInfo(Id.random(), "2001:0db8:85a3:0000:0000:8a2e:0370:7334", 1234));
		nodes6.add(new NodeInfo(Id.random(), "2001:0db8:85a3:0000:0000:8a2e:0370:7335", 1235));

		List<PeerInfo> peers6 = new ArrayList<>();
		for (int i = 0; i < ThreadLocals.random().nextInt(8, 48); i++)
			peers6.add(new PeerInfo(Id.random(), "2001:0db8:85a3:0000:0000:8a2e:0370:7335", 65535 - i));

		FindPeerResponse msg = new FindPeerResponse(txid);
		msg.setId(id);
		msg.setVersion(VERSION);
		msg.setNodes6(nodes6);
		msg.setToken(token);
		msg.setPeers6(peers6);

		byte[] bin = msg.serialize();
		assertTrue(bin.length <= msg.estimateSize());

		printMessage(msg, bin);

		Message pm = Message.parse(bin);
		pm.setId(id);
		assertTrue(pm instanceof FindPeerResponse);
		FindPeerResponse m = (FindPeerResponse)pm;

		assertEquals(Type.RESPONSE, m.getType());
		assertEquals(Method.FIND_PEER, m.getMethod());
		assertEquals(id, m.getId());
		assertEquals(txid, m.getTxid());
		assertEquals(VERSION_STR, m.getReadableVersion());
		assertEquals(token, m.getToken());
		assertTrue(m.getNodes4().isEmpty());
		assertFalse(m.getNodes6().isEmpty());
		assertFalse(m.getPeers6().isEmpty());

		List<NodeInfo> nodes = m.getNodes6();
		assertArrayEquals(nodes6.toArray(), nodes.toArray());

		List<PeerInfo> peers = m.getPeers6();
		assertArrayEquals(peers6.toArray(), peers.toArray());
	}

	@Test
	public void testFindPeerResponse46() throws Exception {
		Id id = Id.random();
		int txid = ThreadLocals.random().nextInt();
		int token = ThreadLocals.random().nextInt();

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

		List<PeerInfo> peers4 = new ArrayList<>();
		for (int i = 0; i < ThreadLocals.random().nextInt(8, 48); i++) {
			peers4.add(new PeerInfo(Id.random(), "192.168.1.2", 65535 - i));
		}

		List<PeerInfo> peers6 = new ArrayList<>();
		for (int i = 0; i < ThreadLocals.random().nextInt(8, 48); i++) {
			peers6.add(new PeerInfo(Id.random(), "2001:0db8:85a3:0000:0000:8a2e:0370:7335", 65535 - i));
		}

		FindPeerResponse msg = new FindPeerResponse(txid);
		msg.setId(id);
		msg.setNodes4(nodes4);
		msg.setNodes6(nodes6);
		msg.setToken(token);
		msg.setPeers4(peers4);
		msg.setPeers6(peers6);

		byte[] bin = msg.serialize();
		assertTrue(bin.length <= msg.estimateSize());

		printMessage(msg, bin);

		Message pm = Message.parse(bin);
		pm.setId(id);
		assertTrue(pm instanceof FindPeerResponse);
		FindPeerResponse m = (FindPeerResponse)pm;

		assertEquals(Type.RESPONSE, m.getType());
		assertEquals(Method.FIND_PEER, m.getMethod());
		assertEquals(id, m.getId());
		assertEquals(txid, m.getTxid());
		assertEquals(0, m.getVersion());
		assertEquals(token, m.getToken());
		assertNotNull(m.getNodes4());
		assertNotNull(m.getNodes6());
		assertNotNull(m.getPeers4());
		assertNotNull(m.getPeers6());

		List<NodeInfo> nodes = m.getNodes4();
		assertArrayEquals(nodes4.toArray(), nodes.toArray());

		nodes = m.getNodes6();
		assertArrayEquals(nodes6.toArray(), nodes.toArray());

		List<PeerInfo> peers = m.getPeers4();
		assertArrayEquals(peers4.toArray(), peers.toArray());

		peers = m.getPeers6();
		assertArrayEquals(peers6.toArray(), peers.toArray());
	}
}
