package elastos.carrier.service.dhtproxy;

import java.util.concurrent.CompletableFuture;

import io.vertx.core.Vertx;

import elastos.carrier.Node;
import elastos.carrier.service.CarrierService;
import elastos.carrier.service.CarrierServiceException;
import elastos.carrier.service.ServiceContext;

public class DHTProxy implements CarrierService {
	private static final String NAME = "DHT Proxy";
	private static final int DEFAULT_PORT = 10080;

	private ServiceContext context;
	private ProxyServer server;
	private Vertx vertx;
	private String deploymentId;

	@Override
	public String getName() {
		return NAME;
	}

	@Override
	public boolean isRunning() {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public void init(ServiceContext context) throws CarrierServiceException {
		Node node = context.getNode();
		int port = (int)context.getConfiguration().getOrDefault("port", 0);
		if (port == 0)
			port = DEFAULT_PORT;

		server = new ProxyServer(node, port);

		this.context = context;
		this.vertx = Vertx.vertx();
	}

	@Override
	public CompletableFuture<Void> start() {
		CompletableFuture<Void> cf = new CompletableFuture<>();

		vertx.deployVerticle(server).onComplete(ar -> {
			if (ar.succeeded()) {
				deploymentId = ar.result();
				cf.complete(null);
			} else {
				cf.completeExceptionally(new CarrierServiceException("Can not start service: " + NAME, ar.cause()));
			}
		});

		return cf;
	}

	@Override
	public CompletableFuture<Void> stop() {
		if (deploymentId == null)
			return CompletableFuture.failedFuture(new CarrierServiceException("Service not started: " + NAME));

		CompletableFuture<Void> cf = new CompletableFuture<>();
		vertx.undeploy(deploymentId).onComplete(ar -> {
			if (ar.succeeded()) {
				deploymentId = null;
				cf.complete(null);
			} else {
				cf.completeExceptionally(new CarrierServiceException("Can not stop service: " + NAME, ar.cause()));
			}
		});

		return cf;
	}
}
