package org.metricshub.extension.wbem;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mockStatic;
import static org.sentrysoftware.metricshub.engine.common.helpers.KnownMonitorType.HOST;
import static org.sentrysoftware.metricshub.engine.common.helpers.MetricsHubConstants.AUTOMATIC_NAMESPACE;

import com.fasterxml.jackson.databind.node.BooleanNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.metricshub.wbem.javax.wbem.WBEMException;
import org.mockito.InjectMocks;
import org.mockito.MockedStatic;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.sentrysoftware.metricshub.engine.common.exception.ClientException;
import org.sentrysoftware.metricshub.engine.common.exception.InvalidConfigurationException;
import org.sentrysoftware.metricshub.engine.common.helpers.StringHelper;
import org.sentrysoftware.metricshub.engine.common.helpers.TextTableHelper;
import org.sentrysoftware.metricshub.engine.common.helpers.ThreadHelper;
import org.sentrysoftware.metricshub.engine.configuration.HostConfiguration;
import org.sentrysoftware.metricshub.engine.configuration.IConfiguration;
import org.sentrysoftware.metricshub.engine.configuration.TransportProtocols;
import org.sentrysoftware.metricshub.engine.connector.model.common.DeviceKind;
import org.sentrysoftware.metricshub.engine.connector.model.identity.criterion.WbemCriterion;
import org.sentrysoftware.metricshub.engine.connector.model.monitor.task.source.WbemSource;
import org.sentrysoftware.metricshub.engine.strategy.detection.CriterionTestResult;
import org.sentrysoftware.metricshub.engine.strategy.source.SourceTable;
import org.sentrysoftware.metricshub.engine.telemetry.HostProperties;
import org.sentrysoftware.metricshub.engine.telemetry.Monitor;
import org.sentrysoftware.metricshub.engine.telemetry.TelemetryManager;

@ExtendWith(MockitoExtension.class)
class WbemExtensionTest {

	private static final String CONNECTOR_ID = "connector";
	private static final String HOST_NAME = "test-host" + UUID.randomUUID();
	private static final String USERNAME = "testUser";
	private static final String PASSWORD = "testPassword";
	private static final String WBEM_CRITERION_TYPE = "wbem";
	private static final String WBEM_TEST_NAMESPACE = "namespace";
	public static final String WBEM_TEST_QUERY = "SELECT Name, SerialNumber FROM CIM_NameSpace";

	public static final List<List<String>> EXECUTE_WBEM_RESULT = Arrays.asList(
		Arrays.asList("value1a", "value2a", "value3a"),
		Arrays.asList("value1b", "value2b", "value3b")
	);

	@Spy
	private WbemRequestExecutor wbemRequestExecutorSpy;

	@InjectMocks
	private WbemExtension wbemExtension;

	private TelemetryManager telemetryManager;

	private void initWbem() {
		final Monitor hostMonitor = Monitor.builder().type(HOST.getKey()).isEndpoint(true).build();

		final Map<String, Map<String, Monitor>> monitors = new HashMap<>(
			Map.of(HOST.getKey(), Map.of(HOST_NAME, hostMonitor))
		);

		final WbemConfiguration wbemConfiguration = WbemConfiguration
			.builder()
			.username(USERNAME)
			.password(PASSWORD.toCharArray())
			.timeout(120L)
			.build();

		telemetryManager =
			TelemetryManager
				.builder()
				.monitors(monitors)
				.hostConfiguration(
					HostConfiguration
						.builder()
						.hostname(HOST_NAME)
						.hostId(HOST_NAME)
						.hostType(DeviceKind.OOB)
						.configurations(Map.of(WbemConfiguration.class, wbemConfiguration))
						.build()
				)
				.build();
	}

	@Test
	void testCheckProtocolUp() throws ClientException {
		{
			initWbem();

			// Mock a positive WBEM protocol health check response
			doReturn(EXECUTE_WBEM_RESULT)
				.when(wbemRequestExecutorSpy)
				.doWbemQuery(anyString(), any(WbemConfiguration.class), anyString(), anyString());

			// Start the WBEM Health Check strategy
			Optional<Boolean> result = wbemExtension.checkProtocol(telemetryManager);

			assertTrue(result.get());
		}

		{
			initWbem();

			doThrow(new RuntimeException(new WBEMException(WBEMException.CIM_ERR_INVALID_NAMESPACE)))
				.when(wbemRequestExecutorSpy)
				.doWbemQuery(anyString(), any(WbemConfiguration.class), anyString(), anyString());

			doCallRealMethod().when(wbemRequestExecutorSpy).isAcceptableException(any());

			// Start the WBEM Health Check strategy
			Optional<Boolean> result = wbemExtension.checkProtocol(telemetryManager);

			assertTrue(result.get());
		}
	}

