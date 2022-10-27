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

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;

import elastos.carrier.utils.Hex;

public class Prefix extends Id {

	/**
	 * identifies the first bit of a key that has to be equal to be considered as
	 * covered by this prefix -1 = prefix matches whole keyspace 0 = 0th bit must
	 * match 1 = ...
	 */
	int depth = -1;

	public static final Prefix WHOLE_KEYSPACE = new Prefix();

	public Prefix() {
		super();
	}

	public Prefix(Prefix p) {
		super(p);
		depth = p.depth;
	}

	public Prefix(Id id, int depth) {
		bitsCopy(id, this, depth);
		this.depth = depth;
	}

	public int getDepth() {
		return depth;
	}

	/**
	 *
	 * @param KeyTest to be checked
	 * @return true if this Prefix covers the provided key
	 */
	public boolean isPrefixOf(Id id) {
		return bitsEqual(this, id, depth);
	}

	public boolean isSplittable() {
		return depth < Id.SIZE - 1;
	}

	public Id first() {
		return new Id(this);
	}

	public Id last() {
		Id trailingBits = new Prefix(Id.MAX_ID, depth).distance(Id.MAX_ID);
		return this.distance(trailingBits);
	}

	public Prefix getParent() {
		if (depth == -1)
			return this;

		Prefix parent = new Prefix(this);
		int oldDepth = parent.depth--;
		// set last bit to zero
		byte[] b = parent.bytes();
		b[oldDepth >>> 3] &= ~(0x80 >> (oldDepth & 0x07));
		return parent;
	}

	// Get child
	public Prefix splitBranch(boolean highBranch) {
		Prefix branch = new Prefix(this);
		int branchDepth = ++branch.depth;
		if (highBranch)
			branch.bytes()[branchDepth / 8] |= 0x80 >> (branchDepth % 8);
		else
			branch.bytes()[branchDepth / 8] &= ~(0x80 >> (branchDepth % 8));

		return branch;
	}

	public boolean isSiblingOf(Prefix other) {
		if (depth != other.depth)
			return false;

		return bitsEqual(this, other, depth - 1);
	}

	/**
	 * Generates a random Id that has falls under this prefix
	 */
	public Id createRandomId() {
		// first generate a random one
		Id id = Id.random();

		bitsCopy(this, id, depth);

		return id;
	}

	public static Prefix getCommonPrefix(Collection<Id> ids) {
		if (ids.isEmpty())
			throw new IllegalArgumentException("ids cannot be empty");

		byte[] first = Collections.min(ids).bytes();
		byte[] last = Collections.max(ids).bytes();

		Prefix prefix = new Prefix();
		byte[] dest = prefix.bytes();

		int i = 0;
		for (; i < Id.BYTES && first[i] == last[i]; i++) {
			dest[i] = first[i];
			prefix.depth += 8;
		}

		if (i < Id.BYTES) {
			// first differing byte
			dest[i] = (byte) (first[i] & last[i]);
			for (int j = 0; j < 8; j++) {
				int mask = 0x80 >>> j;

		// find leftmost differing bit and then zero out all following bits
		if (((first[i] ^ last[i]) & mask) != 0) {
			dest[i] = (byte) (dest[i] & ~(0xFF >>> j));
			break;
		}

		prefix.depth++;
			}
		}

		return prefix;
	}

	@Override
	public boolean equals(Object o) {
		if (o instanceof Prefix) {
			Prefix p = (Prefix) o;

			if (this.depth != p.depth)
				return false;

			return Arrays.equals(this.bytes(), p.bytes());
		}
		return false;
	}


	@Override
	public String toBinaryString() {
		if (depth == -1)
			return "all";

		StringBuilder repr = new StringBuilder(depth + depth >>> 2 + 4);
		byte[] b = bytes();
		for (int i = 0; i <= depth; i++) {
			repr.append((b[i >>> 3] & (0x80 >> (i & 0x07))) != 0 ? '1' : '0');
			if ((i & 0x03) == 0x03) repr.append(' ');
		}
		repr.append("...");
		return repr.toString();
	}

	@Override
	public String toString() {
		if (depth == -1)
			return "all";

		return Hex.encode(bytes(), 0, (depth + 8) >>> 3) + "/" + depth;
	}
}
