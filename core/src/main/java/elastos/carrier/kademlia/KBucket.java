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
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import elastos.carrier.kademlia.messages.Message;
import elastos.carrier.utils.ThreadLocals;

/**
 * A KBucket is just a list of KBucketEntry objects.
 *
 * The list is sorted by time last seen : The first element is the least
 * recently seen, the last the most recently seen.
 *
 * This is a lock-free k-bucket implementation.
 *
 * The k-bucket is a Copy-on-Write k-bucket entry list.
 * Which all mutative operations(add, remove, ...) are implemented by
 * making a fresh copy of the underlying list.
 *
 * This is ordinarily too costly, but may be more efficient for routing
 * table reading. All mutative operations are synchronized, this means
 * only one writer at the same time.
 *
 * CAUTION:
 *   All methods name leading with _ means that method will WRITE the
 *   list, it can only be called inside the routing table's
 *   pipeline processing.

 */
public class KBucket implements Comparable<KBucket> {
	private final Prefix prefix;

	final boolean homeBucket;

	/**
	 * use {@link #insertOrRefresh}, {@link #sortedInsert} or {@link #removeEntry}
	 * to handle this<br>
	 * using copy-on-write semantics for this list, referencing it is safe if you
	 * make local copy
	 */
	private volatile List<KBucketEntry> entries;
	private volatile List<KBucketEntry> cache;

	private long lastRefresh;

	private static final Logger log = LoggerFactory.getLogger(KBucket.class);

	public KBucket(Prefix prefix, Predicate<Prefix> isHome) {
		assert (prefix != null);

		this.prefix = prefix;
		this.homeBucket = isHome.test(prefix);

		// using ArrayList here since reading/iterating is far more common than writing.
		entries = new ArrayList<>(Constants.MAX_ENTRIES_PER_BUCKET);
		cache = new ArrayList<>(Constants.MAX_ENTRIES_PER_BUCKET);
	}

	public KBucket(Prefix prefix) {
		this(prefix, x -> false);
	}

	public Prefix prefix() {
		return prefix;
	}

	public boolean isHomeBucket() {
		return homeBucket;
	}

	private List<KBucketEntry> getEntries() {
		return entries;
	}

	private void setEntries(List<KBucketEntry> entries) {
		this.entries = entries;
	}

	/**
	 * Get the number of entries.
	 *
	 * @return The number of entries in this Bucket
	 */
	public int size() {
		return getEntries().size();
	}

	/**
	 * @return the entries
	 */
	public List<KBucketEntry> entries() {
		return Collections.unmodifiableList(getEntries());
	}

	public KBucketEntry get(Id id, boolean includeCache) {
		for (KBucketEntry e : getEntries()) {
			if (e.getId().equals(id))
				return e;
		}

		if (includeCache) {
			for (KBucketEntry e : getEntries()) {
				if (e.getId().equals(id))
					return e;
			}
		}

		return null;
	}

	public Stream<KBucketEntry> stream() {
		return getEntries().stream();
	}

	private List<KBucketEntry> getCache() {
		return cache;
	}

	private void setCache(List<KBucketEntry> cache) {
		this.cache = cache;
	}

	public int cacheSize() {
		return getCache().size();
	}

	public List<KBucketEntry> cacheEntries() {
		return Collections.unmodifiableList(getCache());
	}

	public Stream<KBucketEntry> cacheStream() {
		return getCache().stream();
	}


	public boolean isFull() {
		return getEntries().size() >= Constants.MAX_ENTRIES_PER_BUCKET;
	}

	public KBucketEntry random() {
		List<KBucketEntry> entriesRef = getEntries();

		return entriesRef.isEmpty() ? null : entriesRef.get(ThreadLocals.random().nextInt(entriesRef.size()));
	}

	public KBucketEntry find(Id id, InetSocketAddress addr) {
		List<KBucketEntry> entriesRef = getEntries();
		for (int i = 0, n = entriesRef.size(); i < n; i++) {
			KBucketEntry entry = entriesRef.get(i);

			if (entry.getId().equals(id) || entry.getAddress().equals(addr))
				return entry;
		}

		return null;
	}

	public KBucketEntry findPingableCacheEntry() {
		List<KBucketEntry> cacheRef = getCache();
		for (int i = 0; i < cacheRef.size(); i++) {
			KBucketEntry entry = cacheRef.get(i);
			if (!entry.isNeverContacted())
				continue;
			return entry;
		}

		return null;
	}

