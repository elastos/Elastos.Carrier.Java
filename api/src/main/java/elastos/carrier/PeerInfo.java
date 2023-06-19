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
import java.util.Objects;

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
	private boolean usedAlt = false;

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

		if (this.proxyId == Id.zero())
			this.proxied = true;
		if (!this.alt.equals(""))
			this.usedAlt = true;
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
		return usedAlt;
	}

	public byte[] getSignature() {
		return signature;
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
		if (usedAlt)
			sb.append(",").append(alt);
		sb.append(",").append(signature.toString());
		sb.append(">");
		return sb.toString();
	}
}
