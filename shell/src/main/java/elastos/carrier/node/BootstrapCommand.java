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

import java.net.InetAddress;
import java.util.concurrent.Callable;

import elastos.carrier.Id;
import elastos.carrier.NodeInfo;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

@Command(name = "bootstrap", mixinStandardHelpOptions = true, version = "Carrier bootstrap 2.0",
		description = "Bootstrap from the node.")
public class BootstrapCommand implements Callable<Integer> {
	@Parameters(paramLabel = "ID", index = "0", description = "The node id.")
	private String id = null;

	@Parameters(paramLabel = "ADDRESS", index = "1", description = "The node address.")
	private String address = null;

	@Parameters(paramLabel = "PORT", index = "2", description = "The node port.")
	private int port = 0;

	@Override
	public Integer call() throws Exception {
		Id nodeId = null;
		try {
			nodeId = Id.of(id);
		} catch (Exception e) {
			System.out.println("Invalid id: " + id);
			return -1;
		}

		InetAddress addr = null;
		try {
			addr = InetAddress.getByName(address);
		} catch (Exception e) {
			System.out.println("Invalid address: " + id);
			return -1;
		}

		if (port <= 0) {
			System.out.println("Invalid port: " + port);
			return -1;
		}

		NodeInfo n = new NodeInfo(nodeId, addr, port);
		Shell.getCarrierNode().bootstrap(n);

		return null;
	}
}
