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

import java.net.Inet4Address;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.util.Objects;

import elastos.carrier.crypto.CryptoBox;
import elastos.carrier.crypto.Signature;
import elastos.carrier.utils.Hex;

public class PeerInfo {
	public static final int AF_IPV4 = 4;
	public static final int AF_IPV6 = 6;

	private final Id nodeId;
	private final Id proxyId;
	private final int port;
	private final String alt;
	private final int family;
	private final byte[] signature;

	private boolean proxied = false;
	private boolean hasAlt = false;

	public PeerInfo(Id nodeId, Id proxyId, int port, int family, String alt, byte[] signature) {
		if (nodeId == null)
			throw new IllegalArgumentException("Invalid node id: null");
		if (port <= 0 || port > 65535)
			throw new IllegalArgumentException("Invalid port: " + port);
		if (family != AF_IPV4 && family != AF_IPV6)
			throw new IllegalArgumentException("Invalid family: " + family);

		this.nodeId = nodeId;
		this.proxyId = proxyId == null ? Id.zero() : proxyId;
		this.port = port;
		this.family = family;
		this.alt = alt == null ? "" : alt;
		this.signature = signature;

		if (this.proxyId != Id.zero())
			this.proxied = true;
		if (!this.alt.equals(""))
			this.hasAlt = true;

		boolean ret = this.isValid();
	}

	public PeerInfo(Id nodeId, int port, int family, String alt, byte[] signature) {
		this(nodeId, null, port, family, alt, signature);
	}

	public Id getNodeId() {
		return nodeId;
	}

	public Id getProxyId() {
		return proxyId;
	}

	public boolean usingProxy() {
		return proxied;
	}

	public int getInetFamily() {
		return family;
	}

	public int getPort() {
		return port;
	}

	public boolean isIPv4() {
		return family == AF_IPV4;
	}

	public boolean isIPv6() {
		return family == AF_IPV6;
	}

	public int getFamily() {
		return family;
	}

	public String getAlt() {
		return alt;
	}

	public boolean isUsedAlt() {
		return hasAlt;
	}

	public byte[] getSignature() {
		return signature;
	}

	public int estimateSize() {
        return 1024; //TODO:: should estimate size later
    }

	@Override
	public int hashCode() {
		return nodeId.hashCode() + alt.hashCode() + family + 0x70; // 'p'
	}

	@Override
	public boolean equals(Object o) {
		if (o instanceof PeerInfo) {
			PeerInfo other = (PeerInfo) o;
			return this.nodeId.equals(other.nodeId) && this.port == other.port
					&& this.family == other.family && Objects.equals(this.alt, other.alt);
		}
		return false;
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append("<").append(nodeId.toString());
		if (proxied)
			sb.append(",").append(proxyId.toString());
		sb.append(",").append(port);
		if (isIPv4())
			sb.append(",ipv4");
		else if (isIPv4())
			sb.append(",ipv6");
		if (hasAlt)
			sb.append(",").append(alt);
		sb.append(",").append(signature.toString());
		sb.append(">");
		return sb.toString();
	}

	public static byte[] getSignData(Id nodeId, Id proxyId, int port, String alt) {
		boolean proxied = false;
		boolean hasAlt = false;
		alt = alt == null ? "" : alt;

		if (proxyId != Id.zero())
			proxied = true;
		if (!alt.equals(""))
			hasAlt = true;

		byte[] toSign = new byte[Id.BYTES + (proxied ? Id.BYTES : 0) + 2 +
		                         (hasAlt ? alt.getBytes().length : 0)];

		ByteBuffer buf = ByteBuffer.wrap(toSign);
		buf.put(nodeId.getBytes());
		if (proxied)
			buf.put(proxyId.bytes());

		buf.put((byte) (port & 0xFF));
		buf.put((byte) ((port>>8) & 0xFF));

		if (hasAlt)
			buf.put(alt.getBytes());

 		return toSign;
	}

	public boolean isValid() {
		Signature.PublicKey pk;
	    if (proxied)
	    	pk = Signature.PublicKey.fromBytes(proxyId.getBytes());
	    else
	    	pk = Signature.PublicKey.fromBytes(nodeId.getBytes());
	    return Signature.verify(getSignData(nodeId, proxyId, port, alt), signature, pk);
	}

}
