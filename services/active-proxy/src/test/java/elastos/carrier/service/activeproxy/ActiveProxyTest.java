package elastos.carrier.service.activeproxy;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.extension.ExtendWith;

import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.Checkpoint;
import io.vertx.junit5.Timeout;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

import elastos.carrier.DefaultConfiguration;
import elastos.carrier.kademlia.Node;
import elastos.carrier.service.DefaultServiceContext;
import elastos.carrier.service.ServiceContext;
import elastos.carrier.utils.AddressUtils;
import elastos.carrier.utils.Hex;

@ExtendWith(VertxExtension.class)
@EnabledIfSystemProperty(named = "elastos.carrier.enviroment", matches = "development")
public class ActiveProxyTest {
	private static final String workingDir = System.getProperty("java.io.tmpdir") + File.separator + "APTest";
	private static final String regularNodeExecutable = "/Users/jingyu/Projects/Elastos/Carrier2/Native/build/apps/launcher/carrier-launcher";
	private static final String caddyExecutable = "/usr/local/bin/caddy";

	private static final Map<String, byte[]> testDigests = new HashMap<>();

	private final static InetAddress localAddr =
			AddressUtils.getAllAddresses().filter(Inet4Address.class::isInstance)
				.filter((a) -> AddressUtils.isAnyUnicast(a))
				.distinct().findFirst().get();

	private static final int regularNodePort = 39006;
	private static final int superNodePort = 39001;
	private static final int activeProxyPort = 8090;
	private static final String upstreamServerHost = "127.0.0.1";
	private static final int upstreamServerPort = 8888;
	private static final int maxConnections = 16;

	private static final String proxyServerPeerKey = "96010b0b04276f8acec13f64abe0451b5543c5d0f5ee24184fa19eabaf42047a28708e83a023fe973675ffb5ffff2434aaf93a46af5926133cee574fd2612b62";
	private static final String proxyServerPeerId = "3irrWb5ZT77HkDDhE7L2bw2oY7BeY5T5SYEX7TyXXP6R";

	private static Node superNode;
	private static ActiveProxy proxyServer;
	private static Process upstreamServerProcess;
	private static Process regularNodeProcess;

	private static void deleteFile(File file) {
		if (file.isDirectory()) {
			File[] children = file.listFiles();
			for (File child : children)
				deleteFile(child);
		}

		file.delete();
	}

	private static void prepareWorkingDirectory() {
		File dir = new File(workingDir);
		if (dir.exists())
			deleteFile(dir);

		dir.mkdirs();
	}

	private static void setupSuperNode() throws Exception {
		File dir = new File(workingDir + File.separator + "super");
		dir.mkdirs();

		elastos.carrier.Configuration config = new DefaultConfiguration.Builder()
				.setIPv4Address(localAddr)
				.setListeningPort(superNodePort)
				.setStoragePath(dir.getAbsolutePath())
				.build();
		superNode = new Node(config);
		superNode.start();

		JsonObject proxyConfig = JsonObject.of(
				"port", activeProxyPort,
				"portMappingRange", "20000-21000",
				"peerPrivateKey", proxyServerPeerKey
			);

		ServiceContext ctx = new DefaultServiceContext(superNode, proxyConfig.getMap());
		proxyServer = new ActiveProxy();
		proxyServer.init(ctx);
		proxyServer.start().get();
	}

	private static byte[] createTestDataFile(File file, int size) throws Exception {
		byte[] buffer = new byte[2014*1024];
		ThreadLocalRandom random = ThreadLocalRandom.current();

		MessageDigest md = MessageDigest.getInstance("SHA-256");
		FileOutputStream o = new FileOutputStream(file);
		while (size > 0) {
			random.nextBytes(buffer);

			int len = size > buffer.length ? buffer.length : size;
			o.write(buffer, 0, len);
			md.update(buffer, 0, len);

			size -= len;
		}
		o.close();

		return md.digest();
	}

	private static File createTestWWWRoot() throws Exception {
		File dir = new File(workingDir + File.separator + "wwwroot");
		dir.mkdirs();

		ThreadLocalRandom random = ThreadLocalRandom.current();

		int shift = 3;
		int lastShift = 0;
		for (int i = 0; i < 16; i++) {
			shift += i <= 10 ? 2 : 1;
			int size = random.nextInt(1 << lastShift, 1 << shift);
			lastShift = shift;

			String filename = "data-" + i + ".bin";
			byte[] hash = createTestDataFile(new File(dir, filename), size);
			testDigests.put(filename, hash);

			System.out.format("Generated test file: %s, size: %d hash: %s\n", filename, size, Hex.encode(hash));
		}

		return dir;
	}

