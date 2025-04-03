package org.metricshub.hardware.threshold;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.sentrysoftware.metricshub.engine.telemetry.Monitor;
import org.sentrysoftware.metricshub.engine.telemetry.metric.NumberMetric;

class TemperatureMetricNormalizerTest {

	private static final long STRATEGY_TIME = System.currentTimeMillis();
	private static final String HOSTNAME = "hostname";
	private static final String HW_TEMPERATURE_LIMIT = "hw.temperature.limit";
	public static final String HW_TEMPERATURE_LIMIT_LIMIT_TYPE_HIGH_CRITICAL =
		HW_TEMPERATURE_LIMIT + "{limit_type=\"high.critical\"}";
	public static final String HW_TEMPERATURE_LIMIT_LIMIT_TYPE_HIGH_DEGRADED =
		HW_TEMPERATURE_LIMIT + "{limit_type=\"high.degraded\"}";

	@Test
	void testNormalize() {
		{
			//Testing when both high degraded and high critical metrics are present and high degraded < high critical
			final NumberMetric hwTemperatureMetric = NumberMetric.builder().value(1.0).name("hw.temperature").build();
			hwTemperatureMetric.setCollectTime(STRATEGY_TIME);
			hwTemperatureMetric.setPreviousCollectTime(STRATEGY_TIME - 1000 * 60 * 2);
			final NumberMetric hwTemperatureLimitCriticalMetric = NumberMetric
				.builder()
				.value(2.0)
				.name(HW_TEMPERATURE_LIMIT_LIMIT_TYPE_HIGH_CRITICAL)
				.attributes(Map.of("limit_type", "high.critical"))
				.build();
			hwTemperatureLimitCriticalMetric.setCollectTime(STRATEGY_TIME);
			hwTemperatureLimitCriticalMetric.setPreviousCollectTime(STRATEGY_TIME - 1000 * 60 * 2);
			final NumberMetric hwTemperatureLimitDegradedMetric = NumberMetric
				.builder()
				.value(1.0)
				.name(HW_TEMPERATURE_LIMIT_LIMIT_TYPE_HIGH_DEGRADED)
				.attributes(Map.of("limit_type", "high.degraded"))
				.build();
			hwTemperatureLimitDegradedMetric.setCollectTime(STRATEGY_TIME);
			hwTemperatureLimitDegradedMetric.setPreviousCollectTime(STRATEGY_TIME - 1000 * 60 * 2);
			final Monitor monitorWithHwTemperatureLimitMetric = Monitor
				.builder()
				.id("monitorOne")
				.type("temperature")
				.metrics(
					new HashMap<>(
						Map.of(
							"hw.temperature",
							hwTemperatureMetric,
							HW_TEMPERATURE_LIMIT_LIMIT_TYPE_HIGH_CRITICAL,
							hwTemperatureLimitCriticalMetric,
							HW_TEMPERATURE_LIMIT_LIMIT_TYPE_HIGH_DEGRADED,
							hwTemperatureLimitDegradedMetric
						)
					)
				)
				.build();

			new TemperatureMetricNormalizer(STRATEGY_TIME, HOSTNAME).normalize(monitorWithHwTemperatureLimitMetric);
			assertEquals(
				2.0,
				monitorWithHwTemperatureLimitMetric
					.getMetric(HW_TEMPERATURE_LIMIT_LIMIT_TYPE_HIGH_CRITICAL, NumberMetric.class)
					.getValue()
			);
			assertEquals(
				1.0,
				monitorWithHwTemperatureLimitMetric
					.getMetric(HW_TEMPERATURE_LIMIT_LIMIT_TYPE_HIGH_DEGRADED, NumberMetric.class)
					.getValue()
			);
		}

		{
			//Testing when both high degraded and high critical metrics are present and high degraded > high critical
			final NumberMetric hwTemperatureMetric = NumberMetric.builder().value(1.0).name("hw.temperature").build();
			hwTemperatureMetric.setCollectTime(STRATEGY_TIME);
			hwTemperatureMetric.setPreviousCollectTime(STRATEGY_TIME - 1000 * 60 * 2);
			final NumberMetric hwTemperatureLimitCriticalMetric = NumberMetric
				.builder()
				.value(2.0)
				.name(HW_TEMPERATURE_LIMIT_LIMIT_TYPE_HIGH_CRITICAL)
				.attributes(Map.of("limit_type", "high.critical"))
				.build();
			hwTemperatureLimitCriticalMetric.setCollectTime(STRATEGY_TIME);
			hwTemperatureLimitCriticalMetric.setPreviousCollectTime(STRATEGY_TIME - 1000 * 60 * 2);
			final NumberMetric hwTemperatureLimitDegradedMetric = NumberMetric
				.builder()
				.value(1.0)
				.name(HW_TEMPERATURE_LIMIT_LIMIT_TYPE_HIGH_DEGRADED)
				.attributes(Map.of("limit_type", "high.degraded"))
				.build();
			hwTemperatureLimitDegradedMetric.setCollectTime(STRATEGY_TIME);
			hwTemperatureLimitDegradedMetric.setPreviousCollectTime(STRATEGY_TIME - 1000 * 60 * 2);
			final Monitor monitorWithHwTemperatureLimitMetric = Monitor
				.builder()
				.id("monitorOne")
				.type("temperature")
				.metrics(
					new HashMap<>(
						Map.of(
							"hw.temperature",
							hwTemperatureMetric,
							HW_TEMPERATURE_LIMIT_LIMIT_TYPE_HIGH_CRITICAL,
							hwTemperatureLimitCriticalMetric,
							HW_TEMPERATURE_LIMIT_LIMIT_TYPE_HIGH_DEGRADED,
							hwTemperatureLimitDegradedMetric
						)
					)
				)
				.build();

			new TemperatureMetricNormalizer(STRATEGY_TIME, HOSTNAME).normalize(monitorWithHwTemperatureLimitMetric);
			assertEquals(
				2.0,
				monitorWithHwTemperatureLimitMetric
					.getMetric(HW_TEMPERATURE_LIMIT_LIMIT_TYPE_HIGH_CRITICAL, NumberMetric.class)
					.getValue()
			);
			assertEquals(
				1.0,
				monitorWithHwTemperatureLimitMetric
					.getMetric(HW_TEMPERATURE_LIMIT_LIMIT_TYPE_HIGH_DEGRADED, NumberMetric.class)
					.getValue()
			);
		}

		{
			// Testing when only the high critical metric is present
			final NumberMetric hwTemperatureMetric = NumberMetric.builder().value(1.0).name("hw.temperature").build();
			hwTemperatureMetric.setCollectTime(STRATEGY_TIME);
			hwTemperatureMetric.setPreviousCollectTime(STRATEGY_TIME - 1000 * 60 * 2);
			final NumberMetric hwTemperatureLimitCriticalMetric = NumberMetric
				.builder()
				.value(2.0)
				.name(HW_TEMPERATURE_LIMIT_LIMIT_TYPE_HIGH_CRITICAL)
				.attributes(Map.of("limit_type", "high.critical"))
				.build();
			hwTemperatureLimitCriticalMetric.setCollectTime(STRATEGY_TIME);
			hwTemperatureLimitCriticalMetric.setPreviousCollectTime(STRATEGY_TIME - 1000 * 60 * 2);
			final Monitor monitorWithHwTemperatureLimitMetric = Monitor
				.builder()
				.id("monitorOne")
				.type("temperature")
				.metrics(
					new HashMap<>(
						Map.of(
							"hw.temperature",
							hwTemperatureMetric,
							HW_TEMPERATURE_LIMIT_LIMIT_TYPE_HIGH_CRITICAL,
							hwTemperatureLimitCriticalMetric
						)
					)
				)
				.build();

			new TemperatureMetricNormalizer(STRATEGY_TIME, HOSTNAME).normalize(monitorWithHwTemperatureLimitMetric);
			assertEquals(
				1.8,
				monitorWithHwTemperatureLimitMetric
					.getMetric(HW_TEMPERATURE_LIMIT_LIMIT_TYPE_HIGH_DEGRADED, NumberMetric.class)
					.getValue()
			);
		}

		{
			// Testing when only the high degraded metric is present
			final NumberMetric hwTemperatureMetric = NumberMetric.builder().value(1.0).name("hw.temperature").build();
			hwTemperatureMetric.setCollectTime(STRATEGY_TIME);
			hwTemperatureMetric.setPreviousCollectTime(STRATEGY_TIME - 1000 * 60 * 2);
			final NumberMetric hwTemperatureLimitDegradedMetric = NumberMetric
				.builder()
				.value(2.0)
				.name(HW_TEMPERATURE_LIMIT_LIMIT_TYPE_HIGH_DEGRADED)
				.attributes(Map.of("limit_type", "high.degraded"))
				.build();
			hwTemperatureLimitDegradedMetric.setCollectTime(STRATEGY_TIME);
			hwTemperatureLimitDegradedMetric.setPreviousCollectTime(STRATEGY_TIME - 1000 * 60 * 2);
			final Monitor monitorWithHwTemperatureLimitMetric = Monitor
				.builder()
				.id("monitorOne")
				.type("temperature")
				.metrics(
					new HashMap<>(
						Map.of(
							"hw.temperature",
							hwTemperatureMetric,
							HW_TEMPERATURE_LIMIT_LIMIT_TYPE_HIGH_DEGRADED,
							hwTemperatureLimitDegradedMetric
						)
					)
				)
				.build();
			new TemperatureMetricNormalizer(STRATEGY_TIME, HOSTNAME).normalize(monitorWithHwTemperatureLimitMetric);
			assertEquals(
				2.2,
				monitorWithHwTemperatureLimitMetric
					.getMetric(HW_TEMPERATURE_LIMIT_LIMIT_TYPE_HIGH_CRITICAL, NumberMetric.class)
					.getValue()
			);
		}
	}
}
