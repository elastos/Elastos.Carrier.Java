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

public class Hex {
	private static final byte[] EMPTY_BYTES = {};
	private static final char[] HEX_CHARS = {
			'0', '1', '2', '3', '4', '5', '6', '7',
			'8', '9', 'a', 'b', 'c', 'd', 'e', 'f'
	};

	private static int decodeNibble(final char c) {
		// Character.digit() is not used here, as it addresses a larger
		// set of characters (both ASCII and full-width latin letters).
		if (c >= '0' && c <= '9') {
			return c - '0';
		}
		if (c >= 'A' && c <= 'F') {
			return c - ('A' - 0xA);
		}
		if (c >= 'a' && c <= 'f') {
			return c - ('a' - 0xA);
		}

		return -1;
	}

	public static byte decodeByte(CharSequence chars, int offset) {
		int hi = decodeNibble(chars.charAt(offset));
		int lo = decodeNibble(chars.charAt(offset + 1));
		if (hi == -1 || lo == -1) {
			throw new IllegalArgumentException(String.format(
					"Invalid hex byte '%s' at index %d of '%s'",
					chars.subSequence(offset, offset + 2), offset, chars));
		}

		return (byte) ((hi << 4) + lo);
	}

	public static byte[] decode(CharSequence chars, int offset, int length) {
		if (length < 0 || (length & 1) != 0)
			throw new IllegalArgumentException("Invalid length: " + length);

		if (length == 0)
			return EMPTY_BYTES;

		byte[] bytes = new byte[length >>> 1];
		for (int i = 0; i < length; i += 2)
			bytes[i >>> 1] = decodeByte(chars, offset + i);

		return bytes;
	}

	public static byte[] decode(CharSequence chars) {
		return decode(chars, 0, chars.length());
	}

	public static String encode(byte[] bytes, int offset, int length) {
		char[] chars = new char[length * 2];

		for (int i = 0; i < length; i++) {
			int v = bytes[offset + i] & 0xFF;
			chars[i << 1] = HEX_CHARS[v >>> 4];
			chars[(i << 1) + 1] = HEX_CHARS[v & 0x0F];
		}

		return new String(chars);
	}

	public static String encode(byte[] bytes) {
		return encode(bytes, 0, bytes.length);
	}
}
