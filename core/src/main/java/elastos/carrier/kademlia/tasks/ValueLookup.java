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

import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import elastos.carrier.kademlia.Constants;
import elastos.carrier.kademlia.DHT;
import elastos.carrier.kademlia.Id;
import elastos.carrier.kademlia.KClosestNodes;
import elastos.carrier.kademlia.NodeInfo;
import elastos.carrier.kademlia.RPCCall;
import elastos.carrier.kademlia.Value;
import elastos.carrier.kademlia.messages.FindValueRequest;
import elastos.carrier.kademlia.messages.FindValueResponse;
import elastos.carrier.kademlia.messages.Message;
import elastos.carrier.utils.AddressUtils;

public class ValueLookup extends TargetedTask {
	int expectedSequence = -1;
	Consumer<Value> resultHandler;

	private static final Logger log = LoggerFactory.getLogger(ValueLookup.class);

	public ValueLookup(DHT dht, Id target) {
		super(dht, target);
	}

	public void setResultHandler(Consumer<Value> resultHandler) {
		this.resultHandler = resultHandler;
	}

	public void setSequence(int seq) {
		expectedSequence = seq;
	}

	@Override
	protected void prepare() {
		KClosestNodes kns = new KClosestNodes(getDHT(), getTarget(), Constants.MAX_ENTRIES_PER_BUCKET * 2);
		kns.fill();
		addCandidates(kns.entries());
	}

	@Override
	protected void update() {
		for (;;) {
			CandidateNode cn = getNextCandidate();
			if (cn == null)
				return;

			FindValueRequest r = new FindValueRequest(getTarget());

			r.setWant4(getDHT().getType() == DHT.Type.IPV4);
			r.setWant6(getDHT().getType() == DHT.Type.IPV6);
			if (expectedSequence != -1)
				r.setSequenceNumber(expectedSequence);

			boolean sent = !sendCall(cn, r, (c) -> {
				cn.setSent();
			});

			if (!sent)
				break;
		}
	}

	@Override
	protected void callResponsed(RPCCall call, Message response) {
		if (!call.matchesId())
			return; // Ignore

		if (response.getType() != Message.Type.RESPONSE || response.getMethod() != Message.Method.FIND_VALUE)
			return;

		FindValueResponse fvr = (FindValueResponse)response;

		if (fvr.getValue() != null) {
			Value value = Value.of(fvr);
			if (value.getId().equals(getTarget())) {
				log.error("Responsed value id {} mismatched with expected {}", value.getId(), getTarget());
				return;
			}

			if (value.isValid()) {
				log.error("Responsed value {} is invalid, signature mismatch", value.getId());
				return;
			}

			if (expectedSequence >= 0 && value.getSequenceNumber() < expectedSequence) {
				log.warn("Responsed value {} is outdated, sequence {}, expected {}",
						value.getId(), value.getSequenceNumber(), expectedSequence);
				return;
			}

			if (resultHandler != null)
				resultHandler.accept(value);
		} else {
			List<NodeInfo> nodes = fvr.getNodes(getDHT().getType());
			if (nodes == null)
				return;

			Set<NodeInfo> cands = nodes.stream().filter(e -> !AddressUtils.isBogon(e.getAddress()) && !getDHT().getNode().isLocalId(e.getId())).collect(Collectors.toSet());
			addCandidates(cands);
		}

		CandidateNode cn = removeCandidate(call.getTargetId());
		if (cn != null) {
			cn.setReplied();
			if (fvr.getToken() != 0) {
				cn.setToken(fvr.getToken());
				addClosest(cn);
			}
		}
	}

	@Override
	protected boolean isDone() {
		return getNextCandidate() == null && super.isDone();
	}

	@Override
	protected Logger getLogger() {
		return log;
	}
}
