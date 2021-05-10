package com.sentrysoftware.matrix.engine.strategy.collect;

import static com.sentrysoftware.matrix.connector.model.monitor.MonitorType.ENCLOSURE;
import static com.sentrysoftware.matrix.connector.model.monitor.MonitorType.TARGET;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doReturn;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.sentrysoftware.matrix.common.helpers.HardwareConstants;
import com.sentrysoftware.matrix.connector.ConnectorStore;
import com.sentrysoftware.matrix.connector.model.Connector;
import com.sentrysoftware.matrix.connector.model.monitor.HardwareMonitor;
import com.sentrysoftware.matrix.connector.model.monitor.MonitorType;
import com.sentrysoftware.matrix.connector.model.monitor.job.collect.Collect;
import com.sentrysoftware.matrix.connector.model.monitor.job.collect.CollectType;
import com.sentrysoftware.matrix.connector.model.monitor.job.source.compute.LeftConcat;
import com.sentrysoftware.matrix.connector.model.monitor.job.source.type.snmp.SNMPGetTableSource;
import com.sentrysoftware.matrix.engine.EngineConfiguration;
import com.sentrysoftware.matrix.engine.protocol.SNMPProtocol;
import com.sentrysoftware.matrix.engine.protocol.SNMPProtocol.SNMPVersion;
import com.sentrysoftware.matrix.engine.strategy.StrategyConfig;
import com.sentrysoftware.matrix.engine.strategy.source.SourceTable;
import com.sentrysoftware.matrix.engine.strategy.source.SourceVisitor;
import com.sentrysoftware.matrix.engine.target.HardwareTarget;
import com.sentrysoftware.matrix.engine.target.TargetType;
import com.sentrysoftware.matrix.model.monitor.Monitor;
import com.sentrysoftware.matrix.model.monitoring.HostMonitoring;
import com.sentrysoftware.matrix.model.monitoring.IHostMonitoring;
import com.sentrysoftware.matrix.model.parameter.IParameterValue;
import com.sentrysoftware.matrix.model.parameter.NumberParam;
import com.sentrysoftware.matrix.model.parameter.ParameterState;
import com.sentrysoftware.matrix.model.parameter.StatusParam;

@ExtendWith(MockitoExtension.class)
class CollectOperationTest {

	private static final String ENERGY_USAGE_15000_JOULES = "15000";
	private static final String OK_RAW_STATUS = "OK";
	private static final String OPERABLE = "Operable";
	private static final String VALUETABLE_COLUMN_10 = "Valuetable.Column(10)";
	private static final String VALUETABLE_COLUMN_1 = "Valuetable.Column(1)";
	private static final String VALUETABLE_COLUMN_2 = "Valuetable.Column(2)";
	private static final String VALUETABLE_COLUMN_3 = "Valuetable.Column(3)";
	private static final String VALUETABLE_COLUMN_4 = "Valuetable.Column(4)";
	private static final String VALUETABLE_COLUMN_5 = "Valuetable.Column(5)";
	private static final String ENCLOSURE_DEVICE_ID = "1.1";
	private static final String COMMUNITY = "public";
	private static final String ECS1_01 = "ecs1-01";
	private static final String MY_CONNECTOR_NAME = "myConnecctor.connector";
	private static final String ENERGY_USAGE = "energyUsage";
	private static final String CONNECTOR_NAME = "myConnector.connector";
	private static final String ENCLOSURE_NAME = "enclosure";
	private static final String ENCLOSURE_ID = "myConnecctor1.connector_enclosure_ecs1-01_1.1";
	private static final String TARGET_ID = "targetId";
	private static final String VALUE_TABLE = "Enclosure.Collect.Source(1)";
	private static final String DEVICE_ID = "deviceId";
	private static final ParameterState UNKNOWN_STATUS_WARN = ParameterState.WARN;
	private static final String OID1 = "1.2.3.4.5";

	@Mock
	private StrategyConfig strategyConfig;

	@Mock
	private ConnectorStore store;

