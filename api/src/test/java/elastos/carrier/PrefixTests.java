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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.Test;

public class PrefixTests {
	@Test
	public void testIsPrefixOf() {
		Id id = Id.of("0x4833af415161cbd0a3ef83aa59a55fbadc9bd520a886a8fa214a3d09b6676cb8");
		Prefix prefix = new Prefix(id, 64);

		assertTrue(prefix.isPrefixOf(id));
		assertTrue(prefix.isPrefixOf(Id.of("0x4833af415161cbd0f3ef83aa59a55fbadc9bd520a886a8fa214a3d09b6676cb8")));
		assertTrue(prefix.isPrefixOf(Id.of("0x4833af415161cbd0ffffffffffffffffffffffffffffffffffffffffffffffff")));
		assertFalse(prefix.isPrefixOf(Id.of("0x4833af415161cbd1f3ef83aa59a55fbadc9bd520a886a8fa214a3d09b6676cb8")));
	}

	@Test
	public void testIsSplitable() {
		for (int i = -1; i < Id.SIZE - 2; i++) {
			Id id = Id.random();
			Prefix p = new Prefix(id, i);
			assertTrue(p.isSplittable());
		}

		Id id = Id.random();
		Prefix p = new Prefix(id, Id.SIZE - 1);
		assertFalse(p.isSplittable());
	}

	@Test
	public void testIsSiblingOf() {
		Id id  = Id.of("0x4833af415161cbd0a3ef83aa59a55fbadc9bd520a886a8fa214a3d09b6676cb8");
		Id id2 = Id.of("0x4833af415161cbd0a3ef8faa59a55fbadc9bd520a886a8fa214a3d09b6676cb8");
		Id id3 = Id.of("0x4833af415161cbd0a3ef93aa59a55fbadc9bd520a886a8fa214a3d09b6676cb8");

		Prefix p = new Prefix(id, 84);
		Prefix p2 = new Prefix(id2, 84);
		Prefix p3 = new Prefix(id3, 84);

		assertTrue(p2.isSiblingOf(p));
		assertFalse(p3.isSiblingOf(p));
	}

	@Test
	public void testFirstAndLast() {
		for (int i = 0; i < Id.SIZE - 1; i++) {
			Id id = Id.random();

			Prefix p = new Prefix(id, i);

			Id first = p.first();
			Id last = p.last();

			assertTrue(p.isPrefixOf(first));
			assertTrue(p.isPrefixOf(last));
			assertFalse(p.isPrefixOf(last.add(Id.of("0x0000000000000000000000000000000000000000000000000000000000000001"))));
		}

	}

	@Test
	public void testGetParent() {
		Id id = Id.random();

		Prefix prefix = new Prefix(id, -1);
		assertEquals(prefix, prefix.getParent());

		for (int i = 0; i < Id.SIZE; i++) {
			id = Id.MAX_ID;

			prefix = new Prefix(id, i);
			Prefix parent = prefix.getParent();

			assertEquals(prefix.getDepth(), parent.getDepth() + 1);

			assertTrue(parent.isPrefixOf(prefix));
			assertTrue(Prefix.bitsEqual(prefix, parent, i - 1));
			assertFalse(Prefix.bitsEqual(prefix, parent, i));
		}
	}

	@Test
	public void testCreateRandomId() {
		for (int i = -1; i < Id.SIZE; i++) {
			Id id = Id.random();
			Prefix prefix = new Prefix(id, i);

			Id rid = prefix.createRandomId();

			assertTrue(prefix.isPrefixOf(id));
			assertTrue(prefix.isPrefixOf(rid));
			assertTrue(Id.bitsEqual(id, rid, i));

			System.out.format("%3d: %s\n", i, id);
			System.out.format("pre: %s\n", prefix);
			System.out.format("rid: %s\n\n", rid);
		}
	}

	@Test
	public void testSplitBranch() {
		for  (int i = -1; i < Id.SIZE - 1; i++) {
			Id id = Id.random();
			Prefix p = new Prefix(id, i);

			Prefix p1 = p.splitBranch(false);
			Prefix p2 = p.splitBranch(true);

			assertTrue(p.isPrefixOf(p1));
			assertTrue(p.isPrefixOf(p2));

			assertEquals(p, p1.getParent());
			assertEquals(p, p2.getParent());

			assertTrue(Id.bitsEqual(p1, p2, p.getDepth()));
			assertFalse(Id.bitsEqual(p1, p2, p.getDepth() + 1));
		}
	}

	@Test
	public void testGetCommonPrefix() {
		for (int depth = -1; depth < Id.SIZE; depth++) {
			Id id = Id.random();
			Prefix p = new Prefix(id, depth);

			int n = 128 + new Random().nextInt(128);
			List<Id> ids = new ArrayList<>(n);
			for (int i = 0; i < n; i++)
				ids.add(p.createRandomId());

			Prefix cp = Prefix.getCommonPrefix(ids);
			assertEquals(p, cp);
		}
	}
}
