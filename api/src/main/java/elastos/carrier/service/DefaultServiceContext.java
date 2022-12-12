package elastos.carrier.service;

import java.util.HashMap;
import java.util.Map;

import elastos.carrier.Id;
import elastos.carrier.Node;

public class DefaultServiceContext implements ServiceContext {
	private Node node;
	private Map<String, Object> configuration;
	private Map<String, Object> properties;

	public DefaultServiceContext(Node node, Map<String, Object> configuration) {
		this.node = node;
		this.configuration = configuration;
		this.properties = new HashMap<>();
	}

	@Override
	public Node getNode() {
		return node;
	}

	@Override
	public Id getNodeId() {
		return node.getId();
	}

	@Override
	public Map<String, Object> getConfiguration() {
		return configuration;
	}

	@Override
	public Object setProperty(String name, Object value) {
		return properties.put(name, value);
	}

	@Override
	public Object getProperty(String name) {
		return properties.get(name);
	}
}