	@Mock
	private SourceVisitor sourceVisitor;

	private static Long strategyTime = new Date().getTime();

	@InjectMocks
	private CollectOperation collectOperation;

	private static EngineConfiguration engineConfiguration;

	private static Connector connector;

	private static Map<String, String> parameters = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
	private static Map<String, String> metadata = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
	private static SourceTable sourceTable;
	private static List<String> row = Arrays.asList(ENCLOSURE_DEVICE_ID,
			OK_RAW_STATUS,
			OPERABLE,
			OK_RAW_STATUS,
			ENERGY_USAGE_15000_JOULES);

	@BeforeAll
	public static void setUp() {
		final SNMPProtocol protocol = SNMPProtocol.builder().community(COMMUNITY).version(SNMPVersion.V1).port(161)
				.timeout(120L).build();
		engineConfiguration = EngineConfiguration
				.builder()
				.target(HardwareTarget
						.builder()
						.hostname(ECS1_01)
						.id(ECS1_01)
						.type(TargetType.LINUX)
						.build())
				.protocolConfigurations(Stream.of(protocol).collect(Collectors.toSet()))
				.unknownStatus(UNKNOWN_STATUS_WARN)
				.build();

		connector = Connector.builder().compiledFilename(MY_CONNECTOR_NAME).build();

		parameters.put(HardwareConstants.DEVICE_ID, VALUETABLE_COLUMN_1);
		parameters.put(HardwareConstants.STATUS_PARAMETER, VALUETABLE_COLUMN_2);
		parameters.put(HardwareConstants.STATUS_INFORMATION_PARAMETER, VALUETABLE_COLUMN_3);
		parameters.put(HardwareConstants.INTRUSION_STATUS_PARAMETER, VALUETABLE_COLUMN_4);
		parameters.put(HardwareConstants.ENERGY_USAGE_PARAMETER, VALUETABLE_COLUMN_5);

		metadata.put(DEVICE_ID, ENCLOSURE_DEVICE_ID);

		final List<List<String>> table = new ArrayList<>();
		table.add(row);

		sourceTable = SourceTable.builder().table(table).build();

		connector.setHardwareMonitors(Collections.singletonList(buildHardwareEnclosureMonitor(CollectType.MULTI_INSTANCE)));

	}

	@BeforeEach
	void beforeEeach() {
		collectOperation.setStrategyTime(strategyTime);
	}


	@Test
	void testPrepare() {

		// First collect
		{
			final IHostMonitoring hostMonitoring = new HostMonitoring();
			final Monitor enclosure = buildEnclosure(metadata);
			hostMonitoring.addMonitor(enclosure, ENCLOSURE_ID, CONNECTOR_NAME, ENCLOSURE, TARGET_ID, TARGET.getName());

			doReturn(hostMonitoring).when(strategyConfig).getHostMonitoring();
			collectOperation.prepare();
			assertEquals(enclosure, hostMonitoring.getMonitors().get(ENCLOSURE).get(ENCLOSURE_ID));
			assertTrue(hostMonitoring.getPreviousMonitors().isEmpty());
			assertTrue(hostMonitoring.getSourceTables().isEmpty());
		}

		// Next collect
		{
			final IHostMonitoring hostMonitoring = new HostMonitoring();

			final Monitor enclosure = buildEnclosure(metadata);

			final IParameterValue parameter = NumberParam.builder().name(ENERGY_USAGE)
					.collectTime(strategyTime).value(100.0).build();
			enclosure.addParameter(parameter);

			hostMonitoring.addMonitor(enclosure, ENCLOSURE_ID, CONNECTOR_NAME, ENCLOSURE, TARGET_ID, TARGET.getName());

			doReturn(hostMonitoring).when(strategyConfig).getHostMonitoring();
			collectOperation.prepare();

			final Monitor result = hostMonitoring.getMonitors().get(ENCLOSURE).get(ENCLOSURE_ID);
			assertNotNull(result);

			final NumberParam parameterAfterReset = (NumberParam) result.getParameters().get(ENERGY_USAGE);
			
			assertNull(parameterAfterReset.getCollectTime());
			assertEquals(strategyTime, parameterAfterReset.getLastCollectTime());
			assertEquals(ENERGY_USAGE, parameterAfterReset.getName());
			assertEquals(ParameterState.OK, parameterAfterReset.getState());
			assertNull(parameterAfterReset.getThreshold());
			assertNull(parameterAfterReset.getValue());
			assertEquals(100.0, parameterAfterReset.getLastValue());
		}
	}

