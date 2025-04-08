package org.metricshub.hardware.threshold;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * MetricsHub Hardware Energy and Sustainability Module
 * ჻჻჻჻჻჻
 * Copyright 2023 - 2025 MetricsHub
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
 * The TemperatureMetricNormalizer class is responsible for normalizing temperature metrics.
 * It extends the AbstractMetricNormalizer class to provide specific
 * normalization logic for temperature monitor hardware metrics.
 */
public class TemperatureMetricNormalizer extends AbstractMetricNormalizer {

	/**
	 * Constructs new instance of TemperatureMetricNormalizer with the specified strategy time.
	 * @param strategyTime The strategy time in milliseconds
	 * @param hostname     The hostname of the monitor
	 */
	public TemperatureMetricNormalizer(long strategyTime, String hostname) {
		super(strategyTime, hostname);
	}

	/**
	 * Normalizes the metrics of the given monitor.
	 * @param monitor The monitor containing the metrics to be normalized
	 */
	@Override
	public void normalize(Monitor monitor) {
		normalizeTemperatureLimitMetric(monitor, "hw.temperature");
	}

	/**
	 * Normalizes the speed limit metrics.
	 * @param monitor The monitor to normalize
	 * @param metricNamePrefix The prefix of the metric name.
	 */
	private void normalizeTemperatureLimitMetric(final Monitor monitor, final String metricNamePrefix) {
		if (!isMetricCollected(monitor, metricNamePrefix)) {
			return;
		}

		// Get the high degraded metric
		final Optional<NumberMetric> maybeHighDegradedMetric = findMetricByNamePrefixAndAttributes(
			monitor,
			String.format("%s.limit", metricNamePrefix),
			Map.of("limit_type", "high.degraded")
		);

		// Get the high critical metric
		final Optional<NumberMetric> maybeHighCriticalMetric = findMetricByNamePrefixAndAttributes(
			monitor,
			String.format("%s.limit", metricNamePrefix),
			Map.of("limit_type", "high.critical")
		);

		if (maybeHighDegradedMetric.isPresent() && maybeHighCriticalMetric.isPresent()) {
			// Adjust values if both metrics are present
			swapIfFirstLessThanSecond(maybeHighCriticalMetric.get(), maybeHighDegradedMetric.get());
		} else if (maybeHighCriticalMetric.isPresent()) {
			// Create high degraded metric if only high critical is present
			final NumberMetric highCriticalMetric = maybeHighCriticalMetric.get();
			final String highDegradedMetricName = replaceLimitType(
				highCriticalMetric.getName(),
				"limit_type=\"high.degraded\""
			);
			collectMetric(monitor, highDegradedMetricName, highCriticalMetric.getValue() * 0.9);
		} else if (maybeHighDegradedMetric.isPresent()) {
			// Create high critical metric if only high degraded is present
			final NumberMetric highDegradedMetric = maybeHighDegradedMetric.get();
			final String highCriticaldMetricName = replaceLimitType(
				highDegradedMetric.getName(),
				"limit_type=\"high.critical\""
			);
			collectMetric(monitor, highCriticaldMetricName, highDegradedMetric.getValue() * 1.1);
		}
	}
}
