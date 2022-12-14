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

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import org.slf4j.Logger;

import elastos.carrier.NodeInfo;
import elastos.carrier.kademlia.Constants;
import elastos.carrier.kademlia.DHT;
import elastos.carrier.kademlia.RPCCall;
import elastos.carrier.kademlia.RPCCallListener;
import elastos.carrier.kademlia.messages.Message;

public abstract class Task implements Comparable<Task> {
	private final int taskId;
	private String name;
	private final AtomicReference<State> state;

	private Task nested;

	private final DHT dht;
	private Set<RPCCall> inFlight;

	long startTime;
	long finishTime;
	private List<TaskListener> listeners;

	private final AtomicInteger lock;

	public static final AtomicInteger nextTaskId = new AtomicInteger(0);

	private static EnumSet<RPCCall.State> callStatesTobeUpdate = EnumSet.of(RPCCall.State.RESPONDED,
			RPCCall.State.ERROR, RPCCall.State.STALLED, RPCCall.State.TIMEOUT);

	public static enum State {
		INITIAL, QUEUED, RUNNING, FINISHED, CANCELED;

		public boolean isTerminal() {
			return this == FINISHED || this == CANCELED;
		}

		public boolean preStart() {
			return this == INITIAL || this == QUEUED;
		}
	}

	public final class CallListener implements RPCCallListener {
		@Override
		public void onStateChange(RPCCall call, RPCCall.State previous, RPCCall.State current) {
			switch (current) {
			case SENT:
				callSent(call);
				break;
			case RESPONDED:
				inFlight.remove(call);
				if (!isFinished())
					callResponsed(call, call.getResponse());
				break;
			case ERROR:
				inFlight.remove(call);
				if (!isFinished())
					callError(call);
				break;
			case TIMEOUT:
				inFlight.remove(call);
				if (!isFinished())
					callTimeout(call);
				break;
			default:
				break;
			}

			if (callStatesTobeUpdate.contains(current))
				serializedUpdate();
		}
	};

	public Task(DHT dht) {
		this.dht = dht;

		taskId = nextTaskId.getAndIncrement();
		state = new AtomicReference<>(State.INITIAL);
		inFlight = ConcurrentHashMap.newKeySet();
		lock = new AtomicInteger();
	}

	public int getTaskId() {
		return taskId;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getName() {
		return name;
	}

	protected boolean setState(State expected, State newState) {
		return setState(EnumSet.of(expected), newState);
	}

	protected boolean setState(Set<State> expected, State newState) {
		State current;
		do {
			current = state.get();
			if (!expected.contains(current))
				return false;

		} while (!state.compareAndSet(current, newState));

		return true;
	}

	public State getState() {
		return state.get();
	}

	public void setNestedTask(Task nested) {
		this.nested = nested;
	}

	public Task getNestedTask() {
		return nested;
	}

	protected DHT getDHT() {
		return dht;
	}

	public void addListener(TaskListener listener) {
		if (listeners == null)
			listeners = new ArrayList<>(4);

		// listener is added after the task already terminated, thus it won't get the
		// event, trigger it manually
		if (state.get().isTerminal())
			listener.finished(this);

		listeners.add(listener);
	}

	public void removeListener(TaskListener listener) {
		if (listeners != null) {
			listeners.remove(listener);
		}
	}

	public void start() {
		if (setState(EnumSet.of(State.INITIAL, State.QUEUED), State.RUNNING)) {
			getLogger().debug("Task starting: {}", toString());
			startTime = System.currentTimeMillis();

			prepare();

			try {
				serializedUpdate();
			} catch (Exception e) {
				getLogger().error("Task start fialed: " + toString(), e);
			}
		}
	}

	private void serializedUpdate() {
		int current = lock.incrementAndGet();

		// another thread is executing
		if(current > 1)
			return;

		getLogger().trace("Task update: {}", toString());

		try {
			do {
				if(isDone())
					finish();

				if (canDoRequest() && !isFinished()) {
					update();

					// check again in case todo-queue has been drained by update()
					if(isDone())
						finish();
				}

				current = lock.addAndGet(Math.negateExact(current));
			} while(current > 0);
		} catch(Throwable t) {
			lock.set(0);
			throw t;
		}
	}

	public void cancel() {
		if (setState(EnumSet.complementOf(EnumSet.of(State.FINISHED, State.CANCELED)), State.CANCELED)) {
			finishTime = System.currentTimeMillis();
			getLogger().debug("Task canceled: {}", toString());
			notifyCompletionListeners();
		}

		if (nested != null)
			nested.cancel();
	}

	private void finish() {
		if (setState(EnumSet.complementOf(EnumSet.of(State.FINISHED, State.CANCELED)), State.FINISHED)) {
			finishTime = System.currentTimeMillis();
			getLogger().debug("Task finished: {}", toString());
			notifyCompletionListeners();
		}
	}

	private void notifyCompletionListeners() {
		if (listeners != null) {
			for (TaskListener l : listeners) {
				l.finished(this);
			}
		}
	}

	public boolean isCancelled() {
		return state.get() == State.CANCELED;
	}

	public boolean isFinished() {
		return state.get().isTerminal();
	}

	public long getStartTime() {
		return startTime;
	}

	public long getFinishedTime() {
		return finishTime;
	}

	public Duration age() {
		return Duration.between(Instant.ofEpochMilli(startTime), Instant.now());
	}

	protected boolean canDoRequest() {
		return inFlight.size() < Constants.MAX_CONCURRENT_TASK_REQUESTS;
	}

	protected boolean sendCall(NodeInfo node, Message request) {
		return sendCall(node, request, null);
	}

	protected boolean sendCall(NodeInfo node, Message request, Consumer<RPCCall> modifyCallBeforeSubmit) {
		if (!canDoRequest())
			return false;

		RPCCall call = new RPCCall(node, request)
				// TODO: needs to be improve
				// .setExpectedRTT(?)
				.addListener(new CallListener());

		if (modifyCallBeforeSubmit != null)
			modifyCallBeforeSubmit.accept(call);

		inFlight.add(call);

		getLogger().debug("Task#{} sending call to {}", getTaskId(), node, request.getRemoteAddress());
		// asyncify since we're under a lock here
		dht.getNode().getScheduler().execute(() -> dht.getServer().sendCall(call));

		return true;
	}

	protected void callSent(RPCCall call) {
	}

	protected void callResponsed(RPCCall call, Message response) {
	}

	protected void callError(RPCCall call) {
	}

	protected void callTimeout(RPCCall call) {
	}

	protected void prepare() {
	}

	protected abstract void update();

	protected boolean isDone() {
		return inFlight.size() == 0;
	}

	protected abstract Logger getLogger();

	@Override
	public int compareTo(Task t) {
		return taskId - t.taskId;
	}

	@Override
	public int hashCode() {
		return taskId;
	}

	@Override
	public String toString() {
		StringBuilder b = new StringBuilder(100);
		b.append(this.getClass().getSimpleName());
		b.append('#').append(getTaskId());
		if (name != null && !name.isEmpty())
			b.append('[').append(name).append(']');
		if(this instanceof LookupTask)
			b.append(" target: ").append(((LookupTask)this).getTarget()).append(',');
		b.append(" DHT: ").append(dht.getType());
		b.append(", state: ").append(state.get());
		if (startTime != 0) {
			if (finishTime == 0)
				b.append(", age: ").append(age());
			else if (finishTime > 0)
				b.append(", timeToFinish: ").append(Duration.between(Instant.ofEpochMilli(startTime), Instant.ofEpochMilli(finishTime)));
		}

		return b.toString();
	}
}

