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

import java.util.Collection;
import java.util.NavigableMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.stream.Stream;

import elastos.carrier.kademlia.Id;

public class ClosestSet {
	private final Id target;
	private final int capacity;
	private final NavigableMap<Id, CandidateNode> closest;

	int insertAttemptsSinceTailModification = 0;
	int insertAttemptsSinceHeadModification = 0;

	public ClosestSet(Id target, int capacity) {
		this.target = target;
		this.capacity = capacity;

		closest = new ConcurrentSkipListMap<>(new Id.Comparator(target));
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

	public boolean contains(Id id) {
		return closest.containsKey(id);
	}

	void add(CandidateNode cn) {
		synchronized (closest) {
			closest.put(cn.getId(), cn);

			if (closest.size() > capacity) {
				CandidateNode last = closest.lastEntry().getValue();
				closest.remove(last.getId());
				if (last == cn)
					insertAttemptsSinceTailModification++;
				else
					insertAttemptsSinceTailModification = 0;
			}

			if (closest.firstEntry().getValue() == cn) {
				insertAttemptsSinceHeadModification = 0;
			} else {
				insertAttemptsSinceHeadModification++;
			}
		}
	}

	public void removeCandidate(Id id) {
		if (closest.isEmpty())
			return;

		synchronized (closest) {
			closest.remove(id);
		}
	}

	public Stream<Id> ids() {
		return closest.keySet().stream();
	}

	public Collection<CandidateNode> getEntries() {
		return closest.values();
	}

	public Stream<CandidateNode> entriesStream() {
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

	public boolean eligible() {
		return reachedCapacity() && insertAttemptsSinceTailModification > capacity;
	}

	@Override
	public String toString() {
		String str = "ClosestNodes: " + closest.size() + " head:" + head().approxDistance(target) + " tail:" + tail().approxDistance(target);

		return str;
	}
}