	/**
	 * Check if the bucket needs to be refreshed
	 *
	 * @return true if it needs to be refreshed
	 */
	public boolean needsToBeRefreshed() {
		long now = System.currentTimeMillis();
		// TODO: timer may be somewhat redundant with needsPing logic
		return now - lastRefresh > Constants.BUCKET_REFRESH_INTERVAL
				&& getEntries().stream().anyMatch(KBucketEntry::needsPing);
	}

	boolean needsCachePing() {
		long now = System.currentTimeMillis();

		return now - lastRefresh > Constants.BUCKET_CACHE_PING_MIN_INTERVAL
				&& (stream().anyMatch(KBucketEntry::needsReplacement)
						|| getEntries().size() < Constants.MAX_ENTRIES_PER_BUCKET)
				&& cacheStream().anyMatch(KBucketEntry::isNeverContacted);
	}


	/**
	 * Resets the last modified for this Bucket
	 */
	public void updateRefreshTimer() {
		lastRefresh = System.currentTimeMillis();
	}


	/**
	 * Notify bucket of new incoming packet from a node, perform update or insert
	 * existing nodes where appropriate
	 *
	 * @param entry The entry to insert
	 */
	void _put(final KBucketEntry newEntry) {
		if (newEntry == null)
			return;

		List<KBucketEntry> entriesRef = getEntries();
		// find existing
		for (KBucketEntry existing : entriesRef) {
			// Update entry if existing
			if (existing.equals(newEntry)) {
				existing.merge(newEntry);
				return;
			}

			// Node id and address conflict
			// Log the conflict and keep the existing entry
			if (existing.match(newEntry)) {
				log.info("New node {} claims same ID or IP as  {}, might be impersonation attack or IP change. "
						+ "ignoring until old entry times out", newEntry, existing);
				return;
			}
		}

		// new entry
		if (newEntry.isReachable()) {
			if (entriesRef.size() < Constants.MAX_ENTRIES_PER_BUCKET) {
				// insert to the list if it still has room
				_update(null, newEntry);
				return;
			}

			// Try to replace the bad entry
			if (_replaceBadEntry(newEntry))
				return;

			// try to check the youngest entry
			KBucketEntry youngest = entriesRef.get(entriesRef.size() - 1);

			// older entries displace younger ones (although that kind of stuff should
			// probably go through #update directly)
			// entries with a 2.5times lower RTT than the current youngest one displace the
			// youngest. safety factor to prevent fibrilliation due to changing
			// RTT-estimates / to only replace when it's really worth it
			if (youngest.creationTime() > newEntry.creationTime()
					|| youngest.getRTT() >= (newEntry.getRTT() * 2.5)) {
				// Replace the youngest entry
				_update(youngest, newEntry);
				// it was a useful entry, see if we can use it to replace something questionable
				_insertIntoCache(youngest);
				return;
			}

		}

		// put the new entry to replacement
		_insertIntoCache(newEntry);
	}

	/**
	 * Tries to instert entry by replacing a bad entry.
	 *
	 * @param entry Entry to insert
	 * @return true if replace was successful
	 */
	private boolean _replaceBadEntry(KBucketEntry newEntry) {
		List<KBucketEntry> entriesRef = getEntries();
		for (int i = 0, n = entriesRef.size(); i < n; i++) {
			KBucketEntry e = entriesRef.get(i);
			if (e.needsReplacement()) {
				// bad one get rid of it
				_update(e, newEntry);
				return true;
			}
		}
		return false;
	}

	/**
	 * @param toRemove Entry to remove, if its bad
	 * @param force    if true entry will be removed regardless of its state
	 */
	void _removeIfBad(KBucketEntry toRemove, boolean force) {
		List<KBucketEntry> entriesRef = getEntries();
		if (entriesRef.contains(toRemove) && (force || toRemove.needsReplacement())) {
			KBucketEntry replacement = null;
			replacement = _pollVerifiedCacheEntry();

			// only remove if we have a replacement or really need to
			if (replacement != null || force)
				_update(toRemove, replacement);
		}
	}

