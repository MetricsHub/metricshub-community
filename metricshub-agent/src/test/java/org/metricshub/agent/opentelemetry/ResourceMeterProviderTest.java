package org.metricshub.agent.opentelemetry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import io.opentelemetry.proto.common.v1.AnyValue;
import io.opentelemetry.proto.common.v1.KeyValue;
import io.opentelemetry.proto.metrics.v1.Gauge;
import io.opentelemetry.proto.metrics.v1.Metric;
import io.opentelemetry.proto.metrics.v1.NumberDataPoint;
import io.opentelemetry.proto.metrics.v1.ResourceMetrics;
import io.opentelemetry.proto.metrics.v1.ScopeMetrics;
import io.opentelemetry.proto.resource.v1.Resource;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.metricshub.agent.service.TestHelper;
import org.metricshub.extension.bmchelix.BmcHelixEnrichmentExtension;
import org.metricshub.extension.bmchelix.shiftright.BmcHelixOtelAttributeMapper;

class ResourceMeterProviderTest {

	private ResourceMeterProvider provider;
	private TestHelper.TestOtelClient client;

	@BeforeEach
	void setUp() {
		client = new TestHelper.TestOtelClient();
		provider = new ResourceMeterProvider(MetricsExporter.builder().withClient(client).build(), List.of());
	}

	@Test
	void newResourceMeter_shouldCreateAndRegisterResourceMeter() {
		final ResourceMeter meter = provider.newResourceMeter("test.instrumentation", Map.of("key", "value"));

		assertNotNull(meter, "ResourceMeter should not be null");
		assertEquals("test.instrumentation", meter.getInstrumentation(), "Instrumentation name should match");
		assertEquals(1, provider.getMeters().size(), "Provider should register one meter");
	}

	@Test
	void exportMetrics_shouldExportAllRegisteredMeters() {
		provider.newResourceMeter("test.instrumentation1", Map.of("key1", "value1"));
		provider.newResourceMeter("test.instrumentation2", Map.of("key2", "value2"));

		provider.exportMetrics(() -> {});

		assertEquals(2, client.getRequest().getResourceMetricsList().size(), "All registered meters should be exported");
	}

	@Test
	void exportMetrics_shouldExportAllRegisteredMeters_withResourceAttributes() {
		provider =
			new ResourceMeterProvider(
				MetricsExporter.builder().withClient(client).withIsAppendResourceAttributes(true).build(),
				List.of()
			);
		provider.newResourceMeter("test.instrumentation1", Map.of("key1", "value1"));
		provider.newResourceMeter("test.instrumentation2", Map.of("key2", "value2"));

		provider.exportMetrics(() -> {});

		assertEquals(2, client.getRequest().getResourceMetricsList().size(), "All registered meters should be exported");
	}

	@Test
	void enrichMetrics_shouldApplyBmchelixEnrichment() {
		final ResourceMetrics resourceMetrics = buildResourceMetrics(
			Map.of("host.name", "host-a", "service.name", "svc-a"),
			"metricshub.agent.uptime"
		);
		final ResourceMeterProvider localProvider = new ResourceMeterProvider(
			MetricsExporter.builder().withClient(client).build(),
			List.of(new BmcHelixEnrichmentExtension())
		);

		final List<ResourceMetrics> enriched = localProvider.enrichMetrics(List.of(resourceMetrics));

		assertEquals(1, enriched.size(), "Should return a single ResourceMetrics");
		final Map<String, String> attributes = new BmcHelixOtelAttributeMapper().toMap(
			enriched.get(0).getResource().getAttributesList()
		);
		assertEquals("host-a", attributes.get(BmcHelixEnrichmentExtension.ENTITY_NAME_KEY));
		assertEquals("svc-a", attributes.get(BmcHelixEnrichmentExtension.INSTANCE_NAME_KEY));
		assertEquals("agent", attributes.get(BmcHelixEnrichmentExtension.ENTITY_TYPE_ID_KEY));
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
					.collect(Collectors.toList())
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
}
