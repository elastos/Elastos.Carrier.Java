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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigInteger;
import java.util.Random;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import elastos.carrier.utils.Base58;
import elastos.carrier.utils.Hex;

public class IdTests {
	@Test
	public void testOfHex() {
		String hexWithPrefix = "0x71e1b2ecdf528b623192f899d984c53f2b13508e21ccd53de5d7158672820636";
		Id id = Id.of(hexWithPrefix);
		assertEquals(hexWithPrefix, id.toHexString());

		String hexWithoutPrefix = "F897B6CB7969005520E6F6101EB5466D9859926A51653365E36C4A3C42E5DE6F";
		id = Id.ofHex(hexWithoutPrefix);
		assertEquals(hexWithoutPrefix.toLowerCase(), id.toHexString().substring(2));

		String hexWithPrefix2 = "0x71E1B2ECDF528B623192F899D984C53F2B13508E21CCD53DE5D71586728206";
		Exception e = assertThrows(IllegalArgumentException.class, () -> {
			Id.of(hexWithPrefix2);
		});
		assertEquals("Hex ID string should be 64 characters long.", e.getMessage());

		String hexWithoutPrefix2 = "f897b6cb7969005520e6f6101eb5466d9859926a51653365e36c4a3c42e5de";
		e = assertThrows(IllegalArgumentException.class, () -> {
			Id.ofHex(hexWithoutPrefix2);
		});
		assertEquals("Hex ID string should be 64 characters long.", e.getMessage());

		String hexWithPrefix3 = "0x71E1B2ECDR528B623192F899D984C53F2B13508E21CCD53DE5D7158672820636";
		e = assertThrows(IllegalArgumentException.class, () -> {
			Id.of(hexWithPrefix3);
		});
		assertEquals("Invalid hex byte 'DR' at index 10 of '0x71E1B2ECDR528B623192F899D984C53F2B13508E21CCD53DE5D7158672820636'", e.getMessage());

		String hexWithoutPrefix3 = "f897b6cb7969005520e6f6101ebx466d9859926a51653365e36c4a3c42e5de6f";
		e = assertThrows(IllegalArgumentException.class, () -> {
			Id.ofHex(hexWithoutPrefix3);
		});
		assertEquals("Invalid hex byte 'bx' at index 26 of 'f897b6cb7969005520e6f6101ebx466d9859926a51653365e36c4a3c42e5de6f'", e.getMessage());
	}

	@Test
	public void testOfBytes() {
		byte[] binId = new byte[Id.BYTES];
		new Random().nextBytes(binId);

		Id id = Id.of(binId);
		assertArrayEquals(binId, Hex.decode(id.toHexString().substring(2)));

		byte[] binId2 = new byte[20];
		new Random().nextBytes(binId2);
		Exception e = assertThrows(IllegalArgumentException.class, () -> {
			Id.of(binId2);
		});
		assertEquals("Binary id should be 32 bytes long.", e.getMessage());
	}

	@Test
	public void testOfId() {
		Id id1 = Id.random();
		Id id2 = Id.of(id1);

		assertEquals(id1.toHexString(), id2.toHexString());
		assertEquals(id1.toInteger(), id2.toInteger());
		assertEquals(id1, id2);
		assertTrue(id1.equals(id2));

		id2 = Id.random();
		assertFalse(id1.equals(id2));
	}

	@Test
	public void testOfBit() {
		for (int i = 0; i < Id.SIZE; i++) {
			Id id = Id.ofBit(i);
			assertTrue(id.toInteger().equals(BigInteger.ZERO.setBit(Id.SIZE - i - 1)));
		}
	}

	@Test
	public void testAdd() {
		Id id1 = Id.of("0x71e1b2ecdf528b623192f899d984c53f2b13508e21ccd53de5d7158672820636");
		Id id2 = Id.of("0xf897b6cb7969005520e6f6101eb5466d9859926a51653365e36c4a3c42e5de6f");

		Id id3 = id1.add(id2);
		assertEquals("0x6a7969b858bb8bb75279eea9f83a0bacc36ce2f8733208a3c9435fc2b567e4a5", id3.toHexString());

		for (int i = 0; i < 1000; i++) {
			id1 = Id.random();
			id2 = Id.random();

			id3 = id1.add(id2);
			BigInteger n = id1.toInteger().add(id2.toInteger()).clearBit(Id.SIZE);
			assertTrue(id3.toInteger().equals(n));
		}
	}

