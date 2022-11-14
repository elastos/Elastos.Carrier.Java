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

package elastos.carrier.deps;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Random;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.cbor.CBORFactory;
import com.fasterxml.jackson.dataformat.cbor.CBORGenerator;
import com.fasterxml.jackson.dataformat.cbor.CBORParser;
import com.fasterxml.jackson.dataformat.cbor.databind.CBORMapper;

import elastos.carrier.utils.Hex;

@TestMethodOrder(OrderAnnotation.class)
public class CborTests {
	public static class Pojo {
		@JsonProperty
		private int intValue;

		@JsonProperty
		private byte byteValue;

		@JsonProperty
		private char charValue;

		@JsonProperty
		private String strValue;

		@JsonProperty
		private boolean boolValue;

		@JsonProperty
		private int[] intArray;

		@JsonProperty
		private char[] charArray;

		@JsonProperty
		private byte[] byteArray;

		protected Pojo() {};

		public Pojo(int i, byte b, char c, String s, boolean bool, int[] ia, char[] ca, byte[] ba) {
			this.intValue = i;
			this.byteValue = b;
			this.charValue = c;
			this.strValue = s;
			this.boolValue = bool;
			this.intArray = ia;
			this.charArray = ca;
			this.byteArray = ba;
		}

		@Override
		public String toString() {
			StringBuilder strb = new StringBuilder();
			strb.append("{\n")
			.append("  intValue = " + intValue + ",\n")
			.append("  byteValue = " + byteValue + ",\n")
			.append("  charValue = " + charValue + ",\n")
			.append("  strValue = " + strValue + ",\n")
			.append("  boolValue = " + boolValue + ",\n")
			.append("  intArray = " + Arrays.toString(intArray) + ",\n")
			.append("  charArray = " + Arrays.toString(charArray) + ",\n")
			.append("  byteArray = " + Arrays.toString(byteArray) + "\n")
			.append("}");

			return strb.toString();
		}
	}

	private static int[] IA = new int[] { -1, 0, 65535, 65536, 0x7FFFFFFF, 0x80000000, 0xFFFFFFF0, 0xFFFFFFFF };
	private static char[] CA = new char[] { 'A', 'b', 'c', 'B', 'o', 'o',  'Z', 'o', 'o'};
	private static byte[] BA;
	static {
		BA = new byte[1024];
		new Random().nextBytes(BA);
	}

	// For performace tests
	// private static int LOOPS = 1000000;
	private static int LOOPS = 100;

	@Test
	@Order(1)
	public void testJSONSerialize() throws Exception {
		ObjectMapper mapper = new ObjectMapper();

		Pojo pojo = new Pojo(1024, (byte)8, 'c', "Hello World!", true, IA, CA, BA);

		@SuppressWarnings("unused")
		String json = null;
		long start = System.currentTimeMillis();

		for (int i = 0; i < LOOPS; i++)
			json = mapper.writeValueAsString(pojo);

		long end = System.currentTimeMillis();
		System.out.format("JSON object serialize: %d ms, size %d bytes\n", end - start, json.length());
	}

	@Test
	@Order(2)
	public void testJSONDeserialize() throws Exception {
		ObjectMapper mapper = new ObjectMapper();

		Pojo pojo = new Pojo(1024, (byte)8, 'c', "Hello World!", true, IA, CA, BA);
		String json = mapper.writeValueAsString(pojo);

		@SuppressWarnings("unused")
		Pojo pojo2 = null;
		long start = System.currentTimeMillis();

		for (int i = 0; i < LOOPS; i++)
			pojo2 = mapper.readValue(json, Pojo.class);

		long end = System.currentTimeMillis();
		System.out.format("JSON object deserialize: %d ms\n", end - start);
	}

