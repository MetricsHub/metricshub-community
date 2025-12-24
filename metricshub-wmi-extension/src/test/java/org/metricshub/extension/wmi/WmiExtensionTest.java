package org.metricshub.extension.wmi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.metricshub.engine.common.helpers.KnownMonitorType.HOST;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mockStatic;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.metricshub.engine.common.exception.ClientException;
import org.metricshub.engine.common.exception.InvalidConfigurationException;
import org.metricshub.engine.common.helpers.LocalOsHandler;
import org.metricshub.engine.common.helpers.MetricsHubConstants;
import org.metricshub.engine.common.helpers.StringHelper;
import org.metricshub.engine.common.helpers.TextTableHelper;
import org.metricshub.engine.configuration.HostConfiguration;
import org.metricshub.engine.configuration.IConfiguration;
import org.metricshub.engine.connector.model.Connector;
import org.metricshub.engine.connector.model.ConnectorStore;
import org.metricshub.engine.connector.model.common.DeviceKind;
import org.metricshub.engine.connector.model.identity.criterion.CommandLineCriterion;
import org.metricshub.engine.connector.model.identity.criterion.IpmiCriterion;
import org.metricshub.engine.connector.model.identity.criterion.ProcessCriterion;
import org.metricshub.engine.connector.model.identity.criterion.ServiceCriterion;
import org.metricshub.engine.connector.model.identity.criterion.WbemCriterion;
import org.metricshub.engine.connector.model.identity.criterion.WmiCriterion;
import org.metricshub.engine.connector.model.monitor.task.source.CommandLineSource;
import org.metricshub.engine.connector.model.monitor.task.source.IpmiSource;
import org.metricshub.engine.connector.model.monitor.task.source.WbemSource;
import org.metricshub.engine.connector.model.monitor.task.source.WmiSource;
import org.metricshub.engine.strategy.detection.CriterionTestResult;
import org.metricshub.engine.strategy.source.SourceTable;
import org.metricshub.engine.strategy.utils.OsCommandResult;
import org.metricshub.engine.telemetry.HostProperties;
import org.metricshub.engine.telemetry.Monitor;
import org.metricshub.engine.telemetry.TelemetryManager;
import org.metricshub.extension.win.IWinConfiguration;
import org.metricshub.extension.win.WinCommandService;
import org.metricshub.extension.win.detection.WmiDetectionService;
import org.metricshub.wmi.exceptions.WmiComException;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WmiExtensionTest {

	private static final String WMI = "wmi";
	private static final String HOST_NAME = "test-host" + UUID.randomUUID().toString();
	private static final List<List<String>> WQL_SUCCESS_RESPONSE = List.of(List.of("success"));
	private static final String CONNECTOR_ID = "connector_id";
	private static final char[] PASSWORD = "pwd".toCharArray();
	private static final String USERNAME = "user";
	private static final String WQL = "SELECT Name from Win32_Process WHERE Name = 'MetricsHub'";
	private static final String COMMAND_LINE =
		"naviseccli -User %{USERNAME} -Password %{PASSWORD} -Address %{HOSTNAME} -Scope 1 getagent";
	public static final List<List<String>> EXECUTE_WMI_RESULT = Arrays.asList(
		Arrays.asList("value1a", "value2a", "value3a"),
		Arrays.asList("value1b", "value2b", "value3b")
	);
	private static final String WMI_TEST_NAMESPACE = "namespace";

	@Mock
	private WmiRequestExecutor wmiRequestExecutorMock;

	@Mock
	private WmiDetectionService wmiDetectionServiceMock;

	@Mock
	private WinCommandService winCommandServiceMock;

	@InjectMocks
	private WmiExtension wmiExtension;

	private TelemetryManager telemetryManager;

	/**
	 * Creates and returns a TelemetryManager instance with an WMI configuration.
	 *
	 * @return A TelemetryManager instance configured with an WMI configuration.
	 */
	private void initWmi() {
		final Monitor hostMonitor = Monitor.builder().type(HOST.getKey()).isEndpoint(true).build();
		final Map<String, Map<String, Monitor>> monitors = new HashMap<>(
			Map.of(HOST.getKey(), Map.of(HOST_NAME, hostMonitor))
		);

		final WmiConfiguration wmiConfiguration = WmiConfiguration
			.builder()
			.password("pass".toCharArray())
			.username("user")
			.timeout(120L)
			.build();

		final Connector connector = Connector.builder().build();

		final Map<String, Connector> store = Map.of(CONNECTOR_ID, connector);

		final ConnectorStore connectorStore = new ConnectorStore();
		connectorStore.setStore(store);

		telemetryManager =
			TelemetryManager
				.builder()
				.monitors(monitors)
				.hostConfiguration(
					HostConfiguration
						.builder()
						.hostname(HOST_NAME)
						.hostId(HOST_NAME)
						.hostType(DeviceKind.WINDOWS)
						.configurations(Map.of(WmiConfiguration.class, wmiConfiguration))
						.build()
				)
				.connectorStore(connectorStore)
				.strategyTime(System.currentTimeMillis())
				.build();
	}

	@Test
	void testCheckProtocolUp() throws ClientException {
		// Create a telemetry manager using a WMI HostConfiguration
		initWmi();

		{
			// Mock a positive WMI protocol health check response
			doReturn(WQL_SUCCESS_RESPONSE)
				.when(wmiRequestExecutorMock)
				.executeWmi(
					anyString(),
					any(WmiConfiguration.class),
					eq(WmiExtension.WMI_TEST_QUERY),
					eq(WmiExtension.WMI_TEST_NAMESPACE)
				);

			// Start the WMI Health Check strategy
			Optional<Boolean> result = wmiExtension.checkProtocol(telemetryManager);

			assertTrue(result.get());
		}

		{
			// Mock an acceptable WMI protocol health check exception
			doThrow(new RuntimeException(new WmiComException("WBEM_E_INVALID_NAMESPACE")))
				.when(wmiRequestExecutorMock)
				.executeWmi(
					anyString(),
					any(WmiConfiguration.class),
					eq(WmiExtension.WMI_TEST_QUERY),
					eq(WmiExtension.WMI_TEST_NAMESPACE)
				);

			doCallRealMethod().when(wmiRequestExecutorMock).isAcceptableException(any());

			// Start the WMI Health Check
			Optional<Boolean> result = wmiExtension.checkProtocol(telemetryManager);

			assertTrue(result.get());
		}
	}

	@Test
	void testCheckProtocolDown() throws ClientException {
		// Create a telemetry manager using a WMI HostConfiguration
		initWmi();

		// Mock a null WMI protocol health check response
		doReturn(null)
			.when(wmiRequestExecutorMock)
			.executeWmi(
				anyString(),
				any(WmiConfiguration.class),
				eq(WmiExtension.WMI_TEST_QUERY),
				eq(WmiExtension.WMI_TEST_NAMESPACE)
			);

		// Start the WMI Health Check
		Optional<Boolean> result = wmiExtension.checkProtocol(telemetryManager);

		assertFalse(result.get());
	}

	@Test
	void testIsValidConfiguration() {
		assertTrue(wmiExtension.isValidConfiguration(WmiConfiguration.builder().build()));
		assertFalse(
			wmiExtension.isValidConfiguration(
				new IConfiguration() {
					@Override
					public void validateConfiguration(String resourceKey) throws InvalidConfigurationException {}

					@Override
					public String getHostname() {
						return null;
					}

					@Override
					public void setHostname(String hostname) {}

					@Override
					public IConfiguration copy() {
						return null;
					}

					@Override
					public void setTimeout(Long timeout) {}

					@Override
					public String getProperty(String property) {
						return null;
					}

					@Override
					public boolean isCorrespondingProtocol(String protocol) {
						return false;
					}
				}
			)
		);
	}

	@Test
	void testGetSupportedSources() {
		assertEquals(
			Set.of(IpmiSource.class, CommandLineSource.class, WmiSource.class),
			wmiExtension.getSupportedSources()
		);
	}

	@Test
	void testGetSupportedCriteria() {
		assertEquals(
			Set.of(IpmiCriterion.class, CommandLineCriterion.class, WmiCriterion.class, ServiceCriterion.class),
			wmiExtension.getSupportedCriteria()
		);
	}

	@Test
	void testGetConfigurationToSourceMapping() {
		assertEquals(
			Map.of(WmiConfiguration.class, Set.of(WmiSource.class)),
			wmiExtension.getConfigurationToSourceMapping()
		);
	}

	@Test
	void testProcessCriterion() throws Exception {
		final WmiConfiguration wmiConfiguration = WmiConfiguration
			.builder()
			.username(USERNAME)
			.password(PASSWORD)
			.timeout(15L)
			.build();

		final Connector connector = Connector.builder().build();

		final Map<String, Connector> store = Map.of(CONNECTOR_ID, connector);

		final ConnectorStore connectorStore = new ConnectorStore();
		connectorStore.setStore(store);

		final TelemetryManager telemetryManager = TelemetryManager
			.builder()
			.hostConfiguration(
				HostConfiguration
					.builder()
					.hostname(HOST_NAME)
					.hostId(HOST_NAME)
					.hostType(DeviceKind.WINDOWS)
					.configurations(Map.of(WmiConfiguration.class, wmiConfiguration))
					.build()
			)
			.connectorStore(connectorStore)
			.build();

		{
			final WbemCriterion wbemCriterion = WbemCriterion.builder().query("SELECT Name FROM CIM_StorageSystem").build();
			assertThrows(
				IllegalArgumentException.class,
				() -> wmiExtension.processCriterion(wbemCriterion, CONNECTOR_ID, telemetryManager)
			);
		}
		{
			final String namespace = "root/system";
			telemetryManager.getHostProperties().getConnectorNamespace(CONNECTOR_ID).setAutomaticWmiNamespace(namespace);

			final WmiCriterion wmiCriterion = WmiCriterion.builder().query(WQL).namespace(namespace).build();
			doReturn(CriterionTestResult.success(wmiCriterion, "metricshub"))
				.when(wmiDetectionServiceMock)
				.performDetectionTest(any(), eq(wmiConfiguration), eq(wmiCriterion));
			assertTrue(wmiExtension.processCriterion(wmiCriterion, CONNECTOR_ID, telemetryManager).isSuccess());
		}
		{
			final CommandLineCriterion commandLineCriterion = new CommandLineCriterion();
			commandLineCriterion.setCommandLine(COMMAND_LINE);
			commandLineCriterion.setExpectedResult("result");
			commandLineCriterion.setExecuteLocally(false);
			commandLineCriterion.setErrorMessage("Unable to connect using Navisphere");
			final HostConfiguration hostConfiguration = HostConfiguration
				.builder()
				.hostId(HOST_NAME)
				.hostname(HOST_NAME)
				.hostType(DeviceKind.WINDOWS)
				.configurations(Map.of(WmiConfiguration.class, wmiConfiguration))
				.build();
			telemetryManager.setHostConfiguration(hostConfiguration);
			final HostProperties hostProperties = HostProperties.builder().isLocalhost(false).build();

			telemetryManager.setHostProperties(hostProperties);

			doReturn(new OsCommandResult("result", COMMAND_LINE))
				.when(winCommandServiceMock)
				.runOsCommand(commandLineCriterion.getCommandLine(), HOST_NAME, wmiConfiguration, Map.of());
			assertTrue(wmiExtension.processCriterion(commandLineCriterion, CONNECTOR_ID, telemetryManager).isSuccess());
		}
		{
			final IpmiCriterion ipmiCriterion = IpmiCriterion.builder().forceSerialization(true).build();

			// Mock performDetectionTest
			doReturn(CriterionTestResult.success(ipmiCriterion, "success"))
				.when(wmiDetectionServiceMock)
				.performDetectionTest(any(), any(), any());

			assertTrue(wmiExtension.processCriterion(ipmiCriterion, CONNECTOR_ID, telemetryManager).isSuccess());
		}
		{
			try (final MockedStatic<LocalOsHandler> mockedLocalOSHandler = mockStatic(LocalOsHandler.class)) {
				mockedLocalOSHandler.when(LocalOsHandler::isWindows).thenReturn(true);

				final ServiceCriterion serviceCriterion = new ServiceCriterion();
				serviceCriterion.setName("metricshub");

				doReturn(CriterionTestResult.success(serviceCriterion, "metricshub;running"))
					.when(wmiDetectionServiceMock)
					.performDetectionTest(any(), any(), any());

				assertTrue(wmiExtension.processCriterion(serviceCriterion, CONNECTOR_ID, telemetryManager).isSuccess());
			}
		}
		{
			final ProcessCriterion processCriterion = new ProcessCriterion();
			processCriterion.setCommandLine("MBM[5-9]\\.exe");

			doReturn(CriterionTestResult.success(processCriterion, "success"))
				.when(wmiDetectionServiceMock)
				.performDetectionTest(eq(MetricsHubConstants.LOCALHOST), any(IWinConfiguration.class), any(WmiCriterion.class));
			assertTrue(wmiExtension.processCriterion(processCriterion, CONNECTOR_ID, telemetryManager).isSuccess());
		}
	}

	@Test
	void testIsSupportedConfigurationType() {
		assertTrue(wmiExtension.isSupportedConfigurationType(WMI));
		assertFalse(wmiExtension.isSupportedConfigurationType("wbem"));
	}

	@Test
	void testProcessSource() throws Exception {
		final WmiConfiguration wmiConfiguration = WmiConfiguration
			.builder()
			.username(USERNAME)
			.password(PASSWORD)
			.timeout(15L)
			.build();

		final Connector connector = Connector.builder().build();

		final Map<String, Connector> store = Map.of(CONNECTOR_ID, connector);

		final ConnectorStore connectorStore = new ConnectorStore();
		connectorStore.setStore(store);

		final TelemetryManager telemetryManager = TelemetryManager
			.builder()
			.hostConfiguration(
				HostConfiguration
					.builder()
					.hostname(HOST_NAME)
					.hostId(HOST_NAME)
					.hostType(DeviceKind.WINDOWS)
					.configurations(Map.of(WmiConfiguration.class, wmiConfiguration))
					.build()
			)
			.connectorStore(connectorStore)
			.build();
		{
			final WbemSource wbemSource = WbemSource.builder().query("SELECT Name FROM CIM_StorageSystem").build();
			assertThrows(
				IllegalArgumentException.class,
				() -> wmiExtension.processSource(wbemSource, CONNECTOR_ID, telemetryManager)
			);
		}
		{
			final CommandLineSource commandLineSource = new CommandLineSource();
			commandLineSource.setCommandLine(COMMAND_LINE);
			commandLineSource.setKeep("Total\\|Available");
			commandLineSource.setSeparators(":");
			commandLineSource.setSelectColumns("2");
			commandLineSource.setExecuteLocally(false);

			final String expectedCommandLineResult =
				"""
				Total Physical Memory:16384 MB
				Available Physical Memory:8192 MB
				""";
			doReturn(new OsCommandResult(expectedCommandLineResult, COMMAND_LINE))
				.when(winCommandServiceMock)
				.runOsCommand(COMMAND_LINE, HOST_NAME, wmiConfiguration, Map.of());

			final List<List<String>> expected = List.of(List.of("16384 MB"), List.of("8192 MB"));
			assertEquals(
				SourceTable.builder().table(expected).rawData("16384 MB\n8192 MB").build(),
				wmiExtension.processSource(commandLineSource, CONNECTOR_ID, telemetryManager)
			);
		}
		{
			final List<List<String>> wmiResult1 = Arrays.asList(Arrays.asList("IdentifyingNumber", "Name", "Vendor"));
			doReturn(wmiResult1)
				.when(wmiRequestExecutorMock)
				.executeWmi(
					HOST_NAME,
					wmiConfiguration,
					"SELECT IdentifyingNumber,Name,Vendor FROM Win32_ComputerSystemProduct",
					"root/cimv2"
				);

			final List<List<String>> wmiResult2 = Arrays.asList(
				Arrays.asList("2", "20", "sensorName(sensorId):description for deviceId", "10", "15", "2", "0", "30", "25")
			);
			doReturn(wmiResult2)
				.when(wmiRequestExecutorMock)
				.executeWmi(
					HOST_NAME,
					wmiConfiguration,
					"SELECT BaseUnits,CurrentReading,Description,LowerThresholdCritical,LowerThresholdNonCritical,SensorType,UnitModifier,UpperThresholdCritical,UpperThresholdNonCritical FROM NumericSensor",
					"root/hardware"
				);

			final List<List<String>> wmiResult3 = Arrays.asList(
				Arrays.asList("state", "sensorName(sensorId):description for deviceType deviceId")
			);
			doReturn(wmiResult3)
				.when(wmiRequestExecutorMock)
				.executeWmi(HOST_NAME, wmiConfiguration, "SELECT CurrentState,Description FROM Sensor", "root/hardware");

			final List<List<String>> expected = Arrays.asList(
				Arrays.asList("FRU", "Vendor", "Name", "IdentifyingNumber"),
				Arrays.asList("Temperature", "sensorId", "sensorName", "deviceId", "20.0", "25.0", "30.0"),
				Arrays.asList("deviceType", "deviceId", "deviceType deviceId", "", "", "", "sensorName=state")
			);
			final SourceTable result = wmiExtension.processSource(new IpmiSource(), CONNECTOR_ID, telemetryManager);
			assertEquals(SourceTable.builder().table(expected).build(), result);
		}
		{
			final List<List<String>> expected = Arrays.asList(
				Arrays.asList("1.1", "0|4587"),
				Arrays.asList("1.2", "2|4587"),
				Arrays.asList("1.3", "1|4587")
			);
			doReturn(expected)
				.when(wmiRequestExecutorMock)
				.executeWmi(HOST_NAME, wmiConfiguration, WQL, MetricsHubConstants.WMI_DEFAULT_NAMESPACE);
			final WmiSource wmiSource = WmiSource.builder().query(WQL).build();
			assertEquals(
				SourceTable.builder().table(expected).build(),
				wmiExtension.processSource(wmiSource, CONNECTOR_ID, telemetryManager)
			);
		}
	}

	@Test
	void testBuildConfiguration() throws InvalidConfigurationException {
		final ObjectNode configuration = JsonNodeFactory.instance.objectNode();
		configuration.set("username", new TextNode(USERNAME));
		configuration.set("password", new TextNode(new String(PASSWORD)));
		configuration.set("timeout", new TextNode("2m"));
		final String namespace = "root/system";
		configuration.set("namespace", new TextNode(namespace));

		assertEquals(
			WmiConfiguration.builder().username(USERNAME).password(PASSWORD).namespace(namespace).timeout(120L).build(),
			wmiExtension.buildConfiguration(WMI, configuration, value -> value)
		);
		assertEquals(
			WmiConfiguration.builder().username(USERNAME).password(PASSWORD).namespace(namespace).timeout(120L).build(),
			wmiExtension.buildConfiguration(WMI, configuration, null)
		);
		configuration.set("namespace", new TextNode(null));
		final WmiConfiguration wmiConfiguration = (WmiConfiguration) wmiExtension.buildConfiguration(
			WMI,
			configuration,
			null
		);
		assertNull(wmiConfiguration.getNamespace());
	}

	@Test
	void testGetIdentifier() {
		String identifier = wmiExtension.getIdentifier();

		assertEquals(WMI, identifier);
	}

	@Test
	void testExecuteQuery() throws Exception {
		initWmi();

		doReturn(EXECUTE_WMI_RESULT)
			.when(wmiRequestExecutorMock)
			.executeWmi(anyString(), any(WmiConfiguration.class), anyString(), anyString());

		final ObjectNode queryNode = JsonNodeFactory.instance.objectNode();
		queryNode.set("query", new TextNode(WQL));
		queryNode.set("queryType", new TextNode(WMI));
		WmiConfiguration configuration = WmiConfiguration
			.builder()
			.hostname(HOST_NAME)
			.username(USERNAME)
			.password(PASSWORD)
			.timeout(120L)
			.namespace(WMI_TEST_NAMESPACE)
			.build();
		final String result = wmiExtension.executeQuery(configuration, queryNode);
		final String expectedResult = TextTableHelper.generateTextTable(
			StringHelper.extractColumns(WQL),
			EXECUTE_WMI_RESULT
		);
		assertEquals(expectedResult, result);
	}

	@Test
	void testExecuteQueryReturnsNull() throws Exception {
		initWmi();

		doThrow(ClientException.class)
			.when(wmiRequestExecutorMock)
			.executeWmi(anyString(), any(WmiConfiguration.class), anyString(), anyString());

		final ObjectNode queryNode = JsonNodeFactory.instance.objectNode();
		queryNode.set("query", new TextNode(WQL));
		queryNode.set("queryType", new TextNode("wmi"));
		WmiConfiguration configuration = WmiConfiguration
			.builder()
			.hostname(HOST_NAME)
			.username(USERNAME)
			.password(PASSWORD)
			.timeout(120L)
			.namespace(WMI_TEST_NAMESPACE)
			.build();
		assertNull(wmiExtension.executeQuery(configuration, queryNode), "Expected null response");
	}
}
