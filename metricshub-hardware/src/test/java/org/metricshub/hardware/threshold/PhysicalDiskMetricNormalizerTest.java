package org.metricshub.hardware.threshold;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.sentrysoftware.metricshub.engine.telemetry.Monitor;
import org.sentrysoftware.metricshub.engine.telemetry.metric.NumberMetric;

class PhysicalDiskMetricNormalizerTest {

	private static final long STRATEGY_TIME = System.currentTimeMillis();
	private static final String HOSTNAME = "hostname";
	private static final String HW_ERRORS_LIMIT = "hw.errors.limit";
	public static final String HW_ERRORS_LIMIT_LIMIT_TYPE_CRITICAL_HW_TYPE_PHYSICAL_DISK =
		HW_ERRORS_LIMIT + "{limit_type=\"critical\", hw.type=\"physical_disk\"}";
	public static final String HW_ERRORS_LIMIT_LIMIT_TYPE_DEGRADED_HW_TYPE_PHYSICAL_DISK =
		HW_ERRORS_LIMIT + "{limit_type=\"degraded\", hw.type=\"physical_disk\"}";

	@Test
	void testNormalize() {
		// Create the hw.errors metric
		final NumberMetric hwErrorsMetric = NumberMetric
			.builder()
			.value(1.0)
			.name("hw.errors")
			.attributes(Map.of("limit_type", "critical", "hw.type", "physical_disk"))
			.build();

		// Test case 1: Both degraded and critical are present and critical < degraded
		{
			final NumberMetric hwErrorsLimitCriticalMetric = NumberMetric
				.builder()
				.value(1.0)
				.name(HW_ERRORS_LIMIT_LIMIT_TYPE_CRITICAL_HW_TYPE_PHYSICAL_DISK)
				.attributes(Map.of("limit_type", "critical", "hw.type", "physical_disk"))
				.build();
			final NumberMetric hwErrorsLimitDegradedMetric = NumberMetric
				.builder()
				.value(2.0)
				.name(HW_ERRORS_LIMIT_LIMIT_TYPE_DEGRADED_HW_TYPE_PHYSICAL_DISK)
				.attributes(Map.of("limit_type", "degraded", "hw.type", "physical_disk"))
				.build();

			// Update the metrics
			setCollectTimes(hwErrorsMetric);
			setCollectTimes(hwErrorsLimitCriticalMetric);
			setCollectTimes(hwErrorsLimitDegradedMetric);

			// Create the monitor
			final Monitor monitorWithBothMetrics = Monitor
				.builder()
				.id("monitorOne")
				.type("physical_disk")
				.metrics(
					new HashMap<>(
						Map.of(
							"hw.errors",
							hwErrorsMetric,
							HW_ERRORS_LIMIT_LIMIT_TYPE_CRITICAL_HW_TYPE_PHYSICAL_DISK,
							hwErrorsLimitCriticalMetric,
							HW_ERRORS_LIMIT_LIMIT_TYPE_DEGRADED_HW_TYPE_PHYSICAL_DISK,
							hwErrorsLimitDegradedMetric
						)
					)
				)
				.build();

			new PhysicalDiskMetricNormalizer(STRATEGY_TIME, HOSTNAME).normalize(monitorWithBothMetrics);
			assertEquals(
				2.0,
				monitorWithBothMetrics
					.getMetric(HW_ERRORS_LIMIT_LIMIT_TYPE_CRITICAL_HW_TYPE_PHYSICAL_DISK, NumberMetric.class)
					.getValue()
			);
			assertEquals(
				1.0,
				monitorWithBothMetrics
					.getMetric(HW_ERRORS_LIMIT_LIMIT_TYPE_DEGRADED_HW_TYPE_PHYSICAL_DISK, NumberMetric.class)
					.getValue()
			);
		}
		// Test case 2: Both degraded and critical are present and critical >= degraded
		{
			final NumberMetric hwErrorsLimitCriticalMetric = NumberMetric
				.builder()
				.value(3.0)
				.name(HW_ERRORS_LIMIT_LIMIT_TYPE_CRITICAL_HW_TYPE_PHYSICAL_DISK)
				.attributes(Map.of("limit_type", "critical", "hw.type", "physical_disk"))
				.build();
			final NumberMetric hwErrorsLimitDegradedMetric = NumberMetric
				.builder()
				.value(2.0)
				.name(HW_ERRORS_LIMIT_LIMIT_TYPE_DEGRADED_HW_TYPE_PHYSICAL_DISK)
				.attributes(Map.of("limit_type", "degraded", "hw.type", "physical_disk"))
				.build();

			// Update the metrics
			setCollectTimes(hwErrorsMetric);
			setCollectTimes(hwErrorsLimitCriticalMetric);
			setCollectTimes(hwErrorsLimitDegradedMetric);

			// Create the monitor
			final Monitor monitorWithBothMetrics = Monitor
				.builder()
				.id("monitorOne")
				.type("physical_disk")
				.metrics(
					new HashMap<>(
						Map.of(
							"hw.errors",
							hwErrorsMetric,
							HW_ERRORS_LIMIT_LIMIT_TYPE_CRITICAL_HW_TYPE_PHYSICAL_DISK,
							hwErrorsLimitCriticalMetric,
							HW_ERRORS_LIMIT_LIMIT_TYPE_DEGRADED_HW_TYPE_PHYSICAL_DISK,
							hwErrorsLimitDegradedMetric
						)
					)
				)
				.build();

			new PhysicalDiskMetricNormalizer(STRATEGY_TIME, HOSTNAME).normalize(monitorWithBothMetrics);
			assertEquals(
				3.0,
				monitorWithBothMetrics
					.getMetric(HW_ERRORS_LIMIT_LIMIT_TYPE_CRITICAL_HW_TYPE_PHYSICAL_DISK, NumberMetric.class)
					.getValue()
			);
			assertEquals(
				2.0,
				monitorWithBothMetrics
					.getMetric(HW_ERRORS_LIMIT_LIMIT_TYPE_DEGRADED_HW_TYPE_PHYSICAL_DISK, NumberMetric.class)
					.getValue()
			);
		}
		// Test case 3: Both degraded and critical are absent
		{
			// Update the metrics
			setCollectTimes(hwErrorsMetric);

			// Create the monitor
			final Monitor monitorWithoutBothMetrics = Monitor
				.builder()
				.id("monitorOne")
				.type("physical_disk")
				.metrics(new HashMap<>(Map.of("hw.errors", hwErrorsMetric)))
				.build();

			new PhysicalDiskMetricNormalizer(STRATEGY_TIME, HOSTNAME).normalize(monitorWithoutBothMetrics);
			assertEquals(
				1.0,
				monitorWithoutBothMetrics
					.getMetric(HW_ERRORS_LIMIT_LIMIT_TYPE_CRITICAL_HW_TYPE_PHYSICAL_DISK, NumberMetric.class)
					.getValue()
			);
		}
		// Test case 4: Only degraded is present
		{
			final NumberMetric hwErrorsLimitDegradedMetric = NumberMetric
				.builder()
				.value(2.0)
				.name(HW_ERRORS_LIMIT_LIMIT_TYPE_DEGRADED_HW_TYPE_PHYSICAL_DISK)
				.attributes(Map.of("limit_type", "degraded", "hw.type", "physical_disk"))
				.build();

			// Update the metrics
			setCollectTimes(hwErrorsMetric);
			setCollectTimes(hwErrorsLimitDegradedMetric);

			// Create the monitor
			final Monitor monitorWithDegradedMetric = Monitor
				.builder()
				.id("monitorOne")
				.type("physical_disk")
				.metrics(
					new HashMap<>(
						Map.of(
							"hw.errors",
							hwErrorsMetric,
							HW_ERRORS_LIMIT_LIMIT_TYPE_DEGRADED_HW_TYPE_PHYSICAL_DISK,
							hwErrorsLimitDegradedMetric
						)
					)
				)
				.build();

			new PhysicalDiskMetricNormalizer(STRATEGY_TIME, HOSTNAME).normalize(monitorWithDegradedMetric);
			assertNull(
				monitorWithDegradedMetric.getMetric(
					HW_ERRORS_LIMIT_LIMIT_TYPE_CRITICAL_HW_TYPE_PHYSICAL_DISK,
					NumberMetric.class
				)
			);
			assertEquals(
				2.0,
				monitorWithDegradedMetric
					.getMetric(HW_ERRORS_LIMIT_LIMIT_TYPE_DEGRADED_HW_TYPE_PHYSICAL_DISK, NumberMetric.class)
					.getValue()
			);
		}
		// Test case 5: Only critical is present
		{
			final NumberMetric hwErrorsLimitCriticalMetric = NumberMetric
				.builder()
				.value(1.0)
				.name(HW_ERRORS_LIMIT_LIMIT_TYPE_CRITICAL_HW_TYPE_PHYSICAL_DISK)
				.attributes(Map.of("limit_type", "critical", "hw.type", "physical_disk"))
				.build();

			// Update the metrics
			setCollectTimes(hwErrorsMetric);
			setCollectTimes(hwErrorsLimitCriticalMetric);

			// Create the monitor
			final Monitor monitorWithCriticalMetric = Monitor
				.builder()
				.id("monitorOne")
				.type("physical_disk")
				.metrics(
					new HashMap<>(
						Map.of(
							"hw.errors",
							hwErrorsMetric,
							HW_ERRORS_LIMIT_LIMIT_TYPE_CRITICAL_HW_TYPE_PHYSICAL_DISK,
							hwErrorsLimitCriticalMetric
						)
					)
				)
				.build();

			new PhysicalDiskMetricNormalizer(STRATEGY_TIME, HOSTNAME).normalize(monitorWithCriticalMetric);
			assertEquals(
				1.0,
				monitorWithCriticalMetric
					.getMetric(HW_ERRORS_LIMIT_LIMIT_TYPE_CRITICAL_HW_TYPE_PHYSICAL_DISK, NumberMetric.class)
					.getValue()
			);
			assertNull(
				monitorWithCriticalMetric.getMetric(
					HW_ERRORS_LIMIT_LIMIT_TYPE_DEGRADED_HW_TYPE_PHYSICAL_DISK,
					NumberMetric.class
				)
			);
		}
		// Test case 6: hw.errors metric is not present
		{
			// Update the metrics
			setCollectTimes(hwErrorsMetric);

			// Create the monitor
			final Monitor monitorWithoutMetrics = Monitor
				.builder()
				.id("monitorOne")
				.type("physical_disk")
				.metrics(new HashMap<>())
				.build();

			new PhysicalDiskMetricNormalizer(STRATEGY_TIME, HOSTNAME).normalize(monitorWithoutMetrics);
			assertNull(
				monitorWithoutMetrics.getMetric(HW_ERRORS_LIMIT_LIMIT_TYPE_CRITICAL_HW_TYPE_PHYSICAL_DISK, NumberMetric.class)
			);
			assertNull(
				monitorWithoutMetrics.getMetric(HW_ERRORS_LIMIT_LIMIT_TYPE_DEGRADED_HW_TYPE_PHYSICAL_DISK, NumberMetric.class)
			);
		}
	}

	/**
	 * Sets the previous and the current collect times of a given metric
	 * @param metric A given metric
	 */
	private void setCollectTimes(final NumberMetric metric) {
		metric.setCollectTime(STRATEGY_TIME);
		metric.setPreviousCollectTime(STRATEGY_TIME - -1000 * 60 * 2);
	}
}
