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
import elastos.carrier.kademlia.LookupOption;
import elastos.carrier.kademlia.Value;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(name = "findvalue", mixinStandardHelpOptions = true, version = "Carrier findvalue 2.0",
		description = "Find value and show the value if exists.")
public class FindValueCommand implements Callable<Integer> {
@Option(names = {"-m", "--mode"}, description = "lookup mode: arbitrary, optimistic, conservative.")
private String mode = "conservative";

@Parameters(paramLabel = "ID", index = "0", description = "The target value id to be find.")
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

	Id id = null;
	try {
		id = new Id(target);
	} catch (Exception e) {
		System.out.println("Invalid ID: " + target);
		return -1;
	}

	CompletableFuture<Value> f = Launcher.getCarrierNode().findValue(id, option);
	Value v = f.get();
	if (v != null)
		System.out.println(v);
	else
		System.out.println("Not found.");

	return 0;
}
}

