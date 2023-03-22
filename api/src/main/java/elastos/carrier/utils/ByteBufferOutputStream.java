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
import java.io.OutputStream;
import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.ReadOnlyBufferException;

public class ByteBufferOutputStream extends OutputStream {
	/**
	 * The ByteBuffer object to which data is written.
	 */
	private ByteBuffer buffer;

	/**
	 * Construct a ByteBufferOutputStream on a ByteBuffer object.
	 *
	 * @param buffer the ByteBuffer to write the data to
	 */
	public ByteBufferOutputStream(ByteBuffer buffer) {
		this.buffer = buffer;
	}

	/**
	 * Obtain the ByteBuffer that this OutputStream is based on.
	 *
	 * @return the underlying ByteBuffer
	 */
	public ByteBuffer getByteBuffer() {
		return buffer;
	}

	/**
	 * Writes the specified byte to this output stream.
	 *
	 * @param b the <code>byte</code>
	 *
	 * @exception IOException if an I/O error occurs
	 */
	@Override
	public void write(int b) throws IOException {
		try {
			getByteBuffer().put((byte) b);
		} catch (NullPointerException e) {
			if (getByteBuffer() == null)
				throw new IOException("Stream closed");
			else
				throw e;
		} catch (BufferOverflowException e) {
			throw new IOException("Stream capacity exceeded: " + String.valueOf(e.getMessage()), e);
		} catch (ReadOnlyBufferException e) {
			throw new IOException("Stream is read-only: " + String.valueOf(e.getMessage()), e);
		}
	}

	/**
	 * Writes <code>len</code> bytes from the specified byte array starting at
	 * offset <code>off</code> to this output stream.
	 * <p>
	 * If <code>b</code> is <code>null</code>, a <code>NullPointerException</code>
	 * is thrown.
	 * <p>
	 * If <code>off</code> is negative, or <code>len</code> is negative, or
	 * <code>off+len</code> is greater than the length of the array <code>b</code>,
	 * then an <tt>IndexOutOfBoundsException</tt> is thrown.
	 *
	 * @param src    the data
	 * @param offset the start offset in the data
	 * @param len    the number of bytes to write
	 *
	 * @exception IOException if an I/O error occurs
	 */
	@Override
	public void write(byte[] src, int offset, int len) throws IOException {
		if (src == null || offset < 0 || len < 0 || offset + len > src.length)
			throw new IllegalArgumentException(
					src == null ? "null buffer" : "length=" + src.length + ", offset=" + offset + ", len=" + len);

		try {
			getByteBuffer().put(src, offset, len);
		} catch (NullPointerException e) {
			if (getByteBuffer() == null)
				throw new IOException("Stream closed");
			else
				throw e;
		} catch (BufferOverflowException e) {
			throw new IOException("Stream capacity exceeded: " + String.valueOf(e.getMessage()), e);
		} catch (ReadOnlyBufferException e) {
			throw new IOException("Stream is read-only: " + String.valueOf(e.getMessage()), e);
		}
	}

	/**
	 * Flush any accumulated bytes.
	 *
	 * @exception IOException if an I/O error occurs
	 */
	@Override
	public void flush() throws IOException {
		ByteBuffer buf = getByteBuffer();
		if (buf instanceof MappedByteBuffer) {
			try {
				((MappedByteBuffer) buf).force();
			} catch (UnsupportedOperationException ignore) {
			}
		}
	}

	/**
	 * Close the stream, flushing any accumulated bytes. The underlying buffer is
	 * not closed.
	 *
	 * @exception IOException if an I/O error occurs
	 */
	@Override
	public void close() throws IOException {
		flush();
		buffer = null;
	}
}