	@Test
	@Order(3)
	public void testCBORSerialize() throws Exception {
		ObjectMapper mapper = new CBORMapper();

		Pojo pojo = new Pojo(1024, (byte)8, 'c', "Hello World!", true, IA, CA, BA);

		@SuppressWarnings("unused")
		byte[] cbor = null;
		long start = System.currentTimeMillis();

		for (int i = 0; i < LOOPS; i++)
			cbor = mapper.writeValueAsBytes(pojo);

		long end = System.currentTimeMillis();
		System.out.format("CBOR object serialize: %d ms, size %d bytes\n", end - start, cbor.length);
	}

	@Test
	@Order(4)
	public void testCBORDeserialize() throws Exception {
		ObjectMapper mapper = new CBORMapper();

		Pojo pojo = new Pojo(1024, (byte)8, 'c', "Hello World!", true, IA, CA, BA);
		byte[] cbor = mapper.writeValueAsBytes(pojo);

		@SuppressWarnings("unused")
		Pojo pojo2 = null;
		long start = System.currentTimeMillis();

		for (int i = 0; i < LOOPS; i++)
			pojo2 = mapper.readValue(cbor, Pojo.class);

		long end = System.currentTimeMillis();
		System.out.format("CBOR object deserialize: %d ms\n", end - start);
	}

	@Test
	@Order(5)
	public void testCBORStreamSerialize() throws Exception {
		CBORFactory cf = new CBORFactory();

		Pojo pojo = new Pojo(1024, (byte)8, 'c', "Hello World!", true, IA, CA, BA);

		byte[] cbor = null;
		long start = System.currentTimeMillis();
		for (int i = 0; i < LOOPS; i++) {
			ByteArrayOutputStream bos = new ByteArrayOutputStream();
			CBORGenerator gen = cf.createGenerator(bos);
			gen.writeStartObject();
			gen.writeNumberField("intValue", pojo.intValue);
			gen.writeNumberField("byteValue", pojo.byteValue);
			gen.writeNumberField("charValue", pojo.charValue);
			gen.writeStringField("strValue", pojo.strValue);
			gen.writeBooleanField("boolValue", pojo.boolValue);
			gen.writeFieldName("intArray");
			gen.writeArray(pojo.intArray, 0, pojo.intArray.length);
			gen.writeFieldName("charArray");
			gen.writeString(pojo.charArray, 0, pojo.charArray.length);
			gen.writeBinaryField("byteArray", pojo.byteArray);
			gen.writeEndObject();
			gen.close();

			cbor = bos.toByteArray();
		}

		long end = System.currentTimeMillis();
		System.out.format("CBOR stream serialize: %d ms, size %d bytes\n", end - start, cbor.length);

		Pojo pojo2 = new CBORMapper().readValue(cbor, Pojo.class);
	}

	@Test
	@Order(6)
	public void testCBORStreamDeserialize() throws Exception {
		ObjectMapper mapper = new CBORMapper();
		CBORFactory cf = new CBORFactory();

		Pojo pojo = new Pojo(1024, (byte)8, 'c', "Hello World!", true, IA, CA, BA);
		byte[] cbor = mapper.writeValueAsBytes(pojo);

		long start = System.currentTimeMillis();
		for (int i = 0; i < LOOPS; i++) {
			CBORParser parser = cf.createParser(cbor);
			parser.nextToken(); // Start object
			Pojo o = new Pojo();
			while (parser.nextToken() != JsonToken.END_OBJECT) {
				String fieldName = parser.getCurrentName();
				parser.nextToken();
				switch (fieldName) {
				case "intValue":
					o.intValue = parser.getIntValue();
					break;

				case "byteValue":
					o.byteValue = (byte)parser.getShortValue();
					break;

				case "charValue":
					o.charValue = parser.getText().charAt(0);
					break;

				case "strValue":
					o.strValue = parser.getText();
					break;

				case "boolValue":
					o.boolValue = parser.getBooleanValue();
					break;

				case "intArray":
					ArrayList<Integer> a = new ArrayList<>();
					while (parser.nextToken() != JsonToken.END_ARRAY) {
						a.add(parser.getIntValue());
					}
					o.intArray = a.stream().mapToInt(Integer::intValue).toArray();
					break;

				case "charArray":
					o.charArray = parser.getText().toCharArray();
					break;

				case "byteArray":
					o.byteArray = parser.getBinaryValue();
					break;
				}
			}
			parser.close();
		}
		long end = System.currentTimeMillis();
		System.out.format("CBOR stream deserialize: %d ms\n", end - start);
	}

