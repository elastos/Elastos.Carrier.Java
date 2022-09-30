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

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.mapdb.BTreeMap;
import org.mapdb.DB;
import org.mapdb.DBMaker;
import org.mapdb.DataInput2;
import org.mapdb.DataOutput2;
import org.mapdb.HTreeMap;
import org.mapdb.Serializer;
import org.mapdb.serializer.GroupSerializerObjectArray;
import org.mapdb.serializer.SerializerArrayTuple;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import elastos.carrier.crypto.CryptoBox;
import elastos.carrier.crypto.Signature;
import elastos.carrier.kademlia.exceptions.CasFail;
import elastos.carrier.kademlia.exceptions.ImmutableSubstitutionFail;
import elastos.carrier.kademlia.exceptions.InvalidSignature;
import elastos.carrier.kademlia.exceptions.KadException;
import elastos.carrier.kademlia.exceptions.SequenceNotMonotonic;

public class MapDBStorage implements DataStorage {
	private DB db;

	private HTreeMap<byte[], ValueEntry> values;
	private BTreeMap<Object[], PeerInfoEntry> peers;

	private ScheduledFuture<?> expireFuture;

	private static final Logger log = LoggerFactory.getLogger(MapDBStorage.class);

	public static DataStorage open(File path, ScheduledExecutorService scheduler) {
		MapDBStorage storage = new MapDBStorage();
		storage.init(path, scheduler);
		return storage;
	}

	private void init(File path, ScheduledExecutorService scheduler) {
		if (path != null)
			db = DBMaker.fileDB(path)
					.fileMmapEnable()
					.concurrencyScale(8)
					.closeOnJvmShutdown()
					.transactionEnable()
					.make();
		else
			db = DBMaker.memoryDB()
					.concurrencyScale(8)
					.make();


		values = db.hashMap("values")
				.keySerializer(Serializer.BYTE_ARRAY)
				.valueSerializer(new ValueEntrySerializer())
				.layout(8, 32, 4)
				.createOrOpen();

		peers = db.treeMap("peers")
				.keySerializer(new SerializerArrayTuple(Serializer.BYTE_ARRAY, Serializer.BYTE, Serializer.BYTE_ARRAY))
				.valueSerializer(new PeerInfoEntrySerializer())
				.createOrOpen();

		this.expireFuture = scheduler.scheduleWithFixedDelay(() -> {
			// Evict the expired entries from the local storage
			expire();
		}, 0, Constants.STORAGE_EXPIRE_INTERVAL, TimeUnit.MILLISECONDS);

		log.info("Data storage opened: {}", path != null ? path : "MEMORY");
	}

	@Override
	public void close() {
		expireFuture.cancel(false);
		// none of the scheduled tasks should experience exceptions,
		// log them if they did
		try {
			expireFuture.get();
		} catch (ExecutionException e) {
			log.error("Scheduled future error", e);
		} catch (InterruptedException e) {
			log.error("Scheduled future error", e);
		} catch (CancellationException ignore) {
		}

		if (peers != null) {
			peers.close();
			peers = null;
		}

		if (values != null) {
			values.close();
			values = null;
		}

		if (db != null) {
			db.close();
			db = null;
		}
	}

	@Override
	public Stream<Id> valueIdStream() {
		return values.keySet().stream().map(b -> new Id(b));
	}

	@Override
	public Value getValue(Id valueId) {
		ValueEntry entry = values.get(valueId.getBytes());
		return entry == null ? null : entry.value;
	}

	@Override
	public Value putValue(Value value, int expectedSeq) throws KadException {
		if (value.isMutable() && !value.isValid())
			throw new InvalidSignature("Value signature validation failed");

		Id id = value.getId();
		ValueEntry entry = new ValueEntry(value);
		while (true) {
			ValueEntry old = values.putIfAbsent(id.getBytes(), entry);
			if (old == null) {
				db.commit();
				return null;
			}

			if(old.value.isMutable()) {
				if(!value.isMutable())
					throw new ImmutableSubstitutionFail("Can not replace mutable value with immutable is not supported");
				if(value.getSequenceNumber() < old.value.getSequenceNumber())
					throw new SequenceNotMonotonic("Sequence number less than current");
				if(expectedSeq >= 0 && old.value.getSequenceNumber() >= 0 && old.value.getSequenceNumber() != expectedSeq)
					throw new CasFail("CAS failure");
			}

			if (values.replace(id.getBytes(), old, entry)) {
				db.commit();
				return old.value;
			}
		}
	}

