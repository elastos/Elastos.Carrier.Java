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

public class AnnouncePeerRequest extends Message {
	private Id target;
	private Id proxyId;
	private int port;
	private String alt;
	private byte[] signature;
	private int token;

	// private String name;

	public AnnouncePeerRequest(Id target, int port, String alt, byte[] signature, int token) {
		super(Type.REQUEST, Method.ANNOUNCE_PEER);
		this.target = target;
		this.port = port;
		this.alt = alt;
		this.signature = signature;
		this.token = token;
	}

	protected AnnouncePeerRequest() {
		super(Type.REQUEST, Method.ANNOUNCE_PEER);
	}

	public Id getTarget() {
		return target;
	}

	public void setTarget(Id target) {
		this.target = target;
	}

	public Id getProxyId() {
		return proxyId;
	}

	public int getPort() {
		return port;
	}

	public void setPort(int port) {
		this.port = port;
	}

	public int getToken() {
		return token;
	}

	public void setToken(int token) {
		this.token = token;
	}

	public String getAlt() {
		return alt;
	}

	public byte[] getSignature() {
		return signature;
	}

	// public String getName() {
	//	return name;
	// }

	// public void setName(String name) {
	// 	this.name = name;
	// }

	@Override
	protected void serialize(JsonGenerator gen) throws IOException {
		gen.writeFieldName(getType().toString());
		gen.writeStartObject();

		gen.writeFieldName("t");
		gen.writeBinary(target.bytes());

		gen.writeFieldName("p");
		gen.writeNumber(port);

		if (alt != null) {
			gen.writeFieldName("alt");
			gen.writeString(alt);
		}

		gen.writeFieldName("sig");
		gen.writeBinary(signature);

		gen.writeFieldName("tok");
		gen.writeNumber(token);

		// if (name != null) {
		// 	gen.writeFieldName("n");
		// 	gen.writeString(name);
		// }

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
				target = Id.of(parser.getBinaryValue());
				break;

			case "x":
				proxyId = Id.of(parser.getBinaryValue());
				break;

			case "p":
				port = parser.getIntValue();
				break;

			case "alt":
				alt = parser.getValueAsString();
				break;

			case "sig":
				signature = parser.getBinaryValue();
				break;

			case "tok":
				token = parser.getIntValue();
				break;

				// case "n":
				// 	name = parser.getValueAsString();
				// 	break;

			default:
				System.out.println("Unknown field: " + fieldName);
				break;
			}
		}
	}

	@Override
	public int estimateSize() {
		// return super.estimateSize() + 54; // + (name != null ? name.length() + 5 : 0);
        return super.estimateSize() + 1024; //TODO:: should estimate size later
	}

	@Override
	protected void toString(StringBuilder b) {
		b.append(",q:{");
		b.append("t:").append(target.toString());
		if (proxyId != null && proxyId != Id.zero())
			b.append(",x:").append(proxyId.toString());
		b.append(",p:").append(port);
		if (alt != null && !alt.isEmpty())
			b.append(",alt:").append(alt);
		b.append(",sig:").append(signature.toString());
		b.append(",tok:").append(token);
		// if (name != null)
		// 	b.append(",n:").append(name);
		b.append("}");
	}
}
