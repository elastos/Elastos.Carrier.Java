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

import java.util.Arrays;
import java.util.Formatter;
import java.util.concurrent.atomic.AtomicLong;

import elastos.carrier.kademlia.messages.Message;
import elastos.carrier.kademlia.messages.Message.Method;
import elastos.carrier.kademlia.messages.Message.Type;

public class RPCStatistics {

	private AtomicLong receivedBytes = new AtomicLong();
	private AtomicLong sentBytes = new AtomicLong();

	private AtomicLong lastReceivedBytes = new AtomicLong();
	private AtomicLong lastSentBytes = new AtomicLong();
	private volatile long lastReceivedTimestamp;
	private volatile long lastSentTimestamp;
	private volatile long receivedBytesPerSec;
	private volatile long sentBytesPerSec;

	private AtomicLong[][] sentMessages;
	private AtomicLong[][] receivedMessages;
	private AtomicLong[] timeoutMessages;

	private AtomicLong droppedPackets = new AtomicLong();
	private AtomicLong droppedBytes = new AtomicLong();

	protected RPCStatistics() {
		sentMessages = new AtomicLong[Method.values().length][Type.values().length];
		receivedMessages = new AtomicLong[Method.values().length][Type.values().length];
		timeoutMessages = new AtomicLong[Method.values().length];

		for (AtomicLong[] a : sentMessages)
			Arrays.setAll(a, (i) -> new AtomicLong());

		for (AtomicLong[] a : receivedMessages)
			Arrays.setAll(a, (i) -> new AtomicLong());

		Arrays.setAll(timeoutMessages, (i) -> new AtomicLong());
	}

	/**
	 * @return the receivedBytes
	 */
	public long getReceivedBytes() {
		return receivedBytes.get();
	}

	/**
	 * @return the sentBytes
	 */
	public long getSentBytes() {
		return sentBytes.get();
	}

	/**
	 * @return
	 */
	public long getReceivedBytesPerSec() {
		long now = System.currentTimeMillis();
		long d = now - lastReceivedTimestamp;
		if (d > 950) {
			long lrb = lastReceivedBytes.getAndSet(0);
			receivedBytesPerSec = (int) (lrb * 1000 / d);
			lastReceivedTimestamp = now;
		}
		return receivedBytesPerSec;
	}

	/**
	 * @return
	 */
	public long getSentBytesPerSec() {
		long now = System.currentTimeMillis();
		long d = now - lastSentTimestamp;
		if (d > 950) {
			long lsb = lastSentBytes.getAndSet(0);
			sentBytesPerSec = (int) (lsb * 1000 / d);
			lastSentTimestamp = now;
		}
		return sentBytesPerSec;
	}

	/**
	 * Returns the Count for the specified Message
	 *
	 * @param m The method of the message
	 * @param t The type of the message
	 * @return count
	 */
	public long getSentMessages(Method m, Type t) {
		return sentMessages[m.ordinal()][t.ordinal()].get();
	}

	public long getTotalSentMessages() {
		long total = 0;

		for (AtomicLong[] t : sentMessages)
			total += Arrays.stream(t).mapToLong(AtomicLong::get).sum();

		return total;
	}

	/**
	 * Returns the Count for the specified Message
	 *
	 * @param m The method of the message
	 * @param t The type of the message
	 * @return count
	 */
	public long getReceivedMessages(Method m, Type t) {
		return receivedMessages[m.ordinal()][t.ordinal()].get();
	}

	public long getTotalReceivedMessages() {
		long total = 0;

		for (AtomicLong[] t : receivedMessages)
			total += Arrays.stream(t).mapToLong(AtomicLong::get).sum();

		return total;
	}

	/**
	 * Returns the Count for the specified requests
	 *
	 * @param m The method of the message
	 * @return count
	 */
	public long getTimeoutMessages(Method m) {
		return timeoutMessages[m.ordinal()].get();
	}

	public long getTotalTimeoutMessages() {
		return Arrays.stream(timeoutMessages).mapToLong(AtomicLong::get).sum();
	}

	public long getDroppedPackets() {
		return droppedPackets.get();
	}

	public long getDroppedBytes() {
		return droppedBytes.get();
	}

	/**
	 * @param receivedBytes the receivedBytes to add
	 */
	protected void receivedBytes(long receivedBytes) {
		lastReceivedBytes.addAndGet(receivedBytes);
		this.receivedBytes.addAndGet(receivedBytes);
	}

	/**
	 * @param sentBytes the sentBytes to add
	 */
	protected void sentBytes(long sentBytes) {
		lastSentBytes.addAndGet(sentBytes);
		this.sentBytes.addAndGet(sentBytes);
	}

	protected void sentMessage(Message msg) {
		sentMessages[msg.getMethod().ordinal()][msg.getType().ordinal()].incrementAndGet();
	}

	/*
	protected void addSentMessageToCount(Method m, Type t) {
		sentMessages[m.ordinal()][t.ordinal()]++;
	}
	 */

	protected void receivedMessage(Message msg) {
		receivedMessages[msg.getMethod().ordinal()][msg.getType().ordinal()].incrementAndGet();
	}

	/*
	protected void addReceivedMessageToCount(Method m, Type t) {
		receivedMessages[m.ordinal()][t.ordinal()]++;
	}
	 */

	protected void timeoutMessage(Message msg) {
		timeoutMessages[msg.getMethod().ordinal()].incrementAndGet();
	}

	protected void droppedPacket(int bytes) {
		droppedPackets.incrementAndGet();
		droppedBytes.addAndGet(bytes);
	}

	@Override
	public String toString() {
		StringBuilder repr = new StringBuilder();

		@SuppressWarnings("resource")
		Formatter f = new Formatter(repr);

		f.format("### local RPCs%n");
		f.format("%18s %19s | %19s %19s %19s %n%n", "Method", "REQ", "RSP", "Error", "Timeout");
		for (Method m : Method.values()) {
			long sent = sentMessages[m.ordinal()][Type.REQUEST.ordinal()].get();
			long received = receivedMessages[m.ordinal()][Type.RESPONSE.ordinal()].get();
			long error = receivedMessages[m.ordinal()][Type.ERROR.ordinal()].get();
			long timeouts = timeoutMessages[m.ordinal()].get();
			f.format("%18s %19d | %19d %19d %19d %n", m, sent, received, error, timeouts);
		}
		f.format("%n### remote RPCs%n");
		f.format("%18s %19s | %19s %19s %n%n", "Method", "REQ", "RSP", "Errors");
		for (Method m : Method.values()) {
			long received = receivedMessages[m.ordinal()][Type.REQUEST.ordinal()].get();
			long sent = sentMessages[m.ordinal()][Type.RESPONSE.ordinal()].get();
			long errors = sentMessages[m.ordinal()][Type.ERROR.ordinal()].get();
			f.format("%18s %19d | %19d %19d %n", m, received, sent, errors);
		}

		f.format("%n### Total[messages/bytes]%n");
		f.format("    sent  %d/%d, received %d/%d, timeout %d/-, dropped %d/%d%n",
				getTotalSentMessages(), sentBytes.get(), getTotalReceivedMessages(), receivedBytes.get(),
				getTotalTimeoutMessages(), droppedPackets.get(), droppedBytes.get());

		return repr.toString();
	}
}