	/**
	 * main list not full or contains entry that needs a replacement -> promote
	 * verified entry (if any) from cache
	 */
	void _promoteVerifiedCacheEntry() {
		List<KBucketEntry> entriesRef = getEntries();
		KBucketEntry toRemove = entriesRef.stream().filter(KBucketEntry::needsReplacement).findAny().orElse(null);
		if (toRemove == null && entriesRef.size() >= Constants.MAX_ENTRIES_PER_BUCKET)
			return;

		KBucketEntry replacement = _pollVerifiedCacheEntry();
		if (replacement != null)
			_update(toRemove, replacement);
	}

	// TODO: CHECKME!!!
	void _update(KBucketEntry toRefresh) {
		List<KBucketEntry> entriesRef = getEntries();
		for (int i = 0, n = entriesRef.size(); i < n; i++) {
			KBucketEntry entry = entriesRef.get(i);
			if (entry.equals(toRefresh)) {
				entry.merge(toRefresh);
				return;
			}
		}

		entriesRef = getCache();
		for (int i = 0, n = entriesRef.size(); i < n; i++) {
			KBucketEntry entry = entriesRef.get(i);
			if (entry.equals(toRefresh)) {
				entry.merge(toRefresh);
				return;
			}
		}
	}

	private void _update(KBucketEntry toRemove, KBucketEntry toInsert) {
		List<KBucketEntry> entriesRef = getEntries();

		if (toInsert != null && entriesRef.stream().anyMatch(toInsert::match))
			return;

		List<KBucketEntry> newEntries = new ArrayList<>(entriesRef);
		boolean removed = false;
		boolean added = false;

		// removal never violates ordering constraint, no checks required
		if (toRemove != null)
			removed = newEntries.remove(toRemove);

		if (toInsert != null) {
			int oldSize = newEntries.size();
			boolean wasFull = oldSize >= Constants.MAX_ENTRIES_PER_BUCKET;

			KBucketEntry youngest = oldSize > 0 ? newEntries.get(oldSize - 1) : null;
			boolean unorderedInsert = youngest != null && toInsert.creationTime() < youngest.creationTime();

			added = !wasFull || unorderedInsert;
			if (added) {
				newEntries.add(toInsert);
				KBucketEntry cacheEntry = _removeFromCache(toInsert);
				if (cacheEntry != null)
					toInsert.merge(cacheEntry);
			} else {
				_insertIntoCache(toInsert);
			}

			if (unorderedInsert)
				Collections.sort(newEntries, KBucketEntry.AGE_ORDER);

			if (wasFull && added)
				while (newEntries.size() > Constants.MAX_ENTRIES_PER_BUCKET)
					_insertIntoCache(newEntries.remove(newEntries.size() - 1));
		}

		// make changes visible
		if (added || removed)
			setEntries(newEntries);
	}

	private static int cacheOrder(KBucketEntry e1, KBucketEntry e2) {
		if (e1.getRTT() < e2.getRTT())
			return -1;

		if (e1.getRTT() > e2.getRTT())
			return 1;

		if (e1.lastSeen() > e2.lastSeen())
			return -1;

		if (e1.lastSeen() < e2.lastSeen())
			return 1;

		if (e1.creationTime() < e2.creationTime())
			return -1;

		if (e1.creationTime() > e2.creationTime())
			return 1;

		return 0;
	}

	void _insertIntoCache(KBucketEntry toInsert) {
		if (toInsert == null)
			return;

		// Check the existing, avoid the duplicated entry
		List<KBucketEntry> cacheRef = getCache();
		for (KBucketEntry existing : cacheRef) {
			// Update entry if existing
			if (existing.equals(toInsert)) {
				existing.merge(toInsert);
				return;
			}

			// Node id and address conflict
			// Log the conflict and keep the existing entry
			if (existing.match(toInsert)) {
				log.info("New node {} claims same ID or IP as  {}, might be impersonation attack or IP change. "
						+ "ignoring until old entry times out", toInsert, existing);
				return;
			}
		}

		List<KBucketEntry> newCache = new ArrayList<>(cacheRef);
		newCache.add(toInsert);
		if (newCache.size() > Constants.MAX_ENTRIES_PER_BUCKET) {
			Collections.sort(newCache, KBucket::cacheOrder);
			KBucketEntry removed = newCache.remove(newCache.size() - 1);
			if (removed.equals(toInsert))
				return;
		}

		setCache(newCache);
	}

