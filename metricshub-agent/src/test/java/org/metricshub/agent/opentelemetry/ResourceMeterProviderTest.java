package org.metricshub.agent.opentelemetry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.metricshub.agent.service.TestHelper;

class ResourceMeterProviderTest {

	private ResourceMeterProvider provider;
	private TestHelper.TestOtelClient client;

	@BeforeEach
	void setUp() {
		client = new TestHelper.TestOtelClient();
		provider = new ResourceMeterProvider(MetricsExporter.builder().withClient(client).build());
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
				MetricsExporter.builder().withClient(client).withIsAppendResourceAttributes(true).build()
			);
		provider.newResourceMeter("test.instrumentation1", Map.of("key1", "value1"));
		provider.newResourceMeter("test.instrumentation2", Map.of("key2", "value2"));

		provider.exportMetrics(() -> {});

		assertEquals(2, client.getRequest().getResourceMetricsList().size(), "All registered meters should be exported");
	}
}
