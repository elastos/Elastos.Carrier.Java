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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

import elastos.carrier.Id;
import elastos.carrier.NodeInfo;
import elastos.carrier.kademlia.messages.Message;
import elastos.carrier.kademlia.tasks.CandidateNode;

public class RPCCall {
	private Message request;
	private Message response;

	private NodeInfo target;
	private boolean sourceWasKnownReachable;

	private long sentTime = -1;
	private long responseTime = -1;
	private long expectedRTT = -1;
	private boolean responseSocketMismatch;
	private State state = State.UNSENT;
	ScheduledExecutorService scheduler;
	private ScheduledFuture<?> timeoutTimer;
	private List<RPCCallListener> listeners;

	public static enum State {
		UNSENT,
		SENT,
		STALLED,
		TIMEOUT,
		CANCELED,
		ERROR,
		RESPONDED
	}

	public RPCCall(NodeInfo target, Message request) {
		assert(request != null) : "null request";
		assert(request.getType() == Message.Type.REQUEST) : "Invalid request message";

		this.target = target;
		this.request = request;
		this.listeners = new ArrayList<>(8);

		request.setRemote(target.getAddress());
		if (target instanceof KBucketEntry) {
			KBucketEntry e = (KBucketEntry)target;
			sourceWasKnownReachable = e.isReachable();
		} else if (target instanceof CandidateNode) {
			CandidateNode n = (CandidateNode)target;
			sourceWasKnownReachable = n.isReachable();
		}
	}

	public boolean knownReachableAtCreationTime() {
		return sourceWasKnownReachable;
	}

	public RPCCall setExpectedRTT(long rtt) {
		this.expectedRTT = rtt;
		return this;
	}

	public long getExpectedRTT() {
		return expectedRTT;
	}

	public Id getTargetId() {
		return target.getId();
	}

	public NodeInfo getTarget() {
		return target;
	}

	public RPCCall addListener(RPCCallListener listener) {
		assert(listener != null) : "Invalid listener";
		if(state != RPCCall.State.UNSENT)
			throw new IllegalStateException("Can not attach listeners after the call is started");

		listeners.add(listener);
		return this;
	}

	public boolean matchesId() {
		return response.getId().equals(target.getId());
	}

	public boolean matchesAddress() {
		return response.getOrigin().equals(request.getRemote());
	}

	public Message getRequest() {
		return request;
	}

	public Message getResponse() {
		return response;
	}

	public long getRTT() {
		if(sentTime == -1 || responseTime == -1)
			return -1;

		return responseTime - sentTime;
	}

	public long getSentTime() {
		return sentTime;
	}

	public long getResponseTime() {
		return responseTime;
	}

	public boolean hasResponseSocketMismatch() {
		return responseSocketMismatch;
	}

	public State getState() {
		return state;
	}

	public boolean isPending() {
		return state.ordinal() < State.TIMEOUT.ordinal();
	}

	void updateState(State state) {
		State prev = this.state;
		this.state = state;

		if (listeners == null)
			return;

		for (RPCCallListener listener : listeners) {
			listener.onStateChange(this, prev, state);

			switch (state) {
			case TIMEOUT:
				listener.onTimeout(this);
				break;
			case STALLED:
				listener.onStall(this);
				break;
			case RESPONDED:
				listener.onResponse(this, response);
				break;
			default:
				break;
			}
		}
	}

	void sent(RPCServer server) {
		assert(expectedRTT >= 0);
		assert(expectedRTT <= Constants.RPC_CALL_TIMEOUT_MAX);

		sentTime = System.currentTimeMillis();
		updateState(State.SENT);

		// Keep the scheduler for later use
		scheduler = server.getScheduler();

		// spread out the stalls by +- 1ms to reduce lock contention
		int smear = ThreadLocalRandom.current().nextInt(-1000, 1000);
		timeoutTimer = scheduler.schedule(this::checkTimeout,
				expectedRTT * 1000 + smear, TimeUnit.MICROSECONDS);
	}

	void responsed(Message response) {
		assert(response != null);
		assert(response.getType() == Message.Type.RESPONSE ||
				response.getType() == Message.Type.ERROR) : "Invalid request message";

		if (timeoutTimer != null)
			timeoutTimer.cancel(false);

		this.response = response;
		responseTime = System.currentTimeMillis();

		switch(response.getType()) {
		case RESPONSE:
			updateState(State.RESPONDED);
			break;
		case ERROR:
			updateState(State.ERROR);
			break;
		default:
			throw new IllegalStateException("INTERNAL ERROR: should never happen!!!");
		}
	}

	void responseSocketMismatch() {
		responseSocketMismatch = true;
	}

	void failed() {
		updateState(State.TIMEOUT);
	}

	void cancel() {
		if (timeoutTimer != null)
			timeoutTimer.cancel(false);

		updateState(State.CANCELED);
	}

	void stall() {
		if (state != State.SENT)
			return;

		updateState(State.STALLED);
	}

	void checkTimeout() {
		if (state != State.SENT && state != State.STALLED)
			return;

		long elapsed = System.currentTimeMillis() - sentTime;
		long remaining = Constants.RPC_CALL_TIMEOUT_MAX - elapsed;
		if (remaining > 0) {
			updateState(State.STALLED);
			// re-schedule for failed
			timeoutTimer = scheduler.schedule(this::checkTimeout, remaining, TimeUnit.MILLISECONDS);
		} else {
			updateState(State.TIMEOUT);
		}
	}
}
