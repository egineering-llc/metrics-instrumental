/**
 * Copyright 2015 E-Gineering, LLC.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.e_gineering.metrics.instrumental;

import com.codahale.metrics.Clock;
import com.codahale.metrics.Counter;
import com.codahale.metrics.Gauge;
import com.codahale.metrics.Histogram;
import com.codahale.metrics.Meter;
import com.codahale.metrics.Metered;
import com.codahale.metrics.MetricFilter;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.ScheduledReporter;
import com.codahale.metrics.Snapshot;
import com.codahale.metrics.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Locale;
import java.util.Map;
import java.util.SortedMap;
import java.util.concurrent.TimeUnit;

/**
 * Handles reporting to the Instrumental backend on a regularly scheduled basis.
 *
 * Largely based upon the graphite reporting module from Dropwizard Metrics.
 */
public class InstrumentalReporter extends ScheduledReporter {

	/**
	 * Returns a new {@link Builder} for {@link InstrumentalReporter}
	 *
	 * @param registry the registry to report
	 * @return a {@link Builder} instance for a {@link InstrumentalReporter}
	 */
	public static Builder forRegistry(MetricRegistry registry) {
		return new Builder(registry);
	}

	/**
	 * A builder for a {@link InstrumentalReporter} instances. Defaults to not using a prefix, using the default clock,
	 * converting rates to events/second, converting durations to milliseconds, and not filtering metrics.
	 */
	public static class Builder {
		private final MetricRegistry registry;
		private Clock clock;
		private String prefix;
		private TimeUnit rateUnit;
		private TimeUnit durationUnit;
		private MetricFilter filter;

		private Builder(MetricRegistry registry) {
			this.registry = registry;
			this.clock = Clock.defaultClock();
			this.prefix = null;
			this.rateUnit = TimeUnit.SECONDS;
			this.durationUnit = TimeUnit.MILLISECONDS;
			this.filter = MetricFilter.ALL;
		}

		/**
		 * Use the given {@link Clock} instance for the time.
		 *
		 * @param clock a {@link Clock} instance
		 * @return {@code this}
		 */
		public Builder withClock(Clock clock) {
			this.clock = clock;
			return this;
		}

		/**
		 * Prefix all metric names with the given string.
		 *
		 * @param prefix the prefix for all metric names
		 * @return {@code this}
		 */
		public Builder prefixedWith(String prefix) {
			this.prefix = prefix;
			return this;
		}

		/**
		 * Convert rates to the given time unit.
		 *
		 * @param rateUnit a unit of time
		 * @return {@code this}
		 */
		public Builder convertRatesTo(TimeUnit rateUnit) {
			this.rateUnit = rateUnit;
			return this;
		}

		/**
		 * Convert durations to the given time unit.
		 *
		 * @param durationUnit a unit of time
		 * @return {@code this}
		 */
		public Builder convertDurationsTo(TimeUnit durationUnit) {
			this.durationUnit = durationUnit;
			return this;
		}

		/**
		 * Only report metrics which match the given filter.
		 *
		 * @param filter a {@link MetricFilter}
		 * @return {@code this}
		 */
		public Builder filter(MetricFilter filter) {
			this.filter = filter;
			return this;
		}

		/**
		 * Builds a {@link InstrumentalReporter} with the given properties, sending metrics
		 * using the given {@link InstrumentalSender}
		 */
		public InstrumentalReporter build(InstrumentalSender instrumental) {
			return new InstrumentalReporter(registry, instrumental, clock, prefix, rateUnit, durationUnit, filter);
		}
	}

	private static final Logger LOGGER = LoggerFactory.getLogger(InstrumentalReporter.class);

	private final InstrumentalSender instrumental;
	private final Clock clock;
	private final String prefix;

	private InstrumentalReporter(MetricRegistry registry,
	                             InstrumentalSender instrumental,
	                             Clock clock,
	                             String prefix,
	                             TimeUnit rateUnit,
	                             TimeUnit durationUnit,
	                             MetricFilter filter) {
		super(registry, "instrumental-reporter", filter, rateUnit, durationUnit);
		this.instrumental = instrumental;
		this.clock = clock;
		this.prefix = prefix;
	}

	@Override
	public void report(SortedMap<String, Gauge> gauges, SortedMap<String, Counter> counters, SortedMap<String, Histogram> histograms, SortedMap<String, Meter> meters, SortedMap<String, Timer> timers) {
		final long timestamp = clock.getTime() / 1000;

		// oh it'd be lovely to use Java 7 here
		try {
			if (!instrumental.isConnected()) {
				instrumental.connect();
			}

			for (Map.Entry<String, Gauge> entry : gauges.entrySet()) {
				reportGauge(entry.getKey(), entry.getValue(), timestamp);
			}

			for (Map.Entry<String, Counter> entry : counters.entrySet()) {
				reportCounter(entry.getKey(), entry.getValue(), timestamp);
			}

			for (Map.Entry<String, Histogram> entry : histograms.entrySet()) {
				reportHistogram(entry.getKey(), entry.getValue(), timestamp);
			}

			for (Map.Entry<String, Meter> entry : meters.entrySet()) {
				reportMetered(entry.getKey(), entry.getValue(), timestamp);
			}

			for (Map.Entry<String, Timer> entry : timers.entrySet()) {
				reportTimer(entry.getKey(), entry.getValue(), timestamp);
			}

			instrumental.flush();
		} catch (IOException e) {
			LOGGER.warn("Unable to report to Instrumental", instrumental, e);
			try {
				instrumental.close();
			} catch (IOException e1) {
				LOGGER.warn("Error closing Instrumental", instrumental, e1);
			}
		}
	}

