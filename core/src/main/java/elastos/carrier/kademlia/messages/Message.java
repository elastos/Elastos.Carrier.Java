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

import static com.google.common.base.Preconditions.checkArgument;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.dataformat.cbor.CBORGenerator;
import com.fasterxml.jackson.dataformat.cbor.CBORParser;

import elastos.carrier.kademlia.Id;
import elastos.carrier.kademlia.RPCCall;
import elastos.carrier.kademlia.RPCServer;
import elastos.carrier.kademlia.Version;
import elastos.carrier.utils.Functional.ThrowingSupplier;
import elastos.carrier.utils.ThreadLocals;

public abstract class Message {
	private static final int TYPE_MASK = 0x000000E0;
	private static final int METHOD_MASK = 0x0000001F;

	public static final int MIN_SIZE = 12;
	protected static final int BASE_SIZE = 56;

	private final int type;
	private Id id;
	private int txid;
	private int version;

	private InetSocketAddress origin;
	private InetSocketAddress remote;

	private RPCServer server;
	private RPCCall associatedCall;

	public static enum Method {
		UNKNOWN(0x00),
		PING(0x01),
		FIND_NODE(0x02),
		ANNOUNCE_PEER(0x03),
		FIND_PEERS(0x04),
		STORE_VALUE(0x05),
		FIND_VALUE(0x06);

		private int value;

		private Method(int value) {
			this.value = value;
		}

		public int value() {
			return value;
		}

		@Override
		public String toString() {
			return name().toLowerCase();
		}

		public static Method valueOf(int value) {
			switch (value & METHOD_MASK) {
			case 0x01:
				return PING;

			case 0x02:
				return FIND_NODE;

			case 0x03:
				return ANNOUNCE_PEER;

			case 0x04:
				return FIND_PEERS;

			case 0x05:
				return STORE_VALUE;

			case 0x06:
				return FIND_VALUE;

			case 0x00:
				return UNKNOWN;

			default:
				throw new IllegalArgumentException("Invalid method: " + value);
			}
		}
	}

	public static enum Type {
		REQUEST(0x20),
		RESPONSE(0x40),
		ERROR(0x00);

		private int value;

		private Type(int value) {
			this.value = value;
		}

		public int value() {
			return value;
		}

		@Override
		public String toString() {
			if (value == REQUEST.value) return "q";
			else if (value == RESPONSE.value) return "r";
			else if (value == ERROR.value) return "e";
			else return null;
		}

		public static Type valueOf(int value) {
			switch (value & TYPE_MASK) {
			case 0x20:
				return REQUEST;

			case 0x40:
				return RESPONSE;

			case 0x00:
				return ERROR;

			default:
				throw new IllegalArgumentException("Invalid Type: " + value);
			}
		}
	}

	protected Message(Type type, Method method, int txid) {
		this.type = type.value | method.value;
		this.txid = txid;
		this.version = 0;
	}

	protected Message(Type type, Method method) {
		this.type = type.value | method.value;
	}

	public Type getType() {
		return Type.valueOf(type);
	}

	public Method getMethod() {
		return Method.valueOf(type);
	}

	public void setId(Id id) {
		this.id = id;
	}

	public Id getId() {
		return id;
	}

	public void setTxid(int txid) {
		assert(txid != 0);
		this.txid = txid;
	}

	public int getTxid() {
		return txid;
	}

	public void setVersion(int version) {
		this.version = version;
	}

	public int getVersion() {
		return version;
	}

	public String getReadableVersion() {
		return Version.toString(version);
	}

	public InetSocketAddress getOrigin() {
		return origin;
	}

	public void setOrigin(InetSocketAddress local) {
		this.origin = local;
	}

	public InetSocketAddress getRemote() {
		return remote;
	}

	public void setRemote(InetSocketAddress remote) {
		this.remote = remote;
	}

	public void setServer(RPCServer server) {
		this.server = server;
	}

	public RPCServer getServer() {
		return server;
	}

	public void setAssociatedCall(RPCCall call) {
		this.associatedCall = call;
	}

	public RPCCall getAssociatedCall() {
		return associatedCall;
	}

	public byte[] serialize() {
		ByteArrayOutputStream out = new ByteArrayOutputStream(1500);
		try {
			serialize(out);
		} catch (MessageException e) {
			throw new RuntimeException("INTERNAL ERROR: should never happen.");
		}
		return out.toByteArray();
	}

	// According to my tests, streaming serialization is about 30x faster than the object mapping
	public void serialize(OutputStream out) throws MessageException {
		try {
			CBORGenerator gen = ThreadLocals.CBORFactory().createGenerator(out);
			serializeInternal(gen);
			gen.close();
		} catch (IOException e) {
			throw new MessageException("Serialize mssage failed.", e);
		}
	}

	protected void serialize(JsonGenerator gen) throws MessageException, IOException {
	}

	private void serializeInternal(JsonGenerator gen) throws MessageException, IOException {
		gen.writeStartObject();

		gen.writeFieldName("y");
		gen.writeNumber(type);

		if (id != null) {
			gen.writeFieldName("i");
			gen.writeBinary(id.getBytes());
		}

		gen.writeFieldName("t");
		gen.writeNumber(txid);

		serialize(gen);

		if (version != 0) {
			gen.writeFieldName("v");
			gen.writeNumber(version);
		}

		gen.writeEndObject();
	}

