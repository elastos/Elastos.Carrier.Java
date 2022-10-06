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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.dataformat.cbor.CBORGenerator;
import com.fasterxml.jackson.dataformat.cbor.databind.CBORMapper;

import elastos.carrier.kademlia.tasks.PingRefreshTask;
import elastos.carrier.kademlia.tasks.Task;
import elastos.carrier.utils.ThreadLocals;

/**
 * This is a lock-free routing table implementation.
 *
 * The table itself and all buckets are Copy-on-Write list.
 * Which all mutative operations(add, remove, ...) are implemented by
 * making a fresh copy of the underlying list.
 *
 * This is ordinarily too costly, but may be more efficient for routing
 * table reading. All mutative operations are synchronized, this means
 * only one writer at the same time.
 *
 * CAUTION:
 *   All methods name leading with _ means that method will WRITE the
 *   routing table, it can only be called inside the pipeline processing.
 */
public final class RoutingTable {
	private DHT dht;

	private volatile List<KBucket> buckets;

	private AtomicInteger writeLock;
	private ConcurrentLinkedQueue<Operation> pipeline;

	private long timeOfLastPingCheck;

	private Map<KBucket, Task> maintenanceTasks = new IdentityHashMap<>();

	private static final Logger log = LoggerFactory.getLogger(RoutingTable.class);

	private static class Operation {
		public static final int PUT = 1;
		public static final int REMOVE = 2;
		public static final int ON_SEND = 3;
		public static final int ON_TIMEOUT = 4;
		public static final int MAINTENANCE = 5;

		public final int code;
		public final Id id;
		public final KBucketEntry entry;

		private Operation(int code, Id id, KBucketEntry entry) {
			this.code = code;
			this.id = id;
			this.entry = entry;
		}

		public static Operation put(KBucketEntry entry) {
			return new Operation(PUT, null, entry);
		}

		public static Operation remove(Id id) {
			return new Operation(REMOVE, id, null);
		}

		public static Operation onSend(Id id) {
			return new Operation(ON_SEND, id, null);
		}

		public static Operation onTimeout(Id id) {
			return new Operation(ON_TIMEOUT, id, null);
		}

		public static Operation maintenance() {
			return new Operation(MAINTENANCE, null, null);
		}
	}

	public RoutingTable(DHT dht) {
		this.dht = dht;
		this.buckets = new ArrayList<>();
		this.writeLock = new AtomicInteger(0);
		this.pipeline = new ConcurrentLinkedQueue<>();
		buckets.add(new KBucket(new Prefix(), x -> true));
	}

	private List<KBucket> getBuckets() {
		return buckets;
	}

	private void setBuckets(List<KBucket> buckets) {
		this.buckets = buckets;
	}

	private DHT getDHT() {
		return dht;
	}

	public int size() {
		return getBuckets().size();
	}

	public KBucket get(int index) {
		return getBuckets().get(index);
	}

	public KBucketEntry getEntry(Id id, boolean includeCache) {
		return bucketOf(id).get(id, includeCache);
	}

	public List<KBucket> buckets() {
		return Collections.unmodifiableList(getBuckets());
	}

	public Stream<KBucket> stream() {
		return getBuckets().stream();
	}

	public int indexOf(Id id) {
		return indexOf(getBuckets(), id);
	}

	public KBucket bucketOf(Id id) {
		List<KBucket> bucketsRef = getBuckets();
		return bucketsRef.get(indexOf(bucketsRef, id));
	}

	static int indexOf(List<KBucket> bucketsRef, Id id) {
		int low = 0;
		int mid = 0;
		int high = bucketsRef.size() - 1;
		int cmp = 0;

		while (low <= high) {
			mid = (low + high) >>> 1;
			KBucket bucket = bucketsRef.get(mid);
			cmp = id.compareTo(bucket.prefix());
			if (cmp > 0)
				low = mid + 1;
			else if (cmp < 0)
				high = mid - 1;
			else
				return mid; // match the current bucket
		}

		return cmp < 0 ? mid - 1 : mid;
	}

	/**
	 * Get the number of entries in the routing table
	 *
	 * @return
	 */
	public int getNumBucketEntries() {
		return getBuckets().stream().flatMapToInt(b -> IntStream.of(b.size())).sum();
	}

	public int getNumCacheEntries() {
		return getBuckets().stream().flatMapToInt(b -> IntStream.of(b.cacheSize())).sum();
	}

