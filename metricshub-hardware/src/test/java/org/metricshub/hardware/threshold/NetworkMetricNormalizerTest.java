package org.metricshub.hardware.threshold;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.metricshub.engine.telemetry.Monitor;
import org.metricshub.engine.telemetry.metric.NumberMetric;

class NetworkMetricNormalizerTest {

	private static final long STRATEGY_TIME = System.currentTimeMillis();
	private static final String HOSTNAME = "hostname";
	private static final String HW_NETWORK_ERROR_RATIO_LIMIT = "hw.network.error_ratio.limit";
	public static final String HW_NETWORK_ERROR_RATIO_LIMIT_LIMIT_TYPE_CRITICAL_HW_TYPE_NETWORK =
		HW_NETWORK_ERROR_RATIO_LIMIT + "{limit_type=\"critical\", hw.type=\"network\"}";
	public static final String HW_NETWORK_ERROR_RATIO_LIMIT_LIMIT_TYPE_DEGRADED_HW_TYPE_NETWORK =
		HW_NETWORK_ERROR_RATIO_LIMIT + "{limit_type=\"degraded\", hw.type=\"network\"}";

	@Test
	void testNormalize() {
		// Critical metric value is less than degraded metric value
		{
			// Create a metric representing network error ratio
			final NumberMetric hwNetworkErrorRatioMetric = NumberMetric
				.builder()
				.value(1.0)
				.name("hw.network.error_ratio")
				.build();
			hwNetworkErrorRatioMetric.setCollectTime(STRATEGY_TIME);
			hwNetworkErrorRatioMetric.setPreviousCollectTime(STRATEGY_TIME - 1000 * 60 * 2);

			// Create a critical limit metric for network error ratio
			final NumberMetric hwNetworkErrorRatioLimitCriticalMetric = NumberMetric
				.builder()
				.value(1.0)
				.name(HW_NETWORK_ERROR_RATIO_LIMIT_LIMIT_TYPE_CRITICAL_HW_TYPE_NETWORK)
				.attributes(Map.of("limit_type", "critical", "hw.type", "network"))
				.build();
			hwNetworkErrorRatioLimitCriticalMetric.setCollectTime(STRATEGY_TIME);
			hwNetworkErrorRatioLimitCriticalMetric.setPreviousCollectTime(STRATEGY_TIME - 1000 * 60 * 2);

			// Create a degraded limit metric for network error ratio
			final NumberMetric hwNetworkErrorRatioLimitDegradedMetric = NumberMetric
				.builder()
				.value(2.0)
				.name(HW_NETWORK_ERROR_RATIO_LIMIT_LIMIT_TYPE_DEGRADED_HW_TYPE_NETWORK)
				.attributes(Map.of("limit_type", "degraded", "hw.type", "network"))
				.build();
			hwNetworkErrorRatioLimitDegradedMetric.setCollectTime(STRATEGY_TIME);
			hwNetworkErrorRatioLimitDegradedMetric.setPreviousCollectTime(STRATEGY_TIME - 1000 * 60 * 2);

			// Create a monitor with the above metrics
			final Monitor monitorWithHwNetworkErrorRatioMetric = Monitor
				.builder()
				.id("monitorOne")
				.type("network")
				.metrics(
					new HashMap<>(
						Map.of(
							"hw.network.error_ratio",
							hwNetworkErrorRatioMetric,
							HW_NETWORK_ERROR_RATIO_LIMIT_LIMIT_TYPE_CRITICAL_HW_TYPE_NETWORK,
							hwNetworkErrorRatioLimitCriticalMetric,
							HW_NETWORK_ERROR_RATIO_LIMIT_LIMIT_TYPE_DEGRADED_HW_TYPE_NETWORK,
							hwNetworkErrorRatioLimitDegradedMetric
						)
					)
				)
				.build();

			// Normalize the metrics using NetworkMetricNormalizer
			new NetworkMetricNormalizer(STRATEGY_TIME, HOSTNAME).normalize(monitorWithHwNetworkErrorRatioMetric);

			// Validate the normalized metrics
			assertEquals(
				2.0,
				monitorWithHwNetworkErrorRatioMetric
					.getMetric(HW_NETWORK_ERROR_RATIO_LIMIT_LIMIT_TYPE_CRITICAL_HW_TYPE_NETWORK, NumberMetric.class)
					.getValue()
			);
			assertEquals(
				1.0,
				monitorWithHwNetworkErrorRatioMetric
					.getMetric(HW_NETWORK_ERROR_RATIO_LIMIT_LIMIT_TYPE_DEGRADED_HW_TYPE_NETWORK, NumberMetric.class)
					.getValue()
			);
		}
		// Critical metric value is greater than degraded metric value
		{
			// Create a metric representing network error ratio
			final NumberMetric hwNetworkErrorRatioMetric = NumberMetric
				.builder()
				.value(1.0)
				.name("hw.network.error_ratio")
				.build();
			hwNetworkErrorRatioMetric.setCollectTime(STRATEGY_TIME);
			hwNetworkErrorRatioMetric.setPreviousCollectTime(STRATEGY_TIME - 1000 * 60 * 2);

			// Create a critical limit metric for network error ratio
			final NumberMetric hwNetworkErrorRatioLimitCriticalMetric = NumberMetric
				.builder()
				.value(3.0)
				.name(HW_NETWORK_ERROR_RATIO_LIMIT_LIMIT_TYPE_CRITICAL_HW_TYPE_NETWORK)
				.attributes(Map.of("limit_type", "critical", "hw.type", "network"))
				.build();
			hwNetworkErrorRatioLimitCriticalMetric.setCollectTime(STRATEGY_TIME);
			hwNetworkErrorRatioLimitCriticalMetric.setPreviousCollectTime(STRATEGY_TIME - 1000 * 60 * 2);

			// Create a degraded limit metric for network error ratio
			final NumberMetric hwNetworkErrorRatioLimitDegradedMetric = NumberMetric
				.builder()
				.value(2.0)
				.name(HW_NETWORK_ERROR_RATIO_LIMIT_LIMIT_TYPE_DEGRADED_HW_TYPE_NETWORK)
				.attributes(Map.of("limit_type", "degraded", "hw.type", "network"))
				.build();
			hwNetworkErrorRatioLimitDegradedMetric.setCollectTime(STRATEGY_TIME);
			hwNetworkErrorRatioLimitDegradedMetric.setPreviousCollectTime(STRATEGY_TIME - 1000 * 60 * 2);

			// Create a monitor with the above metrics
			final Monitor monitorWithHwNetworkErrorRatioMetric = Monitor
				.builder()
				.id("monitorOne")
				.type("network")
				.metrics(
					new HashMap<>(
						Map.of(
							"hw.network.error_ratio",
							hwNetworkErrorRatioMetric,
							HW_NETWORK_ERROR_RATIO_LIMIT_LIMIT_TYPE_CRITICAL_HW_TYPE_NETWORK,
							hwNetworkErrorRatioLimitCriticalMetric,
							HW_NETWORK_ERROR_RATIO_LIMIT_LIMIT_TYPE_DEGRADED_HW_TYPE_NETWORK,
							hwNetworkErrorRatioLimitDegradedMetric
						)
					)
				)
				.build();

			// Normalize the metrics using NetworkMetricNormalizer
			new NetworkMetricNormalizer(STRATEGY_TIME, HOSTNAME).normalize(monitorWithHwNetworkErrorRatioMetric);

			// Validate the normalized metrics
			assertEquals(
				3.0,
				monitorWithHwNetworkErrorRatioMetric
					.getMetric(HW_NETWORK_ERROR_RATIO_LIMIT_LIMIT_TYPE_CRITICAL_HW_TYPE_NETWORK, NumberMetric.class)
					.getValue()
			);
			assertEquals(
				2.0,
				monitorWithHwNetworkErrorRatioMetric
					.getMetric(HW_NETWORK_ERROR_RATIO_LIMIT_LIMIT_TYPE_DEGRADED_HW_TYPE_NETWORK, NumberMetric.class)
					.getValue()
			);
		}
		{
			// Critical metric is absent, degraded metric is present
			{
				// Create a metric representing network error ratio
				final NumberMetric hwNetworkErrorRatioMetric = NumberMetric
					.builder()
					.value(1.0)
					.name("hw.network.error_ratio")
					.build();
				hwNetworkErrorRatioMetric.setCollectTime(STRATEGY_TIME);
				hwNetworkErrorRatioMetric.setPreviousCollectTime(STRATEGY_TIME - 1000 * 60 * 2);

				// Create a degraded limit metric for network error ratio
				final NumberMetric hwNetworkErrorRatioLimitDegradedMetric = NumberMetric
					.builder()
					.value(2.0)
					.name(HW_NETWORK_ERROR_RATIO_LIMIT_LIMIT_TYPE_DEGRADED_HW_TYPE_NETWORK)
					.attributes(Map.of("limit_type", "degraded", "hw.type", "network"))
					.build();
				hwNetworkErrorRatioLimitDegradedMetric.setCollectTime(STRATEGY_TIME);
				hwNetworkErrorRatioLimitDegradedMetric.setPreviousCollectTime(STRATEGY_TIME - 1000 * 60 * 2);

				// Create a monitor with the above metrics
				final Monitor monitorWithHwNetworkErrorRatioMetric = Monitor
					.builder()
					.id("monitorOne")
					.type("network")
					.metrics(
						new HashMap<>(
							Map.of(
								"hw.network.error_ratio",
								hwNetworkErrorRatioMetric,
								HW_NETWORK_ERROR_RATIO_LIMIT_LIMIT_TYPE_DEGRADED_HW_TYPE_NETWORK,
								hwNetworkErrorRatioLimitDegradedMetric
							)
						)
					)
					.build();

				// Normalize the metrics using NetworkMetricNormalizer
				new NetworkMetricNormalizer(STRATEGY_TIME, HOSTNAME).normalize(monitorWithHwNetworkErrorRatioMetric);

				// Validate the normalized metrics

				assertEquals(
					2.0,
					monitorWithHwNetworkErrorRatioMetric
						.getMetric(HW_NETWORK_ERROR_RATIO_LIMIT_LIMIT_TYPE_DEGRADED_HW_TYPE_NETWORK, NumberMetric.class)
						.getValue()
				);
			}
		}
		// Degraded metric is absent, critical metric is present
		{
			// Create a metric representing network error ratio
			final NumberMetric hwNetworkErrorRatioMetric = NumberMetric
				.builder()
				.value(1.0)
				.name("hw.network.error_ratio")
				.build();
			hwNetworkErrorRatioMetric.setCollectTime(STRATEGY_TIME);
			hwNetworkErrorRatioMetric.setPreviousCollectTime(STRATEGY_TIME - 1000 * 60 * 2);

			// Create a critical limit metric for network error ratio
			final NumberMetric hwNetworkErrorRatioLimitCriticalMetric = NumberMetric
				.builder()
				.value(1.0)
				.name(HW_NETWORK_ERROR_RATIO_LIMIT_LIMIT_TYPE_CRITICAL_HW_TYPE_NETWORK)
				.attributes(Map.of("limit_type", "critical", "hw.type", "network"))
				.build();
			hwNetworkErrorRatioLimitCriticalMetric.setCollectTime(STRATEGY_TIME);
			hwNetworkErrorRatioLimitCriticalMetric.setPreviousCollectTime(STRATEGY_TIME - 1000 * 60 * 2);

			// Create a monitor with the above metrics
			final Monitor monitorWithHwNetworkErrorRatioMetric = Monitor
				.builder()
				.id("monitorOne")
				.type("network")
				.metrics(
					new HashMap<>(
						Map.of(
							"hw.network.error_ratio",
							hwNetworkErrorRatioMetric,
							HW_NETWORK_ERROR_RATIO_LIMIT_LIMIT_TYPE_CRITICAL_HW_TYPE_NETWORK,
							hwNetworkErrorRatioLimitCriticalMetric
						)
					)
				)
				.build();

			// Normalize the metrics using NetworkMetricNormalizer
			new NetworkMetricNormalizer(STRATEGY_TIME, HOSTNAME).normalize(monitorWithHwNetworkErrorRatioMetric);

			// Validate the normalized metrics
			assertEquals(
				1.0,
				monitorWithHwNetworkErrorRatioMetric
					.getMetric(HW_NETWORK_ERROR_RATIO_LIMIT_LIMIT_TYPE_CRITICAL_HW_TYPE_NETWORK, NumberMetric.class)
					.getValue()
			);
		}
		// Absent network limit metrics: both critical and degraded are absent
		{
			// Create a metric representing network error ratio
			final NumberMetric hwNetworkErrorRatioMetric = NumberMetric
				.builder()
				.value(1.0)
				.name("hw.network.error_ratio")
				.build();
			hwNetworkErrorRatioMetric.setCollectTime(STRATEGY_TIME);
			hwNetworkErrorRatioMetric.setPreviousCollectTime(STRATEGY_TIME - 1000 * 60 * 2);

			// Create a monitor without critical and degraded limit metrics
			final Monitor monitorWithoutHwNetworkErrorRatioMetrics = Monitor
				.builder()
				.id("monitorTwo")
				.type("network")
				.metrics(new HashMap<>(Map.of("hw.network.error_ratio", hwNetworkErrorRatioMetric)))
				.build();

			// Normalize the metrics using NetworkMetricNormalizer
			new NetworkMetricNormalizer(STRATEGY_TIME, HOSTNAME).normalize(monitorWithoutHwNetworkErrorRatioMetrics);

			// Validate the default normalized metrics
			assertEquals(
				0.3,
				monitorWithoutHwNetworkErrorRatioMetrics
					.getMetric(HW_NETWORK_ERROR_RATIO_LIMIT_LIMIT_TYPE_CRITICAL_HW_TYPE_NETWORK, NumberMetric.class)
					.getValue()
			);
			assertEquals(
				0.2,
				monitorWithoutHwNetworkErrorRatioMetrics
					.getMetric(HW_NETWORK_ERROR_RATIO_LIMIT_LIMIT_TYPE_DEGRADED_HW_TYPE_NETWORK, NumberMetric.class)
					.getValue()
			);
		}
		{
			// Absent hw.network.error_ratio metric
			// Create a monitor without critical and degraded limit metrics
			final Monitor monitorWithoutHwNetworkErrorRatioMetrics = Monitor
				.builder()
				.id("monitorTwo")
				.type("network")
				.metrics(new HashMap<>())
				.build();

			// Normalize the metrics using NetworkMetricNormalizer
			new NetworkMetricNormalizer(STRATEGY_TIME, HOSTNAME).normalize(monitorWithoutHwNetworkErrorRatioMetrics);

			// Validate the default normalized metrics
			assertNull(
				monitorWithoutHwNetworkErrorRatioMetrics.getMetric(
					HW_NETWORK_ERROR_RATIO_LIMIT_LIMIT_TYPE_CRITICAL_HW_TYPE_NETWORK,
					NumberMetric.class
				)
			);
			assertNull(
				monitorWithoutHwNetworkErrorRatioMetrics.getMetric(
					HW_NETWORK_ERROR_RATIO_LIMIT_LIMIT_TYPE_DEGRADED_HW_TYPE_NETWORK,
					NumberMetric.class
				)
			);
		}
	}
}
