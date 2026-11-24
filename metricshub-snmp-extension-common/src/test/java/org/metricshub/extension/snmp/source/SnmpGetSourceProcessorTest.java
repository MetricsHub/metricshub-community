package org.metricshub.extension.snmp.source;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.metricshub.engine.configuration.HostConfiguration;
import org.metricshub.engine.connector.model.common.ContentType;
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
		doThrow(new InterruptedException("Test exception")).when(snmpRequestExecutor)
			.executeSNMPGet("test_oid", snmpConfiguration, "hostname", true, "hostname");

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
		when(snmpRequestExecutor.executeSNMPGet("test_oid", snmpConfiguration, "hostname", true, "hostname")).thenReturn(
			"result"
		);

		SourceTable result = snmpGetSourceProcessor.process(snmpGetSource, "connectorId", telemetryManager);

		assertEquals(SourceTable.ofText("result"), result);
	}

	// The value is the text, whole: no split on ";" or on a line break. An empty value is an empty text; no value (null) is no row, as a failure.
	@Test
	void testProcess_ValueIsTheWholeText() throws Exception {
		SnmpGetSource snmpGetSource = SnmpGetSource.builder().oid("test_oid").build();
		TelemetryManager telemetryManager = createTelemetryManagerWithHostConfiguration();
		ISnmpConfiguration snmpConfiguration = mock(ISnmpConfiguration.class);
		when(configurationRetriever.apply(telemetryManager)).thenReturn(snmpConfiguration);
		when(snmpRequestExecutor.executeSNMPGet("test_oid", snmpConfiguration, "hostname", true, "hostname")).thenReturn(
			"a;b",
			"two\r\nlines",
			"",
			null
		);

		assertEquals(SourceTable.ofText("a;b"), snmpGetSourceProcessor.process(snmpGetSource, "id", telemetryManager));
		assertEquals(
			SourceTable.ofText("two\nlines"),
			snmpGetSourceProcessor.process(snmpGetSource, "id", telemetryManager)
		);
		assertEquals(SourceTable.ofText(null), snmpGetSourceProcessor.process(snmpGetSource, "id", telemetryManager));
		assertEquals(SourceTable.empty(), snmpGetSourceProcessor.process(snmpGetSource, "id", telemetryManager));
	}

	// output: table: one row of one cell holding the value, never split; an empty value is one empty cell; no value,
	// a failed request or no configuration is no row
	@Test
	void testProcess_OutputTable() throws Exception {
		SnmpGetSource snmpGetSource = SnmpGetSource.builder().oid("test_oid").output(ContentType.TABLE).build();
		TelemetryManager telemetryManager = createTelemetryManagerWithHostConfiguration();
		ISnmpConfiguration snmpConfiguration = mock(ISnmpConfiguration.class);
		when(configurationRetriever.apply(telemetryManager)).thenReturn(
			snmpConfiguration,
			snmpConfiguration,
			snmpConfiguration,
			snmpConfiguration,
			null
		);
		when(snmpRequestExecutor.executeSNMPGet("test_oid", snmpConfiguration, "hostname", true, "hostname")).thenReturn(
			"a;b\nc;d",
			"",
			null
		).thenThrow(new InterruptedException("Test exception"));

		assertEquals(
			SourceTable.ofTable(List.of(List.of("a;b\nc;d"))),
			snmpGetSourceProcessor.process(snmpGetSource, "id", telemetryManager)
		);
		assertEquals(
			SourceTable.ofTable(List.of(List.of(""))),
			snmpGetSourceProcessor.process(snmpGetSource, "id", telemetryManager)
		);
		assertEquals(SourceTable.empty(), snmpGetSourceProcessor.process(snmpGetSource, "id", telemetryManager));
		assertEquals(SourceTable.empty(), snmpGetSourceProcessor.process(snmpGetSource, "id", telemetryManager));
		assertEquals(SourceTable.empty(), snmpGetSourceProcessor.process(snmpGetSource, "id", telemetryManager));
	}

	/**
	 * Utility method to create a telemetryManager
	 *
	 * @return a configured telemetryManager instance
	 */
	private TelemetryManager createTelemetryManagerWithHostConfiguration() {
		HostConfiguration hostConfiguration = HostConfiguration.builder()
			.hostname("hostname")
			.configurations(Map.of())
			.build();
		TelemetryManager telemetryManager = TelemetryManager.builder().hostConfiguration(hostConfiguration).build();
		return telemetryManager;
	}
}
