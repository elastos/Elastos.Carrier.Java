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

import elastos.carrier.kademlia.Id;

public class FindValueRequest extends LookupRequest {
	// Only send the value if the real sequence number greater than this.
	private int sequenceNumber;

	public FindValueRequest(Id targetId) {
		super(Method.FIND_VALUE, targetId);
	}

	public FindValueRequest() {
		this(null);
	}

	public int getSequenceNumber() {
		return sequenceNumber;
	}

	public void setSequenceNumber(int sequenceNumber) {
		this.sequenceNumber = sequenceNumber;
	}

	@Override
	protected void _serialize(JsonGenerator gen) throws IOException {
		if (sequenceNumber >= 0) {
			gen.writeFieldName("seq");
			gen.writeNumber(sequenceNumber);
		}
	}

	@Override
	protected void _parse(String fieldName, CBORParser parser) throws MessageException, IOException {
		switch (fieldName) {
		case "seq":
			sequenceNumber = parser.getIntValue();
			break;

		default:
			System.out.println("Unknown field: " + fieldName);
			break;
		}
	}

	@Override
	public int estimateSize() {
		return super.estimateSize() + 9;
	}

	@Override
	protected void _toString(StringBuilder b) {
		if (sequenceNumber >= 0)
			b.append(",seq:").append(sequenceNumber);
	}
}