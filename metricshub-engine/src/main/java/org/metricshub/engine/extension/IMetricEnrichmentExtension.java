package org.metricshub.engine.extension;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * MetricsHub Engine
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
import java.util.List;
import org.metricshub.engine.telemetry.TelemetryManager;

/**
 * Extension interface for enriching metrics before export.
 */
public interface IMetricEnrichmentExtension {
	/**
	 * Return a unique identifier for this enrichment extension.
	 *
	 * @return unique extension identifier
	 */
	String getId();

	/**
	 * Enrich metrics before export (shift-right).
	 *
	 * @param recordedMetrics already recorded metrics
	 * @return enriched metrics list
	 */
	List<ResourceMetrics> enrichShiftRight(List<ResourceMetrics> recordedMetrics);

	/**
	 * Enrich metrics earlier in the pipeline using the telemetry manager.
	 *
	 * @param telemetryManager telemetry manager providing host and monitor context
	 */
	default void enrichShiftLeft(final TelemetryManager telemetryManager) {}
}
