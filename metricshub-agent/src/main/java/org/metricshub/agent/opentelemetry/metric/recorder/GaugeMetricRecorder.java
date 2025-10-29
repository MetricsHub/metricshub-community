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
import lombok.Builder;
import org.metricshub.engine.telemetry.metric.NumberMetric;

/**
 * This class is used to record gauge metrics. It extends {@link AbstractNumberMetricRecorder}.
 */
public class GaugeMetricRecorder extends AbstractNumberMetricRecorder {

	/**
	 * Constructor for the class.
	 *
	 * @param metric             The metric to record.
	 * @param unit               The unit of the metric.
	 * @param description        The description of the metric.
	 * @param resourceAttributes The resource attributes associated with the metric.
	 */
	@Builder(setterPrefix = "with")
	public GaugeMetricRecorder(
		final NumberMetric metric,
		final String unit,
		final String description,
		final Map<String, String> resourceAttributes
	) {
		super(metric, unit, description, resourceAttributes);
	}

	/**
	 * Builds the gauge metric based on the current value.
	 *
	 * @param value The value to record.
	 * @return The recorded OpenTelemetry metric as a {@link Metric}.
	 */
	@Override
	protected Metric buildMetric(final Double value) {
		return buildGaugeMetric(value);
	}
}
