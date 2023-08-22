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

public enum PacketType {
	AUTH(0x00, 0x07, "AUTH"),
	AUTH_ACK(AUTH, "AUTH ACK"),
	ATTACH(0x08, 0x0F, "ATTACH"),
	ATTACH_ACK(ATTACH, "ATTACH ACK"),
	PING(0x10, 0x1F, "PING"),
	PING_ACK(PING, "PING ACK"),
	CONNECT(0x20, 0x2F, "CONNECT"),
	CONNECT_ACK(CONNECT, "CONNECT ACK"),
	DISCONNECT(0x30, 0x3F, "DISCONNECT"),
	DISCONNECT_ACK(DISCONNECT, "DISCONNECT ACK"),
	DATA(0x40, 0x6F, "DATA"),
	ERROR(0x70, 0x7F, "ERROR");

	private static final byte ACK_MASK = (byte) 0x80;
	private static final byte TYPE_MASK = 0x7F;

	private byte min;
	private byte max;
	private boolean ack;
	private String name;

	private PacketType(int min, int max, String name) {
		this.min = (byte)min;
		this.max = (byte)max;
		this.ack = false;
		this.name = name;
	}

	// ACK
	private PacketType(PacketType flag, String name) {
		this.min = flag.min;
		this.max = flag.max;
		this.ack = true;
		this.name = name;
	}

	public byte value() {
		byte value = (byte)ThreadLocals.random().nextInt(min, max + 1);
		return ack ? (byte)(value | (ACK_MASK & 0x00ff)) : value;
	}

	public boolean isAck() {
		return ack;
	}

	public static PacketType valueOf(byte flag) {
		boolean ack = (flag & ACK_MASK) != 0;
		byte type = (byte)(flag & TYPE_MASK);
		switch (type >> 4) {
		case 0:
			if (type <= AUTH.max)
				return ack ? AUTH_ACK : AUTH;
			else
				return ack ? ATTACH_ACK : ATTACH;

		case 1:
			return ack ? PING_ACK : PING;

		case 2:
			return ack ? CONNECT_ACK : CONNECT;

		case 3:
			return ack ? DISCONNECT_ACK : DISCONNECT;

		case 4:
		case 5:
		case 6:
			if (ack)
				throw new IllegalArgumentException("Should never happen: invalid flag");
			else
				return DATA;

		case 7:
			if (ack)
				throw new IllegalArgumentException("Should never happen: invalid flag");
			else
				return ERROR;

		default:
			throw new IllegalArgumentException("Should never happen: invalid flag");
		}
	}

	@Override
	public String toString() {
		return this.name;
	}
}