	@Disabled("To be removed")
	@Test
	@Order(100)
	public void testCBORSerializedSize() throws Exception {
		//ObjectMapper mapper = new ObjectMapper();
		ObjectMapper mapper = new CBORMapper();

		ObjectNode node = mapper.createObjectNode();
		node.set("y", mapper.convertValue("ab", JsonNode.class));
		//System.out.println(mapper.writeValueAsString(node));
		System.out.println(Hex.encode(mapper.writeValueAsBytes(node)));

		node = mapper.createObjectNode();
		byte b = 0;
		node.set("yy", mapper.convertValue(b, JsonNode.class));
		//System.out.println(mapper.writeValueAsString(node));
		System.out.println(Hex.encode(mapper.writeValueAsBytes(node)));

		node = mapper.createObjectNode();
		b = (byte)0x27;
		node.set("yy", mapper.convertValue(b, JsonNode.class));
		//System.out.println(mapper.writeValueAsString(node));
		System.out.println(Hex.encode(mapper.writeValueAsBytes(node)));

		node = mapper.createObjectNode();
		b = (byte)0xf8;
		node.set("yy", mapper.convertValue(b, JsonNode.class));
		//System.out.println(mapper.writeValueAsString(node));
		System.out.println(Hex.encode(mapper.writeValueAsBytes(node)));

		node = mapper.createObjectNode();
		int i = 0;
		node.set("yy", mapper.convertValue(i, JsonNode.class));
		//System.out.println(mapper.writeValueAsString(node));
		System.out.println(Hex.encode(mapper.writeValueAsBytes(node)));

		node = mapper.createObjectNode();
		i = 0x7F;
		node.set("yy", mapper.convertValue(i, JsonNode.class));
		//System.out.println(mapper.writeValueAsString(node));
		System.out.println(Hex.encode(mapper.writeValueAsBytes(node)));

		node = mapper.createObjectNode();
		i = 0xf8;
		node.set("yy", mapper.convertValue(i, JsonNode.class));
		//System.out.println(mapper.writeValueAsString(node));
		System.out.println(Hex.encode(mapper.writeValueAsBytes(node)));

		node = mapper.createObjectNode();
		i = 0x1234;
		node.set("yy", mapper.convertValue(i, JsonNode.class));
		//System.out.println(mapper.writeValueAsString(node));
		System.out.println(Hex.encode(mapper.writeValueAsBytes(node)));

		node = mapper.createObjectNode();
		i = 0x76543210;
		node.set("yy", mapper.convertValue(i, JsonNode.class));
		//System.out.println(mapper.writeValueAsString(node));
		System.out.println(Hex.encode(mapper.writeValueAsBytes(node)));

		node = mapper.createObjectNode();
		byte v = 0;
		node.set("yy", mapper.convertValue(v, JsonNode.class));
		//System.out.println(mapper.writeValueAsString(node));
		System.out.println(Hex.encode(mapper.writeValueAsBytes(node)));

		node = mapper.createObjectNode();
		node.set("y", mapper.convertValue("hello", JsonNode.class));
		//System.out.println(mapper.writeValueAsString(node));
		System.out.println(Hex.encode(mapper.writeValueAsBytes(node)));

		node = mapper.createObjectNode();
		node.set("y", mapper.convertValue(new byte[] {0x01, 0x02, 0x03, 0x04}, JsonNode.class));
		//System.out.println(mapper.writeValueAsString(node));
		System.out.println(Hex.encode(mapper.writeValueAsBytes(node)));
	}
}
