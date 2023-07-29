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

package elastos.carrier;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.text.Normalizer;
import java.util.Objects;

import elastos.carrier.crypto.Signature;

public class PeerInfo {
	private static final Charset UTF8 = Charset.forName("UTF-8");

	private Id publicKey;			// Peer ID
	private byte[] privateKey;		// Private key to sign the peer info
	private Id nodeId;				// The node that provide the service peer
	private Id origin;				// The node that announce the peer
	private int port;
	private String alternativeURL;
	private byte[] signature;

	private PeerInfo(Id peerId, byte[] privateKey, Id nodeId, Id origin, int port,
			String alternativeURL, byte[] signature) {
		if (peerId == null)
			throw new IllegalArgumentException("Invalid peer id");

		if (privateKey != null && privateKey.length != Signature.PrivateKey.BYTES)
			throw new IllegalArgumentException("Invalid private key");

		if (nodeId == null)
			throw new IllegalArgumentException("Invalid node id");

		if (port <= 0 || port > 65535)
			throw new IllegalArgumentException("Invalid port");

		if (signature == null || signature.length != Signature.BYTES)
			throw new IllegalArgumentException("Invalid signature");

		this.publicKey = peerId;
		this.privateKey = privateKey;
		this.nodeId = nodeId;
		this.origin = origin != null ? origin : nodeId;
		this.port = port;
		if (alternativeURL != null && !alternativeURL.isEmpty())
			this.alternativeURL = Normalizer.normalize(alternativeURL, Normalizer.Form.NFC);
		this.signature = signature;
	}

	private PeerInfo(Signature.KeyPair keypair, Id nodeId, Id origin, int port, String alternativeURL) {
		if (keypair == null)
			throw new IllegalArgumentException("Invalid keypair");

		if (nodeId == null)
			throw new IllegalArgumentException("Invalid node id");

		if (port <= 0 || port > 65535)
			throw new IllegalArgumentException("Invalid port");

		this.publicKey = new Id(keypair.publicKey().bytes());;
		this.privateKey = keypair.privateKey().bytes();
		this.nodeId = nodeId;
		this.origin = origin != null ? origin : nodeId;
		this.port = port;
		if (alternativeURL != null && !alternativeURL.isEmpty())
			this.alternativeURL = Normalizer.normalize(alternativeURL, Normalizer.Form.NFC);
		this.signature = Signature.sign(getSignData(), keypair.privateKey());
	}

	public static PeerInfo of(Id peerId, Id nodeId, int port, byte[] signature) {
		return new PeerInfo(peerId, null, nodeId, nodeId, port, null, signature);
	}

	public static PeerInfo of(Id peerId, byte[] privateKey, Id nodeId, int port, byte[] signature) {
		return new PeerInfo(peerId, privateKey, nodeId, nodeId, port, null, signature);
	}

	public static PeerInfo of(Id peerId, Id nodeId, int port, String alternativeURL, byte[] signature) {
		return new PeerInfo(peerId, null, nodeId, nodeId, port, alternativeURL, signature);
	}

	public static PeerInfo of(Id peerId, byte[] privateKey, Id nodeId, int port,
			String alternativeURL, byte[] signature) {
		return new PeerInfo(peerId, privateKey, nodeId, nodeId, port, alternativeURL, signature);
	}

	public static PeerInfo of(Id peerId, Id nodeId, Id origin, int port, byte[] signature) {
		return new PeerInfo(peerId, null, nodeId, origin, port, null, signature);
	}

	public static PeerInfo of(Id peerId, byte[] privateKey, Id nodeId,  Id origin, int port, byte[] signature) {
		return new PeerInfo(peerId, privateKey, nodeId, origin, port, null, signature);
	}

	public static PeerInfo of(Id peerId, Id nodeId, Id origin, int port, String alternativeURL, byte[] signature) {
		return new PeerInfo(peerId, null, nodeId, origin, port, alternativeURL, signature);
	}

