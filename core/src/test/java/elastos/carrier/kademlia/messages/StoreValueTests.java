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

import java.util.Arrays;

import org.junit.jupiter.api.Test;

import elastos.carrier.Id;
import elastos.carrier.kademlia.messages.Message.Method;
import elastos.carrier.kademlia.messages.Message.Type;
import elastos.carrier.utils.ThreadLocals;

public class StoreValueTests extends MessageTests {
	@Test
	public void testStoreValueRequestSize() throws Exception {
		byte[] nonce = new byte[24];
		Arrays.fill(nonce, (byte)'N');
		byte[] sig = new byte[64];
		Arrays.fill(sig, (byte)'S');
		byte[] value = new byte[1025];
		Arrays.fill(value, (byte)'D');

		StoreValueRequest msg = new StoreValueRequest();
		msg.setId(Id.random());
		msg.setTxid(0x87654321);
		msg.setVersion(VERSION);
		msg.setPublicKey(Id.random());
		msg.setRecipient(Id.random());
		msg.setNonce(nonce);
		msg.setExpectedSequenceNumber(0x77654320);
		msg.setSequenceNumber(0x77654321);
		msg.setSignature(sig);
		msg.setToken(0x88888888);
		msg.setValue(value);

		byte[] bin = msg.serialize();
		printMessage(msg, bin);
		assertTrue(bin.length <= msg.estimateSize());
	}

	@Test
	public void testStoreValueRequest() throws Exception {
		Id nodeId = Id.random();
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

		StoreValueRequest msg = new StoreValueRequest();
		msg.setId(nodeId);
		msg.setTxid(txid);
		msg.setVersion(VERSION);
		msg.setPublicKey(pk);
		msg.setRecipient(recipient);
		msg.setNonce(nonce);
		msg.setExpectedSequenceNumber(cas);
		msg.setSequenceNumber(seq);
		msg.setSignature(sig);
		msg.setToken(token);
		msg.setValue(value);

		byte[] bin = msg.serialize();
		printMessage(msg, bin);

		Message pm = Message.parse(bin);
		pm.setId(nodeId);
		assertTrue(pm instanceof StoreValueRequest);
		StoreValueRequest m = (StoreValueRequest)pm;

		assertEquals(Type.REQUEST, m.getType());
		assertEquals(Method.STORE_VALUE, m.getMethod());
		assertEquals(nodeId, m.getId());
		assertEquals(txid, m.getTxid());
		assertEquals(VERSION_STR, m.getReadableVersion());
		assertEquals(pk, m.getPublicKey());
		assertEquals(recipient, m.getRecipient());
		assertArrayEquals(nonce, m.getNonce());
		assertEquals(cas, m.getExpectedSequenceNumber());
		assertEquals(seq, m.getSequenceNumber());
		assertArrayEquals(sig, m.getSignature());
		assertEquals(token, m.getToken());
		assertArrayEquals(value, m.getValue());
	}

	@Test
	public void testStoreValueResponseSize() throws Exception {
		StoreValueResponse msg = new StoreValueResponse(0xf7654321);
		msg.setId(Id.random());
		msg.setTxid(0x87654321);
		msg.setVersion(VERSION);

		byte[] bin = msg.serialize();
		printMessage(msg, bin);
		assertTrue(bin.length <= msg.estimateSize());
	}


	@Test
	public void testStoreValueResponse() throws Exception {
		Id id = Id.random();
		int txid = ThreadLocals.random().nextInt();

		StoreValueResponse msg = new StoreValueResponse(txid);
		msg.setId(id);

		byte[] bin = msg.serialize();
		assertTrue(bin.length <= msg.estimateSize());

		printMessage(msg, bin);

		Message pm = Message.parse(bin);
		pm.setId(id);
		assertTrue(pm instanceof StoreValueResponse);
		StoreValueResponse m = (StoreValueResponse)pm;

		assertEquals(Type.RESPONSE, m.getType());
		assertEquals(Method.STORE_VALUE, m.getMethod());
		assertEquals(id, m.getId());
		assertEquals(txid, m.getTxid());
		assertEquals(0, m.getVersion());
	}
}
