package org.metricshub.agent.opentelemetry.metric.recorder;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * MetricsHub Agent
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

import io.opentelemetry.proto.metrics.v1.Metric;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.metricshub.engine.telemetry.metric.StateSetMetric;

/**
 * This class is used to record metrics that are based on a state. It is an
 * abstract class that extends {@link AbstractMetricRecorder}. The concrete
 * classes will be responsible to implement the method that builds the metric.
 */
@Slf4j
public abstract class AbstractStateMetricRecorder extends AbstractMetricRecorder {

	protected final String stateValue;

	/**
	 * Constructor for the class.
	 *
	 * @param metric             the metric to record.
	 * @param unit               the unit of the metric.
	 * @param description        the description of the metric.
	 * @param stateValue         the state value.
	 * @param resourceAttributes the resource attributes associated with the metric.
	 * @param metricsCache       the metric cache to group data points.
	 */
	protected AbstractStateMetricRecorder(
		final StateSetMetric metric,
		final String unit,
		final String description,
		final String stateValue,
		final Map<String, String> resourceAttributes,
		final Map<String, Metric> metricsCache
	) {
		super(metric, unit, description, resourceAttributes, metricsCache);
		this.stateValue = stateValue;
	}

	/**
	 * Builds the metric based on the current state value.
	 *
	 * @return The recorded OpenTelemetry metric as an {@link Optional} of {@link Metric}.
	 */
	@Override
	public Optional<Metric> doRecord() {
		try {
			return this.<String>getMetricValue().flatMap(this::buildMetric).flatMap(this::storeMetric);
		} catch (Exception e) {
			log.error("Failed to record state metric: {}. Message: {}", metric.getName(), e.getMessage());
			log.debug("Failed to record state metric: {}", metric.getName(), e);
		}

		return Optional.empty();
	}

	/**
	 * Builds the metric based on the current state value.
	 *
	 * @param currentStateValue The current state value (e.g. "ok" or "error").
	 * @return The recorded OpenTelemetry metric as an {@link Optional} of {@link Metric}.
	 */
	protected abstract Optional<Metric> buildMetric(String currentStateValue);
}
