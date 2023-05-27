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

package elastos.carrier.kademlia;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.util.Comparator;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import elastos.carrier.Configuration;
import elastos.carrier.Id;
import elastos.carrier.NodeInfo;
import elastos.carrier.kademlia.messages.FindNodeRequest;
import elastos.carrier.kademlia.messages.Message;
import elastos.carrier.utils.AddressUtils;

@Disabled("Manual")
public class SybilTests {
	private InetAddress getIPv4Address() {
		return AddressUtils.getAllAddresses()
				.filter(Inet4Address.class::isInstance)
				.filter((a) -> AddressUtils.isAnyUnicast(a))
				.distinct()
				.findFirst()
				.orElse(null);
	}

	private void deleteDir(File dir) throws IOException {
		Files.walk(dir.toPath())
			.sorted(Comparator.reverseOrder())
			.forEach(path -> {
				try {
					Files.delete(path);
				} catch (IOException e) {
					e.printStackTrace();
				}
			});
	}

	@Test
	public void TestAddresses() throws Exception {
		Configuration targetNodeConfig = new Configuration() {
			@Override
			public InetSocketAddress IPv4Address() {
				 InetAddress ip = getIPv4Address();
				 return ip != null ? new InetSocketAddress(ip, 39001) : null;
			}
		};

		Node target = new Node(targetNodeConfig);
		target.start();

		NodeInfo targetInfo = new NodeInfo(target.getId(), targetNodeConfig.IPv4Address());

		Node sybil;
		String tmpDir = System.getProperty("java.io.tmpdir");
		File sybilDir = new File(tmpDir, "sybil");
		if (!sybilDir.exists())
			sybilDir.mkdirs();

		for (int i = 0; i < 36; i++) {
			System.out.format("\n\n======== Testing request #%d ...\n\n", i);

			int port = 39002 + i;
			Configuration sybilConfig = new Configuration() {
				@Override
				public InetSocketAddress IPv4Address() {
					 InetAddress ip = getIPv4Address();
					 return ip != null ? new InetSocketAddress(ip, port) : null;
				}

				@Override
				public File storagePath() {
					return sybilDir;
				}
			};

			sybil = new Node(sybilConfig);
			sybil.start();

			FindNodeRequest fnr = new FindNodeRequest(Id.random(), false);
			RPCCall call = new RPCCall(targetInfo, fnr);

			AtomicBoolean result = new AtomicBoolean(false);
			call.addListener(new RPCCallListener() {
				@Override
				public void onResponse(RPCCall c, Message response) {
					synchronized(result) {
						result.set(true);
						result.notifyAll();
					}
				}
				@Override
				public void onTimeout(RPCCall c) {
					synchronized(result) {
						result.set(false);
						result.notifyAll();
					}
				}
			});

			sybil.getDHT(DHT.Type.IPV4).getServer().sendCall(call);

			synchronized(result) {
				result.wait();
			}

			if (i <= 31)
				assertTrue(result.get());
			else
				assertFalse(result.get());

			sybil.stop();

			TimeUnit.SECONDS.sleep(2);
		}

		target.stop();

		deleteDir(sybilDir);
	}

	@Test
	public void TestIds() throws Exception {
		Configuration targetNodeConfig = new Configuration() {
			@Override
			public InetSocketAddress IPv4Address() {
				 InetAddress ip = getIPv4Address();
				 return ip != null ? new InetSocketAddress(ip, 39001) : null;
			}
		};

		Node target = new Node(targetNodeConfig);
		target.start();

		NodeInfo targetInfo = new NodeInfo(target.getId(), targetNodeConfig.IPv4Address());

		Node sybil;

		for (int i = 0; i < 36; i++) {
			System.out.format("\n\n======== Testing request #%d ...\n\n", i);

			Configuration sybilConfig = new Configuration() {
				@Override
				public InetSocketAddress IPv4Address() {
					 InetAddress ip = getIPv4Address();
					 return ip != null ? new InetSocketAddress(ip, 39002) : null;
				}
			};

			sybil = new Node(sybilConfig);
			sybil.start();

			FindNodeRequest fnr = new FindNodeRequest(Id.random(), false);
			RPCCall call = new RPCCall(targetInfo, fnr);

			AtomicBoolean result = new AtomicBoolean(false);
			call.addListener(new RPCCallListener() {
				@Override
				public void onResponse(RPCCall c, Message response) {
					synchronized(result) {
						result.set(true);
						result.notifyAll();
					}
				}
				@Override
				public void onTimeout(RPCCall c) {
					synchronized(result) {
						result.set(false);
						result.notifyAll();
					}
				}
			});

			sybil.getDHT(DHT.Type.IPV4).getServer().sendCall(call);

			synchronized(result) {
				result.wait();
			}

			if (i <= 31)
				assertTrue(result.get());
			else
				assertFalse(result.get());

			sybil.stop();

			TimeUnit.SECONDS.sleep(2);
		}

		target.stop();
	}
}
