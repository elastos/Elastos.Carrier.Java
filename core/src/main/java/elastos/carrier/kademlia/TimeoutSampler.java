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

import static elastos.carrier.utils.Functional.tap;

import java.util.Arrays;
import java.util.Formatter;

import elastos.carrier.kademlia.messages.Message;

public class TimeoutSampler {
	/*
	static final int MIN_BIN = 0;
	static final int MAX_BIN = Constants.RPC_CALL_TIMEOUT_MAX;
	static final int BIN_SIZE = 50;
	static final int NUM_BINS = (int) Math.ceil((MAX_BIN - MIN_BIN) * 1.0f / BIN_SIZE);

	private final float[] bins = new float[NUM_BINS];
	private volatile long updateCount;
	private long timeoutCeiling;
	private long timeoutBaseline;

	private Snapshot snapshot = new Snapshot(tap(bins.clone(), a -> {
		a[a.length - 1] = 1.0f;
	}));

	public TimeoutSampler() {
		reset();
	}

	public void reset() {
		updateCount = 0;
		timeoutBaseline = timeoutCeiling = Constants.RPC_CALL_TIMEOUT_MAX;
		Arrays.fill(bins, 1.0f / bins.length);
	}

	private final RPCCallListener listener = new RPCCallListener() {
		@Override
		public void onResponse(RPCCall call, Message msg) {
			updateAndRecalc(call.getRTT());
		}
	};

	public long getSampleCount() {
		return updateCount;
	}

	public void registerCall(final RPCCall call) {
		call.addListener(listener);
	}

	public void updateAndRecalc(long newRTT) {
		update(newRTT);
		if ((updateCount++ & 0x0f) == 0) {
			newSnapshot();
			decay();
		}
	}

	protected void update(long newRTT) {
		int bin = (int) (newRTT - MIN_BIN) / BIN_SIZE;
		bin = Math.max(Math.min(bin, bins.length - 1), 0);

		bins[bin] += 1.0;
	}

	protected void decay() {
		for (int i = 0; i < bins.length; i++)
			bins[i] *= 0.95f;
	}

	protected void newSnapshot() {
		snapshot = new Snapshot(bins.clone());
		timeoutBaseline = (long) snapshot.getQuantile(0.1f);
		timeoutCeiling = (long) snapshot.getQuantile(0.9f);
	}

	public Snapshot getStats() {
		return snapshot;
	}

	public static class Snapshot {
		final float[] values;

		float mean = 0;
		float mode = 0;

		public Snapshot(float[] bins) {
			values = bins;

			normalize();
			calcStats();
		}

		void normalize() {
			float cumulativePopulation = 0;

			for (int i = 0; i < values.length; i++)
				cumulativePopulation += values[i];

			if (cumulativePopulation > 0) {
				for (int i = 0; i < values.length; i++)
					values[i] /= cumulativePopulation;
			}

		}

		void calcStats() {
			float modePop = 0;

			for (int bin = 0; bin < values.length; bin++) {
				mean += values[bin] * (bin + 0.5f) * BIN_SIZE;
				if (values[bin] > modePop) {
					mode = (bin + 0.5f) * BIN_SIZE;
					modePop = values[bin];
				}

			}
		}

		public float getQuantile(float quant) {
			for(int i=0;i<values.length;i++) {
				quant -= values[i];
				if(quant <= 0)
					return (i + 0.5f) * BIN_SIZE;
			}

			return MAX_BIN;
		}

		@Override
		public String toString() {
			StringBuilder b = new StringBuilder();

			b.append(" mean:").append(mean).append(" median:").append(getQuantile(0.5f)).append(" mode:").append(mode)
			.append(" 10tile:").append(getQuantile(0.1f)).append(" 90tile:").append(getQuantile(0.9f));
			b.append('\n');

			Formatter l1 = new Formatter();
			Formatter l2 = new Formatter();
			for (int i = 0; i < values.length; i++) {
				if (values[i] >= 0.001) {
					l1.format(" %5d | ", i * BIN_SIZE);
					l2.format("%5d‰ | ", Math.round(values[i] * 1000));
				}
			}

			b.append(l1).append('\n');
			b.append(l2).append('\n');

			return b.toString();
		}

	}

	public long getStallTimeout() {
		// either the 90th percentile or the 10th percentile + 100ms baseline,
		// whichever is HIGHER (to prevent descent to zero and missing more
		// than 10% of the packets in the worst case).
		// but At most RPC_CALL_TIMEOUT_MAX
		long timeout = Math.min(Math.max(timeoutBaseline + Constants.RPC_CALL_TIMEOUT_BASELINE_MIN, timeoutCeiling),
				Constants.RPC_CALL_TIMEOUT_MAX);
		return timeout;
	}

	 */
	static final long BASE_QUANTILE = 1024 * 1024;
	static final long LOW_QUANTILE_INDEX = BASE_QUANTILE / 10;
	static final long MEDIAN_QUANTILE_INDEX = BASE_QUANTILE / 2;
	static final long HIGH_QUANTILE_INDEX = BASE_QUANTILE - LOW_QUANTILE_INDEX;

