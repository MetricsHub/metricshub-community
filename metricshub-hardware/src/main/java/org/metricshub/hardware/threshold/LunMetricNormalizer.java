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
 * The LunMetricNormalizer class is responsible for normalizing LUN (Logical Unit Number)
 * metrics for a given monitor. It extends the AbstractMetricNormalizer class and implements
 * methods to normalize specific LUN path limits.
 */
public class LunMetricNormalizer extends AbstractMetricNormalizer {

	/**
	 * Constructs a new instance of LunMetricNormalizer with the specified strategy time and hostname.
	 *
	 * @param strategyTime The strategy time in milliseconds.
	 * @param hostname     The hostname of the monitor.
	 */
	public LunMetricNormalizer(long strategyTime, String hostname) {
		super(strategyTime, hostname);
	}

	/**
	 * Normalizes LUN path limit metrics for the given monitor.
	 * This method retrieves various LUN path metrics such as available, expected, low degraded,
	 * low critical, and maximum metrics. It performs normalization by adjusting the low degraded
	 * and expected metrics based on the available metric values.
	 *
	 * @param monitor The monitor instance containing the metrics to be normalized.
	 */
	private void normalizeLunPathsLimit(final Monitor monitor) {
		// Get the hw.lun.paths available metric
		final Optional<NumberMetric> maybeAvailableMetric = findMetricByNamePrefixAndAttributes(
			monitor,
			"hw.lun.paths",
			Map.of("type", "available")
		);

		// If the hw.lun.paths available metric is absent, there is no metrics normalization
		if (maybeAvailableMetric.isEmpty()) {
			return;
		}

		// Get the hw.lun.paths expected metric
		final Optional<NumberMetric> maybeExpectedMetric = findMetricByNamePrefixAndAttributes(
			monitor,
			"hw.lun.paths",
			Map.of("type", "expected")
		);

		// Get the low degraded metric
		final Optional<NumberMetric> maybeLowDegradedMetric = findMetricByNamePrefixAndAttributes(
			monitor,
			"hw.lun.paths.limit",
			Map.of("limit_type", "low.degraded")
		);

		// Get the maximum metric
		final Optional<NumberMetric> maybeMaximumMetric = findMetricByNamePrefixAndAttributes(
			monitor,
			"hw.lun.paths.limit",
			Map.of("limit_type", "maximum")
		);

		// Retrieve hw.lun.paths available metric value
		final Double available = maybeAvailableMetric.get().getValue();

		// Normalization logic for low degraded metric
		// @formatter:off
		// @CHECKSTYLE:OFF
		if (
				available > 1.0 &&
				(
					maybeLowDegradedMetric.isEmpty() ||
					(maybeMaximumMetric.isPresent() && available > maybeMaximumMetric.get().getValue())
				)
		) {
			collectLowDegradedMetric(monitor, available - 1);
		}

		// @CHECKSTYLE:ON
		// @formatter:on

		// Normalization logic for expected metric
		if (maybeExpectedMetric.isEmpty()) {
			collectMetric(monitor, "hw.lun.paths{type=\"expected\"}", available + 1);
		}
	}

	/**
	 * Collects the low degraded LUN paths limit metric for the given monitor.
	 *
	 * @param monitor  The monitor instance where the metric is collected.
	 * @param newValue The new value for the low degraded LUN paths limit metric.
	 */
	private void collectLowDegradedMetric(final Monitor monitor, final Double newValue) {
		collectMetric(monitor, "hw.lun.paths.limit{limit_type=\"low.degraded\"}", newValue);
	}

	/**
	 * Normalizes the metrics for the given monitor by calling the appropriate normalization methods.
	 *
	 * @param monitor The monitor instance containing the metrics to be normalized.
	 */
	@Override
	public void normalize(Monitor monitor) {
		normalizeLunPathsLimit(monitor);
	}
}
