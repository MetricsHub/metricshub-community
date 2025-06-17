package org.metricshub.extension.jmx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.metricshub.engine.configuration.HostConfiguration;
import org.metricshub.engine.connector.model.monitor.task.source.JmxSource;
import org.metricshub.engine.strategy.source.SourceTable;
import org.metricshub.engine.telemetry.TelemetryManager;

class JmxSourceProcessorTest {

	private JmxRequestExecutor jmxExecutor;
	private TelemetryManager telemetryManager;
	private JmxSourceProcessor processor;

	@BeforeEach
	void setup() {
		jmxExecutor = mock(JmxRequestExecutor.class);
		telemetryManager = mock(TelemetryManager.class);
		processor = new JmxSourceProcessor(jmxExecutor);
	}

	@Test
	void testShouldReturnSourceTableWithFetchedRows() throws Exception {
		final JmxConfiguration jmxConfig = JmxConfiguration.builder().hostname("localhost").build();
		final List<List<String>> mockResponse = List.of(List.of("attr1", "value1"));

		final HostConfiguration hostConfig = mock(HostConfiguration.class);
		when(hostConfig.getConfigurations()).thenReturn(Map.of(JmxConfiguration.class, jmxConfig));
		when(telemetryManager.getHostConfiguration()).thenReturn(hostConfig);

		final JmxSource source = new JmxSource();
		source.setObjectName("java.lang:type=Memory");
		source.setAttributes(List.of("HeapMemoryUsage"));
		source.setKeyProperties(List.of());

		when(jmxExecutor.fetchBeanInfo(jmxConfig, "java.lang:type=Memory", List.of("HeapMemoryUsage"), List.of()))
			.thenReturn(mockResponse);

		final SourceTable resultTable = processor.process(source, telemetryManager);

		assertNotNull(resultTable, "SourceTable should not be null");
		assertEquals(1, resultTable.getTable().size(), "Expected one row in SourceTable");
		assertEquals("attr1", resultTable.getTable().get(0).get(0), "First cell value should match fetched data");
	}

	@Test
	void testShouldHandleNullAttributesAndKeyProperties() throws Exception {
		final JmxConfiguration jmxConfig = JmxConfiguration.builder().hostname("testHost").build();

		final HostConfiguration hostConfig = mock(HostConfiguration.class);
		when(hostConfig.getConfigurations()).thenReturn(Map.of(JmxConfiguration.class, jmxConfig));
		when(telemetryManager.getHostConfiguration()).thenReturn(hostConfig);

		final JmxSource source = new JmxSource();
		source.setObjectName("java.lang:type=Runtime");
		source.setAttributes(null);
		source.setKeyProperties(null);

		when(jmxExecutor.fetchBeanInfo(jmxConfig, "java.lang:type=Runtime", List.of(), List.of())).thenReturn(List.of());

		final SourceTable resultTable = processor.process(source, telemetryManager);

		assertNotNull(resultTable, "SourceTable should not be null");
		assertTrue(resultTable.getTable().isEmpty(), "Expected empty table when no attributes are returned");
	}

	@Test
	void testShouldHandleFetchExceptionGracefully() throws Exception {
		final JmxConfiguration jmxConfig = JmxConfiguration.builder().hostname("errHost").build();

		final HostConfiguration hostConfig = mock(HostConfiguration.class);
		when(hostConfig.getConfigurations()).thenReturn(Map.of(JmxConfiguration.class, jmxConfig));
		when(telemetryManager.getHostConfiguration()).thenReturn(hostConfig);

		final JmxSource source = new JmxSource();
		source.setObjectName("java.lang:type=GarbageCollector");
		source.setAttributes(List.of("CollectionTime"));
		source.setKeyProperties(List.of());

		when(jmxExecutor.fetchBeanInfo(any(), any(), any(), any()))
			.thenThrow(new RuntimeException("JMX connection failed"));

		final SourceTable resultTable = processor.process(source, telemetryManager);

		assertNotNull(resultTable, "SourceTable should not be null even if exception occurs");
		assertTrue(resultTable.getTable().isEmpty(), "Expected empty table if fetchBeanInfo fails");
	}
}
