package elastos.carrier.service.activeproxy;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.http.HttpMethod;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

import elastos.carrier.utils.Hex;

@ExtendWith(VertxExtension.class)
public class VertxTests {
	@Test
	public void test(Vertx vertx, VertxTestContext testContext) throws Exception {
		HttpClientOptions opts = new HttpClientOptions();
		opts.setDefaultHost("httpd.apache.org")
			.setDefaultPort(443)
			.setSsl(true);

		HttpClient client = vertx.createHttpClient(opts);

		client.request(HttpMethod.GET, "/images/httpd_logo_wide_new.png", req -> {
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
						System.out.format("Status: %d\n", response.statusCode());
						response.handler(buf -> {
							System.out.print(".");
							md.update(buf.getBytes());
						}).endHandler(nv -> {
							testContext.verify(() -> {
								byte[] hash = md.digest();
								assertEquals("4a27be532c2e920f73aba5285a50d689a9d58548bbf80db680ae3f99289749ba", Hex.encode(hash));
								testContext.completeNow();
							});
						});
					}
				});
			}
		});
	}
}
