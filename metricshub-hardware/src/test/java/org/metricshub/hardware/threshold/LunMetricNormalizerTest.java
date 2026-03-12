package org.metricshub.hardware.threshold;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.metricshub.engine.connector.model.ConnectorStore;
import org.metricshub.engine.telemetry.Monitor;
import org.metricshub.engine.telemetry.metric.NumberMetric;

class LunMetricNormalizerTest {

	private static final long STRATEGY_TIME = System.currentTimeMillis();
	private static final String HOSTNAME = "hostname";
	public static final String HW_LUN_PATHS_AVAILABLE = "hw.lun.paths{type=\"available\"}";
	public static final String HW_LUN_PATHS_EXPECTED = "hw.lun.paths{type=\"expected\"}";
	public static final String HW_LUN_PATHS_LIMIT_LOW_DEGRADED = "hw.lun.paths.limit{limit_type=\"low.degraded\"}";
	public static final String HW_LUN_PATHS_LIMIT_MAXIMUM = "hw.lun.paths.limit{limit_type=\"maximum\"}";

	@Test
	void testNormalizeNoMetrics() {
		// Create the monitor without any metrics
		final Monitor monitor = Monitor.builder().id("monitorOne").type("lun").metrics(new HashMap<>()).build();

		// Create and run the normalizer
		new LunMetricNormalizer(STRATEGY_TIME, HOSTNAME, new ConnectorStore()).normalize(monitor);

		// Validate the normalized metrics
		assertNull(monitor.getMetric(HW_LUN_PATHS_LIMIT_LOW_DEGRADED, NumberMetric.class));
		assertNull(monitor.getMetric(HW_LUN_PATHS_EXPECTED, NumberMetric.class));
	}

	@Test
	void testNormalizeOnlyAvailablePresent() {
		// Available metric value is less than 1
		{
			// Create the hw.lun.paths{type="available"} metric
			final NumberMetric availableMetric = NumberMetric
				.builder()
				.value(0.5) // Value less than 1.0
				.name(HW_LUN_PATHS_AVAILABLE)
				.attributes(Map.of("type", "available"))
				.build();
			availableMetric.setCollectTime(STRATEGY_TIME);
			availableMetric.setPreviousCollectTime(STRATEGY_TIME - 1000 * 60 * 2);

			// Create the monitor with the metrics
			final Monitor monitor = Monitor
				.builder()
				.id("monitorOne")
				.type("lun")
				.metrics(new HashMap<>(Map.of(HW_LUN_PATHS_AVAILABLE, availableMetric)))
				.build();

			// Create and run the normalizer
			new LunMetricNormalizer(STRATEGY_TIME, HOSTNAME, new ConnectorStore()).normalize(monitor);

			// Validate the normalized metrics
			assertNull(monitor.getMetric(HW_LUN_PATHS_LIMIT_LOW_DEGRADED, NumberMetric.class));
			assertNotNull(monitor.getMetric(HW_LUN_PATHS_EXPECTED, NumberMetric.class));
			assertEquals(1.5, monitor.getMetric(HW_LUN_PATHS_EXPECTED, NumberMetric.class).getValue());
		}
		// Available metric value is greater than 1
		{
			// Create the hw.lun.paths{type="available"} metric
			final NumberMetric availableMetric = NumberMetric
				.builder()
				.value(1.5) // Value greater than 1.0
				.name(HW_LUN_PATHS_AVAILABLE)
				.attributes(Map.of("type", "available"))
				.build();
			availableMetric.setCollectTime(STRATEGY_TIME);
			availableMetric.setPreviousCollectTime(STRATEGY_TIME - 1000 * 60 * 2);

			// Create the monitor with the metrics
			final Monitor monitor = Monitor
				.builder()
				.id("monitorOne")
				.type("lun")
				.metrics(new HashMap<>(Map.of(HW_LUN_PATHS_AVAILABLE, availableMetric)))
				.build();

			// Create and run the normalizer
			new LunMetricNormalizer(STRATEGY_TIME, HOSTNAME, new ConnectorStore()).normalize(monitor);

			// Validate the normalized metrics
			assertNotNull(monitor.getMetric(HW_LUN_PATHS_LIMIT_LOW_DEGRADED, NumberMetric.class));
			assertEquals(0.5, monitor.getMetric(HW_LUN_PATHS_LIMIT_LOW_DEGRADED, NumberMetric.class).getValue());
			assertNotNull(monitor.getMetric(HW_LUN_PATHS_EXPECTED, NumberMetric.class));
			assertEquals(2.5, monitor.getMetric(HW_LUN_PATHS_EXPECTED, NumberMetric.class).getValue());
		}
	}

