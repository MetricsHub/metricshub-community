package org.metricshub.extension.bmchelix.shiftright;

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

/**
 * Applies Helix enrichment rules to resource metrics.
 */
public class BmcHelixMetricsEnricher {

	private final BmcHelixMetricTransformer transformer = new BmcHelixMetricTransformer();

	/**
	 * Enrich recorded metrics and return a transformed list.
	 *
	 * @param recordedMetrics metrics recorded before export
	 * @return enriched metrics list
	 */
	public List<ResourceMetrics> enrich(final List<ResourceMetrics> recordedMetrics) {
		if (recordedMetrics == null || recordedMetrics.isEmpty()) {
			return recordedMetrics;
		}

		return recordedMetrics.stream().map(transformer::transform).toList();
	}
}
