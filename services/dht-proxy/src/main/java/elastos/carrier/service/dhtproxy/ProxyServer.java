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

package elastos.carrier.service.dhtproxy;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;

import elastos.carrier.Id;
import elastos.carrier.LookupOption;
import elastos.carrier.Node;
import elastos.carrier.NodeInfo;
import elastos.carrier.PeerInfo;
import elastos.carrier.Value;
import elastos.carrier.utils.Hex;

public class ProxyServer extends AbstractVerticle {
	private int port;
	private Node node;

	private HttpServer server;

	private static final Logger log = LoggerFactory.getLogger(ProxyServer.class);

	public ProxyServer(Node node, int port) {
		this.node = node;
		this.port = port;
	}

	@Override
	public void start() throws Exception {
		Router router = Router.router(vertx);

		router.errorHandler(500, ctx -> {
			StringWriter sw = new StringWriter();
			PrintWriter pw = new PrintWriter(sw);
			ctx.failure().printStackTrace(pw);
			ctx.response().end(sw.toString());
		});

		router.get("/id")
			.handler(this::getId)
			.failureHandler(this::failureHandler);
		router.get("/nodes/:id")
			.handler(this::findNode)
			.failureHandler(this::failureHandler);
		router.get("/values/:id")
			.handler(this::findValue)
			.failureHandler(this::failureHandler);
		router.post("/values")
			.handler(BodyHandler.create())
			.handler(this::storeValue)
			.failureHandler(this::failureHandler);
		router.get("/peers/:id")
			.handler(this::findPeer)
			.failureHandler(this::failureHandler);

		server = vertx.createHttpServer()
			.requestHandler(router)
			.exceptionHandler((e) -> {
				log.error("HTTP(s) connection error: " + e.getMessage(), e);
			})
			.listen(port, asyncResult -> {
				if (asyncResult.succeeded()) {
					log.info("HTTP server started on port {}", port);
				} else {
					log.error("HTTP Server listen failed on {} - {}", port, asyncResult.cause());
					//throw new CarrierServiceException(asyncResult.cause());
				}
			});
	}

	@Override
	public void stop() throws Exception {
		server.close(asyncResult -> log.info("HTTP Server stopped"));
	}

	private void failureHandler(RoutingContext ctx) {
		int statusCode = ctx.statusCode();
		Throwable error = ctx.failure();

		String msg = error != null ? error.getMessage() : "Unknown error";
		ctx.response().setStatusCode(statusCode).end(msg);
	}

	private void getId(RoutingContext ctx) {
		ctx.response().end(JsonObject.of("id", node.getId().toString()).toBuffer());
	}

	private void findNode(RoutingContext ctx) {
		try {
			Id nodeId = Id.of(ctx.pathParam("id"));

			String mode = ctx.request().params().get("mode");
			LookupOption option = mode != null ? LookupOption.valueOf(mode.toUpperCase()) : null;

			node.findNode(nodeId, option).whenComplete((nl, e) -> {
				if (e != null) {
					vertx.runOnContext((none) -> ctx.fail(500, e));
					return;
				}

				if (nl.isEmpty()) {
					vertx.runOnContext((none) -> ctx.response().setStatusCode(404).end());
				} else {
					vertx.runOnContext((none) -> {
						HttpServerResponse response = ctx.response();
						response.putHeader("content-type", "application/json");
						response.end(nodeToJson(nl).toBuffer());
					});
				}
			});
		} catch (IllegalArgumentException e) {
			ctx.fail(400, e);
		}
	}

	private void findValue(RoutingContext ctx) {
		try {
			Id valueId = Id.of(ctx.pathParam("id"));

			String mode = ctx.request().params().get("mode");
			LookupOption option = mode != null ? LookupOption.valueOf(mode.toUpperCase()) : null;

			node.findValue(valueId, option).whenComplete((v, e) -> {
				if (e != null) {
					vertx.runOnContext((none) -> ctx.fail(500, e));
					return;
				}

				if (v == null) {
					vertx.runOnContext((none) -> ctx.response().setStatusCode(404).end());
				} else {
					vertx.runOnContext((none) -> {
						HttpServerResponse response = ctx.response();
						response.putHeader("content-type", "application/json");
						response.end(valueToJson(v).toBuffer());
					});
				}
			});
		} catch (IllegalArgumentException e) {
			ctx.fail(400, e);
		}
	}