	@Test
	void testNormalizeMaximumNotPresentDegradedPresentAvailableNotPresent() {
		// Create the hw.lun.paths.limit{limit_type="low.degraded"} metric
		final NumberMetric lowDegradedMetric = NumberMetric
			.builder()
			.value(1.0) // Existing low degraded metric
			.name(HW_LUN_PATHS_LIMIT_LOW_DEGRADED)
			.attributes(Map.of("limit_type", "low.degraded"))
			.build();
		lowDegradedMetric.setCollectTime(STRATEGY_TIME);
		lowDegradedMetric.setPreviousCollectTime(STRATEGY_TIME - 1000 * 60 * 2);

		// Create the monitor with only the degraded metric
		final Monitor monitor = Monitor
			.builder()
			.id("monitorOne")
			.type("lun")
			.metrics(new HashMap<>(Map.of(HW_LUN_PATHS_LIMIT_LOW_DEGRADED, lowDegradedMetric)))
			.build();

		// Create and run the normalizer
		new LunMetricNormalizer(STRATEGY_TIME, HOSTNAME, new ConnectorStore()).normalize(monitor);

		// Validate the normalized metrics
		assertNotNull(monitor.getMetric(HW_LUN_PATHS_LIMIT_LOW_DEGRADED, NumberMetric.class));
		assertEquals(1.0, monitor.getMetric(HW_LUN_PATHS_LIMIT_LOW_DEGRADED, NumberMetric.class).getValue());
		assertNull(monitor.getMetric(HW_LUN_PATHS_EXPECTED, NumberMetric.class));
	}

