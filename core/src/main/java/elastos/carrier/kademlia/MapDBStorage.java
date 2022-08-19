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
import java.util.Collections;
import java.util.List;
import java.util.Map;
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

public class MapDBStorage extends DataStorage {
	private DB db;
	private BTreeMap<Object[], PeerInfo> peers;
	private HTreeMap<byte[], Value> values;

	private static final Logger log = LoggerFactory.getLogger(MapDBStorage.class);

	public static DataStorage open(File path) {
		MapDBStorage storage = new MapDBStorage();
		storage.init(path);
		return storage;
	}

	private void init(File path) {
		if (path != null)
			db = DBMaker.fileDB(path)
					.fileMmapEnable()
					.concurrencyScale(8)
					.transactionEnable()
					.make();
		else
			db = DBMaker.memoryDB()
					.concurrencyScale(8)
					.make();

		peers = db.treeMap("peers")
					.keySerializer(new SerializerArrayTuple(Serializer.BYTE_ARRAY, Serializer.BYTE, Serializer.INTEGER))
					.valueSerializer(new PeerInfoSerializer())
					.createOrOpen();

		values = db.hashMap("values")
				.keySerializer(Serializer.BYTE_ARRAY)
				.valueSerializer(new ValueSerializer())
				.layout(8, 32, 4)
				.expireAfterUpdate(Constants.MAX_VALUE_AGE)
				.createOrOpen();

		log.info("Data storage opened: {}", path != null ? path : "MEMORY");
	}

	@Override
	public void close() {
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
	public Value putValue(Id id, Value value, int expectedSeq) throws KadException {
		if (value.isMutable() && !value.isValid())
			throw new InvalidSignature("Value signature validation failed");

		while (true) {
			Value old = values.putIfAbsent(id.getBytes(), value);
			if (old == null) {
				db.commit();
				return old;
			}

			if(old.isMutable()) {
				if(!value.isMutable())
					throw new ImmutableSubstitutionFail("Can not replace mutable value with immutable is not supported");
				if(value.getSequenceNumber() < old.getSequenceNumber())
					throw new SequenceNotMonotonic("Sequence number less than current");
				if(expectedSeq >= 0 && old.getSequenceNumber() >= 0 && old.getSequenceNumber() != expectedSeq)
					throw new CasFail("CAS failure");
			}

			if (values.replace(id.getBytes(), old, value)) {
				db.commit();
				return old;
			}
		}
	}

	@Override
	public Value getValue(Id id) {
		return values.get(id.getBytes());
	}

	@Override
	public Stream<Id> valueIdStream() {
		return values.keySet().stream().map(b -> new Id(b));
	}

	@Override
	public void putPeer(Id id, PeerInfo peer) {
		int t = peer.isIPv4() ? 4 : 6;
		peers.put(new Object[] { id.getBytes(), (byte)t, peer.hashCode() }, peer);
		db.commit();
	}

	@Override
	public List<PeerInfo> getPeers(Id id, boolean ipv4, boolean ipv6, int maxPeers) {
		Object[] prefix = null;
		if (ipv4 == ipv6)
			prefix = new Object[] { id.getBytes() };
		else
			prefix = new Object[] { id.getBytes(), (byte)(ipv4 ? 4 : 6) };

		Map<Object[], PeerInfo> map = peers.prefixSubMap(prefix);
		if (map.size() == 0)
			return Collections.emptyList();

		List<PeerInfo> peerList = new ArrayList<>(map.values());
		Collections.shuffle(peerList);

		if (maxPeers <= 0 || peerList.size() <= maxPeers)
			return peerList;
		else
			return new ArrayList<>(peerList.subList(0, maxPeers));
	}

	@Override
	public Stream<Id> peerIdStream() {
		return peers.keySet().stream().map(o -> new Id((byte[])o[0])).distinct();
	}

	static class PeerInfoSerializer extends GroupSerializerObjectArray<PeerInfo> {
		@Override
		public void serialize(DataOutput2 out, PeerInfo peer) throws IOException {
			out.write(peer.getNodeId().getBytes());
			out.writeByte(peer.isIPv4() ? 0 : 1);
			out.write(peer.getInetAddress().getAddress());
			out.writeInt(peer.getPort());
		}

		@Override
		public PeerInfo deserialize(DataInput2 in, int available) throws IOException {
			byte[] binId = new byte[Id.BYTE_LENGTH];
			in.readFully(binId);
			Id nodeId = new Id(binId);

			boolean isIPv4 = in.readByte() == 0;
			byte[] addr = new byte[isIPv4 ? 4 : 16];
			in.readFully(addr);

			int port = in.readInt();

			return new PeerInfo(nodeId, addr, port);
		}
	}

	static class ValueSerializer implements Serializer<Value> {
		@Override
		public void serialize(DataOutput2 out, Value value) throws IOException {
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
		public Value deserialize(DataInput2 in, int available) throws IOException {
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

			return new Value(publicKey, privateKey, recipient, nonce, sig, seq, data);
		}
	}
}
