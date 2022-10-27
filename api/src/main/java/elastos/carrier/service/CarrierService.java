package elastos.carrier.service;

import java.util.concurrent.CompletableFuture;

public interface CarrierService {
	public String getName();

	public boolean isRunning();

	public void init(ServiceContext context) throws CarrierServiceException;

	public CompletableFuture<Void> start();

	public CompletableFuture<Void> stop();
}
