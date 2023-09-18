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

package elastos.carrier;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledExecutorService;

public interface Node {
	public Id getId();

	public NodeInfo getNodeInfo4();

	public NodeInfo getNodeInfo6();

	public boolean isLocalId(Id id);

	public Configuration getConfig();

	public void setDefaultLookupOption(LookupOption option);

	public void addStatusListener(NodeStatusListener listener);

	public void removeStatusListener(NodeStatusListener listener);

	public ScheduledExecutorService getScheduler();

	public void setScheduler(ScheduledExecutorService scheduler);

	public void bootstrap(NodeInfo node) throws CarrierException;

	public void bootstrap(Collection<NodeInfo> bootstrapNodes) throws CarrierException;

	public void start() throws CarrierException;

	public void stop();

	public NodeStatus getStatus();

	public boolean isRunning();

	public byte[] encrypt(Id recipient, byte[] data) throws CarrierException;

	public byte[] decrypt(Id sender, byte[] data) throws CarrierException;

	public byte[] sign(byte[] data) throws CarrierException;

	public boolean verify(byte[] data, byte[] signature) throws CarrierException;

	public default CompletableFuture<List<NodeInfo>> findNode(Id id) {
		return findNode(id, null);
	}

	public CompletableFuture<List<NodeInfo>> findNode(Id id, LookupOption option);

	public default CompletableFuture<Value> findValue(Id id) {
		return findValue(id, null);
	}

	public CompletableFuture<Value> findValue(Id id, LookupOption option);

	public CompletableFuture<Void> storeValue(Value value, boolean persistent);

	public default CompletableFuture<Void> storeValue(Value value) {
		return storeValue(value, false);
	}

	public default CompletableFuture<List<PeerInfo>> findPeer(Id id, int expected) {
		return findPeer(id, expected, null);
	}

	public CompletableFuture<List<PeerInfo>> findPeer(Id id, int expected, LookupOption option);

	public CompletableFuture<Void> announcePeer(PeerInfo peer, boolean persistent);

	public default CompletableFuture<Void> announcePeer(PeerInfo peer) {
		return announcePeer(peer, false);
	}

	public Value getValue(Id valueId) throws CarrierException;

	public boolean removeValue(Id valueId) throws CarrierException;;

	public PeerInfo getPeer(Id peerId) throws CarrierException;

	public boolean removePeer(Id peerId) throws CarrierException;
}
