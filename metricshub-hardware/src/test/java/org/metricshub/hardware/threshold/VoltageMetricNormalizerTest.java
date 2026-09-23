package org.metricshub.hardware.threshold;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.metricshub.engine.connector.model.ConnectorStore;
import org.metricshub.engine.telemetry.Monitor;
import org.metricshub.engine.telemetry.metric.NumberMetric;

class VoltageMetricNormalizerTest {

	private static final long STRATEGY_TIME = System.currentTimeMillis();
	private static final String HOSTNAME = "hostname";
	private static final String HW_VOLTAGE_LIMIT = "hw.voltage.limit";
	public static final String HW_VOLTAGE_LIMIT_LIMIT_TYPE_HIGH_CRITICAL =
		HW_VOLTAGE_LIMIT + "{limit_type=\"high.critical\"}";
	public static final String HW_VOLTAGE_LIMIT_LIMIT_TYPE_LOW_CRITICAL =
		HW_VOLTAGE_LIMIT + "{limit_type=\"low.critical\"}";
	public static final String HW_VOLTAGE_LIMIT_LIMIT_TYPE_HIGH_DEGRADED =
		HW_VOLTAGE_LIMIT + "{limit_type=\"high.degraded\"}";
	public static final String HW_VOLTAGE_LIMIT_LIMIT_TYPE_LOW_DEGRADED =
		HW_VOLTAGE_LIMIT + "{limit_type=\"low.degraded\"}";

