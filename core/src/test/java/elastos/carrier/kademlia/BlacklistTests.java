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

import elastos.carrier.Id;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class BlacklistTests {
	private static Blacklist blacklist;
	private static InetSocketAddress bannedAddress1;
	private static InetSocketAddress bannedAddress2;
	private static InetSocketAddress bannedAddress3;
	private static Id bannedId1;
	private static Id bannedId2;

	@BeforeAll
	public static void setup() {
		// Observation: 5 minutes
		// Hits: 10 times
		// Ban: 5 minutes
		blacklist = new Blacklist(1, 10, 1);

		// Add the initial data
		bannedAddress1 = new InetSocketAddress("192.169.8.100", 39001);
		blacklist.observe(bannedAddress1, Id.random());
		blacklist.observe(bannedAddress1, Id.random());
		blacklist.observe(bannedAddress1, Id.random());
		blacklist.observe(bannedAddress1, Id.random());
		blacklist.observe(bannedAddress1, Id.random());
		blacklist.observe(bannedAddress1, Id.random());
		blacklist.observe(bannedAddress1, Id.random());
		blacklist.observe(bannedAddress1, Id.random());
		blacklist.observe(bannedAddress1, Id.random());
		blacklist.observe(bannedAddress1, Id.random());
		blacklist.observe(bannedAddress1, Id.random());
		assertFalse(blacklist.isBanned(bannedAddress1));
		assertEquals(12, blacklist.getObservationSize());
		assertEquals(0, blacklist.getBannedSize());
		blacklist.observe(bannedAddress1, Id.random());
		assertTrue(blacklist.isBanned(bannedAddress1));
		assertEquals(12, blacklist.getObservationSize());
		assertEquals(1, blacklist.getBannedSize());

		bannedAddress2 = new InetSocketAddress("192.169.8.200", 39001);
		blacklist.observe(bannedAddress2, Id.random());
		blacklist.observe(bannedAddress2, Id.random());
		blacklist.observe(bannedAddress2, Id.random());
		blacklist.observe(bannedAddress2, Id.random());
		blacklist.observe(bannedAddress2, Id.random());
		blacklist.observe(bannedAddress2, Id.random());
		blacklist.observe(bannedAddress2, Id.random());
		blacklist.observe(bannedAddress2, Id.random());
		blacklist.observe(bannedAddress2, Id.random());
		blacklist.observe(bannedAddress2, Id.random());
		blacklist.observe(bannedAddress2, Id.random());
		assertFalse(blacklist.isBanned(bannedAddress2));
		assertEquals(24, blacklist.getObservationSize());
		assertEquals(1, blacklist.getBannedSize());
		blacklist.observe(bannedAddress2, Id.random());
		assertTrue(blacklist.isBanned(bannedAddress2));
		assertEquals(24, blacklist.getObservationSize());
		assertEquals(2, blacklist.getBannedSize());

		bannedId1 = Id.random();
		blacklist.observe(new InetSocketAddress("192.168.1.1", 39001), bannedId1);
		blacklist.observe(new InetSocketAddress("192.168.1.2", 39001), bannedId1);
		blacklist.observe(new InetSocketAddress("192.168.1.3", 39001), bannedId1);
		blacklist.observe(new InetSocketAddress("192.168.1.4", 39001), bannedId1);
		blacklist.observe(new InetSocketAddress("192.168.1.5", 39001), bannedId1);
		blacklist.observe(new InetSocketAddress("192.168.1.6", 39001), bannedId1);
		blacklist.observe(new InetSocketAddress("192.168.1.7", 39001), bannedId1);
		blacklist.observe(new InetSocketAddress("192.168.1.8", 39001), bannedId1);
		blacklist.observe(new InetSocketAddress("192.168.1.9", 39001), bannedId1);
		blacklist.observe(new InetSocketAddress("192.168.1.10", 39001), bannedId1);
		blacklist.observe(new InetSocketAddress("192.168.1.11", 39001), bannedId1);
		assertFalse(blacklist.isBanned(bannedId1));
		assertEquals(36, blacklist.getObservationSize());
		assertEquals(2, blacklist.getBannedSize());
		blacklist.observe(new InetSocketAddress("192.168.1.12", 39001), bannedId1);
		assertTrue(blacklist.isBanned(bannedId1));
		assertEquals(36, blacklist.getObservationSize());
		assertEquals(3, blacklist.getBannedSize());

		bannedId2 = Id.random();
		blacklist.observe(new InetSocketAddress("192.168.1.1", 39002), bannedId2);
		blacklist.observe(new InetSocketAddress("192.168.1.2", 39002), bannedId2);
		blacklist.observe(new InetSocketAddress("192.168.1.3", 39002), bannedId2);
		blacklist.observe(new InetSocketAddress("192.168.1.4", 39002), bannedId2);
		blacklist.observe(new InetSocketAddress("192.168.1.5", 39002), bannedId2);
		blacklist.observe(new InetSocketAddress("192.168.1.6", 39002), bannedId2);
		blacklist.observe(new InetSocketAddress("192.168.1.7", 39002), bannedId2);
		blacklist.observe(new InetSocketAddress("192.168.1.8", 39002), bannedId2);
		blacklist.observe(new InetSocketAddress("192.168.1.9", 39002), bannedId2);
		blacklist.observe(new InetSocketAddress("192.168.1.10", 39002), bannedId2);
		blacklist.observe(new InetSocketAddress("192.168.1.11", 39002), bannedId2);
		assertFalse(blacklist.isBanned(bannedId2));
		assertEquals(48, blacklist.getObservationSize());
		assertEquals(3, blacklist.getBannedSize());
		blacklist.observe(new InetSocketAddress("192.168.1.12", 39002), bannedId2);
		assertTrue(blacklist.isBanned(bannedId2));
		assertEquals(48, blacklist.getObservationSize());
		assertEquals(4, blacklist.getBannedSize());

		bannedAddress3 = new InetSocketAddress("192.169.8.150", 39001);
		blacklist.observeInvalidMessage(bannedAddress3);
		blacklist.observeInvalidMessage(bannedAddress3);
		blacklist.observeInvalidMessage(bannedAddress3);
		blacklist.observeInvalidMessage(bannedAddress3);
		blacklist.observeInvalidMessage(bannedAddress3);
		blacklist.observeInvalidMessage(bannedAddress3);
		blacklist.observeInvalidMessage(bannedAddress3);
		blacklist.observeInvalidMessage(bannedAddress3);
		blacklist.observeInvalidMessage(bannedAddress3);
		blacklist.observeInvalidMessage(bannedAddress3);
		assertFalse(blacklist.isBanned(bannedAddress3));
		assertEquals(49, blacklist.getObservationSize());
		assertEquals(4, blacklist.getBannedSize());
		blacklist.observeInvalidMessage(bannedAddress3);
		assertTrue(blacklist.isBanned(bannedAddress3));
		assertEquals(48, blacklist.getObservationSize());
		assertEquals(5, blacklist.getBannedSize());
	}

	@Test
	@Order(100)
	@Timeout(value = 10, unit = TimeUnit.MINUTES)
	public void testObserve1() throws Exception {
		TimeUnit.MINUTES.sleep(2);
		assertEquals(0, blacklist.getObservationSize());
		assertEquals(0, blacklist.getBannedSize());
	}
}
