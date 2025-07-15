package org.metricshub.engine.strategy.source;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.metricshub.engine.constants.Constants.AUTOMATIC;
import static org.metricshub.engine.constants.Constants.ECS1_01;
import static org.metricshub.engine.constants.Constants.EXPECTED_SNMP_TABLE_DATA;
import static org.metricshub.engine.constants.Constants.MY_CONNECTOR_1_NAME;
import static org.metricshub.engine.constants.Constants.OID;
import static org.metricshub.engine.constants.Constants.SNMP_SELECTED_COLUMNS;
import static org.metricshub.engine.constants.Constants.SNMP_SELECTED_COLUMNS_LIST;
import static org.metricshub.engine.constants.Constants.TAB1_REF;
import static org.metricshub.engine.constants.Constants.URL;
import static org.metricshub.engine.constants.Constants.VALUE_VAL1;
import static org.metricshub.engine.constants.Constants.VALUE_VAL2;
import static org.metricshub.engine.constants.Constants.VALUE_VAL3;
import static org.metricshub.engine.constants.Constants.WBEM_QUERY;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.metricshub.engine.client.ClientsExecutor;
import org.metricshub.engine.common.exception.ClientException;
import org.metricshub.engine.configuration.HostConfiguration;
import org.metricshub.engine.connector.model.common.DeviceKind;
import org.metricshub.engine.connector.model.common.HttpMethod;
import org.metricshub.engine.connector.model.common.SqlColumn;
import org.metricshub.engine.connector.model.common.SqlTable;
import org.metricshub.engine.connector.model.monitor.task.source.CopySource;
import org.metricshub.engine.connector.model.monitor.task.source.HttpSource;
import org.metricshub.engine.connector.model.monitor.task.source.InternalDbQuerySource;
import org.metricshub.engine.connector.model.monitor.task.source.IpmiSource;
import org.metricshub.engine.connector.model.monitor.task.source.JmxSource;
import org.metricshub.engine.connector.model.monitor.task.source.SnmpGetSource;
import org.metricshub.engine.connector.model.monitor.task.source.SnmpTableSource;
import org.metricshub.engine.connector.model.monitor.task.source.StaticSource;
import org.metricshub.engine.connector.model.monitor.task.source.TableJoinSource;
import org.metricshub.engine.connector.model.monitor.task.source.TableUnionSource;
import org.metricshub.engine.connector.model.monitor.task.source.WmiSource;
import org.metricshub.engine.extension.ExtensionManager;
import org.metricshub.engine.extension.IProtocolExtension;
import org.metricshub.engine.extension.ISourceComputationExtension;
import org.metricshub.engine.extension.TestConfiguration;
import org.metricshub.engine.telemetry.ConnectorNamespace;
import org.metricshub.engine.telemetry.HostProperties;
import org.metricshub.engine.telemetry.TelemetryManager;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SourceProcessorTest {

	@Mock
	private ClientsExecutor clientsExecutorMock;

	@Mock
	private IProtocolExtension protocolExtensionMock;

	@Mock
	private ISourceComputationExtension sourceComputationExtensionMock;

	private static final String LOWERCASE_A = "a";
	private static final String LOWERCASE_B = "b";
	private static final String LOWERCASE_C = "c";
	private static final String LOWERCASE_D = "d";
	private static final String LOWERCASE_A1 = "a1";
	private static final String LOWERCASE_B1 = "b1";
	private static final String LOWERCASE_C1 = "c1";
	private static final String LOWERCASE_B2 = "b2";
	private static final String LOWERCASE_C2 = "c2";
	private static final String UPPERCASE_B2 = "B2";
	private static final String UPPERCASE_C2 = "C2";
	private static final String LOWERCASE_T = "t";
	private static final String LOWERCASE_U = "u";
	private static final String LOWERCASE_V = "v";
	private static final String CAMELCASE_VAL1 = "VaL1";
	private static final String LOWERCASE_V1 = "v1";
	private static final String LOWERCASE_V2 = "v2";
	private static final String LOWERCASE_V3 = "v3";
	private static final String LOWERCASE_V4 = "v4";
	private static final String UPPERCASE_V1 = "V1";
	private static final String UPPERCASE_V2 = "V2";
	private static final String UPPERCASE_V3 = "V3";
	private static final String UPPERCASE_V4 = "V4";
	private static final String LOWERCASE_V10 = "v10";
	private static final String LOWERCASE_V20 = "v20";
	private static final String LOWERCASE_V30 = "v30";
	private static final String LOWERCASE_X = "x";
	private static final String LOWERCASE_Y = "y";
	private static final String LOWERCASE_Z = "z";
	private static final String TAB2_REF = "${source::monitors.cpu.discovery.sources.tab2}";
	private static final String TAB3_REF = "${source::monitors.cpu.discovery.sources.tab3}";
	private static final String VALUE_LIST = "a1;b1;c1";
	private static final String ONE = "1";
	private static final String TWO = "2";
	private static final String THREE = "3";
	private static final String FOUR = "4";
	private static final String TRUE = "TRUE";
	private static final String FALSE = "FALSE";

	private static final String CAMELCASE_NOT_WBEM = "notWbem";
	private static final String CONNECTOR_ID = "myConnector";

	@Test
	void testProcessHttpSource() {
		final TestConfiguration httpConfiguration = TestConfiguration.builder().build();
		final HostConfiguration hostConfiguration = HostConfiguration
			.builder()
			.hostname(ECS1_01)
			.hostId(ECS1_01)
			.hostType(DeviceKind.LINUX)
			.configurations(Collections.singletonMap(TestConfiguration.class, httpConfiguration))
			.build();

		final TelemetryManager telemetryManager = TelemetryManager.builder().hostConfiguration(hostConfiguration).build();

		final ExtensionManager extensionManager = ExtensionManager
			.builder()
			.withProtocolExtensions(List.of(protocolExtensionMock))
			.build();

		final SourceProcessor sourceProcessor = SourceProcessor
			.builder()
			.telemetryManager(telemetryManager)
			.clientsExecutor(clientsExecutorMock)
			.extensionManager(extensionManager)
			.connectorId(CONNECTOR_ID)
			.build();

		doReturn(true).when(protocolExtensionMock).isValidConfiguration(httpConfiguration);

		doReturn(Set.of(HttpSource.class)).when(protocolExtensionMock).getSupportedSources();

		final SourceTable expected = SourceTable.builder().rawData(ECS1_01).build();

		final HttpSource source = HttpSource.builder().url(URL).method(HttpMethod.GET).build();

		doReturn(expected).when(protocolExtensionMock).processSource(eq(source), anyString(), any(TelemetryManager.class));

		final SourceTable actual = sourceProcessor.process(source);

		assertEquals(expected, actual);
	}

	@Test
	void testProcessWmiSource() {
		final TestConfiguration wmiConfiguration = TestConfiguration.builder().build();
		final HostConfiguration hostConfiguration = HostConfiguration
			.builder()
			.hostname(ECS1_01)
			.hostId(ECS1_01)
			.hostType(DeviceKind.WINDOWS)
			.configurations(Collections.singletonMap(TestConfiguration.class, wmiConfiguration))
			.build();

		final TelemetryManager telemetryManager = TelemetryManager.builder().hostConfiguration(hostConfiguration).build();

		final ExtensionManager extensionManager = ExtensionManager
			.builder()
			.withProtocolExtensions(List.of(protocolExtensionMock))
			.build();

		final SourceProcessor sourceProcessor = SourceProcessor
			.builder()
			.telemetryManager(telemetryManager)
			.clientsExecutor(clientsExecutorMock)
			.extensionManager(extensionManager)
			.connectorId(CONNECTOR_ID)
			.build();

		doReturn(true).when(protocolExtensionMock).isValidConfiguration(wmiConfiguration);

		doReturn(Set.of(WmiSource.class)).when(protocolExtensionMock).getSupportedSources();

		final SourceTable expected = SourceTable.builder().rawData(ECS1_01).build();

		final WmiSource wmiSource = WmiSource.builder().query(WBEM_QUERY).namespace(AUTOMATIC).build();

		doReturn(expected)
			.when(protocolExtensionMock)
			.processSource(eq(wmiSource), anyString(), any(TelemetryManager.class));

		final SourceTable actual = sourceProcessor.process(wmiSource);

		assertEquals(expected, actual);
	}

	@Test
	void testProcessSnmpGetSource() throws Exception {
		final TelemetryManager telemetryManager = TelemetryManager
			.builder()
			.hostConfiguration(HostConfiguration.builder().build())
			.build();
		final ExtensionManager extensionManager = ExtensionManager
			.builder()
			.withProtocolExtensions(List.of(protocolExtensionMock))
			.build();

		final SourceProcessor sourceProcessor = SourceProcessor
			.builder()
			.telemetryManager(telemetryManager)
			.clientsExecutor(clientsExecutorMock)
			.extensionManager(extensionManager)
			.connectorId(CONNECTOR_ID)
			.build();

		// no snmp protocol
		final HostConfiguration hostConfigurationNoProtocol = HostConfiguration
			.builder()
			.hostname(ECS1_01)
			.hostId(ECS1_01)
			.hostType(DeviceKind.LINUX)
			.build();
		telemetryManager.setHostConfiguration(hostConfigurationNoProtocol);

		// classic case
		final TestConfiguration snmpConfiguration = TestConfiguration.builder().build();
		final HostConfiguration hostConfiguration = HostConfiguration
			.builder()
			.hostname(ECS1_01)
			.hostId(ECS1_01)
			.hostType(DeviceKind.LINUX)
			.configurations(Collections.singletonMap(TestConfiguration.class, snmpConfiguration))
			.build();
		telemetryManager.setHostConfiguration(hostConfiguration);
		final SnmpGetSource snmpGetSource = SnmpGetSource.builder().oid(OID).build();

		doReturn(true).when(protocolExtensionMock).isValidConfiguration(snmpConfiguration);
		doReturn(Set.of(SnmpGetSource.class, SnmpTableSource.class)).when(protocolExtensionMock).getSupportedSources();

		final SourceTable expected = SourceTable.builder().table(Arrays.asList(Arrays.asList(ECS1_01))).build();
		doReturn(expected)
			.when(protocolExtensionMock)
			.processSource(eq(snmpGetSource), anyString(), any(TelemetryManager.class));

		final SourceTable actual = sourceProcessor.process(snmpGetSource);
		assertEquals(expected, actual);
	}

	@Test
	void testProcessSnmpGetTable() throws Exception {
		final TelemetryManager telemetryManager = TelemetryManager
			.builder()
			.hostConfiguration(HostConfiguration.builder().build())
			.build();

		final ExtensionManager extensionManager = ExtensionManager
			.builder()
			.withProtocolExtensions(List.of(protocolExtensionMock))
			.build();

		final SourceProcessor sourceProcessor = SourceProcessor
			.builder()
			.telemetryManager(telemetryManager)
			.clientsExecutor(clientsExecutorMock)
			.extensionManager(extensionManager)
			.connectorId(CONNECTOR_ID)
			.build();

		// no snmp protocol
		final HostConfiguration hostConfigurationNoProtocol = HostConfiguration
			.builder()
			.hostname(ECS1_01)
			.hostId(ECS1_01)
			.hostType(DeviceKind.LINUX)
			.build();
		telemetryManager.setHostConfiguration(hostConfigurationNoProtocol);
		assertEquals(SourceTable.empty(), sourceProcessor.process(SnmpGetSource.builder().oid(OID).build()));

		final TestConfiguration snmpConfiguration = TestConfiguration.builder().build();
		final HostConfiguration hostConfiguration = HostConfiguration
			.builder()
			.hostname(ECS1_01)
			.hostId(ECS1_01)
			.hostType(DeviceKind.LINUX)
			.configurations(Collections.singletonMap(TestConfiguration.class, snmpConfiguration))
			.build();
		telemetryManager.setHostConfiguration(hostConfiguration);

		doReturn(true).when(protocolExtensionMock).isValidConfiguration(snmpConfiguration);
		doReturn(Set.of(SnmpGetSource.class, SnmpTableSource.class)).when(protocolExtensionMock).getSupportedSources();

		final SnmpTableSource snmpTableSource = SnmpTableSource
			.builder()
			.oid(OID)
			.selectColumns(SNMP_SELECTED_COLUMNS)
			.build();
		final SourceTable expected = SourceTable
			.builder()
			.table(EXPECTED_SNMP_TABLE_DATA)
			.headers(SNMP_SELECTED_COLUMNS_LIST)
			.build();
		doReturn(expected)
			.when(protocolExtensionMock)
			.processSource(eq(snmpTableSource), anyString(), any(TelemetryManager.class));
		final SourceTable actual = sourceProcessor.process(snmpTableSource);
		assertEquals(expected, actual);
	}

	@Test
	void testProcessTableJoinSource() {
		final TestConfiguration snmpConfiguration = TestConfiguration.builder().build();
		final HostConfiguration hostConfiguration = HostConfiguration
			.builder()
			.hostname(ECS1_01)
			.hostId(ECS1_01)
			.hostType(DeviceKind.LINUX)
			.configurations(Collections.singletonMap(TestConfiguration.class, snmpConfiguration))
			.build();

		final Map<String, SourceTable> mapSources = new HashMap<>();
		SourceTable tabl1 = SourceTable
			.builder()
			.table(
				Arrays.asList(
					Arrays.asList(LOWERCASE_A1, LOWERCASE_B1, LOWERCASE_C1),
					Arrays.asList(VALUE_VAL1, VALUE_VAL2, VALUE_VAL3),
					Arrays.asList(UPPERCASE_V1, UPPERCASE_V2, UPPERCASE_V3),
					Arrays.asList(LOWERCASE_X, LOWERCASE_Y, LOWERCASE_Z)
				)
			)
			.build();
		SourceTable tabl2 = SourceTable
			.builder()
			.table(
				Arrays.asList(
					Arrays.asList(LOWERCASE_A1, LOWERCASE_B2, LOWERCASE_C2),
					Arrays.asList(LOWERCASE_V1, LOWERCASE_V2, LOWERCASE_V3),
					Arrays.asList(CAMELCASE_VAL1, UPPERCASE_B2, UPPERCASE_C2),
					Arrays.asList(LOWERCASE_T, LOWERCASE_U, LOWERCASE_V)
				)
			)
			.build();
		mapSources.put(TAB1_REF, tabl1);
		mapSources.put(TAB2_REF, tabl2);
		ConnectorNamespace connectorNamespace = ConnectorNamespace.builder().sourceTables(mapSources).build();

		Map<String, ConnectorNamespace> connectorNamespaces = new HashMap<>();
		connectorNamespaces.put(MY_CONNECTOR_1_NAME, connectorNamespace);
		HostProperties hostProperties = HostProperties.builder().connectorNamespaces(connectorNamespaces).build();

		final TelemetryManager telemetryManager = TelemetryManager
			.builder()
			.hostConfiguration(hostConfiguration)
			.hostProperties(hostProperties)
			.build();
		final SourceProcessor sourceProcessor = SourceProcessor
			.builder()
			.telemetryManager(telemetryManager)
			.clientsExecutor(clientsExecutorMock)
			.connectorId(MY_CONNECTOR_1_NAME)
			.build();

		// standard
		List<List<String>> expectedJoin = Arrays.asList(
			Arrays.asList(LOWERCASE_A1, LOWERCASE_B1, LOWERCASE_C1, LOWERCASE_A1, LOWERCASE_B2, LOWERCASE_C2),
			Arrays.asList(VALUE_VAL1, VALUE_VAL2, VALUE_VAL3, CAMELCASE_VAL1, UPPERCASE_B2, UPPERCASE_C2),
			Arrays.asList(UPPERCASE_V1, UPPERCASE_V2, UPPERCASE_V3, CAMELCASE_VAL1, UPPERCASE_B2, UPPERCASE_C2),
			Arrays.asList(LOWERCASE_X, LOWERCASE_Y, LOWERCASE_Z, LOWERCASE_A1, LOWERCASE_B1, LOWERCASE_C1)
		);
		SourceTable expectedResult = SourceTable.builder().table(expectedJoin).build();

		List<List<String>> clientReturn = Arrays.asList(
			Arrays.asList(LOWERCASE_A1, LOWERCASE_B1, LOWERCASE_C1, LOWERCASE_A1, LOWERCASE_B2, LOWERCASE_C2),
			Arrays.asList(VALUE_VAL1, VALUE_VAL2, VALUE_VAL3, CAMELCASE_VAL1, UPPERCASE_B2, UPPERCASE_C2),
			Arrays.asList(UPPERCASE_V1, UPPERCASE_V2, UPPERCASE_V3, CAMELCASE_VAL1, UPPERCASE_B2, UPPERCASE_C2),
			Arrays.asList(LOWERCASE_X, LOWERCASE_Y, LOWERCASE_Z, LOWERCASE_A1, LOWERCASE_B1, LOWERCASE_C1)
		);
		doReturn(clientReturn)
			.when(clientsExecutorMock)
			.executeTableJoin(
				tabl1.getTable(),
				tabl2.getTable(),
				1,
				1,
				Arrays.asList(LOWERCASE_A1, LOWERCASE_B1, LOWERCASE_C1),
				false,
				true
			);

		TableJoinSource tableJoinExample = TableJoinSource
			.builder()
			.keyType(CAMELCASE_NOT_WBEM)
			.leftTable(TAB1_REF)
			.rightTable(TAB2_REF)
			.leftKeyColumn(1)
			.rightKeyColumn(1)
			.defaultRightLine(VALUE_LIST)
			.build();
		assertEquals(expectedJoin, sourceProcessor.process(tableJoinExample).getTable());
		assertTrue(
			expectedJoin.size() == sourceProcessor.process(tableJoinExample).getTable().size() &&
			expectedJoin.containsAll(sourceProcessor.process(tableJoinExample).getTable()) &&
			sourceProcessor.process(tableJoinExample).getTable().containsAll(expectedJoin)
		);
		assertEquals(new ArrayList<>(), sourceProcessor.process(tableJoinExample).getHeaders());
		assertEquals(expectedResult.getHeaders(), sourceProcessor.process(tableJoinExample).getHeaders());
		assertEquals(expectedResult.getTable(), sourceProcessor.process(tableJoinExample).getTable());

		// no default right line
		expectedJoin =
			Arrays.asList(Arrays.asList(LOWERCASE_A1, LOWERCASE_B1, LOWERCASE_C1, LOWERCASE_A1, LOWERCASE_B2, LOWERCASE_C2));
		expectedResult = SourceTable.builder().table(expectedJoin).build();
		doReturn(expectedJoin)
			.when(clientsExecutorMock)
			.executeTableJoin(tabl1.getTable(), tabl2.getTable(), 1, 1, new ArrayList<>(), false, true);
		tableJoinExample =
			TableJoinSource
				.builder()
				.keyType(CAMELCASE_NOT_WBEM)
				.leftTable(TAB1_REF)
				.rightTable(TAB2_REF)
				.leftKeyColumn(1)
				.rightKeyColumn(1)
				.defaultRightLine(null)
				.build();
		assertEquals(expectedResult.getTable(), sourceProcessor.process(tableJoinExample).getTable());
		assertEquals(new ArrayList<>(), sourceProcessor.process(tableJoinExample).getHeaders());
		assertEquals(expectedResult.getHeaders(), sourceProcessor.process(tableJoinExample).getHeaders());
		assertEquals(expectedResult.getTable(), sourceProcessor.process(tableJoinExample).getTable());

		// no matches
		SourceTable tabl3 = SourceTable
			.builder()
			.table(
				Arrays.asList(
					Arrays.asList(LOWERCASE_A, LOWERCASE_B, LOWERCASE_C),
					Arrays.asList(LOWERCASE_V10, LOWERCASE_V20, LOWERCASE_V30)
				)
			)
			.build();
		mapSources.put(TAB3_REF, tabl3);
		expectedJoin = Arrays.asList(Arrays.asList(LOWERCASE_A, LOWERCASE_B, LOWERCASE_C));
		expectedResult = SourceTable.builder().table(expectedJoin).build();
		doReturn(expectedJoin)
			.when(clientsExecutorMock)
			.executeTableJoin(tabl1.getTable(), tabl3.getTable(), 1, 1, new ArrayList<>(), false, true);
		tableJoinExample =
			TableJoinSource
				.builder()
				.keyType(CAMELCASE_NOT_WBEM)
				.leftTable(TAB1_REF)
				.rightTable(TAB3_REF)
				.leftKeyColumn(1)
				.rightKeyColumn(1)
				.defaultRightLine(null)
				.build();
		assertEquals(expectedResult.getTable(), sourceProcessor.process(tableJoinExample).getTable());

		// wrong column key
		tabl3 =
			SourceTable
				.builder()
				.table(
					Arrays.asList(
						Arrays.asList(LOWERCASE_A, LOWERCASE_B, LOWERCASE_C),
						Arrays.asList(LOWERCASE_V10, LOWERCASE_V20, LOWERCASE_V30)
					)
				)
				.build();
		mapSources.put(TAB3_REF, tabl3);
		tableJoinExample =
			TableJoinSource
				.builder()
				.keyType(CAMELCASE_NOT_WBEM)
				.leftTable(TAB1_REF)
				.rightTable(TAB3_REF)
				.leftKeyColumn(0)
				.rightKeyColumn(1)
				.defaultRightLine(null)
				.build();
		assertEquals(SourceTable.empty(), sourceProcessor.process(tableJoinExample));

		// null args
		tableJoinExample =
			TableJoinSource
				.builder()
				.keyType(CAMELCASE_NOT_WBEM)
				.leftTable(null)
				.rightTable(TAB3_REF)
				.leftKeyColumn(1)
				.rightKeyColumn(1)
				.defaultRightLine(null)
				.build();
		assertEquals(new ArrayList<>(), sourceProcessor.process(tableJoinExample).getTable());
		tableJoinExample =
			TableJoinSource
				.builder()
				.keyType(CAMELCASE_NOT_WBEM)
				.leftTable(TAB1_REF)
				.rightTable(null)
				.leftKeyColumn(1)
				.rightKeyColumn(1)
				.defaultRightLine(null)
				.build();
		// table not in sources
		assertEquals(new ArrayList<>(), sourceProcessor.process(tableJoinExample).getTable());
		tableJoinExample =
			TableJoinSource
				.builder()
				.keyType(CAMELCASE_NOT_WBEM)
				.leftTable(TAB1_REF)
				.rightTable("blabla")
				.leftKeyColumn(1)
				.rightKeyColumn(1)
				.defaultRightLine(null)
				.build();
		assertEquals(new ArrayList<>(), sourceProcessor.process(tableJoinExample).getTable());

		tableJoinExample =
			TableJoinSource
				.builder()
				.keyType(CAMELCASE_NOT_WBEM)
				.leftTable(TAB1_REF)
				.rightTable(TAB2_REF)
				.leftKeyColumn(1)
				.rightKeyColumn(1)
				.defaultRightLine(null)
				.build();
		assertEquals(
			Arrays.asList(Arrays.asList(LOWERCASE_A1, LOWERCASE_B1, LOWERCASE_C1, LOWERCASE_A1, LOWERCASE_B2, LOWERCASE_C2)),
			sourceProcessor.process(tableJoinExample).getTable()
		);
	}

	@Test
	void testProcessTableUnionSourceTest() {
		SourceTable tabl1 = SourceTable
			.builder()
			.table(
				Arrays.asList(
					Arrays.asList(LOWERCASE_A1, LOWERCASE_B1, LOWERCASE_C1),
					Arrays.asList(VALUE_VAL1, VALUE_VAL2, VALUE_VAL3)
				)
			)
			.rawData(LOWERCASE_A1)
			.build();
		SourceTable tabl2 = SourceTable
			.builder()
			.table(
				Arrays.asList(
					Arrays.asList(LOWERCASE_A1, LOWERCASE_B2, LOWERCASE_C2),
					Arrays.asList(LOWERCASE_V1, LOWERCASE_V2, LOWERCASE_V3)
				)
			)
			.rawData(TAB1_REF)
			.build();

		final Map<String, SourceTable> mapSources = new HashMap<>();
		mapSources.put(TAB1_REF, tabl1);
		mapSources.put(TAB2_REF, tabl2);

		// standard
		List<List<String>> expectedUnion = Arrays.asList(
			Arrays.asList(LOWERCASE_A1, LOWERCASE_B1, LOWERCASE_C1),
			Arrays.asList(VALUE_VAL1, VALUE_VAL2, VALUE_VAL3),
			Arrays.asList(LOWERCASE_A1, LOWERCASE_B2, LOWERCASE_C2),
			Arrays.asList(LOWERCASE_V1, LOWERCASE_V2, LOWERCASE_V3)
		);

		final TestConfiguration snmpConfiguration = TestConfiguration.builder().build();
		final HostConfiguration hostConfiguration = HostConfiguration
			.builder()
			.hostname(ECS1_01)
			.hostId(ECS1_01)
			.hostType(DeviceKind.LINUX)
			.configurations(Collections.singletonMap(TestConfiguration.class, snmpConfiguration))
			.build();
		ConnectorNamespace connectorNamespace = ConnectorNamespace.builder().sourceTables(mapSources).build();

		Map<String, ConnectorNamespace> connectorNamespaces = new HashMap<>();
		connectorNamespaces.put(MY_CONNECTOR_1_NAME, connectorNamespace);
		HostProperties hostProperties = HostProperties.builder().connectorNamespaces(connectorNamespaces).build();

		final TelemetryManager telemetryManager = TelemetryManager
			.builder()
			.hostConfiguration(hostConfiguration)
			.hostProperties(hostProperties)
			.build();
		final SourceProcessor sourceProcessor = SourceProcessor
			.builder()
			.telemetryManager(telemetryManager)
			.clientsExecutor(clientsExecutorMock)
			.connectorId(MY_CONNECTOR_1_NAME)
			.build();

		TableUnionSource tableUnionExample = TableUnionSource.builder().tables(Arrays.asList()).build();
		assertEquals(new ArrayList<>(), sourceProcessor.process(tableUnionExample).getTable());

		tableUnionExample = TableUnionSource.builder().tables(Arrays.asList(TAB1_REF, TAB2_REF, TAB3_REF)).build();

		assertEquals(expectedUnion, sourceProcessor.process(tableUnionExample).getTable());
		assertEquals(LOWERCASE_A1 + "\n" + TAB1_REF, sourceProcessor.process(tableUnionExample).getRawData());
	}

	@Test
	void testProcessCopySource() {
		final TestConfiguration snmpConfiguration = TestConfiguration.builder().build();
		final HostConfiguration hostConfiguration = HostConfiguration
			.builder()
			.hostname(ECS1_01)
			.hostId(ECS1_01)
			.hostType(DeviceKind.LINUX)
			.configurations(Collections.singletonMap(TestConfiguration.class, snmpConfiguration))
			.build();

		final List<List<String>> expectedTable = new ArrayList<>(
			Arrays.asList(
				new ArrayList<>(Arrays.asList(LOWERCASE_A1, LOWERCASE_B1, LOWERCASE_C1)),
				new ArrayList<>(Arrays.asList(VALUE_VAL1, VALUE_VAL2, VALUE_VAL3))
			)
		);

		SourceTable table = SourceTable.builder().table(expectedTable).rawData("rawData").build();

		ConnectorNamespace namespace = ConnectorNamespace.builder().sourceTables(Map.of(TAB1_REF, table)).build();
		Map<String, ConnectorNamespace> connectorNamespaces = new HashMap<>();
		connectorNamespaces.put(MY_CONNECTOR_1_NAME, namespace);
		HostProperties hostProperties = HostProperties.builder().connectorNamespaces(connectorNamespaces).build();

		final TelemetryManager telemetryManager = TelemetryManager
			.builder()
			.hostConfiguration(hostConfiguration)
			.hostProperties(hostProperties)
			.build();
		final SourceProcessor sourceProcessor = SourceProcessor
			.builder()
			.telemetryManager(telemetryManager)
			.clientsExecutor(clientsExecutorMock)
			.connectorId(MY_CONNECTOR_1_NAME)
			.build();

		CopySource copySource = null;
		assertEquals(SourceTable.empty(), sourceProcessor.process(copySource));
		copySource = new CopySource();
		assertEquals(SourceTable.empty(), sourceProcessor.process(copySource));

		copySource = CopySource.builder().from("").build();
		assertEquals(SourceTable.empty(), sourceProcessor.process(copySource));

		copySource = CopySource.builder().from(TAB1_REF).build();

		final List<List<String>> tableResult = sourceProcessor.process(copySource).getTable();

		assertEquals(expectedTable, tableResult);

		tableResult.get(0).add(VALUE_VAL3);

		assertEquals(
			Arrays.asList(
				Arrays.asList(LOWERCASE_A1, LOWERCASE_B1, LOWERCASE_C1),
				Arrays.asList(VALUE_VAL1, VALUE_VAL2, VALUE_VAL3)
			),
			expectedTable
		);
	}

	@Test
	void testProcessStaticSource() {
		final TestConfiguration snmpConfiguration = TestConfiguration.builder().build();
		final HostConfiguration hostConfiguration = HostConfiguration
			.builder()
			.hostname(ECS1_01)
			.hostId(ECS1_01)
			.hostType(DeviceKind.LINUX)
			.configurations(Collections.singletonMap(TestConfiguration.class, snmpConfiguration))
			.build();
		final TelemetryManager telemetryManager = TelemetryManager.builder().hostConfiguration(hostConfiguration).build();
		final SourceProcessor sourceProcessor = SourceProcessor
			.builder()
			.telemetryManager(telemetryManager)
			.clientsExecutor(clientsExecutorMock)
			.build();

		StaticSource staticSource = null;
		assertEquals(SourceTable.empty(), sourceProcessor.process(staticSource));
		staticSource = new StaticSource();
		assertEquals(SourceTable.empty(), sourceProcessor.process(staticSource));

		List<List<String>> expectedTable = Arrays.asList(Arrays.asList(LOWERCASE_A1));

		staticSource = StaticSource.builder().value(LOWERCASE_A1).build();

		assertEquals(expectedTable, sourceProcessor.process(staticSource).getTable());
		assertEquals(SourceTable.empty(), sourceProcessor.process(new StaticSource()));

		expectedTable = Arrays.asList(new ArrayList<>(Arrays.asList(LOWERCASE_A1, LOWERCASE_B1, LOWERCASE_C1)));

		staticSource = StaticSource.builder().value(VALUE_LIST).build();

		assertEquals(expectedTable, sourceProcessor.process(staticSource).getTable());
	}

	@Test
	void testProcessWbemSource() throws ClientException {
		final TestConfiguration wbemConfiguration = TestConfiguration.builder().build();
		final HostConfiguration hostConfiguration = HostConfiguration
			.builder()
			.hostname(ECS1_01)
			.hostId(ECS1_01)
			.hostType(DeviceKind.LINUX)
			.configurations(Collections.singletonMap(TestConfiguration.class, wbemConfiguration))
			.build();

		final TelemetryManager telemetryManager = TelemetryManager.builder().hostConfiguration(hostConfiguration).build();

		final ExtensionManager extensionManager = ExtensionManager
			.builder()
			.withProtocolExtensions(List.of(protocolExtensionMock))
			.build();

		final SourceProcessor sourceProcessor = SourceProcessor
			.builder()
			.telemetryManager(telemetryManager)
			.clientsExecutor(clientsExecutorMock)
			.extensionManager(extensionManager)
			.connectorId(CONNECTOR_ID)
			.build();

		doReturn(true).when(protocolExtensionMock).isValidConfiguration(wbemConfiguration);

		doReturn(Set.of(HttpSource.class)).when(protocolExtensionMock).getSupportedSources();

		final SourceTable expected = SourceTable.builder().rawData(ECS1_01).build();

		final HttpSource source = HttpSource.builder().url(URL).method(HttpMethod.GET).build();

		doReturn(expected).when(protocolExtensionMock).processSource(eq(source), anyString(), any(TelemetryManager.class));

		final SourceTable actual = sourceProcessor.process(source);

		assertEquals(expected, actual);
	}

	void testProcessIpmiSourceStorageHost() {
		final HostConfiguration hostConfiguration = HostConfiguration
			.builder()
			.hostname(ECS1_01)
			.hostId(ECS1_01)
			.hostType(DeviceKind.STORAGE)
			.build();
		final TelemetryManager telemetryManager = TelemetryManager.builder().hostConfiguration(hostConfiguration).build();
		final SourceProcessor sourceProcessor = SourceProcessor
			.builder()
			.telemetryManager(telemetryManager)
			.clientsExecutor(clientsExecutorMock)
			.build();
		assertEquals(SourceTable.empty(), sourceProcessor.process(new IpmiSource()));
	}

	@Test
	void testSqlQuery() {
		final HostConfiguration hostConfiguration = HostConfiguration
			.builder()
			.hostname("localhost")
			.hostId("localhost")
			.hostType(DeviceKind.LINUX)
			.build();

		final Map<String, SourceTable> mapSources = new HashMap<>();
		final SourceTable tabl1 = SourceTable
			.builder()
			.table(
				Arrays.asList(
					Arrays.asList(LOWERCASE_A, LOWERCASE_V1, TRUE, ONE),
					Arrays.asList(LOWERCASE_B, LOWERCASE_V2, TRUE, TWO),
					Arrays.asList(LOWERCASE_C, LOWERCASE_V3, FALSE, THREE),
					Arrays.asList(LOWERCASE_D, LOWERCASE_V4, FALSE, FOUR)
				)
			)
			.build();
		final SourceTable tabl2 = SourceTable
			.builder()
			.table(
				Arrays.asList(
					Arrays.asList(ONE, LOWERCASE_A, UPPERCASE_V1, TRUE),
					Arrays.asList(ONE, LOWERCASE_B, UPPERCASE_V2, FALSE),
					Arrays.asList(ONE, LOWERCASE_C, UPPERCASE_V3, TRUE),
					Arrays.asList(TWO, LOWERCASE_D, UPPERCASE_V4, FALSE)
				)
			)
			.build();
		mapSources.put(TAB1_REF, tabl1);
		mapSources.put(TAB2_REF, tabl2);
		final ConnectorNamespace connectorNamespace = ConnectorNamespace.builder().sourceTables(mapSources).build();
		final Map<String, ConnectorNamespace> connectorNamespaces = new HashMap<>();
		connectorNamespaces.put(CONNECTOR_ID, connectorNamespace);

		final HostProperties hostProperties = HostProperties
			.builder()
			.connectorNamespaces(connectorNamespaces)
			.isLocalhost(true)
			.build();
		final TelemetryManager telemetryManager = TelemetryManager
			.builder()
			.hostConfiguration(hostConfiguration)
			.hostProperties(hostProperties)
			.build();
		final ExtensionManager extensionManager = ExtensionManager
			.builder()
			.withSourceComputationExtensions(List.of(sourceComputationExtensionMock))
			.build();
		final SourceProcessor sourceProcessor = SourceProcessor
			.builder()
			.telemetryManager(telemetryManager)
			.clientsExecutor(clientsExecutorMock)
			.connectorId(CONNECTOR_ID)
			.extensionManager(extensionManager)
			.build();

		// InternalDbQuerySource with empty tables
		final List<SqlColumn> columnsTable1 = new ArrayList<>();
		final List<SqlColumn> columnsTable2 = new ArrayList<>();

		final List<SqlTable> sqlTables = Arrays.asList(
			SqlTable.builder().alias("T1").columns(columnsTable1).source(TAB1_REF).build(),
			SqlTable.builder().alias("T2").columns(columnsTable2).source(TAB2_REF).build()
		);
		final InternalDbQuerySource internalDbQuery = InternalDbQuerySource
			.builder()
			.tables(sqlTables)
			.query("SQL QUERY;")
			.build();

		doReturn(true).when(sourceComputationExtensionMock).isValidSource(internalDbQuery);
		doReturn(SourceTable.empty())
			.when(sourceComputationExtensionMock)
			.processSource(internalDbQuery, CONNECTOR_ID, telemetryManager);

		assertEquals(SourceTable.empty(), sourceProcessor.process(internalDbQuery));

		// InternalDbQuerySource well formed
		columnsTable1.add(SqlColumn.builder().name("COL1_1").number(1).type("VARCHAR(255)").build());
		columnsTable1.add(SqlColumn.builder().name("COL2_1").number(3).type("BOOLEAN").build());

		columnsTable2.add(SqlColumn.builder().name("COL1_2").number(2).type("VARCHAR(255)").build());
		columnsTable2.add(SqlColumn.builder().name("COL2_2").number(4).type("BOOLEAN").build());

		internalDbQuery.setQuery("SELECT COL1_1, COL2_1, COL1_2, COL2_2 FROM T1 JOIN T2 ON COL1_1 = COL1_2;");

		List<List<String>> result = Arrays.asList(
			Arrays.asList(LOWERCASE_A, TRUE, LOWERCASE_A, TRUE),
			Arrays.asList(LOWERCASE_B, TRUE, LOWERCASE_B, FALSE),
			Arrays.asList(LOWERCASE_C, FALSE, LOWERCASE_C, TRUE),
			Arrays.asList(LOWERCASE_D, FALSE, LOWERCASE_D, FALSE)
		);

		final SourceTable sourceTableExpected = new SourceTable();
		sourceTableExpected.setTable(result);

		doReturn(true).when(sourceComputationExtensionMock).isValidSource(internalDbQuery);
		doReturn(sourceTableExpected)
			.when(sourceComputationExtensionMock)
			.processSource(internalDbQuery, CONNECTOR_ID, telemetryManager);

		assertEquals(result, sourceProcessor.process(internalDbQuery).getTable());
	}

	@Test
	void testProcessJmxSource() {
		final TestConfiguration jmxConfiguration = TestConfiguration.builder().build();
		final HostConfiguration hostConfiguration = HostConfiguration
			.builder()
			.hostname(ECS1_01)
			.hostId(ECS1_01)
			.hostType(DeviceKind.LINUX)
			.configurations(Collections.singletonMap(TestConfiguration.class, jmxConfiguration))
			.build();

		final TelemetryManager telemetryManager = TelemetryManager.builder().hostConfiguration(hostConfiguration).build();

		final ExtensionManager extensionManager = ExtensionManager
			.builder()
			.withProtocolExtensions(List.of(protocolExtensionMock))
			.build();

		final SourceProcessor sourceProcessor = SourceProcessor
			.builder()
			.telemetryManager(telemetryManager)
			.clientsExecutor(clientsExecutorMock)
			.extensionManager(extensionManager)
			.connectorId(CONNECTOR_ID)
			.build();

		doReturn(true).when(protocolExtensionMock).isValidConfiguration(jmxConfiguration);

		doReturn(Set.of(JmxSource.class)).when(protocolExtensionMock).getSupportedSources();

		final SourceTable expected = SourceTable.builder().rawData("data").build();

		final JmxSource source = JmxSource
			.builder()
			.type("jmx")
			.objectName("org.metricshub.extension.jmx:type=JmxMBean,scope=*")
			.attributes(new LinkedList<>(List.of("Name")))
			.key("${source::beforeAll.jmxSource}")
			.forceSerialization(false)
			.keyProperties(new LinkedList<>(List.of("scope")))
			.build();

		doReturn(expected).when(protocolExtensionMock).processSource(eq(source), anyString(), any(TelemetryManager.class));

		final SourceTable actual = sourceProcessor.process(source);

		assertEquals(expected, actual, "Processed JMX source should match expected output");
	}
}
