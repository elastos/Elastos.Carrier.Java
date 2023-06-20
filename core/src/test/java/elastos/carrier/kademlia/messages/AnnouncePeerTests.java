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

package elastos.carrier.kademlia.messages;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.security.SecureRandom;

import org.junit.jupiter.api.Test;

import elastos.carrier.Id;
import elastos.carrier.kademlia.messages.Message.Method;
import elastos.carrier.kademlia.messages.Message.Type;
import elastos.carrier.utils.ThreadLocals;

public class AnnouncePeerTests extends MessageTests {
	@Test
	public void testAnnouncePeerRequestSize() throws Exception {
		byte[] sig = new byte[64];
		new SecureRandom().nextBytes(sig);
		AnnouncePeerRequest msg = new AnnouncePeerRequest(Id.random(), 65535, "test size", sig, 0x88888888);
		msg.setId(Id.random());
		msg.setTxid(0x87654321);
		msg.setVersion(VERSION);

		byte[] bin = msg.serialize();
		printMessage(msg, bin);
		assertTrue(bin.length <= msg.estimateSize());
	}

	@Test
	public void testAnnouncePeerRequest() throws Exception {
		Id nodeId = Id.random();
		int txid = ThreadLocals.random().nextInt(1, 0x7FFFFFFF);
		Id peerId = Id.random();
		int port = ThreadLocals.random().nextInt(1, 65535);
		int token = ThreadLocals.random().nextInt();
		byte[] sig = new byte[64];
		new SecureRandom().nextBytes(sig);
		String alt ="test announce peer";

		AnnouncePeerRequest msg = new AnnouncePeerRequest(peerId, port, alt, sig, token);
		msg.setId(nodeId);
		msg.setTxid(txid);
		msg.setVersion(VERSION);

		byte[] bin = msg.serialize();
		printMessage(msg, bin);

		Message pm = Message.parse(bin);
		pm.setId(nodeId);
		assertTrue(pm instanceof AnnouncePeerRequest);
		AnnouncePeerRequest m = (AnnouncePeerRequest)pm;

		assertEquals(Type.REQUEST, m.getType());
		assertEquals(Method.ANNOUNCE_PEER, m.getMethod());
		assertEquals(nodeId, m.getId());
		assertEquals(txid, m.getTxid());
		assertEquals(VERSION_STR, m.getReadableVersion());
		assertEquals(peerId, m.getTarget());
		assertEquals(port, m.getPort());
		assertEquals(alt, m.getAlt());
		assertArrayEquals(sig, m.getSignature());
		assertEquals(token, m.getToken());
	}

	@Test
	public void testAnnouncePeerResponseSize() throws Exception {
		AnnouncePeerResponse msg = new AnnouncePeerResponse(0xf7654321);
		msg.setId(Id.random());
		msg.setTxid(0x87654321);
		msg.setVersion(VERSION);

		byte[] bin = msg.serialize();
		printMessage(msg, bin);
		assertTrue(bin.length <= msg.estimateSize());
	}


	@Test
	public void testAnnouncePeerResponse() throws Exception {
		Id id = Id.random();
		int txid = ThreadLocals.random().nextInt();

		AnnouncePeerResponse msg = new AnnouncePeerResponse(txid);
		msg.setId(id);

		byte[] bin = msg.serialize();
		assertTrue(bin.length <= msg.estimateSize());

		printMessage(msg, bin);

		Message pm = Message.parse(bin);
		pm.setId(id);
		assertTrue(pm instanceof AnnouncePeerResponse);
		AnnouncePeerResponse m = (AnnouncePeerResponse)pm;

		assertEquals(Type.RESPONSE, m.getType());
		assertEquals(Method.ANNOUNCE_PEER, m.getMethod());
		assertEquals(id, m.getId());
		assertEquals(txid, m.getTxid());
		assertEquals(0, m.getVersion());
	}
}
