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

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;

import elastos.carrier.Id;
import elastos.carrier.LookupOption;
import elastos.carrier.PeerInfo;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(name = "findpeer", mixinStandardHelpOptions = true, version = "Carrier findpeer 2.0",
		description = "Find peer and show the candidate peers if exists.")
public class FindPeerCommand implements Callable<Integer> {
	@Option(names = { "-m", "--mode" }, description = "lookup mode: arbitrary, optimistic, conservative.")
	private String mode = "conservative";

	@Option(names = { "-x", "--expected-count" }, description = "expected number of peers")
	private int expected = -1;

	@Parameters(paramLabel = "ID", index = "0", description = "The peer id to be find.")
	private String id;

	@Override
	public Integer call() throws Exception {
		LookupOption option = null;
		try {
			option = LookupOption.valueOf(mode.toUpperCase());
		} catch (Exception e) {
			System.out.println("Unknown mode: " + mode);
			return -1;
		}

		Id peerId = Id.of(id);
		CompletableFuture<List<PeerInfo>> f = Shell.getCarrierNode().findPeer(peerId, expected, option);
		List<PeerInfo> pl = f.get();
		if (!pl.isEmpty()) {
			for (PeerInfo p : pl)
				System.out.println(p);
		} else {
			System.out.println("Not found.");
		}

		return 0;
	}
}
