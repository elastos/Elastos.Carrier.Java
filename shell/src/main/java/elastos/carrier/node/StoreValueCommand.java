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
import elastos.carrier.Node;
import elastos.carrier.Value;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(name = "storevalue", mixinStandardHelpOptions = true, version = "Carrier storevalue 2.0",
		description = "Store a value to the DHT.")
public class StoreValueCommand implements Callable<Integer> {
	@Option(names = { "-p", "--persistent" }, description = "Persistent value, default is false.")
	private boolean persistent = false;

	@Option(names = { "-m", "--mutable" }, description = "Mutbale value, default is immutable value, no effect on update mode.")
	private boolean mutable = false;

	@Option(names = { "-u", "--update-value" }, description = "Existing value id to be update.")
	private String target = null;

	@Option(names = { "-r", "--recipient" }, description = "The recipient id, no effect on immutable values or update mode")
	private String recipient = null;

	@Parameters(paramLabel = "VALUE", index = "0", description = "The value text.")
	private String text = null;

	@Override
	public Integer call() throws Exception {
		Node node = Shell.getCarrierNode();
		Value value = null;

		if (recipient != null)
			mutable = true;

		if (target == null) {
			if (mutable) {
				if (recipient == null) {
					value = Value.createSignedValue(text.getBytes());
 				} else {
 					Id recipientId = null;
 					try {
 						recipientId = Id.of(recipient);
 					} catch (Exception e) {
 						System.out.println("Invalid recipient: " + recipient);
 						return -1;
 					}

 					value = Value.createEncryptedValue(recipientId, text.getBytes());
 				}
			} else {
				value = Value.createValue(text.getBytes());
			}
		} else {
			Id id = null;
			try {
				id = Id.of(target);
			} catch (Exception e) {
				System.out.println("Invalid value id to be update: " + target);
				return -1;
			}

			value = node.getValue(id);
			if (value == null) {
				System.out.println("Value not exists: " + target);
				return -1;
			}

			try {
				value = value.update(text.getBytes());
			} catch (Exception e) {
				System.out.println("Can not update the value: " + e.getMessage());
				return -1;
			}
		}

		CompletableFuture<Void> f = Shell.getCarrierNode().storeValue(value, persistent);
		f.get();
		System.out.println("Value " + value.getId() + " stored.");
		return 0;
	}
}
