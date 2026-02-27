package org.metricshub.extension.bmchelix.shiftright;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.opentelemetry.proto.metrics.v1.Gauge;
import io.opentelemetry.proto.metrics.v1.Metric;
import io.opentelemetry.proto.metrics.v1.NumberDataPoint;
import io.opentelemetry.proto.metrics.v1.ResourceMetrics;
import io.opentelemetry.proto.metrics.v1.ScopeMetrics;
import io.opentelemetry.proto.resource.v1.Resource;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.metricshub.extension.bmchelix.BmcHelixEnrichmentExtension;

class BmcHelixMetricsEnricherTest {

	@Test
	void testEnrichTransformsEachResourceMetrics() {
		final ResourceMetrics resourceMetrics = buildResourceMetrics(Map.of("site", "site-a"), "hw.site.pue");

		final List<ResourceMetrics> enriched = new BmcHelixMetricsEnricher().enrich(List.of(resourceMetrics));

		assertEquals(1, enriched.size(), "Should return a single ResourceMetrics");
		final Map<String, String> attributes = new BmcHelixOtelAttributeMapper()
			.toMap(enriched.get(0).getResource().getAttributesList());
		assertEquals("site-a", attributes.get(BmcHelixEnrichmentExtension.ENTITY_NAME_KEY));
		assertEquals("site", attributes.get(BmcHelixEnrichmentExtension.ENTITY_TYPE_ID_KEY));
	}

	private static ResourceMetrics buildResourceMetrics(final Map<String, String> attributes, final String metricName) {
		final BmcHelixOtelAttributeMapper mapper = new BmcHelixOtelAttributeMapper();
		final Resource resource = Resource.newBuilder().addAllAttributes(mapper.toKeyValues(attributes)).build();
		final Metric metric = Metric
			.newBuilder()
			.setName(metricName)
			.setGauge(Gauge.newBuilder().addDataPoints(NumberDataPoint.getDefaultInstance()).build())
			.build();
		final ScopeMetrics scopeMetrics = ScopeMetrics.newBuilder().addMetrics(metric).build();
		return ResourceMetrics.newBuilder().setResource(resource).addScopeMetrics(scopeMetrics).build();
	}
}
