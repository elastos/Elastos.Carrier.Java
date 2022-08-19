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
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.dataformat.cbor.CBORParser;

import elastos.carrier.kademlia.DHT;
import elastos.carrier.kademlia.Id;
import elastos.carrier.kademlia.NodeInfo;

public abstract class LookupResponse extends Message {
	private List<NodeInfo> nodes4;
	private List<NodeInfo> nodes6;

	public LookupResponse(Method method, int txid) {
		super(Type.RESPONSE, method, txid);
	}

	public void setNodes4(List<NodeInfo> nodes4) {
		this.nodes4 = nodes4;
	}

	public List<NodeInfo> getNodes4() {
		return nodes4 != null ? nodes4 : Collections.emptyList();
	}

	public void setNodes6(List<NodeInfo> nodes6) {
		this.nodes6 = nodes6;
	}

	public List<NodeInfo> getNodes6() {
		return nodes6 != null ? nodes6 : Collections.emptyList();
	}

	public List<NodeInfo> getNodes(DHT.Type type) {
		if (type == DHT.Type.IPV4)
			return getNodes4();
		else
			return getNodes6();

	}

	@Override
	protected void serialize(JsonGenerator gen) throws IOException {
		gen.writeFieldName(getType().toString());
		gen.writeStartObject();

		if (nodes4 != null && !nodes4.isEmpty())
			serializeNodes(gen, "n4", nodes4);

		if (nodes6 != null && !nodes6.isEmpty()) {
			serializeNodes(gen, "n6", nodes6);
		}

		_serialize(gen);
		gen.writeEndObject();
	}

	protected void _serialize(JsonGenerator gen) throws IOException {
	}

	private void serializeNodes(JsonGenerator gen, String fieldName, List<NodeInfo> nodes) throws IOException {
		gen.writeFieldName(fieldName);
		gen.writeStartArray();
		for (NodeInfo ni : nodes) {
			gen.writeStartArray();
			gen.writeBinary(ni.getId().getBytes());
			gen.writeBinary(ni.getAddress().getAddress().getAddress());
			gen.writeNumber(ni.getAddress().getPort());
			gen.writeEndArray();
		}
		gen.writeEndArray();
	}

	@Override
	protected void parse(String fieldName, CBORParser parser) throws MessageException, IOException {
		if (!fieldName.equals(Type.RESPONSE.toString()) || parser.getCurrentToken() != JsonToken.START_OBJECT)
			throw new MessageException("Invalid " + getMethod() + " response message");

		while (parser.nextToken() != JsonToken.END_OBJECT) {
			String name = parser.getCurrentName();
			parser.nextToken();
			switch (name) {
			case "n4":
				nodes4 = parseNodes(fieldName, parser);
				break;

			case "n6":
				nodes6 = parseNodes(fieldName, parser);
				break;
			default:
				_parse(name, parser);
				break;
			}
		}
	}

	protected void _parse(String fieldName, CBORParser parser) throws MessageException, IOException {
	}

	private List<NodeInfo> parseNodes(String fieldName, CBORParser parser) throws IOException {
		if (parser.currentToken() != JsonToken.START_ARRAY)
			throw new IOException("Invalid " + fieldName + " data, should be array");

		List<NodeInfo> nodes = new ArrayList<>();
		while (parser.nextToken() != JsonToken.END_ARRAY) {
			if (parser.currentToken() != JsonToken.START_ARRAY)
				throw new IOException("Invalid " + fieldName + " info data, should be array");

			parser.nextToken();
			Id id = new Id(parser.getBinaryValue());
			parser.nextToken();
			InetAddress addr = InetAddress.getByAddress(parser.getBinaryValue());
			parser.nextToken();
			int port = parser.getIntValue();

			if (parser.nextToken() != JsonToken.END_ARRAY)
				throw new IOException("Invalid " + fieldName + " info data");

			nodes.add(new NodeInfo(id, addr, port));
		}

		return nodes.isEmpty() ? null : nodes;
	}

	@Override
	public int estimateSize() {
		int size = super.estimateSize() + 4;

		if (nodes4 != null && !nodes4.isEmpty())
			size += (5 + 44 * nodes4.size());

		if (nodes6 != null && !nodes6.isEmpty())
			size += (5 + 56 * nodes6.size());

		return size;
	}

	@Override
	protected void toString(StringBuilder b) {
		b.append(",r:{");

		if (nodes4 != null && !nodes4.isEmpty()) {
			b.append("n4:");
			b.append(nodes4.stream().map(n -> n.toString()).collect(Collectors.joining(",", "[", "]")));
		}

		if (nodes6 != null && !nodes6.isEmpty()) {
			if (nodes4 != null && !nodes4.isEmpty())
				b.append(",");

			b.append("n6:");
			b.append(nodes6.stream().map(n -> n.toString()).collect(Collectors.joining(",", "[", "]")));
		}

		_toString(b);
		b.append("}");
	}

	protected void _toString(StringBuilder b) {
	}
}
