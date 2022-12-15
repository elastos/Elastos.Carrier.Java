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

package elastos.carrier;

import java.io.File;
import java.net.InetSocketAddress;
import java.util.Collection;
import java.util.Map;

public interface Configuration {
	public InetSocketAddress IPv4Address();

	public InetSocketAddress IPv6Address();

	/**
	 * If a Path that points to an existing, writable directory is returned then the routing table
	 * will be persisted to that directory periodically and during shutdown
	 */
	public File storagePath();

	/**
	 * if true then attempt to bootstrap through well-known nodes is made.
	 * you either must have a persisted routing table which can be loaded or
	 * manually seed the routing table by calling {@link DHT#addDHTNode(String, int)}
	 */
	//public boolean routerBootstrap();

	public Collection<NodeInfo> bootstrapNodes();

	public  Map<String, Map<String, Object>> services();
}
