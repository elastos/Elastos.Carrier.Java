package elastos.carrier.node;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.Callable;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.dataformat.cbor.databind.CBORMapper;

import elastos.carrier.kademlia.KBucketEntry;
import elastos.carrier.utils.ThreadLocals;
import picocli.CommandLine.ArgGroup;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(name = "displaycache", mixinStandardHelpOptions = true, version = "Carrier displaycache 2.0",
		description = "Display the cached routing table.")
public class DisplayCacheCommnand implements Callable<Integer> {
	@ArgGroup(exclusive = true, multiplicity = "0..1")
    AddressFamily af;

    static class AddressFamily {
    	@Option(names = {"-4", "--ipv4-only"}, description = "Diaplay the routing table for IPv4 only.")
    	private boolean ipv4Only = false;

    	@Option(names = {"-6", "--ipv6-only"}, description = "Diaplay the routing table for IPv6 only.")
    	private boolean ipv6Only = false;
    }

	@Parameters(paramLabel = "CACHE_LOCATION", index = "0", defaultValue = ".",
			description = "The carrier cache location, default current directory.")
	private String cachePath = null;

	private void print(File cache) {
		CBORMapper mapper = new CBORMapper(ThreadLocals.CBORFactory());
		long now = System.currentTimeMillis();
		try {
			JsonNode node = mapper.readTree(cache);
			long timestamp = node.get("timestamp").asLong();
			System.out.format("Timestamp: %s / %s\n",
					new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSSZ").format(new Date(timestamp)),
					Duration.ofMillis(now - timestamp));

			JsonNode nodes = node.get("entries");
			System.out.println("Entries: " + nodes.size());
			for (JsonNode n : nodes) {
				Map<String, Object> map = mapper.convertValue(n, new TypeReference<Map<String, Object>>() {});
				KBucketEntry entry = KBucketEntry.fromMap(map);
				System.out.println("    " + entry);
			}

			nodes = node.get("cache");
			System.out.println("Cached: " + nodes.size());
			for (JsonNode n : nodes) {
				Map<String, Object> map = mapper.convertValue(n, new TypeReference<Map<String, Object>>() {});
				KBucketEntry entry = KBucketEntry.fromMap(map);
				System.out.println("    " + entry);
			}
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	@Override
	public Integer call() throws Exception {
		File cacheLocation = cachePath.startsWith("~") ?
				new File(System.getProperty("user.home") + cachePath.substring(1)) :
				new File(cachePath);

		if (af == null || !af.ipv6Only) {
			File cache4 = new File(cacheLocation, "dht4.cache");
			if (cache4.exists() && !cache4.isDirectory()) {
				System.out.println("IPv4 routing table:");
				print(cache4);
			}
		}

		if (af == null || !af.ipv4Only) {
			File cache6 = new File(cacheLocation, "dht6.cache");
			if (cache6.exists() && !cache6.isDirectory()) {
				System.out.println("IPv6 routing table:");
				print(cache6);
			}
		}

		return 0;
	}
}
