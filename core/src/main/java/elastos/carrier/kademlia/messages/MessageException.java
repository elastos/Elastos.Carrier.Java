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

package elastos.carrier.kademlia.messages;

import elastos.carrier.kademlia.ErrorCode;
import elastos.carrier.kademlia.exceptions.KadException;

public class MessageException extends KadException {
	private static final long serialVersionUID = -4890520057742428818L;
	private static final int DEFAULT_CODE = ErrorCode.ProtocolError.value();

	private PartialMessage partialMsg = PartialMessage.BLANK;

	/**
	 * Constructs a new KadException with {@code null} as its detail message. The
	 * cause is not initialized, and may subsequently be initialized by a call to
	 * {@link #initCause}.
	 */
	public MessageException() {
		super(DEFAULT_CODE);
	}

	/**
	 * Constructs a new KadException with the specified detail message. The cause is
	 * not initialized, and may subsequently be initialized by a call to
	 * {@link #initCause}.
	 *
	 * @param message the detail message. The detail message is saved for later
	 *                retrieval by the {@link #getMessage()} method.
	 */
	public MessageException(String message) {
		super(DEFAULT_CODE, message);
	}

	/**
	 * Constructs a new KadException with the specified detail message and cause.
	 * <p>
	 * Note that the detail message associated with {@code cause} is <i>not</i>
	 * automatically incorporated in this exception's detail message.
	 * </p>
	 *
	 * @param message the detail message (which is saved for later retrieval by the
	 *                {@link #getMessage()} method).
	 * @param cause   the cause (which is saved for later retrieval by the
	 *                {@link #getCause()} method). (A {@code null} value is
	 *                permitted, and indicates that the cause is nonexistent or
	 *                unknown.)
	 */
	public MessageException(String message, Throwable cause) {
		super(DEFAULT_CODE, message, cause);
	}

	/**
	 * Constructs a new KadException with the specified cause and a detail message
	 * of {@code (cause==null ? null : cause.toString())} (which typically contains
	 * the class and detail message of {@code cause}). This constructor is useful
	 * for exceptions that are little more than wrappers for other throwables (for
	 * example, {@link java.security.PrivilegedActionException}).
	 *
	 * @param cause the cause (which is saved for later retrieval by the
	 *              {@link #getCause()} method). (A {@code null} value is permitted,
	 *              and indicates that the cause is nonexistent or unknown.)
	 */
	public MessageException(Throwable cause) {
		super(DEFAULT_CODE, cause);
	}

	public MessageException setPartialMessage(PartialMessage msg) {
		this.partialMsg = msg;
		return this;
	}

	public PartialMessage getPartialMessage() {
		return partialMsg;
	}
}
