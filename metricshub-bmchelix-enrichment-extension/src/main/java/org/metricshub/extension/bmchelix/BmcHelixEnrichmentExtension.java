package org.metricshub.extension.bmchelix;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * MetricsHub Helix Enrichment Extension
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
import java.util.regex.Pattern;
import org.metricshub.engine.extension.IMetricEnrichmentExtension;
import org.metricshub.engine.telemetry.TelemetryManager;
import org.metricshub.extension.bmchelix.shiftleft.BmcHelixTelemetryManagerEnricher;
import org.metricshub.extension.bmchelix.shiftright.BmcHelixMetricsEnricher;

/**
 * BMC Helix enrichment extension implementation.
 */
public class BmcHelixEnrichmentExtension implements IMetricEnrichmentExtension {

	/**
	 * Attribute key for entity name enrichment.
	 */
	public static final String ENTITY_NAME_KEY = "entityName";

	/**
	 * Attribute key for entity type enrichment.
	 */
	public static final String ENTITY_TYPE_ID_KEY = "entityTypeId";

	/**
	 * Attribute key for instance name enrichment.
	 */
	public static final String INSTANCE_NAME_KEY = "instanceName";

	/**
	 * Regex pattern used to trim surrounding whitespace and colons.
	 */
	private static final Pattern IDENTITY_TRIM_PATTERN = Pattern.compile("^[\\s:]+|[\\s:]+$");

	private final BmcHelixMetricsEnricher metricsEnricher = new BmcHelixMetricsEnricher();
	private final BmcHelixTelemetryManagerEnricher telemetryManagerEnricher = new BmcHelixTelemetryManagerEnricher();

	/**
	 * Return the unique identifier for this enrichment extension.
	 *
	 * @return extension id
	 */
	@Override
	public String getId() {
		return "bmchelix";
	}

	/**
	 * Enrich recorded metrics using Helix rules (shift-right).
	 *
	 * @param recordedMetrics metrics recorded before export
	 * @return enriched metrics list
	 */
	@Override
	public List<ResourceMetrics> enrichShiftRight(final List<ResourceMetrics> recordedMetrics) {
		return metricsEnricher.enrich(recordedMetrics);
	}

	/**
	 * Enrich metrics earlier in the pipeline using the telemetry manager.
	 *
	 * @param telemetryManager telemetry manager providing host and monitor context
	 */
	@Override
	public void enrichShiftLeft(final TelemetryManager telemetryManager) {
		telemetryManagerEnricher.enrich(telemetryManager);
	}

	/**
	 * Normalize identity values by trimming surrounding whitespace and colons.
	 *
	 * @param value raw identity value
	 * @return normalized identity value
	 */
	public static String normalizeIdentityValue(final String value) {
		if (value == null) {
			return null;
		}
		return IDENTITY_TRIM_PATTERN.matcher(value).replaceAll("");
	}
}