	@Override
	public Value putValue(Value value) throws KadException {
		return putValue(value, -1);
	}

	@Override
	public Stream<Id> peerIdStream() {
		return peers.keySet().stream().map(o -> new Id((byte[])o[0])).distinct();
	}

	@Override
	public List<PeerInfo> getPeer(Id peerId, int family, int maxPeers) {
		List<Integer> families = new ArrayList<>(2);

		if (family == 4) {
			families.add(4);
		} else if (family == 6) {
			families.add(6);
		} else if (family == 10) { // IPv4 + IPv6
			families.add(4);
			families.add(6);
		} else {
			return Collections.emptyList();
		}

		List<PeerInfoEntry> result = new ArrayList<>(maxPeers * families.size());
		for (int f : families) {
			Object[] prefix = new Object[] { peerId.getBytes(), (byte)f };
			Map<Object[], PeerInfoEntry> map = peers.prefixSubMap(prefix);
			if (map.size() == 0)
				continue;

			List<PeerInfoEntry> peerList = new ArrayList<>(map.values());
			Collections.shuffle(peerList);

			if (maxPeers > 0 && peerList.size() > maxPeers)
				result.addAll(peerList.subList(0, maxPeers));
			else
				result.addAll(peerList);
		}

		if (result.isEmpty())
			return Collections.emptyList();
		else
			return result.stream().map(e -> e.peer).collect(Collectors.toList());
	}

	@Override
	public PeerInfo getPeer(Id peerId, int family, Id nodeId) {
		PeerInfoEntry entry = peers.get(new Object[] { peerId.getBytes(), (byte)family, nodeId.getBytes() });
		return entry == null ? null : entry.peer;
	}

	@Override
	public void putPeer(Id peerId, Collection<PeerInfo> peers) {
		if (peers.isEmpty())
			return;

		for (PeerInfo peer : peers) {
			int family = peer.isIPv4() ? 4 : 6;
			PeerInfoEntry entry = new PeerInfoEntry(peer);
			this.peers.put(new Object[] { peerId.getBytes(), (byte)family, peer.getNodeId().getBytes() }, entry);
		}

		db.commit();
	}

	@Override
	public void putPeer(Id peerId, PeerInfo peer) {
		putPeer(peerId, Arrays.asList(peer));
	}

	private void expire() {
		long now = System.currentTimeMillis();

		Iterator<Map.Entry<byte[], ValueEntry>> valueIterator = values.entrySet().iterator();
		while (valueIterator.hasNext()) {
			Map.Entry<byte[], ValueEntry> me = valueIterator.next();
			long age = now - me.getValue().timestamp;
			if (age >= Constants.MAX_VALUE_AGE)
				valueIterator.remove();
		}

		Iterator<Map.Entry<Object[], PeerInfoEntry>> peerIterator = peers.entrySet().iterator();
		while (peerIterator.hasNext()) {
			Map.Entry<Object[], PeerInfoEntry> me = peerIterator.next();
			long age = now - me.getValue().timestamp;
			if (age >= Constants.MAX_PEER_AGE)
				peerIterator.remove();
		}

		db.commit();
	}

	static class ValueEntry {
		public long timestamp;
		public Value value;

		public ValueEntry(long timestamp, Value value) {
			this.timestamp = timestamp;
			this.value = value;
		}

		public ValueEntry(Value value) {
			this(System.currentTimeMillis(), value);
		}

		@Override
		public boolean equals(Object o) {
			if (o == this)
				return true;

			if (o instanceof ValueEntry) {
				ValueEntry e = (ValueEntry)o;
				return this.value.equals(e.value);
			}

			return false;
		}
	}

	static class PeerInfoEntry {
		public long timestamp;
		public PeerInfo peer;

		public PeerInfoEntry(long timestamp, PeerInfo peer) {
			this.timestamp = timestamp;
			this.peer = peer;
		}

		public PeerInfoEntry(PeerInfo peer) {
			this(System.currentTimeMillis(), peer);
		}

