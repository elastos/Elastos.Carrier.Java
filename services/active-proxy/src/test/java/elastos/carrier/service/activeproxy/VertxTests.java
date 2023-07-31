package elastos.carrier.service.activeproxy;

import org.junit.jupiter.api.Test;

import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpMethod;

public class VertxTests {
	@Test
	public void test() throws Exception {
		Object done = new Object();

		Vertx v = Vertx.vertx();
		HttpClient client = v.createHttpClient();

		client.request(HttpMethod.GET, "cn.bing.com", "/").compose(request -> {
			return request.send().compose(response -> {
				System.out.format("Status: %d\n", response.statusCode());
				return response.body();
			});
		}).onSuccess(body -> {
			System.out.println();
			System.out.println(body.toString());
			synchronized (done) {
				done.notify();
			}
		}).onFailure(t -> {
			t.printStackTrace();
			synchronized (done) {
				done.notify();
			}
		});

		synchronized (done) {
			done.wait();
		}

		v.close();
	}
}
