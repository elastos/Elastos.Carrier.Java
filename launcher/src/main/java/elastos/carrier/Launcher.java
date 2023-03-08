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

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import elastos.carrier.kademlia.Node;
import elastos.carrier.service.CarrierService;
import elastos.carrier.service.CarrierServiceException;
import elastos.carrier.service.DefaultServiceContext;
import elastos.carrier.service.ServiceContext;

public class Launcher {
	private static Configuration config;
	private static Object shutdown = new Object();

	private static Node node;
	private static List<CarrierService> services;

	private static void initCarrierNode() {
		try {
			shutdown = new Object();
			node = new Node(config);
			node.addStatusListener((o, n) -> {
				if (n == NodeStatus.Stopped) {
					synchronized(shutdown) {
						shutdown.notifyAll();
					}
				}
			});
			node.start();

			System.out.format("Carrier node %s is running.\n", node.getId());
		} catch (Exception e) {
			System.out.println("Start carrier super node failed, error: " + e.getMessage());
			e.printStackTrace(System.err);
			System.exit(-1);
		}
	}

	private static void loadServices() {
		if (config.services().isEmpty())
			return;

		services = new ArrayList<>(config.services().size());

		config.services().forEach(Launcher::loadService);
	}

	private static void loadService(String className, Map<String, Object> configuration) {
		try {
			Class<?> clazz = Class.forName(className);
			Object o = clazz.getDeclaredConstructor().newInstance();
			if (!(o instanceof CarrierService)) {
				System.out.println("Class isn't a carrier service: " + className);
				return;
			}

			CarrierService svc = (CarrierService)o;
			ServiceContext ctx = new DefaultServiceContext(node, configuration);
			svc.init(ctx);
			System.out.format("Service %s[%s] is loaded.\n", svc.getName(), className);

			svc.start().get();
			System.out.format("Service %s[%s] is started.\n", svc.getName(), className);

			services.add(svc);

		} catch (ClassNotFoundException | InstantiationException | IllegalAccessException | IllegalArgumentException | InvocationTargetException | NoSuchMethodException | SecurityException e) {
			System.out.println("Can not load service: " + className);
			e.printStackTrace(System.err);
		} catch (CarrierServiceException e) {
			System.out.println("Failed to initialize service: " + className);
			e.printStackTrace(System.err);
		} catch (InterruptedException | ExecutionException e) {
			System.out.println("Failed to start service: " + className);
			e.printStackTrace(System.err);
		}
	}

	private static void unloadServices() {
		for (CarrierService svc : services) {
			try {
				svc.stop().get();
			} catch (InterruptedException | ExecutionException e) {
				System.out.println("Failed to stop service: " + svc.getName());
				e.printStackTrace(System.err);
			}
		}
	}

	private static void parseArgs(String[] args) {
		DefaultConfiguration.Builder builder = new DefaultConfiguration.Builder();

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
		Runtime.getRuntime().addShutdownHook(new Thread(() -> {
			if (node != null) {
				unloadServices();
				node.stop();
				node = null;
			}
		}));

		parseArgs(args);
		initCarrierNode();
		loadServices();

		synchronized(shutdown) {
			try {
				shutdown.wait();
				System.out.println("Carrier node stopped.");
			} catch (InterruptedException ignore) {
			}
		}
	}
}
