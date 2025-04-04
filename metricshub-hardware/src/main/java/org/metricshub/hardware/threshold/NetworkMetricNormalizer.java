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
 * The NetworkMetricNormalizer class is responsible for normalizing network-related metrics.
 * This class extends AbstractMetricNormalizer and provides functionality specific to network error ratio metrics.
 */
public class NetworkMetricNormalizer extends AbstractMetricNormalizer {

	/**
	 * Constructs a new instance of NetworkMetricNormalizer with the specified strategy time and hostname.
	 *
	 * @param strategyTime The strategy time in milliseconds.
	 * @param hostname     The hostname of the monitor.
	 */
	public NetworkMetricNormalizer(long strategyTime, String hostname) {
		super(strategyTime, hostname);
	}

	/**
	 * Normalizes the network error ratio metric for the given monitor.
	 * If the metric is not collected, it returns immediately. It then retrieves
	 * the degraded and critical metrics and ensures they are correctly set.
	 * If both metrics are absent, it collects default values. If both are present,
	 * it ensures the critical metric is not less than the degraded metric.
	 *
	 * @param monitor The monitor object containing the metrics to be normalized.
	 */
	private void normalizeNetworkErrorRatioMetric(final Monitor monitor) {
		if (!isMetricCollected(monitor, "hw.network.error_ratio")) {
			return;
		}
		// Get the degraded metric
		final Optional<NumberMetric> maybeDegradedMetric = findMetricByNamePrefixAndAttributes(
			monitor,
			"hw.network.error_ratio.limit",
			Map.of("limit_type", "degraded", "hw.type", monitor.getType())
		);

		// Get the critical metric
		final Optional<NumberMetric> maybeCriticalMetric = findMetricByNamePrefixAndAttributes(
			monitor,
			"hw.network.error_ratio.limit",
			Map.of("limit_type", "critical", "hw.type", monitor.getType())
		);

		if (maybeCriticalMetric.isEmpty() && maybeDegradedMetric.isEmpty()) {
			collectMetric(monitor, "hw.network.error_ratio.limit{limit_type=\"degraded\", hw.type=\"network\"}", 0.2);
			collectMetric(monitor, "hw.network.error_ratio.limit{limit_type=\"critical\", hw.type=\"network\"}", 0.3);
		} else if (maybeCriticalMetric.isPresent() && maybeDegradedMetric.isPresent()) {
			swapIfFirstLessThanSecond(maybeCriticalMetric.get(), maybeDegradedMetric.get());
		}
	}

	/**
	 * Normalizes the metrics for the given monitor by invoking the method to normalize network error ratio metrics.
	 *
	 * @param monitor The monitor object containing the metrics to be normalized.
	 */
	@Override
	public void normalize(final Monitor monitor) {
		normalizeNetworkErrorRatioMetric(monitor);
	}
}
