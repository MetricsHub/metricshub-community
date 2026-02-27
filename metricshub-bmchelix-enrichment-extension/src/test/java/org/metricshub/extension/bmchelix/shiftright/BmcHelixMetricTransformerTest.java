package org.metricshub.extension.bmchelix.shiftright;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import io.opentelemetry.proto.metrics.v1.Gauge;
import io.opentelemetry.proto.metrics.v1.Metric;
import io.opentelemetry.proto.metrics.v1.NumberDataPoint;
import io.opentelemetry.proto.metrics.v1.ResourceMetrics;
import io.opentelemetry.proto.metrics.v1.ScopeMetrics;
import io.opentelemetry.proto.resource.v1.Resource;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.metricshub.extension.bmchelix.BmcHelixEnrichmentExtension;

class BmcHelixMetricTransformerTest {

	@Test
	void testTransformEnrichesResourceAttributesAndKeepsMetrics() {
		final ResourceMetrics input = buildResourceMetrics(
			Map.of("host.name", "host-a", "service.name", "svc-a", "keep", "value"),
			"metricshub.agent.uptime"
		);

		final ResourceMetrics output = new BmcHelixMetricTransformer().transform(input);

		assertNotNull(output.getResource(), "Resource should be present");
		final Map<String, String> resourceAttributes = new BmcHelixOtelAttributeMapper()
			.toMap(output.getResource().getAttributesList());
		assertEquals("value", resourceAttributes.get("keep"));
		assertEquals("host-a", resourceAttributes.get(BmcHelixEnrichmentExtension.ENTITY_NAME_KEY));
		assertEquals("svc-a", resourceAttributes.get(BmcHelixEnrichmentExtension.INSTANCE_NAME_KEY));
		assertEquals("agent", resourceAttributes.get(BmcHelixEnrichmentExtension.ENTITY_TYPE_ID_KEY));

		assertEquals(1, output.getScopeMetricsCount(), "Should keep a single scope");
		assertEquals(1, output.getScopeMetrics(0).getMetricsCount(), "Should keep a single metric");
		assertEquals("metricshub.agent.uptime", output.getScopeMetrics(0).getMetrics(0).getName());
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
