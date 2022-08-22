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

package elastos.carrier.utils;

import static elastos.carrier.utils.Functional.unchecked;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.ProtocolFamily;
import java.net.SocketException;
import java.net.StandardProtocolFamily;
import java.net.UnknownHostException;
import java.nio.channels.DatagramChannel;
import java.util.Arrays;
import java.util.Collections;
import java.util.stream.Stream;

public class AddressUtils {
	private static final NetMask V4_MAPPED;
	// private final static NetMask V4_COMPAT = NetMask.fromString("0000::/96");
	private final static byte[] LOCAL_BROADCAST = new byte[] { (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff };

	private static final boolean devEnv = System.getProperty("elastos.carrier.enviroment", "").equals("development");

	static {
		try {
			// ::ffff:0:0/96
			V4_MAPPED = new NetMask(Inet6Address.getByAddress(null, new byte[] { 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
					0x00, 0x00, 0x00, 0x00, (byte) 0xff, (byte) 0xff, 0x00, 0x00, 0x00, 0x00, }, null), 96);
		} catch (Exception e) {
			throw new RuntimeException("INTERNAL ERROR: should never happen");
		}
	}

	public static class NetMask {
		private byte[] network;
		private int mask;

		public static NetMask fromString(String toParse) {
			String[] parts = toParse.split("/");
			return new NetMask(unchecked(() -> InetAddress.getByName(parts[0])), Integer.valueOf(parts[1]));
		}

		public NetMask(InetAddress network, int mask) {
			this.mask = mask;
			this.network = network.getAddress();
			if (this.network.length * 8 < mask)
				throw new IllegalArgumentException(
						"mask cannot cover more bits than the length of the network address");
		}

		public boolean contains(InetAddress addr) {
			byte[] other = addr.getAddress();
			if (network.length != other.length)
				return false;

			for (int i = 0; i < mask >>> 3; i++) {
				if (network[i] != other[i])
					return false;
			}

			if ((mask & 0x07) == 0)
				return true;

			int offset = (mask >>> 3);
			int probeMask = (0xff00 >> (mask & 0x07)) & 0xff;
			return (network[offset] & probeMask) == (other[offset] & probeMask);
		}
	}

	// https://en.wikipedia.org/wiki/Bogon_filtering
	// https://en.wikipedia.org/wiki/Reserved_IP_addresses
	// https://en.wikipedia.org/wiki/Martian_packet
	public static boolean isBogon(InetSocketAddress addr) {
		return isBogon(addr.getAddress(), addr.getPort());
	}

	public static boolean isBogon(InetAddress addr, int port) {
		return !(port > 0 && port <= 0xFFFF &&
				devEnv ? !isLocalUnicast(addr) : isGlobalUnicast(addr));
	}

	// https://datatracker.ietf.org/doc/html/rfc4380
	public static boolean isTeredo(InetAddress addr) {
		if (!(addr instanceof Inet6Address))
			return false;
		byte[] raw = addr.getAddress();

		// https://datatracker.ietf.org/doc/html/rfc4380#section-2.6
		// prefix 2001:0000:/32
		return raw[0] == 0x20 && raw[1] == 0x01 && raw[2] == 0x00 && raw[3] == 0x00;
	}

	public static boolean isGlobalUnicast(InetAddress addr) {
		// local identification block
		if (addr instanceof Inet4Address && addr.getAddress()[0] == 0)
			return false;
		// this would be rejected by a socket with broadcast disabled anyway, but filter
		// it to reduce exceptions
		if (addr instanceof Inet4Address && java.util.Arrays.equals(addr.getAddress(), LOCAL_BROADCAST))
			return false;
		if (addr instanceof Inet6Address && (addr.getAddress()[0] & 0xfe) == 0xfc) // fc00::/7
			return false;
		if (addr instanceof Inet6Address && (V4_MAPPED.contains(addr) || ((Inet6Address) addr).isIPv4CompatibleAddress()))
			return false;

		return !(addr.isAnyLocalAddress() || addr.isLinkLocalAddress() || addr.isLoopbackAddress()
				|| addr.isMulticastAddress() || addr.isSiteLocalAddress());
	}

	public static boolean isLocalUnicast(InetAddress addr) {
		// local identification block
		if (addr instanceof Inet4Address && addr.getAddress()[0] == 0)
			return true;
		// this would be rejected by a socket with broadcast disabled anyway, but filter
		// it to reduce exceptions
		if (addr instanceof Inet4Address && java.util.Arrays.equals(addr.getAddress(), LOCAL_BROADCAST))
			return true;
		if (addr instanceof Inet6Address && (addr.getAddress()[0] & 0xfe) == 0xfc) // fc00::/7
			return true;
		if (addr instanceof Inet6Address && (V4_MAPPED.contains(addr) || ((Inet6Address) addr).isIPv4CompatibleAddress()))
			return true;

		return addr.isLinkLocalAddress() || addr.isLoopbackAddress() || addr.isMulticastAddress();
	}

	public static InetAddress fromBytesVerbatim(byte[] raw) throws UnknownHostException {
		// bypass ipv4 mapped address conversion
		if(raw.length == 16)
			return Inet6Address.getByAddress(null, raw, null);

		return InetAddress.getByAddress(raw);
	}

	public static Stream<InetAddress> getAllAddresses() {
		try {
			return Collections.list(NetworkInterface.getNetworkInterfaces()).stream().filter(iface -> {
				try {
					return iface.isUp();
				} catch (SocketException e) {
					e.printStackTrace();
					return false;
				}
			}).flatMap(iface -> Collections.list(iface.getInetAddresses()).stream());
		} catch (SocketException e) {
			e.printStackTrace();
			return Stream.empty();
		}
	}

	public static Stream<InetAddress> getNonlocalAddresses() {
		return getAllAddresses().filter(addr -> {
			return !addr.isAnyLocalAddress() && !addr.isLoopbackAddress();
		});
	}

	public static Stream<InetAddress> getAvailableGloballyRoutableAddrs(Stream<InetAddress> toFilter,
			Class<? extends InetAddress> type) {
		return toFilter.filter(type::isInstance).filter(AddressUtils::isGlobalUnicast)
				.sorted((a, b) -> Arrays.compareUnsigned(a.getAddress(), b.getAddress()));
	}

	public static boolean isValidBindAddress(InetAddress addr) {
		// we don't like them them but have to allow them
		if (addr.isAnyLocalAddress())
			return true;
		try {
			NetworkInterface iface = NetworkInterface.getByInetAddress(addr);
			if (iface == null)
				return false;
			return iface.isUp() && !iface.isLoopback();
		} catch (SocketException e) {
			return false;
		}
	}

	public static InetAddress getAnyLocalAddress(Class<? extends InetAddress> type) {
		try {
			if (type == Inet6Address.class)
				return InetAddress.getByAddress(new byte[16]);
			if (type == Inet4Address.class)
				return InetAddress.getByAddress(new byte[4]);
		} catch (UnknownHostException e) {
			e.printStackTrace();
		}

		throw new RuntimeException("INTERNAL ERROR: should never happen");
	}

	public static InetAddress getDefaultRoute(Class<? extends InetAddress> type) {
		InetAddress target = null;

		ProtocolFamily family = type == Inet6Address.class ? StandardProtocolFamily.INET6 : StandardProtocolFamily.INET;

		try (DatagramChannel chan = DatagramChannel.open(family)) {
			if (type == Inet4Address.class)
				target = InetAddress.getByAddress(new byte[] { 8, 8, 8, 8 });
			if (type == Inet6Address.class)
				target = InetAddress.getByName("2001:4860:4860::8888");

			chan.connect(new InetSocketAddress(target, 63));

			InetSocketAddress soa = (InetSocketAddress) chan.getLocalAddress();
			InetAddress local = soa.getAddress();

			if (type.isInstance(local) && !local.isAnyLocalAddress())
				return local;
			return null;
		} catch (IOException e) {
			e.printStackTrace();
			return null;
		}
	}

	public static String toString(InetSocketAddress sockAddr, boolean align) {
		InetAddress addr = sockAddr.getAddress();
		int port = sockAddr.getPort();

		if (align)
			return addr instanceof Inet6Address ?
					String.format("%41s:%-5d", "[" + addr.getHostAddress() + "]", port)
					: String.format("%15s:%-5d", addr.getHostAddress(), port);
		else
			return (addr instanceof Inet6Address ?
					"[" + addr.getHostAddress() + "]" : addr.getHostAddress()) + ":"
					+ port;
	}

	public static String toString(InetSocketAddress sockAddr) {
		return toString(sockAddr, false);
	}
}
