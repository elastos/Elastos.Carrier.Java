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

import java.math.RoundingMode;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.text.DecimalFormat;
import java.time.Duration;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;

import elastos.carrier.Id;
import elastos.carrier.NodeInfo;
import elastos.carrier.Version;
import elastos.carrier.utils.AddressUtils;

/**
 * Entry in a KBucket, it basically contains an IP address of a node, the UDP
 * port of the node and a node id.
 */
public class KBucketEntry extends NodeInfo {
	private static final double RTT_EMA_WEIGHT = 0.3;

	/**
	 * ascending order for last seen, i.e. the last value will be the least recently
	 * seen one
	 */
	public static final Comparator<KBucketEntry> LAST_SEEN_ORDER = Comparator.comparingLong(KBucketEntry::lastSeen);

	/**
	 * ascending order for timeCreated, i.e. the first value will be the oldest
	 */
	public static final Comparator<KBucketEntry> AGE_ORDER = Comparator.comparingLong(KBucketEntry::creationTime);

	/**
	 * same order as the Key class, based on the Entrie's nodeID
	 */
	public static final Comparator<KBucketEntry> KEY_ORDER = Comparator.comparing(KBucketEntry::getId);

	/**
	 * -1 = never queried / learned about it from incoming requests 0 = last query
	 * was a success > 0 = query failed
	 */
	private ExponentialWeightendMovingAverage avgRTT = new ExponentialWeightendMovingAverage(RTT_EMA_WEIGHT);

	private long created;
	private long lastSeen;
	private long lastSend;

	private boolean reachable = false;
	private int failedRequests;

	public static final class DistanceOrder implements Comparator<KBucketEntry> {
		final Id target;

		public DistanceOrder(Id target) {
			this.target = target;
		}

		@Override
		public int compare(KBucketEntry o1, KBucketEntry o2) {
			return target.threeWayCompare(o1.getId(), o2.getId());
		}
	}

	/**
	 * Constructor, set the ip, port and key
	 *
	 * @param addr socket address
	 * @param id   ID of node
	 */
	public KBucketEntry(Id id, InetSocketAddress addr) {
		super(id, addr);
		created = System.currentTimeMillis();
		lastSeen = created;
		failedRequests = 0;
	}

	public KBucketEntry(NodeInfo node) {
		this(node.getId(), node.getAddress());
	}

	public long creationTime() {
		return created;
	}

	public long lastSeen() {
		return lastSeen;
	}

	public long lastSend() {
		return lastSend;
	}

	public int failedRequests() {
		return failedRequests;
	}

	public boolean isReachable() {
		return reachable;
	}

	public boolean isNeverContacted() {
		return lastSend == 0;
	}

	public boolean isEligibleForNodesList() {
		// 1 timeout can occasionally happen. should be fine to hand it out as long as
		// we've verified it at least once
		return isReachable() && failedRequests < 3;
	}

	public boolean isEligibleForLocalLookup() {
		// allow implicit initial ping during lookups
		// TODO: make this work now that we don't keep unverified entries in the main bucket
		//if ((!isReachable() && failedRequests > 0) || failedRequests > 3)
		// 	return false;
		//return true;

		return (isReachable() && failedRequests <= 3) || failedRequests <= 0;
	}

	private boolean withinBackoffWindow(long now) {
		int backoff = Constants.KBUCKET_PING_BACKOFF_BASE_INTERVAL <<
				Math.min(Constants.KBUCKET_MAX_TIMEOUTS, Math.max(0, failedRequests() - 1));

		return failedRequests != 0 && now - lastSend < backoff;
	}

	public long backoffWindowEnd() {
		if (failedRequests == 0 || lastSend <= 0)
			return -1L;

		int backoff = Constants.KBUCKET_PING_BACKOFF_BASE_INTERVAL <<
				Math.min(Constants.KBUCKET_MAX_TIMEOUTS, Math.max(0, failedRequests() - 1));

		return lastSend + backoff;
	}

	public boolean withinBackoffWindow() {
		return withinBackoffWindow(System.currentTimeMillis());
	}

	public boolean needsPing() {
		long now = System.currentTimeMillis();

		// don't ping if recently seen to allow NAT entries to time out
		// see https://arxiv.org/pdf/1605.05606v1.pdf for numbers
		// and do exponential backoff after failures to reduce traffic
		if (now - lastSeen < 30 * 1000 || withinBackoffWindow(now))
			return false;

		return failedRequests != 0 || now - lastSeen > Constants.KBUCKET_OLD_AND_STALE_TIME;
	}

	// old entries, e.g. from routing table reload
	public boolean oldAndStale() {
		return failedRequests > Constants.KBUCKET_OLD_AND_STALE_TIMEOUTS &&
				System.currentTimeMillis() - lastSeen > Constants.KBUCKET_OLD_AND_STALE_TIME;
	}