	public KBucketEntry getRandomEntry() {
		List<KBucket> bucketsRef = getBuckets();

		int offset = ThreadLocals.random().nextInt(bucketsRef.size());
		return bucketsRef.get(offset).random();
	}

	private boolean isHomeBucket(Prefix p) {
		return p.isPrefixOf(getDHT().getNode().getId());
	}

	// TODO: CHECKME!!!
	void _refreshOnly(KBucketEntry toRefresh) {
		bucketOf(toRefresh.getId())._update(toRefresh);
	}

	public void put(KBucketEntry entry) {
		pipeline.add(Operation.put(entry));
		processPipeline();
	}

	public void remove(Id id) {
		pipeline.add(Operation.remove(id));
		processPipeline();
	}

	public void onSend(Id id) {
		pipeline.add(Operation.onSend(id));
		processPipeline();
	}

	public void onTimeout(Id id) {
		pipeline.add(Operation.onTimeout(id));
		processPipeline();
	}

	void maintenance() {
		pipeline.add(Operation.maintenance());
		processPipeline();
	}

	private void processPipeline() {
		if(!writeLock.compareAndSet(0, 1))
			return;

		// we are now the exclusive writer for the routing table
		while(true) {
			Operation op = pipeline.poll();
			if(op == null)
				break;

			switch (op.code) {
			case Operation.PUT:
				_put(op.entry);
				break;

			case Operation.REMOVE:
				_remove(op.id);
				break;

			case Operation.ON_SEND:
				_onSend(op.id);
				break;

			case Operation.ON_TIMEOUT:
				_onTimeout(op.id);
				break;

			case Operation.MAINTENANCE:
				_maintenance();
				break;
			}
		}

		writeLock.set(0);

		// check if we might have to pick it up again due to races
		// schedule async to avoid infinite stacks
		if(pipeline.peek() != null)
			getDHT().getNode().getScheduler().execute(this::processPipeline);
	}

	private void _put(KBucketEntry toInsert) {
		Id nodeId = toInsert.getId();
		KBucket bucket = bucketOf(nodeId);

		while (_needsSplit(bucket, toInsert)) {
			_split(bucket);
			bucket = bucketOf(nodeId);
		}

		bucket._put(toInsert);
	}

	private KBucketEntry _remove(Id id) {
		KBucket bucket = bucketOf(id);
		KBucketEntry toRemove = bucket.get(id, false);
		if (toRemove != null)
			bucket._removeIfBad(toRemove, true);

		return toRemove;
	}

	void _onTimeout(Id id) {
		KBucket bucket = bucketOf(id);
		bucket._onTimeout(id);
	}

	void _onSend(Id id) {
		KBucket bucket = bucketOf(id);
		bucket._onSend(id);
	}

	private boolean _needsSplit(KBucket bucket, KBucketEntry toInsert) {
		if (!bucket.isFull() || !toInsert.isReachable())
			return false;

		Prefix highBranch = bucket.prefix().splitBranch(true);
		return highBranch.isPrefixOf(toInsert.getId());
	}

	private void _modify(Collection<KBucket> toRemove, Collection<KBucket> toAdd) {
		List<KBucket> newBuckets = new ArrayList<>(getBuckets());
		if (toRemove != null && !toRemove.isEmpty())
			newBuckets.removeAll(toRemove);
		if (toAdd != null && !toAdd.isEmpty())
			newBuckets.addAll(toAdd);
		Collections.sort(newBuckets);
		setBuckets(newBuckets);
	}

	private void _split(KBucket bucket) {
		KBucket a = new KBucket(bucket.prefix().splitBranch(false), this::isHomeBucket);
		KBucket b = new KBucket(bucket.prefix().splitBranch(true), this::isHomeBucket);

		for (KBucketEntry e : bucket.entries()) {
			if (a.prefix().isPrefixOf(e.getId()))
				a._put(e);
			else
				b._put(e);
		}

		for (KBucketEntry e : bucket.cacheEntries()) {
			if (a.prefix().isPrefixOf(e.getId()))
				a._put(e);
			else
				b._put(e);
		}

		_modify(Arrays.asList(bucket), Arrays.asList(a, b));
	}

