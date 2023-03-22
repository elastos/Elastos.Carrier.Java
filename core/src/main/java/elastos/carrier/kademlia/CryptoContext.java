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

package elastos.carrier.kademlia;

import java.util.Arrays;

import elastos.carrier.Id;
import elastos.carrier.crypto.CryptoBox;
import elastos.carrier.crypto.CryptoBox.KeyPair;
import elastos.carrier.crypto.CryptoBox.PublicKey;
import elastos.carrier.crypto.CryptoException;
import elastos.carrier.kademlia.exceptions.CryptoError;

public class CryptoContext implements AutoCloseable {
	private CryptoBox box;
	private CryptoBox.Nonce nonce;

	public CryptoContext(Id id, KeyPair keyPair) throws CryptoError {
		try {
			PublicKey pk = id.toEncryptionKey();
			box = CryptoBox.fromKeys(pk, keyPair.privateKey());

			Id receiver = Id.of(pk.bytes());
			Id sender = Id.of(keyPair.publicKey().bytes());

			Id dist = Id.distance(sender, receiver);

			nonce = CryptoBox.Nonce.fromBytes(Arrays.copyOf(dist.bytes(), CryptoBox.Nonce.BYTES));
		} catch (CryptoException e) {
			throw new CryptoError(e.getMessage(), e);
		}
	}

	public byte[] encrypt(byte[] plain) throws CryptoError {
		try {
			return box.encrypt(plain, nonce);
		} catch (CryptoException e) {
			throw new CryptoError(e.getMessage(), e);
		}
	}

	public byte[] decrypt(byte[] cipher) throws CryptoError {
		try {
			return box.decrypt(cipher, nonce);
		} catch (CryptoException e) {
			throw new CryptoError(e.getMessage(), e);
		}
	}

	@Override
	public void close() {
		box.close();
	}
}