	public static Message parse(byte[] data) throws MessageException {
		checkArgument(data != null && data.length >= MIN_SIZE, "Invalid data");

		return parse(() -> (ThreadLocals.CBORFactory().createParser(data)));
	}

	public static Message parse(InputStream in) throws MessageException {
		checkArgument(in.markSupported(), "Input stream shoud support mark()");
		in.mark(1500);

		return parse(() -> {
			in.reset();
			in.mark(1500);
			return ThreadLocals.CBORFactory().createParser(in);
		});
	}

	// According to my tests, streaming deserialization is about 25x faster than the object mapping
	private static Message parse(ThrowingSupplier<CBORParser, IOException> ps) throws MessageException {
		Message msg = null;

		try {
			int typeCode = Integer.MAX_VALUE;

			CBORParser parser = ps.get();
			int depth = 0;
			while (true) {
				JsonToken tok = parser.nextToken();

				if (tok == JsonToken.START_OBJECT) {
					depth++;
				} else if (tok == JsonToken.END_OBJECT) {
					if (--depth == 0)
						break;
				}

				String name = parser.getCurrentName();
				if (name != null && name.equals("y")) {
					parser.nextToken();
					typeCode = parser.getIntValue();
					break;
				}
			}

			if (typeCode == Integer.MAX_VALUE)
				throw new MessageException("Missing message type");

			Type type;
			try {
				type = Type.valueOf(typeCode);
			} catch (IllegalArgumentException e) {
				throw new MessageException("Invalid message type: " + typeCode);
			}

			Method method;
			try {
				method = Method.valueOf(typeCode);
			} catch (IllegalArgumentException e) {
				throw new MessageException("Invalid message type: " + typeCode);
			}

			msg = createMessage(type, method);

			parser = ps.get();
			depth = 0;
			while (true) {
				JsonToken tok = parser.nextToken();

				if (tok == JsonToken.START_OBJECT) {
					depth++;
				} else if (tok == JsonToken.END_OBJECT) {
					if (--depth == 0)
						break;
				} else if (tok == JsonToken.FIELD_NAME) {
					String name = parser.getCurrentName();
					parser.nextToken();
					switch (name) {
					case "y":
						break;

					case "i":
						try {
							msg.id = new Id(parser.getBinaryValue());
						} catch (IllegalArgumentException e) {
							throw new MessageException("Invalid node id for 'i'").setPartialMessage(PartialMessage.of(msg));
						}
						break;

					case "t":
						msg.txid = parser.getIntValue();
						break;

					case "q":
						if (type != Type.REQUEST)
							throw new MessageException("Invalid " + type.name().toLowerCase() + " message, unknown 'q'").setPartialMessage(PartialMessage.of(msg));
						else
							msg.parse(name, parser);
						break;

					case "r":
						if (type != Type.RESPONSE)
							throw new MessageException("Invalid " + type.name().toLowerCase() + " message, unknown 'r'").setPartialMessage(PartialMessage.of(msg));
						else
							msg.parse(name, parser);
						break;

					case "e":
						if (type != Type.ERROR)
							throw new MessageException("Invalid " + type.name().toLowerCase() + " message, unknown 'e'").setPartialMessage(PartialMessage.of(msg));
						else
							msg.parse(name, parser);
						break;

					case "v":
						msg.version = parser.getIntValue();
						break;
					}
				}
			}
		} catch (IOException e) {
			throw new MessageException("Parse message failed", e).setPartialMessage(PartialMessage.of(msg));
		}

		return msg;
	}

	protected void parse(String fieldName, CBORParser parser) throws MessageException, IOException {
	}

	private static Message createMessage(Type type, Method method) throws MessageException {
		switch (type) {
		case REQUEST:
			switch (method) {
			case PING:
				return new PingRequest();
			case FIND_NODE:
				return new FindNodeRequest();
			case ANNOUNCE_PEER:
				return new AnnouncePeerRequest();
			case FIND_PEERS:
				return new FindPeersRequest();
			case STORE_VALUE:
				return new StoreValueRequest();
			case FIND_VALUE:
				return new FindValueRequest();
			default:
				throw new MessageException("Invalid request method.");
			}

		case RESPONSE:
			switch (method) {
			case PING:
				return new PingResponse();
			case FIND_NODE:
				return new FindNodeResponse();
			case ANNOUNCE_PEER:
				return new AnnouncePeerResponse();
			case FIND_PEERS:
				return new FindPeersResponse();
			case STORE_VALUE:
				return new StoreValueResponse();
			case FIND_VALUE:
				return new FindValueResponse();
			default:
				throw new MessageException("Invalid response method.");
			}

		case ERROR:
			return new ErrorMessage(method);

		default:
			throw new RuntimeException("INTERNAL ERROR: should never happen.");
		}
	}

	public int estimateSize() {
		return BASE_SIZE;
	}

	@Override
	public String toString() {
		StringBuilder b = new StringBuilder(1500);
		b.append("y:").append(getType());
		b.append(",m:").append(getMethod());
		b.append(",i:").append(id);
		b.append(",t:").append(txid);

		toString(b);

		if (version != 0)
			b.append(",v:").append(getReadableVersion());

		return b.toString();
	}

	protected void toString(StringBuilder b) {
	}
}