	@Test
	void testNormalize() {
		{
			//Testing when both low critical and high critical metrics are present and high critical < low critical
			final NumberMetric hwVoltageMetric = NumberMetric.builder().value(1.0).name("hw.voltage").build();
			hwVoltageMetric.setCollectTime(STRATEGY_TIME);
			hwVoltageMetric.setPreviousCollectTime(STRATEGY_TIME - 1000 * 60 * 2);
			final NumberMetric hwVoltageLimitHighCriticalMetric = NumberMetric.builder()
				.value(10.0)
				.name(HW_VOLTAGE_LIMIT_LIMIT_TYPE_HIGH_CRITICAL)
				.attributes(Map.of("limit_type", "high.critical"))
				.build();
			hwVoltageLimitHighCriticalMetric.setCollectTime(STRATEGY_TIME);
			hwVoltageLimitHighCriticalMetric.setPreviousCollectTime(STRATEGY_TIME - 1000 * 60 * 2);
			final NumberMetric hwVoltageLimitLowCriticaldMetric = NumberMetric.builder()
				.value(15.0)
				.name(HW_VOLTAGE_LIMIT_LIMIT_TYPE_LOW_CRITICAL)
				.attributes(Map.of("limit_type", "low.critical"))
				.build();
			hwVoltageLimitLowCriticaldMetric.setCollectTime(STRATEGY_TIME);
			hwVoltageLimitLowCriticaldMetric.setPreviousCollectTime(STRATEGY_TIME - 1000 * 60 * 2);
			final Monitor monitorWithHwVoltageLimitMetric = Monitor.builder()
				.id("monitorOne")
				.type("voltage")
				.metrics(
					new HashMap<>(
						Map.of(
							"hw.voltage",
							hwVoltageMetric,
							HW_VOLTAGE_LIMIT_LIMIT_TYPE_HIGH_CRITICAL,
							hwVoltageLimitHighCriticalMetric,
							HW_VOLTAGE_LIMIT_LIMIT_TYPE_LOW_CRITICAL,
							hwVoltageLimitLowCriticaldMetric
						)
					)
				)
				.build();

			new VoltageMetricNormalizer(STRATEGY_TIME, HOSTNAME, new ConnectorStore()).normalize(
				monitorWithHwVoltageLimitMetric
			);
			assertEquals(
				15.0,
				monitorWithHwVoltageLimitMetric
					.getMetric(HW_VOLTAGE_LIMIT_LIMIT_TYPE_HIGH_CRITICAL, NumberMetric.class)
					.getValue()
			);
			assertEquals(
				10.0,
				monitorWithHwVoltageLimitMetric
					.getMetric(HW_VOLTAGE_LIMIT_LIMIT_TYPE_LOW_CRITICAL, NumberMetric.class)
					.getValue()
			);
		}

		{
			//Testing when both low critical and high critical metrics are present and high critical > low critical
			final NumberMetric hwVoltageMetric = NumberMetric.builder().value(1.0).name("hw.voltage").build();
			hwVoltageMetric.setCollectTime(STRATEGY_TIME);
			hwVoltageMetric.setPreviousCollectTime(STRATEGY_TIME - 1000 * 60 * 2);
			final NumberMetric hwVoltageLimitHighCriticalMetric = NumberMetric.builder()
				.value(15.0)
				.name(HW_VOLTAGE_LIMIT_LIMIT_TYPE_HIGH_CRITICAL)
				.attributes(Map.of("limit_type", "high.critical"))
				.build();
			hwVoltageLimitHighCriticalMetric.setCollectTime(STRATEGY_TIME);
			hwVoltageLimitHighCriticalMetric.setPreviousCollectTime(STRATEGY_TIME - 1000 * 60 * 2);
			final NumberMetric hwVoltageLimitLowCriticaldMetric = NumberMetric.builder()
				.value(10.0)
				.name(HW_VOLTAGE_LIMIT_LIMIT_TYPE_LOW_CRITICAL)
				.attributes(Map.of("limit_type", "low.critical"))
				.build();
			hwVoltageLimitLowCriticaldMetric.setCollectTime(STRATEGY_TIME);
			hwVoltageLimitLowCriticaldMetric.setPreviousCollectTime(STRATEGY_TIME - 1000 * 60 * 2);
			final Monitor monitorWithHwVoltageLimitMetric = Monitor.builder()
				.id("monitorOne")
				.type("voltage")
				.metrics(
					new HashMap<>(
						Map.of(
							"hw.voltage",
							hwVoltageMetric,
							HW_VOLTAGE_LIMIT_LIMIT_TYPE_HIGH_CRITICAL,
							hwVoltageLimitHighCriticalMetric,
							HW_VOLTAGE_LIMIT_LIMIT_TYPE_LOW_CRITICAL,
							hwVoltageLimitLowCriticaldMetric
						)
					)
				)
				.build();

			new VoltageMetricNormalizer(STRATEGY_TIME, HOSTNAME, new ConnectorStore()).normalize(
				monitorWithHwVoltageLimitMetric
			);
			assertEquals(
				15.0,
				monitorWithHwVoltageLimitMetric
					.getMetric(HW_VOLTAGE_LIMIT_LIMIT_TYPE_HIGH_CRITICAL, NumberMetric.class)
					.getValue()
			);
			assertEquals(
				10.0,
				monitorWithHwVoltageLimitMetric
					.getMetric(HW_VOLTAGE_LIMIT_LIMIT_TYPE_LOW_CRITICAL, NumberMetric.class)
					.getValue()
			);
		}

		{
			// Testing when only the high critical metric is present
			final NumberMetric hwVoltageMetric = NumberMetric.builder().value(1.0).name("hw.voltage").build();
			hwVoltageMetric.setCollectTime(STRATEGY_TIME);
			hwVoltageMetric.setPreviousCollectTime(STRATEGY_TIME - 1000 * 60 * 2);
			final NumberMetric hwVoltageLimitHighCriticalMetric = NumberMetric.builder()
				.value(10.0)
				.name(HW_VOLTAGE_LIMIT_LIMIT_TYPE_HIGH_CRITICAL)
				.attributes(Map.of("limit_type", "high.critical"))
				.build();
			hwVoltageLimitHighCriticalMetric.setCollectTime(STRATEGY_TIME);
			hwVoltageLimitHighCriticalMetric.setPreviousCollectTime(STRATEGY_TIME - 1000 * 60 * 2);
			final Monitor monitorWithHwVoltageLimitMetric = Monitor.builder()
				.id("monitorOne")
				.type("voltage")
				.metrics(
					new HashMap<>(
						Map.of(
							"hw.voltage",
							hwVoltageMetric,
							HW_VOLTAGE_LIMIT_LIMIT_TYPE_HIGH_CRITICAL,
							hwVoltageLimitHighCriticalMetric
						)
					)
				)
				.build();

			new VoltageMetricNormalizer(STRATEGY_TIME, HOSTNAME, new ConnectorStore()).normalize(
				monitorWithHwVoltageLimitMetric
			);
			assertEquals(
				10.0,
				monitorWithHwVoltageLimitMetric
					.getMetric(HW_VOLTAGE_LIMIT_LIMIT_TYPE_HIGH_CRITICAL, NumberMetric.class)
					.getValue()
			);
			assertEquals(
				9.0,
				monitorWithHwVoltageLimitMetric
					.getMetric(HW_VOLTAGE_LIMIT_LIMIT_TYPE_HIGH_DEGRADED, NumberMetric.class)
					.getValue()
			);
		}

		{
			// Testing when only the high critical metric is present and < = 0
			final NumberMetric hwVoltageMetric = NumberMetric.builder().value(1.0).name("hw.voltage").build();
			hwVoltageMetric.setCollectTime(STRATEGY_TIME);
			hwVoltageMetric.setPreviousCollectTime(STRATEGY_TIME - 1000 * 60 * 2);
			final NumberMetric hwVoltageLimitHighCriticalMetric = NumberMetric.builder()
				.value(-10.0)
				.name(HW_VOLTAGE_LIMIT_LIMIT_TYPE_HIGH_CRITICAL)
				.attributes(Map.of("limit_type", "high.critical"))
				.build();
			hwVoltageLimitHighCriticalMetric.setCollectTime(STRATEGY_TIME);
			hwVoltageLimitHighCriticalMetric.setPreviousCollectTime(STRATEGY_TIME - 1000 * 60 * 2);
			final Monitor monitorWithHwVoltageLimitMetric = Monitor.builder()
				.id("monitorOne")
				.type("voltage")
				.metrics(
					new HashMap<>(
						Map.of(
							"hw.voltage",
							hwVoltageMetric,
							HW_VOLTAGE_LIMIT_LIMIT_TYPE_HIGH_CRITICAL,
							hwVoltageLimitHighCriticalMetric
						)
					)
				)
				.build();

			new VoltageMetricNormalizer(STRATEGY_TIME, HOSTNAME, new ConnectorStore()).normalize(
				monitorWithHwVoltageLimitMetric
			);
			assertEquals(
				-10.0,
				monitorWithHwVoltageLimitMetric
					.getMetric(HW_VOLTAGE_LIMIT_LIMIT_TYPE_HIGH_CRITICAL, NumberMetric.class)
					.getValue()
			);
			assertEquals(
				-11.0,
				monitorWithHwVoltageLimitMetric
					.getMetric(HW_VOLTAGE_LIMIT_LIMIT_TYPE_HIGH_DEGRADED, NumberMetric.class)
					.getValue()
			);
		}

		{
			// Testing when only the low critical metric is present
			final NumberMetric hwVoltageMetric = NumberMetric.builder().value(1.0).name("hw.voltage").build();
			hwVoltageMetric.setCollectTime(STRATEGY_TIME);
			hwVoltageMetric.setPreviousCollectTime(STRATEGY_TIME - 1000 * 60 * 2);
			final NumberMetric hwVoltageLimitLowCriticalMetric = NumberMetric.builder()
				.value(10.0)
				.name(HW_VOLTAGE_LIMIT_LIMIT_TYPE_LOW_CRITICAL)
				.attributes(Map.of("limit_type", "low.critical"))
				.build();
			hwVoltageLimitLowCriticalMetric.setCollectTime(STRATEGY_TIME);
			hwVoltageLimitLowCriticalMetric.setPreviousCollectTime(STRATEGY_TIME - 1000 * 60 * 2);
			final Monitor monitorWithHwVoltageLimitMetric = Monitor.builder()
				.id("monitorOne")
				.type("voltage")
				.metrics(
					new HashMap<>(
						Map.of(
							"hw.voltage",
							hwVoltageMetric,
							HW_VOLTAGE_LIMIT_LIMIT_TYPE_LOW_CRITICAL,
							hwVoltageLimitLowCriticalMetric
						)
					)
				)
				.build();

			new VoltageMetricNormalizer(STRATEGY_TIME, HOSTNAME, new ConnectorStore()).normalize(
				monitorWithHwVoltageLimitMetric
			);
			assertEquals(
				11.0,
				monitorWithHwVoltageLimitMetric
					.getMetric(HW_VOLTAGE_LIMIT_LIMIT_TYPE_LOW_DEGRADED, NumberMetric.class)
					.getValue()
			);
			assertEquals(
				10.0,
				monitorWithHwVoltageLimitMetric
					.getMetric(HW_VOLTAGE_LIMIT_LIMIT_TYPE_LOW_CRITICAL, NumberMetric.class)
					.getValue()
			);
		}

		{
			// Testing when only the low critical metric is present and < = 0
			final NumberMetric hwVoltageMetric = NumberMetric.builder().value(1.0).name("hw.voltage").build();
			hwVoltageMetric.setCollectTime(STRATEGY_TIME);
			hwVoltageMetric.setPreviousCollectTime(STRATEGY_TIME - 1000 * 60 * 2);
			final NumberMetric hwVoltageLimitLowCriticalMetric = NumberMetric.builder()
				.value(-10.0)
				.name(HW_VOLTAGE_LIMIT_LIMIT_TYPE_HIGH_CRITICAL)
				.attributes(Map.of("limit_type", "low.critical"))
				.build();
			hwVoltageLimitLowCriticalMetric.setCollectTime(STRATEGY_TIME);
			hwVoltageLimitLowCriticalMetric.setPreviousCollectTime(STRATEGY_TIME - 1000 * 60 * 2);
			final Monitor monitorWithHwVoltageLimitMetric = Monitor.builder()
				.id("monitorOne")
				.type("voltage")
				.metrics(
					new HashMap<>(
						Map.of(
							"hw.voltage",
							hwVoltageMetric,
							HW_VOLTAGE_LIMIT_LIMIT_TYPE_LOW_CRITICAL,
							hwVoltageLimitLowCriticalMetric
						)
					)
				)
				.build();

			new VoltageMetricNormalizer(STRATEGY_TIME, HOSTNAME, new ConnectorStore()).normalize(
				monitorWithHwVoltageLimitMetric
			);
			assertEquals(
				-9.0,
				monitorWithHwVoltageLimitMetric
					.getMetric(HW_VOLTAGE_LIMIT_LIMIT_TYPE_LOW_DEGRADED, NumberMetric.class)
					.getValue()
			);
			assertEquals(
				-10.0,
				monitorWithHwVoltageLimitMetric
					.getMetric(HW_VOLTAGE_LIMIT_LIMIT_TYPE_LOW_CRITICAL, NumberMetric.class)
					.getValue()
			);
		}
	}

