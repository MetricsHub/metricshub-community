package org.metricshub.agent.opentelemetry;

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

import io.opentelemetry.proto.common.v1.AnyValue;
import io.opentelemetry.proto.common.v1.InstrumentationScope;
import io.opentelemetry.proto.common.v1.KeyValue;
import io.opentelemetry.proto.metrics.v1.Metric;
import io.opentelemetry.proto.metrics.v1.ResourceMetrics;
import io.opentelemetry.proto.metrics.v1.ScopeMetrics;
import io.opentelemetry.proto.resource.v1.Resource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.Builder;
import lombok.Builder.Default;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.metricshub.agent.helper.OtelHelper;
import org.metricshub.agent.opentelemetry.metric.MetricContext;
import org.metricshub.agent.opentelemetry.metric.MetricHandler;
import org.metricshub.agent.opentelemetry.metric.recorder.AbstractMetricRecorder;
import org.metricshub.engine.telemetry.metric.AbstractMetric;

/**
 * ResourceMeter class used to record metrics for a given resource.
 * It safely records metrics by catching any exception and logging it.
 */
@Data
@Builder(setterPrefix = "with")
@Slf4j
public class ResourceMeter {

	private final String instrumentation;

	@Default
	private Map<String, String> attributes = new HashMap<>();

	private final List<AbstractMetricRecorder> metricRecorders = new ArrayList<>();

	private boolean isAppendResourceAttributes;

	/**
	 * Builds ResourceMetrics by invoking all metric recorders safely.
	 *
	 * @return a new {@link ResourceMetrics} instance.
	 */
	public ResourceMetrics recordSafe() {
		try {
			return doRecord();
		} catch (Exception e) {
			log.error(
				"Failed to record resource metrics. Instrumentation {}. Error message: {}",
				instrumentation,
				e.getMessage()
			);
			log.debug("Failed to record resource metrics. Instrumentation {}", e);
			return ResourceMetrics.getDefaultInstance();
		}
	}

	/**
	 * Builds ResourceMetrics by invoking all metric recorders.
	 *
	 * @return a new {@link ResourceMetrics} instance.
	 */
	private ResourceMetrics doRecord() {
		final Resource resource = Resource
			.newBuilder()
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

		final List<Metric> metrics = metricRecorders
			.stream()
			.map(AbstractMetricRecorder::doRecord)
			.filter(Optional::isPresent)
			.map(Optional::get)
			.toList();

		final ScopeMetrics scopeMetrics = ScopeMetrics
			.newBuilder()
			.setScope(InstrumentationScope.newBuilder().setName(instrumentation).build())
			.addAllMetrics(metrics)
			.build();

		return ResourceMetrics.newBuilder().setResource(resource).addScopeMetrics(scopeMetrics).build();
	}

	/**
	 * Registers a metric recorder to be invoked when recording metrics.
	 *
	 * @param context the metric context defining the metric's metadata such as description, unit, etc.
	 * @param metric the metric from the MetricsHub engine to record.
	 */
	public void registerRecorder(final MetricContext context, final AbstractMetric metric) {
		// Handle the metric and add the metric recorders to the list.
		metricRecorders.addAll(
			MetricHandler.handle(context, metric, isAppendResourceAttributes ? attributes : Collections.emptyMap())
		);
	}

	/**
	 * Clears all registered metric recorders.
	 * This method should be called after metrics have been exported to prevent memory leaks.
	 */
	public void clearRecorders() {
		metricRecorders.clear();
	}
}
