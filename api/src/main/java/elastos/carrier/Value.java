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

package elastos.carrier;

import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Objects;

import elastos.carrier.crypto.CryptoBox;
import elastos.carrier.crypto.CryptoException;
import elastos.carrier.crypto.Signature;
import elastos.carrier.utils.Hex;
import elastos.carrier.utils.ThreadLocals;

public class Value {
	private Id publicKey;
	private byte[] privateKey;
	private Id recipient;
	private byte[] nonce;
	private byte[] signature;
	private int sequenceNumber;
	private byte[] data;

	protected Value() {
	}

	protected Value(Id publicKey, byte[] privateKey, Id recipient, byte[] nonce, int sequenceNumber, byte[] signature, byte[] data) {
		this.publicKey = publicKey;
		this.privateKey = privateKey;
		this.recipient = recipient;
		this.nonce = nonce;
		this.sequenceNumber = sequenceNumber;
		this.signature = signature;
		this.data = data;
	}

	protected Value(Signature.KeyPair keypair, Id recipient, CryptoBox.Nonce nonce, int sequenceNumber, byte[] data) throws CryptoException {
		this.publicKey = new Id(keypair.publicKey().bytes());
		this.privateKey = keypair.privateKey().bytes();
		this.recipient = recipient;
		this.nonce = nonce.bytes();
		this.sequenceNumber = sequenceNumber;

		if (recipient != null) {
			CryptoBox.PublicKey recipientPk = recipient.toEncryptionKey();
			CryptoBox.PrivateKey ownerSk = CryptoBox.PrivateKey.fromSignatureKey(keypair.privateKey());

			this.data = CryptoBox.encrypt(data, recipientPk, ownerSk, nonce);
		} else {
			this.data = data;
		}

		this.signature = Signature.sign(getSignData(), keypair.privateKey());
	}

	public static Value of(byte[] data) {
		return new Value(null, null, null, null, 0, null, data);
	}

	public static Value of(Id publicKey, Id recipient, byte[] nonce, int sequenceNumber, byte[] signature, byte[] data) {
		return new Value(publicKey, null, recipient, nonce, sequenceNumber, signature, data);
	}

	public static Value of(Id publicKey, byte[] privateKey, Id recipient, byte[] nonce, int sequenceNumber, byte[] signature, byte[] data) {
		return new Value(publicKey, privateKey, recipient, nonce, sequenceNumber, signature, data);
	}

	public static Value of(Signature.KeyPair keypair ,CryptoBox.Nonce nonce, int sequenceNumber, byte[] data) throws CryptoException {
		return new Value(keypair, null, nonce, sequenceNumber, data);
	}

	public static Value of(Signature.KeyPair keypair, Id recipient, CryptoBox.Nonce nonce, int sequenceNumber, byte[] data) throws CryptoException {
		return new Value(keypair, recipient, nonce, sequenceNumber, data);
	}

	private byte[] getSignData() {
		byte[] toSign = new byte[(recipient != null ? Id.BYTES : 0) +
				CryptoBox.Nonce.BYTES + Integer.BYTES + this.data.length];
		ByteBuffer buf = ByteBuffer.wrap(toSign);
		if (recipient != null)
			buf.put(recipient.bytes());
		buf.put(nonce);
		buf.putInt(sequenceNumber);
		buf.put(data);

		return toSign;
	}

	public Id getId() {
		return calculateId(this.publicKey, this.nonce, this.data);
	}

	public Id getPublicKey() {
		return publicKey;
	}

	public boolean hasPrivateKey() {
		return privateKey != null;
	}

	public byte[] getPrivateKey() {
		return privateKey;
	}

	public Id getRecipient() {
		return recipient;
	}

	public byte[] getNonce() {
		return nonce;
	}

	public int getSequenceNumber() {
		return sequenceNumber;
	}

	public byte[] getSignature() {
		return signature;
	}

	public byte[] getData() {
		return data;
	}

	public static Id calculateId(Id publicKey, byte[] nonce, byte[] data) {
		MessageDigest digest = ThreadLocals.sha256();
		digest.reset();

		if(publicKey != null) {
			digest.update(publicKey.bytes());
			digest.update(nonce);
		} else {
			digest.update(data);
		}

		return new Id(digest.digest());
	}

	public boolean isMutable() {
		return publicKey != null;
	}

	public boolean isEncrypted() {
		return recipient != null;
	}

	public boolean isValid() {
		if (data == null || data.length == 0)
			return false;

		if (isMutable()) {
			if (nonce == null || nonce.length != CryptoBox.Nonce.BYTES)
				return false;

			if (signature == null || signature.length != Signature.BYTES)
				return false;

			Signature.PublicKey pk = publicKey.toSignatureKey();

			return Signature.verify(getSignData(), signature, pk);
		}

		return true;
	}

	public byte[] decryptData(Signature.PrivateKey recipientSk) throws CryptoException {
		if (!isValid())
			return null;

		if (recipient == null)
			return null;

		CryptoBox.PublicKey pk = publicKey.toEncryptionKey();
		CryptoBox.PrivateKey sk = CryptoBox.PrivateKey.fromSignatureKey(recipientSk);

		return CryptoBox.decrypt(data, pk, sk, CryptoBox.Nonce.fromBytes(nonce));
	}

	@Override
	public boolean equals(Object o) {
		if (o == this)
			return true;

		if (o instanceof Value) {
			Value v = (Value)o;

			return sequenceNumber == v.sequenceNumber &&
					Objects.equals(publicKey, v.publicKey) &&
					Objects.equals(recipient, v.recipient) &&
					Arrays.equals(nonce, v.nonce) &&
					Arrays.equals(signature, v.signature) &&
					Arrays.equals(data, v.data);
		}

		return false;
	}

	@Override
	public String toString() {
		StringBuilder repr = new StringBuilder();
		repr.append("id:").append(getId());

		if (publicKey != null)
			repr.append(",publicKey:").append(publicKey);

		if (recipient != null)
			repr.append(",recipient:").append(recipient);

		if (nonce != null)
			repr.append(",nonce: ").append(Hex.encode(nonce));

		if (publicKey != null)
			repr.append(",seq:").append(sequenceNumber);

		if (signature != null)
			repr.append(",sig:").append(Hex.encode(signature));

		repr.append(",data:").append(Hex.encode(data));

		return repr.toString();
	}
}
