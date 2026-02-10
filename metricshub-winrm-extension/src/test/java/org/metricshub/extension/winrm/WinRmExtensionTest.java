package org.metricshub.extension.winrm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.metricshub.engine.common.helpers.KnownMonitorType.HOST;
import static org.metricshub.extension.winrm.WinRmExtension.WINRM_TEST_NAMESPACE;
import static org.metricshub.extension.winrm.WinRmExtension.WINRM_TEST_QUERY;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mockStatic;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.IntNode;
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
import org.metricshub.engine.configuration.TransportProtocols;
import org.metricshub.engine.connector.model.Connector;
import org.metricshub.engine.connector.model.ConnectorStore;
import org.metricshub.engine.connector.model.common.DeviceKind;
import org.metricshub.engine.connector.model.identity.criterion.CommandLineCriterion;
import org.metricshub.engine.connector.model.identity.criterion.ServiceCriterion;
import org.metricshub.engine.connector.model.identity.criterion.WbemCriterion;
import org.metricshub.engine.connector.model.identity.criterion.WmiCriterion;
import org.metricshub.engine.connector.model.monitor.task.source.CommandLineSource;
import org.metricshub.engine.connector.model.monitor.task.source.EventLogSource;
import org.metricshub.engine.connector.model.monitor.task.source.WbemSource;
import org.metricshub.engine.connector.model.monitor.task.source.WmiSource;
import org.metricshub.engine.strategy.detection.CriterionTestResult;
import org.metricshub.engine.strategy.source.SourceTable;
import org.metricshub.engine.strategy.utils.OsCommandResult;
import org.metricshub.engine.telemetry.HostProperties;
import org.metricshub.engine.telemetry.Monitor;
import org.metricshub.engine.telemetry.TelemetryManager;
import org.metricshub.extension.win.WinCommandService;
import org.metricshub.extension.win.detection.WmiDetectionService;
import org.metricshub.winrm.exceptions.WqlQuerySyntaxException;
import org.metricshub.winrm.service.client.auth.AuthenticationEnum;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WinRmExtensionTest {

	private static final String HOST_NAME = "test-host" + UUID.randomUUID().toString();
	public static final List<List<String>> WQL_SUCCESS_RESPONSE = Arrays.asList(
		Arrays.asList("value1a", "value2a", "value3a"),
		Arrays.asList("value1b", "value2b", "value3b")
	);
	private static final String CONNECTOR_ID = "connector_id";
	private static final char[] PASSWORD = "pwd".toCharArray();
	private static final String USERNAME = "user";
	private static final String WQL = "SELECT Name from Win32_Process WHERE Name = 'MetricsHub'";
	private static final String COMMAND_LINE =
		"naviseccli -User %{USERNAME} -Password %{PASSWORD} -Address %{HOSTNAME} -Scope 1 getagent";
	private static final String NAMESPACE = "namespace";

	@Mock
	private WinRmRequestExecutor winRmRequestExecutorMock;

	@Mock
	private WmiDetectionService wmiDetectionServiceMock;

	@Mock
	private WinCommandService winCommandServiceMock;

	@InjectMocks
	private WinRmExtension winRmExtension;

	private TelemetryManager telemetryManager;

	/**
	 * Creates and returns a TelemetryManager instance with a WinRm configuration.
	 *
	 * @return A TelemetryManager instance configured with a WinRm configuration.
	 */
	private void initWinRm() {
		final Monitor hostMonitor = Monitor.builder().type(HOST.getKey()).isEndpoint(true).build();
		final Map<String, Map<String, Monitor>> monitors = new HashMap<>(
			Map.of(HOST.getKey(), Map.of(HOST_NAME, hostMonitor))
		);

		final WinRmConfiguration winRmConfiguration = WinRmConfiguration
			.builder()
			.password(PASSWORD)
			.username(USERNAME)
			.namespace(NAMESPACE)
			.port(443)
			.protocol(TransportProtocols.HTTP)
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
						.configurations(Map.of(WinRmConfiguration.class, winRmConfiguration))
						.build()
				)
				.connectorStore(connectorStore)
				.strategyTime(System.currentTimeMillis())
				.build();
	}

	@Test
	void testCheckProtocolUp() throws ClientException {
		// Create a telemetry manager using a WinRm HostConfiguration
		initWinRm();

		{
			// Mock a positive WinRm protocol health check response
			doReturn(WQL_SUCCESS_RESPONSE)
				.when(winRmRequestExecutorMock)
				.executeWmi(anyString(), any(WinRmConfiguration.class), eq(WINRM_TEST_QUERY), eq(WINRM_TEST_NAMESPACE));

			// Start the WinRm Health Check
			Optional<Boolean> result = winRmExtension.checkProtocol(telemetryManager);

			assertTrue(result.get());
		}

		{
			// Mock an acceptable WinRm protocol health check exception
			doThrow(new RuntimeException(new WqlQuerySyntaxException("WBEM_E_INVALID_NAMESPACE")))
				.when(winRmRequestExecutorMock)
				.executeWmi(anyString(), any(WinRmConfiguration.class), eq(WINRM_TEST_QUERY), eq(WINRM_TEST_NAMESPACE));

			doCallRealMethod().when(winRmRequestExecutorMock).isAcceptableException(any());

			// Start the WinRm Health Check
			Optional<Boolean> result = winRmExtension.checkProtocol(telemetryManager);

			assertTrue(result.get());
		}
	}

	@Test
	void testCheckProtocolDown() throws ClientException {
		// Create a telemetry manager using a WinRm HostConfiguration
		initWinRm();

		// Mock a null WinRm protocol health check response
		doReturn(null)
			.when(winRmRequestExecutorMock)
			.executeWmi(anyString(), any(WinRmConfiguration.class), eq(WINRM_TEST_QUERY), eq(WINRM_TEST_NAMESPACE));

		// Start the WinRm Health Check
		Optional<Boolean> result = winRmExtension.checkProtocol(telemetryManager);

		assertFalse(result.get());
	}

	@Test
	void testIsValidConfiguration() {
		assertTrue(winRmExtension.isValidConfiguration(WinRmConfiguration.builder().build()));
		assertFalse(
			winRmExtension.isValidConfiguration(
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
			Set.of(CommandLineSource.class, WmiSource.class, EventLogSource.class),
			winRmExtension.getSupportedSources()
		);
	}

	@Test
	void testGetSupportedCriteria() {
		assertEquals(
			Set.of(CommandLineCriterion.class, WmiCriterion.class, ServiceCriterion.class),
			winRmExtension.getSupportedCriteria()
		);
	}

	@Test
	void testGetConfigurationToSourceMapping() {
		assertEquals(
			Map.of(WinRmConfiguration.class, Set.of(WmiSource.class, EventLogSource.class)),
			winRmExtension.getConfigurationToSourceMapping()
		);
	}

	@Test
	void testProcessCriterion() throws Exception {
		final WinRmConfiguration winRmConfiguration = WinRmConfiguration
			.builder()
			.username(USERNAME)
			.password(PASSWORD)
			.namespace(NAMESPACE)
			.port(443)
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
					.configurations(Map.of(WinRmConfiguration.class, winRmConfiguration))
					.build()
			)
			.connectorStore(connectorStore)
			.build();

		{
			final WbemCriterion wbemCriterion = WbemCriterion.builder().query("SELECT Name FROM CIM_StorageSystem").build();
			assertThrows(
				IllegalArgumentException.class,
				() -> winRmExtension.processCriterion(wbemCriterion, CONNECTOR_ID, telemetryManager)
			);
		}
		{
			final String namespace = "root/system";
			telemetryManager.getHostProperties().getConnectorNamespace(CONNECTOR_ID).setAutomaticWmiNamespace(namespace);

			final WmiCriterion wmiCriterion = WmiCriterion.builder().query(WQL).namespace(namespace).build();
			doReturn(CriterionTestResult.success(wmiCriterion, "metricshub"))
				.when(wmiDetectionServiceMock)
				.performDetectionTest(any(), eq(winRmConfiguration), eq(wmiCriterion));
			assertTrue(winRmExtension.processCriterion(wmiCriterion, CONNECTOR_ID, telemetryManager).isSuccess());
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
				.configurations(Map.of(WinRmConfiguration.class, winRmConfiguration))
				.build();
			telemetryManager.setHostConfiguration(hostConfiguration);
			final HostProperties hostProperties = HostProperties.builder().isLocalhost(false).build();

			telemetryManager.setHostProperties(hostProperties);

			doReturn(new OsCommandResult("result", COMMAND_LINE))
				.when(winCommandServiceMock)
				.runOsCommand(commandLineCriterion.getCommandLine(), HOST_NAME, winRmConfiguration, Map.of());
			assertTrue(winRmExtension.processCriterion(commandLineCriterion, CONNECTOR_ID, telemetryManager).isSuccess());
		}
		{
			try (final MockedStatic<LocalOsHandler> mockedLocalOSHandler = mockStatic(LocalOsHandler.class)) {
				mockedLocalOSHandler.when(LocalOsHandler::isWindows).thenReturn(true);

				final ServiceCriterion serviceCriterion = new ServiceCriterion();
				serviceCriterion.setName("metricshub");

				doReturn(CriterionTestResult.success(serviceCriterion, "metricshub;running"))
					.when(wmiDetectionServiceMock)
					.performDetectionTest(any(), any(), any());

				assertTrue(winRmExtension.processCriterion(serviceCriterion, CONNECTOR_ID, telemetryManager).isSuccess());
			}
		}
	}

	@Test
	void testIsSupportedConfigurationType() {
		assertTrue(winRmExtension.isSupportedConfigurationType("winrm"));
		assertFalse(winRmExtension.isSupportedConfigurationType("wbem"));
	}

	@Test
	void testProcessSource() throws Exception {
		final WinRmConfiguration winRmConfiguration = WinRmConfiguration
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
					.configurations(Map.of(WinRmConfiguration.class, winRmConfiguration))
					.build()
			)
			.connectorStore(connectorStore)
			.build();
		{
			final WbemSource wbemSource = WbemSource.builder().query("SELECT Name FROM CIM_StorageSystem").build();
			assertThrows(
				IllegalArgumentException.class,
				() -> winRmExtension.processSource(wbemSource, CONNECTOR_ID, telemetryManager)
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
				.runOsCommand(COMMAND_LINE, HOST_NAME, winRmConfiguration, Map.of());

			final List<List<String>> expected = List.of(List.of("16384 MB"), List.of("8192 MB"));
			assertEquals(
				SourceTable.builder().table(expected).rawData("16384 MB\n8192 MB").build(),
				winRmExtension.processSource(commandLineSource, CONNECTOR_ID, telemetryManager)
			);
		}
		{
			final List<List<String>> expected = Arrays.asList(
				Arrays.asList("1.1", "0|4587"),
				Arrays.asList("1.2", "2|4587"),
				Arrays.asList("1.3", "1|4587")
			);
			doReturn(expected)
				.when(winRmRequestExecutorMock)
				.executeWmi(HOST_NAME, winRmConfiguration, WQL, MetricsHubConstants.WMI_DEFAULT_NAMESPACE);
			final WmiSource wmiSource = WmiSource.builder().query(WQL).build();
			assertEquals(
				SourceTable.builder().table(expected).build(),
				winRmExtension.processSource(wmiSource, CONNECTOR_ID, telemetryManager)
			);
		}
	}

	@Test
	void testBuildConfiguration() throws InvalidConfigurationException {
		final ObjectNode configuration = JsonNodeFactory.instance.objectNode();
		ArrayNode authenticationsList = JsonNodeFactory.instance.arrayNode();
		authenticationsList.add("kerberos");
		authenticationsList.add("ntlm");
		configuration.set("username", new TextNode(USERNAME));
		configuration.set("password", new TextNode(new String(PASSWORD)));
		configuration.set("timeout", new TextNode("2m"));
		configuration.set("port", new IntNode(443));
		configuration.set("protocol", new TextNode("http"));
		configuration.set("authentications", authenticationsList);
		final String namespace = "root/system";
		configuration.set("namespace", new TextNode(namespace));

		assertEquals(
			WinRmConfiguration
				.builder()
				.username(USERNAME)
				.password(PASSWORD)
				.port(443)
				.protocol(TransportProtocols.HTTP)
				.authentications(List.of(AuthenticationEnum.KERBEROS, AuthenticationEnum.NTLM))
				.namespace(namespace)
				.timeout(120L)
				.build(),
			winRmExtension.buildConfiguration("winrm", configuration, value -> value)
		);
		assertEquals(
			WinRmConfiguration
				.builder()
				.username(USERNAME)
				.password(PASSWORD)
				.port(443)
				.protocol(TransportProtocols.HTTP)
				.authentications(List.of(AuthenticationEnum.KERBEROS, AuthenticationEnum.NTLM))
				.namespace(namespace)
				.timeout(120L)
				.build(),
			winRmExtension.buildConfiguration("winrm", configuration, null)
		);
		configuration.set("namespace", new TextNode(null));
		final WinRmConfiguration winRmConfiguration = (WinRmConfiguration) winRmExtension.buildConfiguration(
			"winrm",
			configuration,
			null
		);
		assertNull(winRmConfiguration.getNamespace());
	}

	@Test
	void testExecuteQuery() throws Exception {
		initWinRm();

		doReturn(WQL_SUCCESS_RESPONSE)
			.when(winRmRequestExecutorMock)
			.executeWmi(anyString(), any(WinRmConfiguration.class), anyString(), anyString());

		final ObjectNode queryNode = JsonNodeFactory.instance.objectNode();
		queryNode.set("query", new TextNode(WQL));
		queryNode.set("queryType", new TextNode("wmi"));
		WinRmConfiguration configuration = WinRmConfiguration
			.builder()
			.hostname(HOST_NAME)
			.username(USERNAME)
			.password(PASSWORD)
			.timeout(120L)
			.namespace(WINRM_TEST_NAMESPACE)
			.build();
		final String result = winRmExtension.executeQuery(configuration, queryNode);
		final String expectedResult = TextTableHelper.generateTextTable(
			StringHelper.extractColumns(WQL),
			WQL_SUCCESS_RESPONSE
		);
		assertEquals(expectedResult, result);
	}

	@Test
	void testExecuteQueryThrow() throws Exception {
		initWinRm();

		doThrow(ClientException.class)
			.when(winRmRequestExecutorMock)
			.executeWmi(anyString(), any(WinRmConfiguration.class), anyString(), anyString());

		final ObjectNode queryNode = JsonNodeFactory.instance.objectNode();
		queryNode.set("query", new TextNode(WQL));
		queryNode.set("queryType", new TextNode("wmi"));
		WinRmConfiguration configuration = WinRmConfiguration
			.builder()
			.hostname(HOST_NAME)
			.username(USERNAME)
			.password(PASSWORD)
			.timeout(120L)
			.namespace(WINRM_TEST_NAMESPACE)
			.build();
		assertNull(winRmExtension.executeQuery(configuration, queryNode), "Expected null response");
	}
}
