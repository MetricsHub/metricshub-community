package org.metricshub.hardware.threshold;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.sentrysoftware.metricshub.engine.telemetry.Monitor;
import org.sentrysoftware.metricshub.engine.telemetry.metric.NumberMetric;

class FanMetricNormalizerTest {

	private static final long STRATEGY_TIME = System.currentTimeMillis();
	private static final String HOSTNAME = "hostname";
	private static final String HW_FAN_SPEED_LIMIT = "hw.fan.speed.limit";
	public static final String HW_FAN_SPEED_LIMIT_LIMIT_TYPE_LOW_CRITICAL =
		HW_FAN_SPEED_LIMIT + "{limit_type=\"low.critical\"}";
	public static final String HW_FAN_SPEED_LIMIT_LIMIT_TYPE_LOW_DEGRADED =
		HW_FAN_SPEED_LIMIT + "{limit_type=\"low.degraded\"}";

	@Test
	void testNormalize() {
		{
			//Testing when both low degraded and low critical metrics are present and low degraded < low critical
			final NumberMetric hwFanSpeedMetric = NumberMetric.builder().value(1.0).name("hw.fan.speed").build();
			hwFanSpeedMetric.setCollectTime(STRATEGY_TIME);
			hwFanSpeedMetric.setPreviousCollectTime(STRATEGY_TIME - 1000 * 60 * 2);
			final NumberMetric hwFanSpeedLimitCriticalMetric = NumberMetric
				.builder()
				.value(2.0)
				.name(HW_FAN_SPEED_LIMIT_LIMIT_TYPE_LOW_CRITICAL)
				.attributes(Map.of("limit_type", "low.critical"))
				.build();
			hwFanSpeedLimitCriticalMetric.setCollectTime(STRATEGY_TIME);
			hwFanSpeedLimitCriticalMetric.setPreviousCollectTime(STRATEGY_TIME - 1000 * 60 * 2);
			final NumberMetric hwFanSpeedLimitDegradedMetric = NumberMetric
				.builder()
				.value(1.0)
				.name(HW_FAN_SPEED_LIMIT_LIMIT_TYPE_LOW_DEGRADED)
				.attributes(Map.of("limit_type", "low.degraded"))
				.build();
			hwFanSpeedLimitDegradedMetric.setCollectTime(STRATEGY_TIME);
			hwFanSpeedLimitDegradedMetric.setPreviousCollectTime(STRATEGY_TIME - 1000 * 60 * 2);
			final Monitor monitorWithHwFanSpeedLimitMetric = Monitor
				.builder()
				.id("monitorOne")
				.type("fan")
				.metrics(
					new HashMap<>(
						Map.of(
							"hw.fan.speed",
							hwFanSpeedMetric,
							HW_FAN_SPEED_LIMIT_LIMIT_TYPE_LOW_CRITICAL,
							hwFanSpeedLimitCriticalMetric,
							HW_FAN_SPEED_LIMIT_LIMIT_TYPE_LOW_DEGRADED,
							hwFanSpeedLimitDegradedMetric
						)
					)
				)
				.build();

			new FanMetricNormalizer(STRATEGY_TIME, HOSTNAME).normalize(monitorWithHwFanSpeedLimitMetric);
			assertEquals(
				1.0,
				monitorWithHwFanSpeedLimitMetric
					.getMetric(HW_FAN_SPEED_LIMIT_LIMIT_TYPE_LOW_CRITICAL, NumberMetric.class)
					.getValue()
			);
			assertEquals(
				2.0,
				monitorWithHwFanSpeedLimitMetric
					.getMetric(HW_FAN_SPEED_LIMIT_LIMIT_TYPE_LOW_DEGRADED, NumberMetric.class)
					.getValue()
			);
		}

		{
			// testing when both low degraded and low critical metrics are present and low degraded > low critical
			final NumberMetric hwFanSpeedMetric = NumberMetric.builder().value(1.0).name("hw.fan.speed").build();
			hwFanSpeedMetric.setCollectTime(STRATEGY_TIME);
			hwFanSpeedMetric.setPreviousCollectTime(STRATEGY_TIME - 1000 * 60 * 2);
			final NumberMetric hwFanSpeedLimitCriticalMetric = NumberMetric
				.builder()
				.value(1.0)
				.name(HW_FAN_SPEED_LIMIT_LIMIT_TYPE_LOW_CRITICAL)
				.attributes(Map.of("limit_type", "low.critical"))
				.build();
			hwFanSpeedLimitCriticalMetric.setCollectTime(STRATEGY_TIME);
			hwFanSpeedLimitCriticalMetric.setPreviousCollectTime(STRATEGY_TIME - 1000 * 60 * 2);
			final NumberMetric hwFanSpeedLimitDegradedMetric = NumberMetric
				.builder()
				.value(2.0)
				.name(HW_FAN_SPEED_LIMIT_LIMIT_TYPE_LOW_DEGRADED)
				.attributes(Map.of("limit_type", "low.degraded"))
				.build();
			hwFanSpeedLimitDegradedMetric.setCollectTime(STRATEGY_TIME);
			hwFanSpeedLimitDegradedMetric.setPreviousCollectTime(STRATEGY_TIME - 1000 * 60 * 2);
			final Monitor monitorWithHwFanSpeedLimitMetric = Monitor
				.builder()
				.id("monitorOne")
				.type("fan")
				.metrics(
					new HashMap<>(
						Map.of(
							"hw.fan.speed",
							hwFanSpeedMetric,
							HW_FAN_SPEED_LIMIT_LIMIT_TYPE_LOW_CRITICAL,
							hwFanSpeedLimitCriticalMetric,
							HW_FAN_SPEED_LIMIT_LIMIT_TYPE_LOW_DEGRADED,
							hwFanSpeedLimitDegradedMetric
						)
					)
				)
				.build();

			new FanMetricNormalizer(STRATEGY_TIME, HOSTNAME).normalize(monitorWithHwFanSpeedLimitMetric);
			assertEquals(
				1.0,
				monitorWithHwFanSpeedLimitMetric
					.getMetric(HW_FAN_SPEED_LIMIT_LIMIT_TYPE_LOW_CRITICAL, NumberMetric.class)
					.getValue()
			);
			assertEquals(
				2.0,
				monitorWithHwFanSpeedLimitMetric
					.getMetric(HW_FAN_SPEED_LIMIT_LIMIT_TYPE_LOW_DEGRADED, NumberMetric.class)
					.getValue()
			);
		}

		{
			// Testing when both low degraded and low critical metrics are absent
			final NumberMetric hwFanSpeedMetric = NumberMetric.builder().value(1.0).name("hw.fan.speed").build();
			hwFanSpeedMetric.setCollectTime(STRATEGY_TIME);
			hwFanSpeedMetric.setPreviousCollectTime(STRATEGY_TIME - 1000 * 60 * 2);

			final Monitor monitorWithoutHwFanSpeedLimit = Monitor
				.builder()
				.id("monitorOne")
				.type("fan")
				.metrics(new HashMap<>(Map.of("hw.fan.speed", hwFanSpeedMetric)))
				.build();

			new FanMetricNormalizer(STRATEGY_TIME, HOSTNAME).normalize(monitorWithoutHwFanSpeedLimit);

			assertEquals(
				0.0,
				monitorWithoutHwFanSpeedLimit
					.getMetric(HW_FAN_SPEED_LIMIT_LIMIT_TYPE_LOW_CRITICAL, NumberMetric.class)
					.getValue()
			);

			assertEquals(
				500,
				monitorWithoutHwFanSpeedLimit
					.getMetric(HW_FAN_SPEED_LIMIT_LIMIT_TYPE_LOW_DEGRADED, NumberMetric.class)
					.getValue()
			);
		}

		{
			// Testing when only the low critical metric is present
			final NumberMetric hwFanSpeedMetric = NumberMetric.builder().value(1.0).name("hw.fan.speed").build();
			hwFanSpeedMetric.setCollectTime(STRATEGY_TIME);
			hwFanSpeedMetric.setPreviousCollectTime(STRATEGY_TIME - 1000 * 60 * 2);
			final NumberMetric hwFanSpeedLimitCriticalMetric = NumberMetric
				.builder()
				.value(2.0)
				.name(HW_FAN_SPEED_LIMIT_LIMIT_TYPE_LOW_CRITICAL)
				.attributes(Map.of("limit_type", "low.critical"))
				.build();
			final Monitor monitorWithHwFanSpeedLimitMetric = Monitor
				.builder()
				.id("monitorOne")
				.type("fan")
				.metrics(
					new HashMap<>(
						Map.of(
							"hw.fan.speed",
							hwFanSpeedMetric,
							HW_FAN_SPEED_LIMIT_LIMIT_TYPE_LOW_CRITICAL,
							hwFanSpeedLimitCriticalMetric
						)
					)
				)
				.build();
			hwFanSpeedLimitCriticalMetric.setCollectTime(STRATEGY_TIME);
			hwFanSpeedLimitCriticalMetric.setPreviousCollectTime(STRATEGY_TIME - 1000 * 60 * 2);
			new FanMetricNormalizer(STRATEGY_TIME, HOSTNAME).normalize(monitorWithHwFanSpeedLimitMetric);
			assertEquals(
				2.2,
				monitorWithHwFanSpeedLimitMetric
					.getMetric(HW_FAN_SPEED_LIMIT_LIMIT_TYPE_LOW_DEGRADED, NumberMetric.class)
					.getValue()
			);
		}

		{
			// Testing when only the low degraded metric is present
			final NumberMetric hwFanSpeedMetric = NumberMetric.builder().value(1.0).name("hw.fan.speed").build();
			hwFanSpeedMetric.setCollectTime(STRATEGY_TIME);
			hwFanSpeedMetric.setPreviousCollectTime(STRATEGY_TIME - 1000 * 60 * 2);
			final NumberMetric hwFanSpeedLimitDegradedMetric = NumberMetric
				.builder()
				.value(2.0)
				.name(HW_FAN_SPEED_LIMIT_LIMIT_TYPE_LOW_DEGRADED)
				.attributes(Map.of("limit_type", "low.degraded"))
				.build();
			hwFanSpeedLimitDegradedMetric.setCollectTime(STRATEGY_TIME);
			hwFanSpeedLimitDegradedMetric.setPreviousCollectTime(STRATEGY_TIME - 1000 * 60 * 2);
			final Monitor monitorWithHwFanSpeedLimitMetric = Monitor
				.builder()
				.id("monitorOne")
				.type("fan")
				.metrics(
					new HashMap<>(
						Map.of(
							"hw.fan.speed",
							hwFanSpeedMetric,
							HW_FAN_SPEED_LIMIT_LIMIT_TYPE_LOW_DEGRADED,
							hwFanSpeedLimitDegradedMetric
						)
					)
				)
				.build();
			new FanMetricNormalizer(STRATEGY_TIME, HOSTNAME).normalize(monitorWithHwFanSpeedLimitMetric);
			assertEquals(
				1.8,
				monitorWithHwFanSpeedLimitMetric
					.getMetric(HW_FAN_SPEED_LIMIT_LIMIT_TYPE_LOW_CRITICAL, NumberMetric.class)
					.getValue()
			);
		}
	}
}
