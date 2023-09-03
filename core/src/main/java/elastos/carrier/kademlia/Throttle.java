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

import java.net.InetAddress;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public class Throttle {
	private Map<InetAddress, Integer> counter = new ConcurrentHashMap<>();
	private AtomicLong lastDecayTime = new AtomicLong(System.currentTimeMillis());

	private static final int LIMITS = 16;
	private static final int PERMITS_PER_SECOND = 4;

	protected Throttle() {
	}

	public boolean saturatingInc(InetAddress addr) {
		int count = counter.compute(addr, (key, old) -> old == null ? 1 : Math.min(old + 1, LIMITS));
		return count >= LIMITS;
	}

	public void saturatingDec(InetAddress addr) {
		counter.compute(addr, (key, old) -> old == null || old == 1 ? null : old - 1);
	}

	public void clear(InetAddress addr) {
		counter.remove(addr);
	}

	public boolean test(InetAddress addr) {
		return counter.getOrDefault(addr, 0) >= LIMITS;
	}

	public int estimateDeplayAndInc(InetAddress addr) {
		int count = counter.compute(addr, (key, old) -> old == null ? 1 : old + 1);
		int diff = count - LIMITS + 1; // TODO: CHECKME! +1 fixed that throttled by peer
		return Math.max(diff, 0) * 1000 / PERMITS_PER_SECOND;
	}

	public void decay() {
		long now = System.currentTimeMillis();
		long last = lastDecayTime.get();
		long interval = TimeUnit.MILLISECONDS.toSeconds(now - last);

		if (interval < 1)
			return;
		if (!lastDecayTime.compareAndSet(last, last + interval * 1000))
			return;

		int delta = (int)(interval * PERMITS_PER_SECOND);
		// minor optimization: delete first, then replace only what's left
		counter.entrySet().removeIf(entry -> entry.getValue() <= delta);
		counter.replaceAll((k, v) -> v - delta);
	}

	public static class Eanbled extends Throttle {
	}

	public static class Disabled extends Throttle {
		@Override
		public boolean saturatingInc(InetAddress addr) {
			return false;
		}

		@Override
		public void saturatingDec(InetAddress addr) {
		}

		@Override
		public void clear(InetAddress addr) {
		}

		@Override
		public boolean test(InetAddress addr) {
			return false;
		}

		@Override
		public int estimateDeplayAndInc(InetAddress addr) {
			return 0;
		}

		@Override
		public void decay() {
		}
	}
}
