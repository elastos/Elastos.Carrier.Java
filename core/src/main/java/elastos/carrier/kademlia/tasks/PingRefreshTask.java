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

package elastos.carrier.kademlia.tasks;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.EnumSet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import elastos.carrier.Id;
import elastos.carrier.kademlia.DHT;
import elastos.carrier.kademlia.KBucket;
import elastos.carrier.kademlia.KBucketEntry;
import elastos.carrier.kademlia.RPCCall;
import elastos.carrier.kademlia.messages.PingRequest;

public class PingRefreshTask extends Task {
	@SuppressWarnings("unused")
	private KBucket bucket;
	private Deque<KBucketEntry> todo;

	private boolean checkAll;
	private boolean removeOnTimeout;
	private boolean probeCache;

	private static final Logger log = LoggerFactory.getLogger(PingRefreshTask.class);

	public static enum Options {
		checkAll, removeOnTimeout, probeCache,
	}

	/**
	 * @param rpc
	 * @param node
	 * @param bucket         the bucket to refresh
	 * @param cleanOnTimeout if true Nodes that fail to respond are removed. should
	 *                       be false for normal use.
	 */
	public PingRefreshTask(DHT dht, KBucket bucket, EnumSet<Options> options) {
		super(dht);

		todo = new ArrayDeque<>();

		checkAll = options.contains(Options.checkAll);
		removeOnTimeout = options.contains(Options.removeOnTimeout);
		probeCache = options.contains(Options.probeCache);

		addBucket(bucket);
	}

	private void addBucket(KBucket bucket) {
		this.bucket = bucket;
		bucket.updateRefreshTimer();

		for (KBucketEntry e : bucket.entries()) {
			if (e.needsPing() || checkAll || removeOnTimeout)
				todo.add(e);
		}

		if (probeCache) {
			KBucketEntry entry = bucket.findPingableCacheEntry();
			if (entry != null)
				todo.add(entry);
		}

	}

	@Override
	protected void callTimeout(RPCCall call) {
		if (!removeOnTimeout)
			return;

		// CAUSION:
		// Should not use the original bucket object,
		// because the routing table is dynamic, maybe already changed.
		Id nodeId = call.getTargetId();
		log.debug("Removing invalid entry from cache.");
		getDHT().getRoutingTable().remove(nodeId);
	}

	@Override
	protected void update() {
		if (todo.isEmpty())
			return;

		while (!todo.isEmpty() && canDoRequest()) {
			KBucketEntry e = todo.peekFirst();

			if (!checkAll && !e.needsPing()) {
				// Entry already updated during the task running
				todo.remove(e);
				continue;
			}

			PingRequest pr = new PingRequest();
			boolean sent = sendCall(e, pr, c -> {
				todo.remove(e);
			});

			if (!sent) // in flight call queue full
				break;
		}
	}

	@Override
	protected boolean isDone() {
		return todo.isEmpty() && super.isDone();
	}

	@Override
	protected Logger getLogger() {
		return log;
	}
}
