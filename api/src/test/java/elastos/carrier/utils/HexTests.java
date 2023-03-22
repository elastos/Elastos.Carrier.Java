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

package elastos.carrier.utils;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Arrays;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class HexTests {
	private static byte[] BIN;
	private static String TEXT;

	@BeforeAll
	public static void beforeAll() {
		BIN = new byte[256];
		StringBuilder sb = new StringBuilder();

		for (int i = 0; i < 256; i++) {
			BIN[i] = (byte)i;
			sb.append(String.format("%02x", i));
		}

		TEXT = sb.toString();
	}

	@Test
	public void decodeTestWithOffset() {
		for (int i = 0; i < 128; i++) {
			byte[] bytes = Hex.decode(TEXT, i * 2, TEXT.length() - i * 4);
			assertArrayEquals(Arrays.copyOfRange(BIN, i, BIN.length - i) , bytes);
		}
	}

	@Test
	public void decodeTest() {
		String hex = "6a2f0300505B03070A00000808000d63f5c3e6Fe";
		byte[] result = { 0x6a, 0x2f, 0x03, 0x00, 0x50, 0x5b, 0x03, 0x07,
				0x0a, 0x00, 0x00, 0x08, 0x08, 0x00, 0x0d, 0x63, (byte)0xf5,
				(byte)0xc3, (byte)0xe6,(byte)0xfe };

		byte[] bytes = Hex.decode(hex);
		assertArrayEquals(result, bytes);
	}

	@Test
	public void encodeTestWithOffset() {
		for (int i = 0; i < 128; i++) {
			String s = Hex.encode(BIN, i, BIN.length - i * 2);
			assertEquals(TEXT.substring(i * 2, (BIN.length - i) * 2), s);
		}
	}

	@Test
	public void encodeTest() {
		byte[] bytes = { 0x6a, 0x2f, 0x03, 0x00, 0x50, 0x5b, 0x03, 0x07,
				0x0a, 0x00, 0x00, 0x08, 0x08, 0x00, 0x0d, 0x63, (byte)0xf5 };
		String result = "6a2f0300505b03070a00000808000d63f5";

		String s = Hex.encode(bytes).toLowerCase();
		assertEquals(result, s);
	}
}