	/**
	 * Build a voltage monitor with a 12.01 V reading and the given limits, then normalize it.
	 *
	 * @param limits limit_type to value
	 * @return the normalized monitor
	 */
	private static Monitor normalizeLimits(final Map<String, Double> limits) {
		final Monitor monitor = Monitor.builder().id("monitorOne").type("voltage").build();
		final NumberMetric reading = NumberMetric.builder().value(12.01).name("hw.voltage").build();
		reading.setCollectTime(STRATEGY_TIME);
		monitor.getMetrics().put("hw.voltage", reading);
		limits.forEach((limitType, value) -> {
			final String name = HW_VOLTAGE_LIMIT + "{limit_type=\"" + limitType + "\"}";
			final NumberMetric metric = NumberMetric.builder()
				.value(value)
				.name(name)
				.attributes(Map.of("limit_type", limitType))
				.build();
			metric.setCollectTime(STRATEGY_TIME);
			monitor.getMetrics().put(name, metric);
		});
		new VoltageMetricNormalizer(STRATEGY_TIME, HOSTNAME, new ConnectorStore()).normalize(monitor);
		return monitor;
	}

	private static Double limit(final Monitor monitor, final String name) {
		final NumberMetric metric = monitor.getMetric(name, NumberMetric.class);
		return metric == null ? null : metric.getValue();
	}

