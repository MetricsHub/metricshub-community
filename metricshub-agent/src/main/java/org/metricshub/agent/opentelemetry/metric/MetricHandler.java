package org.metricshub.agent.opentelemetry.metric;

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

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Stream;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.metricshub.agent.opentelemetry.ResourceMeter;
import org.metricshub.agent.opentelemetry.metric.recorder.AbstractMetricRecorder;
import org.metricshub.agent.opentelemetry.metric.recorder.CounterMetricRecorder;
import org.metricshub.agent.opentelemetry.metric.recorder.CounterStateMetricRecorder;
import org.metricshub.agent.opentelemetry.metric.recorder.CounterSuppressZerosStateMetricRecorder;
import org.metricshub.agent.opentelemetry.metric.recorder.GaugeMetricRecorder;
import org.metricshub.agent.opentelemetry.metric.recorder.GaugeStateMetricRecorder;
import org.metricshub.agent.opentelemetry.metric.recorder.GaugeSuppressZerosStateMetricRecorder;
import org.metricshub.agent.opentelemetry.metric.recorder.UpDownCounterMetricRecorder;
import org.metricshub.agent.opentelemetry.metric.recorder.UpDownCounterStateMetricRecorder;
import org.metricshub.agent.opentelemetry.metric.recorder.UpDownCounterSuppressZerosStateMetricRecorder;
import org.metricshub.engine.connector.model.metric.MetricType;
import org.metricshub.engine.telemetry.metric.AbstractMetric;
import org.metricshub.engine.telemetry.metric.NumberMetric;
import org.metricshub.engine.telemetry.metric.StateSetMetric;

