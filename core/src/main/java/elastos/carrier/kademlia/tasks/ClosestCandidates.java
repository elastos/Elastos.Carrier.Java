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

package elastos.carrier.kademlia.tasks;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.SortedMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import elastos.carrier.Id;
import elastos.carrier.NodeInfo;
import elastos.carrier.kademlia.Constants;

public class ClosestCandidates {
	private final Id target;
	private final int capacity;
	private final SortedMap<Id, CandidateNode> closest;
	private final Set<Object> dedup;

	public ClosestCandidates(Id target, int capacity) {
		this.target = target;
		this.capacity = capacity;

		closest = new ConcurrentSkipListMap<>(target::threeWayCompare);
		dedup = ConcurrentHashMap.newKeySet();
	}

	boolean reachedCapacity() {
		return closest.size() >= capacity;
	}

	public int size() {
		return closest.size();
	}

	public CandidateNode get(Id id) {
		return closest.get(id);
	}

	private int candidateOrder(CandidateNode cn1, CandidateNode cn2) {
		if (cn1.getPinged() < cn2.getPinged())
			return -1;
		else if (cn1.getPinged() > cn2.getPinged())
			return 1;
		else
			return target.threeWayCompare(cn1.getId(), cn2.getId());
	}

	public void add(Collection<? extends NodeInfo> nodes) {
		synchronized (closest) {
			for (NodeInfo node : nodes) {
				// Check existing node id
				if (!dedup.add(node.getId()))
					continue;

				// Check existing:
				// - develop env: existing socket address
				// - product env: existing ip address
				Object addr = Constants.DEVELOPMENT_ENVIRONMENT ?
						node.getAddress() : node.getAddress().getAddress();

				if (!dedup.add(addr))
					continue;

				CandidateNode cn = new CandidateNode(node);
				closest.put(cn.getId(), cn);
			}

			if (closest.size() > capacity) {
				List<Id> toRemove = closest.values().stream()
						.filter(cn -> !cn.isInFlight())
						.sorted(this::candidateOrder).skip(capacity)
						.map(cn -> cn.getId()).collect(Collectors.toList());
				for (Id id : toRemove)
					closest.remove(id);
			}
		}
	}

	public void remove(Predicate<CandidateNode> filter) {
		if (closest.isEmpty())
			return;

		synchronized (closest) {
			closest.entrySet().removeIf((e) -> {
				return filter.test(e.getValue());
			});
		}
	}

	public CandidateNode remove(Id id) {
		if (closest.isEmpty())
			return null;

		synchronized (closest) {
			return closest.remove(id);
		}
	}

	public CandidateNode next() {
		synchronized (closest) {
			return closest.values().stream()
					.filter(CandidateNode::isEligible)
					.sorted(this::candidateOrder)
					.findFirst().orElse(null);
		}
	}

	public Stream<Id> ids() {
		return closest.keySet().stream();
	}

	public Stream<CandidateNode> entries() {
		return closest.values().stream();
	}

	public Id tail() {
		if (closest.isEmpty())
			return target.distance(Id.MAX_ID);

		return closest.lastKey();
	}

	public Id head() {
		if (closest.isEmpty())
			return target.distance(Id.MAX_ID);

		return closest.firstKey();
	}

	@Override
	public String toString() {
		String str = "ClosestCandidates: " + closest.size() + " head:" + head().approxDistance(target) + " tail:" + tail().approxDistance(target);

		return str;
	}
}
