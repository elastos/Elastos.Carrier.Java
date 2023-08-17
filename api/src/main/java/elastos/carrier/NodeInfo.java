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

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;

public class NodeInfo {
	private final Id id;
	private final InetSocketAddress addr;
	private int version;

	public NodeInfo(Id id, InetSocketAddress addr) {
		if (id == null)
			throw new IllegalArgumentException("Invalid node id: null");

		if (addr == null)
			throw new IllegalArgumentException("Invalid socket address: null");

		this.id = id;
		this.addr = addr;
	}

	public NodeInfo(Id id, InetAddress addr, int port) {
		if (id == null)
			throw new IllegalArgumentException("Invalid node id: null");

		if (addr == null)
			throw new IllegalArgumentException("Invalid socket address: null");

		if (port <= 0 || port > 65535)
			throw new IllegalArgumentException("Invalud port: " + port);

		this.id = id;
		this.addr = new InetSocketAddress(addr, port);
	}

	public NodeInfo(Id id, String addr, int port) {
		if (id == null)
			throw new IllegalArgumentException("Invalid node id: null");

		if (addr == null)
			throw new IllegalArgumentException("Invalid socket address: null");

		if (port <= 0 || port > 65535)
			throw new IllegalArgumentException("Invalud port: " + port);

		this.id = id;
		this.addr = new InetSocketAddress(addr, port);
	}

	public NodeInfo(Id id, byte[] addr, int port) {
		if (id == null)
			throw new IllegalArgumentException("Invalid node id: null");
		if (addr == null)
			throw new IllegalArgumentException("Invalid socket address: null");
		if (port <= 0 || port > 65535)
			throw new IllegalArgumentException("Invalid port: " + port);

		this.id = id;
		try {
			this.addr = new InetSocketAddress(InetAddress.getByAddress(addr), port);
		} catch (UnknownHostException e) {
			throw new IllegalArgumentException("Invalid binary inet address", e);
		}
	}

	public NodeInfo(NodeInfo ni) {
		if (ni == null)
			throw new IllegalArgumentException("Invalid node info: null");

		this.id = ni.id;
		this.addr = ni.addr;
		this.version = ni.version;
	}

	public Id getId() {
		return id;
	}

	public InetSocketAddress getAddress() {
		return addr;
	}

	public InetAddress getInetAddress() {
		return addr.getAddress();
	}

	public int getPort() {
		return addr.getPort();
	}

	public void setVersion(int version) {
		this.version = version;
	}

	public int getVersion() {
		return version;
	}

	public boolean matches(NodeInfo other) {
		if (other != null)
			return this.id.equals(other.id) || this.addr.equals(other.addr);
		else
			return false;
	}

	@Override
	public int hashCode() {
		return id.hashCode() + 0x6e; // + 'n'
	}

	@Override
	public boolean equals(Object o) {
		if (o instanceof NodeInfo) {
			NodeInfo other = (NodeInfo) o;
			return this.id.equals(other.id) && this.addr.equals(other.addr);
		}

		return false;
	}

	@Override
	public String toString() {
		return "<" + id + "," + addr.getAddress().toString() + "," + addr.getPort() + ">";
	}
}
