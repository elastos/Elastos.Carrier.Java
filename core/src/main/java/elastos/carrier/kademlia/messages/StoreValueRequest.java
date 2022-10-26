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

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.dataformat.cbor.CBORParser;

import elastos.carrier.kademlia.Id;
import elastos.carrier.kademlia.Value;
import elastos.carrier.utils.Hex;

public class StoreValueRequest extends Message {
	private int token;
	private Id publicKey;
	private Id recipient;
	private byte[] nonce;
	private byte[] signature;
	private int expectedSequenceNumber = -1;
	private int sequenceNumber = -1;
	private byte[] value;

	public StoreValueRequest() {
		super(Type.REQUEST, Method.STORE_VALUE);
	}

	public int getToken() {
		return token;
	}

	public void setToken(int token) {
		this.token = token;
	}

	public Id getPublicKey() {
		return publicKey;
	}

	public void setPublicKey(Id publicKey) {
		this.publicKey = publicKey;
	}

	public Id getRecipient() {
		return recipient;
	}

	public void setRecipient(Id recipient) {
		this.recipient = recipient;
	}

	public byte[] getNonce() {
		return nonce;
	}

	public void setNonce(byte[] nonce) {
		this.nonce = nonce;
	}

	public byte[] getSignature() {
		return signature;
	}

	public void setSignature(byte[] signature) {
		this.signature = signature;
	}

	public int getExpectedSequenceNumber() {
		return expectedSequenceNumber;
	}

	public void setExpectedSequenceNumber(int expectedSequenceNumber) {
		this.expectedSequenceNumber = expectedSequenceNumber;
	}

	public int getSequenceNumber() {
		return sequenceNumber;
	}

	public void setSequenceNumber(int sequenceNumber) {
		this.sequenceNumber = sequenceNumber;
	}

	public byte[] getValue() {
		return value;
	}

	public void setValue(byte[] value) {
		this.value = value;
	}

	public boolean isMutable() {
		return publicKey != null;
	}

	public Id getValueId() {
		return Value.calculateId(publicKey, nonce, value);
	}

	public Value toValue() {
		return new Value(getPublicKey(), getRecipient(), getNonce(),
				getSequenceNumber(), getSignature(), getValue());
	}

	@Override
	protected void serialize(JsonGenerator gen) throws IOException {
		gen.writeFieldName(getType().toString());
		gen.writeStartObject();

		gen.writeFieldName("tok");
		gen.writeNumber(token);

		if (publicKey != null) {
			gen.writeFieldName("k");
			gen.writeBinary(publicKey.getBytes());

			if (recipient != null) {
				gen.writeFieldName("rec");
				gen.writeBinary(recipient.getBytes());
			}

			if (nonce != null) {
				gen.writeFieldName("n");
				gen.writeBinary(nonce);
			}

			if (signature != null) {
				gen.writeFieldName("sig");
				gen.writeBinary(signature);
			}

			if (expectedSequenceNumber >= 0) {
				gen.writeFieldName("cas");
				gen.writeNumber(expectedSequenceNumber);
			}

			if (sequenceNumber >= 0) {
				gen.writeFieldName("seq");
				gen.writeNumber(sequenceNumber);
			}
		}

		gen.writeFieldName("v");
		gen.writeBinary(value);

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
			case "k":
				publicKey = new Id(parser.getBinaryValue());
				break;

			case "rec":
				recipient = new Id(parser.getBinaryValue());
				break;

			case "n":
				nonce = parser.getBinaryValue();
				break;

			case "sig":
				signature = parser.getBinaryValue();
				break;

			case "cas":
				expectedSequenceNumber = parser.getIntValue();
				break;

			case "seq":
				sequenceNumber = parser.getIntValue();
				break;

			case "tok":
				token = parser.getIntValue();
				break;

			case "v":
				value = parser.getBinaryValue();
				break;

			default:
				System.out.println("Unknown field: " + fieldName);
				break;
			}
		}
	}

	@Override
	public int estimateSize() {
		return super.estimateSize() + 208 + value.length;
	}

	@Override
	protected void toString(StringBuilder b) {
		b.append(",q:{");

		if (publicKey != null) {
			b.append("k:").append(publicKey.toString());

			if (recipient != null)
				b.append(",rec:").append(recipient.toString());

			if (nonce != null)
				b.append(",n:").append(Hex.encode(nonce));

			if (signature != null)
				b.append(",sig:").append(Hex.encode(signature));

			if (expectedSequenceNumber >= 0)
				b.append(",cas:").append(expectedSequenceNumber);

			if (sequenceNumber >= 0)
				b.append(",seq:").append(sequenceNumber);

			b.append(",");
		}

		b.append("tok:").append(token);
		b.append(",v:").append(Hex.encode(value));
		b.append("}");
	}
}
