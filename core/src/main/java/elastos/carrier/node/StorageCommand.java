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

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import elastos.carrier.kademlia.DataStorage;
import elastos.carrier.kademlia.Id;
import elastos.carrier.kademlia.PeerInfo;
import elastos.carrier.kademlia.Value;
import elastos.carrier.node.StorageCommand.ListPeerCommand;
import elastos.carrier.node.StorageCommand.ListValueCommand;
import elastos.carrier.node.StorageCommand.PeerCommand;
import elastos.carrier.node.StorageCommand.ValueCommand;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

@Command(name = "storage", mixinStandardHelpOptions = true, version = "Carrier storage 2.0",
	description = "Show the local data storage.", subcommands = {
			ListValueCommand.class,
			ValueCommand.class,
			ListPeerCommand.class,
			PeerCommand.class
		})
public class StorageCommand {
	@Command(name = "listvalue", mixinStandardHelpOptions = true, version = "Carrier listvalue 2.0",
			description = "List values from the local storage.")
	public static class ListValueCommand implements Callable<Integer> {

		@Override
		public Integer call() throws Exception {
			DataStorage storage = Launcher.getCarrierNode().getStorage();

			Stream<Id> ids = storage.valueIdStream();
			AtomicInteger n = new AtomicInteger(0);
			ids.forEach(id -> {
				System.out.println(id);
				n.incrementAndGet();
			});

			System.out.println("Total " + n.get() + " values.");
			return 0;
		}
	}

	@Command(name = "value", mixinStandardHelpOptions = true, version = "Carrier value 2.0",
			description = "Display value from the local storage.")
	public static class ValueCommand implements Callable<Integer> {
		@Parameters(paramLabel = "ID", index = "0", description = "The value id.")
		private String id = null;

		@Override
		public Integer call() throws Exception {
			Id valueId = null;
			try {
				valueId = new Id(id);
			} catch (Exception e) {
				System.out.println("Invalid id: " + id);
				return -1;
			}

			DataStorage storage = Launcher.getCarrierNode().getStorage();
			Value value = storage.getValue(valueId);
			if (value != null)
				System.out.println(value);
			else
				System.out.println("Value " + id + " not exists.");

			return 0;
		}
	}

	@Command(name = "listpeer", mixinStandardHelpOptions = true, version = "Carrier listpeer 2.0",
			description = "List peers from the local storage.")
	public static class ListPeerCommand implements Callable<Integer> {

		@Override
		public Integer call() throws Exception {
			DataStorage storage = Launcher.getCarrierNode().getStorage();

			Stream<Id> ids = storage.peerIdStream();
			AtomicInteger n = new AtomicInteger(0);
			ids.forEach(id -> {
				System.out.println(id);
				n.incrementAndGet();
			});

			System.out.println("Total " + n.get() + " peers.");
			return 0;
		}
	}

	@Command(name = "peer", mixinStandardHelpOptions = true, version = "Carrier peer 2.0",
			description = "Display peer info from the local storage.")
	public static class PeerCommand implements Callable<Integer> {
		@Parameters(paramLabel = "ID", index = "0", description = "The peer id.")
		private String id = null;

		@Override
		public Integer call() throws Exception {
			Id peerId = null;
			try {
				peerId = new Id(id);
			} catch (Exception e) {
				System.out.println("Invalid id: " + id);
				return -1;
			}

			DataStorage storage = Launcher.getCarrierNode().getStorage();
			List<PeerInfo> peers = storage.getPeers(peerId, true, true, -1);
			if (!peers.isEmpty()) {
				for (PeerInfo peer : peers)
					System.out.println(peer);
				System.out.println("Total " + peers.size() + " peers.");
			} else {
				System.out.println("Peer " + id + " not exists.");
			}

			return 0;
		}
	}
}
