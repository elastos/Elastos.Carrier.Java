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

package elastos.carrier.service.activeproxy;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Map;

import io.vertx.core.net.NetServerOptions;

public class Configuration {
	private static final int DEFAULT_PORT = 8090;
	private static final String DEFAULT_PORT_MAPPING_RANGE = "32768-65535";
    public static final int	DEFAULT_HELPER_UPDATE_INTERVAL = 540; // 9 minutes

	private String host;
	private int port;
	private String portMappingRange;

	private String helperServer;
	private int helperPort;
	private boolean helperEnabledSSL;
	private String helperApiKey;
	private int helperUpdateInterval;

	public Configuration(Map<String, Object> config) throws IllegalArgumentException {
		host = (String)config.getOrDefault("host", NetServerOptions.DEFAULT_HOST);
		port = (int)config.getOrDefault("port", DEFAULT_PORT);
		portMappingRange = (String)config.getOrDefault("portMappingRange", DEFAULT_PORT_MAPPING_RANGE);

		String helper = (String)config.get("helper");
		if (helper != null) {
			try {
				URL url = new URL(helper);
				if (url.getProtocol().equals("http"))
					helperEnabledSSL = false;
				else if (url.getProtocol().equals("https"))
					helperEnabledSSL = true;
				else
					throw new IllegalArgumentException("helper service config error");

				helperServer = url.getHost();
				helperPort = url.getPort() > 0 ? url.getPort() : url.getDefaultPort();
			} catch (MalformedURLException e) {
				throw new IllegalArgumentException("helper service config error", e);
			}

			helperApiKey = (String)config.get("helpApiKey");
			helperUpdateInterval = (int)config.getOrDefault("helperUpdateInterval", DEFAULT_HELPER_UPDATE_INTERVAL);
		}
	}

	public String getHost() {
		return host;
	}

	public int getPort() {
		return port;
	}

	public String getPortMappingRange() {
		return portMappingRange;
	}

	public boolean isHelperEnabled() {
		return helperServer != null;
	}

	public String getHelperServer() {
		return helperServer;
	}

	public int getHelperPort() {
		return helperPort;
	}

	public boolean isHelperEnabledSSL() {
		return helperEnabledSSL;
	}

	public String getHelperApiKey() {
		return helperApiKey;
	}

	public int getHelperUpdateInterval() {
		return helperUpdateInterval;
	}
}