	@Test
	void testCollectedDegradedLimitIsKept() {
		final Monitor low = normalizeLimits(Map.of("low.critical", 10.5, "low.degraded", 11.0));
		assertEquals(11.0, limit(low, HW_VOLTAGE_LIMIT_LIMIT_TYPE_LOW_DEGRADED));
		assertEquals(10.5, limit(low, HW_VOLTAGE_LIMIT_LIMIT_TYPE_LOW_CRITICAL));

		final Monitor high = normalizeLimits(Map.of("high.critical", 13.5, "high.degraded", 13.0));
		assertEquals(13.0, limit(high, HW_VOLTAGE_LIMIT_LIMIT_TYPE_HIGH_DEGRADED));
		assertEquals(13.5, limit(high, HW_VOLTAGE_LIMIT_LIMIT_TYPE_HIGH_CRITICAL));
	}

	@Test
	void testMissingDegradedLimitIsDerived() {
		assertEquals(
			11.55,
			limit(normalizeLimits(Map.of("low.critical", 10.5)), HW_VOLTAGE_LIMIT_LIMIT_TYPE_LOW_DEGRADED),
			1e-9
		);
		assertEquals(
			12.15,
			limit(normalizeLimits(Map.of("high.critical", 13.5)), HW_VOLTAGE_LIMIT_LIMIT_TYPE_HIGH_DEGRADED),
			1e-9
		);
		assertEquals(
			-9.72,
			limit(normalizeLimits(Map.of("low.critical", -10.8)), HW_VOLTAGE_LIMIT_LIMIT_TYPE_LOW_DEGRADED),
			1e-9
		);
	}