	@Test
	public void testDistance() {
		Id id1 = Id.of("0x00000000f528d6132c15787ed16f09b08a4e7de7e2c5d3838974711032cb7076");
		Id id2 = Id.of("0x00000000f0a8d6132c15787ed16f09b08a4e7de7e2c5d3838974711032cb7076");

		assertEquals("0x0000000005800000000000000000000000000000000000000000000000000000", Id.distance(id1, id2).toHexString());

		for (int i = 0; i < 1000; i++) {
			id1 = Id.random();
			id2 = Id.random();

			Id id3 = id1.distance(id2);
			BigInteger n = id1.toInteger().xor(id2.toInteger());
			assertTrue(id3.toInteger().equals(n));
		}
	}

	@Test
	public void testApproxDistance() {
		Id id1 = Id.of("0x00000000f528d6132c15787ed16f09b08a4e7de7e2c5d3838974711032cb7076");
		Id id2 = Id.of("0x00000000f0a8d6132c15787ed16f09b08a4e7de7e2c5d3838974711032cb7076");

		assertEquals(219, Id.approxDistance(id1, id2));

		for (int i = 0; i < 1000; i++) {
			id1 = Id.random();
			id2 = Id.random();

			int d = id1.approxDistance(id2);
			int n = id1.toInteger().xor(id2.toInteger()).bitLength();
			assertEquals(n, d);
		}
	}

	@Test
	public void testThreeWayCompare() {
		Id id = Id.of("0x4833af415161cbd0a3ef83aa59a55fbadc9bd520a886a8ca214a3d09b6676cb8");
		Id id1 = Id.of("0x4833af415161cbd0a3ef83aa59a55fbadc9bd520a886a8fa214a3d09b6676cb8");
		Id id2 = Id.of("0x4833af415161cbd0a3ef83aa59a55fbadc9bd520a885a8ca214a3d09b6676cb8");

		assertTrue(id.threeWayCompare(id1, id2) < 0);

		id1 = Id.of("0xf833af415161cbd0a3ef83aa59a55fbadc9bd520b886a8ca214a3d09b6676cb8");
		id2 = Id.of("0xf833af415161cbd0a3ef83aa59a55fbadc9bd520b886a8ca214a3d09b6676cb8");

		assertTrue(id.threeWayCompare(id1, id2) == 0);

		id1 = Id.of("0x4833af415161cbd0a3ef83aa59a55f1adc9bd520a886a8ca214a3d09b6676cb8");
		id2 = Id.of("0x4833af415161cbd0a3ef83aa59a55fcadc9bd520a886a8ca214a3d09b6676cb8");

		assertTrue(id.threeWayCompare(id1, id2) > 0);

		for (int i = 0; i < 1000; i++) {
			id1 = Id.random();
			id2 = Id.random();
			int d = id.threeWayCompare(id1, id2);

			Id d1 = id.distance(id1);
			Id d2 = id.distance(id2);
			int n = d1.toInteger().compareTo(d2.toInteger());

			assertEquals(n, d);
		}
	}

	@Test
	public void testBitsEqual() {
		Id id1 = Id.of("0x4833af415161cbd0a3ef83aa59a55fbadc9bd520a886a8fa214a3d09b6676cb8");
		Id id2 = Id.of("0x4833af415166cbd0a3ef83aa59a55fbadc9bd520a886a8fa214a3d09b6676cb8");

		for (int i = 0; i < 45; i++)
			assertTrue(Id.bitsEqual(id1, id2, i));

		for (int i = 45; i < Id.SIZE; i++)
			assertFalse(Id.bitsEqual(id1, id2, i));

		id2 = Id.of(id1);
		for (int i = 0; i < Id.SIZE; i++)
			assertTrue(Id.bitsEqual(id1, id2, i));

		id2 = Id.of("0x4833af415161cbd0a3ef83aa59a55fbadc9bd520a886a8fa214a3d09b6676cb9");

		for (int i = 0; i < Id.SIZE - 1; i++)
			assertTrue(Id.bitsEqual(id1, id2, i));

		assertFalse(Id.bitsEqual(id1, id2, Id.SIZE -1));
	}

	@Test
	public void testBitsCopy() {
		Id id1 = Id.of("0x4833af415161cbd0a3ef83aa59a55fbadc9bd520a886a8fa214a3d09b6676cb8");

		for (int i = 0; i > Id.SIZE; i++) {
			Id id2 = Id.random();

			Id.bitsCopy(id1, id2, i);
			assertTrue(Id.bitsEqual(id1, id2, i));
		}
	}

	@Disabled("Performance")
	@Test
	public void testToHexPerf() {
		System.out.println("Testing to hex performance...");

		Id id = Id.random();
		BigInteger bi = id.toInteger();

		assertEquals(padLeftZeros(bi.toString(16), 64), id.toHexString().substring(2));

		int loops = 1000000;

		long begin = System.currentTimeMillis();

		for (int i = 0; i < loops; i++)
			bi.toString(16);

		long end = System.currentTimeMillis();
		long duration = end - begin;
		System.out.println("BigInteger to hex: " + duration);

		begin = System.currentTimeMillis();

		for (int i = 0; i < loops; i++)
			id.toHexString();

		end = System.currentTimeMillis();
		duration = end - begin;
		System.out.println("Bytes to hex: " + duration + "\n");
	}

