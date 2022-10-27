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

import elastos.carrier.Id;

public abstract class LookupRequest extends Message {
	private Id target;
	private boolean want4;
	private boolean want6;
	private boolean wantToken;

	public LookupRequest(Method method, Id target) {
		super(Type.REQUEST, method);
		this.target = target;
	}

	public Id getTarget() {
		return target;
	}

	public void setWant4(boolean want4) {
		this.want4 = want4;
	}

	public boolean doesWant4() {
		return want4;
	}

	public void setWant6(boolean want6) {
		this.want6 = want6;
	}

	public boolean doesWant6() {
		return want6;
	}

    protected void setWantToken(boolean wantToken) {
		this.wantToken = wantToken;
	}

    protected boolean doesWantToken() {
		return wantToken;
	}

	protected int getWant() {
		int want = 0;

		if (want4)
			want |= 0x01;

		if (want6)
			want |= 0x02;

		if (wantToken)
			want |= 0x04;

		return want;
	}

	protected void setWant(int want) {
		want4 = (want & 0x01) == 0x01;
		want6 = (want & 0x02) == 0x02;
		wantToken = (want & 0x04) == 0x04;
	}

	@Override
	protected void serialize(JsonGenerator gen) throws IOException {
		gen.writeFieldName(getType().toString());
		gen.writeStartObject();
		gen.writeFieldName("t");
		gen.writeBinary(target.bytes());
		gen.writeFieldName("w");
		gen.writeNumber(getWant());
		_serialize(gen);
		gen.writeEndObject();
	}

	protected void _serialize(JsonGenerator gen) throws IOException {
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

			case "w":
				setWant(parser.getIntValue());
				break;

			default:
				_parse(name, parser);
				break;
			}
		}
	}

	protected void _parse(String fieldName, CBORParser parser) throws MessageException, IOException {
	}

	@Override
	public int estimateSize() {
		return super.estimateSize() + 43;
	}

	@Override
	protected void toString(StringBuilder b) {
		b.append(",q:{t:").append(target).append(",w:").append(getWant());
		_toString(b);
		b.append("}");
	}

	protected void _toString(StringBuilder b) {
	}
}
