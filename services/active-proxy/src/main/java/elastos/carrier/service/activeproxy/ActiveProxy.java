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

package elastos.carrier.service.activeproxy;

import java.util.concurrent.CompletableFuture;

import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;

import elastos.carrier.service.CarrierService;
import elastos.carrier.service.CarrierServiceException;
import elastos.carrier.service.ServiceContext;

public class ActiveProxy implements CarrierService {
	static final String NAME = "ActiveProxy";
	static final int DEFAULT_PORT = 8090;
	static final String DEFAULT_PORT_MAPPING_RANGE = "32768-65535";
	
    static final String HELPER_SERVER_DOMAIN = "api.pc2.net";
    static final int HELPER_SERVER_PORT = 443;
    public static final int	HELPER_VERIFY_INTERVAL = 200;  //200 seconds

	@SuppressWarnings("unused")
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
		return deploymentId != null;
	}

	@Override
	public void init(ServiceContext context) throws CarrierServiceException {
		server = new ProxyServer(context);

		this.context = context;

		VertxOptions options = new VertxOptions();
		// options.setBlockedThreadCheckIntervalUnit(TimeUnit.SECONDS);
		// options.setBlockedThreadCheckInterval(300);
		this.vertx = Vertx.vertx(options);
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
