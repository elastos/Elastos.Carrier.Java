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

package elastos.carrier.service.activeproxy;

import elastos.carrier.utils.ThreadLocals;

public class PacketFlag {
	// [MIN, MAX]
	public static final byte AUTH = 0x00;
	private static final byte AUTH_MIN = AUTH;
	private static final byte AUTH_MAX = 0x07;

	public static final byte ATTACH = 0x08;
	private static final byte ATTACH_MIN = ATTACH;
	private static final byte ATTACH_MAX = 0x0F;

	public static final byte PING = 0x10;
	private static final byte PING_MIN = PING;
	private static final byte PING_MAX = 0x1F;

	public static final byte CONNECT = 0x20;
	private static final byte CONNECT_MIN = CONNECT;
	private static final byte CONNECT_MAX = 0x2F;

	public static final byte DISCONNECT = 0x30;
	private static final byte DISCONNECT_MIN = DISCONNECT;
	private static final byte DISCONNECT_MAX = 0x3F;

	public static final byte DATA = 0x40;
	private static final byte DATA_MIN = DATA;
	private static final byte DATA_MAX = 0x4F;

	public static final byte ERROR = 0x70;
	private static final byte ERROR_MIN = ERROR;
	private static final byte ERROR_MAX = 0x7F;

	private static final byte ACK_MASK = (byte) 0x80;
	private static final byte TYPE_MASK = 0x7F;
	
	public static final byte SIGNATURE = 0x50;
	private static final byte SIGNATURE_MIN = SIGNATURE;
	private static final byte SIGNATURE_MAX = 0x5F;

	private static byte random(byte min, byte max, boolean ack) {
		byte type = (byte)ThreadLocals.random().nextInt(min, max + 1);
		return ack ? (byte)(type | (ACK_MASK & 0x00ff)) : type;
	}

	public static byte auth() {
		return random(AUTH_MIN, AUTH_MAX, false);
	}

	public static byte authAck() {
		return random(AUTH_MIN, AUTH_MAX, true);
	}

	public static byte attach() {
		return random(ATTACH_MIN, ATTACH_MAX, false);
	}

	public static byte attachAck() {
		return random(ATTACH_MIN, ATTACH_MAX, true);
	}

	public static byte ping() {
		return random(PING_MIN, PING_MAX, false);
	}

	public static byte pingAck() {
		return random(PING_MIN, PING_MAX, true);
	}

	public static byte connect() {
		return random(CONNECT_MIN, CONNECT_MAX, false);
	}

	public static byte connectAck() {
		return random(CONNECT_MIN, CONNECT_MAX, true);
	}

	public static byte disconnect() {
		return random(DISCONNECT_MIN, DISCONNECT_MAX, false);
	}

	public static byte data() {
		return random(DATA_MIN, DATA_MAX, false);
	}
	
	public static byte signature() {
		return random(SIGNATURE_MIN, SIGNATURE_MAX, false);
	}

	public static byte error() {
		return random(ERROR_MIN, ERROR_MAX, true);
	}

	public static boolean isAck(byte flag) {
		return (flag & ACK_MASK) != 0;
	}

	public static byte getType(byte flag) {
		byte type = (byte)(flag & TYPE_MASK);
		switch (type >> 4) {
		case 0:
			return type >= ATTACH ? ATTACH : AUTH;

		case 1:
			return PING;

		case 2:
			return CONNECT;

		case 3:
			return DISCONNECT;

		case 4:
			return DATA;
		case 5:
			return SIGNATURE;
		case 6:
		case 7:
			return ERROR;

		default:
			throw new IllegalArgumentException("Should never happen: invalid flag");
		}
	}

	public static String toString(byte flag) {
		boolean ack = isAck(flag);
		byte type = getType(flag);
		switch (type) {
		case AUTH:
			return ack ? "AUTH ACK" : "AUTH";

		case ATTACH:
			return ack ? "ATTACH ACK" : "ATTACH";

		case PING:
			return ack ? "PING ACK" : "PING";

		case CONNECT:
			return ack ? "CONNECT ACK" : "CONNECT";

		case DISCONNECT:
			return ack ? "N/A" : "DISCONNECT";

		case DATA:
			return ack ? "N/A" : "DATA";
			
		case SIGNATURE:
			return ack ? "N/A" : "SIGNATURE";

		case ERROR:
			return ack ? "ERROR ACK" : "N/A";

		default:
			return "N/A";
		}
	}
}