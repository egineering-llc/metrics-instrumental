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
import com.codahale.metrics.MetricFilter;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Snapshot;
import com.codahale.metrics.Timer;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InOrder;

import java.net.UnknownHostException;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static com.e_gineering.metrics.instrumental.MetricType.*;

public class InstrumentalReporterTest {
    private final long timestamp = 1000198;
    private final Clock clock = mock(Clock.class);
    private final Instrumental instrumental = mock(Instrumental.class);
    private final MetricRegistry registry = mock(MetricRegistry.class);
    private final InstrumentalReporter reporter = InstrumentalReporter.forRegistry(registry)
                                                              .withClock(clock)
                                                              .prefixedWith("prefix")
                                                              .convertRatesTo(TimeUnit.SECONDS)
                                                              .convertDurationsTo(TimeUnit.MILLISECONDS)
                                                              .filter(MetricFilter.ALL)
                                                              .build(instrumental);

    @Before
    public void setUp() throws Exception {
        when(clock.getTime()).thenReturn(timestamp * 1000);
    }

    @Test
    public void doesNotReportStringGaugeValues() throws Exception {
        reporter.report(map("gauge", gauge("value")),
                        this.<Counter>map(),
                        this.<Histogram>map(),
                        this.<Meter>map(),
                        this.<Timer>map());

        final InOrder inOrder = inOrder(instrumental);
        inOrder.verify(instrumental).isConnected();
        inOrder.verify(instrumental).connect();
        inOrder.verify(instrumental, never()).send(GAUGE, "prefix.gauge", "value", timestamp);
        inOrder.verify(instrumental).flush();

        verifyNoMoreInteractions(instrumental);
    }

    @Test
    public void reportsByteGaugeValues() throws Exception {
        reporter.report(map("gauge", gauge((byte) 1)),
                        this.<Counter>map(),
                        this.<Histogram>map(),
                        this.<Meter>map(),
                        this.<Timer>map());

        final InOrder inOrder = inOrder(instrumental);
        inOrder.verify(instrumental).isConnected();
        inOrder.verify(instrumental).connect();
        inOrder.verify(instrumental).send(GAUGE, "prefix.gauge", "1.00", timestamp);
        inOrder.verify(instrumental).flush();

        verifyNoMoreInteractions(instrumental);
    }

    @Test
    public void reportsShortGaugeValues() throws Exception {
        reporter.report(map("gauge", gauge((short) 1)),
                        this.<Counter>map(),
                        this.<Histogram>map(),
                        this.<Meter>map(),
                        this.<Timer>map());

        final InOrder inOrder = inOrder(instrumental);
        inOrder.verify(instrumental).isConnected();
        inOrder.verify(instrumental).connect();
        inOrder.verify(instrumental).send(GAUGE, "prefix.gauge", "1.00", timestamp);
        inOrder.verify(instrumental).flush();

        verifyNoMoreInteractions(instrumental);
    }

    @Test
    public void reportsIntegerGaugeValues() throws Exception {
        reporter.report(map("gauge", gauge(1)),
                        this.<Counter>map(),
                        this.<Histogram>map(),
                        this.<Meter>map(),
                        this.<Timer>map());

        final InOrder inOrder = inOrder(instrumental);
        inOrder.verify(instrumental).isConnected();
        inOrder.verify(instrumental).connect();
        inOrder.verify(instrumental).send(GAUGE, "prefix.gauge", "1.00", timestamp);
        inOrder.verify(instrumental).flush();

        verifyNoMoreInteractions(instrumental);
    }

    @Test
    public void reportsLongGaugeValues() throws Exception {
        reporter.report(map("gauge", gauge(1L)),
                        this.<Counter>map(),
                        this.<Histogram>map(),
                        this.<Meter>map(),
                        this.<Timer>map());

        final InOrder inOrder = inOrder(instrumental);
        inOrder.verify(instrumental).isConnected();
        inOrder.verify(instrumental).connect();
        inOrder.verify(instrumental).send(GAUGE, "prefix.gauge", "1.00", timestamp);
        inOrder.verify(instrumental).flush();

        verifyNoMoreInteractions(instrumental);
    }

    @Test
    public void reportsFloatGaugeValues() throws Exception {
        reporter.report(map("gauge", gauge(1.1f)),
                        this.<Counter>map(),
                        this.<Histogram>map(),
                        this.<Meter>map(),
                        this.<Timer>map());

        final InOrder inOrder = inOrder(instrumental);
        inOrder.verify(instrumental).isConnected();
        inOrder.verify(instrumental).connect();
        inOrder.verify(instrumental).send(GAUGE, "prefix.gauge", "1.10", timestamp);
        inOrder.verify(instrumental).flush();

        verifyNoMoreInteractions(instrumental);
    }

    @Test
    public void reportsDoubleGaugeValues() throws Exception {
        reporter.report(map("gauge", gauge(1.1)),
                        this.<Counter>map(),
                        this.<Histogram>map(),
                        this.<Meter>map(),
                        this.<Timer>map());

        final InOrder inOrder = inOrder(instrumental);
        inOrder.verify(instrumental).isConnected();
        inOrder.verify(instrumental).connect();
        inOrder.verify(instrumental).send(GAUGE, "prefix.gauge", "1.10", timestamp);
        inOrder.verify(instrumental).flush();

        verifyNoMoreInteractions(instrumental);
    }

