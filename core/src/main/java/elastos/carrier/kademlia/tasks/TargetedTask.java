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

import elastos.carrier.kademlia.Constants;
import elastos.carrier.kademlia.DHT;
import elastos.carrier.kademlia.Id;
import elastos.carrier.kademlia.NodeInfo;
import elastos.carrier.kademlia.RPCCall;

public abstract class TargetedTask extends Task {
	private Id target;
	private ClosestSet closest;
	private ClosestCandidates candidates;

	public TargetedTask(DHT dht, Id target) {
		super(dht);
		this.target = target;

		closest = new ClosestSet(target, Constants.MAX_ENTRIES_PER_BUCKET);
		candidates = new ClosestCandidates(target, Constants.MAX_ENTRIES_PER_BUCKET * 3);
	}

	public Id getTarget() {
		return target;
	}

	public int getCandidateSize() {
		return candidates.size();
	}

	protected CandidateNode getCandidate(Id id) {
		return candidates.get(id);
	}

	protected void addCandidates(Collection<? extends NodeInfo> nodes) {
		candidates.add(nodes);
	}

	protected CandidateNode removeCandidate(Id id) {
		return candidates.remove(id);
	}

	protected CandidateNode getNextCandidate() {
		return candidates.next();
	}

	protected void addClosest(CandidateNode cn) {
		closest.add(cn);
	}

	public ClosestSet getClosestSet() {
		return closest;
	}

	@Override
	protected boolean isDone() {
		return (getCandidateSize() == 0 ||
				(closest.eligible() && (target.threeWayCompare(closest.tail(), candidates.head()) <= 0))) &&
				super.isDone();
	}

	@Override
	protected void callError(RPCCall call) {
		candidates.remove(call.getTargetId());
	}

	@Override
	protected void callTimeout(RPCCall call) {
		// TODO: move to cached search nodes?
		candidates.remove(call.getTargetId());
	}
}
