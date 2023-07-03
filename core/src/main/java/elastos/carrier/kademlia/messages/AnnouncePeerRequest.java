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

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.dataformat.cbor.CBORParser;

import elastos.carrier.Id;
import elastos.carrier.PeerInfo;
import elastos.carrier.crypto.Signature;

public class AnnouncePeerRequest extends Message {
	private int token;
	private Id peerId;
	private Id nodeId; // Optional, only for the delegated peers
	private int port;
	private String alternativeURL;
	private byte[] signature;

	public AnnouncePeerRequest() {
		super(Type.REQUEST, Method.ANNOUNCE_PEER);
	}

	public AnnouncePeerRequest(PeerInfo peer, int token) {
		this();
		setPeer(peer);
		setToken(token);
	}

	public int getToken() {
		return token;
	}

	public void setToken(int token) {
		this.token = token;
	}

	public void setPeer(PeerInfo peer) {
		peerId = peer.getId();
		nodeId = peer.getNodeId();
		port = peer.getPort();
		if (peer.hasAlternativeURL())
			alternativeURL = peer.getAlternativeURL();
		signature = peer.getSignature();
	}

	public PeerInfo getPeer() {
		if (nodeId == null)
			nodeId = getId();

		return PeerInfo.of(peerId, nodeId, getId(), port, alternativeURL, signature);
	}

	public Id getTarget() {
		return peerId;
	}

	@Override
	protected void serialize(JsonGenerator gen) throws IOException {
		gen.writeFieldName(getType().toString());
		gen.writeStartObject();

		gen.writeFieldName("tok");
		gen.writeNumber(token);

		gen.writeFieldName("t");
		gen.writeBinary(peerId.bytes());

		if (!nodeId.equals(getId())) {
			gen.writeFieldName("x");
			gen.writeBinary(nodeId.bytes());
		}

		gen.writeFieldName("p");
		gen.writeNumber(port);

		if (alternativeURL != null) {
			gen.writeFieldName("alt");
			gen.writeString(alternativeURL);
		}

		gen.writeFieldName("sig");
		gen.writeBinary(signature);

		gen.writeEndObject();
	}

	@Override
	protected void parse(String fieldName, CBORParser parser) throws MessageException, IOException {
		if (!fieldName.equals(Type.REQUEST.toString()) || parser.getCurrentToken() != JsonToken.START_OBJECT)
			throw new MessageException("Invalid " + getMethod() + " request message");

		while (parser.nextToken() != JsonToken.END_OBJECT) {
			String name = parser.getCurrentName();
			parser.nextToken();
			switch (name) {
			case "t":
				peerId = Id.of(parser.getBinaryValue());
				break;

			case "x":
				nodeId = Id.of(parser.getBinaryValue());
				break;

			case "p":
				port = parser.getIntValue();
				break;

			case "alt":
				alternativeURL = parser.getValueAsString();
				break;

			case "sig":
				signature = parser.getBinaryValue();
				break;

			case "tok":
				token = parser.getIntValue();
				break;

			default:
				System.out.println("Unknown field: " + fieldName);
				break;
			}
		}
	}

	@Override
	public int estimateSize() {
        int size = 4 + 9 + 36 + 5 + 6 + Signature.BYTES;
        size += nodeId.equals(getId()) ? 0 : 4 + Id.BYTES;
        size += alternativeURL == null ? 0 : 6 + alternativeURL.getBytes().length;
        return super.estimateSize() + size;
	}

	@Override
	protected void toString(StringBuilder b) {
		b.append(",q:{");
		b.append("t:").append(peerId.toString());
		if (nodeId != null && !nodeId.equals(getId()))
			b.append(",x:").append(nodeId.toString());
		b.append(",p:").append(port);
		if (alternativeURL != null && !alternativeURL.isEmpty())
			b.append(",alt:").append(alternativeURL);
		b.append(",sig:").append(signature.toString());
		b.append(",tok:").append(token);
		b.append("}");
	}
}
