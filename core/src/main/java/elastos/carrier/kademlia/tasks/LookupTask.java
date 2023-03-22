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

import java.net.InetSocketAddress;
import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;

import elastos.carrier.Id;
import elastos.carrier.NodeInfo;
import elastos.carrier.kademlia.Constants;
import elastos.carrier.kademlia.DHT;
import elastos.carrier.kademlia.RPCCall;
import elastos.carrier.kademlia.messages.LookupResponse;
import elastos.carrier.kademlia.messages.Message;
import elastos.carrier.utils.AddressUtils;

public abstract class LookupTask extends Task {
	private Id target;
	private ClosestSet closest;
	private ClosestCandidates candidates;

	public LookupTask(DHT dht, Id target) {
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

	private boolean isBogonAddress(InetSocketAddress addr) {
		return Constants.DEVELOPMENT_ENVIRONMENT ?
				!AddressUtils.isAnyUnicast(addr.getAddress()) : AddressUtils.isBogon(addr);
	}

	protected void addCandidates(Collection<? extends NodeInfo> nodes) {
		Set<NodeInfo> cands = nodes.stream()
				.filter(n -> !isBogonAddress(n.getAddress()) && !getDHT().getNode().isLocalId(n.getId()))
				.filter(n -> !closest.contains(n.getId()))
				.collect(Collectors.toSet());

		if (!cands.isEmpty())
			candidates.add(cands);
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
		return super.isDone() &&
				(getCandidateSize() == 0 || (closest.isEligible() && (target.threeWayCompare(closest.tail(), candidates.head()) <= 0)));
	}

	@Override
	protected void callError(RPCCall call) {
		CandidateNode cn = (CandidateNode)call.getTarget();
		// Remove the candidate on error
		candidates.remove(cn.getId());
	}

	@Override
	protected void callTimeout(RPCCall call) {
		CandidateNode cn = (CandidateNode)call.getTarget();
		if (cn.isUnreachable())
			// Remove the candidate only when make sure it's unreachable
			candidates.remove(cn.getId());
		else
			// Clear the sent time-stamp and make it available again for the next retry
			cn.clearSent();
	}

	@Override
	protected void callResponsed(RPCCall call, Message response) {
		CandidateNode cn = removeCandidate(call.getTargetId());
		if (cn != null) {
			cn.setReplied();
			cn.setToken(((LookupResponse)response).getToken());
			addClosest(cn);
		}
	}

	protected String getStatus() {
		StringBuilder status = new StringBuilder();

		status.append(toString()).append('\n');
		status.append("Closest: \n");
		if (closest.size() > 0)
			status.append(closest.entriesStream().map(NodeInfo::toString).collect(Collectors.joining("\n    ", "    ", "\n")));
		else
			status.append("    <empty>\n");
		status.append("Candidates: \n");
		if (candidates.size() > 0)
			status.append(candidates.entries().map(NodeInfo::toString).collect(Collectors.joining("\n    ", "    ", "\n")));
		else
			status.append("    <empty>\n");

		return status.toString();
	}
}
