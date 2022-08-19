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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import elastos.carrier.utils.Hex;
import elastos.carrier.utils.ThreadLocals;

public class SignatureTests {
	@Test
	public void keyPairFromSeed() {
		byte[] seed = new byte[32];
		ThreadLocals.random().nextBytes(seed);

		Signature.KeyPair kp = Signature.KeyPair.fromSeed(seed);
		Signature.KeyPair kp2 = Signature.KeyPair.fromSeed(seed);

		assertEquals(kp.privateKey(), kp2.privateKey());
		assertEquals(kp.publicKey(), kp2.publicKey());

		assertEquals(Signature.PrivateKey.length(), kp.privateKey().bytes().length);
		assertEquals(Signature.PublicKey.length(), kp.publicKey().bytes().length);
	}

	@Test
	public void testEqualityAndRecovery() {
		Signature.KeyPair kp = Signature.KeyPair.random();
		Signature.KeyPair otherKp1 = Signature.KeyPair.fromPrivateKey(kp.privateKey());
		Signature.KeyPair otherKp2 = Signature.KeyPair.fromPrivateKey(kp.privateKey().bytes());

		assertEquals(kp, otherKp1);
		assertEquals(kp, otherKp2);

		assertEquals(Signature.PrivateKey.length(), kp.privateKey().bytes().length);
		assertEquals(Signature.PublicKey.length(), kp.publicKey().bytes().length);
	}

	@Test
	public void testKeyBytes() {
		Signature.KeyPair kp = Signature.KeyPair.random();
		Signature.PrivateKey sk = Signature.PrivateKey.fromBytes(kp.privateKey().bytes());
		Signature.PublicKey pk = Signature.PublicKey.fromBytes(kp.publicKey().bytes());

		assertEquals(kp.privateKey(), sk);
		assertEquals(kp.publicKey(), pk);

		assertArrayEquals(kp.privateKey().bytes(), sk.bytes());
		assertArrayEquals(kp.publicKey().bytes(), pk.bytes());
	}

	@Test
	public void checkSignAndVerify() {
		Signature.KeyPair kp = Signature.KeyPair.random();
		byte[] sig = Signature.sign(Hex.decode("deadbeef"), kp.privateKey());
		assertEquals(Signature.length(), sig.length);
		System.out.println(Hex.encode(sig));

		boolean result = Signature.verify(Hex.decode("deadbeef"), sig, kp.publicKey());
		assertTrue(result);
	}

	@Test
	public void checkSignAndVerifyWithKey() {
		Signature.KeyPair kp = Signature.KeyPair.random();
		byte[] sig = kp.privateKey().sign(Hex.decode("deadbeef"));
		assertEquals(Signature.length(), sig.length);
		System.out.println(Hex.encode(sig));

		boolean result = kp.publicKey().verify(Hex.decode("deadbeef"), sig);
		assertTrue(result);
	}

	@Test
	public void testDestroy() {
		Signature.KeyPair keyPair = Signature.KeyPair.random();
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
