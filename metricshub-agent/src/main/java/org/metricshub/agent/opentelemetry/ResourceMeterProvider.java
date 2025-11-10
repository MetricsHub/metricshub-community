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

import io.opentelemetry.proto.metrics.v1.ResourceMetrics;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.Getter;

/**
 * ResourceMeterProvider class used to provide {@link ResourceMeter} instances and export metrics.
 */
public class ResourceMeterProvider {

	@Getter
	private MetricsExporter metricsExporter;

	@Getter
	private List<ResourceMeter> meters = new ArrayList<>();

	/**
	 * Constructs a new ResourceMeterProvider instance.
	 *
	 * @param exporter the metrics exporter to use.
	 */
	public ResourceMeterProvider(final MetricsExporter exporter) {
		this.metricsExporter = exporter;
	}

	/**
	 * Records the metrics for all resource meters and exports them.
	 * Metric recorders are cleared immediately after recording (before async export) to prevent memory leaks.
	 * This is safe because the metrics are already serialized into ResourceMetrics protobuf objects.
	 *
	 * @param logContextSetter The log context setter to use for asynchronous logging.
	 */
	public void exportMetrics(final LogContextSetter logContextSetter) {
		// First, record all metrics into protobuf ResourceMetrics objects
		// This extracts all data from the recorders into immutable protobuf structures
		final List<ResourceMetrics> recordedMetrics = meters.stream().map(ResourceMeter::recordSafe).toList();

		// Now we can safely clear the recorders - the data is already captured in recordedMetrics
		// This prevents memory leaks without risking data loss, even though export is async
		meters.forEach(ResourceMeter::clearRecorders);

		// Export the already-recorded metrics asynchronously
		// Since the data is in recordedMetrics (not in recorders), clearing recorders doesn't affect export
		metricsExporter.export(recordedMetrics, logContextSetter);
	}

	/**
	 * Creates a new resource meter and registers it.
	 *
	 * @param instrumentation The name of the instrumentation used to distinguish between different resource metrics.
	 * @param attributes      The attributes to use for the resource.
	 * @return a new {@link ResourceMeter} instance.
	 */
	public ResourceMeter newResourceMeter(final String instrumentation, final Map<String, String> attributes) {
		final ResourceMeter meter = ResourceMeter
			.builder()
			.withInstrumentation(instrumentation)
			.withAttributes(attributes)
			.withIsAppendResourceAttributes(metricsExporter.isAppendResourceAttributes())
			.build();
		meters.add(meter);
		return meter;
	}
}