	@Test
	void testCallNoConnectorMonitor() throws Exception {

		{
			IHostMonitoring hostMonitoring = new HostMonitoring();
			final Monitor enclosure = buildEnclosure(metadata);

			hostMonitoring.addMonitor(enclosure);

			doReturn(engineConfiguration).when(strategyConfig).getEngineConfiguration();
			doReturn(hostMonitoring).when(strategyConfig).getHostMonitoring();
			collectOperation.call();

			final Monitor actual = getCollectedEnclosure(hostMonitoring);

			assertEquals(enclosure, actual);
		}

		{
			final IHostMonitoring hostMonitoring = new HostMonitoring();

			final Monitor connectorMonitor = Monitor
					.builder()
					.monitorType(MonitorType.CONNECTOR)
					.name(MY_CONNECTOR_NAME)
					.parentId(ECS1_01)
					.targetId(ECS1_01)
					.id(MY_CONNECTOR_NAME)
					.build();

			final Monitor enclosure = buildEnclosure(metadata);

			hostMonitoring.addMonitor(enclosure);
			hostMonitoring.addMonitor(connectorMonitor);
			hostMonitoring.removeMonitor(connectorMonitor);

			doReturn(engineConfiguration).when(strategyConfig).getEngineConfiguration();
			doReturn(hostMonitoring).when(strategyConfig).getHostMonitoring();

			collectOperation.call();

			final Monitor actual = getCollectedEnclosure(hostMonitoring);

			assertEquals(enclosure, actual);


		}

	}

	@Test
	void testCall() throws Exception {

		final IHostMonitoring hostMonitoring = new HostMonitoring();

		final Monitor connectorMonitor = Monitor
				.builder()
				.monitorType(MonitorType.CONNECTOR)
				.name(MY_CONNECTOR_NAME)
				.parentId(ECS1_01)
				.targetId(ECS1_01)
				.id(MY_CONNECTOR_NAME)
				.build();

		final Monitor enclosure = buildEnclosure(metadata);

		hostMonitoring.addMonitor(enclosure);
		hostMonitoring.addMonitor(connectorMonitor);

		doReturn(engineConfiguration).when(strategyConfig).getEngineConfiguration();
		doReturn(Collections.singletonMap(MY_CONNECTOR_NAME, connector)).when(store).getConnectors();
		doReturn(hostMonitoring).when(strategyConfig).getHostMonitoring();
		doReturn(sourceTable).when(sourceVisitor).visit((SNMPGetTableSource) connector
				.getHardwareMonitors()
				.get(0)
				.getCollect()
				.getSources()
				.get(0));

		collectOperation.call();

		final Monitor expected = buildExpectedEnclosure();
		final Monitor actual = getCollectedEnclosure(hostMonitoring);

		assertEquals(expected, actual);

	}

	@Test
	void testCollectNoHardwareMonitors() {
		final IHostMonitoring hostMonitoring = new HostMonitoring();
		final Monitor enclosure = buildEnclosure(metadata);

		hostMonitoring.addMonitor(enclosure);

		final Connector connector = Connector.builder().hardwareMonitors(null).build();

		collectOperation.collect(connector, hostMonitoring, ECS1_01);

		final Monitor actual = getCollectedEnclosure(hostMonitoring);

		assertEquals(enclosure, actual);

	}