    @Test
    public void reportsCounters() throws Exception {
        final Counter counter = mock(Counter.class);
        when(counter.getCount()).thenReturn(100L);

        reporter.report(this.<Gauge>map(),
                        this.<Counter>map("counter", counter),
                        this.<Histogram>map(),
                        this.<Meter>map(),
                        this.<Timer>map());

        final InOrder inOrder = inOrder(instrumental);
        inOrder.verify(instrumental).isConnected();
        inOrder.verify(instrumental).connect();
        inOrder.verify(instrumental).send(GAUGE, "prefix.counter.count", "100", timestamp);
        inOrder.verify(instrumental).flush();

        verifyNoMoreInteractions(instrumental);
    }

    @Test
    public void reportsHistograms() throws Exception {
        final Histogram histogram = mock(Histogram.class);
        when(histogram.getCount()).thenReturn(1L);

        final Snapshot snapshot = mock(Snapshot.class);
        when(snapshot.getMax()).thenReturn(2L);
        when(snapshot.getMean()).thenReturn(3.0);
        when(snapshot.getMin()).thenReturn(4L);
        when(snapshot.getStdDev()).thenReturn(5.0);
        when(snapshot.getMedian()).thenReturn(6.0);
        when(snapshot.get75thPercentile()).thenReturn(7.0);
        when(snapshot.get95thPercentile()).thenReturn(8.0);
        when(snapshot.get98thPercentile()).thenReturn(9.0);
        when(snapshot.get99thPercentile()).thenReturn(10.0);
        when(snapshot.get999thPercentile()).thenReturn(11.0);

        when(histogram.getSnapshot()).thenReturn(snapshot);

        reporter.report(this.<Gauge>map(),
                        this.<Counter>map(),
                        this.<Histogram>map("histogram", histogram),
                        this.<Meter>map(),
                        this.<Timer>map());

        final InOrder inOrder = inOrder(instrumental);
        inOrder.verify(instrumental).isConnected();
        inOrder.verify(instrumental).connect();
        inOrder.verify(instrumental).send(GAUGE, "prefix.histogram.count", "1", timestamp);
        inOrder.verify(instrumental).send(GAUGE, "prefix.histogram.max", "2", timestamp);
        inOrder.verify(instrumental).send(GAUGE, "prefix.histogram.mean", "3.00", timestamp);
        inOrder.verify(instrumental).send(GAUGE, "prefix.histogram.min", "4", timestamp);
        inOrder.verify(instrumental).send(GAUGE, "prefix.histogram.stddev", "5.00", timestamp);
        inOrder.verify(instrumental).send(GAUGE, "prefix.histogram.p50", "6.00", timestamp);
        inOrder.verify(instrumental).send(GAUGE, "prefix.histogram.p75", "7.00", timestamp);
        inOrder.verify(instrumental).send(GAUGE, "prefix.histogram.p95", "8.00", timestamp);
        inOrder.verify(instrumental).send(GAUGE, "prefix.histogram.p98", "9.00", timestamp);
        inOrder.verify(instrumental).send(GAUGE, "prefix.histogram.p99", "10.00", timestamp);
        inOrder.verify(instrumental).send(GAUGE, "prefix.histogram.p999", "11.00", timestamp);
        inOrder.verify(instrumental).flush();

        verifyNoMoreInteractions(instrumental);
    }

    @Test
    public void reportsMeters() throws Exception {
        final Meter meter = mock(Meter.class);
        when(meter.getCount()).thenReturn(1L);
        when(meter.getOneMinuteRate()).thenReturn(2.0);
        when(meter.getFiveMinuteRate()).thenReturn(3.0);
        when(meter.getFifteenMinuteRate()).thenReturn(4.0);
        when(meter.getMeanRate()).thenReturn(5.0);

        reporter.report(this.<Gauge>map(),
                        this.<Counter>map(),
                        this.<Histogram>map(),
                        this.<Meter>map("meter", meter),
                        this.<Timer>map());

        final InOrder inOrder = inOrder(instrumental);
        inOrder.verify(instrumental).isConnected();
        inOrder.verify(instrumental).connect();
        inOrder.verify(instrumental).send(GAUGE, "prefix.meter.count", "1", timestamp);
        inOrder.verify(instrumental).send(GAUGE, "prefix.meter.m1_rate", "2.00", timestamp);
        inOrder.verify(instrumental).send(GAUGE, "prefix.meter.m5_rate", "3.00", timestamp);
        inOrder.verify(instrumental).send(GAUGE, "prefix.meter.m15_rate", "4.00", timestamp);
        inOrder.verify(instrumental).send(GAUGE, "prefix.meter.mean_rate", "5.00", timestamp);
        inOrder.verify(instrumental).flush();

        verifyNoMoreInteractions(instrumental);
    }

