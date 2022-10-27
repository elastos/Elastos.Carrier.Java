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

import elastos.carrier.kademlia.Node;

public class Launcher {
	private static Configuration config;
	private static Object shutdown = new Object();

	private static Node carrierNode;

	private static void initCarrierNode() {
		try {
			shutdown = new Object();
			carrierNode = new Node(config);
			carrierNode.addStatusListener((o, n) -> {
				if (n == NodeStatus.Stopped) {
					synchronized(shutdown) {
						shutdown.notifyAll();
					}
				}
			});
			carrierNode.start();
		} catch (Exception e) {
			System.out.println("Start carrier super node failed, error: " + e.getMessage());
			e.printStackTrace(System.err);
			System.exit(-1);
		}
	}

	private static void parseArgs(String[] args) {
		DefaultConfiguration.Builder builder = new DefaultConfiguration.Builder();
		builder.setAutoIPv4Address(true);
		builder.setAutoIPv6Address(false);

		int i = 0;
		while (i < args.length) {
			if (!args[i].startsWith("-")) {
				System.out.format("Unknown arg:%d %s\n", i, args[i]);
				i++;
				continue;
			}

			if (args[i].equalsIgnoreCase("--config") || args[i].equalsIgnoreCase("-c")) {
				if (i + 1 >= args.length) {
					System.out.format("Missing the value for arg:%d %s\n", i, args[i]);
					System.exit(-1);
				}

				String configFile = args[++i];
				try {
					builder.load(configFile);
				} catch (Exception e) {
					System.out.println("Can not load the config file: " + configFile + ", error: " + e.getMessage());
					e.printStackTrace(System.err);
					System.exit(-1);
				}
			} else if (args[i].equals("--address4") || args[i].equals("-4")) {
				if (i + 1 >= args.length) {
					System.out.format("Missing the value for arg:%d %s\n", i, args[i]);
					System.exit(-1);
				}

				builder.setIPv4Address(args[++i]);
			} else if (args[i].equals("--address6") || args[i].equals("-6")) {
				if (i + 1 >= args.length) {
					System.out.format("Missing the value for arg:%d %s\n", i, args[i]);
					System.exit(-1);
				}

				builder.setIPv6Address(args[++i]);
			} else if (args[i].equals("--port") || args[i].equals("-p")) {
				if (i + 1 >= args.length) {
					System.out.format("Missing the value for arg:%d %s\n", i, args[i]);
					System.exit(-1);
				}

				builder.setListeningPort(Integer.valueOf(args[++i]));
			} else if (args[i].equals("--data-dir") || args[i].equals("-d")) {
				if (i + 1 >= args.length) {
					System.out.format("Missing the value for arg:%d %s\n", i, args[i]);
					System.exit(-1);
				}

				builder.setStoragePath(args[++i]);
			} else if (args[i].equals("--help") || args[i].equals("-h")) {
				System.out.println("Usage: launcher [OPTIONS]");
				System.out.println("Available options:");
				System.out.println("  -c, --config <configFile>    The configuration file.");
				System.out.println("  -4, --address4 <addr4>       IPv4 address to listen.");
				System.out.println("  -6, --address6 <addr6>       IPv6 address to listen.");
				System.out.println("  -p, --port <port>            The port to listen.");
				System.out.println("  -h, --help                   Show this help message and exit.");

				System.exit(0);
			}

			i++;
		}

		config = builder.build();
	}

	public static void main(String[] args) {
		parseArgs(args);

		initCarrierNode();

		Runtime.getRuntime().addShutdownHook(new Thread(() -> {
			carrierNode.stop();
		}));

		System.out.println("Carrier node is running!");

		synchronized(shutdown) {
			try {
				shutdown.wait();
				System.out.println("Carrier node stopped.");
			} catch (InterruptedException ignore) {
			}
		}
	}
}
