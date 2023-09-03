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

package elastos.carrier.kademlia;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.InetSocketAddress;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import elastos.carrier.Id;
import elastos.carrier.kademlia.Blacklist.ObservationData;

@EnabledIfSystemProperty(named = "elastos.carrier.enviroment", matches = "development")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class BlacklistTests {
	// Observation: 2 minutes
	// Hits: 10 times
	// Ban: 2 minutes
	private static final long observationPeriod = 2;
	private static final long hits = 10;
	private static final long banDuration = 2;
	private static final long timeout = 5;

	private static Blacklist blacklist;

	private static InetSocketAddress bannedAddress1;
	private static InetSocketAddress bannedAddress2;
	private static InetSocketAddress bannedAddress3;
	private static Id bannedId1;
	private static Id bannedId2;

	@BeforeAll
	public static void setup() {
		blacklist = new Blacklist(observationPeriod, hits, banDuration);
	}

	@Test
	@Order(1)
	public void testBanAddress() {
		// Add the initial data
		bannedAddress1 = new InetSocketAddress("192.169.8.100", 39001);
		for (int i = 0; i <= hits; i++)
			blacklist.observe(bannedAddress1, Id.random());

		assertFalse(blacklist.isBanned(bannedAddress1));
		assertEquals(hits + 2, blacklist.getObservationSize());
		assertEquals(0, blacklist.getBannedSize());
		blacklist.observe(bannedAddress1, Id.random());
		assertTrue(blacklist.isBanned(bannedAddress1));
		assertEquals(hits + 2, blacklist.getObservationSize());
		assertEquals(1, blacklist.getBannedSize());

		bannedAddress2 = new InetSocketAddress("192.169.8.200", 39001);
		for (int i = 0; i <= hits; i++)
			blacklist.observe(bannedAddress2, Id.random());

		assertFalse(blacklist.isBanned(bannedAddress2));
		assertEquals(2 * (hits + 2), blacklist.getObservationSize());
		assertEquals(1, blacklist.getBannedSize());
		blacklist.observe(bannedAddress2, Id.random());
		assertTrue(blacklist.isBanned(bannedAddress2));
		assertEquals(2 * (hits + 2), blacklist.getObservationSize());
		assertEquals(2, blacklist.getBannedSize());
	}

	@Test
	@Order(2)
	public void testBanId() {
		bannedId1 = Id.random();
		for (int i = 0; i <= hits; i++) {
			String ip = "192.168.1." + ( i + 1);
			blacklist.observe(new InetSocketAddress(ip, 39001), bannedId1);
		}
		assertFalse(blacklist.isBanned(bannedId1));
		assertEquals(3 * (hits + 2), blacklist.getObservationSize());
		assertEquals(2, blacklist.getBannedSize());
		blacklist.observe(new InetSocketAddress("192.168.1.111", 39001), bannedId1);
		assertTrue(blacklist.isBanned(bannedId1));
		assertEquals(3 * (hits + 2), blacklist.getObservationSize());
		assertEquals(3, blacklist.getBannedSize());

		bannedId2 = Id.random();
		for (int i = 0; i <= hits; i++) {
			String ip = "192.168.1." + ( i + 1);
			blacklist.observe(new InetSocketAddress(ip, 39002), bannedId2);
		}

		assertFalse(blacklist.isBanned(bannedId2));
		assertEquals(4 * (hits + 2), blacklist.getObservationSize());
		assertEquals(3, blacklist.getBannedSize());
		blacklist.observe(new InetSocketAddress("192.168.1.222", 39002), bannedId2);
		assertTrue(blacklist.isBanned(bannedId2));
		assertEquals(4 * (hits + 2), blacklist.getObservationSize());
		assertEquals(4, blacklist.getBannedSize());
	}

	@Test
	@Order(3)
	public void testBanInvalidMessage() {
		bannedAddress3 = new InetSocketAddress("192.169.8.150", 39001);
		for (int i = 0; i < hits; i++)
			blacklist.observeInvalidMessage(bannedAddress3);

		assertFalse(blacklist.isBanned(bannedAddress3));
		assertEquals(4 * (hits + 2) + 1, blacklist.getObservationSize());
		assertEquals(4, blacklist.getBannedSize());
		blacklist.observeInvalidMessage(bannedAddress3);
		assertTrue(blacklist.isBanned(bannedAddress3));
		assertEquals(4 * (hits + 2), blacklist.getObservationSize());
		assertEquals(5, blacklist.getBannedSize());
	}

	@Test
	@Order(4)
	@Timeout(value = timeout, unit = TimeUnit.MINUTES)
	public void testExpire() throws Exception {
		for (int i = 0; i <= Math.max(observationPeriod, banDuration); i++) {
			assertTrue(blacklist.isBanned(bannedAddress1));
			assertTrue(blacklist.isBanned(bannedAddress3));
			assertTrue(blacklist.isBanned(bannedId1));

			TimeUnit.MINUTES.sleep(1);
		}

		assertTrue(blacklist.isBanned(bannedAddress1));
		assertTrue(blacklist.isBanned(bannedAddress3));
		assertTrue(blacklist.isBanned(bannedId1));

		assertFalse(blacklist.isBanned(bannedAddress2));
		assertFalse(blacklist.isBanned(bannedId2));
	}

	@Test
	@Order(5)
	@Timeout(value = timeout, unit = TimeUnit.MINUTES)
	public void testGetSize() throws Exception {
		TimeUnit.MINUTES.sleep(Math.max(observationPeriod, banDuration) + 1);

		assertFalse(blacklist.underObservation(bannedAddress1));
		assertFalse(blacklist.underObservation(bannedAddress2));
		assertFalse(blacklist.underObservation(bannedAddress3));
		assertFalse(blacklist.underObservation(bannedId1));
		assertFalse(blacklist.underObservation(bannedId2));

		assertFalse(blacklist.isBanned(bannedAddress1));
		assertFalse(blacklist.isBanned(bannedAddress2));
		assertFalse(blacklist.isBanned(bannedAddress3));
		assertFalse(blacklist.isBanned(bannedId1));
		assertFalse(blacklist.isBanned(bannedId2));

		assertEquals(0, blacklist.getObservationSize());
		assertEquals(0, blacklist.getBannedSize());
	}

	@Test
	@Order(6)
	@Timeout(value = timeout, unit = TimeUnit.MINUTES)
	public void testDecay() throws Exception {
		// Address
		InetSocketAddress addr = new InetSocketAddress("192.169.8.100", 39001);
		Id lastId = null;
		for (int i = 0; i <= hits; i++) {
			lastId = Id.random();
			blacklist.observe(addr, lastId);
		}

		assertFalse(blacklist.isBanned(addr));
		assertEquals(0, blacklist.getBannedSize());

		ObservationData od = blacklist.getObservation(addr);
		assertEquals(hits, od.hits);

		for (int i = 0; i <= hits; i++) {
			TimeUnit.MILLISECONDS.sleep(observationPeriod*60*1000 / hits);
			blacklist.observe(addr, lastId);

			od = blacklist.getObservation(addr);
			System.out.format("ObservationData[hits: %d, decay: %d]\n", od.hits, od.decay);
		}

		od = blacklist.getObservation(addr);
		assertEquals(hits-2, od.hits);

		// Id
		Id id = Id.random();
		InetSocketAddress lastAddr = null;
		for (int i = 0; i <= hits; i++) {
			String ip = "192.168.1." + ( i + 1);
			lastAddr = new InetSocketAddress(ip, 39001);
			blacklist.observe(lastAddr, id);
		}

		assertFalse(blacklist.isBanned(id));
		assertEquals(0, blacklist.getBannedSize());

		od = blacklist.getObservation(id);
		assertEquals(hits, od.hits);

		for (int i = 0; i <= hits; i++) {
			TimeUnit.MILLISECONDS.sleep(observationPeriod*60*1000 / hits);
			blacklist.observe(lastAddr, id);

			od = blacklist.getObservation(id);
			System.out.format("ObservationData[hits: %d, decay: %d]\n", od.hits, od.decay);
		}

		od = blacklist.getObservation(id);
		assertEquals(hits-2, od.hits);
	}
}
