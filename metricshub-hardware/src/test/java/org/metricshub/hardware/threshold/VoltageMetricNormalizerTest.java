package org.metricshub.hardware.threshold;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
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
			final NumberMetric hwVoltageLimitHighCriticalMetric = NumberMetric
				.builder()
				.value(10.0)
				.name(HW_VOLTAGE_LIMIT_LIMIT_TYPE_HIGH_CRITICAL)
				.attributes(Map.of("limit_type", "high.critical"))
				.build();
			hwVoltageLimitHighCriticalMetric.setCollectTime(STRATEGY_TIME);
			hwVoltageLimitHighCriticalMetric.setPreviousCollectTime(STRATEGY_TIME - 1000 * 60 * 2);
			final NumberMetric hwVoltageLimitLowCriticaldMetric = NumberMetric
				.builder()
				.value(15.0)
				.name(HW_VOLTAGE_LIMIT_LIMIT_TYPE_LOW_CRITICAL)
				.attributes(Map.of("limit_type", "low.critical"))
				.build();
			hwVoltageLimitLowCriticaldMetric.setCollectTime(STRATEGY_TIME);
			hwVoltageLimitLowCriticaldMetric.setPreviousCollectTime(STRATEGY_TIME - 1000 * 60 * 2);
			final Monitor monitorWithHwVoltageLimitMetric = Monitor
				.builder()
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

			new VoltageMetricNormalizer(STRATEGY_TIME, HOSTNAME).normalize(monitorWithHwVoltageLimitMetric);
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
			final NumberMetric hwVoltageLimitHighCriticalMetric = NumberMetric
				.builder()
				.value(15.0)
				.name(HW_VOLTAGE_LIMIT_LIMIT_TYPE_HIGH_CRITICAL)
				.attributes(Map.of("limit_type", "high.critical"))
				.build();
			hwVoltageLimitHighCriticalMetric.setCollectTime(STRATEGY_TIME);
			hwVoltageLimitHighCriticalMetric.setPreviousCollectTime(STRATEGY_TIME - 1000 * 60 * 2);
			final NumberMetric hwVoltageLimitLowCriticaldMetric = NumberMetric
				.builder()
				.value(10.0)
				.name(HW_VOLTAGE_LIMIT_LIMIT_TYPE_LOW_CRITICAL)
				.attributes(Map.of("limit_type", "low.critical"))
				.build();
			hwVoltageLimitLowCriticaldMetric.setCollectTime(STRATEGY_TIME);
			hwVoltageLimitLowCriticaldMetric.setPreviousCollectTime(STRATEGY_TIME - 1000 * 60 * 2);
			final Monitor monitorWithHwVoltageLimitMetric = Monitor
				.builder()
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

			new VoltageMetricNormalizer(STRATEGY_TIME, HOSTNAME).normalize(monitorWithHwVoltageLimitMetric);
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
			final NumberMetric hwVoltageLimitHighCriticalMetric = NumberMetric
				.builder()
				.value(10.0)
				.name(HW_VOLTAGE_LIMIT_LIMIT_TYPE_HIGH_CRITICAL)
				.attributes(Map.of("limit_type", "high.critical"))
				.build();
			hwVoltageLimitHighCriticalMetric.setCollectTime(STRATEGY_TIME);
			hwVoltageLimitHighCriticalMetric.setPreviousCollectTime(STRATEGY_TIME - 1000 * 60 * 2);
			final Monitor monitorWithHwVoltageLimitMetric = Monitor
				.builder()
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

			new VoltageMetricNormalizer(STRATEGY_TIME, HOSTNAME).normalize(monitorWithHwVoltageLimitMetric);
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
			final NumberMetric hwVoltageLimitHighCriticalMetric = NumberMetric
				.builder()
				.value(-10.0)
				.name(HW_VOLTAGE_LIMIT_LIMIT_TYPE_HIGH_CRITICAL)
				.attributes(Map.of("limit_type", "high.critical"))
				.build();
			hwVoltageLimitHighCriticalMetric.setCollectTime(STRATEGY_TIME);
			hwVoltageLimitHighCriticalMetric.setPreviousCollectTime(STRATEGY_TIME - 1000 * 60 * 2);
			final Monitor monitorWithHwVoltageLimitMetric = Monitor
				.builder()
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

			new VoltageMetricNormalizer(STRATEGY_TIME, HOSTNAME).normalize(monitorWithHwVoltageLimitMetric);
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
			final NumberMetric hwVoltageLimitLowCriticalMetric = NumberMetric
				.builder()
				.value(10.0)
				.name(HW_VOLTAGE_LIMIT_LIMIT_TYPE_LOW_CRITICAL)
				.attributes(Map.of("limit_type", "low.critical"))
				.build();
			hwVoltageLimitLowCriticalMetric.setCollectTime(STRATEGY_TIME);
			hwVoltageLimitLowCriticalMetric.setPreviousCollectTime(STRATEGY_TIME - 1000 * 60 * 2);
			final Monitor monitorWithHwVoltageLimitMetric = Monitor
				.builder()
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

			new VoltageMetricNormalizer(STRATEGY_TIME, HOSTNAME).normalize(monitorWithHwVoltageLimitMetric);
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
			final NumberMetric hwVoltageLimitLowCriticalMetric = NumberMetric
				.builder()
				.value(-10.0)
				.name(HW_VOLTAGE_LIMIT_LIMIT_TYPE_HIGH_CRITICAL)
				.attributes(Map.of("limit_type", "low.critical"))
				.build();
			hwVoltageLimitLowCriticalMetric.setCollectTime(STRATEGY_TIME);
			hwVoltageLimitLowCriticalMetric.setPreviousCollectTime(STRATEGY_TIME - 1000 * 60 * 2);
			final Monitor monitorWithHwVoltageLimitMetric = Monitor
				.builder()
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

			new VoltageMetricNormalizer(STRATEGY_TIME, HOSTNAME).normalize(monitorWithHwVoltageLimitMetric);
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
}
