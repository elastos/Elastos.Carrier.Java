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
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import elastos.carrier.utils.AddressUtils;

public class DefaultConfiguration implements Configuration {
	private Inet4Address addr4;
	private Inet6Address addr6;
	private int port;
	private File storagePath;
	private List<NodeInfo> bootstrapNodes;

	private DefaultConfiguration(Inet4Address addr4, Inet6Address addr6, int port,
			File storagePath, Collection<NodeInfo> bootstrapNodes) {
		this.addr4 = addr4;
		this.addr6 = addr6;
		this.port = port;
		this.storagePath = storagePath;
		this.bootstrapNodes = Collections.unmodifiableList(
				(bootstrapNodes.isEmpty()) ?
				Collections.emptyList() : new ArrayList<>(bootstrapNodes));
	}

	@Override
	public Inet4Address IPv4Address() {
		return addr4;
	}

	@Override
	public Inet6Address IPv6Address() {
		return addr6;
	}

	@Override
	public int listeningPort() {
		return port;
	}

	@Override
	public File storagePath() {
		return storagePath;
	}

	@Override
	public Collection<NodeInfo> bootstrapNodes() {
		return bootstrapNodes;
	}

	public static class Builder {
		private static final boolean AUTO_IPV4 = true;
		private static final boolean AUTO_IPV6 = false;

		private boolean autoAddr4 = AUTO_IPV4;
		private boolean autoAddr6 = AUTO_IPV6;
		private Inet4Address addr4;
		private Inet6Address addr6;
		private int port;
		private File storagePath;
		private Set<NodeInfo> bootstrapNodes = new HashSet<>();

		public Builder setAutoIPv4Address(boolean auto) {
			autoAddr4 = auto;
			return this;
		}

		public Builder setAutoIPv6Address(boolean auto) {
			autoAddr6 = auto;
			return this;
		}

		public Builder setAutoIPAddress(boolean auto) {
			autoAddr4 = auto;
			autoAddr6 = auto;
			return this;
		}

		public Builder setIPv4Address(String addr) {
			try {
				return setIPv4Address(addr != null ? InetAddress.getByName(addr) : null);
			} catch (IOException | IllegalArgumentException e) {
				throw new IllegalArgumentException("Invalid IPv4 address: " + addr, e);
			}
		}

		public Builder setIPv4Address(InetAddress addr) {
			if (addr != null && !(addr instanceof Inet4Address))
				throw new IllegalArgumentException("Invalid IPv4 address: " + addr);

			addr4 = (Inet4Address)addr;
			return this;
		}

		public Builder setIPv6Address(String addr) {
			try {
				return setIPv6Address(addr != null ? InetAddress.getByName(addr) : null);
			} catch (IOException | IllegalArgumentException e) {
				throw new IllegalArgumentException("Invalid IPv6 address: " + addr, e);
			}
		}

		public Builder setIPv6Address(InetAddress addr) {
			if (addr != null && !(addr instanceof Inet6Address))
				throw new IllegalArgumentException("Invalid IPv6 address: " + addr);

			addr6 = (Inet6Address)addr;
			return this;
		}

		public Builder setListeningPort(int port) {
			if (port <= 0 || port > 65535)
				throw new IllegalArgumentException("Invalid port: " + port);

			this.port = port;
			return this;
		}

		public Builder setStoragePath(String path) {
			storagePath = path != null ?  toFile(path) : null;
			return this;
		}

		public Builder addBootstrap(String id, String addr, int port) {
			NodeInfo node = new NodeInfo(Id.of(id), addr, port);
			bootstrapNodes.add(node);
			return this;
		}

		public Builder addBootstrap(Id id, InetAddress addr, int port) {
			NodeInfo node = new NodeInfo(id, addr, port);
			bootstrapNodes.add(node);
			return this;
		}

		public Builder addBootstrap(Id id, InetSocketAddress addr) {
			NodeInfo node = new NodeInfo(id, addr);
			bootstrapNodes.add(node);
			return this;
		}

		public Builder addBootstrap(NodeInfo node) {
			if (node == null)
				throw new IllegalArgumentException("Invaild node info: null");

			bootstrapNodes.add(node);
			return this;
		}

		public Builder load(String file) throws IOException {
			File configFile = toFile(file);
			if (configFile == null || !configFile.exists() || configFile.isDirectory())
				throw new IllegalArgumentException("Invalid config file: " + String.valueOf(file));

			try (FileInputStream in = new FileInputStream(configFile)) {
				ObjectMapper mapper = new ObjectMapper();
				JsonNode root = mapper.readTree(in);

				boolean enabled = root.has("ipv4") ? root.get("ipv4").asBoolean() : AUTO_IPV4;
				setAutoIPv4Address(enabled);
				if (enabled) {
					if (root.has("address4"))
						setIPv4Address(root.get("address4").asText());
				}

				enabled = root.has("ipv6") ?  root.get("ipv6").asBoolean() : AUTO_IPV6;
				setAutoIPv6Address(enabled);
				if (enabled) {
					if (root.has("address6"))
						setIPv6Address(root.get("address6").asText());
				}

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

						try {
							Id id = Id.of(bootstrap.get("id").asText());
							InetAddress addr = InetAddress.getByName(bootstrap.get("address").asText());
							int port = bootstrap.get("port").asInt();

							NodeInfo node = new NodeInfo(id, addr, port);
							bootstrapNodes.add(node);
						} catch (Exception e) {
							throw new IOException("Config file error: bootstap node - " +
									bootstrap.get("id").asText(), e);
						}

					}
				}
			}

			return this;
		}

		public Builder clear() {
			autoAddr4 = true;
			autoAddr6 = true;
			addr4 = null;
			addr6 = null;
			port = 0;
			storagePath = null;
			bootstrapNodes.clear();

			return this;
		}

		public Configuration build() {
			if (addr4 == null && autoAddr4)
				addr4 = (Inet4Address)AddressUtils.getAllAddresses().filter(Inet4Address.class::isInstance)
						.filter((a) -> !AddressUtils.isLocalUnicast(a))
						.distinct()
						.findFirst().orElse(null);

			if (addr6 == null && autoAddr6)
				addr6 = (Inet6Address)AddressUtils.getAllAddresses().filter(Inet6Address.class::isInstance)
						.filter((a) -> !AddressUtils.isLocalUnicast(a))
						.distinct()
						.findFirst().orElse(null);

			return new DefaultConfiguration(addr4, addr6, port, storagePath, bootstrapNodes);
		}

		private static File toFile(String file) {
			if (file == null || file.isEmpty())
				return null;

			if (file.startsWith("~"))
				return new File(System.getProperty("user.home") + file.substring(1));
			else
				return new File(file);
		}
	}
}