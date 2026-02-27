package org.metricshub.extension.bmchelix;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.opentelemetry.proto.common.v1.AnyValue;
import io.opentelemetry.proto.common.v1.KeyValue;
import io.opentelemetry.proto.metrics.v1.Gauge;
import io.opentelemetry.proto.metrics.v1.Metric;
import io.opentelemetry.proto.metrics.v1.NumberDataPoint;
import io.opentelemetry.proto.metrics.v1.ResourceMetrics;
import io.opentelemetry.proto.metrics.v1.ScopeMetrics;
import io.opentelemetry.proto.resource.v1.Resource;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.metricshub.engine.telemetry.Monitor;
import org.metricshub.engine.telemetry.TelemetryManager;

class BmcHelixEnrichmentExtensionTest {

	@Test
	void testGetIdReturnsBmcHelix() {
		final BmcHelixEnrichmentExtension extension = new BmcHelixEnrichmentExtension();

		assertEquals("bmchelix", extension.getId(), "Extension id should be bmchelix");
	}

	@Test
	void testEnrichShiftLeftPopulatesMonitorAttributes() {
		final TelemetryManager telemetryManager = new TelemetryManager();
		final Monitor monitor = new Monitor();
		monitor.setId("host-1");
		monitor.setType("host");
		monitor.addAttribute("service.name", "svc-1");

		final Map<String, Monitor> monitorsByType = new HashMap<>();
		monitorsByType.put(monitor.getId(), monitor);
		final Map<String, Map<String, Monitor>> monitors = new HashMap<>();
		monitors.put(monitor.getType(), monitorsByType);
		telemetryManager.setMonitors(monitors);

		final BmcHelixEnrichmentExtension extension = new BmcHelixEnrichmentExtension();
		extension.enrichShiftLeft(telemetryManager);

		assertEquals("host-1", monitor.getAttribute(BmcHelixEnrichmentExtension.ENTITY_NAME_KEY));
		assertEquals("host", monitor.getAttribute(BmcHelixEnrichmentExtension.ENTITY_TYPE_ID_KEY));
		assertEquals("svc-1", monitor.getAttribute(BmcHelixEnrichmentExtension.INSTANCE_NAME_KEY));
	}

	@Test
	void testEnrichShiftRightUpdatesResourceAttributes() {
		final ResourceMetrics resourceMetrics = buildResourceMetrics(
			Map.of("host.name", "host-a", "service.name", "svc-a"),
			"metricshub.agent.uptime"
		);

		final BmcHelixEnrichmentExtension extension = new BmcHelixEnrichmentExtension();
		final List<ResourceMetrics> enriched = extension.enrichShiftRight(List.of(resourceMetrics));

		assertEquals(1, enriched.size(), "Should return a single ResourceMetrics");
		final Map<String, String> resourceAttributes = toMap(enriched.get(0).getResource().getAttributesList());
		assertEquals("host-a", resourceAttributes.get(BmcHelixEnrichmentExtension.ENTITY_NAME_KEY));
		assertEquals("svc-a", resourceAttributes.get(BmcHelixEnrichmentExtension.INSTANCE_NAME_KEY));
		assertEquals("agent", resourceAttributes.get(BmcHelixEnrichmentExtension.ENTITY_TYPE_ID_KEY));
	}

	private static ResourceMetrics buildResourceMetrics(final Map<String, String> attributes, final String metricName) {
		final Resource resource = Resource
			.newBuilder()
			.addAllAttributes(
				attributes
					.entrySet()
					.stream()
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
		final Metric metric = Metric
			.newBuilder()
			.setName(metricName)
			.setGauge(Gauge.newBuilder().addDataPoints(NumberDataPoint.getDefaultInstance()).build())
			.build();
		final ScopeMetrics scopeMetrics = ScopeMetrics.newBuilder().addMetrics(metric).build();
		return ResourceMetrics.newBuilder().setResource(resource).addScopeMetrics(scopeMetrics).build();
	}

	private static Map<String, String> toMap(final List<KeyValue> attributes) {
		return attributes
			.stream()
			.collect(Collectors.toMap(KeyValue::getKey, keyValue -> keyValue.getValue().getStringValue()));
	}
}
