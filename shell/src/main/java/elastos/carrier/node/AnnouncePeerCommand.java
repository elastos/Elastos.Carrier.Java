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

package elastos.carrier.node;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;

import elastos.carrier.Id;
import elastos.carrier.PeerInfo;
import elastos.carrier.crypto.Signature;
import elastos.carrier.utils.Hex;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(name = "announcepeer", mixinStandardHelpOptions = true, version = "Carrier announcepeer 2.0",
		description = "Announce a service peer.")
public class AnnouncePeerCommand implements Callable<Integer> {
   	@Option(names = {"-k", "--private-key"}, description = "The private key.")
	private String privateKey = null;

   	@Option(names = {"-n", "--node-id"}, description = "The node id.")
	private String nodeId = null;

	@Option(names = {"-a", "--alternative-url"}, description = "The alternative URL.")
	private String alt = null;

	@Parameters(paramLabel = "PORT", index = "0", description = "The peer port to be announce.")
	private int port = 0;

	@Override
	public Integer call() throws Exception {
		Signature.KeyPair keypair = null;
		try {
			if (privateKey != null)
				keypair = Signature.KeyPair.fromPrivateKey(Hex.decode(privateKey));
		} catch (Exception e) {
			System.out.println("Invalid private key: " + privateKey + ", " + e.getMessage());
			return -1;
		}

		Id peerNodeId = Shell.getCarrierNode().getId();
		try {
			if (nodeId != null)
				peerNodeId = Id.of(nodeId);
		} catch (Exception e) {
			System.out.println("Invalid node id: " + nodeId + ", " + e.getMessage());
			return -1;
		}

		if (port <= 0) {
			System.out.println("Invalid port: " + port);
			return -1;
		}

		PeerInfo peer = PeerInfo.create(keypair, peerNodeId, Shell.getCarrierNode().getId(), port, alt);
		CompletableFuture<Void> f = Shell.getCarrierNode().announcePeer(peer);
		f.get();
		System.out.println("Peer " + peer.getId() + " announced with private key " +
				Hex.encode(peer.getPrivateKey()));

		return 0;
	}
}
