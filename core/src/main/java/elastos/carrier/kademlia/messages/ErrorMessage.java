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
import java.nio.charset.Charset;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.dataformat.cbor.CBORParser;

public class ErrorMessage extends Message {
	private static final Charset utf8 = Charset.forName("UTF-8");

	private int code;
	private String message;

	public ErrorMessage(Method method, int txid, int code, String message) {
		super(Type.ERROR, method, txid);
		this.code = code;
		this.message = message;
	}

	protected ErrorMessage(Method method) {
		super(Type.ERROR, method);
	}

	public int getCode() {
		return code;
	}

	public String getMessage() {
		return message;
	}

	@Override
	protected void serialize(JsonGenerator gen) throws IOException {
		gen.writeFieldName(getType().toString());
		gen.writeStartObject();
		gen.writeFieldName("c");
		gen.writeNumber(code);
		gen.writeFieldName("m");
		gen.writeString(message);
		gen.writeEndObject();
	}

	@Override
	protected void parse(String fieldName, CBORParser parser) throws MessageException, IOException {
		if (!fieldName.equals(Type.ERROR.toString()) || parser.getCurrentToken() != JsonToken.START_OBJECT)
			throw new MessageException("Invalid error message");

		while (parser.nextToken() != JsonToken.END_OBJECT) {
			String name = parser.getCurrentName();
			parser.nextToken();
			switch  (name) {
			case "c":
				code = parser.getIntValue();
				break;

			case "m":
				message = parser.getValueAsString();
				break;

			default:
				System.out.println("Unknown field: " + fieldName);
			}
		}
	}

	@Override
	public int estimateSize() {
		return super.estimateSize() + 16 + (message != null ? message.getBytes(utf8).length : 0);
	}

	@Override
	public void toString(StringBuilder b) {
		b.append(",e:{c:").append(code).append(",m:'").append(String.valueOf(message)).append("'}");;
	}
}