		@Override
		public boolean equals(Object o) {
			if (o == this)
				return true;

			if (o instanceof PeerInfoEntry) {
				PeerInfoEntry e = (PeerInfoEntry)o;
				return this.peer.equals(e.peer);
			}

			return false;
		}
	}

	static class PeerInfoEntrySerializer extends GroupSerializerObjectArray<PeerInfoEntry> {
		@Override
		public void serialize(DataOutput2 out, PeerInfoEntry entry) throws IOException {
			out.writeLong(entry.timestamp);

			PeerInfo peer = entry.peer;
			out.write(peer.getNodeId().getBytes());
			out.writeByte(peer.isIPv4() ? 0 : 1);
			out.write(peer.getInetAddress().getAddress());
			out.writeInt(peer.getPort());
		}

		@Override
		public PeerInfoEntry deserialize(DataInput2 in, int available) throws IOException {
			long timestamp = in.readLong();

			byte[] binId = new byte[Id.BYTE_LENGTH];
			in.readFully(binId);
			Id nodeId = new Id(binId);

			boolean isIPv4 = in.readByte() == 0;
			byte[] addr = new byte[isIPv4 ? 4 : 16];
			in.readFully(addr);

			int port = in.readInt();

			PeerInfo peer = new PeerInfo(nodeId, addr, port);
			return new PeerInfoEntry(timestamp, peer);
		}
	}

	static class ValueEntrySerializer implements Serializer<ValueEntry> {
		@Override
		public void serialize(DataOutput2 out, ValueEntry entry) throws IOException {
			out.writeLong(entry.timestamp);

			Value value = entry.value;
			if (value.getPublicKey() == null) {
				out.writeByte(0);
			} else {
				out.writeByte(Id.BYTE_LENGTH);
				out.write(value.getPublicKey().getBytes());
			}

			if (value.getPrivateKey() == null) {
				out.writeByte(0);
			} else {
				out.writeByte(Signature.PrivateKey.length());
				out.write(value.getPrivateKey());
			}

			if (value.getRecipient() == null) {
				out.writeByte(0);
			} else {
				out.writeByte(Id.BYTE_LENGTH);
				out.write(value.getRecipient().getBytes());
			}

			if (value.getNonce() == null) {
				out.writeByte(0);
			} else {
				out.writeByte(value.getNonce().length);
				out.write(value.getNonce());
			}

			if (value.getSignature() == null) {
				out.writeByte(0);
			} else {
				out.writeByte(value.getSignature().length);
				out.write(value.getSignature());
			}

			out.writeInt(value.getSequenceNumber());

			if (value.getData() == null) {
				out.writeShort(0);
			} else {
				out.writeShort(value.getData().length);
				out.write(value.getData());
			}
		}

		@Override
		public ValueEntry deserialize(DataInput2 in, int available) throws IOException {
			long timestamp = in.readLong();

			Id publicKey = null;
			byte s = in.readByte();
			if (s != 0) {
				assert(s == Id.BYTE_LENGTH);
				publicKey  = new Id();
				in.readFully(publicKey.getBytes());
			}

			byte[] privateKey = null;
			s = in.readByte();
			if (s != 0) {
				assert(s == Signature.PrivateKey.length());
				privateKey = new byte[Signature.PrivateKey.length()];
				in.readFully(privateKey);
			}

			Id recipient = null;
			s = in.readByte();
			if (s != 0) {
				assert(s == Id.BYTE_LENGTH);
				recipient = new Id();
				in.readFully(recipient.getBytes());
			}

			byte[] nonce = null;
			s = in.readByte();
			if (s != 0) {
				assert(s == CryptoBox.Nonce.length());
				nonce = new byte[CryptoBox.Nonce.length()];
				in.readFully(nonce);
			}

			byte[] sig = null;
			s = in.readByte();
			if (s != 0) {
				assert(s == 64);
				sig = new byte[64];
				in.readFully(sig);
			}

			int seq = in.readInt();
			int l = in.readShort();
			byte[] data = null;
			if (l > 0) {
				data = new byte[l];
				in.readFully(data);
			}

			Value value = new Value(publicKey, privateKey, recipient, nonce, sig, seq, data);
			return new ValueEntry(timestamp, value);
		}
	}
}
