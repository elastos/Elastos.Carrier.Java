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

package elastos.carrier.kademlia.messages;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.dataformat.cbor.CBORParser;

import elastos.carrier.kademlia.Id;
import elastos.carrier.kademlia.PeerInfo;

public class FindPeersResponse extends LookupResponse {
	private int token;
	private List<PeerInfo> peers;

	public FindPeersResponse(int txid) {
		super(Method.FIND_PEERS, txid);
	}

	protected FindPeersResponse() {
		this(0);
	}

	public int getToken() {
		return token;
	}

	public void setToken(int token) {
		this.token = token;
	}

	public void setPeers(List<PeerInfo> peers) {
		this.peers = peers;
	}

	public List<PeerInfo> getPeers() {
		return peers != null ? peers : Collections.emptyList();
	}

	@Override
	protected void _serialize(JsonGenerator gen) throws IOException {
		if (peers != null && !peers.isEmpty()) {
			if (token != 0) {
				gen.writeFieldName("tok");
				gen.writeNumber(token);
			}

			if (peers != null && !peers.isEmpty()) {
				gen.writeFieldName("p");
				gen.writeStartArray();
				for (PeerInfo pi : peers) {
					gen.writeStartArray();
					gen.writeBinary(pi.getNodeId().getBytes());
					gen.writeBinary(pi.getInetAddress().getAddress());
					gen.writeNumber(pi.getPort());
					gen.writeEndArray();
				}
				gen.writeEndArray();
			}
		}
	}

	@Override
	protected void _parse(String fieldName, CBORParser parser) throws MessageException, IOException {
		switch (fieldName) {
		case "tok":
			token = parser.getIntValue();
			break;

		case "p":
			peers = parsePeers(fieldName, parser);
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
			Id nodeId = new Id(parser.getBinaryValue());
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
		int tokSize = (token == 0) ? 0 : 9;
		int piSize = (peers.get(0).getInetAddress() instanceof Inet4Address) ? 44 : 56;
		return super.estimateSize() + tokSize + (peers == null ? 0 : piSize * peers.size() + 4);
	}

	@Override
	protected void _toString(StringBuilder b) {
		if (peers != null && !peers.isEmpty()) {
			b.append("p:");
			b.append(peers.stream().map(p -> p.toString()).collect(Collectors.joining(",", "[", "]")));
		}
	}
}
