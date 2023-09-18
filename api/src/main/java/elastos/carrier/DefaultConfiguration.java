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

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import elastos.carrier.utils.AddressUtils;

public class DefaultConfiguration implements Configuration {
	private static final int DEFAULT_DHT_PORT = 39001;

	InetSocketAddress addr4;
	InetSocketAddress addr6;

	private File storagePath;
	private Set<NodeInfo> bootstraps;
	private Map<String, Map<String, Object>> services;

	private DefaultConfiguration() {
		this.bootstraps = new HashSet<>();
		this.services = new LinkedHashMap<>();
	}

	@Override
	public InetSocketAddress IPv4Address() {
		return addr4;
	}

	@Override
	public InetSocketAddress IPv6Address() {
		return addr6;
	}

	@Override
	public File storagePath() {
		return storagePath;
	}

	@Override
	public Collection<NodeInfo> bootstrapNodes() {
		return bootstraps;
	}

	@Override
	public Map<String, Map<String, Object>> services() {
		return services;
	}

	public static class Builder {
		private static final boolean AUTO_IPV4 = true;
		private static final boolean AUTO_IPV6 = false;

		private boolean autoAddr4 = AUTO_IPV4;
		private boolean autoAddr6 = AUTO_IPV6;

		private Inet4Address inetAddr4;
		private Inet6Address inetAddr6;
		private int port = DEFAULT_DHT_PORT;

		private DefaultConfiguration conf;

		private DefaultConfiguration getConfiguration() {
			if (conf == null)
				conf = new DefaultConfiguration();

			return conf;
		}

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

			this.inetAddr4 = (Inet4Address)addr;
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

			this.inetAddr6 = (Inet6Address)addr;
			return this;
		}

		public Builder setListeningPort(int port) {
			if (port <= 0 || port > 65535)
				throw new IllegalArgumentException("Invalid port: " + port);

			this.port = port;
			return this;
		}

		public boolean hasStoragePath() {
			return getConfiguration().storagePath != null;
		}

		public Builder setStoragePath(String path) {
			getConfiguration().storagePath = path != null ?  toFile(path) : null;
			return this;
		}

		public Builder addBootstrap(String id, String addr, int port) {
			NodeInfo node = new NodeInfo(Id.of(id), addr, port);
			getConfiguration().bootstraps.add(node);
			return this;
		}

		public Builder addBootstrap(Id id, InetAddress addr, int port) {
			NodeInfo node = new NodeInfo(id, addr, port);
			getConfiguration().bootstraps.add(node);
			return this;
		}

		public Builder addBootstrap(Id id, InetSocketAddress addr) {
			NodeInfo node = new NodeInfo(id, addr);
			getConfiguration().bootstraps.add(node);
			return this;
		}

		public Builder addBootstrap(NodeInfo node) {
			if (node == null)
				throw new IllegalArgumentException("Invaild node info: null");

			getConfiguration().bootstraps.add(node);
			return this;
		}

		public Builder addBootstrap(Collection<NodeInfo> nodes) {
			if (nodes == null)
				throw new IllegalArgumentException("Invaild node info: null");

			getConfiguration().bootstraps.addAll(nodes);
			return this;
		}

		public Builder addService(String clazz, Map<String, Object> configuration) {
			if (clazz == null || clazz.isEmpty())
				throw new IllegalArgumentException("Invaild service class name");

			getConfiguration().services.put(clazz, Collections.unmodifiableMap(
					configuration == null || configuration.isEmpty() ?
					Collections.emptyMap() : configuration));
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

							addBootstrap(id, addr, port);
						} catch (Exception e) {
							throw new IOException("Config file error: bootstap node - " +
									bootstrap.get("id").asText(), e);
						}
					}
				}

				if (root.has("services")) {
					JsonNode services = root.get("services");
					if (!services.isArray())
						throw new IOException("Config file error: services");

					for (JsonNode service : services) {
						if (!service.has("class"))
							throw new IOException("Config file error: service class name");

						String clazz = service.get("class").asText();

						Map<String, Object> configuration = null;
						if (service.has("configuration")) {
							JsonNode config = service.get("configuration");
							configuration = mapper.convertValue(config, new TypeReference<Map<String, Object>>(){});
						}

						addService(clazz, configuration);
					}
				}
			}

			return this;
		}

		public Builder clear() {
			autoAddr4 = AUTO_IPV4;
			autoAddr6 = AUTO_IPV6;

			inetAddr4 = null;
			inetAddr6 = null;
			port = DEFAULT_DHT_PORT;

			conf = null;

			return this;
		}

		public Configuration build() {
			DefaultConfiguration c = getConfiguration();
			conf = null;

			if (inetAddr4 == null && autoAddr4)
				inetAddr4 = (Inet4Address)AddressUtils.getAllAddresses().filter(Inet4Address.class::isInstance)
						.filter((a) -> AddressUtils.isAnyUnicast(a))
						.distinct()
						.findFirst().orElse(null);

			if (inetAddr6 == null && autoAddr6)
				inetAddr6 = (Inet6Address)AddressUtils.getAllAddresses().filter(Inet6Address.class::isInstance)
						.filter((a) -> AddressUtils.isAnyUnicast(a))
						.distinct()
						.findFirst().orElse(null);

			c.addr4 = inetAddr4 != null ? new InetSocketAddress(inetAddr4, port) : null;
			c.addr6 = inetAddr6 != null ? new InetSocketAddress(inetAddr6, port) : null;

			c.bootstraps = Collections.unmodifiableSet(
					(c.bootstraps == null || c.bootstraps.isEmpty()) ?
					Collections.emptySet() : c.bootstraps);

			c.services = Collections.unmodifiableMap(
					(c.services == null || c.services.isEmpty()) ?
					Collections.emptyMap() : c.services);

			return c;
		}

		private static File toFile(String file) {
			if (file == null || file.isEmpty())
				return null;

			return file.startsWith("~") ?
				new File(System.getProperty("user.home") + file.substring(1)) :
			    new File(file);
		}
	}
}