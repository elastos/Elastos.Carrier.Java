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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;

import org.junit.jupiter.api.Test;

import elastos.carrier.Id;
import elastos.carrier.kademlia.messages.Message.Method;
import elastos.carrier.kademlia.messages.Message.Type;
import elastos.carrier.utils.ThreadLocals;

public class ErrorMessageTests extends MessageTests {

	@Test
	public void testErrorMessageSize() throws Exception {
		byte[] em = new byte[1025];
		Arrays.fill(em, (byte)'E');

		ErrorMessage msg = new ErrorMessage(Method.PING, 0xF7654321, 0x87654321, new String(em));
		msg.setId(Id.random());
		msg.setVersion(VERSION);
		byte[] bin = msg.serialize();
		printMessage(msg, bin);
		assertTrue(bin.length <= msg.estimateSize());
	}

	@Test
	public void testErrorMessage() throws Exception {
		int txid = ThreadLocals.random().nextInt();
		int code = ThreadLocals.random().nextInt();
		String error = "Test error message";

		ErrorMessage msg = new ErrorMessage(Method.PING, txid, code, error);
		msg.setId(Id.random());
		msg.setVersion(VERSION);

		byte[] bin = msg.serialize();
		assertTrue(bin.length <= msg.estimateSize());

		printMessage(msg, bin);

		Message pm = Message.parse(bin);
		assertTrue(pm instanceof ErrorMessage);
		ErrorMessage m = (ErrorMessage)pm;

		assertEquals(Type.ERROR, m.getType());
		assertEquals(Method.PING, m.getMethod());
		assertEquals(txid, m.getTxid());
		assertEquals(VERSION_STR, m.getReadableVersion());
		assertEquals(code, m.getCode());
		assertEquals(error, m.getMessage());
	}

	@Test
	public void testErrorMessagei18n() throws Exception {
		int txid = ThreadLocals.random().nextInt();
		int code = ThreadLocals.random().nextInt();
		String error = "错误信息；エラーメッセージ；에러 메시지；Message d'erreur";

		ErrorMessage msg = new ErrorMessage(Method.UNKNOWN, txid, code, error);
		msg.setId(Id.random());
		msg.setVersion(VERSION);

		byte[] bin = msg.serialize();
		assertTrue(bin.length <= msg.estimateSize());

		printMessage(msg, bin);

		Message pm = Message.parse(bin);
		assertTrue(pm instanceof ErrorMessage);
		ErrorMessage m = (ErrorMessage)pm;

		assertEquals(Type.ERROR, m.getType());
		assertEquals(Method.UNKNOWN, m.getMethod());
		assertEquals(txid, m.getTxid());
		assertEquals(VERSION_STR, m.getReadableVersion());
		assertEquals(code, m.getCode());
		assertEquals(error, m.getMessage());
	}
}
