package org.metricshub.hardware.threshold;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.metricshub.engine.connector.model.ConnectorStore;
import org.metricshub.engine.telemetry.Monitor;
import org.metricshub.engine.telemetry.metric.NumberMetric;

class MemoryMetricNormalizerTest {

	private static final long STRATEGY_TIME = System.currentTimeMillis();
	private static final String HOSTNAME = "hostname";
	private static final String HW_ERRORS_LIMIT = "hw.errors.limit";
	public static final String HW_ERRORS_LIMIT_LIMIT_TYPE_CRITICAL_HW_TYPE_MEMORY =
		HW_ERRORS_LIMIT + "{limit_type=\"critical\", hw.type=\"memory\"}";
	public static final String HW_ERRORS_LIMIT_LIMIT_TYPE_DEGRADED_HW_TYPE_MEMORY =
		HW_ERRORS_LIMIT + "{limit_type=\"degraded\", hw.type=\"memory\"}";

	@Test
	void testNormalize() {
		{
			final NumberMetric hwErrorsMetric = NumberMetric.builder().value(1.0).name("hw.errors").build();
			hwErrorsMetric.setCollectTime(STRATEGY_TIME);
			hwErrorsMetric.setPreviousCollectTime(STRATEGY_TIME - 1000 * 60 * 2);
			final NumberMetric hwErrorsLimitCriticalMetric = NumberMetric
				.builder()
				.value(1.0)
				.name(HW_ERRORS_LIMIT_LIMIT_TYPE_CRITICAL_HW_TYPE_MEMORY)
				.attributes(Map.of("limit_type", "critical", "hw.type", "memory"))
				.build();
			hwErrorsLimitCriticalMetric.setCollectTime(STRATEGY_TIME);
			hwErrorsLimitCriticalMetric.setPreviousCollectTime(STRATEGY_TIME - 1000 * 60 * 2);
			final NumberMetric hwErrorsLimitDegradedMetric = NumberMetric
				.builder()
				.value(2.0)
				.name(HW_ERRORS_LIMIT_LIMIT_TYPE_DEGRADED_HW_TYPE_MEMORY)
				.attributes(Map.of("limit_type", "degraded", "hw.type", "memory"))
				.build();
			hwErrorsLimitDegradedMetric.setCollectTime(STRATEGY_TIME);
			hwErrorsLimitDegradedMetric.setPreviousCollectTime(STRATEGY_TIME - 1000 * 60 * 2);
			final Monitor monitorWithHwErrorsLimitMetric = Monitor
				.builder()
				.id("monitorOne")
				.type("memory")
				.metrics(
					new HashMap<>(
						Map.of(
							"hw.errors",
							hwErrorsMetric,
							HW_ERRORS_LIMIT_LIMIT_TYPE_CRITICAL_HW_TYPE_MEMORY,
							hwErrorsLimitCriticalMetric,
							HW_ERRORS_LIMIT_LIMIT_TYPE_DEGRADED_HW_TYPE_MEMORY,
							hwErrorsLimitDegradedMetric
						)
					)
				)
				.build();

			new MemoryMetricNormalizer(STRATEGY_TIME, HOSTNAME, new ConnectorStore())
				.normalize(monitorWithHwErrorsLimitMetric);
			assertEquals(
				2.0,
				monitorWithHwErrorsLimitMetric
					.getMetric(HW_ERRORS_LIMIT_LIMIT_TYPE_CRITICAL_HW_TYPE_MEMORY, NumberMetric.class)
					.getValue()
			);
			assertEquals(
				1.0,
				monitorWithHwErrorsLimitMetric
					.getMetric(HW_ERRORS_LIMIT_LIMIT_TYPE_DEGRADED_HW_TYPE_MEMORY, NumberMetric.class)
					.getValue()
			);
		}

		{
			final NumberMetric hwErrorsMetric = NumberMetric.builder().value(1.0).name("hw.errors").build();
			hwErrorsMetric.setCollectTime(STRATEGY_TIME);
			hwErrorsMetric.setPreviousCollectTime(STRATEGY_TIME - 1000 * 60 * 2);
			final NumberMetric hwErrorsLimitCriticalMetric = NumberMetric
				.builder()
				.value(2.0)
				.name(HW_ERRORS_LIMIT_LIMIT_TYPE_CRITICAL_HW_TYPE_MEMORY)
				.attributes(Map.of("limit_type", "critical", "hw.type", "memory"))
				.build();
			hwErrorsLimitCriticalMetric.setCollectTime(STRATEGY_TIME);
			hwErrorsLimitCriticalMetric.setPreviousCollectTime(STRATEGY_TIME - 1000 * 60 * 2);
			final NumberMetric hwErrorsLimitDegradedMetric = NumberMetric
				.builder()
				.value(1.0)
				.name(HW_ERRORS_LIMIT_LIMIT_TYPE_DEGRADED_HW_TYPE_MEMORY)
				.attributes(Map.of("limit_type", "degraded", "hw.type", "memory"))
				.build();
			hwErrorsLimitDegradedMetric.setCollectTime(STRATEGY_TIME);
			hwErrorsLimitDegradedMetric.setPreviousCollectTime(STRATEGY_TIME - 1000 * 60 * 2);
			final Monitor monitorWithHwErrorsLimitMetric = Monitor
				.builder()
				.id("monitorOne")
				.type("memory")
				.metrics(
					new HashMap<>(
						Map.of(
							"hw.errors",
							hwErrorsMetric,
							HW_ERRORS_LIMIT_LIMIT_TYPE_CRITICAL_HW_TYPE_MEMORY,
							hwErrorsLimitCriticalMetric,
							HW_ERRORS_LIMIT_LIMIT_TYPE_DEGRADED_HW_TYPE_MEMORY,
							hwErrorsLimitDegradedMetric
						)
					)
				)
				.build();

			new MemoryMetricNormalizer(STRATEGY_TIME, HOSTNAME, new ConnectorStore())
				.normalize(monitorWithHwErrorsLimitMetric);
			assertEquals(
				2.0,
				monitorWithHwErrorsLimitMetric
					.getMetric(HW_ERRORS_LIMIT_LIMIT_TYPE_CRITICAL_HW_TYPE_MEMORY, NumberMetric.class)
					.getValue()
			);
			assertEquals(
				1.0,
				monitorWithHwErrorsLimitMetric
					.getMetric(HW_ERRORS_LIMIT_LIMIT_TYPE_DEGRADED_HW_TYPE_MEMORY, NumberMetric.class)
					.getValue()
			);
		}
	}
}
