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

import io.opentelemetry.proto.common.v1.AnyValue;
import io.opentelemetry.proto.common.v1.KeyValue;
import io.opentelemetry.proto.metrics.v1.AggregationTemporality;
import io.opentelemetry.proto.metrics.v1.Gauge;
import io.opentelemetry.proto.metrics.v1.Metric;
import io.opentelemetry.proto.metrics.v1.Metric.Builder;
import io.opentelemetry.proto.metrics.v1.NumberDataPoint;
import io.opentelemetry.proto.metrics.v1.Sum;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.sentrysoftware.metricshub.agent.helper.OtelHelper;
import org.sentrysoftware.metricshub.engine.telemetry.MetricFactory;
import org.sentrysoftware.metricshub.engine.telemetry.metric.AbstractMetric;

/**
 * AbstractMetricRecorder class used to record metrics. It defines the entry point to record a metric through the <code>doRecord</code> method.
 * It also provides the abstract methods to build the OpenTelemetry metric.
 */
@AllArgsConstructor
@Data
public abstract class AbstractMetricRecorder {

	private static final String STATE_ATTRIBUTE_KEY = "state";

	protected final AbstractMetric metric;
	protected final String unit;
	protected final String description;

	/**
	 * Records the metric and return the OpenTelemetry metric.
	 *
	 * @return The recorded OpenTelemetry metric as an {@link Optional} of {@link Metric}.
	 */
	public abstract Optional<Metric> doRecord();

	/**
	 * Builds the OpenTelemetry metric. The implementation should build the metric according to the metric value.
	 *
	 * @param value The value of the metric.
	 * @return The OpenTelemetry metric.
	 */
	protected abstract Metric buildMetric(Double value);

	/**
	 * Get the metric value. If the metric is not updated, it returns an empty Optional.
	 *
	 * @param <T> The type of the value.
	 *
	 * @return Optional of a {@link Double} value.
	 */
	protected <T> Optional<T> getMetricValue() {
		if (metric != null && metric.isUpdated()) {
			return Optional.ofNullable(metric.getValue());
		}
		return Optional.empty();
	}

	/**
	 * Creates a new metric builder with the metric name, description and unit.
	 *
	 * @return The metric builder.
	 */
	private Builder newMetricBuilder() {
		return Metric
			.newBuilder()
			.setName(MetricFactory.extractName(metric.getName()))
			.setDescription(description)
			.setUnit(unit != null ? unit : "");
	}

	/**
	 * Builds a counter metric.
	 *
	 * @param value The value of the metric.
	 * @return The OpenTelemetry metric.
	 */
	protected Metric buildCounterMetric(final Double value) {
		return buildCounterMetric(value, true, null);
	}

	/**
	 * Builds a counter metric with a state value.
	 *
	 * @param value      The value of the metric.
	 * @param stateValue The state value of the metric (e.g. "ok", "failed", etc.)
	 * @return The OpenTelemetry metric.
	 */
	protected Metric buildCounterStateMetric(final Double value, final String stateValue) {
		return buildCounterMetric(value, true, stateValue);
	}

	/**
	 * Builds an up-down counter metric.
	 *
	 * @param value The value of the metric.
	 * @return The OpenTelemetry metric.
	 */
	protected Metric buildUpDownCounterMetric(final Double value) {
		return buildCounterMetric(value, false, null);
	}

	/**
	 * Builds an up-down counter metric with a state value.
	 *
	 * @param value      The value of the metric.
	 * @param stateValue The state value of the metric (e.g. "ok", "failed", etc.)
	 * @return The OpenTelemetry metric.
	 */
	protected Metric buildUpDownCounterStateMetric(final Double value, final String stateValue) {
		return buildCounterMetric(value, false, stateValue);
	}

	/**
	 * Builds a gauge metric.
	 *
	 * @param value The value of the metric.
	 * @return The OpenTelemetry metric.
	 */
	protected Metric buildGaugeMetric(final Double value) {
		return buildGaugeMetric(value, null);
	}

	/**
	 * Builds a gauge metric with a state value.
	 *
	 * @param value      The value of the metric.
	 * @param stateValue The state value of the metric (e.g. "ok", "failed", etc.)
	 * @return The OpenTelemetry metric.
	 */
	protected Metric buildGaugeStateMetric(final Double value, final String stateValue) {
		return buildGaugeMetric(value, stateValue);
	}

	/**
	 * Builds a gauge metric with a state value.
	 *
	 * @param value The value of the metric.
	 * @param stateValue The state value of the metric (e.g. "ok", "failed", etc.)
	 * @return The OpenTelemetry metric.
	 */
	private Metric buildGaugeMetric(final Double value, final String stateValue) {
		return newMetricBuilder()
			.setGauge(Gauge.newBuilder().addDataPoints(buildDataPoint(value, stateValue)).build())
			.build();
	}

	/**
	 * Builds a counter metric.
	 *
	 * @param value       The value of the metric.
	 * @param isMonotonic If the metric is monotonic. If true it is a counter, otherwise it is an up-down counter.
	 * @param stateValue  The state value of the metric (e.g. "ok", "failed", etc.)
	 * @return The OpenTelemetry metric.
	 */
	private Metric buildCounterMetric(final Double value, final boolean isMonotonic, final String stateValue) {
		return newMetricBuilder()
			.setSum(
				Sum
					.newBuilder()
					.setIsMonotonic(isMonotonic)
					.setAggregationTemporality(AggregationTemporality.AGGREGATION_TEMPORALITY_CUMULATIVE)
					.addDataPoints(buildDataPoint(value, stateValue))
					.build()
			)
			.build();
	}

	/**
	 * Builds a data point with the metric value and the state value.
	 *
	 * @param value       The value of the metric.
	 * @param stateValue  The state value of the metric (e.g. "ok", "failed", etc.)
	 * @return The OpenTelemetry data point.
	 */
	private NumberDataPoint buildDataPoint(final Double value, final String stateValue) {
		final Map<String, String> attributes = new HashMap<>(metric.getAttributes());
		if (stateValue != null) {
			attributes.put(STATE_ATTRIBUTE_KEY, stateValue);
		}

		return NumberDataPoint
			.newBuilder()
			.setAsDouble(value)
			.setTimeUnixNano(metric.getCollectTime() * 1_000_000L)
			.addAllAttributes(
				attributes
					.entrySet()
					.stream()
					.filter(entry -> entry.getValue() != null)
					.filter(entry -> OtelHelper.isAcceptedKey(entry.getKey()))
					.map(entry ->
						KeyValue
							.newBuilder()
							.setKey(entry.getKey())
							.setValue(AnyValue.newBuilder().setStringValue(entry.getValue()).build())
							.build()
					)
					.toList()
			)
			.build();
	}
}
