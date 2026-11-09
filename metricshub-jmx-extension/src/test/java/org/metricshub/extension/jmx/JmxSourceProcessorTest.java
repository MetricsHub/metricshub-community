package org.metricshub.extension.jmx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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

		when(jmxExecutor.fetchMBean(jmxConfig, "java.lang:type=Memory", List.of("HeapMemoryUsage"), List.of(), null, null))
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

		when(jmxExecutor.fetchMBean(jmxConfig, "java.lang:type=Runtime", List.of(), List.of(), null, null)).thenReturn(
			List.of()
		);

		final SourceTable resultTable = processor.process(source, telemetryManager);

		assertNotNull(resultTable, "SourceTable should not be null");
		assertEquals(SourceTable.empty(), resultTable, "Expected an empty table when no attributes are returned");
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

		when(jmxExecutor.fetchMBean(any(), any(), any(), any(), any(), any())).thenThrow(
			new RuntimeException("JMX connection failed")
		);

		final SourceTable resultTable = processor.process(source, telemetryManager);

		assertNotNull(resultTable, "SourceTable should not be null even if exception occurs");
		assertEquals(SourceTable.empty(), resultTable, "Expected an empty table if fetchBeanInfo fails");
	}

	/**
	 * A <code>;</code> inside a cell is just a character of the cell: the result is a table, never serialized.
	 */
	@Test
	void testTableKeepsSeparatorInsideCells() throws Exception {
		final JmxConfiguration jmxConfig = JmxConfiguration.builder().hostname("localhost").build();
		final HostConfiguration hostConfig = mock(HostConfiguration.class);
		when(hostConfig.getConfigurations()).thenReturn(Map.of(JmxConfiguration.class, jmxConfig));
		when(telemetryManager.getHostConfiguration()).thenReturn(hostConfig);

		final JmxSource source = new JmxSource();
		source.setObjectName("java.lang:type=Memory");
		source.setAttributes(List.of("Name"));
		source.setKeyProperties(List.of());
		when(jmxExecutor.fetchMBean(jmxConfig, "java.lang:type=Memory", List.of("Name"), List.of(), null, null)).thenReturn(
			List.of(List.of("a;b", "c"), List.of("", "d"))
		);

		final SourceTable result = processor.process(source, telemetryManager);

		assertEquals(SourceTable.ofTable(List.of(List.of("a;b", "c"), List.of("", "d"))), result);
	}
}