	private void _mergeBuckets() {
		int i = 0;

		// perform bucket merge operations where possible
		while (true) {
			i++;

			if (i < 1)
				continue;

			List<KBucket> bucketsRef = getBuckets();
			if (i >= bucketsRef.size())
				break;

			KBucket b1 = bucketsRef.get(i - 1);
			KBucket b2 = bucketsRef.get(i);

			if (b1.prefix().isSiblingOf(b2.prefix())) {
				int effectiveSize1 = (int) (b1.stream().filter(e -> !e.removableWithoutReplacement()).count()
						+ b1.cacheStream().filter(KBucketEntry::eligibleForNodesList).count());
				int effectiveSize2 = (int) (b2.stream().filter(e -> !e.removableWithoutReplacement()).count()
						+ b2.cacheStream().filter(KBucketEntry::eligibleForNodesList).count());

				if (effectiveSize1 + effectiveSize2 <= Constants.MAX_ENTRIES_PER_BUCKET) {
					KBucket newBucket = new KBucket(b1.prefix().getParent(), this::isHomeBucket);

					for (KBucketEntry e : b1.entries())
						newBucket._put(e);
					for (KBucketEntry e : b2.entries())
						newBucket._put(e);

					for (KBucketEntry e : b1.cacheEntries())
						newBucket._put(e);
					for (KBucketEntry e : b2.cacheEntries())
						newBucket._put(e);

					_modify(Arrays.asList(b1, b2), Arrays.asList(newBucket));

					i -= 2;
				}
			}
		}
	}

	/**
	 * Check if a buckets needs to be refreshed, and refresh if necessary.
	 */
	private void _maintenance() {
		long now = System.currentTimeMillis();

		// don't spam the checks if we're not receiving anything.
		// we don't want to cause too many stray packets somewhere in a network
		// if (!isRunning() && now - timeOfLastPingCheck < Constants.BOOTSTRAP_MIN_INTERVAL)
		if (now - timeOfLastPingCheck < Constants.ROUTING_TABLE_MAINTENANCE_INTERVAL)
			return;

		timeOfLastPingCheck = now;

		_mergeBuckets();

		List<KBucket> bucketsRef = getBuckets();
		for (KBucket bucket : bucketsRef) {
			boolean isHome = bucket.isHomeBucket();
			Id localId = getDHT().getNode().getId();

			List<KBucketEntry> entries = bucket.entries();
			//boolean wasFull = entries.size() >= Constants.MAX_ENTRIES_PER_BUCKET;
			for (KBucketEntry entry : entries) {
				// remove really old entries, ourselves and bootstrap nodes if the bucket is full
				// if (localIds.contains(entry.getID()) || (wasFull && dht.getBootStrapNodes().contains(entry.getAddress())))
				if (entry.getId().equals(localId)) {
					bucket._removeIfBad(entry, true);
					continue;
				}

				if (!bucket.prefix().isPrefixOf(entry.getId())) {
					bucket._removeIfBad(entry, true);
					put(entry);
				}
			}

			boolean refreshNeeded = bucket.needsToBeRefreshed();
			boolean replacementNeeded = bucket.needsCachePing() || (isHome && bucket.findPingableCacheEntry() != null);
			if (refreshNeeded || replacementNeeded)
				tryPingMaintenance(bucket, EnumSet.of(PingRefreshTask.Options.probeCache), "Refreshing Bucket - " + bucket.prefix());

			// only replace 1 bad entry with a replacement bucket entry at a time (per bucket)
			bucket._promoteVerifiedCacheEntry();
		}
	}

	void tryPingMaintenance(KBucket bucket, EnumSet<PingRefreshTask.Options> options, String name) {
		if (maintenanceTasks.containsKey(bucket))
			return;

		PingRefreshTask task = new PingRefreshTask(getDHT(), bucket, options);
		task.setName(name);
		if (maintenanceTasks.putIfAbsent(bucket, task) == null) {
			task.addListener(t -> maintenanceTasks.remove(bucket, task));
			getDHT().getTaskManager().add(task);
		}
	}

	/**
	 * Check if a buckets needs to be refreshed, and refresh if necesarry
	 *
	 * @param dh_table
	 */
	void fillBuckets() {
		List<KBucket> bucketsRef = getBuckets();

		for (int i = 0, n = bucketsRef.size(); i < n; i++) {
			KBucket bucket = bucketsRef.get(i);

			int num = bucket.size();

			// just try to fill partially populated buckets
			// not empty ones, they may arise as artifacts from deep splitting
			if (num > 0 && num < Constants.MAX_ENTRIES_PER_BUCKET) {
				Task task = getDHT().findNode(bucket.prefix().createRandomId(), null);
				task.setName("Filling Bucket - " + bucket.prefix());
			}
		}
	}

