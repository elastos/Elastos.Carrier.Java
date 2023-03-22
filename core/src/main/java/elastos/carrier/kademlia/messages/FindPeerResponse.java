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

package elastos.carrier.kademlia.messages;

import java.io.IOException;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.dataformat.cbor.CBORParser;

import elastos.carrier.Id;
import elastos.carrier.PeerInfo;
import elastos.carrier.kademlia.DHT;

public class FindPeerResponse extends LookupResponse {
	private List<PeerInfo> peers4;
	private List<PeerInfo> peers6;

	public FindPeerResponse(int txid) {
		super(Method.FIND_PEER, txid);
	}

	protected FindPeerResponse() {
		this(0);
	}

	public void setPeers4(List<PeerInfo> peers) {
		this.peers4 = peers;
	}

	public List<PeerInfo> getPeers4() {
		return peers4 != null ? Collections.unmodifiableList(peers4) : Collections.emptyList();
	}

	public void setPeers6(List<PeerInfo> peers) {
		this.peers6 = peers;
	}

	public List<PeerInfo> getPeers6() {
		return peers6 != null ? Collections.unmodifiableList(peers6) : Collections.emptyList();
	}

	public List<PeerInfo> getPeers(DHT.Type type) {
		if (type == DHT.Type.IPV4)
			return getPeers4();
		else
			return getPeers6();
	}

	@Override
	protected void _serialize(JsonGenerator gen) throws IOException {
		if (peers4 != null && !peers4.isEmpty())
			serializePeers(gen, "p4", peers4);

		if (peers6 != null && !peers6.isEmpty())
			serializePeers(gen, "p6", peers6);
	}

	private void serializePeers(JsonGenerator gen, String fieldName, List<PeerInfo> peers) throws IOException {
		gen.writeFieldName(fieldName);
		gen.writeStartArray();
		for (PeerInfo pi : peers) {
			gen.writeStartArray();
			gen.writeBinary(pi.getNodeId().bytes());
			gen.writeBinary(pi.getInetAddress().getAddress());
			gen.writeNumber(pi.getPort());
			gen.writeEndArray();
		}
		gen.writeEndArray();
	}

	@Override
	protected void _parse(String fieldName, CBORParser parser) throws MessageException, IOException {
		switch (fieldName) {
		case "p4":
			peers4 = parsePeers(fieldName, parser);
			break;

		case "p6":
			peers6 = parsePeers(fieldName, parser);
			break;

		default:
			System.out.println("Unknown field: " + fieldName);
			break;
		}
	}

	private List<PeerInfo> parsePeers(String fieldName, CBORParser parser) throws IOException {
		if (parser.currentToken() != JsonToken.START_ARRAY)
			throw new IOException("Invalid " + fieldName + " data, should be array");

		List<PeerInfo> ps = new ArrayList<>();
		while (parser.nextToken() != JsonToken.END_ARRAY) {
			if (parser.currentToken() != JsonToken.START_ARRAY)
				throw new IOException("Invalid " + fieldName + " info data, should be array");

			parser.nextToken();
			Id nodeId = Id.of(parser.getBinaryValue());
			parser.nextToken();
			InetAddress addr = InetAddress.getByAddress(parser.getBinaryValue());
			parser.nextToken();
			int port = parser.getIntValue();

			if (parser.nextToken() != JsonToken.END_ARRAY)
				throw new IOException("Invalid " + fieldName + " info data");

			ps.add(new PeerInfo(nodeId, addr, port));
		}

		return ps.isEmpty() ? null : ps;
	}

	@Override
	public int estimateSize() {
		int size = super.estimateSize();

		if (peers4 != null && !peers4.isEmpty())
			size += (44 * peers4.size() + 5);

		if (peers6 != null && !peers6.isEmpty())
			size += (56 * peers6.size() + 5);

		return size;
	}

	@Override
	protected void _toString(StringBuilder b) {
		if (peers4 != null && !peers4.isEmpty()) {
			b.append("p4:");
			b.append(peers4.stream().map(p -> p.toString()).collect(Collectors.joining(",", "[", "]")));
		}

		if (peers6 != null && !peers6.isEmpty()) {
			b.append("p6:");
			b.append(peers6.stream().map(p -> p.toString()).collect(Collectors.joining(",", "[", "]")));
		}
	}
}
