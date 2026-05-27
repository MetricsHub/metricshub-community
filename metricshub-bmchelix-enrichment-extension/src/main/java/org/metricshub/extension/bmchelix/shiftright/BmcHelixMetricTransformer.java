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

import io.opentelemetry.proto.metrics.v1.Metric;
import io.opentelemetry.proto.metrics.v1.ResourceMetrics;
import io.opentelemetry.proto.metrics.v1.ScopeMetrics;
import io.opentelemetry.proto.resource.v1.Resource;
import java.util.Map;

/**
 * Transforms metrics with Helix-specific attribute and naming rules.
 */
public class BmcHelixMetricTransformer {

	private final BmcHelixMetricRules rules = new BmcHelixMetricRules();
	private final BmcHelixOtelAttributeMapper attributeMapper = new BmcHelixOtelAttributeMapper();

	/**
	 * Transform a resource metrics payload by enriching resource attributes.
	 *
	 * @param resourceMetrics the resource metrics to transform
	 * @return transformed resource metrics
	 */
	ResourceMetrics transform(final ResourceMetrics resourceMetrics) {
		if (resourceMetrics == null) {
			return ResourceMetrics.getDefaultInstance();
		}

		final Map<String, String> resourceAttributes = attributeMapper.toMap(
			resourceMetrics.getResource().getAttributesList()
		);

		applyRulesToResource(resourceAttributes, resourceMetrics);

		final Resource updatedResource = Resource.newBuilder(resourceMetrics.getResource())
			.clearAttributes()
			.addAllAttributes(attributeMapper.toKeyValues(resourceAttributes))
			.build();

		return ResourceMetrics.newBuilder(resourceMetrics).setResource(updatedResource).build();
	}

	/**
	 * Apply identity rules to enrich resource attributes.
	 *
	 * @param resourceAttributes the resource-level attributes map
	 * @param resourceMetrics the resource metrics containing metric names
	 */
	private void applyRulesToResource(
		final Map<String, String> resourceAttributes,
		final ResourceMetrics resourceMetrics
	) {
		for (ScopeMetrics scopeMetrics : resourceMetrics.getScopeMetricsList()) {
			for (Metric metric : scopeMetrics.getMetricsList()) {
				rules.enrichAttributes(metric.getName(), resourceAttributes);
			}
		}
	}
}