	@Disabled("Performance")
	@Test
	public void testXorPerf() {
		System.out.println("Testing XOR performance...");

		Id id1 = Id.random();
		Id id2 = Id.random();

		BigInteger bi1 = id1.toInteger();
		BigInteger bi2 = id2.toInteger();

		int loops = 10000000;

		long begin = System.currentTimeMillis();

		for (int i = 0; i < loops; i++)
			bi1.xor(bi2);

		long end = System.currentTimeMillis();
		long duration = end - begin;
		System.out.println("BigInteger XOR: " + duration);

		begin = System.currentTimeMillis();

		for (int i = 0; i < loops; i++)
			id1.distance(id2);

		end = System.currentTimeMillis();
		duration = end - begin;
		System.out.println("Bytes XOR: " + duration + "\n");
	}

	@Disabled("Performance")
	@Test
	public void testAddPerf() {
		System.out.println("Testing ADD performance...");

		Id id1 = Id.random();
		Id id2 = Id.random();

		BigInteger bi1 = id1.toInteger();
		BigInteger bi2 = id2.toInteger();

		int loops = 10000000;

		long begin = System.currentTimeMillis();

		for (int i = 0; i < loops; i++)
			bi1.add(bi2);

		long end = System.currentTimeMillis();
		long duration = end - begin;
		System.out.println("BigInteger ADD: " + duration);

		begin = System.currentTimeMillis();

		for (int i = 0; i < loops; i++)
			id1.add(id2);

		end = System.currentTimeMillis();
		duration = end - begin;
		System.out.println("Bytes ADD: " + duration + "\n");
	}

	@Disabled("Performance")
	@Test
	public void testMSBPerf() {
		System.out.println("Testing MSB performance...");

		Id id = Id.of(Hex.decode("0000000000000000000000000000000000000000000000000000000000000100"));

		assertEquals(Id.SIZE - id.toInteger().bitLength(), id.getLeadingZeros());

		int loops = 100000000;

		long begin = System.currentTimeMillis();

		for (int i = 0; i < loops; i++)
			id.toInteger().bitLength();

		long end = System.currentTimeMillis();
		long duration = end - begin;
		System.out.println("BigInteger MSB: " + duration);

		begin = System.currentTimeMillis();

		for (int i = 0; i < loops; i++)
			id.getLeadingZeros();

		end = System.currentTimeMillis();
		duration = end - begin;
		System.out.println("Bytes MSB: " + duration + "\n");
	}

	@Disabled("Performance")
	@Test
	public void testLSBPerf() {
		System.out.println("Testing LSB performance...");

		Id id = Id.of(Hex.decode("0010000000000000000000000000000000000000000000000000000000000000"));

		assertEquals(id.toInteger().getLowestSetBit(), id.getTrailingZeros());

		int loops = 100000000;

		long begin = System.currentTimeMillis();

		for (int i = 0; i < loops; i++)
			id.toInteger().getLowestSetBit();

		long end = System.currentTimeMillis();
		long duration = end - begin;
		System.out.println("BigInteger LSB: " + duration);

		begin = System.currentTimeMillis();

		for (int i = 0; i < loops; i++)
			id.getTrailingZeros();

		end = System.currentTimeMillis();
		duration = end - begin;
		System.out.println("Bytes LSB: " + duration + "\n");
	}

	@Disabled("Performance")
	@Test
	public void testToStringPerf() {
		System.out.println("Testing to hex performance...");

		Id id = Id.random();

		System.out.println("   Hex format: " + id.toHexString());
		System.out.println("Base58 format: " + id.toBase58String());

		assertEquals(id.toHexString().substring(2), Hex.encode(Base58.decode(id.toBase58String())));

		int loops = 1000000;

		long begin = System.currentTimeMillis();

		for (int i = 0; i < loops; i++)
			id.toString();

		long end = System.currentTimeMillis();
		long duration = end - begin;
		System.out.println("Hex: " + duration);

		begin = System.currentTimeMillis();

		for (int i = 0; i < loops; i++)
			id.toBase58String();

		end = System.currentTimeMillis();
		duration = end - begin;
		System.out.println("Base58: " + duration + "\n");
	}

	private static String padLeftZeros(String inputString, int length) {
	    if (inputString.length() >= length) {
	        return inputString;
	    }
	    StringBuilder sb = new StringBuilder();
	    while (sb.length() < length - inputString.length()) {
	        sb.append('0');
	    }
	    sb.append(inputString);

	    return sb.toString();
	}
}