	/**
	 * Loads the routing table from a file
	 *
	 * @param file
	 * @param runWhenLoaded is executed when all load operations are finished
	 * @throws IOException
	 */
	void load(File file) {
		if (!file.exists() || !file.isFile())
			return;

		int totalEntries = 0;

		try (FileInputStream in = new FileInputStream(file)) {
			CBORMapper mapper = new CBORMapper();
			JsonNode root = mapper.readTree(in);
			long timestamp = root.get("timestamp").asLong();

			JsonNode nodes = root.get("entries");
			if (!nodes.isArray())
				throw new IOException("Invalid node entries");

			for (JsonNode node : nodes) {
				Map<String, Object> map = mapper.convertValue(node, new TypeReference<Map<String, Object>>(){});
				KBucketEntry entry = KBucketEntry.fromMap(map);
				if (entry != null) {
					_put(entry);
					totalEntries++;
				}
			}

			nodes = root.get("cache");
			if (!nodes.isArray())
				throw new IOException("Invalid node entries");

			for (JsonNode node : nodes) {
				Map<String, Object> map = mapper.convertValue(node, new TypeReference<Map<String, Object>>(){});
				KBucketEntry entry = KBucketEntry.fromMap(map);
				if (entry != null) {
					bucketOf(entry.getId())._insertIntoCache(entry);
					totalEntries++;
				}
			}

			log.info("Loaded {} entries from persistent file. it was {} min old.", totalEntries,
					((System.currentTimeMillis() - timestamp) / (60 * 1000)));
		} catch (IOException e) {
			log.error("Can not load the routing table.", e);
		}
	}

	/**
	 * Saves the routing table to a file
	 *
	 * @param file to save to
	 * @throws IOException
	 */
	void save(File file) throws IOException {
		if (file.isDirectory())
			return;

		if (this.getNumBucketEntries() == 0) {
			log.trace("Skip to save the empty routing table.");
			return;
		}

		Path tempFile = Files.createTempFile(file.getParentFile().toPath(), file.getName(), "-" + String.valueOf(System.currentTimeMillis()));
		try (FileOutputStream out = new FileOutputStream(tempFile.toFile())) {
			CBORGenerator gen = ThreadLocals.CBORFactory().createGenerator(out);
			gen.writeStartObject();

			gen.writeFieldName("timestamp");
			gen.writeNumber(System.currentTimeMillis());

			gen.writeFieldName("entries");
			gen.writeStartArray();
			for (KBucket bucket : getBuckets()) {
				for (KBucketEntry entry : bucket.entries()) {
					gen.writeStartObject();

					Map<String, Object> map = entry.toMap();
					for (Map.Entry<String, Object> kv : map.entrySet()) {
						gen.writeFieldName(kv.getKey());
						gen.writeObject(kv.getValue());
					}

					gen.writeEndObject();
				}
			}
			gen.writeEndArray();

			gen.writeFieldName("cache");
			gen.writeStartArray();
			for (KBucket bucket : getBuckets()) {
				for (KBucketEntry entry : bucket.cacheEntries()) {
					gen.writeStartObject();

					Map<String, Object> map = entry.toMap();
					for (Map.Entry<String, Object> kv : map.entrySet()) {
						gen.writeFieldName(kv.getKey());
						gen.writeObject(kv.getValue());
					}

					gen.writeEndObject();
				}
			}
			gen.writeEndArray();

			gen.writeEndObject();
			gen.close();
			out.close();
			Files.move(tempFile, file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
		} finally {
			// Force delete the tempFile if error occurred
			Files.deleteIfExists(tempFile);
		}
	}

	@Override
	public String toString() {
		StringBuilder repr = new StringBuilder(10240);
		List<KBucket> buckets = getBuckets();
		repr.append("buckets: ").append(buckets.size()).append(" / entries: ").append(getNumBucketEntries());
		repr.append('\n');
		for (KBucket bucket : buckets) {
			repr.append(bucket);
			repr.append('\n');
		}

		return repr.toString();
	}
}
