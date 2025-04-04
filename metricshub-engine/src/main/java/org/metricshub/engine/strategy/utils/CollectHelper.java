package org.metricshub.engine.strategy.utils;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * MetricsHub Engine
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

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.metricshub.engine.telemetry.Monitor;
import org.metricshub.engine.telemetry.metric.NumberMetric;
import org.metricshub.engine.telemetry.metric.StateSetMetric;

/**
 * The {@code CollectHelper} class provides utility methods for extracting metric values and collect times from
 * {@link Monitor} instances. It includes methods for working with both {@link NumberMetric} and {@link StateSetMetric}.
 * The class is designed to have a private no-argument constructor to prevent instantiation.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class CollectHelper {

	/**
	 * Get the {@link NumberMetric} value
	 *
	 * @param monitor    The {@link Monitor} instance we wish to extract the {@link NumberMetric} value
	 * @param metricName The name of the {@link NumberMetric} instance
	 * @param previous   Indicate whether we should return the <code>value</code> or the <code>previousValue</code>.
	 * @return a {@link Double} value
	 */
	public static Double getNumberMetricValue(final Monitor monitor, final String metricName, final boolean previous) {
		final NumberMetric metric = monitor.getMetric(metricName, NumberMetric.class);

		if (metric == null) {
			return null;
		}

		return previous ? getDoubleValue(metric.getPreviousValue()) : getDoubleValue(metric.getValue());
	}

	/**
	 * Get the {@link StateSetMetric} value.
	 *
	 * @param monitor    The {@link Monitor} instance from which to extract the {@link StateSetMetric} value.
	 * @param metricName The name of the {@link StateSetMetric} instance.
	 * @param previous   Indicate whether to return the {@code value} or the {@code previousValue}.
	 * @return The {@link String} value.
	 */
	public static String getStateSetMetricValue(final Monitor monitor, final String metricName, final boolean previous) {
		final StateSetMetric stateSetMetric = monitor.getMetric(metricName, StateSetMetric.class);

		if (stateSetMetric == null) {
			return null;
		}

		return previous ? stateSetMetric.getPreviousValue() : stateSetMetric.getValue();
	}

	/**
	 * Get the {@link NumberMetric} collect time
	 *
	 * @param monitor       The {@link Monitor} instance we wish to extract the {@link NumberMetric} collect time
	 * @param metricRateName The name of the {@link NumberMetric} instance
	 * @param previous      Indicate whether we should return the <code>collectTime</code> or the <code>previousCollectTime</code>.
	 * @return a {@link Double} value
	 */
	public static Double getNumberMetricCollectTime(
		final Monitor monitor,
		final String metricRateName,
		final boolean previous
	) {
		final NumberMetric metric = monitor.getMetric(metricRateName, NumberMetric.class);

		if (metric == null) {
			return null;
		}

		return previous ? getDoubleValue(metric.getPreviousCollectTime()) : getDoubleValue(metric.getCollectTime());
	}

	/**
	 * Return the {@link Double} value of the given {@link Number} instance
	 *
	 * @param number	The {@link Number} whose {@link Double} value should be extracted from
	 * @return {@link Double} instance
	 */
	public static Double getDoubleValue(final Number number) {
		if (number == null) {
			return null;
		}

		return number.doubleValue();
	}

	/**
	 * Get the updated {@link NumberMetric} value
	 *
	 * @param monitor    The {@link Monitor} instance we wish to extract the {@link NumberMetric} value
	 * @param metricName The name of the {@link NumberMetric} instance
	 * @return a {@link Double} value
	 */
	public static Double getUpdatedNumberMetricValue(final Monitor monitor, final String metricName) {
		final NumberMetric metric = monitor.getMetric(metricName, NumberMetric.class);

		if (metric == null) {
			return null;
		}

		return metric.isUpdated() ? getDoubleValue(metric.getValue()) : null;
	}
}