	@Test
	void testCheckWbemDownHealth() throws ClientException {
		initWbem();

		// Mock null WBEM protocol health check response
		doReturn(null)
			.when(wbemRequestExecutorSpy)
			.doWbemQuery(anyString(), any(WbemConfiguration.class), anyString(), anyString());

		// Start the WBEM Health Check strategy
		Optional<Boolean> result = wbemExtension.checkProtocol(telemetryManager);

		assertFalse(result.get());
	}

	@Test
	void testIsValidConfiguration() {
		assertTrue(wbemExtension.isValidConfiguration(WbemConfiguration.builder().build()));
		assertFalse(
			wbemExtension.isValidConfiguration(
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
				}
			)
		);
	}

	@Test
	void testGetSupportedSources() {
		assertFalse(wbemExtension.getSupportedSources().isEmpty());
		assertTrue(wbemExtension.getSupportedSources().contains(WbemSource.class));
	}

	@Test
	void testGetSupportedCriteria() {
		assertFalse(wbemExtension.getSupportedCriteria().isEmpty());
		assertTrue(wbemExtension.getSupportedCriteria().contains(WbemCriterion.class));
	}

	@Test
	void testGetConfigurationToSourceMapping() {
		assertFalse(wbemExtension.getConfigurationToSourceMapping().isEmpty());
	}

	@Test
	void testIsSupportedConfigurationType() {
		assertTrue(wbemExtension.isSupportedConfigurationType("wbem"));
		assertFalse(wbemExtension.isSupportedConfigurationType("snmp"));
	}

	@Test
	void testBuildConfiguration() throws InvalidConfigurationException {
		final ObjectNode configuration = JsonNodeFactory.instance.objectNode();
		configuration.set("username", new TextNode(USERNAME));
		configuration.set("password", new TextNode(PASSWORD));
		configuration.set("timeout", new TextNode("120"));
		configuration.set("port", new TextNode("5989"));
		configuration.set("vcenter", new TextNode("vcenter"));
		configuration.set("skipAuth", BooleanNode.valueOf(false));

		assertEquals(
			WbemConfiguration
				.builder()
				.username(USERNAME)
				.password(PASSWORD.toCharArray())
				.port(5989)
				.vCenter("vcenter")
				.timeout(120L)
				.build(),
			wbemExtension.buildConfiguration(WBEM_CRITERION_TYPE, configuration, value -> value)
		);

		assertEquals(
			WbemConfiguration
				.builder()
				.username(USERNAME)
				.password(PASSWORD.toCharArray())
				.port(5989)
				.vCenter("vcenter")
				.timeout(120L)
				.build(),
			wbemExtension.buildConfiguration(WBEM_CRITERION_TYPE, configuration, null)
		);
	}

	@Test
	void testProcessCriterionConfigurationNullTest() {
		initWbem();

		telemetryManager.getHostConfiguration().setConfigurations(Map.of());

		final WbemCriterion wbemCriterion = WbemCriterion
			.builder()
			.type(WBEM_CRITERION_TYPE)
			.query(WBEM_TEST_QUERY)
			.build();

		assertFalse(wbemExtension.processCriterion(wbemCriterion, CONNECTOR_ID, telemetryManager).isSuccess());
	}

	@Test
	void testProcessCriterionNullResultTest() throws ClientException {
		initWbem();

		final WbemCriterion wbemCriterion = WbemCriterion
			.builder()
			.type(WBEM_CRITERION_TYPE)
			.query(WBEM_TEST_QUERY)
			.build();

		doReturn(null)
			.when(wbemRequestExecutorSpy)
			.doWbemQuery(anyString(), any(WbemConfiguration.class), anyString(), anyString());

		final CriterionTestResult criterionTestResult = wbemExtension.processCriterion(
			wbemCriterion,
			CONNECTOR_ID,
			telemetryManager
		);

		assertEquals("No result.", criterionTestResult.getResult());
		assertFalse(criterionTestResult.isSuccess());
		assertTrue(criterionTestResult.getMessage().contains("No result."));
		assertNull(criterionTestResult.getException());
	}

	@Test
	void testProcessCriterionOk() throws ClientException {
		final Monitor hostMonitor = Monitor.builder().type(HOST.getKey()).isEndpoint(true).build();

		final Map<String, Map<String, Monitor>> monitors = new HashMap<>(
			Map.of(HOST.getKey(), Map.of(HOST_NAME, hostMonitor))
		);

		final WbemConfiguration wbemConfiguration = WbemConfiguration
			.builder()
			.username(USERNAME)
			.password(PASSWORD.toCharArray())
			.timeout(120L)
			.build();

		telemetryManager =
			TelemetryManager
				.builder()
				.monitors(monitors)
				.hostConfiguration(
					HostConfiguration
						.builder()
						.hostname(HOST_NAME)
						.hostId(HOST_NAME)
						.hostType(DeviceKind.OOB)
						.configurations(Map.of(WbemConfiguration.class, wbemConfiguration))
						.build()
				)
				.build();

		final WbemCriterion wbemCriterion = WbemCriterion
			.builder()
			.type(WBEM_CRITERION_TYPE)
			.namespace("namespace")
			.query(WBEM_TEST_QUERY)
			.build();

		doReturn(EXECUTE_WBEM_RESULT)
			.when(wbemRequestExecutorSpy)
			.doWbemQuery(anyString(), any(WbemConfiguration.class), anyString(), anyString());

		final String message = "WbemCriterion test succeeded:";
		final CriterionTestResult criterionTestResult = wbemExtension.processCriterion(
			wbemCriterion,
			CONNECTOR_ID,
			telemetryManager
		);

		assertEquals(SourceTable.tableToCsv(EXECUTE_WBEM_RESULT, ";", false), criterionTestResult.getResult());
		assertTrue(criterionTestResult.isSuccess());
		assertTrue(criterionTestResult.getMessage().contains(message));
		assertNull(criterionTestResult.getException());
	}

	@Test
	void testProcessCriterionOkEmptyPossibleNamespaces() throws ClientException {
		final Monitor hostMonitor = Monitor.builder().type(HOST.getKey()).isEndpoint(true).build();

		final Map<String, Map<String, Monitor>> monitors = new HashMap<>(
			Map.of(HOST.getKey(), Map.of(HOST_NAME, hostMonitor))
		);

		final WbemConfiguration wbemConfiguration = WbemConfiguration
			.builder()
			.username(USERNAME)
			.password(PASSWORD.toCharArray())
			.timeout(120L)
			.build();

		telemetryManager =
			TelemetryManager
				.builder()
				.monitors(monitors)
				.hostProperties(HostProperties.builder().possibleWbemNamespaces(new HashSet<>()).build())
				.hostConfiguration(
					HostConfiguration
						.builder()
						.hostname(HOST_NAME)
						.hostId(HOST_NAME)
						.hostType(DeviceKind.OOB)
						.configurations(Map.of(WbemConfiguration.class, wbemConfiguration))
						.build()
				)
				.build();

		final WbemCriterion wbemCriterion = WbemCriterion
			.builder()
			.type(WBEM_CRITERION_TYPE)
			.namespace(AUTOMATIC_NAMESPACE)
			.query(WBEM_TEST_QUERY)
			.build();

		doReturn(EXECUTE_WBEM_RESULT)
			.when(wbemRequestExecutorSpy)
			.doWbemQuery(anyString(), any(WbemConfiguration.class), anyString(), anyString());

		final String message = "WbemCriterion test succeeded:";
		final CriterionTestResult criterionTestResult = wbemExtension.processCriterion(
			wbemCriterion,
			CONNECTOR_ID,
			telemetryManager
		);

		assertEquals(SourceTable.tableToCsv(EXECUTE_WBEM_RESULT, ";", false), criterionTestResult.getResult());
		assertTrue(criterionTestResult.isSuccess());
		assertTrue(criterionTestResult.getMessage().contains(message));
		assertNull(criterionTestResult.getException());
	}

	@Test
	void testProcessCriterionOkWithVcenter() throws ClientException {
		try (MockedStatic<ThreadHelper> threadHelperMock = mockStatic(ThreadHelper.class)) {
			final Monitor hostMonitor = Monitor.builder().type(HOST.getKey()).isEndpoint(true).build();

			final Map<String, Map<String, Monitor>> monitors = new HashMap<>(
				Map.of(HOST.getKey(), Map.of(HOST_NAME, hostMonitor))
			);

			final WbemConfiguration wbemConfiguration = WbemConfiguration
				.builder()
				.username(USERNAME)
				.password(PASSWORD.toCharArray())
				.vCenter("vcenter")
				.timeout(120L)
				.build();

			telemetryManager =
				TelemetryManager
					.builder()
					.monitors(monitors)
					.hostConfiguration(
						HostConfiguration
							.builder()
							.hostname(HOST_NAME)
							.hostId(HOST_NAME)
							.hostType(DeviceKind.OOB)
							.configurations(Map.of(WbemConfiguration.class, wbemConfiguration))
							.build()
					)
					.build();

			final WbemCriterion wbemCriterion = WbemCriterion
				.builder()
				.type(WBEM_CRITERION_TYPE)
				.query(WBEM_TEST_QUERY)
				.build();

			{
				doReturn(EXECUTE_WBEM_RESULT)
					.when(wbemRequestExecutorSpy)
					.doWbemQuery(anyString(), any(WbemConfiguration.class), anyString(), anyString());

				{
					final String message = "vCenter refresh ticket query failed on";

					final CriterionTestResult criterionTestResult = wbemExtension.processCriterion(
						wbemCriterion,
						CONNECTOR_ID,
						telemetryManager
					);

					assertTrue(criterionTestResult.getMessage().contains(message));
				}

				{
					threadHelperMock.when(() -> ThreadHelper.execute(any(), anyLong())).thenReturn("ticket");

					final String message = "WbemCriterion test succeeded:";

					final CriterionTestResult criterionTestResult = wbemExtension.processCriterion(
						wbemCriterion,
						CONNECTOR_ID,
						telemetryManager
					);

					assertEquals(SourceTable.tableToCsv(EXECUTE_WBEM_RESULT, ";", false), criterionTestResult.getResult());
					assertTrue(criterionTestResult.isSuccess());
					assertTrue(criterionTestResult.getMessage().contains(message));
					assertNull(criterionTestResult.getException());
				}
			}
		}
	}

	@Test
	void testProcessCriterionOkWithVcenterWithTicket() throws ClientException {
		try (MockedStatic<ThreadHelper> threadHelperMock = mockStatic(ThreadHelper.class)) {
			final Monitor hostMonitor = Monitor.builder().type(HOST.getKey()).isEndpoint(true).build();

			final Map<String, Map<String, Monitor>> monitors = new HashMap<>(
				Map.of(HOST.getKey(), Map.of(HOST_NAME, hostMonitor))
			);

			final WbemConfiguration wbemConfiguration = WbemConfiguration
				.builder()
				.username(USERNAME)
				.password(PASSWORD.toCharArray())
				.vCenter("vcenter")
				.timeout(120L)
				.build();

			HostProperties hostProperties = HostProperties.builder().vCenterTicket("ticket").build();

			telemetryManager =
				TelemetryManager
					.builder()
					.monitors(monitors)
					.hostProperties(hostProperties)
					.hostConfiguration(
						HostConfiguration
							.builder()
							.hostname(HOST_NAME)
							.hostId(HOST_NAME)
							.hostType(DeviceKind.OOB)
							.configurations(Map.of(WbemConfiguration.class, wbemConfiguration))
							.build()
					)
					.build();

			final WbemCriterion wbemCriterion = WbemCriterion
				.builder()
				.type(WBEM_CRITERION_TYPE)
				.query(WBEM_TEST_QUERY)
				.build();

			threadHelperMock.when(() -> ThreadHelper.execute(any(), anyLong())).thenReturn("ticket");

			{
				doReturn(EXECUTE_WBEM_RESULT)
					.when(wbemRequestExecutorSpy)
					.doWbemQuery(anyString(), any(WbemConfiguration.class), anyString(), anyString());

				final String message = "WbemCriterion test succeeded:";
				final CriterionTestResult criterionTestResult = wbemExtension.processCriterion(
					wbemCriterion,
					CONNECTOR_ID,
					telemetryManager
				);

				assertEquals(SourceTable.tableToCsv(EXECUTE_WBEM_RESULT, ";", false), criterionTestResult.getResult());
				assertTrue(criterionTestResult.isSuccess());
				assertTrue(criterionTestResult.getMessage().contains(message));
				assertNull(criterionTestResult.getException());
			}

			{
				ClientException clientException = new ClientException(new WBEMException("error"));

				doThrow(clientException)
					.when(wbemRequestExecutorSpy)
					.doWbemQuery(anyString(), any(WbemConfiguration.class), anyString(), anyString());

				final CriterionTestResult criterionTestResult = wbemExtension.processCriterion(
					wbemCriterion,
					CONNECTOR_ID,
					telemetryManager
				);

				assertNull(criterionTestResult.getResult());
			}

			{}
		}
	}

	@Test
	void testProcessCriterionAutomaticNamespace() throws ClientException {
		final Monitor hostMonitor = Monitor.builder().type(HOST.getKey()).isEndpoint(true).build();

		final Map<String, Map<String, Monitor>> monitors = new HashMap<>(
			Map.of(HOST.getKey(), Map.of(HOST_NAME, hostMonitor))
		);

		final WbemConfiguration wbemConfiguration = WbemConfiguration
			.builder()
			.username(USERNAME)
			.password(PASSWORD.toCharArray())
			.namespace(AUTOMATIC_NAMESPACE)
			.timeout(120L)
			.build();

		telemetryManager =
			TelemetryManager
				.builder()
				.monitors(monitors)
				.hostConfiguration(
					HostConfiguration
						.builder()
						.hostname(HOST_NAME)
						.hostId(HOST_NAME)
						.hostType(DeviceKind.OOB)
						.configurations(Map.of(WbemConfiguration.class, wbemConfiguration))
						.build()
				)
				.build();

		final WbemCriterion wbemCriterion = WbemCriterion
			.builder()
			.type(WBEM_CRITERION_TYPE)
			.query(WBEM_TEST_QUERY)
			.namespace(AUTOMATIC_NAMESPACE)
			.build();

		doReturn(EXECUTE_WBEM_RESULT)
			.when(wbemRequestExecutorSpy)
			.doWbemQuery(anyString(), any(WbemConfiguration.class), anyString(), anyString());

		final String message = "WbemCriterion test succeeded:";
		final CriterionTestResult criterionTestResult = wbemExtension.processCriterion(
			wbemCriterion,
			CONNECTOR_ID,
			telemetryManager
		);

		assertEquals(SourceTable.tableToCsv(EXECUTE_WBEM_RESULT, ";", false), criterionTestResult.getResult());
		assertTrue(criterionTestResult.isSuccess());
		assertTrue(criterionTestResult.getMessage().contains(message));
		assertNull(criterionTestResult.getException());
	}

	@Test
	void testProcessWbemSourceOk() throws ClientException {
		initWbem();

		final WbemConfiguration configuration = (WbemConfiguration) telemetryManager
			.getHostConfiguration()
			.getConfigurations()
			.get(WbemConfiguration.class);

		doReturn(EXECUTE_WBEM_RESULT)
			.when(wbemRequestExecutorSpy)
			.executeWbem(HOST_NAME, configuration, WBEM_TEST_QUERY, WBEM_TEST_NAMESPACE, telemetryManager);
		final SourceTable actual = wbemExtension.processSource(
			WbemSource.builder().query(WBEM_TEST_QUERY).namespace(WBEM_TEST_NAMESPACE).build(),
			CONNECTOR_ID,
			telemetryManager
		);

		final SourceTable expected = SourceTable.builder().table(EXECUTE_WBEM_RESULT).build();
		assertEquals(expected, actual);
	}

	@Test
	void testProcessWbemSourceThrowsException() throws ClientException {
		initWbem();

		final WbemConfiguration configuration = (WbemConfiguration) telemetryManager
			.getHostConfiguration()
			.getConfigurations()
			.get(WbemConfiguration.class);

		doThrow(new RuntimeException("exception"))
			.when(wbemRequestExecutorSpy)
			.executeWbem(anyString(), eq(configuration), eq(WBEM_TEST_QUERY), eq(WBEM_TEST_NAMESPACE), eq(telemetryManager));
		final SourceTable actual = wbemExtension.processSource(
			WbemSource.builder().query(WBEM_TEST_QUERY).build(),
			CONNECTOR_ID,
			telemetryManager
		);

		final SourceTable expected = SourceTable.empty();
		assertEquals(expected, actual);
	}

	@Test
	void testProcessWbemSourceEmptyResult() throws ClientException {
		initWbem();

		final WbemConfiguration configuration = (WbemConfiguration) telemetryManager
			.getHostConfiguration()
			.getConfigurations()
			.get(WbemConfiguration.class);

		{
			doReturn(null)
				.when(wbemRequestExecutorSpy)
				.executeWbem(HOST_NAME, configuration, WBEM_TEST_QUERY, WBEM_TEST_NAMESPACE, telemetryManager);
			final SourceTable actual = wbemExtension.processSource(
				WbemSource.builder().query(WBEM_TEST_QUERY).build(),
				CONNECTOR_ID,
				telemetryManager
			);

			final SourceTable expected = SourceTable.empty();
			assertEquals(expected, actual);
		}

		{
			doReturn(Collections.emptyList())
				.when(wbemRequestExecutorSpy)
				.executeWbem(HOST_NAME, configuration, WBEM_TEST_QUERY, WBEM_TEST_NAMESPACE, telemetryManager);
			final SourceTable actual = wbemExtension.processSource(
				WbemSource.builder().query(WBEM_TEST_QUERY).build(),
				CONNECTOR_ID,
				telemetryManager
			);

			final SourceTable expected = SourceTable.builder().rawData(null).build();
			assertEquals(expected, actual);
		}
	}

	@Test
	void testProcessWbemSourceNoWbemConfiguration() {
		initWbem();

		telemetryManager.getHostConfiguration().setConfigurations(Map.of());

		assertEquals(
			SourceTable.empty(),
			wbemExtension.processSource(
				WbemSource.builder().query(WBEM_TEST_NAMESPACE).build(),
				CONNECTOR_ID,
				telemetryManager
			)
		);
	}

	@Test
	void tesExecuteQuery() throws Exception {
		initWbem();

		doReturn(EXECUTE_WBEM_RESULT)
			.when(wbemRequestExecutorSpy)
			.executeWbem(anyString(), any(WbemConfiguration.class), anyString(), anyString(), any(TelemetryManager.class));

		final ObjectNode queryNode = JsonNodeFactory.instance.objectNode();
		queryNode.set("query", new TextNode(WBEM_TEST_QUERY));
		WbemConfiguration configuration = WbemConfiguration
			.builder()
			.hostname(HOST_NAME)
			.username(USERNAME)
			.password(PASSWORD.toCharArray())
			.timeout(120L)
			.namespace(WBEM_TEST_NAMESPACE)
			.vCenter("vcenter")
			.protocol(TransportProtocols.HTTPS)
			.build();
		final String result = wbemExtension.executeQuery(configuration, queryNode);
		final String expectedResult = TextTableHelper.generateTextTable(
			StringHelper.extractColumns(WBEM_TEST_QUERY),
			EXECUTE_WBEM_RESULT
		);
		assertEquals(expectedResult, result);
	}

	@Test
	void tesExecuteQueryThrow() throws Exception {
		initWbem();

		doThrow(ClientException.class)
			.when(wbemRequestExecutorSpy)
			.executeWbem(anyString(), any(WbemConfiguration.class), anyString(), anyString(), any(TelemetryManager.class));

		final ObjectNode queryNode = JsonNodeFactory.instance.objectNode();
		queryNode.set("query", new TextNode(WBEM_TEST_QUERY));
		WbemConfiguration configuration = WbemConfiguration
			.builder()
			.hostname(HOST_NAME)
			.username(USERNAME)
			.password(PASSWORD.toCharArray())
			.timeout(120L)
			.namespace(WBEM_TEST_NAMESPACE)
			.vCenter("vcenter")
			.protocol(TransportProtocols.HTTPS)
			.build();
		assertThrows(ClientException.class, () -> wbemExtension.executeQuery(configuration, queryNode));
	}
}
