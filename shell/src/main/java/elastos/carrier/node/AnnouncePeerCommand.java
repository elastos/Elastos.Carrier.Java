/*
 * Copyright (c) 2022 Elastos Foundation
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

package elastos.carrier.node;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;

import elastos.carrier.kademlia.Id;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

@Command(name = "announcepeer", mixinStandardHelpOptions = true, version = "Carrier announcepeer 2.0",
		description = "Announce a service peer.")
public class AnnouncePeerCommand implements Callable<Integer> {
	@Parameters(paramLabel = "ID", index = "0", description = "The service peer id to be announce.")
	private String target;

	@Parameters(paramLabel = "PORT", index = "1", description = "The service port to be announce.")
	private int port = 0;

	@Override
	public Integer call() throws Exception {
		Id id = null;
		try {
			id = new Id(target);
		} catch (Exception e) {
			System.out.println("Invalid ID: " + target);
			return -1;
		}

		if (port <= 0) {
			System.out.println("Invalid port: " + port);
			return -1;
		}

		CompletableFuture<Void> f = Shell.getCarrierNode().announcePeer(id, port);
		f.get();
		System.out.println("Peer " + id + " announced.");

		return 0;
	}
}
