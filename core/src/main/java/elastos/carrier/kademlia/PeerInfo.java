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

package elastos.carrier.kademlia;

import static com.google.common.base.Preconditions.checkArgument;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;

public class PeerInfo {
	private final Id nodeId;
	private final InetSocketAddress addr;

	public PeerInfo(Id nodeId, InetSocketAddress addr) {
		checkArgument(nodeId != null, "Invalid node id");
		checkArgument(addr != null, "Invalid socket address");
		this.nodeId = nodeId;
		this.addr = addr;
	}

	public PeerInfo(Id nodeId, InetAddress addr, int port) {
		checkArgument(nodeId != null, "Invalid node id");
		checkArgument(addr != null, "Invalid socket address");
		checkArgument(port > 0 && port < 65536, "Invalid port");

		this.nodeId = nodeId;
		this.addr = new InetSocketAddress(addr, port);
	}

	public PeerInfo(Id nodeId, String addr, int port) {
		checkArgument(nodeId != null, "Invalid node id");
		checkArgument(addr != null, "Invalid socket address");
		checkArgument(port > 0 && port < 65536, "Invalid port");

		this.nodeId = nodeId;
		this.addr = new InetSocketAddress(addr, port);
	}

	public PeerInfo(Id nodeId, byte[] addr, int port) {
		checkArgument(nodeId != null, "Invalid node id");
		checkArgument(addr != null, "Invalid socket address");
		checkArgument(addr.length == 4 || addr.length == 16, "Invalid address");
		checkArgument(port > 0 && port < 65536, "Invalid port");

		this.nodeId = nodeId;
		try {
			this.addr = new InetSocketAddress(InetAddress.getByAddress(addr), port);
		} catch (UnknownHostException e) {
			throw new RuntimeException("INTERNAL ERROR - Invalid binary inet address");
		}
	}

	public Id getNodeId() {
		return nodeId;
	}

	public InetSocketAddress getSocketAddress() {
		return addr;
	}

	public InetAddress getInetAddress() {
		return addr.getAddress();
	}

	public int getPort() {
		return addr.getPort();
	}

	public boolean isIPv4() {
		return addr.getAddress() instanceof Inet4Address;
	}

	public boolean isIPv6() {
		return addr.getAddress() instanceof Inet6Address;
	}

	@Override
	public int hashCode() {
		return addr.getAddress().hashCode();
	}

	@Override
	public boolean equals(Object o) {
		if (o instanceof PeerInfo) {
			PeerInfo ni = (PeerInfo) o;
			return ni.addr.equals(addr);
		}
		return false;
	}

	@Override
	public String toString() {
		return "<" + nodeId.toString() + "," + addr.getHostString() + "," + addr.getPort() + ">";
	}
}
