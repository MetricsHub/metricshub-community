package org.sentrysoftware.metricshub.agent.opentelemetry.metric.recorder;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * MetricsHub Agent
 * ჻჻჻჻჻჻
 * Copyright 2023 - 2025 Sentry Software
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

import io.opentelemetry.proto.metrics.v1.Metric;
import lombok.Builder;
import org.sentrysoftware.metricshub.engine.telemetry.metric.StateSetMetric;

/**
 * This class is used to record counter state metrics with suppress zeros compression. It extends {@link AbstractSuppressZerosStateMetricRecorder}.
 */
public class CounterSuppressZerosStateMetricRecorder extends AbstractSuppressZerosStateMetricRecorder {

	/**
	 * Constructor for the class.
	 *
	 * @param metric      the metric to record.
	 * @param unit        the unit of the metric.
	 * @param description the description of the metric.
	 * @param stateValue  the state value to check.
	 */
	@Builder(setterPrefix = "with")
	public CounterSuppressZerosStateMetricRecorder(
		final StateSetMetric metric,
		final String unit,
		final String description,
		final String stateValue
	) {
		super(metric, unit, description, stateValue);
	}

	/**
	 * Builds the counter state metric based on the current value.
	 *
	 * @param value the value to record.
	 * @return The recorded OpenTelemetry metric as a {@link Metric}.
	 */
	@Override
	protected Metric buildMetric(final Double value) {
		return buildCounterStateMetric(value, stateValue);
	}
}
