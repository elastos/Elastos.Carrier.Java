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

import java.io.Closeable;
import java.util.List;
import java.util.stream.Stream;

import elastos.carrier.kademlia.exceptions.KadException;

public abstract class DataStorage implements Closeable {
	public Value putValue(Id id, Value value) throws KadException {
		return putValue(id, value, -1);
	}

	public abstract Stream<Id> valueIdStream();
	public abstract Stream<Id> peerIdStream();

	public abstract Value putValue(Id id, Value value, int expectedSeq) throws KadException;
	public abstract Value getValue(Id id);

	public abstract void putPeer(Id id, PeerInfo peer) throws KadException;
	public abstract List<PeerInfo> getPeers(Id id, boolean ipv4, boolean ipv6, int maxPeers);
}
