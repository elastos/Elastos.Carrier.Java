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

package elastos.carrier;

import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.Arrays;

import elastos.carrier.crypto.CryptoBox;
import elastos.carrier.crypto.Signature;
import elastos.carrier.utils.Base58;
import elastos.carrier.utils.Hex;

public class Id implements Comparable<Id> {
	public static final int SIZE = 256;
	public static final int BYTES = SIZE / Byte.SIZE;

	public static final Id MIN_ID = Id.zero();
	public static final Id MAX_ID = Id.ofHex("0xFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF");

	// the performance for raw bytes is much better then BigInteger
	private byte[] bytes;

	/**
	 * sorts the closest entries to the head, the furth est to the tail
	 */
	public static final class Comparator implements java.util.Comparator<Id> {
		private final Id target;

		public Comparator(Id target) {
			this.target = target;
		}

		@Override
		public int compare(Id o1, Id o2) {
			return target.threeWayCompare(o1, o2);
		}
	}

	/**
	 * Construct a random kademlia Id.
	 */
	protected Id() {
		bytes = new byte[BYTES];
	}

	/**
	 * Construct a random kademlia Id.
	 */
	protected Id(Id id) {
		this(id.bytes);
	}

	protected Id(byte[] id) {
		this(id, 0);
	}

	/**
	 * Construct the kademlia Id from a given binary id
	 *
	 * @param id the binary id in bytes
	 */
	protected Id(byte[] buf, int pos) {
		this.bytes = Arrays.copyOfRange(buf, pos, pos + BYTES);
	}

	/**
	 * Construct the kademlia Id from a given binary id
	 *
	 * @param id the binary id in bytes
	 */
	public static Id of(byte[] id) {
		if (id.length != BYTES)
			throw new IllegalArgumentException("Binary id should be " + BYTES + " bytes long.");

		return new Id(id);
	}

	public static Id of(byte[] buf, int pos) {
		if (buf.length - pos < BYTES)
			throw new IllegalArgumentException("Binary id should be " + BYTES + " bytes long.");

		return new Id(buf, pos);
	}

	/**
	 * Clone constructor
	 *
	 * @param id Id to clone
	 */
	public static Id of(Id id) {
		return new Id(id);
	}

	public static Id of(String id) {
		return id.startsWith("0x") ? ofHex(id) : ofBase58(id);
	}

	/**
	 * Create a Carrier Id from hex string.
	 *
	 * @param hexId the id string
	 */
	public static Id ofHex(String hexId) {
		int pos = hexId.startsWith("0x") ? 2 : 0;
		if (hexId.length() != BYTES * 2 + pos)
			throw new IllegalArgumentException("Hex ID string should be " + BYTES * 2 + " characters long.");

		return of(Hex.decode(hexId, pos, BYTES * 2));
	}

	/**
	 * Create a Carrier Id from base58 string.
	 *
	 * @param base58Id the id string
	 */
	public static Id ofBase58(String base58Id) {
		return of(Base58.decode(base58Id));
	}

	public static Id ofBit(int idx) {
		Id id = new Id();
		id.bytes[idx / 8] = (byte)(0x80 >>> (idx % 8));
		return id;
	}

	public static Id zero() {
		return new Id();
	}

	public static Id random() {
		Id id = new Id();
		new SecureRandom().nextBytes(id.bytes);
		return id;
	}

	public byte[] getBytes() {
		return bytes.clone();
	}

	public byte[] bytes() {
		return bytes;
	}

	public int getInt(int offset) {
		return Byte.toUnsignedInt(bytes[offset]) << 24 |
				Byte.toUnsignedInt(bytes[offset+1]) << 16 |
				Byte.toUnsignedInt(bytes[offset+2]) << 8 |
				Byte.toUnsignedInt(bytes[offset+3]);
	}

