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

package elastos.carrier.deps;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import org.apache.tuweni.crypto.sodium.Box;
import org.apache.tuweni.crypto.sodium.Signature;
import org.apache.tuweni.crypto.sodium.Sodium;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import elastos.carrier.utils.Hex;

public class SodiumBoxTests {
	private static Box.Nonce nonce;

	@BeforeAll
	public static void setup() {
		assumeTrue(Sodium.isAvailable(), "Sodium native library is not available");
		nonce = Box.Nonce.random();
	}

	@BeforeEach
	public void incrementNonce() {
		nonce = nonce.increment();
	}

	@Test
	public void encryptDecryptSealed() {
		Box.KeyPair keyPair = Box.KeyPair.random();
		byte[] encrypted = Box.encryptSealed(Hex.decode("deadbeef"), keyPair.publicKey());
		byte[] decrypted = Box.decryptSealed(encrypted, keyPair.publicKey(), keyPair.secretKey());
		assertArrayEquals(Hex.decode("deadbeef"), decrypted);
	}

	@Test
	public void checkCombinedEncryptDecrypt() {
		Box.KeyPair alice = Box.KeyPair.random();
		Box.KeyPair bob = Box.KeyPair.random();

		byte[] message = "This is a test message".getBytes();

		byte[] cipherText = Box.encrypt(message, alice.publicKey(), bob.secretKey(), nonce);
		byte[] clearText = Box.decrypt(cipherText, bob.publicKey(), alice.secretKey(), nonce);

		assertNotNull(clearText);
		assertArrayEquals(message, clearText);

		clearText = Box.decrypt(cipherText, bob.publicKey(), alice.secretKey(), nonce.increment());
		assertNull(clearText);

		Box.KeyPair other = Box.KeyPair.random();
		clearText = Box.decrypt(cipherText, other.publicKey(), bob.secretKey(), nonce);
		assertNull(clearText);
	}

	@Test
	public void checkCombinedPrecomputedEncryptDecrypt() {
		Box.KeyPair alice = Box.KeyPair.random();
		Box.KeyPair bob = Box.KeyPair.random();

		byte[] message = "This is a test message".getBytes();
		byte[] cipherText;

		try (Box precomputed = Box.forKeys(alice.publicKey(), bob.secretKey())) {
			cipherText = precomputed.encrypt(message, nonce);
		}

		byte[] clearText = Box.decrypt(cipherText, bob.publicKey(), alice.secretKey(), nonce);

		assertNotNull(clearText);
		assertArrayEquals(message, clearText);

		try (Box precomputed = Box.forKeys(bob.publicKey(), alice.secretKey())) {
			clearText = precomputed.decrypt(cipherText, nonce);

			assertNotNull(clearText);
			assertArrayEquals(message, clearText);

			assertNull(precomputed.decrypt(cipherText, nonce.increment()));
		}

		Box.KeyPair other = Box.KeyPair.random();
		try (Box precomputed = Box.forKeys(bob.publicKey(), other.secretKey())) {
			assertNull(precomputed.decrypt(cipherText, nonce));
		}
	}

	@Test
	public void checkBoxKeyPairForSignatureKeyPair() {
		Signature.KeyPair signKeyPair = Signature.KeyPair.random();
		System.out.println(Hex.encode(signKeyPair.secretKey().bytesArray()));
		System.out.println(Hex.encode(signKeyPair.publicKey().bytesArray()));
		Box.KeyPair boxKeyPair = Box.KeyPair.forSignatureKeyPair(signKeyPair);
		System.out.println(Hex.encode(boxKeyPair.secretKey().bytesArray()));
		System.out.println(Hex.encode(boxKeyPair.publicKey().bytesArray()));
		assertNotNull(boxKeyPair);
	}

	@Test
	public void checkBoxKeysForSignatureKeys() {
		Signature.KeyPair keyPair = Signature.KeyPair.random();
		Box.PublicKey boxPubKey = Box.PublicKey.forSignaturePublicKey(keyPair.publicKey());
		Box.SecretKey boxSecretKey = Box.SecretKey.forSignatureSecretKey(keyPair.secretKey());
		assertEquals(boxPubKey, Box.KeyPair.forSecretKey(boxSecretKey).publicKey());

		Box.KeyPair boxKeyPair = Box.KeyPair.forSignatureKeyPair(keyPair);
		assertEquals(boxKeyPair, Box.KeyPair.forSecretKey(boxSecretKey));
		assertEquals(boxSecretKey, boxKeyPair.secretKey());
		assertEquals(boxPubKey, boxKeyPair.publicKey());
	}
}