	private KBucketEntry _pollVerifiedCacheEntry() {
		List<KBucketEntry> cacheRef = getCache();
		if (cacheRef.isEmpty())
			return null;

		List<KBucketEntry> newCache = new ArrayList<>(cacheRef);
		Collections.sort(newCache, KBucket::cacheOrder);
		KBucketEntry entry = newCache.remove(0);
		setCache(newCache);

		return entry;
	}

	private KBucketEntry _removeFromCache(KBucketEntry toRemove) {
		if (!getCache().contains(toRemove))
			return null;

		List<KBucketEntry> newCache = new ArrayList<>(getCache());
		for (int i = 0; i < newCache.size(); i++) {
			KBucketEntry entry = newCache.get(i);
			if (entry.equals(toRemove)) {
				newCache.remove(i);
				setCache(newCache);
				return entry;
			}
		}

		return null;
	}

	// TODO: CHECKME!!!
	void _notifyOfResponse(Message msg) {
		if (msg.getType() != Message.Type.RESPONSE || msg.getAssociatedCall() == null)
			return;

		List<KBucketEntry> entriesRef = getEntries();
		for (int i = 0, n = entriesRef.size(); i < n; i++) {
			KBucketEntry entry = entriesRef.get(i);

			// update last responded. insert will be invoked soon, thus we don't have to do
			// the move-to-end stuff
			if (entry.getId().equals(msg.getId())) {
				entry.signalResponse(msg.getAssociatedCall().getRTT());
				return;
			}
		}

		entriesRef = getCache();
		for (int i = 0, n = entriesRef.size(); i < n; i++) {
			KBucketEntry entry = entriesRef.get(i);

			// update last responded. insert will be invoked soon, thus we don't have to do
			// the move-to-end stuff
			if (entry.getId().equals(msg.getId())) {
				entry.signalResponse(msg.getAssociatedCall().getRTT());
				return;
			}
		}
	}

	/**
	 * A peer failed to respond
	 *
	 * @param addr Address of the peer
	 */
	void _onTimeout(Id id) {
		List<KBucketEntry> entriesRef = getEntries();
		for (int i = 0, n = entriesRef.size(); i < n; i++) {
			KBucketEntry e = entriesRef.get(i);
			if (e.getId().equals(id)) {
				e.signalRequestTimeout();
				// only removes the entry if it is bad
				_removeIfBad(e, false);
				return;
			}
		}

		entriesRef = getCache();
		for (int i = 0, n = entriesRef.size(); i < n; i++) {
			KBucketEntry e = entriesRef.get(i);
			if (e.getId().equals(id)) {
				e.signalRequestTimeout();
				return;
			}
		}
	}

	void _onSend(Id id) {
		List<KBucketEntry> entriesRef = getEntries();
		for (int i = 0, n = entriesRef.size(); i < n; i++) {
			KBucketEntry e = entriesRef.get(i);
			if (e.getId().equals(id)) {
				e.signalScheduledRequest();
				return;
			}
		}

		entriesRef = getCache();
		for (int i = 0, n = entriesRef.size(); i < n; i++) {
			KBucketEntry e = entriesRef.get(i);
			if (e.getId().equals(id)) {
				e.signalScheduledRequest();
				return;
			}
		}
	}

	@Override
	public boolean equals(Object o) {
		if (o == null)
			return false;

		if (this == o)
			return true;

		if (o instanceof KBucket) {
			KBucket bucket = (KBucket) o;
			return prefix.equals(bucket.prefix);
		}

		return false;
	}

	@Override
	public int compareTo(KBucket bucket) {
		return prefix.compareTo(bucket.prefix);
	}

	@Override
	public String toString() {
		StringBuilder repr = new StringBuilder(1024);
		repr.append("Prefix: ").append(prefix);
		if (isHomeBucket())
			repr.append(" [Home]");
		repr.append('\n');

		List<KBucketEntry> entries = getEntries();
		if (!entries.isEmpty()) {
			repr.append("  entries[").append(entries.size()).append("]:\n");
			repr.append(entries.stream().map(KBucketEntry::toString).collect(Collectors.joining(",\n    ", "    ", "\n")));
		}

		entries = getCache();
		if (!entries.isEmpty()) {
			repr.append("  cache[").append(entries.size()).append("]:\n");
			repr.append(entries.stream().map(KBucketEntry::toString).collect(Collectors.joining(",\n    ", "    ", "\n")));
		}

		return repr.toString();
	}
}
