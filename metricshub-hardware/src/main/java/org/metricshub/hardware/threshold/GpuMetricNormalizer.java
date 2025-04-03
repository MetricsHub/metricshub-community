package org.metricshub.hardware.threshold;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * MetricsHub Hardware Energy and Sustainability Module
 * ჻჻჻჻჻჻
 * Copyright 2023 - 2024 Sentry Software
 * ჻჻჻჻჻჻
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 * ╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱
 */

import java.util.Map;
import java.util.Optional;
import org.sentrysoftware.metricshub.engine.telemetry.Monitor;
import org.sentrysoftware.metricshub.engine.telemetry.metric.NumberMetric;

/**
 * The GpuMetricNormalizer class is responsible for normalizing GPU metrics.
 * It extends the AbstractMetricNormalizer class to provide specific
 * normalization logic for GPU monitor hardware metrics.
 */
public class GpuMetricNormalizer extends AbstractMetricNormalizer {

	/**
	 * Constructs a new instance with the specified strategy time.
	 *
	 * @param strategyTime The strategy time in milliseconds
	 * @param hostname     The hostname of the monitor
	 */
	public GpuMetricNormalizer(long strategyTime, String hostname) {
		super(strategyTime, hostname);
	}

	/**
	 * Normalizes GPU utilization limit metrics for a given monitor and metric name prefix.
	 * If both degraded and critical metrics are absent, it collects default values for them.
	 * If both metrics are present, it ensures the degraded value is not greater than the critical value.
	 * If only one of the metrics is absent, it calculates and collects a value for the missing metric.
	 *
	 * @param monitor              The monitor object where the metrics are collected.
	 * @param metricNamePrefix     The prefix of the metric names to be normalized.
	 * @param defaultCriticalValue The default value to use for the critical metric.
	 * @param defaultDegradedValue The default value to use for the degraded metric.
	 */
	protected void normalizeGpuLimitMetric(
		final Monitor monitor,
		final String metricNamePrefix,
		final double defaultCriticalValue,
		final double defaultDegradedValue
	) {
		final String metricNamePrefixWithLimit = metricNamePrefix + ".limit";
		// Check if the metric is collected
		if (!isMetricCollected(monitor, metricNamePrefix)) {
			return;
		}

		// Get the degraded metric
		final Optional<NumberMetric> maybeDegradedMetric = findMetricByNamePrefixAndAttributes(
			monitor,
			metricNamePrefixWithLimit,
			Map.of("limit_type", "degraded")
		);

		// Get the critical metric
		final Optional<NumberMetric> maybeCriticalMetric = findMetricByNamePrefixAndAttributes(
			monitor,
			metricNamePrefixWithLimit,
			Map.of("limit_type", "critical")
		);

		if (maybeDegradedMetric.isEmpty() && maybeCriticalMetric.isEmpty()) {
			// Collect default metrics if both are absent
			collectMetric(monitor, metricNamePrefixWithLimit + "{limit_type=\"critical\"}", defaultCriticalValue);
			collectMetric(monitor, metricNamePrefixWithLimit + "{limit_type=\"degraded\"}", defaultDegradedValue);
		} else if (maybeDegradedMetric.isPresent() && maybeCriticalMetric.isPresent()) {
			// If both the degraded and critical metrics are available, adjust the values if necessary
			swapIfFirstLessThanSecond(maybeCriticalMetric.get(), maybeDegradedMetric.get());
		} else if (maybeDegradedMetric.isEmpty()) {
			// If the degraded metric is absent, create and collect a new degraded metric
			final NumberMetric criticalMetric = maybeCriticalMetric.get();
			final String degradedMetricName = replaceLimitType(criticalMetric.getName(), "limit_type=\"degraded\"");
			collectMetric(monitor, degradedMetricName, maybeCriticalMetric.get().getValue() * 0.9);
		} else {
			// If the critical metric is absent, create and collect a new critical metric
			final NumberMetric degradedMetric = maybeDegradedMetric.get();
			final String criticalMetricName = replaceLimitType(degradedMetric.getName(), "limit_type=\"critical\"");
			collectMetric(monitor, criticalMetricName, 100 - ((100 - maybeDegradedMetric.get().getValue()) * 0.5));
		}
	}

	/**
	 * Normalizes various metrics for the given monitor.
	 * This method includes normalization for error limits and GPU utilization metrics.
	 *
	 * @param monitor The monitor object where the metrics are collected.
	 */
	@Override
	public void normalize(Monitor monitor) {
		// Normalize the errors limit metric
		normalizeErrorsLimitMetric(monitor);

		// Normalize the GPU memory utilization limit metric with default critical and degraded values
		normalizeGpuLimitMetric(monitor, "hw.gpu.memory.utilization", 0.95, 0.9);

		// Normalize the GPU utilization limit metric with default critical and degraded values
		normalizeGpuLimitMetric(monitor, "hw.gpu.utilization", 0.9, 0.8);
	}
}
