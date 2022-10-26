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

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;

public class KClosestNodes {
	private DHT dht;
	private Id target;
	private List<KBucketEntry> entries;
	private int maxEntries;
	private Comparator<KBucketEntry> cmp;
	public Predicate<KBucketEntry> filter = KBucketEntry::eligibleForNodesList;

	/**
	 * Constructor sets the key to compare with
	 *
	 * @param key         The key to compare with
	 * @param maxEntries The maximum number of entries can be in the map
	 * @return
	 */
	public KClosestNodes(DHT dht, Id id, int maxEntries) {
		this.dht = dht;
		this.target = id;
		this.maxEntries = maxEntries;
		this.cmp = new KBucketEntry.DistanceOrder(id);
		this.entries = new ArrayList<>(maxEntries + Constants.MAX_ENTRIES_PER_BUCKET);
	}

	/**
	 * @return the Target key of the search
	 */
	public Id getTarget() {
		return target;
	}

	/**
	 * @return the number of entries
	 */
	public int size() {
		return entries.size();
	}

	private void insertEntries(KBucket bucket) {
		bucket.stream().filter(filter).forEach(entries::add);
	}

	private void shave() {
		int overshoot = entries.size() - maxEntries;

		if (overshoot <= 0)
			return;

		entries.sort(cmp);
		entries.subList(entries.size() - overshoot, entries.size()).clear();
	}

	public void fill() {
		fill(false);
	}

	public void fill(boolean includeSelf) {
		List<KBucket> buckets = dht.getRoutingTable().buckets();

		final int idx = RoutingTable.indexOf(buckets, target);
		KBucket bucket = buckets.get(idx);
		insertEntries(bucket);

		int low = idx;
		int high = idx;
		while (entries.size() < maxEntries) {
			KBucket lowBucket = null;
			KBucket highBucket = null;

			if (low > 0)
				lowBucket = buckets.get(low - 1);

			if (high < buckets.size() - 1)
				highBucket = buckets.get(high + 1);

			if (lowBucket == null && highBucket == null)
				break;

			if (lowBucket == null) {
				high++;
				insertEntries(highBucket);
			} else if (highBucket == null) {
				low--;
				insertEntries(lowBucket);
			} else {
				int dir = target.threeWayCompare(lowBucket.prefix().last(), highBucket.prefix().first());
				if (dir < 0) {
					low--;
					insertEntries(lowBucket);
				} else if (dir > 0) {
					high++;
					insertEntries(highBucket);
				} else {
					low--;
					high++;
					insertEntries(lowBucket);
					insertEntries(highBucket);
				}
			}
		}

		if (entries.size() < maxEntries) {
			for (NodeInfo n : dht.getNode().getConfig().bootstrapNodes()) {
				if (dht.getType().canUseSocketAddress(n.getAddress()))
					entries.add(new KBucketEntry(n));
			}
		}

		if (includeSelf && entries.size() < maxEntries) {
			InetSocketAddress sockAddr = dht.getServer().getAddress();
			entries.add(new KBucketEntry(dht.getNode().getId(), sockAddr));
		}

		shave();
	}

	public boolean isFull() {
		return entries.size() >= maxEntries;
	}

	/**
	 * @return a unmodifiable List of the entries
	 */
	public List<KBucketEntry> entries() {
		return Collections.unmodifiableList(entries);
	}

	public List<NodeInfo> asNodeList() {
		return new ArrayList<>(entries());
	}
}
