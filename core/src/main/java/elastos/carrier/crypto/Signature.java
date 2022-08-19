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

import java.util.Arrays;

import javax.security.auth.Destroyable;

import org.apache.tuweni.crypto.sodium.Signature.SecretKey;
import org.apache.tuweni.crypto.sodium.Signature.Seed;
import org.apache.tuweni.crypto.sodium.Sodium;

public class Signature {
	public static class PublicKey implements Destroyable {
		private org.apache.tuweni.crypto.sodium.Signature.PublicKey key;
		private byte[] bytes;

		private PublicKey(org.apache.tuweni.crypto.sodium.Signature.PublicKey key) {
			this.key = key;
		}

		public static PublicKey fromBytes(byte[] key) {
			return new PublicKey(org.apache.tuweni.crypto.sodium.Signature.PublicKey.fromBytes(key));
		}

		public static int length() {
			return org.apache.tuweni.crypto.sodium.Signature.PublicKey.length();
		}

		org.apache.tuweni.crypto.sodium.Signature.PublicKey raw() {
			return key;
		}

		public byte[] bytes() {
			if (bytes == null)
				bytes = key.bytesArray();

			return bytes;
		}

		public boolean verify(byte[] data, byte[] signature) {
			return Signature.verify(data, signature, this);
		}

		@Override
		public boolean equals(Object obj) {
			if (obj == this)
				return true;

			if (obj instanceof PublicKey) {
				PublicKey other = (PublicKey)obj;
				return key.equals(other.key);
			}

			return false;
		}

		@Override
		public int hashCode() {
			return key.hashCode() + 0x63; // + 'c' - Carrier
		}

		/**
		 * Destroy this {@code Object}.
		 *
		 * <p> Sensitive information associated with this {@code Object}
		 * is destroyed or cleared.  Subsequent calls to certain methods
		 * on this {@code Object} will result in an
		 * {@code IllegalStateException} being thrown.
		 */
		@Override
		public void destroy() {
			if (!key.isDestroyed()) {
				key.destroy();

				if (bytes != null) {
					Arrays.fill(bytes, (byte)0);
					bytes = null;
				}
			}
		}

		/**
		 * Determine if this {@code Object} has been destroyed.
		 *
		 * @return true if this {@code Object} has been destroyed,
		 *          false otherwise.
		 */
		@Override
		public boolean isDestroyed() {
			return key.isDestroyed();
		}
	}

	public static class PrivateKey implements Destroyable {
		private org.apache.tuweni.crypto.sodium.Signature.SecretKey key;
		private byte[] bytes;

		private PrivateKey(org.apache.tuweni.crypto.sodium.Signature.SecretKey key) {
			this.key = key;
		}

		public static PrivateKey fromBytes(byte[] key) {
			return new PrivateKey(org.apache.tuweni.crypto.sodium.Signature.SecretKey.fromBytes(key));
		}

		public static int length() {
			return org.apache.tuweni.crypto.sodium.Signature.SecretKey.length();
		}

		org.apache.tuweni.crypto.sodium.Signature.SecretKey raw() {
			return key;
		}

		public byte[] bytes() {
			if (bytes == null)
				bytes = key.bytesArray();

			return bytes;
		}

		public byte[] sign(byte[] data) {
			return Signature.sign(data, this);
		}

		@Override
		public boolean equals(Object obj) {
			if (obj == this)
				return true;

			if (obj instanceof PrivateKey) {
				PrivateKey other = (PrivateKey)obj;
				return key.equals(other.key);
			}

			return false;
		}

		@Override
		public int hashCode() {
			return key.hashCode() + 0x63; // + 'c' - Carrier
		}

		/**
		 * Destroy this {@code Object}.
		 *
		 * <p> Sensitive information associated with this {@code Object}
		 * is destroyed or cleared.  Subsequent calls to certain methods
		 * on this {@code Object} will result in an
		 * {@code IllegalStateException} being thrown.
		 */
		@Override
		public void destroy() {
			if (!key.isDestroyed()) {
				key.destroy();

				if (bytes != null) {
					Arrays.fill(bytes, (byte)0);
					bytes = null;
				}
			}
		}

		/**
		 * Determine if this {@code Object} has been destroyed.
		 *
		 * @return true if this {@code Object} has been destroyed,
		 *          false otherwise.
		 */
		@Override
		public boolean isDestroyed() {
			return key.isDestroyed();
		}
	}

	public static class KeyPair {
		private org.apache.tuweni.crypto.sodium.Signature.KeyPair keyPair;
		private PublicKey pk;
		private PrivateKey sk;

		private KeyPair(org.apache.tuweni.crypto.sodium.Signature.KeyPair keyPair) {
			this.keyPair = keyPair;
		}

		public static KeyPair fromPrivateKey(byte[] privateKey) {
			SecretKey sk = SecretKey.fromBytes(privateKey);
			return new KeyPair(org.apache.tuweni.crypto.sodium.Signature.KeyPair.forSecretKey(sk));
		}

		public static KeyPair fromPrivateKey(PrivateKey key) {
			return new KeyPair(org.apache.tuweni.crypto.sodium.Signature.KeyPair.forSecretKey(key.raw()));
		}

		/* 32 bytes seed */
		public static KeyPair fromSeed(byte[] seed) {
			Seed sd = Seed.fromBytes(seed);
			return new KeyPair(org.apache.tuweni.crypto.sodium.Signature.KeyPair.fromSeed(sd));
		}

		public static KeyPair random() {
			return new KeyPair(org.apache.tuweni.crypto.sodium.Signature.KeyPair.random());
		}

		org.apache.tuweni.crypto.sodium.Signature.KeyPair raw() {
			return keyPair;
		}

		public PublicKey publicKey() {
			if (pk == null)
				pk = new PublicKey(keyPair.publicKey());

			return pk;
		}

		public PrivateKey privateKey() {
			if (sk == null)
				sk = new PrivateKey(keyPair.secretKey());

			return sk;
		}

		@Override
		public boolean equals(Object obj) {
			if (obj == this)
				return true;

			if (obj instanceof KeyPair) {
				KeyPair other = (KeyPair)obj;
				return keyPair.equals(other.keyPair);
			}

			return false;
		}

		@Override
		public int hashCode() {
			return keyPair.hashCode() + 0x63; // + 'c' - Carrier key pair
		}
	}

	public static int length() {
		// Can not access internal method
		// return (int)Sodium.crypto_sign_bytes();
		return 64;
	}

	public static byte[] sign(byte[] data, PrivateKey key) {
		return org.apache.tuweni.crypto.sodium.Signature.signDetached(data, key.raw());
	}

	public static boolean verify(byte[] data, byte[] signature, PublicKey key) {
		return org.apache.tuweni.crypto.sodium.Signature.verifyDetached(data, signature, key.raw());
	}

	static {
		if (!Sodium.isAvailable()) {
			throw new RuntimeException("Sodium native library is not available!");
		}
	}
}
