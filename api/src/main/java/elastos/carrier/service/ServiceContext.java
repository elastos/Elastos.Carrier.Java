package elastos.carrier.service;

import java.util.Map;

import elastos.carrier.Id;
import elastos.carrier.Node;

public interface ServiceContext {
	public Node getNode();

	public Id getNodeId();

	public Map<String, String> getConfig();
}
