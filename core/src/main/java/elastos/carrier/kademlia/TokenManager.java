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

package elastos.carrier.kademlia;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import elastos.carrier.utils.ThreadLocals;

public class TokenManager {
	private AtomicLong timestamp = new AtomicLong();
	private volatile long previousTimestamp;
	private byte[] sessionSecret = new byte[32];

	TokenManager() {
		ThreadLocals.random().nextBytes(sessionSecret);
	}

	void updateTokenTimestamps() {
		long current = timestamp.get();
		long now = System.nanoTime();
		while (TimeUnit.NANOSECONDS.toMillis(now - current) > Constants.TOKEN_TIMEOUT) {
			if (timestamp.compareAndSet(current, now)) {
				previousTimestamp = current;
				break;
			}
			current = timestamp.get();
		}
	}

	private static int generateToken(Id nodeId, InetAddress ip, int port, Id targetId, long timestamp, byte[] sessionSecret) {
		byte[] tokData = new byte[Id.BYTE_LENGTH * 2 + ip.getAddress().length + 2 + 8 + sessionSecret.length];

		// nodeId + ip + port + targetId + timestamp + sessionSecret
		ByteBuffer bb = ByteBuffer.wrap(tokData);
		bb.put(nodeId.getBytes());
		bb.put(ip.getAddress());
		bb.putShort((short)port);
		bb.put(targetId.getBytes());
		bb.putLong(timestamp);
		bb.put(sessionSecret);

		byte[] digest = ThreadLocals.sha256().digest(tokData);
		int token = ((digest[0] & 0xff) << 24) |
				((digest[1] & 0xff) << 16) |
				((digest[2] & 0xff) << 8) |
				(digest[3] & 0xff);

		return token;
	}

	public int generateToken(Id nodeId, InetSocketAddress addr, Id targetId) {
		updateTokenTimestamps();
		return generateToken(nodeId, addr.getAddress(), addr.getPort(), targetId, timestamp.get(), sessionSecret);
	}

	public int generateToken(Id nodeId, InetAddress ip, int port, Id targetId) {
		updateTokenTimestamps();
		return generateToken(nodeId, ip, port, targetId, timestamp.get(), sessionSecret);
	}

	public boolean verifyToken(int token, Id nodeId, InetAddress ip, int port, Id targetId) {
		updateTokenTimestamps();
		int currentToken = generateToken(nodeId, ip, port, targetId, timestamp.get(), sessionSecret);
		if (token == currentToken)
			return true;

		int previousToken = generateToken(nodeId, ip, port, targetId, previousTimestamp, sessionSecret);
		return token == previousToken;
	}

	public boolean verifyToken(int token, Id nodeId, InetSocketAddress addr, Id targetId) {
		return verifyToken(token, nodeId, addr.getAddress(), addr.getPort(), targetId);
	}
}
