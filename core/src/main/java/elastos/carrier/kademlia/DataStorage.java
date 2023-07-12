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

package elastos.carrier.kademlia;

import java.io.Closeable;
import java.util.Collection;
import java.util.List;
import java.util.stream.Stream;

import elastos.carrier.Id;
import elastos.carrier.PeerInfo;
import elastos.carrier.Value;
import elastos.carrier.kademlia.exceptions.KadException;

public interface DataStorage extends Closeable {
	public Value getValue(Id valueId) throws KadException;

	public boolean removeValue(Id valueId) throws KadException;

	public Value putValue(Value value, int expectedSeq, boolean persistent, boolean updateLastAnnounce)
			throws KadException;

	public default Value putValue(Value value, boolean persistent) throws KadException {
		return putValue(value, -1, persistent, true);
	}

	public default Value putValue(Value value, int expectedSeq) throws KadException {
		return putValue(value, expectedSeq, false, false);
	}

	public default Value putValue(Value value) throws KadException {
		return putValue(value, -1, false, false);
	}

	public void updateValueLastAnnounce(Id valueId) throws KadException;

	public Stream<Value> getPersistentValues(long lastAnnounceBefore) throws KadException;

	public Stream<Id> getAllValues() throws KadException;

	public List<PeerInfo> getPeer(Id peerId, int maxPeers) throws KadException;

	public PeerInfo getPeer(Id peerId, Id origin) throws KadException ;

	public boolean removePeer(Id peerId, Id origin) throws KadException;

	public void putPeer(Collection<PeerInfo> peers) throws KadException;

	public default void putPeer(PeerInfo peer) throws KadException {
		putPeer(peer, false, false);
	}

	public void putPeer(PeerInfo peer, boolean persistent, boolean updateLastAnnounce) throws KadException;

	public default void putPeer(PeerInfo peer, boolean persistent) throws KadException {
		putPeer(peer, persistent, true);
	}

	public void updatePeerLastAnnounce(Id peerId, Id origin) throws KadException;

	public Stream<PeerInfo> getPersistentPeers(long lastAnnounceBefore) throws KadException;

	public Stream<Id> getAllPeers() throws KadException;
}
