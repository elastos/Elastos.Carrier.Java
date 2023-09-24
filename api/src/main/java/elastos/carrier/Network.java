package elastos.carrier;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ProtocolFamily;
import java.net.StandardProtocolFamily;

public enum Network {
	IPv4(StandardProtocolFamily.INET, Inet4Address.class, 20 + 8, 1450),
	IPv6(StandardProtocolFamily.INET6, Inet6Address.class, 40 + 8, 1200);

	private final ProtocolFamily protocolFamily;
	private final Class<? extends InetAddress> preferredAddressType;
	private final int protocolHeaderSize;
	private final int maxPacketSize;

	private Network(ProtocolFamily family, Class<? extends InetAddress> addresstype, int headerSize, int maxPacketSize) {
		this.protocolFamily = family;
		this.preferredAddressType = addresstype;
		this.protocolHeaderSize = headerSize;
		this.maxPacketSize = maxPacketSize;
	}

	public boolean canUseSocketAddress(InetSocketAddress addr) {
		return canUseAddress(addr.getAddress());
	}

	public boolean canUseAddress(InetAddress addr) {
		return preferredAddressType.isInstance(addr);
	}

	public static Network of(InetSocketAddress addr) {
		return (addr.getAddress() instanceof Inet4Address) ? IPv4 : IPv6;
	}

	ProtocolFamily protocolFamily() {
		return protocolFamily;
	}

	public int protocolHeaderSize() {
		return protocolHeaderSize;
	}

	public int maxPacketSize() {
		return maxPacketSize;
	}

	@Override
	public String toString() {
		return name();
	}
}
