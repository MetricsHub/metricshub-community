package org.metricshub.engine.strategy.source;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.metricshub.engine.common.helpers.MetricsHubConstants.MONITOR_ATTRIBUTE_ID;
import static org.metricshub.engine.constants.Constants.EMPTY;
import static org.metricshub.engine.constants.Constants.ENCLOSURE_COLLECT_SOURCE_1;
import static org.metricshub.engine.constants.Constants.EXPECTED_RESULT;
import static org.metricshub.engine.constants.Constants.EXPECTED_SNMP_TABLE_DATA;
import static org.metricshub.engine.constants.Constants.EXPECTED_VAL_1;
import static org.metricshub.engine.constants.Constants.EXPECTED_VAL_1_AND_2;
import static org.metricshub.engine.constants.Constants.EXPECTED_VAL_1_AND_2_ARRAY;
import static org.metricshub.engine.constants.Constants.EXPECTED_VAL_2;
import static org.metricshub.engine.constants.Constants.LOCALHOST;
import static org.metricshub.engine.constants.Constants.MONITOR_ID_ATTRIBUTE_VALUE;
import static org.metricshub.engine.constants.Constants.MY_CONNECTOR_1_NAME;
import static org.metricshub.engine.constants.Constants.OID;
import static org.metricshub.engine.constants.Constants.SNMP_SELECTED_COLUMNS;
import static org.metricshub.engine.constants.Constants.SNMP_SELECTED_COLUMNS_LIST;
import static org.metricshub.engine.constants.Constants.TAB1_REF;
import static org.metricshub.engine.constants.Constants.URL;
import static org.metricshub.engine.constants.Constants.VALUE_A1;
import static org.metricshub.engine.constants.Constants.VALUE_B1;
import static org.metricshub.engine.constants.Constants.VALUE_C1;
import static org.metricshub.engine.constants.Constants.VALUE_VAL1;
import static org.metricshub.engine.constants.Constants.VALUE_VAL2;
import static org.metricshub.engine.constants.Constants.VALUE_VAL3;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.metricshub.engine.common.helpers.MetricsHubConstants;
import org.metricshub.engine.configuration.HostConfiguration;
import org.metricshub.engine.connector.model.common.CustomConcatMethod;
import org.metricshub.engine.connector.model.common.DeviceKind;
import org.metricshub.engine.connector.model.common.EntryConcatMethod;
import org.metricshub.engine.connector.model.common.ExecuteForEachEntryOf;
import org.metricshub.engine.connector.model.common.HttpMethod;
import org.metricshub.engine.connector.model.monitor.task.source.CommandLineSource;
import org.metricshub.engine.connector.model.monitor.task.source.CopySource;
import org.metricshub.engine.connector.model.monitor.task.source.HttpSource;
import org.metricshub.engine.connector.model.monitor.task.source.IpmiSource;
import org.metricshub.engine.connector.model.monitor.task.source.JmxSource;
import org.metricshub.engine.connector.model.monitor.task.source.SnmpGetSource;
import org.metricshub.engine.connector.model.monitor.task.source.SnmpTableSource;
import org.metricshub.engine.connector.model.monitor.task.source.StaticSource;
import org.metricshub.engine.connector.model.monitor.task.source.TableJoinSource;
import org.metricshub.engine.connector.model.monitor.task.source.TableUnionSource;
import org.metricshub.engine.connector.model.monitor.task.source.WbemSource;
import org.metricshub.engine.connector.model.monitor.task.source.WmiSource;
import org.metricshub.engine.extension.TestConfiguration;
import org.metricshub.engine.telemetry.HostProperties;
import org.metricshub.engine.telemetry.TelemetryManager;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SourceUpdaterProcessorTest {

	@Mock
	private ISourceProcessor sourceProcessor;

	@Test
	void testProcessHttpPSource() {
		final TestConfiguration httpConfiguration = TestConfiguration.builder().build();
		final HostConfiguration hostConfiguration = HostConfiguration
			.builder()
			.hostname(LOCALHOST)
			.hostId(LOCALHOST)
			.hostType(DeviceKind.LINUX)
			.configurations(Collections.singletonMap(TestConfiguration.class, httpConfiguration))
			.build();
		final TelemetryManager telemetryManager = TelemetryManager.builder().hostConfiguration(hostConfiguration).build();

		doReturn(SourceTable.empty()).when(sourceProcessor).process(any(HttpSource.class));
		assertEquals(
			SourceTable.empty(),
			new SourceUpdaterProcessor(
				sourceProcessor,
				telemetryManager,
				MY_CONNECTOR_1_NAME,
				Map.of(MONITOR_ATTRIBUTE_ID, MONITOR_ID_ATTRIBUTE_VALUE)
			)
				.process(HttpSource.builder().url(URL).method(HttpMethod.GET).build())
		);

		final HttpSource httpSource = HttpSource.builder().url(URL).build();
		httpSource.setExecuteForEachEntryOf(ExecuteForEachEntryOf.builder().source(ENCLOSURE_COLLECT_SOURCE_1).build());

		final SourceTable sourceTable = SourceTable
			.builder()
			.table(
				Arrays.asList(Arrays.asList(VALUE_VAL1, VALUE_VAL2, VALUE_VAL3), Arrays.asList(VALUE_A1, VALUE_B1, VALUE_C1))
			)
			.build();

		final HostProperties hostProperties = HostProperties.builder().build();

		hostProperties.getConnectorNamespace(MY_CONNECTOR_1_NAME).addSourceTable(ENCLOSURE_COLLECT_SOURCE_1, sourceTable);

		telemetryManager.setHostProperties(hostProperties);

		final SourceTable expected1 = SourceTable.builder().rawData(EXPECTED_VAL_1).build();
		final SourceTable expected2 = SourceTable.builder().rawData(EXPECTED_VAL_2).build();
		doReturn(expected1, expected2).when(sourceProcessor).process(any(HttpSource.class));
		SourceTable result = new SourceUpdaterProcessor(
			sourceProcessor,
			telemetryManager,
			MY_CONNECTOR_1_NAME,
			Map.of(MONITOR_ATTRIBUTE_ID, MONITOR_ID_ATTRIBUTE_VALUE)
		)
			.process(httpSource);
		assertEquals(EXPECTED_VAL_1_AND_2, result.getRawData());

		httpSource.setExecuteForEachEntryOf(
			ExecuteForEachEntryOf.builder().source(ENCLOSURE_COLLECT_SOURCE_1).concatMethod(EntryConcatMethod.LIST).build()
		);
		doReturn(expected1, expected2).when(sourceProcessor).process(any(HttpSource.class));
		result =
			new SourceUpdaterProcessor(
				sourceProcessor,
				telemetryManager,
				MY_CONNECTOR_1_NAME,
				Map.of(MONITOR_ATTRIBUTE_ID, MONITOR_ID_ATTRIBUTE_VALUE)
			)
				.process(httpSource);
		assertEquals(EXPECTED_VAL_1_AND_2, result.getRawData());

		httpSource.setExecuteForEachEntryOf(
			ExecuteForEachEntryOf
				.builder()
				.source(ENCLOSURE_COLLECT_SOURCE_1)
				.concatMethod(EntryConcatMethod.JSON_ARRAY)
				.build()
		);
		doReturn(expected1, expected2).when(sourceProcessor).process(any(HttpSource.class));
		result =
			new SourceUpdaterProcessor(
				sourceProcessor,
				telemetryManager,
				MY_CONNECTOR_1_NAME,
				Map.of(MONITOR_ATTRIBUTE_ID, MONITOR_ID_ATTRIBUTE_VALUE)
			)
				.process(httpSource);
		assertEquals(EXPECTED_VAL_1_AND_2_ARRAY, result.getRawData());

		httpSource.setExecuteForEachEntryOf(
			ExecuteForEachEntryOf
				.builder()
				.source(ENCLOSURE_COLLECT_SOURCE_1)
				.concatMethod(EntryConcatMethod.JSON_ARRAY_EXTENDED)
				.build()
		);
		doReturn(expected1, expected2).when(sourceProcessor).process(any(HttpSource.class));
		result =
			new SourceUpdaterProcessor(
				sourceProcessor,
				telemetryManager,
				MY_CONNECTOR_1_NAME,
				Map.of(MONITOR_ATTRIBUTE_ID, MONITOR_ID_ATTRIBUTE_VALUE)
			)
				.process(httpSource);

		assertEquals(EXPECTED_RESULT, result.getRawData());
	}

	@Test
	void testProcessHttpSourceCustomExecuteForEachEntry() {
		final TestConfiguration httpConfiguration = TestConfiguration.builder().build();
		final HostConfiguration hostConfiguration = HostConfiguration
			.builder()
			.hostname(LOCALHOST)
			.hostId(LOCALHOST)
			.hostType(DeviceKind.LINUX)
			.configurations(Collections.singletonMap(TestConfiguration.class, httpConfiguration))
			.build();
		final TelemetryManager telemetryManager = TelemetryManager.builder().hostConfiguration(hostConfiguration).build();

		final HttpSource httpSource = HttpSource.builder().url(URL).build();
		final CustomConcatMethod customConcatMethod = CustomConcatMethod
			.builder()
			.concatStart("concatStart:{")
			.concatEnd("}concatEnd;")
			.build();
		httpSource.setExecuteForEachEntryOf(
			ExecuteForEachEntryOf.builder().source(ENCLOSURE_COLLECT_SOURCE_1).concatMethod(customConcatMethod).build()
		);

		final SourceTable sourceTable = SourceTable
			.builder()
			.table(
				Arrays.asList(Arrays.asList(VALUE_VAL1, VALUE_VAL2, VALUE_VAL3), Arrays.asList(VALUE_A1, VALUE_B1, VALUE_C1))
			)
			.build();

		final HostProperties hostProperties = HostProperties.builder().build();

		hostProperties.getConnectorNamespace(MY_CONNECTOR_1_NAME).addSourceTable(ENCLOSURE_COLLECT_SOURCE_1, sourceTable);

		telemetryManager.setHostProperties(hostProperties);

		final SourceTable expected1 = SourceTable.builder().rawData(EXPECTED_VAL_1).build();
		final SourceTable expected2 = SourceTable.builder().rawData(EXPECTED_VAL_2).build();
		doReturn(expected1, expected2).when(sourceProcessor).process(any(HttpSource.class));

		SourceTable result = new SourceUpdaterProcessor(
			sourceProcessor,
			telemetryManager,
			MY_CONNECTOR_1_NAME,
			Map.of(MONITOR_ATTRIBUTE_ID, MONITOR_ID_ATTRIBUTE_VALUE)
		)
			.process(httpSource);
		final String expectedResult = "concatStart:{expectedVal1}concatEnd;concatStart:{expectedVal2}concatEnd;";
		assertEquals(expectedResult, result.getRawData());
	}

	@Test
	void testProcessHttpSourceExecuteForEachEntrySleep() {
		final HttpSource httpSource = HttpSource.builder().url(URL).build();
		assertNull(httpSource.getSleepExecuteForEachEntryOf());

		httpSource.setExecuteForEachEntryOf(
			ExecuteForEachEntryOf.builder().source(ENCLOSURE_COLLECT_SOURCE_1).concatMethod(EntryConcatMethod.LIST).build()
		);

		assertEquals(null, httpSource.getSleepExecuteForEachEntryOf());

		httpSource.setExecuteForEachEntryOf(
			ExecuteForEachEntryOf
				.builder()
				.source(ENCLOSURE_COLLECT_SOURCE_1)
				.concatMethod(EntryConcatMethod.LIST)
				.sleep(200)
				.build()
		);

		assertEquals(200, httpSource.getSleepExecuteForEachEntryOf());

		final CustomConcatMethod customConcatMethod = CustomConcatMethod
			.builder()
			.concatStart("concatStart:{")
			.concatEnd("}concatEnd;")
			.build();

		httpSource.setExecuteForEachEntryOf(
			ExecuteForEachEntryOf
				.builder()
				.source(ENCLOSURE_COLLECT_SOURCE_1)
				.concatMethod(customConcatMethod)
				.sleep(400)
				.build()
		);
		assertEquals(400, httpSource.getSleepExecuteForEachEntryOf());
	}

	@Test
	void testProcessSNMPGetSource() {
		final TestConfiguration snmpConfiguration = TestConfiguration.builder().build();
		final HostConfiguration hostConfiguration = HostConfiguration
			.builder()
			.hostname(LOCALHOST)
			.hostId(LOCALHOST)
			.hostType(DeviceKind.LINUX)
			.configurations(Collections.singletonMap(TestConfiguration.class, snmpConfiguration))
			.build();
		final TelemetryManager telemetryManager = TelemetryManager.builder().hostConfiguration(hostConfiguration).build();

		doReturn(SourceTable.empty()).when(sourceProcessor).process(any(SnmpGetSource.class));
		assertEquals(
			SourceTable.empty(),
			new SourceUpdaterProcessor(
				sourceProcessor,
				telemetryManager,
				MY_CONNECTOR_1_NAME,
				Map.of(MONITOR_ATTRIBUTE_ID, MONITOR_ID_ATTRIBUTE_VALUE)
			)
				.process(SnmpGetSource.builder().oid(OID).build())
		);

		final SourceTable expected1 = SourceTable.builder().rawData(EXPECTED_VAL_1).build();

		doReturn(expected1).when(sourceProcessor).process(any(SnmpGetSource.class));
		assertEquals(
			expected1,
			new SourceUpdaterProcessor(
				sourceProcessor,
				telemetryManager,
				MY_CONNECTOR_1_NAME,
				Map.of(MONITOR_ATTRIBUTE_ID, MONITOR_ID_ATTRIBUTE_VALUE)
			)
				.process(SnmpGetSource.builder().oid(OID).build())
		);
	}

	@Test
	void testProcessSNMPGetTableSource() {
		final TestConfiguration snmpConfiguration = TestConfiguration.builder().build();
		final HostConfiguration hostConfiguration = HostConfiguration
			.builder()
			.hostname(LOCALHOST)
			.hostId(LOCALHOST)
			.hostType(DeviceKind.LINUX)
			.configurations(Collections.singletonMap(TestConfiguration.class, snmpConfiguration))
			.build();
		final TelemetryManager telemetryManager = TelemetryManager.builder().hostConfiguration(hostConfiguration).build();
		doReturn(SourceTable.empty()).when(sourceProcessor).process(any(SnmpTableSource.class));
		assertEquals(
			SourceTable.empty(),
			new SourceUpdaterProcessor(
				sourceProcessor,
				telemetryManager,
				MY_CONNECTOR_1_NAME,
				Map.of(MONITOR_ATTRIBUTE_ID, MONITOR_ID_ATTRIBUTE_VALUE)
			)
				.process(SnmpTableSource.builder().oid(OID).selectColumns(SNMP_SELECTED_COLUMNS).build())
		);

		SourceTable expected = SourceTable
			.builder()
			.table(EXPECTED_SNMP_TABLE_DATA)
			.headers(SNMP_SELECTED_COLUMNS_LIST)
			.build();
		doReturn(expected).when(sourceProcessor).process(any(SnmpTableSource.class));
		assertEquals(
			expected,
			new SourceUpdaterProcessor(
				sourceProcessor,
				telemetryManager,
				MY_CONNECTOR_1_NAME,
				Map.of(MONITOR_ATTRIBUTE_ID, MONITOR_ID_ATTRIBUTE_VALUE)
			)
				.process(SnmpTableSource.builder().oid(OID).selectColumns(SNMP_SELECTED_COLUMNS).build())
		);
	}

	@Test
	void testProcessTableJoinSource() {
		final TestConfiguration snmpConfiguration = TestConfiguration.builder().build();
		final HostConfiguration hostConfiguration = HostConfiguration
			.builder()
			.hostname(LOCALHOST)
			.hostId(LOCALHOST)
			.hostType(DeviceKind.LINUX)
			.configurations(Collections.singletonMap(TestConfiguration.class, snmpConfiguration))
			.build();
		final TelemetryManager telemetryManager = TelemetryManager.builder().hostConfiguration(hostConfiguration).build();
		doReturn(SourceTable.empty()).when(sourceProcessor).process(any(TableJoinSource.class));
		assertEquals(
			SourceTable.empty(),
			new SourceUpdaterProcessor(
				sourceProcessor,
				telemetryManager,
				MY_CONNECTOR_1_NAME,
				Map.of(MONITOR_ATTRIBUTE_ID, MONITOR_ID_ATTRIBUTE_VALUE)
			)
				.process(TableJoinSource.builder().build())
		);
	}

	@Test
	void testProcessTableUnionSource() {
		final TestConfiguration snmpConfiguration = TestConfiguration.builder().build();
		final HostConfiguration hostConfiguration = HostConfiguration
			.builder()
			.hostname(LOCALHOST)
			.hostId(LOCALHOST)
			.hostType(DeviceKind.LINUX)
			.configurations(Collections.singletonMap(TestConfiguration.class, snmpConfiguration))
			.build();
		final TelemetryManager telemetryManager = TelemetryManager.builder().hostConfiguration(hostConfiguration).build();
		doReturn(SourceTable.empty()).when(sourceProcessor).process(any(TableUnionSource.class));
		assertEquals(
			SourceTable.empty(),
			new SourceUpdaterProcessor(
				sourceProcessor,
				telemetryManager,
				MY_CONNECTOR_1_NAME,
				Map.of(MONITOR_ATTRIBUTE_ID, MONITOR_ID_ATTRIBUTE_VALUE)
			)
				.process(TableUnionSource.builder().tables(new ArrayList<>()).build())
		);
	}

	@Test
	void testProcessCopySource() {
		final TestConfiguration snmpConfiguration = TestConfiguration.builder().build();
		final HostConfiguration hostConfiguration = HostConfiguration
			.builder()
			.hostname(LOCALHOST)
			.hostId(LOCALHOST)
			.hostType(DeviceKind.LINUX)
			.configurations(Collections.singletonMap(TestConfiguration.class, snmpConfiguration))
			.build();
		final TelemetryManager telemetryManager = TelemetryManager.builder().hostConfiguration(hostConfiguration).build();
		doReturn(SourceTable.empty()).when(sourceProcessor).process(any(CopySource.class));
		assertEquals(
			SourceTable.empty(),
			new SourceUpdaterProcessor(
				sourceProcessor,
				telemetryManager,
				MY_CONNECTOR_1_NAME,
				Map.of(MONITOR_ATTRIBUTE_ID, MONITOR_ID_ATTRIBUTE_VALUE)
			)
				.process(CopySource.builder().from(TAB1_REF).build())
		);

		CopySource copySource = CopySource.builder().from(TAB1_REF).build();
		final List<List<String>> result = Collections.singletonList(Collections.singletonList(VALUE_VAL1));
		final SourceTable expected = SourceTable.builder().table(result).build();

		final SnmpGetSource snmpGetSource = SnmpGetSource.builder().oid(OID).build();

		final List<List<String>> resultSnmp = Collections.singletonList(Collections.singletonList(VALUE_VAL1));
		final SourceTable expectedSnmp = SourceTable.builder().table(resultSnmp).build();
		doReturn(expectedSnmp).when(sourceProcessor).process(any(SnmpGetSource.class));
		assertEquals(
			expectedSnmp,
			new SourceUpdaterProcessor(
				sourceProcessor,
				telemetryManager,
				MY_CONNECTOR_1_NAME,
				Map.of(MONITOR_ATTRIBUTE_ID, MONITOR_ID_ATTRIBUTE_VALUE)
			)
				.process(snmpGetSource)
		);

		doReturn(expected).when(sourceProcessor).process(any(CopySource.class));
		assertEquals(
			expected,
			new SourceUpdaterProcessor(
				sourceProcessor,
				telemetryManager,
				MY_CONNECTOR_1_NAME,
				Map.of(MONITOR_ATTRIBUTE_ID, MONITOR_ID_ATTRIBUTE_VALUE)
			)
				.process(copySource)
		);
	}

	@Test
	void testProcessStaticSource() {
		final TestConfiguration snmpConfiguration = TestConfiguration.builder().build();
		final HostConfiguration hostConfiguration = HostConfiguration
			.builder()
			.hostname(LOCALHOST)
			.hostId(LOCALHOST)
			.hostType(DeviceKind.LINUX)
			.configurations(Collections.singletonMap(TestConfiguration.class, snmpConfiguration))
			.build();
		final TelemetryManager telemetryManager = TelemetryManager.builder().hostConfiguration(hostConfiguration).build();

		StaticSource staticSource = StaticSource.builder().value(VALUE_VAL1).build();
		final List<List<String>> resultTable = Collections.singletonList(Collections.singletonList(VALUE_VAL1));
		final SourceTable expected = SourceTable.builder().table(resultTable).build();
		doReturn(expected).when(sourceProcessor).process(any(StaticSource.class));
		assertEquals(
			expected,
			new SourceUpdaterProcessor(
				sourceProcessor,
				telemetryManager,
				MY_CONNECTOR_1_NAME,
				Map.of(MONITOR_ATTRIBUTE_ID, MONITOR_ID_ATTRIBUTE_VALUE)
			)
				.process(staticSource)
		);
	}

	@Test
	void testReplaceAttributeReferences() {
		String command = "show ${attribute::id}";
		assertEquals("show disk1", SourceUpdaterProcessor.replaceAttributeReferences(command, Map.of("id", "disk1")));
		assertEquals(command, SourceUpdaterProcessor.replaceAttributeReferences(command, Map.of()));
		assertEquals(command, SourceUpdaterProcessor.replaceAttributeReferences(command, null));
		assertNull(SourceUpdaterProcessor.replaceAttributeReferences(null, Map.of()));
		command = "show ${attribute::type} ${attribute::id}";
		assertEquals(
			"show disk 1",
			SourceUpdaterProcessor.replaceAttributeReferences(command, Map.of("id", "1", "type", "disk"))
		);
	}

	@Test
	void testProcessWbemSource() {
		final TelemetryManager telemetryManager = TelemetryManager
			.builder()
			.hostConfiguration(HostConfiguration.builder().build())
			.build();
		doReturn(SourceTable.empty()).when(sourceProcessor).process(any(WbemSource.class));
		assertEquals(
			SourceTable.empty(),
			new SourceUpdaterProcessor(
				sourceProcessor,
				telemetryManager,
				MY_CONNECTOR_1_NAME,
				Map.of(MONITOR_ATTRIBUTE_ID, MONITOR_ID_ATTRIBUTE_VALUE)
			)
				.process(WbemSource.builder().query(EMPTY).build())
		);
	}

	@Test
	void testProcessWmiSource() {
		doReturn(SourceTable.empty()).when(sourceProcessor).process(any(WmiSource.class));
		assertEquals(
			SourceTable.empty(),
			new SourceUpdaterProcessor(
				sourceProcessor,
				TelemetryManager.builder().build(),
				MY_CONNECTOR_1_NAME,
				Map.of(MONITOR_ATTRIBUTE_ID, MONITOR_ID_ATTRIBUTE_VALUE)
			)
				.process(WmiSource.builder().query(EMPTY).build())
		);
	}

	@Test
	void testProcessCommandLineSource() {
		doReturn(SourceTable.empty()).when(sourceProcessor).process(any(CommandLineSource.class));

		assertEquals(
			SourceTable.empty(),
			new SourceUpdaterProcessor(
				sourceProcessor,
				TelemetryManager.builder().build(),
				MY_CONNECTOR_1_NAME,
				Map.of(MONITOR_ATTRIBUTE_ID, MONITOR_ID_ATTRIBUTE_VALUE)
			)
				.process(
					CommandLineSource
						.builder()
						.commandLine("/usr/sbin/pvdisplay /dev/dsk/%PhysicalDisk.Collect.DeviceID%")
						.build()
				)
		);
	}

	@Test
	void testProcessIpmiSource() {
		doReturn(SourceTable.empty()).when(sourceProcessor).process(any(IpmiSource.class));
		assertEquals(
			SourceTable.empty(),
			new SourceUpdaterProcessor(
				sourceProcessor,
				TelemetryManager.builder().build(),
				MY_CONNECTOR_1_NAME,
				Map.of(MONITOR_ATTRIBUTE_ID, MONITOR_ID_ATTRIBUTE_VALUE)
			)
				.process(IpmiSource.builder().build())
		);
	}

	@Test
	void testReplaceSourceReferenceContent() {
		final TelemetryManager telemetryManager = new TelemetryManager();
		telemetryManager.setHostConfiguration(HostConfiguration.builder().hostname("hostname").build());

		final String vendorSource1Ref = "${source::monitors.cpu.discovery.sources.vendor}";

		{
			final String value = String.format("%s value", vendorSource1Ref);
			telemetryManager
				.getHostProperties()
				.getConnectorNamespace(MY_CONNECTOR_1_NAME)
				.addSourceTable(
					vendorSource1Ref,
					SourceTable.builder().table(SourceTable.csvToTable("vendor", MetricsHubConstants.TABLE_SEP)).build()
				);

			final String result = SourceUpdaterProcessor.replaceSourceReferenceContent(
				value,
				telemetryManager,
				MY_CONNECTOR_1_NAME,
				"source",
				"object"
			);
			assertEquals("vendor value", result);
		}

		{
			final String value = String.format("%s %s value", vendorSource1Ref, vendorSource1Ref);
			telemetryManager
				.getHostProperties()
				.getConnectorNamespace(MY_CONNECTOR_1_NAME)
				.addSourceTable(
					vendorSource1Ref,
					SourceTable.builder().table(SourceTable.csvToTable("vendor", MetricsHubConstants.TABLE_SEP)).build()
				);

			final String result = SourceUpdaterProcessor.replaceSourceReferenceContent(
				value,
				telemetryManager,
				MY_CONNECTOR_1_NAME,
				"source",
				"object"
			);
			assertEquals("vendor vendor value", result);
		}
		{
			final String value = String.format("%s%s value", vendorSource1Ref, vendorSource1Ref);
			telemetryManager
				.getHostProperties()
				.getConnectorNamespace(MY_CONNECTOR_1_NAME)
				.addSourceTable(
					vendorSource1Ref,
					SourceTable.builder().table(SourceTable.csvToTable("vendor", MetricsHubConstants.TABLE_SEP)).build()
				);

			final String result = SourceUpdaterProcessor.replaceSourceReferenceContent(
				value,
				telemetryManager,
				MY_CONNECTOR_1_NAME,
				"source",
				"object"
			);
			assertEquals("vendorvendor value", result);
		}
	}

	@Test
	void testProcessJmxSource() {
		final JmxSource source = JmxSource
			.builder()
			.type("jmx")
			.objectName("org.metricshub.extension.jmx:type=JmxMBean,scope=*")
			.attributes(new LinkedList<>(List.of("Name")))
			.key("${source::beforeAll.jmxSource}")
			.forceSerialization(false)
			.keyProperties(new LinkedList<>(List.of("scope")))
			.build();

		final SourceTable sourceTable = SourceTable.builder().table(List.of(List.of("12345"))).build();
		doReturn(sourceTable).when(sourceProcessor).process(any(JmxSource.class));
		assertEquals(
			sourceTable,
			new SourceUpdaterProcessor(
				sourceProcessor,
				TelemetryManager.builder().build(),
				MY_CONNECTOR_1_NAME,
				Map.of(MONITOR_ATTRIBUTE_ID, MONITOR_ID_ATTRIBUTE_VALUE)
			)
				.process(source),
			"Processing JMX source did not return expected result."
		);
	}
}
