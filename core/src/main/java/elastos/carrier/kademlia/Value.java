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

import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Objects;

import elastos.carrier.crypto.CryptoBox;
import elastos.carrier.crypto.Signature;
import elastos.carrier.kademlia.exceptions.CryptoError;
import elastos.carrier.kademlia.messages.FindValueResponse;
import elastos.carrier.kademlia.messages.StoreValueRequest;
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

	protected Value(byte[] data) {
		this(null, null, null, null, 0, null, data);
	}

	protected Value(Id publicKey, Id recipient, byte[] nonce, int sequenceNumber, byte[] signature, byte[] data) {
		this(publicKey, null, recipient, nonce, sequenceNumber, signature, data);
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

	protected Value(Signature.KeyPair keypair ,CryptoBox.Nonce nonce, int sequenceNumber, byte[] data) throws CryptoError {
		this(keypair, null, nonce, sequenceNumber, data);
	}

	protected Value(Signature.KeyPair keypair, Id recipient, CryptoBox.Nonce nonce, int sequenceNumber, byte[] data) throws CryptoError {
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

	private byte[] getSignData() {
		byte[] toSign = new byte[(recipient != null ? Id.BYTE_LENGTH : 0) +
				CryptoBox.Nonce.length() + Integer.BYTES + this.data.length];
		ByteBuffer buf = ByteBuffer.wrap(toSign);
		if (recipient != null)
			buf.put(recipient.getBytes());
		buf.put(nonce);
		buf.putInt(sequenceNumber);
		buf.put(data);

		return toSign;
	}

	public static Value of(StoreValueRequest request) {
		return new Value(request.getPublicKey(), request.getRecipient(), request.getNonce(),
				request.getSequenceNumber(), request.getSignature(), request.getValue());
	}

	public static Value of(FindValueResponse response) {
		return new Value(response.getPublicKey(), response.getRecipient(), response.getNonce(),
				response.getSequenceNumber(), response.getSignature(), response.getValue());
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

	byte[] getPrivateKey() {
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

		byte[] hash;
		if(publicKey != null) {
			digest.update(publicKey.getBytes());
			digest.update(nonce);
			hash = digest.digest();
		} else {
			digest.update(data);
			hash = digest.digest();
		}

		return new Id(hash);
	}

	public boolean isMutable() {
		return publicKey != null;
	}

	public boolean isEncrupted() {
		return recipient != null;
	}

	public boolean isValid() {
		if (data == null || data.length == 0)
			return false;

		if (isMutable()) {
			if (nonce == null || nonce.length != CryptoBox.Nonce.length())
				return false;

			if (signature == null || signature.length != Signature.length())
				return false;

			Signature.PublicKey pk = publicKey.toSignatureKey();

			return Signature.verify(getSignData(), signature, pk);
		}

		return true;
	}

	public byte[] decryptData(Signature.PrivateKey recipientSk) {
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
