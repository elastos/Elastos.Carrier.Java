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

package elastos.carrier.utils;

import java.io.IOException;
import java.nio.ByteBuffer;

public class ByteBufferInputStream {
	/**
	 * The ByteBuffer object from which data is read.
	 */
	private ByteBuffer buffer;

	/**
	 * Construct a ByteBufferInputStream on a ByteBuffer object.
	 *
	 * @param buffer the ByteBuffer to read the data from
	 */
	public ByteBufferInputStream(ByteBuffer buffer) {
		this.buffer = buffer;
	}

	/**
	 * Obtain the ByteBuffer that this InputStream is based on.
	 *
	 * @return the underlying ByteBuffer
	 */
	public ByteBuffer getByteBuffer() {
		return buffer;
	}

	// ----- InputStream implementation -------------------------------------

	/**
	 * Reads the next byte of data from the input stream. The value byte is returned
	 * as an <code>int</code> in the range <code>0</code> to <code>255</code>. If no
	 * byte is available because the end of the stream has been reached, the value
	 * <code>-1</code> is returned. This method blocks until input data is
	 * available, the end of the stream is detected, or an exception is thrown.
	 *
	 * @return the next byte of data, or <code>-1</code> if the end of the stream is
	 *         reached
	 *
	 * @exception IOException if an I/O error occurs
	 */
	public int read() throws IOException {
		ByteBuffer buf = getByteBuffer();
		try {
			return buf.hasRemaining() ? ((buf.get()) & 0xFF) : -1;
		} catch (NullPointerException e) {
			if (getByteBuffer() == null)
				throw new IOException("Stream closed");
			else
				throw e;
		}
	}

	/**
	 * Reads up to <code>len</code> bytes of data from the input stream into an
	 * array of bytes. An attempt is made to read as many as <code>len</code> bytes,
	 * but a smaller number may be read, possibly zero. The number of bytes actually
	 * read is returned as an integer.
	 *
	 * @param dest   the buffer into which the data is read
	 * @param offset the start offset in array <code>b</code> at which the data is
	 *               written
	 * @param len    the maximum number of bytes to read
	 *
	 * @return the total number of bytes read into the buffer, or <code>-1</code> if
	 *         there is no more data because the end of the stream has been reached.
	 */
	public int read(byte dest[], int offset, int len) throws IOException {
		if (dest == null || offset < 0 || len < 0 || offset + len > dest.length) {
			if (dest == null) {
				throw new IllegalArgumentException("null byte array");
			} else {
				throw new IllegalArgumentException("length=" + dest.length + ", offset=" + offset + ", len=" + len);
			}
		}

		int max = available(); // note: also checks if stream is closed
		if (len > max) {
			if (max == 0) {
				return -1;
			}

			len = max;
		}

		getByteBuffer().get(dest, offset, len);
		return len;
	}

	/**
	 * Skips over and discards <code>n</code> bytes of data from this input stream.
	 * The <code>skip</code> method may, for a variety of reasons, end up skipping
	 * over some smaller number of bytes, possibly <code>0</code>. This may result
	 * from any of a number of conditions; reaching end of file before
	 * <code>n</code> bytes have been skipped is only one possibility. The actual
	 * number of bytes skipped is returned. If <code>n</code> is negative, no bytes
	 * are skipped.
	 *
	 * @param n the number of bytes to be skipped
	 *
	 * @return the actual number of bytes skipped
	 *
	 * @exception IOException if an I/O error occurs
	 */
	public long skip(long n) throws IOException {
		int cb;
		if (n > Integer.MAX_VALUE)
			cb = Integer.MAX_VALUE;
		else if (n < 0)
			cb = 0;
		else
			cb = (int) n;

		cb = Math.min(cb, available()); // note: also checks if stream is closed

		ByteBuffer buffer = getByteBuffer();
		int of = buffer.position();
		buffer.position(of + cb);

		return cb;
	}

	/**
	 * Returns the number of bytes that can be read (or skipped over) from this
	 * input stream without blocking by the next caller of a method for this input
	 * stream. The next caller might be the same thread or or another thread.
	 *
	 * @return the number of bytes that can be read from this input stream without
	 *         blocking.
	 */
	public int available() throws IOException {
		try {
			return getByteBuffer().remaining();
		} catch (NullPointerException e) {
			if (getByteBuffer() == null)
				throw new IOException("Stream closed");
			else
				throw e;
		}
	}

	/**
	 * Marks the current position in this input stream. A subsequent call to the
	 * <code>reset</code> method repositions this stream at the last marked position
	 * so that subsequent reads re-read the same bytes.
	 *
	 * @param readlimit the maximum limit of bytes that can be read before the mark
	 *                  position becomes invalid
	 */
	public void mark(int readlimit) {
		try {
			getByteBuffer().mark();
		} catch (NullPointerException e) {
		}
	}

	/**
	 * Repositions this stream to the position at the time the <code>mark</code>
	 * method was last called on this input stream.
	 *
	 * @exception IOException if an I/O error occurs.
	 */
	public void reset() throws IOException {
		try {
			getByteBuffer().reset();
		} catch (NullPointerException e) {
			if (getByteBuffer() == null)
				throw new IOException("Stream closed");
			else
				throw e;
		}
	}

	/**
	 * Tests if this input stream supports the <code>mark</code> and
	 * <code>reset</code> methods. The <code>markSupported</code> method of
	 * <code>InputStream</code> returns <code>false</code>.
	 *
	 * @return <code>true</code> if this true type supports the mark and reset
	 *         method; <code>false</code> otherwise
	 */
	public boolean markSupported() {
		return true;
	}

	/**
	 * Close the stream, flushing any accumulated bytes. The underlying buffer is
	 * not closed.
	 *
	 * @exception IOException if an I/O error occurs.
	 */
	public void close() throws IOException {
		buffer = null;
	}
}
