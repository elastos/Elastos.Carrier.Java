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
import com.fasterxml.jackson.dataformat.cbor.CBORParser;

import elastos.carrier.Id;
import elastos.carrier.Value;
import elastos.carrier.utils.Hex;

public class FindValueResponse extends LookupResponse {
	private Id publicKey;
	private Id recipient;
	private byte[] nonce;
	private byte[] signature;
	private int sequenceNumber = -1;
	private byte[] value;

	public FindValueResponse(int txid) {
		super(Method.FIND_VALUE, txid);
	}

	protected FindValueResponse() {
		this(0);
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

	public Value value() {
		return Value.of(getPublicKey(), getRecipient(), getNonce(),
				getSequenceNumber(), getSignature(), getValue());
	}

	@Override
	protected void _serialize(JsonGenerator gen) throws IOException {
		if (publicKey != null) {
			gen.writeFieldName("k");
			gen.writeBinary(publicKey.bytes());
		}

		if (recipient != null) {
			gen.writeFieldName("rec");
			gen.writeBinary(recipient.bytes());
		}

		if (nonce != null) {
			gen.writeFieldName("n");
			gen.writeBinary(nonce);
		}

		if (signature != null) {
			gen.writeFieldName("sig");
			gen.writeBinary(signature);
		}

		if (sequenceNumber >= 0) {
			gen.writeFieldName("seq");
			gen.writeNumber(sequenceNumber);
		}

		if (value != null) {
			gen.writeFieldName("v");
			gen.writeBinary(value);
		}
	}

	@Override
	protected void _parse(String fieldName, CBORParser parser) throws MessageException, IOException {
		switch (fieldName) {
		case "k":
			publicKey = Id.of(parser.getBinaryValue());
			break;

		case "rec":
			recipient = Id.of(parser.getBinaryValue());
			break;

		case "n":
			nonce = parser.getBinaryValue();
			break;

		case "sig":
			signature = parser.getBinaryValue();
			break;

		case "seq":
			sequenceNumber = parser.getIntValue();
			break;

		case "v":
			value = parser.getBinaryValue();
			break;

		default:
			System.out.println("Unknown field: " + fieldName);
			break;
		}
	}

	@Override
	public int estimateSize() {
		return super.estimateSize() + 195 + (value == null ? 0 : value.length);
	}

	@Override
	protected void _toString(StringBuilder b) {
		if (publicKey != null)
			b.append(",k:").append(publicKey.toString());

		if (recipient != null)
			b.append(",rec:").append(recipient.toString());

		if (nonce != null)
			b.append(",n:").append(Hex.encode(nonce));

		if (signature != null)
			b.append(",sig:").append(Hex.encode(signature));

		if (sequenceNumber >= 0)
			b.append(",seq:").append(sequenceNumber);

		if (value != null)
			b.append(",v:").append(Hex.encode(value));
	}
}