	public Id add(Id id) {
		Id result = new Id();

		byte[] a = bytes;
		byte[] b = id.bytes;
		byte[] r = result.bytes;

		int carry = 0;
		for(int i = BYTES - 1; i >= 0; i--) {
			carry = (a[i] & 0xff) + (b[i] & 0xff) + carry;
			r[i] = (byte)(carry & 0xff);
			carry >>>= 8;
		}

		return result;
	}

	/**
	 * Checks the distance between this and another Id
	 *
	 * @param id another Id
	 *
	 * @return The distance of this NodeId from the given NodeId
	 */
	public Id distance(Id to) {
		return distance(this, to);
	}

	public static Id distance(Id id1, Id id2) {
		Id result = new Id();

		byte[] r = result.bytes;
		byte[] a = id1.bytes;
		byte[] b = id2.bytes;

		for (int i = 0; i < BYTES; i++)
			r[i] = (byte) (a[i] ^ b[i]);

		return result;
	}

	/**
	 * Get an Id that is some distance away from this Id
	 *
	 * @param distance in number of bits
	 * @return the newly generated Id
	 */
	public Id getIdByDistance(int distance) {
		byte[] result = new byte[BYTES];

		int zeroBytes = (SIZE - distance) / 8;
		int zeroBits = (SIZE - distance) % 8;

		// new byte array is initialized with all zeroes
		// Arrays.fill(result, 0, zeroBytes, (byte)0);

		if (zeroBytes < BYTES) {
			result[zeroBytes] = (byte)(0xFF >>> zeroBits);

			Arrays.fill(result, zeroBytes + 1, BYTES, (byte) 0xFF);
		}

		return this.distance(Id.of(result));
	}

	/**
	 * Gets the distance from this NodeId to another NodeId
	 *
	 * @param to
	 *
	 * @return Integer The distance
	 */
	public int approxDistance(Id to) {
		return approxDistance(this, to);
	}

	public static int approxDistance(Id id1, Id id2) {
		/**
		 * Compute the xor of this and to Get the index i of the first set bit of the
		 * xor returned NodeId The distance between them is ID_LENGTH - i
		 */
		return SIZE - id1.distance(id2).getLeadingZeros();
	}

	/**
	 * Compares the distance of two keys relative to this one using the XOR metric
	 *
	 * @return -1 if k1 is closer to this key, 0 if k1 and k2 are equal distant, 1 if
	 *         k2 is closer
	 */
	public int threeWayCompare(Id id1, Id id2) {
		int mmi = Arrays.mismatch(id1.bytes, id2.bytes);
		if (mmi == -1)
			return 0;

		int r = bytes[mmi] & 0xff;
		int a = id1.bytes[mmi] & 0xff;
		int b = id2.bytes[mmi] & 0xff;

		return Integer.compareUnsigned(a ^ r, b ^ r);
	}

	/**
	 * Counts the number of leading 0's in this Id
	 *
	 * @return the number of leading 0's
	 */
	public int getLeadingZeros() {
		int msb = 0;

		int i;
		for (i = 0; i < BYTES && bytes[i] == 0; i++);
		msb += i << 3;

		if (i < BYTES) {
			byte b = bytes[i];
			if (b > 0) {
				int n = 7;
				if (b >= 1 <<  4) { n -=  4; b >>>=  4; }
				if (b >= 1 <<  2) { n -=  2; b >>>=  2; }
				msb += (n - (b >>> 1));
			}
		}

		return msb;
	}

	/**
	 * Counts the number of trailing 0's in this Id
	 *
	 * @return the number of trailing 0's
	 */
	public int getTrailingZeros() {
		int lsb = 0;

		int i;
		for (i = BYTES - 1; i >= 0 && bytes[i] == 0; i--);
		lsb += (BYTES - 1 - i) << 3;

		if (i >= 0) {
			byte b = (byte)(~bytes[i] & (bytes[i] - 1));
			if (b <= 0) {
				lsb += (b & 8);
			} else {
				if (b > 1 <<  4) { lsb +=  4; b >>>=  4; }
				if (b > 1 <<  2) { lsb +=  2; b >>>=  2; }
				lsb += ((b >>> 1) + 1);
			}
		}

		return lsb;
	}

