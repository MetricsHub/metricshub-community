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
import java.util.Optional;
import org.sentrysoftware.metricshub.engine.telemetry.metric.StateSetMetric;

/**
 * This class is used to record metrics that are not compressed and are based on a state.
 * It is an abstract class that extends {@link AbstractStateMetricRecorder} The concrete classes
 * will be responsible to implement the method that builds the metric.
 */
public abstract class AbstractNotCompressedStateMetricRecorder extends AbstractStateMetricRecorder {

	/**
	 * Constructor for the class.
	 * @param metric      the metric to record.
	 * @param unit        the unit of the metric.
	 * @param description the description of the metric.
	 * @param stateValue  the state value.
	 */
	protected AbstractNotCompressedStateMetricRecorder(
		final StateSetMetric metric,
		final String unit,
		final String description,
		final String stateValue
	) {
		super(metric, unit, description, stateValue);
	}

	/**
	 * Builds the metric based on the current state value.
	 *
	 * @param currentStateValue the current state value.
	 * @return The recorded OpenTelemetry metric as an {@link Optional} of {@link Metric}.
	 */
	@Override
	protected Optional<Metric> buildMetric(final String currentStateValue) {
		return Optional.of(buildMetric(currentStateValue.equalsIgnoreCase(stateValue) ? 1.0 : 0.0));
	}
}
