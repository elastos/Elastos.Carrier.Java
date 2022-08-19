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

package elastos.carrier.crypto;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import elastos.carrier.utils.Hex;
import elastos.carrier.utils.ThreadLocals;

public class CryptoBoxTests {
	private CryptoBox.Nonce nonce;

	@BeforeEach
	protected void incrementNonce() {
		if (nonce == null)
			nonce = CryptoBox.Nonce.random();
		else
			nonce = nonce.increment();
	}

	@Test
	public void keyPairFromSeed() {
		byte[] seed = new byte[32];
		ThreadLocals.random().nextBytes(seed);

		CryptoBox.KeyPair kp = CryptoBox.KeyPair.fromSeed(seed);
		CryptoBox.KeyPair kp2 = CryptoBox.KeyPair.fromSeed(seed);

		assertEquals(kp.privateKey(), kp2.privateKey());
		assertEquals(kp.publicKey(), kp2.publicKey());

		assertEquals(CryptoBox.PrivateKey.length(), kp.privateKey().bytes().length);
		assertEquals(CryptoBox.PublicKey.length(), kp.publicKey().bytes().length);
	}

	@Test
	public void testEqualityAndRecovery() {
		CryptoBox.KeyPair kp = CryptoBox.KeyPair.random();
		CryptoBox.KeyPair otherKp1 = CryptoBox.KeyPair.fromPrivateKey(kp.privateKey());
		CryptoBox.KeyPair otherKp2 = CryptoBox.KeyPair.fromPrivateKey(kp.privateKey().bytes());

		assertEquals(kp, otherKp1);
		assertEquals(kp, otherKp2);

		assertEquals(CryptoBox.PrivateKey.length(), kp.privateKey().bytes().length);
		assertEquals(CryptoBox.PublicKey.length(), kp.publicKey().bytes().length);
	}

	@Test
	public void testKeyBytes() {
		CryptoBox.KeyPair kp = CryptoBox.KeyPair.random();
		CryptoBox.PrivateKey sk = CryptoBox.PrivateKey.fromBytes(kp.privateKey().bytes());
		CryptoBox.PublicKey pk = CryptoBox.PublicKey.fromBytes(kp.publicKey().bytes());

		assertEquals(kp.privateKey(), sk);
		assertEquals(kp.publicKey(), pk);

		assertArrayEquals(kp.privateKey().bytes(), sk.bytes());
		assertArrayEquals(kp.publicKey().bytes(), pk.bytes());
	}

	@Test
	public void encryptDecryptSealed() {
		CryptoBox.KeyPair receiver = CryptoBox.KeyPair.random();

		byte[] encrypted = CryptoBox.encrypt(Hex.decode("deadbeef"), receiver.publicKey());
		assertNotNull(encrypted);

		byte[] decrypted = CryptoBox.decrypt(encrypted, receiver.publicKey(), receiver.privateKey());
		assertNotNull(decrypted);
		assertArrayEquals(Hex.decode("deadbeef"), decrypted);
	}

	@Test
	public void checkEncryptAndDecrypt() {
		CryptoBox.KeyPair alice = CryptoBox.KeyPair.random();
		CryptoBox.KeyPair bob = CryptoBox.KeyPair.random();

		byte[] message = "This is a test message".getBytes();

		byte[] encrypted = CryptoBox.encrypt(message, alice.publicKey(), bob.privateKey(), nonce);
		assertNotNull(encrypted);

		byte[] decrypted = CryptoBox.decrypt(encrypted, bob.publicKey(), alice.privateKey(), nonce);
		assertNotNull(decrypted);
		assertArrayEquals(message, decrypted);

		decrypted = CryptoBox.decrypt(encrypted, bob.publicKey(), alice.privateKey(), nonce.increment());
		assertNull(decrypted);

		CryptoBox.KeyPair other = CryptoBox.KeyPair.random();
		encrypted = CryptoBox.decrypt(encrypted, bob.publicKey(), other.privateKey(), nonce);
		assertNull(encrypted);
	}

	@Test
	public void checkPrecomputedEncryptAndDecrypt() {
		CryptoBox.KeyPair alice = CryptoBox.KeyPair.random();
		CryptoBox.KeyPair bob = CryptoBox.KeyPair.random();

		byte[] message = "This is a test message".getBytes();
		byte[] encrypted;

		try (CryptoBox precomputed = CryptoBox.fromKeys(alice.publicKey(), bob.privateKey())) {
			encrypted = precomputed.encrypt(message, nonce);
			assertNotNull(encrypted);
		}

		try (CryptoBox precomputed = CryptoBox.fromKeys(bob.publicKey(), alice.privateKey())) {
			byte[] decrypted = precomputed.decrypt(encrypted, nonce);
			assertNotNull(decrypted);
			assertArrayEquals(message, decrypted);
		}

		try (CryptoBox precomputed = CryptoBox.fromKeys(bob.publicKey(), alice.privateKey())) {
			byte[] decrypted = precomputed.decrypt(encrypted, nonce);

			assertNotNull(decrypted);
			assertArrayEquals(message, decrypted);

			assertNull(precomputed.decrypt(encrypted, nonce.increment()));
		}

		CryptoBox.KeyPair other = CryptoBox.KeyPair.random();
		try (CryptoBox precomputed = CryptoBox.fromKeys(bob.publicKey(), other.privateKey())) {
			assertNull(precomputed.decrypt(encrypted, nonce));
		}
	}

	@Test
	public void checkBoxKeyPairFromSignatureKeyPair() {
		Signature.KeyPair signKeyPair = Signature.KeyPair.random();
		CryptoBox.KeyPair boxKeyPair = CryptoBox.KeyPair.fromSignatureKeyPair(signKeyPair);
		assertNotNull(boxKeyPair);
	}

	@Test
	public void checkBoxKeysFromSignatureKeys() {
		Signature.KeyPair keyPair = Signature.KeyPair.random();
		CryptoBox.PublicKey boxPk = CryptoBox.PublicKey.fromSignatureKey(keyPair.publicKey());
		CryptoBox.PrivateKey boxSk = CryptoBox.PrivateKey.fromSignatureKey(keyPair.privateKey());
		assertEquals(boxPk, CryptoBox.KeyPair.fromPrivateKey(boxSk).publicKey());

		CryptoBox.KeyPair boxKeyPair = CryptoBox.KeyPair.fromSignatureKeyPair(keyPair);
		assertEquals(boxKeyPair, CryptoBox.KeyPair.fromPrivateKey(boxSk));
		assertEquals(boxSk, boxKeyPair.privateKey());
		assertEquals(boxPk, boxKeyPair.publicKey());
	}

	@Test
	public void testDestroy() {
		CryptoBox.KeyPair keyPair = CryptoBox.KeyPair.random();
		keyPair.privateKey().destroy();
		assertTrue(keyPair.privateKey().isDestroyed());
		assertFalse(keyPair.publicKey().isDestroyed());

		keyPair.publicKey().destroy();
		assertTrue(keyPair.privateKey().isDestroyed());
		assertTrue(keyPair.publicKey().isDestroyed());

		IllegalStateException ex = assertThrows(IllegalStateException.class, () -> {
			keyPair.privateKey().bytes();
		});
		assertEquals("allocated value has been destroyed", ex.getMessage());

		ex = assertThrows(IllegalStateException.class, () -> {
			keyPair.publicKey().bytes();
		});
		assertEquals("allocated value has been destroyed", ex.getMessage());
	}
}
