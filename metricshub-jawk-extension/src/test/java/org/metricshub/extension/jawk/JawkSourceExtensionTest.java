package org.metricshub.extension.jawk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.metricshub.engine.configuration.ConnectorVariables;
import org.metricshub.engine.configuration.HostConfiguration;
import org.metricshub.engine.connector.model.common.HttpMethod;
import org.metricshub.engine.connector.model.common.ResultContent;
import org.metricshub.engine.connector.model.monitor.task.source.HttpSource;
import org.metricshub.engine.connector.model.monitor.task.source.IpmiSource;
import org.metricshub.engine.connector.model.monitor.task.source.JawkSource;
import org.metricshub.engine.connector.model.monitor.task.source.SnmpGetSource;
import org.metricshub.engine.connector.model.monitor.task.source.SnmpTableSource;
import org.metricshub.engine.connector.model.monitor.task.source.Source;
import org.metricshub.engine.connector.model.monitor.task.source.WbemSource;
import org.metricshub.engine.connector.model.monitor.task.source.WmiSource;
import org.metricshub.engine.strategy.source.SourceProcessor;
import org.metricshub.engine.strategy.source.SourceTable;
import org.metricshub.engine.telemetry.HostProperties;
import org.metricshub.engine.telemetry.TelemetryManager;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class JawkSourceExtensionTest {

	private static final String RAW_DATA_RESULT_1 = "{\"myResult\": \"rawData result 1\"}";
	private static final String RAW_DATA_RESULT_2 = "{\"myResult\": \"rawData result 2\"}";
	private static final String RAW_DATA_RESULT_3 = "{\"myResult\": \"rawData result 3\"}";
	private static final String DATA_RESULT_1 = "rawData result 1";
	private static final String DATA_RESULT_2 = "rawData result 2";
	private static final String DATA_RESULT_3 = "rawData result 3";
	private static final String CONNECTOR_ID = "connectorId";

	@Mock
	private SourceProcessor sourceProcessorMock;

	@Test
	void testProcessSource() {
		final ConnectorVariables connectorVariables = ConnectorVariables
			.builder()
			.variableValues(Map.of("myVariable", "myVariableValue"))
			.build();
		final JawkSourceExtension jawkExtension = new JawkSourceExtension();
		final TelemetryManager telemetryManager = TelemetryManager
			.builder()
			.hostConfiguration(
				HostConfiguration
					.builder()
					.hostname("test-host")
					.hostId("test-host")
					.connectorVariables(Map.of(CONNECTOR_ID, connectorVariables))
					.build()
			)
			.build();

		final List<String> line1 = Arrays.asList("FOO", "1", "2", "3");
		final List<String> line2 = Arrays.asList("BAR", "10", "20", "30");
		final List<String> line3 = Arrays.asList("BAZ", "100", "200", "300");
		final List<List<String>> tableOne = Arrays.asList(line1, line2, line3);
		final SourceTable sourceTableOne = SourceTable
			.builder()
			.table(tableOne)
			.rawData(SourceTable.tableToCsv(tableOne, ";", false))
			.build();

		final HostProperties hostProperties = HostProperties.builder().build();

		hostProperties
			.getConnectorNamespace(CONNECTOR_ID)
			.addSourceTable("${source::monitors.system.discovery.sources.source_one}", sourceTableOne);

		telemetryManager.setHostProperties(hostProperties);

		// Http request & Json2CSV
		final Source source = JawkSource
			.builder()
			.type("Awk")
			.input("${source::monitors.system.discovery.sources.source_one}")
			.script(
				"""
					BEGIN {
					    FS=";"
					    OFS=";"
				    }

				    {
					    requestArguments["method"] = "get"
					    requestArguments["path"] = "/ConfigurationManager/v1/objects/storages/" $1
					    requestArguments["resultContent"] = "body"
					    requestArguments["header"] = getVariable("myVariable")
					    json2csvArguments["jsonSource"] = executeHttpRequest(requestArguments)
					    json2csvArguments["entryKey"] = "/"
					    json2csvArguments["properties"] = "myResult"
					    json2csvArguments["separator"] = ";"
					    print json2csv(json2csvArguments)
					}
				"""
			)
			.build();

		final SourceTable sourceTableResult1 = SourceTable.builder().rawData(RAW_DATA_RESULT_1).build();
		final SourceTable sourceTableResult2 = SourceTable.builder().rawData(RAW_DATA_RESULT_2).build();
		final SourceTable sourceTableResult3 = SourceTable.builder().rawData(RAW_DATA_RESULT_3).build();

		final HttpSource httpSourceFoo = HttpSource
			.builder()
			.type("http")
			.method(HttpMethod.GET)
			.path("/ConfigurationManager/v1/objects/storages/FOO")
			.resultContent(ResultContent.BODY)
			.header("myVariableValue")
			.build();
		final HttpSource httpSourceBar = HttpSource
			.builder()
			.type("http")
			.method(HttpMethod.GET)
			.path("/ConfigurationManager/v1/objects/storages/BAR")
			.resultContent(ResultContent.BODY)
			.header("myVariableValue")
			.build();
		final HttpSource httpSourceBaz = HttpSource
			.builder()
			.type("http")
			.method(HttpMethod.GET)
			.path("/ConfigurationManager/v1/objects/storages/BAZ")
			.resultContent(ResultContent.BODY)
			.header("myVariableValue")
			.build();

		doReturn(telemetryManager).when(sourceProcessorMock).getTelemetryManager();

		when(sourceProcessorMock.process(any(HttpSource.class)))
			.thenReturn(sourceTableResult1, sourceTableResult2, sourceTableResult3);

		// Create an ArgumentCaptor for capturing HttpSource arguments
		final ArgumentCaptor<HttpSource> httpCaptor = ArgumentCaptor.forClass(HttpSource.class);

		assertEquals(
			Arrays.asList(
				Arrays.asList("/", DATA_RESULT_1),
				Arrays.asList("/", DATA_RESULT_2),
				Arrays.asList("/", DATA_RESULT_3)
			),
			jawkExtension.processSource(source, CONNECTOR_ID, telemetryManager, sourceProcessorMock).getTable()
		);

		// Verify and capture the arguments
		verify(sourceProcessorMock, times(3)).process(httpCaptor.capture());

		// Retrieve the captured arguments
		final List<HttpSource> capturedHttpSources = httpCaptor.getAllValues();

		assertNotNull(capturedHttpSources);
		assertEquals(3, capturedHttpSources.size());

		assertEquals(httpSourceFoo.getMethod(), capturedHttpSources.get(0).getMethod());
		assertEquals(httpSourceFoo.getPath(), capturedHttpSources.get(0).getPath());
		assertEquals(httpSourceFoo.getHeader(), capturedHttpSources.get(0).getHeader());
		assertEquals(httpSourceFoo.getResultContent(), capturedHttpSources.get(0).getResultContent());

		assertEquals(httpSourceBar.getMethod(), capturedHttpSources.get(1).getMethod());
		assertEquals(httpSourceBar.getPath(), capturedHttpSources.get(1).getPath());
		assertEquals(httpSourceBar.getHeader(), capturedHttpSources.get(1).getHeader());
		assertEquals(httpSourceBar.getResultContent(), capturedHttpSources.get(1).getResultContent());

		assertEquals(httpSourceBaz.getMethod(), capturedHttpSources.get(2).getMethod());
		assertEquals(httpSourceBaz.getPath(), capturedHttpSources.get(2).getPath());
		assertEquals(httpSourceBaz.getHeader(), capturedHttpSources.get(2).getHeader());
		assertEquals(httpSourceBaz.getResultContent(), capturedHttpSources.get(2).getResultContent());

		// WBEM Request
		((JawkSource) source).setScript(
				"""
					BEGIN {
					    FS=";"
					    OFS=";"
				    }

				    {
				    	requestArguments["query"] = "myQuery"
				    	requestArguments["namespace"] = "myNamespace"
					    print executeWbemRequest(requestArguments)
					}
				"""
			);
		((JawkSource) source).setInput("input test");
		doReturn(
			SourceTable.builder().rawData("result1;result2").table(SourceTable.csvToTable("result1;result2", ";")).build()
		)
			.when(sourceProcessorMock)
			.process(any(WbemSource.class));

		// Create an ArgumentCaptor for capturing HttpSource arguments
		final ArgumentCaptor<WbemSource> wbemCaptor = ArgumentCaptor.forClass(WbemSource.class);

		assertEquals(
			Collections.singletonList(Arrays.asList("result1", "result2")),
			jawkExtension.processSource(source, CONNECTOR_ID, telemetryManager, sourceProcessorMock).getTable()
		);

		// Verify and capture the arguments
		verify(sourceProcessorMock).process(wbemCaptor.capture());

		// Retrieve the captured arguments
		final List<WbemSource> capturedWbemSources = wbemCaptor.getAllValues();

		assertNotNull(capturedWbemSources);
		assertEquals(1, capturedWbemSources.size());

		assertEquals("myQuery", wbemCaptor.getValue().getQuery());
		assertEquals("myNamespace", wbemCaptor.getValue().getNamespace());

		// WMI Request
		((JawkSource) source).setScript(
				"""
					BEGIN {
					    FS=";"
					    OFS=";"
				    }

				    {
				    	requestArguments["query"] = "myQuery"
				    	requestArguments["namespace"] = "myNamespace"
					    print executeWmiRequest(requestArguments)
					}
				"""
			);
		doReturn(
			SourceTable.builder().rawData("result1;result2").table(SourceTable.csvToTable("result1;result2", ";")).build()
		)
			.when(sourceProcessorMock)
			.process(any(WmiSource.class));

		// Create an ArgumentCaptor for capturing HttpSource arguments
		final ArgumentCaptor<WmiSource> wmiCaptor = ArgumentCaptor.forClass(WmiSource.class);

		assertEquals(
			Collections.singletonList(Arrays.asList("result1", "result2")),
			jawkExtension.processSource(source, CONNECTOR_ID, telemetryManager, sourceProcessorMock).getTable()
		);

		// Verify and capture the arguments
		verify(sourceProcessorMock).process(wmiCaptor.capture());

		// Retrieve the captured arguments
		final List<WmiSource> capturedWmiSources = wmiCaptor.getAllValues();

		assertNotNull(capturedWmiSources);
		assertEquals(1, capturedWmiSources.size());

		assertEquals("myQuery", wmiCaptor.getValue().getQuery());
		assertEquals("myNamespace", wmiCaptor.getValue().getNamespace());

		// SNMP Get Request
		((JawkSource) source).setScript(
				"""
					BEGIN {
					    FS=";"
					    OFS=";"
				    }

				    {
				    	requestArguments["oid"] = "1.2.3.4.5.6"
					    print executeSnmpGet(requestArguments)
					}
				"""
			);

		doReturn(
			SourceTable.builder().rawData("result1;result2").table(SourceTable.csvToTable("result1;result2", ";")).build()
		)
			.when(sourceProcessorMock)
			.process(any(SnmpGetSource.class));

		// Create an ArgumentCaptor for capturing HttpSource arguments
		final ArgumentCaptor<SnmpGetSource> snmpGetCaptor = ArgumentCaptor.forClass(SnmpGetSource.class);

		assertEquals(
			Collections.singletonList(Arrays.asList("result1", "result2")),
			jawkExtension.processSource(source, CONNECTOR_ID, telemetryManager, sourceProcessorMock).getTable()
		);

		// Verify and capture the arguments
		verify(sourceProcessorMock).process(snmpGetCaptor.capture());

		// Retrieve the captured arguments
		final List<SnmpGetSource> capturedSnmpGetSources = snmpGetCaptor.getAllValues();

		assertNotNull(capturedSnmpGetSources);
		assertEquals(1, capturedSnmpGetSources.size());

		assertEquals("1.2.3.4.5.6", snmpGetCaptor.getValue().getOid());

		// SNMP Table Request
		((JawkSource) source).setScript(
				"""
					BEGIN {
					    FS=";"
					    OFS=";"
				    }

				    {
				    	requestArguments["oid"] = "1.2.3.4.5.6"
				    	requestArguments["selectColumns"] = "1"
					    print executeSnmpTable(requestArguments)
					}
				"""
			);
		doReturn(
			SourceTable.builder().rawData("result1;result2").table(SourceTable.csvToTable("result1;result2", ";")).build()
		)
			.when(sourceProcessorMock)
			.process(any(SnmpTableSource.class));

		// Create an ArgumentCaptor for capturing HttpSource arguments
		final ArgumentCaptor<SnmpTableSource> snmpTableCaptor = ArgumentCaptor.forClass(SnmpTableSource.class);

		assertEquals(
			Collections.singletonList(Arrays.asList("result1", "result2")),
			jawkExtension.processSource(source, CONNECTOR_ID, telemetryManager, sourceProcessorMock).getTable()
		);

		// Verify and capture the arguments
		verify(sourceProcessorMock).process(snmpTableCaptor.capture());

		// Retrieve the captured arguments
		final List<SnmpTableSource> capturedSnmpTableSources = snmpTableCaptor.getAllValues();

		assertNotNull(capturedSnmpTableSources);
		assertEquals(1, capturedSnmpTableSources.size());

		assertEquals("1.2.3.4.5.6", snmpTableCaptor.getValue().getOid());
		assertEquals("1", snmpTableCaptor.getValue().getSelectColumns());
	}

	@Test
	void testIsValidSource() {
		final JawkSourceExtension jawkExtension = new JawkSourceExtension();
		assertFalse(jawkExtension.isValidSource(new IpmiSource()));
		assertTrue(jawkExtension.isValidSource(new JawkSource()));
	}
}