	static final int MIN_BIN = 0;
	static final int MAX_BIN = Constants.RPC_CALL_TIMEOUT_MAX;
	static final int NUM_BINS = 256;
	static final int BIN_SIZE = 40;

	private final long[] bins = new long[NUM_BINS];
	private long timeoutCeiling;
	private long timeoutBaseline;
	private volatile long updateCount;

	Snapshot snapshot = new Snapshot(tap(bins.clone(), ary -> {
		ary[ary.length - 1] = BASE_QUANTILE;
	}));

	private final RPCCallListener listener = new RPCCallListener() {
		@Override
		public void onResponse(RPCCall c, Message response) {
			updateAndRecalc(c.getRTT());
		}
	};

	public static class Snapshot {
		final long[] values;

		long mean = 0;
		long mode = 0;

		Snapshot(long[] ary) {
			values = ary;

			normalize();
			calcStats();
		}

		void normalize() {
			long cumulativePopulation = 0;

			for (int i = 0; i < values.length; i++) {
				cumulativePopulation += values[i];
			}

			if (cumulativePopulation > 0) {
				for (int i = 0; i < values.length; i++) {
					values[i] /= (cumulativePopulation >>> 20);
				}
			}
		}

		void calcStats() {
			long modePop = 0;

			for (int bin = 0; bin < values.length; bin++) {
				mean += ((values[bin] * BIN_SIZE * bin) + ((values[bin] * BIN_SIZE) >>> 1)) >>> 20;
				if (values[bin] > modePop) {
					mode = (bin * BIN_SIZE) + (BIN_SIZE >>> 1);
					modePop = values[bin];
				}
			}
		}

		public long getQuantile(long quant) {
			for (int i = 0; i < values.length; i++) {
				quant -= values[i];
				if (quant <= 0)
					return (i * BIN_SIZE) + (BIN_SIZE >>> 1);
			}

			return MAX_BIN;
		}

		@Override
		public String toString() {
			StringBuilder repr = new StringBuilder();

			repr.append("mean: ").append(mean)
			.append(", median: ").append(getQuantile(MEDIAN_QUANTILE_INDEX))
			.append(", mode: ").append(mode)
			.append(", 10tile: ").append(getQuantile(LOW_QUANTILE_INDEX))
			.append(", 90tile: ").append(getQuantile(HIGH_QUANTILE_INDEX))
			.append('\n');

			Formatter l1 = new Formatter();
			Formatter l2 = new Formatter();
			for (int i = 0; i < values.length; i++) {
				if (values[i] >= 1000) {
					l1.format("%5d | ", i * BIN_SIZE + MIN_BIN);
					l2.format(" %3d‰ | ", values[i] / 1000);
				}
			}

			repr.append(l1).append('\n').append(l2).append('\n');
			return repr.toString();
		}
	}

	public TimeoutSampler() {
		reset();
	}

	public void reset() {
		updateCount = 0;
		timeoutBaseline = timeoutCeiling = Constants.RPC_CALL_TIMEOUT_MAX;
		Arrays.fill(bins, BASE_QUANTILE >>> 8);
	}

	public long getSampleCount() {
		return updateCount;
	}

	public void registerCall(final RPCCall call) {
		call.addListener(listener);
	}

	public void updateAndRecalc(long newRTT) {
		update(newRTT);
		if ((updateCount++ & 0x0f) == 0) {
			newSnapshot();
			decay();
		}
	}

	protected void update(long newRTT) {
		int bin = (int) (newRTT - MIN_BIN) / BIN_SIZE;
		bin = Math.max(Math.min(bin, bins.length - 1), 0);

		bins[bin] += BASE_QUANTILE;
	}

	protected void decay() {
		for (int i = 0; i < bins.length; i++)
			bins[i] -= (bins[i] >>> 4);
	}

	protected void newSnapshot() {
		snapshot = new Snapshot(bins.clone());
		timeoutBaseline = snapshot.getQuantile(LOW_QUANTILE_INDEX);
		timeoutCeiling = snapshot.getQuantile(HIGH_QUANTILE_INDEX);
	}

	public Snapshot getStats() {
		return snapshot;
	}

	public long getStallTimeout() {
		// either the 90th percentile or the 10th percentile + 100ms baseline, whichever
		// is HIGHER (to prevent descent to zero and missing more than 10% of the
		// packets in the worst case).
		// but At most RPC_CALL_TIMEOUT_MAX
		long timeout = Math.min(Math.max(timeoutBaseline + Constants.RPC_CALL_TIMEOUT_BASELINE_MIN, timeoutCeiling),
				Constants.RPC_CALL_TIMEOUT_MAX);
		return timeout;
	}
}
