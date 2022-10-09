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

package elastos.carrier;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import elastos.carrier.kademlia.Configuration;
import elastos.carrier.kademlia.Id;
import elastos.carrier.kademlia.NodeInfo;
import elastos.carrier.utils.AddressUtils;

public class DefaultConfiguration implements Configuration {
	private Inet4Address addr4;
	private Inet6Address addr6;
	private int port;
	private File storagePath;
	private List<NodeInfo> bootstrapNodes = new ArrayList<>();

	@Override
	public Inet4Address IPv4Address() {
		if (addr4 == null)
			addr4 = (Inet4Address)AddressUtils.getAllAddresses().filter(Inet4Address.class::isInstance)
					.filter((a) -> !AddressUtils.isLocalUnicast(a))
					.distinct()
					.findFirst().orElse(null);

		return addr4;
	}

	void setIPv4Address(String addr) {
		if (addr == null)
			return;

		try {
			InetAddress a = InetAddress.getByName(addr);
			if (!(a instanceof Inet4Address))
				throw new IllegalArgumentException("Invalid IPv4 address: " + addr);

			addr4 = (Inet4Address)a;
		} catch (IOException e) {
			throw new IllegalArgumentException("Invalid IPv4 address: " + addr);
		}
	}

	@Override
	public Inet6Address IPv6Address() {
		// Disable IPv6 by default
		/*
		if (addr6 == null)
			addr6 = (Inet6Address)AddressUtils.getAllAddresses().filter(Inet6Address.class::isInstance)
					.filter((a) -> !AddressUtils.isLocalUnicast(a))
					.distinct()
					.findFirst().orElse(null);
		*/
		return addr6;
	}

	void setIPv6Address(String addr) {
		if (addr == null)
			return;

		try {
			InetAddress a = InetAddress.getByName(addr);
			if (!(a instanceof Inet6Address))
				throw new IllegalArgumentException("Invalid IPv6 address: " + addr);

			addr6 = (Inet6Address)a;
		} catch (IOException e) {
			throw new IllegalArgumentException("Invalid IPv6 address: " + addr);
		}
	}

	@Override
	public int listeningPort() {
		return port;
	}

	void setListeningPort(int port) {
		if (port <= 0 || port >= 65535)
			return;

		this.port = port;
	}

	@Override
	public File storagePath() {
		if (storagePath == null)
			storagePath = toFile("~/.cache/carrier");

		return storagePath;
	}

	void setStoragePath(String path) {
		if (path == null)
			return;

		storagePath = toFile(path);
	}

	@Override
	public List<NodeInfo> bootstrapNodes() {
		return bootstrapNodes;
	}

	protected static File toFile(String file) {
		if (file == null || file.isEmpty())
			return null;

		if (file.startsWith("~"))
			return new File(System.getProperty("user.home") + file.substring(1));
		else
			return new File(file);
	}

	public void load(String file) throws IOException {
		File config = toFile(file);

		try (FileInputStream in = new FileInputStream(config)) {
			ObjectMapper mapper = new ObjectMapper();
			JsonNode root = mapper.readTree(in);

			if (root.has("address4"))
				setIPv4Address(root.get("address4").asText());

			if (root.has("address6"))
				setIPv6Address(root.get("address6").asText());

			if (root.has("port"))
				setListeningPort(root.get("port").asInt());

			if (root.has("dataDir"))
				setStoragePath(root.get("dataDir").asText());

			if (root.has("bootstraps")) {
				JsonNode bootstraps = root.get("bootstraps");
				if (!bootstraps.isArray())
					throw new IOException("Config file error: bootstaps");

				for (JsonNode bootstrap : bootstraps) {
					if (!bootstrap.has("id"))
						throw new IOException("Config file error: bootstap node id");

					if (!bootstrap.has("address"))
						throw new IOException("Config file error: bootstap node address");

					if (!bootstrap.has("port"))
						throw new IOException("Config file error: bootstap node port");

					Id id = null;
					try {
						id = new Id(bootstrap.get("id").asText());
					} catch (Exception e) {
						throw new IOException("Config file error: bootstap node id", e);
					}

					InetAddress addr = InetAddress.getByName(root.get("address").asText());
					int port = root.get("port").asInt();

					NodeInfo node = new NodeInfo(id, addr, port);
					bootstrapNodes.add(node);
				}
			}
		}
	}
}