	@Test
	void testCollect() {
		final IHostMonitoring hostMonitoring = new HostMonitoring();
		final Monitor enclosure = buildEnclosure(metadata);

		hostMonitoring.addMonitor(enclosure);

		doReturn(engineConfiguration).when(strategyConfig).getEngineConfiguration();
		doReturn(sourceTable).when(sourceVisitor).visit((SNMPGetTableSource) connector
				.getHardwareMonitors()
				.get(0)
				.getCollect()
				.getSources()
				.get(0));

		collectOperation.collect(connector, hostMonitoring, ECS1_01);

		final Monitor expected = buildExpectedEnclosure();
		final Monitor actual = getCollectedEnclosure(hostMonitoring);

		assertEquals(expected, actual);

	}

	@Test
	void testCollectSameTypeMonitorsMonoInstance() {

		final IHostMonitoring hostMonitoring = new HostMonitoring();
		final Monitor enclosure = buildEnclosure(metadata);

		hostMonitoring.addMonitor(enclosure);

		final HardwareMonitor enclosureHardwareMonitor = buildHardwareEnclosureMonitor(CollectType.MONO_INSTANCE);

		assertDoesNotThrow(() -> collectOperation.collectSameTypeMonitors(enclosureHardwareMonitor, connector, hostMonitoring, ECS1_01), "Not implemented yet");
	}

	@Test
	void testCollectSameTypeMonitorsNullParameters() {

		final IHostMonitoring hostMonitoring = new HostMonitoring();
		final Monitor enclosure = buildEnclosure(metadata);

		hostMonitoring.addMonitor(enclosure);

		final HardwareMonitor enclosureHardwareMonitor = buildHardwareEnclosureMonitor(CollectType.MULTI_INSTANCE);

		enclosureHardwareMonitor.getCollect().setParameters(null);

		collectOperation.collectSameTypeMonitors(enclosureHardwareMonitor, connector, hostMonitoring, ECS1_01);

		Monitor actual = getCollectedEnclosure(hostMonitoring);

		assertEquals(enclosure, actual);

		enclosureHardwareMonitor.getCollect().setParameters(Collections.emptyMap());

		collectOperation.collectSameTypeMonitors(enclosureHardwareMonitor, connector, hostMonitoring, ECS1_01);

		actual = getCollectedEnclosure(hostMonitoring);

		assertEquals(enclosure, actual);
	}

	@Test
	void testCollectSameTypeMonitorsNullCollectType() {

		final IHostMonitoring hostMonitoring = new HostMonitoring();
		final Monitor enclosure = buildEnclosure(metadata);

		hostMonitoring.addMonitor(enclosure);

		final HardwareMonitor enclosureHardwareMonitor = buildHardwareEnclosureMonitor(CollectType.MULTI_INSTANCE);

		enclosureHardwareMonitor.getCollect().setType(null);

		collectOperation.collectSameTypeMonitors(enclosureHardwareMonitor, connector, hostMonitoring, ECS1_01);

		final Monitor actual = getCollectedEnclosure(hostMonitoring);

		assertEquals(enclosure, actual);
	}

	@Test
	void testCollectSameTypeMonitorsNullCollect() {

		final IHostMonitoring hostMonitoring = new HostMonitoring();
		final Monitor enclosure = buildEnclosure(metadata);

		hostMonitoring.addMonitor(enclosure);

		final HardwareMonitor enclosureHardwareMonitor = buildHardwareEnclosureMonitor(CollectType.MULTI_INSTANCE);

		enclosureHardwareMonitor.setCollect(null);

		collectOperation.collectSameTypeMonitors(enclosureHardwareMonitor, connector, hostMonitoring, ECS1_01);

		final Monitor actual = getCollectedEnclosure(hostMonitoring);

		assertEquals(enclosure, actual);
	}

