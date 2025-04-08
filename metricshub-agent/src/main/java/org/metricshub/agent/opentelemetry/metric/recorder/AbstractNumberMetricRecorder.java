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
import org.metricshub.engine.telemetry.metric.NumberMetric;

/**
 * This class is used to record metrics that are numbers. It is an abstract
 * class that extends {@link AbstractMetricRecorder}. The concrete classes will
 * be responsible to implement the method that builds the metric.
 */
@Slf4j
public abstract class AbstractNumberMetricRecorder extends AbstractMetricRecorder {

	/**
	 * Constructor for the class.
	 * @param metric             the metric to record.
	 * @param unit               the unit of the metric.
	 * @param description        the description of the metric.
	 * @param resourceAttributes the resource attributes associated with the metric.
	 */
	protected AbstractNumberMetricRecorder(
		final NumberMetric metric,
		final String unit,
		final String description,
		final Map<String, String> resourceAttributes
	) {
		super(metric, unit, description, resourceAttributes);
	}

	/**
	 * Builds the metric based on the current value.
	 * @return The recorded OpenTelemetry metric as an {@link Optional} of {@link Metric}.
	 */
	@Override
	public Optional<Metric> doRecord() {
		try {
			return this.<Double>getMetricValue().map(this::buildMetric);
		} catch (Exception e) {
			log.error("Failed to record metric: {}. Message: {}", metric.getName(), e.getMessage());
			log.debug("Failed to record metric: {}", metric.getName(), e);
		}
		return Optional.empty();
	}
}