    @Test
    public void reportsTimers() throws Exception {
        final Timer timer = mock(Timer.class);
        when(timer.getCount()).thenReturn(1L);
        when(timer.getMeanRate()).thenReturn(2.0);
        when(timer.getOneMinuteRate()).thenReturn(3.0);
        when(timer.getFiveMinuteRate()).thenReturn(4.0);
        when(timer.getFifteenMinuteRate()).thenReturn(5.0);

        final Snapshot snapshot = mock(Snapshot.class);
        when(snapshot.getMax()).thenReturn(TimeUnit.MILLISECONDS.toNanos(100));
        when(snapshot.getMean()).thenReturn((double) TimeUnit.MILLISECONDS.toNanos(200));
        when(snapshot.getMin()).thenReturn(TimeUnit.MILLISECONDS.toNanos(300));
        when(snapshot.getStdDev()).thenReturn((double) TimeUnit.MILLISECONDS.toNanos(400));
        when(snapshot.getMedian()).thenReturn((double) TimeUnit.MILLISECONDS.toNanos(500));
        when(snapshot.get75thPercentile()).thenReturn((double) TimeUnit.MILLISECONDS.toNanos(600));
        when(snapshot.get95thPercentile()).thenReturn((double) TimeUnit.MILLISECONDS.toNanos(700));
        when(snapshot.get98thPercentile()).thenReturn((double) TimeUnit.MILLISECONDS.toNanos(800));
        when(snapshot.get99thPercentile()).thenReturn((double) TimeUnit.MILLISECONDS.toNanos(900));
        when(snapshot.get999thPercentile()).thenReturn((double) TimeUnit.MILLISECONDS
                                                                        .toNanos(1000));

        when(timer.getSnapshot()).thenReturn(snapshot);

        reporter.report(this.<Gauge>map(),
                        this.<Counter>map(),
                        this.<Histogram>map(),
                        this.<Meter>map(),
                        map("timer", timer));

        final InOrder inOrder = inOrder(instrumental);
        inOrder.verify(instrumental).isConnected();
        inOrder.verify(instrumental).connect();
        inOrder.verify(instrumental).send(GAUGE, "prefix.timer.max", "100.00", timestamp);
        inOrder.verify(instrumental).send(GAUGE, "prefix.timer.mean", "200.00", timestamp);
        inOrder.verify(instrumental).send(GAUGE, "prefix.timer.min", "300.00", timestamp);
        inOrder.verify(instrumental).send(GAUGE, "prefix.timer.stddev", "400.00", timestamp);
        inOrder.verify(instrumental).send(GAUGE, "prefix.timer.p50", "500.00", timestamp);
        inOrder.verify(instrumental).send(GAUGE, "prefix.timer.p75", "600.00", timestamp);
        inOrder.verify(instrumental).send(GAUGE, "prefix.timer.p95", "700.00", timestamp);
        inOrder.verify(instrumental).send(GAUGE, "prefix.timer.p98", "800.00", timestamp);
        inOrder.verify(instrumental).send(GAUGE, "prefix.timer.p99", "900.00", timestamp);
        inOrder.verify(instrumental).send(GAUGE, "prefix.timer.p999", "1000.00", timestamp);
        inOrder.verify(instrumental).send(GAUGE, "prefix.timer.count", "1", timestamp);
        inOrder.verify(instrumental).send(GAUGE, "prefix.timer.m1_rate", "3.00", timestamp);
        inOrder.verify(instrumental).send(GAUGE, "prefix.timer.m5_rate", "4.00", timestamp);
        inOrder.verify(instrumental).send(GAUGE, "prefix.timer.m15_rate", "5.00", timestamp);
        inOrder.verify(instrumental).send(GAUGE, "prefix.timer.mean_rate", "2.00", timestamp);
        inOrder.verify(instrumental).flush();

        verifyNoMoreInteractions(instrumental);
    }

    @Test
    public void closesConnectionIfInstrumentalIsUnavailable() throws Exception {
        doThrow(new UnknownHostException("UNKNOWN-HOST")).when(instrumental).connect();
        reporter.report(map("gauge", gauge(1)),
            this.<Counter>map(),
            this.<Histogram>map(),
            this.<Meter>map(),
            this.<Timer>map());

        final InOrder inOrder = inOrder(instrumental);
        inOrder.verify(instrumental).isConnected();
        inOrder.verify(instrumental).connect();
        inOrder.verify(instrumental).close();

        verifyNoMoreInteractions(instrumental);
    }

    @Test
    public void closesConnectionOnReporterStop() throws Exception {
        reporter.stop();

        verify(instrumental).close();

        verifyNoMoreInteractions(instrumental);
    }

    private <T> SortedMap<String, T> map() {
        return new TreeMap<String, T>();
    }

    private <T> SortedMap<String, T> map(String name, T metric) {
        final TreeMap<String, T> map = new TreeMap<String, T>();
        map.put(name, metric);
        return map;
    }

    private <T> Gauge gauge(T value) {
        final Gauge gauge = mock(Gauge.class);
        when(gauge.getValue()).thenReturn(value);
        return gauge;
    }
}
