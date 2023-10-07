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

package elastos.carrier;

public class Result<T> {
	private T v4;
	private T v6;

	public Result(T v4, T v6) {
		this.v4 = v4;
		this.v6 = v6;
	}

	public T getV4() {
		return v4;
	}

	public T getV6() {
		return v6;
	}

	public T getValue(Network network) {
		switch (network) {
		case IPv4:
			return v4;

		case IPv6:
			return v6;
		}

		return null;
	}

	protected void setValue(Network network, T value) {
		switch (network) {
		case IPv4:
			v4 = value;

		case IPv6:
			v6 = value;
		}
	}

	public boolean isEmpty() {
		return v4 == null && v6 == null;
	}

	public boolean hasValue() {
		return v4 != null || v6 != null;
	}

	public boolean isComplete() {
		return v4 != null && v6 != null;
	}
}