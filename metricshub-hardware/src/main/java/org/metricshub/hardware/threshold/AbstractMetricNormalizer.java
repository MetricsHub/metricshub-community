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

import static org.metricshub.hardware.util.HwCollectHelper.findMetricByNamePrefixAndAttributes;
import static org.metricshub.hardware.util.HwCollectHelper.isMetricCollected;

import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.metricshub.engine.connector.model.ConnectorStore;
import org.metricshub.engine.telemetry.MetricFactory;
import org.metricshub.engine.telemetry.Monitor;
import org.metricshub.engine.telemetry.metric.NumberMetric;

/**
 * An abstract class that provides methods for normalizing metrics in a monitoring system.
 * This class contains utility methods to check the availability of the monitor metrics and to adjust their values
 * based on specified conditions.
 */
@AllArgsConstructor
@Slf4j
public abstract class AbstractMetricNormalizer {

	protected long strategyTime;
	protected String hostname;
	protected ConnectorStore connectorStore;
	private static final Pattern LIMIT_TYPE_PATTERN = Pattern.compile("limit_type\s*=\s*\"([^\"]+)\"");

	/**
	 * Adjusts the corresponding monitor's metric as follows:
	 * For example:
	 * If the hw.fan.speed.limit{limit_type="low.critical"} metric is not available while the hw.fan.speed.limit{limit_type="low.degraded"}
	 * metric is, set hw.fan.speed.limit{limit_type="low.critical"} to hw.fan.speed.limit{limit_type="low.degraded"} * 0.9.
	 * We need to manage the following use case as well:
	 * If the hw.fan.speed.limit{limit_type="low.critical"} metric is not available while the hw.fan.speed.limit{limit_type="low.degraded",
	 * unknown_attr="value"} metric is, set hw.fan.speed.limit{limit_type="low.critical", unknown_attr="value"} to
	 * hw.fan.speed.limit{limit_type="low.degraded", unknown_attr="value"} * 0.9.
	 *
	 * @param monitor A given {@link Monitor}
	 */
	public abstract void normalize(Monitor monitor);

	/**
	 * Normalizes the errors limit metric.
	 *
	 * @param monitor The monitor to normalize
	 */
	protected void normalizeErrorsLimitMetric(Monitor monitor) {
		if (!isMetricCollected(monitor, "hw.errors")) {
			return;
		}

		// Get the degraded metric
		final Optional<NumberMetric> maybeDegradedMetric = findMetricByNamePrefixAndAttributes(
			hostname,
			monitor,
			"hw.errors.limit",
			Map.of("limit_type", "degraded", "hw.type", monitor.getType())
		);

		// Get the critical metric
		final Optional<NumberMetric> maybeCriticalMetric = findMetricByNamePrefixAndAttributes(
			hostname,
			monitor,
			"hw.errors.limit",
			Map.of("limit_type", "critical", "hw.type", monitor.getType())
		);

		// If both the degraded and critical metrics are not available, create a critical metric with the value 1
		if (maybeDegradedMetric.isEmpty() && maybeCriticalMetric.isEmpty()) {
			final MetricFactory metricFactory = new MetricFactory(hostname, connectorStore);
			metricFactory.collectNumberMetric(
				monitor,
				String.format("hw.errors.limit{limit_type=\"critical\", hw.type=\"%s\"}", monitor.getType()),
				1D,
				strategyTime
			);
		} else if (maybeDegradedMetric.isPresent() && maybeCriticalMetric.isPresent()) {
			// If both the degraded and critical metrics are available, adjust the values
			final NumberMetric degradedMetric = maybeDegradedMetric.get();
			final NumberMetric criticalMetric = maybeCriticalMetric.get();
			swapIfFirstLessThanSecond(criticalMetric, degradedMetric);
		}
	}

	/**
	 * Swaps the values of two metrics if the first metric's value is less than the second's.
	 *
	 * @param firstMetric  The first metric
	 * @param secondMetric The second metric
	 */
	protected void swapIfFirstLessThanSecond(final NumberMetric firstMetric, final NumberMetric secondMetric) {
		final Double firstMetricValue = firstMetric.getValue();
		final Double secondMetricValue = secondMetric.getValue();

		if (firstMetricValue < secondMetricValue) {
			firstMetric.setValue(secondMetricValue);
			secondMetric.setValue(firstMetricValue);
		}
	}

	/**
	 * Collect a metric using a given metric name and a given value.
	 *
	 * @param monitor The monitor to collect the metric
	 * @param metricName The metric name
	 * @param value The value of the metric
	 */
	protected void collectMetric(final Monitor monitor, final String metricName, final Double value) {
		final MetricFactory metricFactory = new MetricFactory(hostname, connectorStore);
		metricFactory.collectNumberMetric(monitor, metricName, value, strategyTime);
	}

	/**
	 * Replaces the limit type in the metric name using a regular expression pattern.
	 * This method searches for the pattern defined by {@code LIMIT_TYPE_PATTERN} in the
	 * given {@code metricName} and replaces the old limit type with the new limit type.
	 *
	 * @param metricName    the original metric name which contains the limit type to be replaced
	 * @param newLimitType  the new limit type that will replace the old limit type in the metric name
	 * @return              the modified metric name with the new limit type
	 */
	protected String replaceLimitType(final String metricName, final String newLimitType) {
		final Matcher matcher = LIMIT_TYPE_PATTERN.matcher(metricName);
		return matcher.replaceAll(newLimitType);
	}
}