	@Override
	public void stop() {
		try {
			super.stop();
		} finally {
			try {
				instrumental.close();
			} catch (IOException e) {
				LOGGER.debug("Error disconnecting from Instrumental", instrumental, e);
			}
		}
	}

	private void reportTimer(String name, Timer timer, long timestamp) throws IOException {
		final Snapshot snapshot = timer.getSnapshot();

		instrumental.send(MetricType.GAUGE, prefix(name, "max"), format(convertDuration(snapshot.getMax())), timestamp);
		instrumental.send(MetricType.GAUGE, prefix(name, "mean"), format(convertDuration(snapshot.getMean())), timestamp);
		instrumental.send(MetricType.GAUGE, prefix(name, "min"), format(convertDuration(snapshot.getMin())), timestamp);
		instrumental.send(MetricType.GAUGE, prefix(name, "stddev"),
				             format(convertDuration(snapshot.getStdDev())),
				             timestamp);
		instrumental.send(MetricType.GAUGE, prefix(name, "p50"),
				             format(convertDuration(snapshot.getMedian())),
				             timestamp);
		instrumental.send(MetricType.GAUGE, prefix(name, "p75"),
				             format(convertDuration(snapshot.get75thPercentile())),
				             timestamp);
		instrumental.send(MetricType.GAUGE, prefix(name, "p95"),
				             format(convertDuration(snapshot.get95thPercentile())),
				             timestamp);
		instrumental.send(MetricType.GAUGE, prefix(name, "p98"),
				             format(convertDuration(snapshot.get98thPercentile())),
				             timestamp);
		instrumental.send(MetricType.GAUGE, prefix(name, "p99"),
				             format(convertDuration(snapshot.get99thPercentile())),
				             timestamp);
		instrumental.send(MetricType.GAUGE, prefix(name, "p999"),
				             format(convertDuration(snapshot.get999thPercentile())),
				             timestamp);

		reportMetered(name, timer, timestamp);
	}

	private void reportMetered(String name, Metered meter, long timestamp) throws IOException {
		instrumental.send(MetricType.GAUGE, prefix(name, "count"), format(meter.getCount()), timestamp);
		instrumental.send(MetricType.GAUGE, prefix(name, "m1_rate"),
				             format(convertRate(meter.getOneMinuteRate())),
				             timestamp);
		instrumental.send(MetricType.GAUGE, prefix(name, "m5_rate"),
				             format(convertRate(meter.getFiveMinuteRate())),
				             timestamp);
		instrumental.send(MetricType.GAUGE, prefix(name, "m15_rate"),
				             format(convertRate(meter.getFifteenMinuteRate())),
				             timestamp);
		instrumental.send(MetricType.GAUGE, prefix(name, "mean_rate"),
				             format(convertRate(meter.getMeanRate())),
				             timestamp);
	}

	private void reportHistogram(String name, Histogram histogram, long timestamp) throws IOException {
		final Snapshot snapshot = histogram.getSnapshot();
		instrumental.send(MetricType.GAUGE, prefix(name, "count"), format(histogram.getCount()), timestamp);
		instrumental.send(MetricType.GAUGE, prefix(name, "max"), format(snapshot.getMax()), timestamp);
		instrumental.send(MetricType.GAUGE, prefix(name, "mean"), format(snapshot.getMean()), timestamp);
		instrumental.send(MetricType.GAUGE, prefix(name, "min"), format(snapshot.getMin()), timestamp);
		instrumental.send(MetricType.GAUGE, prefix(name, "stddev"), format(snapshot.getStdDev()), timestamp);
		instrumental.send(MetricType.GAUGE, prefix(name, "p50"), format(snapshot.getMedian()), timestamp);
		instrumental.send(MetricType.GAUGE, prefix(name, "p75"), format(snapshot.get75thPercentile()), timestamp);
		instrumental.send(MetricType.GAUGE, prefix(name, "p95"), format(snapshot.get95thPercentile()), timestamp);
		instrumental.send(MetricType.GAUGE, prefix(name, "p98"), format(snapshot.get98thPercentile()), timestamp);
		instrumental.send(MetricType.GAUGE, prefix(name, "p99"), format(snapshot.get99thPercentile()), timestamp);
		instrumental.send(MetricType.GAUGE, prefix(name, "p999"), format(snapshot.get999thPercentile()), timestamp);
	}

	private void reportCounter(String name, Counter counter, long timestamp) throws IOException {
		instrumental.send(MetricType.GAUGE, prefix(name, "count"), format(counter.getCount()), timestamp);
	}

	private void reportGauge(String name, Gauge gauge, long timestamp) throws IOException {
		final String value = format(gauge.getValue());
		if (value != null) {
			instrumental.send(MetricType.GAUGE, prefix(name), value, timestamp);
		}
	}

	private String prefix(String... components) {
		return MetricRegistry.name(prefix, components);
	}

	private String format(long n) {
		return Long.toString(n);
	}

	private String format(double v) {
		return String.format(Locale.US, "%2.2f", v);
	}

	private String format(Object o) {
		if (o instanceof Float) {
			return format(((Float) o).floatValue());
		} else if (o instanceof Double) {
			return format(((Double) o).floatValue());
		} else if (o instanceof Byte) {
			return format(((Byte) o).floatValue());
		} else if (o instanceof Short) {
			return format(((Short) o).floatValue());
		} else if (o instanceof Integer) {
			return format(((Integer) o).floatValue());
		} else if (o instanceof Long) {
			return format(((Long) o).floatValue());
		}
		return null;
	}
}
