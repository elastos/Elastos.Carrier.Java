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

public final class Constants {
	///////////////////////////////////////////////////////////////////////////
	// Default DHT port
	///////////////////////////////////////////////////////////////////////////
	// IANA - https://www.iana.org/assignments/service-names-port-numbers/service-names-port-numbers.xhtml
	// 38866-39062, Unassigned
	public static final int 	DEFAULT_DHT_PORT						= 39001;

	///////////////////////////////////////////////////////////////////////////
	// RPC server constants
	///////////////////////////////////////////////////////////////////////////
	// enter survival mode if we don't see new packets after this time
	public static final int		RPC_SERVER_REACHABILITY_TIMEOUT			= 60 * 1000;
	public static final int		MAX_ACTIVE_CALLS						= 256;
	public static final int		RPC_CALL_TIMEOUT_MAX					= 10 * 1000;
	public static final int		RPC_CALL_TIMEOUT_BASELINE_MIN			= 100; // ms
	public static final int		RECEIVE_BUFFER_SIZE						= 5 * 1024;

	///////////////////////////////////////////////////////////////////////////
	// Task & Lookup constants
	///////////////////////////////////////////////////////////////////////////
	public static final int 	MAX_CONCURRENT_TASK_REQUESTS			= 10;
	public static final int 	MAX_ACTIVE_TASKS						= 16;

	///////////////////////////////////////////////////////////////////////////
	// DHT maintenance constants
	///////////////////////////////////////////////////////////////////////////
	public static final int		DHT_UPDATE_INTERVAL						= 1000;
	public static final	int		BOOTSTRAP_MIN_INTERVAL					= 4 * 60 * 1000;
	public static final int		BOOTSTRAP_IF_LESS_THAN_X_PEERS			= 30;
	public static final int		SELF_LOOKUP_INTERVAL					= 30 * 60 * 1000; 	// 30 minutes
	public static final int		RANDOM_LOOKUP_INTERVAL					= 10 * 60 * 1000; 	// 10 minutes
	public static final int		RANDOM_PING_INTERVAL					= 10 * 1000; 		// 10 seconds
	public static final int		ROUTING_TABLE_PERSIST_INTERVAL			= 10 * 60 * 1000; 	// 10 minutes

	///////////////////////////////////////////////////////////////////////////
	// Routing table and KBucket constants
	///////////////////////////////////////////////////////////////////////////
	public static final int		MAX_ENTRIES_PER_BUCKET					= 8;
	public static final int		BUCKET_REFRESH_INTERVAL					= 15 * 60 * 1000;
	public static final	int		ROUTING_TABLE_MAINTENANCE_INTERVAL		= 4 * 60 * 1000;
	// 5 timeouts, used for exponential back-off as per Kademlia paper
	public static final	int		KBUCKET_MAX_TIMEOUTS					= 5;
	public static final int		KBUCKET_OLD_AND_STALE_TIMEOUTS			= 2;
	// haven't seen it for a long time + timeout == evict sooner than pure timeout
	// based threshold. e.g. for old entries that we haven't touched for a long time
	public static final int		KBUCKET_OLD_AND_STALE_TIME				= 15 * 60 * 1000;
	public static final int		KBUCKET_PING_BACKOFF_BASE_INTERVAL		= 60 * 1000;
	public static final int		BUCKET_CACHE_PING_MIN_INTERVAL			= 30 * 1000;

	///////////////////////////////////////////////////////////////////////////
	// Tokens and data storage constants
	///////////////////////////////////////////////////////////////////////////
	public static final int		STORAGE_EXPIRE_INTERVAL					= 5 * 60 * 1000;
	public static final int		TOKEN_TIMEOUT							= 5 * 60 * 1000;
	public static final int		MAX_PEER_AGE							= 120 * 60 * 1000;
	public static final int		MAX_VALUE_AGE							= 60 * 60 * 1000;

	///////////////////////////////////////////////////////////////////////////
	// Node software name and version
	///////////////////////////////////////////////////////////////////////////
	public static final String 	NODE_NAME								= "Orca";
	public static final int 	NODE_VERSION							= 1;
	public static final int		VERSION									= Version.build(NODE_NAME, NODE_VERSION);


	///////////////////////////////////////////////////////////////////////////
	// Development environment
	///////////////////////////////////////////////////////////////////////////
	public static final String	ENVIRONMENT_PROPERTY					= "elastos.carrier.enviroment";
	public static final boolean	DEVELOPMENT_ENVIRONMENT					= System.getProperty(ENVIRONMENT_PROPERTY, "")
																				.equals("development");
}
