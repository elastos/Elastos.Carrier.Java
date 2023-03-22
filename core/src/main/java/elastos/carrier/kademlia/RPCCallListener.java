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

import elastos.carrier.kademlia.messages.Message;

/**
 *  Class which objects should derive from, if they want to know the result of a call.
 */
public interface RPCCallListener {

	public default void onStateChange(RPCCall c, RPCCall.State previous, RPCCall.State current) {}

	/**
	 * A response was received.
	 * @param c The call
	 * @param rsp The response
	 */
	public default void onResponse (RPCCall c, Message response) {}


	/**
	 * The call has not timed out yet but is estimated to be unlikely to succeed
	 */
	public default void onStall(RPCCall c) {}

	/**
	 * The call has timed out.
	 * @param c The call
	 */
	public default void onTimeout (RPCCall c) {}

}
