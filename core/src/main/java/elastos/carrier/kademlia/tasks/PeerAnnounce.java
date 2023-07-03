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

import java.util.ArrayDeque;
import java.util.Deque;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import elastos.carrier.PeerInfo;
import elastos.carrier.kademlia.DHT;
import elastos.carrier.kademlia.messages.AnnouncePeerRequest;

public class PeerAnnounce extends Task {
	private Deque<CandidateNode> todo;
	private PeerInfo peer;

	private static final Logger log = LoggerFactory.getLogger(ValueAnnounce.class);

	public PeerAnnounce(DHT dht, ClosestSet closest, PeerInfo peer) {
		super(dht);
		this.todo = new ArrayDeque<>(closest.getEntries());
		this.peer = peer;
	}

	@Override
	protected void update() {
		while (!todo.isEmpty() && canDoRequest()) {
			CandidateNode cn = todo.peekFirst();

			AnnouncePeerRequest q = new AnnouncePeerRequest(peer, cn.getToken());
			sendCall(cn, q, c -> {
				todo.remove(cn);
			});
		}
	}

	@Override
	protected boolean isDone() {
		return todo.isEmpty() && super.isDone();
	}

	@Override
	protected Logger getLogger() {
		return log;
	}
}
