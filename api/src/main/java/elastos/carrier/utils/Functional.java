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

import java.util.function.Consumer;

public class Functional {
	public static <T> T tap(T obj, Consumer<T> c) {
		c.accept(obj);
		return obj;
	}

	public static interface ThrowingConsumer<T,E extends Throwable> {
		void accept(T arg) throws E;
	}

	@FunctionalInterface
	public static interface ThrowingSupplier<T,E extends Throwable> {
		T get() throws E;
	}

	@FunctionalInterface
	public static interface ThrowingFunction<R, T, E extends Throwable> {
		R apply(T arg) throws E;
	}

	public static <T> T unchecked(ThrowingSupplier<? extends T, ?> f) {
		try {
			return f.get();
		} catch (Throwable e) {
			throwAsUnchecked(e);
			return null;
		}
	}

	private static void throwAsUnchecked(Throwable t) {
		asUnchecked(t);
	}

	@SuppressWarnings("unchecked")
	private static <T extends Throwable> void asUnchecked(Throwable t) throws T {
		throw (T) t;
	}
}
