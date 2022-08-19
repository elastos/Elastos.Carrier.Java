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

import org.apache.tuweni.crypto.sodium.Box;
import org.apache.tuweni.crypto.sodium.Box.SecretKey;
import org.apache.tuweni.crypto.sodium.Box.Seed;
import org.apache.tuweni.crypto.sodium.Sodium;

public class CryptoBox implements AutoCloseable {
	private Box box;

	public static class PublicKey implements Destroyable {
		private Box.PublicKey key;
		private byte[] bytes;

		private PublicKey(Box.PublicKey key) {
			this.key = key;
		}

		public static PublicKey fromBytes(byte[] key) {
			return new PublicKey(Box.PublicKey.fromBytes(key));
		}

		public static PublicKey fromSignatureKey(Signature.PublicKey key) {
			return new PublicKey(Box.PublicKey.forSignaturePublicKey(key.raw()));
		}

		public static int length() {
			return Box.PublicKey.length();
		}

		Box.PublicKey raw() {
			return key;
		}

		public byte[] bytes() {
			if (bytes == null)
				bytes = key.bytesArray();

			return bytes;
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
		private Box.SecretKey key;
		private byte[] bytes;

		private PrivateKey(Box.SecretKey key) {
			this.key = key;
		}

		public static PrivateKey fromBytes(byte[] key) {
			return new PrivateKey(Box.SecretKey.fromBytes(key));
		}

		public static PrivateKey fromSignatureKey(Signature.PrivateKey key) {
			return new PrivateKey(Box.SecretKey.forSignatureSecretKey(key.raw()));
		}

		public static int length() {
			return Box.SecretKey.length();
		}

		Box.SecretKey raw() {
			return key;
		}

		public byte[] bytes() {
			if (bytes == null)
				bytes = key.bytesArray();

			return bytes;
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
		private Box.KeyPair keyPair;
		private PublicKey pk;
		private PrivateKey sk;

		private KeyPair(Box.KeyPair keyPair) {
			this.keyPair = keyPair;
		}

		public static KeyPair fromPrivateKey(byte[] privateKey) {
			SecretKey sk = SecretKey.fromBytes(privateKey);
			return new KeyPair(Box.KeyPair.forSecretKey(sk));
		}

		public static KeyPair fromPrivateKey(PrivateKey key) {
			return new KeyPair(Box.KeyPair.forSecretKey(key.raw()));
		}

		public static KeyPair fromSeed(byte[] seed) {
			Seed sd = Seed.fromBytes(seed);
			return new KeyPair(Box.KeyPair.fromSeed(sd));
		}

		public static KeyPair fromSignatureKeyPair(Signature.KeyPair keyPair) {
			return new KeyPair(Box.KeyPair.forSignatureKeyPair(keyPair.raw()));
		}

		public static KeyPair random() {
			return new KeyPair(Box.KeyPair.random());
		}

		Box.KeyPair raw() {
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

	public static class Nonce {
		private Box.Nonce nonce;
		private byte[] bytes;

		private Nonce(Box.Nonce nonce) {
			this.nonce = nonce;
		}

		public static Nonce fromBytes(byte[] nonce) {
			return new Nonce(Box.Nonce.fromBytes(nonce));
		}

		public static Nonce random() {
			return new Nonce(Box.Nonce.random());
		}

		public static Nonce zero() {
			return new Nonce(Box.Nonce.zero());
		}

		public static int length() {
			return Box.Nonce.length();
		}

		Box.Nonce raw() {
			return nonce;
		}

		public Nonce increment() {
			return new Nonce(nonce.increment());
		}

		public byte[] bytes() {
			if (bytes == null)
				bytes = nonce.bytesArray();

			return bytes;
		}

		@Override
		public boolean equals(Object obj) {
			if (obj == this)
				return true;

			if (obj instanceof Nonce) {
				Nonce other = (Nonce)obj;
				return nonce.equals(other.nonce);
			}

			return false;
		}

		@Override
		public int hashCode() {
			return nonce.hashCode() + 0x63; // + 'c' - Carrier
		}
	}

	private CryptoBox(Box box) {
		this.box = box;
	}

	public static CryptoBox fromKeys(PublicKey pk, PrivateKey sk) {
		return new CryptoBox(Box.forKeys(pk.raw(), sk.raw()));
	}

	public byte[] encrypt(byte[] plain, Nonce nonce) {
		return box.encrypt(plain, nonce.raw());
	}

	public static byte[] encrypt(byte[] plain, PublicKey receiver, PrivateKey sender, Nonce nonce) {
		return Box.encrypt(plain, receiver.raw(), sender.raw(), nonce.raw());
	}

	public static byte[] encrypt(byte[] plain, PublicKey receiver) {
		return Box.encryptSealed(plain, receiver.raw());
	}

	public byte[] decrypt(byte[] cipher, Nonce nonce) {
		return box.decrypt(cipher, nonce.raw());
	}

	public static byte[] decrypt(byte[] cipher, PublicKey sender, PrivateKey receiver, Nonce nonce) {
		return Box.decrypt(cipher, sender.raw(), receiver.raw(), nonce.raw());
	}

	public static byte[] decrypt(byte[] cipher, PublicKey pk, PrivateKey sk) {
		return Box.decryptSealed(cipher, pk.raw(), sk.raw());
	}

	@Override
	public void close() {
		box.close();
	}

	@Override
	protected void finalize() {
		close();
	}

	static {
		if (!Sodium.isAvailable()) {
			throw new RuntimeException("Sodium native library is not available!");
		}
	}
}