	@Test
	void testCollectSameTypeMonitorsNullMonitorType() {

		final IHostMonitoring hostMonitoring = new HostMonitoring();
		final Monitor enclosure = buildEnclosure(metadata);

		hostMonitoring.addMonitor(enclosure);

		final HardwareMonitor enclosureHardwareMonitor = buildHardwareEnclosureMonitor(CollectType.MULTI_INSTANCE);

		enclosureHardwareMonitor.setType(null);

		collectOperation.collectSameTypeMonitors(enclosureHardwareMonitor, connector, hostMonitoring, ECS1_01);

		final Monitor actual = getCollectedEnclosure(hostMonitoring);

		assertEquals(enclosure, actual);
	}

	@Test
	void testCollectSameTypeMonitors() {

		final IHostMonitoring hostMonitoring = new HostMonitoring();
		final Monitor enclosure = buildEnclosure(metadata);

		hostMonitoring.addMonitor(enclosure);

		final HardwareMonitor enclosureHardwareMonitor = buildHardwareEnclosureMonitor(CollectType.MULTI_INSTANCE);

		doReturn(engineConfiguration).when(strategyConfig).getEngineConfiguration();
		doReturn(sourceTable).when(sourceVisitor).visit((SNMPGetTableSource) enclosureHardwareMonitor.getCollect().getSources().get(0));

		collectOperation.collectSameTypeMonitors(enclosureHardwareMonitor, connector, hostMonitoring, ECS1_01);

		final Monitor expected = buildExpectedEnclosure();
		final Monitor actual = getCollectedEnclosure(hostMonitoring);

		assertEquals(expected, actual);
	}

	@Test
	void testCollectSameTypeMonitorsNullSourceTable() {

		final IHostMonitoring hostMonitoring = new HostMonitoring();
		final Monitor enclosure = buildEnclosure(metadata);

		hostMonitoring.addMonitor(enclosure);

		final HardwareMonitor enclosureHardwareMonitor = buildHardwareEnclosureMonitor(CollectType.MULTI_INSTANCE);
		collectOperation.collectSameTypeMonitors(enclosureHardwareMonitor, connector, hostMonitoring, ECS1_01);

		final Monitor actual = getCollectedEnclosure(hostMonitoring);

		assertEquals(enclosure, actual);
	}

	@Test
	void testProcessValueTableMonitorNoFound() {
		final IHostMonitoring hostMonitoring = new HostMonitoring();

		hostMonitoring.addSourceTable(VALUE_TABLE, sourceTable);

		collectOperation.processValueTable(VALUE_TABLE, MY_CONNECTOR_NAME, hostMonitoring, parameters, ENCLOSURE, ECS1_01);

		assertTrue(hostMonitoring.getMonitors().isEmpty());
	}

	@Test
	void testProcessValueTableNullSourceTable() {
		final IHostMonitoring hostMonitoring = new HostMonitoring();
		final Monitor expectedEnclosure = buildEnclosure(metadata);
		hostMonitoring.addMonitor(expectedEnclosure);

		collectOperation.processValueTable(VALUE_TABLE, MY_CONNECTOR_NAME, hostMonitoring, parameters, ENCLOSURE, ECS1_01);

		final Monitor collectedEnclosure = getCollectedEnclosure(hostMonitoring);

		assertEquals(expectedEnclosure, collectedEnclosure);
		assertTrue(collectedEnclosure.getParameters().isEmpty());
	}

	@Test
	void testProcessValueTableNullValueTableKey() {
		final IHostMonitoring hostMonitoring = new HostMonitoring();
		final Monitor expectedEnclosure = buildEnclosure(metadata);
		hostMonitoring.addMonitor(expectedEnclosure);
		hostMonitoring.addSourceTable(VALUE_TABLE, sourceTable);

		collectOperation.processValueTable(null, MY_CONNECTOR_NAME, hostMonitoring, parameters, ENCLOSURE, ECS1_01);

		final Monitor collectedEnclosure = getCollectedEnclosure(hostMonitoring);

		assertEquals(expectedEnclosure, collectedEnclosure);
		assertTrue(collectedEnclosure.getParameters().isEmpty());
	}

