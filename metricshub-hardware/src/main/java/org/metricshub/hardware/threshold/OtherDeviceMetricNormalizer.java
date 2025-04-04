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
import org.metricshub.engine.telemetry.Monitor;
import org.metricshub.engine.telemetry.metric.NumberMetric;

/**
 * The OtherDeviceMetricNormalize class is responsible for normalizing other device metrics.
 * It extends the AbstractMetricNormalizer class to provide specific
 * normalization logic for other device monitor hardware metrics.
 */
public class OtherDeviceMetricNormalizer extends AbstractMetricNormalizer {

	/**
	 * Constructs a new instance of OtherDeviceMetricNormalizer with the specified strategy time.
	 * @param strategyTime The strategy time in milliseconds
	 * @param hostname     The hostname of the monitor
	 */
	public OtherDeviceMetricNormalizer(long strategyTime, String hostname) {
		super(strategyTime, hostname);
	}

	/**
	 * Normalizes the metrics of the given monitor.
	 * @param monitor The monitor containing the metrics to be normalized
	 */
	@Override
	public void normalize(Monitor monitor) {
		normalizeOtherDeviceLimitMetric(monitor, "hw.other_device.uses");
		normalizeOtherDeviceLimitMetric(monitor, "hw.other_device.value");
	}

	/**
	 * Normalizes the speed limit metrics.
	 * @param monitor The monitor to normalize
	 * @param metricNamePrefix The prefix of the metric name.
	 */
	private void normalizeOtherDeviceLimitMetric(final Monitor monitor, final String metricNamePrefix) {
		if (!isMetricCollected(monitor, metricNamePrefix)) {
			return;
		}

		// Get the degraded metric
		final Optional<NumberMetric> maybeDegradedMetric = findMetricByNamePrefixAndAttributes(
			monitor,
			String.format("%s.limit", metricNamePrefix),
			Map.of("limit_type", "degraded")
		);

		// Get the critical metric
		final Optional<NumberMetric> maybeCriticalMetric = findMetricByNamePrefixAndAttributes(
			monitor,
			String.format("%s.limit", metricNamePrefix),
			Map.of("limit_type", "critical")
		);

		if (maybeDegradedMetric.isPresent() && maybeCriticalMetric.isPresent()) {
			// Adjust values if both metrics are present
			swapIfFirstLessThanSecond(maybeDegradedMetric.get(), maybeCriticalMetric.get());
		} else if (maybeCriticalMetric.isPresent()) {
			// Create degraded metric if only critical is present
			final NumberMetric criticalMetric = maybeCriticalMetric.get();
			final String degradedMetricName = replaceLimitType(criticalMetric.getName(), "limit_type=\"degraded\"");
			collectMetric(monitor, degradedMetricName, criticalMetric.getValue() * 0.9);
		} else if (maybeDegradedMetric.isPresent()) {
			// Create critical metric if only degraded is present
			final NumberMetric degradedMetric = maybeDegradedMetric.get();
			final String criticalMetricName = replaceLimitType(degradedMetric.getName(), "limit_type=\"critical\"");
			collectMetric(monitor, criticalMetricName, degradedMetric.getValue() * 1.1);
		}
	}
}