	@Test
	void testOutOfRangeLimitsAreDiscarded() {
		final Monitor highInvalid = normalizeLimits(Map.of("low.critical", 10.5, "high.critical", 65535.0));
		assertNull(limit(highInvalid, HW_VOLTAGE_LIMIT_LIMIT_TYPE_HIGH_CRITICAL));
		assertEquals(10.5, limit(highInvalid, HW_VOLTAGE_LIMIT_LIMIT_TYPE_LOW_CRITICAL));
		assertEquals(11.55, limit(highInvalid, HW_VOLTAGE_LIMIT_LIMIT_TYPE_LOW_DEGRADED), 1e-9);

		final Monitor lowInvalid = normalizeLimits(Map.of("low.critical", -1000.0, "high.critical", 13.5));
		assertNull(limit(lowInvalid, HW_VOLTAGE_LIMIT_LIMIT_TYPE_LOW_CRITICAL));
		assertEquals(13.5, limit(lowInvalid, HW_VOLTAGE_LIMIT_LIMIT_TYPE_HIGH_CRITICAL));
		assertEquals(12.15, limit(lowInvalid, HW_VOLTAGE_LIMIT_LIMIT_TYPE_HIGH_DEGRADED), 1e-9);

		final Monitor degradedInvalid = normalizeLimits(Map.of("high.critical", 13.5, "high.degraded", 450.1));
		assertEquals(12.15, limit(degradedInvalid, HW_VOLTAGE_LIMIT_LIMIT_TYPE_HIGH_DEGRADED), 1e-9);

		final Monitor bounds = normalizeLimits(Map.of("low.critical", -100.0, "high.critical", 450.0));
		assertEquals(-100.0, limit(bounds, HW_VOLTAGE_LIMIT_LIMIT_TYPE_LOW_CRITICAL));
		assertEquals(450.0, limit(bounds, HW_VOLTAGE_LIMIT_LIMIT_TYPE_HIGH_CRITICAL));
	}

	@Test
	void testInvertedCriticalLimitsAreSwapped() {
		final Monitor monitor = normalizeLimits(Map.of("low.critical", 13.5, "high.critical", 10.5));
		assertEquals(10.5, limit(monitor, HW_VOLTAGE_LIMIT_LIMIT_TYPE_LOW_CRITICAL));
		assertEquals(13.5, limit(monitor, HW_VOLTAGE_LIMIT_LIMIT_TYPE_HIGH_CRITICAL));
	}
}