	@Test
	void testNormalizeMaximumPresentDegradedPresent() {
		// Available metric value is less than maximum metric value and less than 1
		{
			// Create the hw.lun.paths{type="available"} metric
			final NumberMetric availableMetric = NumberMetric
				.builder()
				.value(0.5) // Value less than or equal to 1.0
				.name(HW_LUN_PATHS_AVAILABLE)
				.attributes(Map.of("type", "available"))
				.build();
			availableMetric.setCollectTime(STRATEGY_TIME);
			availableMetric.setPreviousCollectTime(STRATEGY_TIME - 1000 * 60 * 2);

			// Create the hw.lun.paths.limit{limit_type="maximum"} metric
			final NumberMetric maximumMetric = NumberMetric
				.builder()
				.value(0.7) // Value greater than available
				.name(HW_LUN_PATHS_LIMIT_MAXIMUM)
				.attributes(Map.of("limit_type", "maximum"))
				.build();
			maximumMetric.setCollectTime(STRATEGY_TIME);
			maximumMetric.setPreviousCollectTime(STRATEGY_TIME - 1000 * 60 * 2);

			// Create the hw.lun.paths.limit{limit_type="low.degraded"} metric
			final NumberMetric lowDegradedMetric = NumberMetric
				.builder()
				.value(1.0) // Existing low degraded metric
				.name(HW_LUN_PATHS_LIMIT_LOW_DEGRADED)
				.attributes(Map.of("limit_type", "low.degraded"))
				.build();
			lowDegradedMetric.setCollectTime(STRATEGY_TIME);
			lowDegradedMetric.setPreviousCollectTime(STRATEGY_TIME - 1000 * 60 * 2);

			// Create the monitor with the metrics
			final Monitor monitor = Monitor
				.builder()
				.id("monitorOne")
				.type("lun")
				.metrics(
					new HashMap<>(
						Map.of(
							HW_LUN_PATHS_AVAILABLE,
							availableMetric,
							HW_LUN_PATHS_LIMIT_MAXIMUM,
							maximumMetric,
							HW_LUN_PATHS_LIMIT_LOW_DEGRADED,
							lowDegradedMetric
						)
					)
				)
				.build();

			// Create and run the normalizer
			new LunMetricNormalizer(STRATEGY_TIME, HOSTNAME, new ConnectorStore()).normalize(monitor);

			// Validate the normalized metrics
			assertNotNull(monitor.getMetric(HW_LUN_PATHS_LIMIT_LOW_DEGRADED, NumberMetric.class));
			assertEquals(1.0, monitor.getMetric(HW_LUN_PATHS_LIMIT_LOW_DEGRADED, NumberMetric.class).getValue());
			assertEquals(1.5, monitor.getMetric(HW_LUN_PATHS_EXPECTED, NumberMetric.class).getValue());
		}
		// Available metric value is greater than maximum metric value and less than 1
		{
			// Create the hw.lun.paths{type="available"} metric
			final NumberMetric availableMetric = NumberMetric
				.builder()
				.value(0.8) // Value less than or equal to 1.0
				.name(HW_LUN_PATHS_AVAILABLE)
				.attributes(Map.of("type", "available"))
				.build();
			availableMetric.setCollectTime(STRATEGY_TIME);
			availableMetric.setPreviousCollectTime(STRATEGY_TIME - 1000 * 60 * 2);

			// Create the hw.lun.paths.limit{limit_type="maximum"} metric
			final NumberMetric maximumMetric = NumberMetric
				.builder()
				.value(0.7) // Value greater than available
				.name(HW_LUN_PATHS_LIMIT_MAXIMUM)
				.attributes(Map.of("limit_type", "maximum"))
				.build();
			maximumMetric.setCollectTime(STRATEGY_TIME);
			maximumMetric.setPreviousCollectTime(STRATEGY_TIME - 1000 * 60 * 2);

			// Create the hw.lun.paths.limit{limit_type="low.degraded"} metric
			final NumberMetric lowDegradedMetric = NumberMetric
				.builder()
				.value(1.0) // Existing low degraded metric
				.name(HW_LUN_PATHS_LIMIT_LOW_DEGRADED)
				.attributes(Map.of("limit_type", "low.degraded"))
				.build();
			lowDegradedMetric.setCollectTime(STRATEGY_TIME);
			lowDegradedMetric.setPreviousCollectTime(STRATEGY_TIME - 1000 * 60 * 2);

			// Create the monitor with the metrics
			final Monitor monitor = Monitor
				.builder()
				.id("monitorOne")
				.type("lun")
				.metrics(
					new HashMap<>(
						Map.of(
							HW_LUN_PATHS_AVAILABLE,
							availableMetric,
							HW_LUN_PATHS_LIMIT_MAXIMUM,
							maximumMetric,
							HW_LUN_PATHS_LIMIT_LOW_DEGRADED,
							lowDegradedMetric
						)
					)
				)
				.build();

			// Create and run the normalizer
			new LunMetricNormalizer(STRATEGY_TIME, HOSTNAME, new ConnectorStore()).normalize(monitor);

			// Validate the normalized metrics
			assertNotNull(monitor.getMetric(HW_LUN_PATHS_LIMIT_LOW_DEGRADED, NumberMetric.class));
			assertEquals(1.0, monitor.getMetric(HW_LUN_PATHS_LIMIT_LOW_DEGRADED, NumberMetric.class).getValue());
			assertEquals(1.8, monitor.getMetric(HW_LUN_PATHS_EXPECTED, NumberMetric.class).getValue());
		}
		// Available metric value is greater than 1 and available metric is greater that the maximum metric value
		{
			// Create the hw.lun.paths{type="available"} metric
			final NumberMetric availableMetric = NumberMetric
				.builder()
				.value(3.0) // Value greater than maximum and greater than 1.0
				.name(HW_LUN_PATHS_AVAILABLE)
				.attributes(Map.of("type", "available"))
				.build();
			availableMetric.setCollectTime(STRATEGY_TIME);
			availableMetric.setPreviousCollectTime(STRATEGY_TIME - 1000 * 60 * 2);

			// Create the hw.lun.paths.limit{limit_type="maximum"} metric
			final NumberMetric maximumMetric = NumberMetric
				.builder()
				.value(2.0) // Value less than available
				.name(HW_LUN_PATHS_LIMIT_MAXIMUM)
				.attributes(Map.of("limit_type", "maximum"))
				.build();
			maximumMetric.setCollectTime(STRATEGY_TIME);
			maximumMetric.setPreviousCollectTime(STRATEGY_TIME - 1000 * 60 * 2);

			// Create the hw.lun.paths.limit{limit_type="low.degraded"} metric
			final NumberMetric lowDegradedMetric = NumberMetric
				.builder()
				.value(1.0) // Existing low degraded metric
				.name(HW_LUN_PATHS_LIMIT_LOW_DEGRADED)
				.attributes(Map.of("limit_type", "low.degraded"))
				.build();
			lowDegradedMetric.setCollectTime(STRATEGY_TIME);
			lowDegradedMetric.setPreviousCollectTime(STRATEGY_TIME - 1000 * 60 * 2);

			// Create the hw.lun.paths{type="expected"} metric
			final NumberMetric expectedMetric = NumberMetric
				.builder()
				.value(1.0) // Existing expected metric
				.name(HW_LUN_PATHS_EXPECTED)
				.attributes(Map.of("type", "expected"))
				.build();
			expectedMetric.setCollectTime(STRATEGY_TIME);
			expectedMetric.setPreviousCollectTime(STRATEGY_TIME - 1000 * 60 * 2);

			// Create the monitor with the metrics
			final Monitor monitor = Monitor
				.builder()
				.id("monitorOne")
				.type("lun")
				.metrics(
					new HashMap<>(
						Map.of(
							HW_LUN_PATHS_AVAILABLE,
							availableMetric,
							HW_LUN_PATHS_LIMIT_MAXIMUM,
							maximumMetric,
							HW_LUN_PATHS_LIMIT_LOW_DEGRADED,
							lowDegradedMetric,
							HW_LUN_PATHS_EXPECTED,
							expectedMetric
						)
					)
				)
				.build();

			// Create and run the normalizer
			new LunMetricNormalizer(STRATEGY_TIME, HOSTNAME, new ConnectorStore()).normalize(monitor);

			// Validate the normalized metrics
			assertEquals(
				2.0, // availableMetric value (3.0) - 1 = 2.0
				monitor.getMetric(HW_LUN_PATHS_LIMIT_LOW_DEGRADED, NumberMetric.class).getValue()
			);
		}
		// Available metric is not present
		{
			// Create the hw.lun.paths.limit{limit_type="maximum"} metric
			final NumberMetric maximumMetric = NumberMetric
				.builder()
				.value(2.0) // Value of maximum metric
				.name(HW_LUN_PATHS_LIMIT_MAXIMUM)
				.attributes(Map.of("limit_type", "maximum"))
				.build();
			maximumMetric.setCollectTime(STRATEGY_TIME);
			maximumMetric.setPreviousCollectTime(STRATEGY_TIME - 1000 * 60 * 2);

			// Create the hw.lun.paths.limit{limit_type="low.degraded"} metric
			final NumberMetric lowDegradedMetric = NumberMetric
				.builder()
				.value(1.0) // Existing low degraded metric
				.name(HW_LUN_PATHS_LIMIT_LOW_DEGRADED)
				.attributes(Map.of("limit_type", "low.degraded"))
				.build();
			lowDegradedMetric.setCollectTime(STRATEGY_TIME);
			lowDegradedMetric.setPreviousCollectTime(STRATEGY_TIME - 1000 * 60 * 2);

			// Create the monitor with the metrics
			final Monitor monitor = Monitor
				.builder()
				.id("monitorOne")
				.type("lun")
				.metrics(
					new HashMap<>(
						Map.of(HW_LUN_PATHS_LIMIT_MAXIMUM, maximumMetric, HW_LUN_PATHS_LIMIT_LOW_DEGRADED, lowDegradedMetric)
					)
				)
				.build();

			// Create and run the normalizer
			new LunMetricNormalizer(STRATEGY_TIME, HOSTNAME, new ConnectorStore()).normalize(monitor);

			// Validate the normalized metrics
			assertNotNull(monitor.getMetric(HW_LUN_PATHS_LIMIT_LOW_DEGRADED, NumberMetric.class));
			assertEquals(1.0, monitor.getMetric(HW_LUN_PATHS_LIMIT_LOW_DEGRADED, NumberMetric.class).getValue());
			assertNull(monitor.getMetric(HW_LUN_PATHS_EXPECTED, NumberMetric.class));
		}
	}
}
