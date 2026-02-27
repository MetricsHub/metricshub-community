package org.metricshub.hardware.threshold;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.metricshub.engine.connector.model.ConnectorStore;
import org.metricshub.engine.telemetry.Monitor;
import org.metricshub.engine.telemetry.metric.NumberMetric;

class OtherDeviceMetricNormalizerTest {

	private static final long STRATEGY_TIME = System.currentTimeMillis();
	private static final String HOSTNAME = "hostname";
	private static final String HW_OTHER_DEVICE_USES_LIMIT = "hw.other_device.uses.limit";
	public static final String HW_OTHER_DEVICE_USES_LIMIT_LIMIT_TYPE_CRITICAL =
		HW_OTHER_DEVICE_USES_LIMIT + "{limit_type=\"critical\"}";
	public static final String HW_OTHER_DEVICE_USES_LIMIT_LIMIT_TYPE_DEGRADED =
		HW_OTHER_DEVICE_USES_LIMIT + "{limit_type=\"degraded\"}";

	@Test
	void testNormalize() {
		{
			//Testing when both degraded and critical metrics are present and degraded < critical
			final NumberMetric hwOtherDeviceUsesMetric = NumberMetric
				.builder()
				.value(1.0)
				.name("hw.other_device.uses")
				.build();
			hwOtherDeviceUsesMetric.setCollectTime(STRATEGY_TIME);
			hwOtherDeviceUsesMetric.setPreviousCollectTime(STRATEGY_TIME - 1000 * 60 * 2);
			final NumberMetric hwOtherDeviceUsesLimitCriticalMetric = NumberMetric
				.builder()
				.value(2.0)
				.name(HW_OTHER_DEVICE_USES_LIMIT_LIMIT_TYPE_CRITICAL)
				.attributes(Map.of("limit_type", "critical"))
				.build();
			hwOtherDeviceUsesLimitCriticalMetric.setCollectTime(STRATEGY_TIME);
			hwOtherDeviceUsesLimitCriticalMetric.setPreviousCollectTime(STRATEGY_TIME - 1000 * 60 * 2);
			final NumberMetric hwOtherDeviceUsesLimitDegradedMetric = NumberMetric
				.builder()
				.value(1.0)
				.name(HW_OTHER_DEVICE_USES_LIMIT_LIMIT_TYPE_DEGRADED)
				.attributes(Map.of("limit_type", "degraded"))
				.build();
			hwOtherDeviceUsesLimitDegradedMetric.setCollectTime(STRATEGY_TIME);
			hwOtherDeviceUsesLimitDegradedMetric.setPreviousCollectTime(STRATEGY_TIME - 1000 * 60 * 2);
			final Monitor monitorWithHwOtherDeviceUsesLimitMetric = Monitor
				.builder()
				.id("monitorOne")
				.type("other_device")
				.metrics(
					new HashMap<>(
						Map.of(
							"hw.other_device.uses",
							hwOtherDeviceUsesMetric,
							HW_OTHER_DEVICE_USES_LIMIT_LIMIT_TYPE_CRITICAL,
							hwOtherDeviceUsesLimitCriticalMetric,
							HW_OTHER_DEVICE_USES_LIMIT_LIMIT_TYPE_DEGRADED,
							hwOtherDeviceUsesLimitDegradedMetric
						)
					)
				)
				.build();

			new OtherDeviceMetricNormalizer(STRATEGY_TIME, HOSTNAME, new ConnectorStore())
				.normalize(monitorWithHwOtherDeviceUsesLimitMetric);
			assertEquals(
				1.0,
				monitorWithHwOtherDeviceUsesLimitMetric
					.getMetric(HW_OTHER_DEVICE_USES_LIMIT_LIMIT_TYPE_CRITICAL, NumberMetric.class)
					.getValue()
			);
			assertEquals(
				2.0,
				monitorWithHwOtherDeviceUsesLimitMetric
					.getMetric(HW_OTHER_DEVICE_USES_LIMIT_LIMIT_TYPE_DEGRADED, NumberMetric.class)
					.getValue()
			);
		}

		{
			// testing when both degraded and critical metrics are present and degraded > critical
			final NumberMetric hwOtherDeviceUsesMetric = NumberMetric
				.builder()
				.value(1.0)
				.name("hw.other_device.uses")
				.build();
			hwOtherDeviceUsesMetric.setCollectTime(STRATEGY_TIME);
			hwOtherDeviceUsesMetric.setPreviousCollectTime(STRATEGY_TIME - 1000 * 60 * 2);
			final NumberMetric hwOtherDeviceUsesLimitCriticalMetric = NumberMetric
				.builder()
				.value(1.0)
				.name(HW_OTHER_DEVICE_USES_LIMIT_LIMIT_TYPE_CRITICAL)
				.attributes(Map.of("limit_type", "critical"))
				.build();
			hwOtherDeviceUsesLimitCriticalMetric.setCollectTime(STRATEGY_TIME);
			hwOtherDeviceUsesLimitCriticalMetric.setPreviousCollectTime(STRATEGY_TIME - 1000 * 60 * 2);
			final NumberMetric hwOtherDeviceUsesLimitDegradedMetric = NumberMetric
				.builder()
				.value(2.0)
				.name(HW_OTHER_DEVICE_USES_LIMIT_LIMIT_TYPE_DEGRADED)
				.attributes(Map.of("limit_type", "degraded"))
				.build();
			hwOtherDeviceUsesLimitDegradedMetric.setCollectTime(STRATEGY_TIME);
			hwOtherDeviceUsesLimitDegradedMetric.setPreviousCollectTime(STRATEGY_TIME - 1000 * 60 * 2);
			final Monitor monitorWithHwOtherDeviceUsesLimitMetric = Monitor
				.builder()
				.id("monitorOne")
				.type("other_device")
				.metrics(
					new HashMap<>(
						Map.of(
							"hw.other_device.uses",
							hwOtherDeviceUsesMetric,
							HW_OTHER_DEVICE_USES_LIMIT_LIMIT_TYPE_CRITICAL,
							hwOtherDeviceUsesLimitCriticalMetric,
							HW_OTHER_DEVICE_USES_LIMIT_LIMIT_TYPE_DEGRADED,
							hwOtherDeviceUsesLimitDegradedMetric
						)
					)
				)
				.build();

			new OtherDeviceMetricNormalizer(STRATEGY_TIME, HOSTNAME, new ConnectorStore())
				.normalize(monitorWithHwOtherDeviceUsesLimitMetric);
			assertEquals(
				1.0,
				monitorWithHwOtherDeviceUsesLimitMetric
					.getMetric(HW_OTHER_DEVICE_USES_LIMIT_LIMIT_TYPE_CRITICAL, NumberMetric.class)
					.getValue()
			);
			assertEquals(
				2.0,
				monitorWithHwOtherDeviceUsesLimitMetric
					.getMetric(HW_OTHER_DEVICE_USES_LIMIT_LIMIT_TYPE_DEGRADED, NumberMetric.class)
					.getValue()
			);
		}

		{
			// Testing when only the critical metric is present
			final NumberMetric hwOtherDeviceUsesMetric = NumberMetric
				.builder()
				.value(1.0)
				.name("hw.other_device.uses")
				.build();
			hwOtherDeviceUsesMetric.setCollectTime(STRATEGY_TIME);
			hwOtherDeviceUsesMetric.setPreviousCollectTime(STRATEGY_TIME - 1000 * 60 * 2);
			final NumberMetric hwOtherDeviceUsesLimitCriticalMetric = NumberMetric
				.builder()
				.value(2.0)
				.name(HW_OTHER_DEVICE_USES_LIMIT_LIMIT_TYPE_CRITICAL)
				.attributes(Map.of("limit_type", "critical"))
				.build();
			hwOtherDeviceUsesLimitCriticalMetric.setCollectTime(STRATEGY_TIME);
			hwOtherDeviceUsesLimitCriticalMetric.setPreviousCollectTime(STRATEGY_TIME - 1000 * 60 * 2);
			final Monitor monitorWithHwOtherDeviceUsesLimitMetric = Monitor
				.builder()
				.id("monitorOne")
				.type("other_device")
				.metrics(
					new HashMap<>(
						Map.of(
							"hw.other_device.uses",
							hwOtherDeviceUsesMetric,
							HW_OTHER_DEVICE_USES_LIMIT_LIMIT_TYPE_CRITICAL,
							hwOtherDeviceUsesLimitCriticalMetric
						)
					)
				)
				.build();

			new OtherDeviceMetricNormalizer(STRATEGY_TIME, HOSTNAME, new ConnectorStore())
				.normalize(monitorWithHwOtherDeviceUsesLimitMetric);
			assertEquals(
				1.8,
				monitorWithHwOtherDeviceUsesLimitMetric
					.getMetric(HW_OTHER_DEVICE_USES_LIMIT_LIMIT_TYPE_DEGRADED, NumberMetric.class)
					.getValue()
			);
		}

		{
			// Testing when only the degraded metric is present
			final NumberMetric hwOtherDeviceUsesMetric = NumberMetric
				.builder()
				.value(1.0)
				.name("hw.other_device.uses")
				.build();
			hwOtherDeviceUsesMetric.setCollectTime(STRATEGY_TIME);
			hwOtherDeviceUsesMetric.setPreviousCollectTime(STRATEGY_TIME - 1000 * 60 * 2);
			final NumberMetric hwOtherDeviceUsesLimitDegradedMetric = NumberMetric
				.builder()
				.value(2.0)
				.name(HW_OTHER_DEVICE_USES_LIMIT_LIMIT_TYPE_DEGRADED)
				.attributes(Map.of("limit_type", "degraded"))
				.build();
			hwOtherDeviceUsesLimitDegradedMetric.setCollectTime(STRATEGY_TIME);
			hwOtherDeviceUsesLimitDegradedMetric.setPreviousCollectTime(STRATEGY_TIME - 1000 * 60 * 2);
			final Monitor monitorWithHwOtherDeviceUsesLimitMetric = Monitor
				.builder()
				.id("monitorOne")
				.type("other_device")
				.metrics(
					new HashMap<>(
						Map.of(
							"hw.other_device.uses",
							hwOtherDeviceUsesMetric,
							HW_OTHER_DEVICE_USES_LIMIT_LIMIT_TYPE_DEGRADED,
							hwOtherDeviceUsesLimitDegradedMetric
						)
					)
				)
				.build();
			new OtherDeviceMetricNormalizer(STRATEGY_TIME, HOSTNAME, new ConnectorStore())
				.normalize(monitorWithHwOtherDeviceUsesLimitMetric);
			assertEquals(
				2.2,
				monitorWithHwOtherDeviceUsesLimitMetric
					.getMetric(HW_OTHER_DEVICE_USES_LIMIT_LIMIT_TYPE_CRITICAL, NumberMetric.class)
					.getValue()
			);
		}
	}
}
