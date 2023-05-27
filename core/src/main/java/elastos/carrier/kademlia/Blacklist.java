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

import java.net.InetSocketAddress;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;

import elastos.carrier.Id;
import elastos.carrier.utils.AddressUtils;

public class Blacklist {
	private static final long OBSERVATION_PERIOD = 20; // minutes
	private static final int OBSERVATION_HITS = 30;
	private static final long BAN_DURATION = 60; // minutes

	private Cache<Object, ObservationData> observations;
	private Cache<Object, Object> banned;

	@SuppressWarnings("unused")
	private long observationPeriod;
	private long observationHits;
	@SuppressWarnings("unused")
	private long banDiration;

	private static final Object OBJ = new Object();
	private static final Logger log = LoggerFactory.getLogger(Blacklist.class);

	static class ObservationData {
		ObservationData(InetSocketAddress addr, Id id, int hits) {
			this.lastAddr = addr;
			this.lastId = id;
			this.hits = hits;
			this.decay = 0;
		}

		ObservationData(InetSocketAddress addr, Id id) {
			this(addr, id, 0);
		}

		ObservationData(InetSocketAddress addr) {
			this(addr, null, 1);
		}

		int hits;
		int decay;
		Id lastId;
		InetSocketAddress lastAddr;
		long lastHitTime;
	}

	public Blacklist() {
		this(OBSERVATION_PERIOD, OBSERVATION_HITS, BAN_DURATION);
	}

	public Blacklist(long observationPeriod, long observationHits, long banDiration) {
		this.observationPeriod = observationPeriod;
		this.observationHits = observationHits;
		this.banDiration = banDiration;

		observations = CacheBuilder.newBuilder()
			.expireAfterAccess(observationPeriod, TimeUnit.MINUTES)
			.build();

		banned = CacheBuilder.newBuilder()
			.expireAfterAccess(banDiration, TimeUnit.MINUTES)
			.build();
	}

	void ban(InetSocketAddress addr) {
		banned.put(addr, OBJ);
		log.info("Banned address {}", AddressUtils.toString(addr));
	}

	void ban(Id id) {
		banned.put(id, OBJ);
		log.info("Banned node {}", id);
	}

	void observe(InetSocketAddress addr, Id id) {
		observations.asMap().compute(addr, (a, o) -> {
			if (o == null)
				return new ObservationData(addr, id);

			if (o.lastId == null || !o.lastId.equals(id)) {
				o.lastId = id;
				o.decay = 0;
				o.lastHitTime = System.currentTimeMillis();
				if (++o.hits > observationHits) {
					ban(addr);
					return null;
				}
			} else {
				if (o.hits > 0) {
					if (++o.decay >= (observationHits >> 1)) {
						long duration = System.currentTimeMillis() - o.lastHitTime;
						if (duration >= TimeUnit.MINUTES.toMillis((observationPeriod >> 1))) {
							--o.hits;
							o.decay = 0;
						}
					}
				}
			}

			return o;
		});

		observations.asMap().compute(id, (i, o) -> {
			if (o == null)
				return new ObservationData(addr, id);

			if (o.lastAddr == null || !o.lastAddr.equals(addr)) {
				o.lastAddr = addr;
				o.decay = 0;
				o.lastHitTime = System.currentTimeMillis();
				if (++o.hits > observationHits) {
					ban(id);
					return null;
				}
			} else {
				if (o.hits > 0) {
					if (++o.decay >= (observationHits >> 1)) {
						long duration = System.currentTimeMillis() - o.lastHitTime;
						if (duration >= TimeUnit.MINUTES.toMillis((observationPeriod >> 1))) {
							--o.hits;
							o.decay = 0;
						}
					}
				}
			}

			return o;
		});
	}

	void observeInvalidMessage(InetSocketAddress addr) {
		observations.asMap().compute(addr, (a, o) -> {
			if (o == null)
				return new ObservationData(addr);

			if (++o.hits > observationHits) {
				ban(addr);
				return null;
			}

			return o;
		});
	}

	public boolean underObservation(InetSocketAddress addr) {
		return observations.getIfPresent(addr) != null;
	}

	public boolean underObservation(Id id) {
		return observations.getIfPresent(id) != null;
	}

	ObservationData getObservation(InetSocketAddress addr) {
		return observations.getIfPresent(addr);
	}

	ObservationData getObservation(Id id) {
		return observations.getIfPresent(id);
	}

	public long getObservationSize() {
		observations.cleanUp();
		return observations.size();
	}

	public boolean isBanned(InetSocketAddress addr, Id id) {
		return (banned.getIfPresent(addr) != null || banned.getIfPresent(id) != null);
	}

	public boolean isBanned(InetSocketAddress addr) {
		return banned.getIfPresent(addr) != null;
	}

	public boolean isBanned(Id id) {
		return banned.getIfPresent(id) != null;
	}

	public long getBannedSize() {
		banned.cleanUp();
		return banned.size();
	}
}