	public boolean removableWithoutReplacement() {
		// some non-reachable nodes may contact us repeatedly, bumping the last seen
		// counter. they might be interesting to keep around so we can keep track of the
		// backoff interval to not waste pings on them
		// but things we haven't heard from in a while can be discarded

		boolean seenSinceLastPing = lastSeen > lastSend;

		return failedRequests > Constants.KBUCKET_MAX_TIMEOUTS && !seenSinceLastPing;
	}

	protected boolean needsReplacement() {
		return (failedRequests > 1 && !isReachable()) ||
				(failedRequests > Constants.KBUCKET_MAX_TIMEOUTS && oldAndStale());
	}

	protected void merge(KBucketEntry other) {
		if (!this.equals(other) || this == other)
			return;

		created = Math.min(created, other.creationTime());
		lastSeen = Math.max(lastSeen, other.lastSeen());
		lastSend = Math.max(lastSend, other.lastSend());
		if (other.isReachable())
			setReachable(true);
		if (!Double.isNaN(other.avgRTT.getAverage()))
			avgRTT.updateAverage(other.avgRTT.getAverage());
		if (other.failedRequests() > 0)
			failedRequests = Math.min(failedRequests, other.failedRequests());
	}

	public int getRTT() {
		return (int) avgRTT.getAverage(Constants.RPC_CALL_TIMEOUT_MAX);
	}

	/**
	 *
	 * @param rtt > 0 in ms. -1 if unknown
	 */
	public void signalResponse(long rtt) {
		lastSeen = System.currentTimeMillis();
		failedRequests = 0;
		reachable = true;
		if (rtt > 0)
			avgRTT.updateAverage(rtt);
	}

	public void mergeRequestTime(long requestSent) {
		lastSend = Math.max(lastSend, requestSent);
	}

	public void signalRequest() {
		lastSend = System.currentTimeMillis();
	}

	/**
	 * Should be called to signal that a request to this peer has timed out;
	 */
	public void signalRequestTimeout() {
		if (failedRequests <= 0)
			failedRequests = 1;
		else
			failedRequests++;
	}

	void setReachable(boolean reachable) {
		this.reachable = reachable;
	}

	public boolean match(KBucketEntry entry) {
		if (entry == null)
			return false;

		return super.match(entry);
	}

	// for routing table persistence
	Map<String, Object> toMap() {
		Map<String, Object> map = new LinkedHashMap<>();

		map.put("id", getId().bytes());
		map.put("addr", getInetAddress().getAddress());
		map.put("port", getPort());
		map.put("created", created);
		map.put("lastSeen", lastSeen);
		map.put("lastSend", lastSend);
		map.put("failedRequests", failedRequests());
		map.put("reachable", isReachable());
		map.put("version", getVersion());

		return map;
	}

	static KBucketEntry fromMap(Map<String, Object> map) {
		try {
			Id id = Id.of((byte[])map.get("id"));
			InetAddress addr = InetAddress.getByAddress((byte[])map.get("addr"));
			int port = (int)map.get("port");

			KBucketEntry entry = new KBucketEntry(id, new InetSocketAddress(addr, port));

			entry.created = (long)map.get("created");
			entry.lastSeen = (long)map.get("lastSeen");
			entry.lastSend = (long)map.get("lastSend");
			entry.failedRequests = (int)map.get("failedRequests");
			entry.reachable = (boolean)map.get("reachable");
			entry.setVersion((int)map.get("version"));

			return entry;
		} catch (Exception e) {
			return null;
		}
	}

	@Override
	public boolean equals(Object o) {
		if (o == null)
			return false;

		if (this == o)
			return true;

		if (o instanceof KBucketEntry) {
			KBucketEntry entry = (KBucketEntry) o;
			return super.equals(entry);
		}

		return false;
	}

	@Override
	public int hashCode() {
		return getId().hashCode() + 0x6b; // + 'k'
	}

	@Override
	public String toString() {
		long now = System.currentTimeMillis();
		StringBuilder repr = new StringBuilder(80);
		repr.append(getId()).append('@').append(AddressUtils.toString(getAddress()));
		repr.append(";seen:").append(Duration.ofMillis(now - lastSeen));
		repr.append(";age:").append(Duration.ofMillis(now - created));

		if (lastSend > 0)
			repr.append(";sent:" + Duration.ofMillis(now - lastSend));
		if (failedRequests != 0)
			repr.append(";fail:" + failedRequests);
		if (reachable)
			repr.append(";reachable");

		double rtt = avgRTT.getAverage();
		if (!Double.isNaN(rtt)) {
			DecimalFormat df = new DecimalFormat();
			df.setMaximumFractionDigits(2);
			df.setRoundingMode(RoundingMode.HALF_UP);
			repr.append(";rtt:").append(df.format(rtt));
		}

		if (getVersion() != 0)
			repr.append(";ver:").append(Version.toString(getVersion()));

		return repr.toString();
	}
}
