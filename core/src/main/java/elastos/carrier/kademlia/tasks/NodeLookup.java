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
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import elastos.carrier.Id;
import elastos.carrier.NodeInfo;
import elastos.carrier.kademlia.Constants;
import elastos.carrier.kademlia.DHT;
import elastos.carrier.kademlia.KBucketEntry;
import elastos.carrier.kademlia.KClosestNodes;
import elastos.carrier.kademlia.RPCCall;
import elastos.carrier.kademlia.messages.FindNodeRequest;
import elastos.carrier.kademlia.messages.FindNodeResponse;
import elastos.carrier.kademlia.messages.Message;

public class NodeLookup extends LookupTask {
	private boolean bootstrap = false;
	private boolean wantToken = false;
	Consumer<NodeInfo> resultHandler;

	private static final Logger log = LoggerFactory.getLogger(NodeLookup.class);

	public NodeLookup(DHT dht, Id nodeId) {
		super(dht, nodeId);
	}

 	public void setBootstrap(boolean bootstrap) {
		this.bootstrap = bootstrap;
	}

	public boolean isBootstrap() {
		return bootstrap;
	}

 	public void setWantToken(boolean wantToken) {
		this.wantToken = wantToken;
	}

	public boolean doesWantToken() {
		return wantToken;
	}

	public void injectCandidates(Collection<NodeInfo> nodes) {
		addCandidates(nodes);
	}

	public void setResultHandler(Consumer<NodeInfo> resultHandler) {
		this.resultHandler = resultHandler;
	}

	@Override
	protected void prepare() {
		// if we're bootstrapping start from the bucket that has the greatest possible
		// distance from ourselves so we discover new things along the (longer) path
		Id knsTarget = bootstrap ? getTarget().distance(Id.MAX_ID) : getTarget();

		// delay the filling of the todo list until we actually start the task
		KClosestNodes kns = new KClosestNodes(getDHT(), knsTarget,
				Constants.MAX_ENTRIES_PER_BUCKET * 2, KBucketEntry::isEligibleForLocalLookup);
		kns.fill();
		addCandidates(kns.entries());
	}

	@Override
	protected void update() {
		while (canDoRequest()) {
			CandidateNode cn = getNextCandidate();
			if(cn == null) // no candidates
				return;

			// send a findNode to the node
			FindNodeRequest r = new FindNodeRequest(getTarget(), doesWantToken());
			r.setWant4(getDHT().getType() == DHT.Type.IPV4);
			r.setWant6(getDHT().getType() == DHT.Type.IPV6);

			sendCall(cn, r, (c) -> {
				cn.setSent();
			});
		}
	}

	@Override
	protected void callResponsed(RPCCall call, Message response) {
		super.callResponsed(call, response);

		if (!call.matchesId())
			return; // Ignore

		if (response.getType() != Message.Type.RESPONSE || response.getMethod() != Message.Method.FIND_NODE)
			return;

		FindNodeResponse r = (FindNodeResponse)response;
		// TODO: handle bouth IPv4 & IPv6 result
		List<NodeInfo> nodes = r.getNodes(getDHT().getType());
		if (nodes.isEmpty())
			return;

		addCandidates(nodes);

		if (resultHandler != null) {
			for (NodeInfo node : nodes) {
				if (node.getId().equals(getTarget()))
					resultHandler.accept(node);
			}
		}
	}

	@Override
	protected Logger getLogger() {
		return log;
	}
}