	@Test
	void testProcessValueTable() {
		final IHostMonitoring hostMonitoring = new HostMonitoring();
		final Monitor enclosure = buildEnclosure(metadata);
		hostMonitoring.addMonitor(enclosure);
		hostMonitoring.addSourceTable(VALUE_TABLE, sourceTable);

		doReturn(engineConfiguration).when(strategyConfig).getEngineConfiguration();

		collectOperation.processValueTable(VALUE_TABLE, MY_CONNECTOR_NAME, hostMonitoring, parameters, ENCLOSURE, ECS1_01);

		final Monitor collectedEnclosure = getCollectedEnclosure(hostMonitoring);

		final Monitor expected = buildExpectedEnclosure();

		assertEquals(expected, collectedEnclosure);
	}

	private static Monitor getCollectedEnclosure(final IHostMonitoring hostMonitoring) {
		return hostMonitoring.selectFromType(MonitorType.ENCLOSURE).get(ENCLOSURE_ID);
	}

	@Test
	void testGetMonitorNoMonitors() {
		final IHostMonitoring hostMonitoring = new HostMonitoring();

		final Optional<Monitor> result = collectOperation.getMonitor(VALUE_TABLE,
				ENCLOSURE,
				hostMonitoring,
				row, VALUETABLE_COLUMN_1);

		assertEquals(Optional.empty(), result);
	}
	
	@Test
	void testGetMonitorCannotExtractDeviceId() {
		final IHostMonitoring hostMonitoring = new HostMonitoring();
		final Map<String, String> metadata = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
		metadata.put(DEVICE_ID, ENCLOSURE_DEVICE_ID);

		final Monitor enclosure = buildEnclosure(metadata);

		hostMonitoring.addMonitor(enclosure);

		final Optional<Monitor> result = collectOperation.getMonitor(VALUE_TABLE,
				ENCLOSURE,
				hostMonitoring,
				Arrays.asList("differentDeviceId", "100"), VALUETABLE_COLUMN_1);

		assertEquals(Optional.empty(), result);
	}

	@Test
	void testGetMonitorNoDeviceColumnTable() {
		final IHostMonitoring hostMonitoring = new HostMonitoring();
		final Map<String, String> metadata = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
		metadata.put(DEVICE_ID, ENCLOSURE_DEVICE_ID);

		final Monitor enclosure = buildEnclosure(metadata);
		hostMonitoring.addMonitor(enclosure);

		final Optional<Monitor> result = collectOperation.getMonitor(VALUE_TABLE,
				ENCLOSURE,
				hostMonitoring,
				row, null);

		assertEquals(Optional.empty(), result);
	}

	@Test
	void testGetMonitorDeviceIdDifferent() {
		final IHostMonitoring hostMonitoring = new HostMonitoring();
		final Map<String, String> metadata = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
		metadata.put(DEVICE_ID, ENCLOSURE_DEVICE_ID);

		final Monitor enclosure = buildEnclosure(metadata);
		hostMonitoring.addMonitor(enclosure);

		// With value table column out of range
		final Optional<Monitor> result = collectOperation.getMonitor(VALUE_TABLE,
				ENCLOSURE,
				hostMonitoring,
				row, VALUETABLE_COLUMN_10);

		assertEquals(Optional.empty(), result);
	}

	@Test
	void testGetMonitorNoMetadata() {
		final IHostMonitoring hostMonitoring = new HostMonitoring();

		final Monitor enclosure = buildEnclosure(Collections.emptyMap());
		enclosure.setMetadata(null);

		hostMonitoring.addMonitor(enclosure);

		final Optional<Monitor> result = collectOperation.getMonitor(VALUE_TABLE,
				ENCLOSURE,
				hostMonitoring,
				row, VALUETABLE_COLUMN_1);

		assertEquals(Optional.empty(), result);
	}