	/**
	 * Check if the leading bits up to the Nth bit of both Id are equal
	 *
	 * <pre>
	 *   n = -1: no bits have to match
	 *   n >= 0: n bytes have to match
	 * </pre>
	 */
	protected static boolean bitsEqual(Id id1, Id id2, int n) {
		if (n < 0)
			return true;

		int mmi = Arrays.mismatch(id1.bytes, id2.bytes);

		int indexToCheck = n >>> 3;
		int diff = (id1.bytes[indexToCheck] ^ id2.bytes[indexToCheck]) & 0xff;

		boolean bitsDiff = (diff & (0xff80 >>> (n & 0x07))) == 0;

		return mmi == indexToCheck ? bitsDiff : Integer.compareUnsigned(mmi, indexToCheck) > 0;
	}

	protected static void bitsCopy(Id src, Id dest, int depth) {
		if (depth < 0)
			return;

		// copy over all complete bytes
		int idx = depth >>> 3;
		if (idx > 0)
			System.arraycopy(src.bytes, 0, dest.bytes, 0, idx);

		int mask = 0xFF80 >>> (depth & 0x07);

		// mask out the part we have to copy over from the last prefix byte
		dest.bytes[idx] &= ~mask;
		// copy the bits from the last byte
		dest.bytes[idx] |= src.bytes[idx] & mask;
	}

	public Signature.PublicKey toSignatureKey() {
		return Signature.PublicKey.fromBytes(bytes);
	}

	public CryptoBox.PublicKey toEncryptionKey() {
		return CryptoBox.PublicKey.fromSignatureKey(toSignatureKey());
	}

	/**
	 * compares Keys according to their natural distance
	 *
	 * @param o the Id object to be compared.
	 */
	@Override
	public int compareTo(Id o) {
		return Arrays.compareUnsigned(bytes, o.bytes);
	}

	/**
	 * Compares a NodeId to this NodeId
	 *
	 * @param o The NodeId to compare to this NodeId
	 * @return boolean Whether the 2 NodeIds are equal
	 */
	@Override
	public boolean equals(Object o) {
		if (o instanceof Id) {
			Id id = (Id) o;
			return Arrays.equals(this.bytes, id.bytes);
		}
		return false;
	}

	@Override
	public int hashCode() {
		byte[] b = bytes;

		return (((b[0] ^ b[1] ^ b[2] ^ b[3] ^ b[4] ^ b[5] ^ b[6] ^ b[7]) & 0xff) << 24)
				| (((b[8] ^ b[9] ^ b[10] ^ b[11] ^ b[12] ^ b[13] ^ b[14] ^ b[15]) & 0xff) << 16)
				| (((b[16] ^ b[17] ^ b[18] ^ b[19] ^ b[20] ^ b[21] ^ b[22] ^ b[23]) & 0xff) << 8)
				| ((b[24] ^ b[25] ^ b[26] ^ b[27] ^ b[28] ^ b[29] ^ b[30] ^ b[31]) & 0xff);
	}

	/**
	 * @return The BigInteger representation of the key
	 */
	public BigInteger toInteger() {
		return new BigInteger(1, bytes);
	}

	/**
	 * @return The hex string representation of the key
	 */
	public String toHexString() {
		return "0x" + Hex.encode(bytes);
	}

	public String toBase58String() {
		return Base58.encode(bytes);
	}

	public String toBinaryString() {
		StringBuilder repr = new StringBuilder(SIZE + (SIZE >>> 2));

		for(int i = 0; i < SIZE; i++) {
			repr.append((bytes[i >>> 3] & (0x80 >> (i & 0x07))) != 0 ? '1' : '0');
			if ((i & 0x03) == 0x03) repr.append(' ');
		}
		return repr.toString();
	}

	@Override
	public String toString() {
		return this.toBase58String();
		//return this.toHexString();
	}
}
