package org.sentrysoftware.metricshub.hardware.threshold;

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

import static org.sentrysoftware.metricshub.hardware.util.HwConstants.HW_ERRORS_LIMIT_LIMIT_TYPE_CRITICAL_HW_TYPE_CPU;
import static org.sentrysoftware.metricshub.hardware.util.HwConstants.HW_ERRORS_LIMIT_LIMIT_TYPE_DEGRADED_HW_TYPE_CPU;
import static org.sentrysoftware.metricshub.hardware.util.HwConstants.METRIC_CRITICAL_ATTRIBUTES;
import static org.sentrysoftware.metricshub.hardware.util.HwConstants.METRIC_DEGRADED_ATTRIBUTES;
import static org.sentrysoftware.metricshub.hardware.util.HwConstants.METRIC_PREFIX;

import org.sentrysoftware.metricshub.engine.telemetry.MetricFactory;
import org.sentrysoftware.metricshub.engine.telemetry.Monitor;
import org.sentrysoftware.metricshub.engine.telemetry.metric.NumberMetric;

/**
 * The CpuMetricNormalizer class is responsible for normalizing CPU metrics.
 * It extends the AbstractMetricNormalizer class to provide specific
 * normalization logic for CPU monitor hardware metrics.
 */
public class CpuMetricNormalizer extends AbstractMetricNormalizer {

	/**
	 * Normalizes the errors limit metric for CPU hardware types.
	 * This method checks the availability of critical and degraded error limit metrics,
	 * adjusts them if necessary, and collects a new metric if neither is available.
	 * @param monitor The monitor instance used to retrieve and update metrics.
	 */
	@Override
	public void normalizeErrorsLimitMetric(final Monitor monitor) {
		final boolean isCriticalMetricAvailable = isMetricAvailable(
			HW_ERRORS_LIMIT_LIMIT_TYPE_CRITICAL_HW_TYPE_CPU,
			METRIC_PREFIX,
			METRIC_CRITICAL_ATTRIBUTES
		);
		final boolean isDegradedMetricAvailable = isMetricAvailable(
			HW_ERRORS_LIMIT_LIMIT_TYPE_DEGRADED_HW_TYPE_CPU,
			METRIC_PREFIX,
			METRIC_DEGRADED_ATTRIBUTES
		);
		final MetricFactory metricFactory = MetricFactory.builder().build();
		if (!isCriticalMetricAvailable && !isDegradedMetricAvailable) {
			metricFactory.collectNumberMetric(
				monitor,
				HW_ERRORS_LIMIT_LIMIT_TYPE_CRITICAL_HW_TYPE_CPU,
				1.0,
				System.currentTimeMillis()
			);
		} else if (isCriticalMetricAvailable && isDegradedMetricAvailable) {
			final NumberMetric criticalMetric = monitor.getMetric(
				HW_ERRORS_LIMIT_LIMIT_TYPE_CRITICAL_HW_TYPE_CPU,
				NumberMetric.class
			);
			final NumberMetric degradedMetric = monitor.getMetric(
				HW_ERRORS_LIMIT_LIMIT_TYPE_DEGRADED_HW_TYPE_CPU,
				NumberMetric.class
			);
			if (criticalMetric.getValue() < degradedMetric.getValue()) {
				swapMetricsValues(monitor, criticalMetric, degradedMetric);
			}
		}
	}
}
