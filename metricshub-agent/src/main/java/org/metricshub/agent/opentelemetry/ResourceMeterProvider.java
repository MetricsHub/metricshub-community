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
import lombok.extern.slf4j.Slf4j;
import org.metricshub.engine.extension.IMetricEnrichmentExtension;

/**
 * ResourceMeterProvider class used to provide {@link ResourceMeter} instances and export metrics.
 */
@Slf4j
public class ResourceMeterProvider {

	@Getter
	private MetricsExporter metricsExporter;

	@Getter
	private List<ResourceMeter> meters = new ArrayList<>();

	@Getter
	private List<IMetricEnrichmentExtension> metricEnrichmentExtensions;

	/**
	 * Constructs a new ResourceMeterProvider instance.
	 *
	 * @param exporter the metrics exporter to use.
	 * @param metricEnrichmentExtensions the metric enrichment extensions to use.
	 */
	public ResourceMeterProvider(
		final MetricsExporter exporter,
		final List<IMetricEnrichmentExtension> metricEnrichmentExtensions
	) {
		this.metricsExporter = exporter;
		this.metricEnrichmentExtensions = metricEnrichmentExtensions != null ? metricEnrichmentExtensions : List.of();
	}

	/**
	 * Records the metrics for all resource meters and exports them.
	 * Metric recorders are cleared immediately after recording (before async export) to prevent memory leaks.
	 * This is safe because the metrics are already serialized into ResourceMetrics protobuf objects.
	 *
	 * @param logContextSetter The log context setter to use for asynchronous logging.
	 */
	public void exportMetrics(final LogContextSetter logContextSetter) {
		final List<ResourceMetrics> recordedMetrics = prepareMetrics();
		final List<ResourceMetrics> enrichedMetrics = enrichMetrics(recordedMetrics);

		// Export the already-recorded metrics asynchronously
		// Since the data is in recordedMetrics (not in recorders), clearing recorders doesn't affect export
		metricsExporter.export(enrichedMetrics, logContextSetter);
	}

	/**
	 * Records metrics and clears recorders after successful recording.
	 *
	 * @return the recorded metrics
	 */
	public List<ResourceMetrics> prepareMetrics() {
		// First, record all metrics into protobuf ResourceMetrics objects
		// This extracts all data from the recorders into immutable protobuf structures
		final List<ResourceMetrics> recordedMetrics = meters.stream().map(ResourceMeter::recordSafe).toList();

		// Now we can safely clear the recorders - the data is already captured in recordedMetrics
		// This prevents memory leaks without risking data loss, even though export is async
		meters.forEach(ResourceMeter::clearRecorders);

		return recordedMetrics;
	}

	/**
	 * Enrich metrics using registered enrichment extensions.
	 *
	 * @param recordedMetrics the metrics recorded during the collection phase
	 * @return the enriched metrics
	 */
	public List<ResourceMetrics> enrichMetrics(final List<ResourceMetrics> recordedMetrics) {
		if (recordedMetrics == null || recordedMetrics.isEmpty()) {
			return recordedMetrics;
		}

		final List<IMetricEnrichmentExtension> enrichers = metricEnrichmentExtensions;
		if (enrichers.isEmpty()) {
			return recordedMetrics;
		}

		List<ResourceMetrics> enrichedMetrics = recordedMetrics;
		for (IMetricEnrichmentExtension enricher : enrichers) {
			try {
				enrichedMetrics = enricher.enrichShiftRight(enrichedMetrics);
			} catch (Exception e) {
				log.error(
					"Failed to enrich metrics with {}. Error: {}",
					enricher != null ? enricher.getClass().getSimpleName() : "<null>",
					e.getMessage()
				);
				log.debug("Failed to enrich metrics. Stack trace:", e);
			}
		}

		return enrichedMetrics;
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