	private static void setupUpstreamServer() throws Exception {
		File wwwroot = createTestWWWRoot();

		upstreamServerProcess = Runtime.getRuntime().exec(caddyExecutable + " file-server --listen " +
				upstreamServerHost + ":" + upstreamServerPort + " --root " + wwwroot.getAbsolutePath());
		upstreamServerProcess.onExit().thenApply(p -> {
			System.out.println("Upstream server[caddy] exited, exit code = " + p.exitValue());
			return null;
		});
	}

	private static File createRegularNodeConfig() throws IOException {
		File dir = new File(workingDir + File.separator + "regular");
		dir.mkdirs();

		JsonObject config = new JsonObject();

		config.put("ipv4", true);
		config.put("ipv6", false);
		config.put("addr4", localAddr.getHostAddress());
		config.put("port", regularNodePort);
		config.put("dataDir", dir.getAbsolutePath());

		config.put("logger", JsonObject.of(
				"level", "debug",
				"logFile", dir.getAbsolutePath() + File.separator + "carrier.log",
				"pattern", "[%Y-%m-%d %T] [%n] %^[%l] %v%$"
				));

		config.put("bootstraps", JsonArray.of(JsonObject.of(
				"id", superNode.getId().toString(),
				"address", localAddr.getHostAddress(),
				"port", superNodePort
				)));

		config.put("addons", JsonArray.of(JsonObject.of(
				"name", "ActiveProxy",
				"configuration", JsonObject.of(
						"serverPeerId", proxyServerPeerId,
						"upstreamHost", upstreamServerHost,
						"upstreamPort", upstreamServerPort,
						"maxConnections", maxConnections
						)
				)));

		File file = new File(dir, "default.conf");
		try (FileWriter o = new FileWriter(file)) {
			o.write(config.encodePrettily());
		}

		return file;
	}

	private static void setupRegularNode() throws Exception {
		File configFile = createRegularNodeConfig();

		regularNodeProcess = Runtime.getRuntime().exec(regularNodeExecutable + " -c " + configFile.getAbsolutePath());
		regularNodeProcess.onExit().thenApply(p -> {
			System.out.println("Regular node[carrier-launcher] exited, exit code = " + p.exitValue());
			return null;
		});
	}

	@BeforeAll
	static void setup() throws Exception {
		prepareWorkingDirectory();
		setupSuperNode();
		setupRegularNode();
		setupUpstreamServer();
		System.out.println("Waiting for everything ready......");
		TimeUnit.SECONDS.sleep(30);
		System.out.println("GO!");
	}

	@AfterAll
	static void tearDown() throws Exception {
		regularNodeProcess.destroyForcibly();
		upstreamServerProcess.destroyForcibly();
		proxyServer.stop().get();
		superNode.stop();
	}

	private static final int MAX_DOWNLOADS = 128;

	@Test
	@Timeout(value = 60, timeUnit = TimeUnit.MINUTES)
	public void testDownload(Vertx vertx, VertxTestContext testContext) throws Exception {
		Checkpoint checkPoint = testContext.checkpoint(MAX_DOWNLOADS);

		HttpClientOptions opts = new HttpClientOptions();
		opts.setDefaultHost("127.0.0.1")
			.setDefaultPort(20000);
		HttpClient client = vertx.createHttpClient(opts);

		Semaphore pending = new Semaphore(maxConnections);

		for (int i = 0; i < MAX_DOWNLOADS; i++) {
			final int idx = i;
			pending.acquire();

			String filename = "data-" + (i % 16) + ".bin";

			client.request(HttpMethod.GET, "/" + filename, req -> {
				if (req.succeeded()) {
					HttpClientRequest request = req.result();
					request.send(res -> {
						if (res.succeeded()) {
							MessageDigest md;
							try {
								md = MessageDigest.getInstance("SHA-256");
							} catch (NoSuchAlgorithmException e) {
								throw new RuntimeException(e);
							}

							HttpClientResponse response = res.result();
							response.handler(buf -> {
								md.update(buf.getBytes());
							}).endHandler(nv -> {
								testContext.verify(() -> {
									byte[] hash = md.digest();
									byte[] expected = testDigests.get(filename);
									System.out.format("%d: Downloading %s ...... %s\n", idx, filename,
											(Arrays.equals(expected, hash) ? "DONE" : "MISMATCH"));
									assertArrayEquals(expected, hash);
									checkPoint.flag();
								});

								pending.release();
							});
						} else {
							System.err.format("%d: Downloading %s ...... error\n", idx, filename);
							res.cause().printStackTrace(System.err);
							fail(res.cause());
							checkPoint.flag();
							pending.release();
						}
					});
				} else {
					System.err.format("%d: Downloading %s ...... error\n", idx, filename);
					req.cause().printStackTrace(System.err);
					fail(req.cause());
					checkPoint.flag();
					pending.release();
				}
			});
		}
	}
}
