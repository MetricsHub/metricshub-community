package org.metricshub.extension.snmp.source;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.function.Function;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.metricshub.engine.configuration.HostConfiguration;
import org.metricshub.engine.connector.model.monitor.task.source.SnmpGetSource;
import org.metricshub.engine.strategy.source.SourceTable;
import org.metricshub.engine.telemetry.TelemetryManager;
import org.metricshub.extension.snmp.AbstractSnmpRequestExecutor;
import org.metricshub.extension.snmp.ISnmpConfiguration;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SnmpGetSourceProcessorTest {

	@Mock
	private AbstractSnmpRequestExecutor snmpRequestExecutor;

	@Mock
	private Function<TelemetryManager, ISnmpConfiguration> configurationRetriever;

	private SnmpGetSourceProcessor snmpGetSourceProcessor;

	@BeforeEach
	public void setUp() {
		snmpGetSourceProcessor = new SnmpGetSourceProcessor(snmpRequestExecutor, configurationRetriever);
	}

	// Test case when snmpGetSource is null.
	@Test
	void testProcess_WhenSourceIsNull_ReturnsEmptySourceTable() {
		SnmpGetSource snmpGetSource = null;
		TelemetryManager telemetryManager = createTelemetryManagerWithHostConfiguration();

		SourceTable result = snmpGetSourceProcessor.process(snmpGetSource, "connectorId", telemetryManager);
		assertEquals(SourceTable.empty(), result);
	}

	// Test case for when snmpconfiguration is null."
	@Test
	void testProcess_WhenConfigurationIsNull_ReturnsEmptySourceTable() {
		SnmpGetSource snmpGetSource = SnmpGetSource.builder().oid("test_oid").build();
		TelemetryManager telemetryManager = createTelemetryManagerWithHostConfiguration();
		when(configurationRetriever.apply(telemetryManager)).thenReturn(null);

		SourceTable result = snmpGetSourceProcessor.process(snmpGetSource, "connectorId", telemetryManager);
		assertEquals(SourceTable.empty(), result);
	}

	// Test case when the requestExecutor throws an exception.
	@Test
	void testProcess_WhenRequestExecutorThrowsException_ReturnsEmptySourceTable() throws Exception {
		SnmpGetSource snmpGetSource = SnmpGetSource.builder().oid("test_oid").build();
		TelemetryManager telemetryManager = createTelemetryManagerWithHostConfiguration();
		ISnmpConfiguration snmpConfiguration = mock(ISnmpConfiguration.class);
		when(configurationRetriever.apply(telemetryManager)).thenReturn(snmpConfiguration);
		doThrow(new InterruptedException("Test exception"))
			.when(snmpRequestExecutor)
			.executeSNMPGet("test_oid", snmpConfiguration, "hostname", true);

		SourceTable result = snmpGetSourceProcessor.process(snmpGetSource, "connectorId", telemetryManager);
		assertEquals(SourceTable.empty(), result);
	}

	//Test case when requestExecutor returns a valid result.
	@Test
	void testProcess_WhenRequestExecutorReturnsValidResult_ReturnsSourceTableWithResult() throws Exception {
		SnmpGetSource snmpGetSource = SnmpGetSource.builder().oid("test_oid").build();
		TelemetryManager telemetryManager = createTelemetryManagerWithHostConfiguration();
		ISnmpConfiguration snmpConfiguration = mock(ISnmpConfiguration.class);
		when(configurationRetriever.apply(telemetryManager)).thenReturn(snmpConfiguration);
		when(snmpRequestExecutor.executeSNMPGet("test_oid", snmpConfiguration, "hostname", true)).thenReturn("result");

		SourceTable result = snmpGetSourceProcessor.process(snmpGetSource, "connectorId", telemetryManager);

		assertNotNull(result);
		assertTrue(result.getTable().size() == 1);
		assertEquals("result", result.getTable().get(0).get(0));
	}

	/**
	 * Utility method to create a telemetryManager
	 *
	 * @return a configured telemetryManager instance
	 */
	private TelemetryManager createTelemetryManagerWithHostConfiguration() {
		HostConfiguration hostConfiguration = HostConfiguration
			.builder()
			.hostname("hostname")
			.configurations(Map.of())
			.build();
		TelemetryManager telemetryManager = TelemetryManager.builder().hostConfiguration(hostConfiguration).build();
		return telemetryManager;
	}
}
