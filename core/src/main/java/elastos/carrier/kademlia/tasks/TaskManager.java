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

import static com.google.common.base.Preconditions.checkState;

import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ConcurrentSkipListSet;

import elastos.carrier.kademlia.Constants;
import elastos.carrier.kademlia.DHT;

public class TaskManager {
	private DHT dht;
	private Deque<Task> queued;
	private Set<Task> running;
	private boolean canceling;

	public TaskManager(DHT dht) {
		this.dht = dht;

		queued = new ConcurrentLinkedDeque<>();
		running = new ConcurrentSkipListSet<>();
	}

	public void add(Task task, boolean prior) {
		checkState(!canceling, "Can not add new tasks when stopping");

		// remove finished and dequeue the queued
		task.addListener(t -> {
			running.remove(t);
			dequeue();
		});

		if (task.getState() == Task.State.RUNNING) {
			running.add(task);
			return;
		}

		if (!task.setState(Task.State.INITIAL, Task.State.QUEUED))
			return;

		if (prior)
			queued.addFirst(task);
		else
			queued.addLast(task);

		dequeue();
	}

	public void add(Task task) {
		add(task, false);
	}

	public synchronized void dequeue() {
		Task t;

		while (true) {
			if (!canStartTask())
				break;

			t = queued.pollFirst();
			if (t == null)
				break;

			if (t.isFinished())
				continue;

			running.add(t);

			dht.getNode().getScheduler().execute(t::start);
		}
	}

	List<Task> getRunningTasks() {
		return new ArrayList<>(running);
	}

	List<Task> getQueuedTasks() {
		return new ArrayList<>(queued);
	}

	/// Get the number of running tasks
	public int getNumRunningTasks() {
		return running.size();
	}

	/// Get the number of queued tasks
	public int getNumQueuedTasks() {
		return queued.size();
	}

	public boolean canStartTask() {
		return !canceling && (running.size() <= Constants.MAX_ACTIVE_TASKS);
	}

	public int queuedCount() {
		return queued.size();
	}

	public void cancleAll() {
		canceling = true;

		for (Task t : running)
			t.cancel();

		for (Task t : queued)
			t.cancel();

		canceling = false;
	}

	@Override
	public String toString() {
		StringBuilder b = new StringBuilder();
		b.append("#### active: \n");

		for (Task t : getRunningTasks())
			b.append(t.toString()).append('\n');

		b.append("#### queued: \n");

		for (Task t : getQueuedTasks())
			b.append(t.toString()).append('\n');

		return b.toString();
	}
}
