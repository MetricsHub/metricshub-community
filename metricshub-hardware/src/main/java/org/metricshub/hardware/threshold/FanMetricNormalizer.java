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
 * The FanMetricNormalizer class is responsible for normalizing FAN metrics.
 * It extends the AbstractMetricNormalizer class to provide specific
 * normalization logic for FAN monitor hardware metrics.
 */
public class FanMetricNormalizer extends AbstractMetricNormalizer {

	private static final Double DEFAULT_LOW_DEGRADED_VALUE_SPEED_LIMIT_METRIC = 500.0;
	private static final Double DEFAULT_LOW_CRITICAL_VALUE_SPEED_LIMIT_METRIC = 0.0;
	private static final Double DEFAULT_LOW_DEGRADED_VALUE_SPEED_RATIO_LIMIT_METRIC = 0.05;
	private static final Double DEFAULT_LOW_CRITICAL_VALUE_SPEED_RATIO_LIMIT_METRIC = 0.0;

	/**
	 * Constructs a new instance with the specified strategy time.
	 * @param strategyTime The strategy time in milliseconds
	 * @param hostname The hostname of the monitor
	 */
	public FanMetricNormalizer(long strategyTime, String hostname) {
		super(strategyTime, hostname);
	}

	/**
	 * Normalizes fan speed and speed ratio limit metrics
	 * @param monitor A given {@link Monitor}
	 */
	@Override
	public void normalize(Monitor monitor) {
		normalizeSpeedLimitMetric(
			monitor,
			"hw.fan.speed",
			DEFAULT_LOW_DEGRADED_VALUE_SPEED_LIMIT_METRIC,
			DEFAULT_LOW_CRITICAL_VALUE_SPEED_LIMIT_METRIC
		);
		normalizeSpeedLimitMetric(
			monitor,
			"hw.fan.speed_ratio",
			DEFAULT_LOW_DEGRADED_VALUE_SPEED_RATIO_LIMIT_METRIC,
			DEFAULT_LOW_CRITICAL_VALUE_SPEED_RATIO_LIMIT_METRIC
		);
	}

	/**
	 * Normalizes the speed limit metrics.
	 * @param monitor The monitor to normalize
	 * @param metricNamePrefix The prefix of the metric name.
	 * @param defaultLowDegradedValueMetric The default value for the low degraded limit metric.
	 * @param defaultLowCriticalValueMetric The default value for the low critical limit metric.
	 */
	public void normalizeSpeedLimitMetric(
		final Monitor monitor,
		final String metricNamePrefix,
		final Double defaultLowDegradedValueMetric,
		final Double defaultLowCriticalValueMetric
	) {
		if (!isMetricCollected(monitor, metricNamePrefix)) {
			return;
		}

		// Get the low degraded metric
		final Optional<NumberMetric> maybeLowDegradedMetric = findMetricByNamePrefixAndAttributes(
			monitor,
			String.format("%s.limit", metricNamePrefix),
			Map.of("limit_type", "low.degraded")
		);

		// Get the low critical metric
		final Optional<NumberMetric> maybeLowCriticalMetric = findMetricByNamePrefixAndAttributes(
			monitor,
			String.format("%s.limit", metricNamePrefix),
			Map.of("limit_type", "low.critical")
		);

		if (maybeLowDegradedMetric.isEmpty() && maybeLowCriticalMetric.isEmpty()) {
			// Create both metrics if neither are present
			final String lowDegradedMetricName = String.format(
				"%s.limit{limit_type=\"%s\"}",
				metricNamePrefix,
				"low.degraded"
			);
			final String lowCriticaldMetricName = String.format(
				"%s.limit{limit_type=\"%s\"}",
				metricNamePrefix,
				"low.critical"
			);
			collectMetric(monitor, lowDegradedMetricName, defaultLowDegradedValueMetric);
			collectMetric(monitor, lowCriticaldMetricName, defaultLowCriticalValueMetric);
		} else if (maybeLowDegradedMetric.isPresent() && maybeLowCriticalMetric.isPresent()) {
			// Adjust values if both metrics are present
			swapIfFirstLessThanSecond(maybeLowDegradedMetric.get(), maybeLowCriticalMetric.get());
		} else if (maybeLowDegradedMetric.isEmpty()) {
			// Create low degraded metric if only low critical is present
			final NumberMetric lowCriticalMetric = maybeLowCriticalMetric.get();
			final String lowDegradedMetricName = replaceLimitType(lowCriticalMetric.getName(), "limit_type=\"low.degraded\"");
			collectMetric(monitor, lowDegradedMetricName, lowCriticalMetric.getValue() * 1.1);
		} else {
			// Create low critical metric if only low degraded is present
			final NumberMetric lowDegradedMetric = maybeLowDegradedMetric.get();
			final String lowCriticalMetricName = replaceLimitType(lowDegradedMetric.getName(), "limit_type=\"low.critical\"");
			collectMetric(monitor, lowCriticalMetricName, lowDegradedMetric.getValue() * 0.9);
		}
	}
}