	@Test
	void testGetMonitor() {
		final IHostMonitoring hostMonitoring = new HostMonitoring();
		final Map<String, String> metadata = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
		metadata.put(DEVICE_ID, ENCLOSURE_DEVICE_ID);

		final Monitor expectedEnclosure = buildEnclosure(metadata);
		hostMonitoring.addMonitor(expectedEnclosure);

		final Optional<Monitor> result = collectOperation.getMonitor(VALUE_TABLE,
				ENCLOSURE,
				hostMonitoring,
				row, VALUETABLE_COLUMN_1);

		assertEquals(expectedEnclosure, result.get());
	}

	@Test
	void testCollectMonoInstance() {
		assertDoesNotThrow(() -> collectOperation.collectMonoInstance(Collections.emptyList(), new HostMonitoring(),
				connector, ENCLOSURE, Collections.emptyMap(), ECS1_01));
	}

	private static HardwareMonitor buildHardwareEnclosureMonitor(final CollectType collectType) {

		final SNMPGetTableSource source = SNMPGetTableSource
				.builder()
				.oid(OID1)
				.key(VALUE_TABLE)
				.computes(Collections.singletonList(LeftConcat.builder().column(1).string(HardwareConstants.EMPTY).build()))
				.build();
		final Map<String, String> parameters = Map.of(
				DEVICE_ID, VALUETABLE_COLUMN_1,
				HardwareConstants.STATUS_PARAMETER, VALUETABLE_COLUMN_2, 
				HardwareConstants.STATUS_INFORMATION_PARAMETER, VALUETABLE_COLUMN_3,
				HardwareConstants.INTRUSION_STATUS_PARAMETER, VALUETABLE_COLUMN_4,
				HardwareConstants.ENERGY_USAGE_PARAMETER, VALUETABLE_COLUMN_5);
		final Collect collect = Collect
				.builder()
				.valueTable(VALUE_TABLE)
				.sources(Collections.singletonList(source))
				.parameters(parameters)
				.type(collectType)
				.build();
		final HardwareMonitor hardwareMonitor = HardwareMonitor
				.builder()
				.type(MonitorType.ENCLOSURE)
				.collect(collect)
				.build();
		return hardwareMonitor;
	}

	private static Monitor buildExpectedEnclosure() {
		final Monitor expected = buildEnclosure(metadata);

		final String statusInformation = new StringBuilder()
				.append("status: 0 (Operable)")
				.append("\n")
				.append("intrusionStatus: 0 (No Intrusion Detected)")
				.append("\n")
				.append("energyUsage: 15000.0 Joules")
				.toString();

		final IParameterValue statusParam = StatusParam
				.builder()
				.name(HardwareConstants.STATUS_PARAMETER)
				.collectTime(strategyTime)
				.state(ParameterState.OK)
				.unit(HardwareConstants.STATUS_PARAMETER_UNIT)
				.statusInformation(statusInformation)
				.build();
		expected.addParameter(statusParam);

		final IParameterValue intructionStatusParam = StatusParam
				.builder()
				.name(HardwareConstants.INTRUSION_STATUS_PARAMETER)
				.collectTime(strategyTime)
				.state(ParameterState.OK)
				.unit(HardwareConstants.INTRUSION_STATUS_PARAMETER_UNIT)
				.statusInformation("intrusionStatus: 0 (No Intrusion Detected)")
				.build();
		expected.addParameter(intructionStatusParam);

		final IParameterValue energyUsage = NumberParam
				.builder()
				.name(HardwareConstants.ENERGY_USAGE_PARAMETER)
				.collectTime(strategyTime)
				.unit(HardwareConstants.ENERGY_USAGE_PARAMETER_UNIT)
				.value(15000D)
				.build();
		expected.addParameter(energyUsage);
		return expected;
	}

	private static Monitor buildEnclosure(final Map<String, String> metadata) {
		final Monitor enclosure = Monitor.builder()
				.id(ENCLOSURE_ID)
				.name(ENCLOSURE_NAME)
				.parentId(ECS1_01)
				.targetId(ECS1_01)
				.metadata(metadata)
				.monitorType(MonitorType.ENCLOSURE)
				.extendedType(HardwareConstants.COMPUTER)
				.build();
		return enclosure;
	}
}