	public static PeerInfo of(Id peerId, byte[] privateKey, Id nodeId, Id origin, int port,
			String alternativeURL, byte[] signature) {
		return new PeerInfo(peerId, privateKey, nodeId, origin, port, alternativeURL, signature);
	}

	public static PeerInfo create(Id nodeId, int port) {
		return create(null, nodeId, nodeId, port, null);
	}

	public static PeerInfo create(Signature.KeyPair keypair, Id nodeId, int port) {
		return create(keypair, nodeId, nodeId, port, null);
	}

	public static PeerInfo create(Id nodeId, Id origin, int port) {
		return create(null, nodeId, origin, port, null);
	}

	public static PeerInfo create(Signature.KeyPair keypair, Id nodeId, Id origin, int port) {
		return create(keypair, nodeId, origin, port, null);
	}

	public static PeerInfo create(Id nodeId, int port, String alternativeURL) {
		return create(null, nodeId, nodeId, port, alternativeURL);
	}

	public static PeerInfo create(Signature.KeyPair keypair, Id nodeId, int port, String alternativeURL) {
		return create(keypair, nodeId, nodeId, port, alternativeURL);
	}

	public static PeerInfo create(Id nodeId, Id origin, int port, String alternativeURL) {
		return create(null, nodeId, origin, port, alternativeURL);
	}

	public static PeerInfo create(Signature.KeyPair keypair, Id nodeId, Id origin,
			int port, String alternativeURL) {
		if (keypair == null)
			keypair = Signature.KeyPair.random();

		return new PeerInfo(keypair, nodeId, origin, port, alternativeURL);
	}

	public Id getId() {
		return publicKey;
	}

	public boolean hasPrivateKey() {
		return privateKey != null;
	}

	public byte[] getPrivateKey() {
		return privateKey;
	}

	public Id getNodeId() {
		return nodeId;
	}

	public Id getOrigin() {
		return origin;
	}

	public boolean isDelegated() {
		return !origin.equals(nodeId);
	}

	public int getPort() {
		return port;
	}

	public String getAlternativeURL() {
		return alternativeURL;
	}

	public boolean hasAlternativeURL() {
		return alternativeURL != null && !alternativeURL.isEmpty();
	}

	public byte[] getSignature() {
		return signature;
	}

	private byte[] getSignData() {
		byte[] alt = alternativeURL == null || alternativeURL.isEmpty() ?
				null : alternativeURL.getBytes(UTF8);

		byte[] toSign = new byte[Id.BYTES * 2 + Short.BYTES + (alt == null ? 0 : alt.length)];
		ByteBuffer buf = ByteBuffer.wrap(toSign);
		buf.put(nodeId.bytes())
			.put(origin.bytes())
			.putShort((short)port);
		if (alt != null)
			buf.put(alt);

		return toSign;
	}

	public boolean isValid() {
		if (signature == null || signature.length != Signature.BYTES)
			return false;

		Signature.PublicKey pk = publicKey.toSignatureKey();

		return Signature.verify(getSignData(), signature, pk);
	}

	@Override
	public int hashCode() {
		return publicKey.hashCode() + nodeId.hashCode() + origin.hashCode() + 0x70; // 'p'
	}

	@Override
	public boolean equals(Object o) {
		if (o == this)
			return true;

		if (o instanceof PeerInfo) {
			PeerInfo v = (PeerInfo)o;

			return Objects.equals(publicKey, v.publicKey) &&
					Objects.equals(nodeId, v.nodeId) &&
					Objects.equals(origin, v.origin) &&
					port == v.port &&
					Objects.equals(alternativeURL, v.alternativeURL);
		}

		return false;
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append("<")
			.append(publicKey.toString()).append(',')
			.append(nodeId.toString()).append(',');
		if (isDelegated())
			sb.append(getOrigin().toString()).append(',');
		sb.append(port);
		if (hasAlternativeURL())
			sb.append(",").append(alternativeURL);
		sb.append(">");
		return sb.toString();
	}
}
