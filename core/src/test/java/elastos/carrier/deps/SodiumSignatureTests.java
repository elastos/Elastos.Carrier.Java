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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import org.apache.tuweni.crypto.sodium.Signature;
import org.apache.tuweni.crypto.sodium.Sodium;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import elastos.carrier.utils.Hex;

public class SodiumSignatureTests {
	@BeforeAll
	static void checkAvailable() {
		assumeTrue(Sodium.isAvailable(), "Sodium native library is not available");
	}

	@Test
	public void keyPairFromSeed() {
		Signature.Seed seed = Signature.Seed.random();
		Signature.KeyPair kp = Signature.KeyPair.fromSeed(seed);
		Signature.KeyPair kp2 = Signature.KeyPair.fromSeed(seed);

		assertEquals(kp.secretKey(), kp2.secretKey());
		assertEquals(kp.publicKey(), kp2.publicKey());
	}

	@Test
	void testEqualityAndRecovery() {
		Signature.KeyPair kp = Signature.KeyPair.random();
		Signature.KeyPair otherKp = Signature.KeyPair.forSecretKey(kp.secretKey());
		assertEquals(kp, otherKp);
	}

	@Test
	void checkDetachedSignVerify() {
		Signature.KeyPair kp = Signature.KeyPair.random();
		byte[] signature = Signature.signDetached(Hex.decode("deadbeef"), kp.secretKey());
		System.out.println(Hex.encode(signature));
		boolean result = Signature.verifyDetached(Hex.decode("deadbeef"), signature, kp.publicKey());
		assertTrue(result);
	}

	@Test
	void checkSignAndVerify() {
		Signature.KeyPair keyPair = Signature.KeyPair.random();
		byte[] signed = Signature.sign(Hex.decode("deadbeef"), keyPair.secretKey());
		System.out.println(Hex.encode(signed));
		byte[] messageBytes = Signature.verify(signed, keyPair.publicKey());
		assertArrayEquals(Hex.decode("deadbeef"), messageBytes);
	}

	@Test
	void testDestroyPublicKey() {
		Signature.KeyPair keyPair = Signature.KeyPair.random();
		Signature.PublicKey sigPubKey = Signature.PublicKey.fromBytes(keyPair.publicKey().bytes());
		sigPubKey.destroy();
		assertTrue(sigPubKey.isDestroyed());
		assertFalse(keyPair.publicKey().isDestroyed());
	}
}