/**
 * MetricHandler class used to handle metrics and generate the corresponding metric recorders, see {@link AbstractMetricRecorder}.
 * The metric recorders will be used to record the metrics by {@link ResourceMeter}.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class MetricHandler {

	/**
	 * The handlers map used to handle the different metric types.
	 */
	// @formatter:off
	private static final Map<MetricType, BiFunction<ResourceMetricContext, AbstractMetric, List<AbstractMetricRecorder>>> HANDLERS = Map.of(
		MetricType.GAUGE, MetricHandler::handleGauge,
		MetricType.COUNTER, MetricHandler::handleCounter,
		MetricType.UP_DOWN_COUNTER, MetricHandler::handleUpDownCounter
	);

	// @formatter:on

	/**
	 * Handles the metric and generates the corresponding metric recorders.
	 */
	public static List<AbstractMetricRecorder> handle(
		final MetricContext context,
		final AbstractMetric metric,
		final Map<String, String> resourceAttributes
	) {
		return HANDLERS.get(context.getType()).apply(new ResourceMetricContext(context, resourceAttributes), metric);
	}

	/**
	 * Hander for gauge metrics.
	 *
	 * @param resourceMetricContext Metric information context defining the metric type, unit, description, resource attributes etc.
	 * @param metric                The metric to handle.
	 * @return a list of {@link AbstractMetricRecorder} instances.
	 */
	private static List<AbstractMetricRecorder> handleGauge(
		final ResourceMetricContext resourceMetricContext,
		final AbstractMetric metric
	) {
		final MetricContext context = resourceMetricContext.context;
		final Map<String, String> resourceAttributes = resourceMetricContext.resourceAttributes;

		if (metric instanceof NumberMetric numberMetric) {
			return List.of(
				GaugeMetricRecorder
					.builder()
					.withMetric(numberMetric)
					.withDescription(context.getDescription())
					.withUnit(context.getUnit())
					.withResourceAttributes(resourceAttributes)
					.build()
			);
		} else if (metric instanceof StateSetMetric stateSetMetric) {
			final Function<String, AbstractMetricRecorder> creator = context.isSuppressZerosCompression()
				? state ->
					GaugeSuppressZerosStateMetricRecorder
						.builder()
						.withMetric(stateSetMetric)
						.withDescription(context.getDescription())
						.withUnit(context.getUnit())
						.withStateValue(state)
						.withResourceAttributes(resourceAttributes)
						.build()
				: state ->
					GaugeStateMetricRecorder
						.builder()
						.withMetric(stateSetMetric)
						.withDescription(context.getDescription())
						.withUnit(context.getUnit())
						.withStateValue(state)
						.withResourceAttributes(resourceAttributes)
						.build();

			return Stream.of(stateSetMetric.getStateSet()).map(creator).toList();
		}

		return Collections.emptyList();
	}

	/**
	 * Handler for counter metrics.
	 *
	 * @param resourceMetricContext Metric information context defining the metric type, unit, description, resource attributes etc.
	 * @param metric                The metric to handle.
	 * @return a list of {@link AbstractMetricRecorder} instances.
	 */
	private static List<AbstractMetricRecorder> handleCounter(
		final ResourceMetricContext resourceMetricContext,
		final AbstractMetric metric
	) {
		final MetricContext context = resourceMetricContext.context;
		final Map<String, String> resourceAttributes = resourceMetricContext.resourceAttributes;
		if (metric instanceof NumberMetric numberMetric) {
			return List.of(
				CounterMetricRecorder
					.builder()
					.withMetric(numberMetric)
					.withDescription(context.getDescription())
					.withUnit(context.getUnit())
					.withResourceAttributes(resourceAttributes)
					.build()
			);
		} else if (metric instanceof StateSetMetric stateSetMetric) {
			final Function<String, AbstractMetricRecorder> creator = context.isSuppressZerosCompression()
				? state ->
					CounterSuppressZerosStateMetricRecorder
						.builder()
						.withMetric(stateSetMetric)
						.withDescription(context.getDescription())
						.withUnit(context.getUnit())
						.withStateValue(state)
						.withResourceAttributes(resourceAttributes)
						.build()
				: state ->
					CounterStateMetricRecorder
						.builder()
						.withMetric(stateSetMetric)
						.withDescription(context.getDescription())
						.withUnit(context.getUnit())
						.withStateValue(state)
						.withResourceAttributes(resourceAttributes)
						.build();

			return Stream.of(stateSetMetric.getStateSet()).map(creator).toList();
		}

		return Collections.emptyList();
	}

	/**
	 * Handler for up-down counter metrics.
	 *
	 * @param resourceMetricContext Metric information context defining the metric type, unit, description, resource attributes etc.
	 * @param metric                The metric to handle.
	 * @return a list of {@link AbstractMetricRecorder} instances.
	 */
	private static List<AbstractMetricRecorder> handleUpDownCounter(
		final ResourceMetricContext resourceMetricContext,
		final AbstractMetric metric
	) {
		final MetricContext context = resourceMetricContext.context;
		final Map<String, String> resourceAttributes = resourceMetricContext.resourceAttributes;
		if (metric instanceof NumberMetric numberMetric) {
			return List.of(
				UpDownCounterMetricRecorder
					.builder()
					.withMetric(numberMetric)
					.withDescription(context.getDescription())
					.withUnit(context.getUnit())
					.withResourceAttributes(resourceAttributes)
					.build()
			);
		} else if (metric instanceof StateSetMetric stateSetMetric) {
			final Function<String, AbstractMetricRecorder> creator = context.isSuppressZerosCompression()
				? state ->
					UpDownCounterSuppressZerosStateMetricRecorder
						.builder()
						.withMetric(stateSetMetric)
						.withDescription(context.getDescription())
						.withUnit(context.getUnit())
						.withStateValue(state)
						.withResourceAttributes(resourceAttributes)
						.build()
				: state ->
					UpDownCounterStateMetricRecorder
						.builder()
						.withMetric(stateSetMetric)
						.withDescription(context.getDescription())
						.withUnit(context.getUnit())
						.withStateValue(state)
						.withResourceAttributes(resourceAttributes)
						.build();

			return Stream.of(stateSetMetric.getStateSet()).map(creator).toList();
		}

		return Collections.emptyList();
	}

	/**
	 * ResourceMetricContext class used to hold the metric context and the resource attributes.
	 */
	private static record ResourceMetricContext(MetricContext context, Map<String, String> resourceAttributes) {}
}