	private void storeValue(RoutingContext ctx) {
		try {
			Value value = valueFromJson(ctx.body().asJsonObject());
			node.storeValue(value).whenComplete((onone, e) -> {
				if (e != null) {
					vertx.runOnContext((none) -> ctx.fail(500, e));
					return;
				}

				vertx.runOnContext((none) -> {
					HttpServerResponse response = ctx.response();
					response.setStatusCode(202);
					response.putHeader("content-type", "application/json");
					response.end(JsonObject.of("id", value.getId().toString()).toBuffer());
				});
			});
		} catch (IllegalArgumentException e) {
			ctx.fail(400, e);
		}
	}

	private void findPeer(RoutingContext ctx) {
		try {
			Id peerId = Id.of(ctx.pathParam("id"));

			String mode = ctx.request().params().get("mode");
			LookupOption option = mode != null ? LookupOption.valueOf(mode.toUpperCase()) : null;

			String exp = ctx.request().params().get("expected");
			int expected = exp != null ? Integer.valueOf(exp) : -1;

			node.findPeer(peerId, expected, option).whenComplete((pl, e) -> {
				if (e != null) {
					vertx.runOnContext((none) -> ctx.fail(500, e));
					return;
				}

				if (pl.isEmpty()) {
					vertx.runOnContext((none) -> ctx.response().setStatusCode(404).end());
				} else {
					vertx.runOnContext((none) -> {
						HttpServerResponse response = ctx.response();
						response.putHeader("content-type", "application/json");
						response.end(peerToJson(pl).toBuffer());
					});
				}
			});
		} catch (IllegalArgumentException e) {
			ctx.fail(400, e);
		}
	}

	private JsonArray nodeToJson(List<NodeInfo> nodes) {
		JsonArray array = new JsonArray();
		for (NodeInfo node : nodes)
			array.add(JsonObject.of(
				"id", node.getId().toString(),
				"ip", node.getInetAddress().toString(),
				"port", node.getPort()
			));

		return array;
	}

	private JsonArray peerToJson(List<PeerInfo> peers) {
		JsonArray array = new JsonArray();
		for (PeerInfo peer : peers)
			array.add(JsonObject.of(
				"nodeId", peer.getNodeId().toString(),
				"proxyId", peer.getProxyId().toString(),
				"port", peer.getPort(),
				"alt", peer.getAlt(),
				"signature", peer.getSignature()
			));

		return array;
	}

	private JsonObject valueToJson(Value value) {
		JsonObject object = new JsonObject();

		if (value.isMutable()) {
			object.put("pk", value.getPublicKey().toString());
			if (value.getRecipient() != null)
				object.put("rec", value.getRecipient().toString());
			object.put("nonce", Hex.encode(value.getNonce()));
			object.put("seq", value.getSequenceNumber());
			object.put("sig", Hex.encode(value.getSignature()));
		}
		object.put("data", value.getData());

		return object;
	}

	private Value valueFromJson(JsonObject object) {
		String v = object.getString("pk");
		Id pk = v != null ? Id.of(v) : null;

		Id rec = null;
		byte[] nonce = null;
		int seq = 0;
		byte[] sig = null;

		if (pk != null) {
			v = object.getString("rec");
			if (v != null)
				rec = Id.of(v);

			v = object.getString("nonce");
			if (v != null)
				nonce = Hex.decode(v);

			seq = object.getInteger("seq", 0);

			v = object.getString("sig");
			if (v != null)
				sig = Hex.decode(v);
		}

		byte[] data = object.getBinary("data");

		Value value = Value.of(pk, rec, nonce, seq, sig, data);
		if (!value.isValid())
			throw new IllegalArgumentException("Invalid json for value");

		return value;
	}
}
