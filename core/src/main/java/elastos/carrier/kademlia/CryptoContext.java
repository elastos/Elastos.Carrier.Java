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

package elastos.carrier.kademlia;

import java.util.Arrays;

import elastos.carrier.Id;
import elastos.carrier.crypto.CryptoBox;
import elastos.carrier.crypto.CryptoBox.KeyPair;
import elastos.carrier.crypto.CryptoBox.PublicKey;

public class CryptoContext implements AutoCloseable {
	private CryptoBox box;
	private CryptoBox.Nonce nonce;

	public CryptoContext(PublicKey pk, KeyPair keyPair) {
		box = CryptoBox.fromKeys(pk, keyPair.privateKey());

		Id receiver = Id.of(pk.bytes());
		Id sender = Id.of(keyPair.publicKey().bytes());

		Id dist = Id.distance(sender, receiver);

		nonce = CryptoBox.Nonce.fromBytes(Arrays.copyOf(dist.bytes(), CryptoBox.Nonce.length()));
	}

	public byte[] encrypt(byte[] plain) {
		return box.encrypt(plain, nonce);
	}

	public byte[] decrypt(byte[] cipher) {
		return box.decrypt(cipher, nonce);
	}

	@Override
	public void close() {
		box.close();
	}
}
