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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.dataformat.cbor.CBORParser;

import elastos.carrier.Id;
import elastos.carrier.PeerInfo;
import elastos.carrier.crypto.Signature;

public class FindPeerResponse extends LookupResponse {
	private List<PeerInfo> peers;

	public FindPeerResponse(int txid) {
		super(Method.FIND_PEER, txid);
	}

	protected FindPeerResponse() {
		this(0);
	}

	public void setPeers(List<PeerInfo> peers) {
		this.peers = peers;
	}

	public boolean hasPeers() {
		return peers != null;
	}

	public List<PeerInfo> getPeers() {
		return peers != null ? Collections.unmodifiableList(peers) : Collections.emptyList();
	}

	@Override
	protected void _serialize(JsonGenerator gen) throws IOException {
		if (peers == null || peers.isEmpty())
			return;

		gen.writeFieldName("p");
		gen.writeStartArray();
		// p[0] is the peerid
		gen.writeBinary(peers.get(0).getId().bytes());
		// p[1...] is the peerinfo without id
		for (PeerInfo pi : peers) {
			gen.writeStartArray();
			gen.writeBinary(pi.getNodeId().bytes());
			if (pi.isDelegated())
				gen.writeBinary(pi.getOrigin().bytes());
			else
				gen.writeNull();
			gen.writeNumber(pi.getPort());
			if (pi.hasAlternativeURL())
				gen.writeString(pi.getAlternativeURL());
			else
				gen.writeNull();
			gen.writeBinary(pi.getSignature());
			gen.writeEndArray();
		}
		gen.writeEndArray();

	}

	@Override
	protected void _parse(String fieldName, CBORParser parser) throws MessageException, IOException {
		switch (fieldName) {
		case "p":
			peers = parsePeers(parser);
			break;

		default:
			System.out.println("Unknown field: " + fieldName);
			break;
		}
	}

	private List<PeerInfo> parsePeers(CBORParser parser) throws IOException {
		if (parser.currentToken() != JsonToken.START_ARRAY)
			throw new IOException("Invalid peers, should be array");

		// get peer id from p[0]
		parser.nextToken();
		Id peerId = Id.of(parser.getBinaryValue());

		List<PeerInfo> ps = new ArrayList<>();
		while (parser.nextToken() != JsonToken.END_ARRAY) {
			if (parser.currentToken() != JsonToken.START_ARRAY)
				throw new IOException("Invalid peer info, should be array");

			parser.nextToken();
			Id nodeId = Id.of(parser.getBinaryValue());
			parser.nextToken();
			Id origin = parser.currentToken() != JsonToken.VALUE_NULL ? Id.of(parser.getBinaryValue()) : null;
			parser.nextToken();
			int port = parser.getIntValue();
			parser.nextToken();
			String alt = parser.currentToken() != JsonToken.VALUE_NULL ? parser.getText() : null;
			parser.nextToken();
			byte[] signature = parser.getBinaryValue();

			if (parser.nextToken() != JsonToken.END_ARRAY)
				throw new IOException("Invalid peer info");

			PeerInfo pi = PeerInfo.of(peerId, nodeId, origin, port, alt, signature);
			ps.add(pi);
		}

		return ps.isEmpty() ? null : ps;
	}

	@Override
	public int estimateSize() {
		int size = super.estimateSize();

		if (peers != null && !peers.isEmpty()) {
			size += (2 + 2 + 2 + Id.BYTES);

			for (PeerInfo pi : peers) {
				size += (2 + 2 + Id.BYTES + 1 + Short.BYTES + 2 + Signature.BYTES);
				size += pi.isDelegated() ? 2 + Id.BYTES : 1;
				size += pi.hasAlternativeURL() ? 2 + pi.getAlternativeURL().getBytes().length : 1;
			}
		}

		return size;
	}

	@Override
	protected void _toString(StringBuilder b) {
		if (peers != null && !peers.isEmpty()) {
			b.append(",p:");
			b.append(peers.stream().map(p -> p.toString()).collect(Collectors.joining(",", "[", "]")));
		}
	}
}
