package elastos.carrier.node;

import java.io.File;
import java.util.concurrent.Callable;

import elastos.carrier.kademlia.RoutingTable;
import picocli.CommandLine.ArgGroup;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(name = "displaycache", mixinStandardHelpOptions = true, version = "Carrier displaycache 2.0",
		description = "Display the cached routing table.")
public class DisplayCache implements Callable<Integer> {
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
		RoutingTable routingTable = new RoutingTable(null);
		routingTable.load(cache);

		System.out.println(routingTable);
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
