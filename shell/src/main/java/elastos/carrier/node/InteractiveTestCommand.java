/*
 * Copyright (c) 2023 trinity-tech.io
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

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;

import elastos.carrier.Id;
import elastos.carrier.LookupOption;
import elastos.carrier.Node;
import elastos.carrier.NodeInfo;
import elastos.carrier.PeerInfo;
import elastos.carrier.Value;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(name = "test", mixinStandardHelpOptions = true, version = "Carrier test 2.0",
		description = "Interactive test with native.")
public class InteractiveTestCommand implements Callable<Integer> {
	@Option(names = {"-m", "--mode"}, description = "lookup mode: arbitrary, optimistic, conservative.")
	private String mode = "conservative";

	@Parameters(paramLabel = "ID", index = "0", description = "The target node id of native to be test.")
	private String target;

	@Override
	public Integer call() throws Exception {
		LookupOption option = null;
		try {
			option = LookupOption.valueOf(mode.toUpperCase());
		} catch (Exception e) {
			System.out.println("Unknown mode: " + mode);
			return -1;
		}

		Id nativeId;
		try {
			nativeId = Id.of(target);
		} catch (Exception e) {
			System.out.println("Invalid ID: " + target);
			return -1;
		}

		Node node = Shell.getCarrierNode();

		CompletableFuture<List<NodeInfo>> fn = node.findNode(nativeId, option);
		List<NodeInfo> nl = fn.get();
		if (!nl.isEmpty()) {
			for (NodeInfo n : nl)
				System.out.println(n);
		}
		else {
			throw new RuntimeException("Node not found.");
		}

        //Test values
        byte[] data = "Hello world".getBytes();
		byte[] newData = "Foo bar".getBytes();

        Value value = Value.createValue(data);
        CompletableFuture<Void> fv = node.storeValue(value, true);
		fv.get();
		System.out.println("Value " + value.getId() + " stored.");

		Value signedValue = Value.createSignedValue(data);
		fv = node.storeValue(signedValue, true);
		fv.get();
		System.out.println("SignedValue " + signedValue.getId() + " stored.");

		Id recipientId = nativeId;
        Value encryptedValue = Value.createEncryptedValue(recipientId, data);
		fv = node.storeValue(encryptedValue, true);
		fv.get();
		System.out.println("EncryptedValue " + encryptedValue.getId() + " stored.");

		Node node2;
//		node2 = Shell.createNode(4233);
//		node2.start();
//		node2.bootstrap(nl.get(0));

		node2 = node;

		CompletableFuture<Value> fvalue = node2.findValue(value.getId(), option);
		Value v = fvalue.get();
		if (v != null) {
			System.out.println("Value: " + v + " found.");
			if (!v.isValid()) {
				throw new RuntimeException("Value is invalid.");
			}
		}
		else {
			throw new RuntimeException("Value not found.");
		}

		fvalue = node2.findValue(signedValue.getId(), option);
		v = fvalue.get();
		if (v != null) {
			System.out.println("SignedValue: " + v + " found.");
			if (!v.isValid()) {
				throw new RuntimeException("SignedValue is invalid.");
			}
		}
		else {
			throw new RuntimeException("SignedValue not found.");
		}

		fvalue = node2.findValue(encryptedValue.getId(), option);
		v = fvalue.get();
		if (v != null) {
			System.out.println("EncryptedValue: " + v + " found.");
			if (!v.isValid()) {
				throw new RuntimeException("EncryptedValue is invalid.");
			}
		}
		else {
			throw new RuntimeException("EncryptedValue not found.");
		}


        Value updatevalue = signedValue.update(data);
        fv = Shell.getCarrierNode().storeValue(updatevalue, true);
		fv.get();
		System.out.println("Update value " + updatevalue.getId() + " stored.");

        fvalue = node2.findValue(updatevalue.getId(), option);
		v = fvalue.get();
		if (v != null) {
			System.out.println("Update value: " + v + " found.");
			if (!v.isValid()) {
				throw new RuntimeException("Update value is invalid.");
			}
		}
		else {
			throw new RuntimeException("Update value not found.");
		}


		PeerInfo pi = PeerInfo.create(node.getId(), 4234);
		CompletableFuture<Void> fv2 = node.announcePeer(pi, true);
		fv2.get();
		System.out.println("Peer1 " + pi.getId() + " annouce.");

		Id nodeId = nativeId;
		PeerInfo pi2 = PeerInfo.create(nodeId, node.getId(), 4235);
		fv2 = node.announcePeer(pi2, true);
		fv2.get();
		System.out.println("Peer2 " + pi2.getId() + " annouce.");

		Node node3;
		node3 = node;

		CompletableFuture<List<PeerInfo>> fp = node2.findPeer(pi.getId(), 1, option);
		List<PeerInfo> pl = fp.get();
		if (!nl.isEmpty()) {
			if (!pl.get(0).isValid()) {
				throw new RuntimeException("Peer1 is invalid.");
			}

		}
		else {
			throw new RuntimeException("Peer1 not found.");
		}

		fp = node2.findPeer(pi2.getId(), 1, option);
		pl = fp.get();
		if (!pl.isEmpty()) {
			if (!pl.get(0).isValid()) {
				throw new RuntimeException("Peer2 is invalid.");
			}

		}
		else {
			throw new RuntimeException("Peer2 not found.");
		}


		if (!node2.equals(node))
			node2.stop();

		if (!node3.equals(node))
			node3.stop();

		System.out.println("---- test OK! ---- ");

		return 0;
	}
}
