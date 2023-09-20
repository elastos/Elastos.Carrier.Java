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

package elastos.carrier.kademlia;

import java.io.IOException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NetworkEngine {
	private Queue<Selectable> registrations;
	private Queue<Selectable> interestUpdates;
	private List<Selectable> selectables;
	private AtomicReference<Thread> worker;

	private Selector selector;

	private static final Logger log = LoggerFactory.getLogger(NetworkEngine.class);

	public interface Selectable {
		public SelectableChannel getChannel();

		public void selectEvent(SelectionKey key) throws IOException;

		public void checkState() throws IOException;

		public int interestOps();
	}

	public NetworkEngine() {
		this.registrations = new ConcurrentLinkedQueue<>();
		this.interestUpdates = new ConcurrentLinkedQueue<>();
		this.selectables = new ArrayList<>();
		this.worker = new AtomicReference<>();

		try {
			this.selector = Selector.open();
			log.info("NIO network engine started.");
		} catch (IOException e) {
			throw new RuntimeException("NIO selector error.", e);
		}
	}

	public void register(Selectable selectable) {
		registrations.add(selectable);
		if(Thread.currentThread() != worker.get()) {
			ensureRunning();
			selector.wakeup();
		}
	}

	public void updateInterestOps(Selectable selectable) {
		interestUpdates.add(selectable);
		if(Thread.currentThread() != worker.get())
			selector.wakeup();
	}

	public boolean isIdle() {
		return worker.get() == null;
	}

	Selector getSelector() {
		return selector;
	}

	// Register the new registered selectables to the selector
	private void processRegistrations() throws IOException {
		Selectable selectable;
		while((selectable = registrations.poll()) != null) {
			rolls++;
			SelectableChannel ch = selectable.getChannel();
			try {
				ch.register(selector, selectable.interestOps(), selectable);
			} catch (ClosedChannelException ex) {
				// ignore the closed channels
				continue;
			}

			selectables.add(selectable);
		}
	}

	// Regular selectables check, remove the closed selectables.
	private void checkSelectables() throws IOException {
		for(Selectable selectable : new ArrayList<>(selectables)) {
			rolls++;
			selectable.checkState();
			SelectableChannel ch = selectable.getChannel();
			SelectionKey key;
			if(ch == null || (key = ch.keyFor(selector)) == null || !key.isValid())
				selectables.remove(selectable);
		}
	}

	// Process the select event
	private void processSelected() throws IOException {
		Set<SelectionKey> keys = selector.selectedKeys();
		for(SelectionKey key : keys) {
			rolls++;
			Selectable selectable = (Selectable)key.attachment();
			selectable.selectEvent(key);
		}
		keys.clear();
	}

	// Update the changed interest OPs
	private void processInterestUpdates() {
		Selectable selectable;
		while((selectable = interestUpdates.poll()) != null) {
			rolls++;
			SelectionKey key = selectable.getChannel().keyFor(selector);
			if(key != null && key.isValid())
				key.interestOps(selectable.interestOps());
		}
	}

	private int rolls;

	private void loop() {
		int iterations = 0;
		int idleIterations = 0;

		log.info("Started select loop.");

		while (true) {
			rolls = 0;

			try {
				selector.select(100);

				if((iterations & 0x0F) == 0)
					checkSelectables();

				processSelected();
				processRegistrations();
				processInterestUpdates();
			} catch (Exception e) {
				log.error("Select loop encounter an error: " + e.getMessage(), e);
			}

			iterations++;
			idleIterations = (rolls != 0) ? 0 : idleIterations + 1;

			// check the idle loops
			if(selectables.size() == 0 && registrations.peek() == null && idleIterations > 100) {
				// stop the idle loop, restart on demand
				worker.set(null);
				log.info("Stopped select loop, restart on demand.");
				break;
			}
		}
	}

	private void ensureRunning() {
		while (true) {
			Thread thread = worker.get();
			if (thread == null && registrations.peek() != null) {
				thread = new Thread(this::loop);
				thread.setName("KademliaNetworkEngine");
				thread.setDaemon(true);
				if (worker.compareAndSet(null, thread)) {
					thread.start();
					break;
				}
			} else {
				break;
			}
		}
	}
}
