package org.sentrysoftware.metricshub.extension.jdbc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mockStatic;

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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.sentrysoftware.metricshub.engine.common.exception.ClientException;
import org.sentrysoftware.metricshub.engine.common.exception.InvalidConfigurationException;
import org.sentrysoftware.metricshub.engine.common.helpers.StringHelper;
import org.sentrysoftware.metricshub.engine.common.helpers.TextTableHelper;
import org.sentrysoftware.metricshub.engine.configuration.HostConfiguration;
import org.sentrysoftware.metricshub.engine.configuration.IConfiguration;
import org.sentrysoftware.metricshub.engine.connector.model.Connector;
import org.sentrysoftware.metricshub.engine.connector.model.ConnectorStore;
import org.sentrysoftware.metricshub.engine.connector.model.identity.criterion.SqlCriterion;
import org.sentrysoftware.metricshub.engine.connector.model.identity.criterion.WbemCriterion;
import org.sentrysoftware.metricshub.engine.connector.model.monitor.task.source.SqlSource;
import org.sentrysoftware.metricshub.engine.connector.model.monitor.task.source.WbemSource;
import org.sentrysoftware.metricshub.engine.strategy.detection.CriterionTestResult;
import org.sentrysoftware.metricshub.engine.telemetry.Monitor;
import org.sentrysoftware.metricshub.engine.telemetry.TelemetryManager;

@ExtendWith(MockitoExtension.class)
class JdbcExtensionTest {

	private static final String HOST_NAME = "test-host";
	private static final String CONNECTOR_ID = "connector_id";
	private static final char[] PASSWORD = "password".toCharArray();
	private static final String USERNAME = "user";
	private static final String SQL_QUERY = "SELECT 1";
	public static final List<List<String>> SQL_RESULT = Arrays.asList(
		Arrays.asList("value1a", "value2a", "value3a"),
		Arrays.asList("value1b", "value2b", "value3b")
	);

	@Mock
	private SqlRequestExecutor sqlRequestExecutorMock;

	@InjectMocks
	private JdbcExtension jdbcExtension;

	private TelemetryManager telemetryManager;

	/**
	 * Creates and returns a TelemetryManager instance with an SQL configuration.
	 *
	 * @return A TelemetryManager instance configured with an SQL configuration.
	 */
	private void initSql() {
		final Monitor hostMonitor = Monitor.builder().type("HOST").isEndpoint(true).build();
		final Map<String, Map<String, Monitor>> monitors = new HashMap<>(Map.of("HOST", Map.of(HOST_NAME, hostMonitor)));

		final JdbcConfiguration jdbcConfiguration = JdbcConfiguration
			.builder()
			.hostname("hostname")
			.username(USERNAME)
			.password(PASSWORD)
			.url("jdbc:mysql://hostname:3306/testdb".toCharArray())
			.timeout(30L)
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
						.configurations(Map.of(JdbcConfiguration.class, jdbcConfiguration))
						.build()
				)
				.connectorStore(connectorStore)
				.strategyTime(System.currentTimeMillis())
				.build();
	}

	@Test
	void testCheckSqlUpHealth() throws Exception {
		initSql();
		// Mock the static method isDatabaseAlive
		try (MockedStatic<JdbcExtension> mockedStatic = mockStatic(JdbcExtension.class)) {
			mockedStatic
				.when(() -> JdbcExtension.isDatabaseAlive(anyString(), anyString(), any(), anyLong()))
				.thenReturn(true);
			final Optional<Boolean> result = jdbcExtension.checkProtocol(telemetryManager);
			assertTrue(result.get());
		}
	}

	@Test
	void testCheckSqlDownHealth() throws Exception {
		initSql();
		// Mock the static method isDatabaseAlive
		try (MockedStatic<JdbcExtension> mockedStatic = mockStatic(JdbcExtension.class)) {
			mockedStatic
				.when(() -> JdbcExtension.isDatabaseAlive(anyString(), anyString(), any(), anyLong()))
				.thenReturn(false);

			final Optional<Boolean> result = jdbcExtension.checkProtocol(telemetryManager);
			assertFalse(result.get());
		}
	}

	@Test
	void testIsValidConfiguration() {
		assertTrue(jdbcExtension.isValidConfiguration(JdbcConfiguration.builder().build()));

		assertFalse(
			jdbcExtension.isValidConfiguration(
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
		assertEquals(Set.of(SqlSource.class), jdbcExtension.getSupportedSources());
	}

	@Test
	void testGetSupportedCriteria() {
		assertEquals(Set.of(SqlCriterion.class), jdbcExtension.getSupportedCriteria());
	}

	@Test
	void testSqlCriterionReturnsSuccessWithResults() throws Exception {
		initSql();
		final SqlCriterion sqlCriterion = SqlCriterion.builder().query(SQL_QUERY).build();

		doReturn(List.of(List.of("1")))
			.when(sqlRequestExecutorMock)
			.executeSql(anyString(), any(JdbcConfiguration.class), anyString(), anyBoolean());

		final CriterionTestResult result = jdbcExtension.processCriterion(sqlCriterion, CONNECTOR_ID, telemetryManager);

		assertTrue(result.isSuccess());
	}

	@Test
	void testSqlCriterionFailureNoResults() throws Exception {
		initSql();
		final SqlCriterion sqlCriterion = SqlCriterion.builder().query(SQL_QUERY).build();

		doReturn(List.of())
			.when(sqlRequestExecutorMock)
			.executeSql(anyString(), any(JdbcConfiguration.class), anyString(), anyBoolean());

		final CriterionTestResult result = jdbcExtension.processCriterion(sqlCriterion, CONNECTOR_ID, telemetryManager);

		assertFalse(result.isSuccess());
		assertEquals("Hostname hostname - SQL test failed - The SQL test did not return any result.", result.getMessage());
	}

	@Test
	void testSqlCriterionFailureWithException() throws Exception {
		initSql();
		final SqlCriterion sqlCriterion = SqlCriterion.builder().query(SQL_QUERY).build();

		doThrow(new ClientException("SQL query failed"))
			.when(sqlRequestExecutorMock)
			.executeSql(anyString(), any(JdbcConfiguration.class), anyString(), anyBoolean());

		final CriterionTestResult result = jdbcExtension.processCriterion(sqlCriterion, CONNECTOR_ID, telemetryManager);

		assertFalse(result.isSuccess());
	}

	@Test
	void testSqlCriterionFailureWithNullCriterion() {
		initSql();

		final SqlCriterion sqlCriterion = null;

		final IllegalArgumentException exception = assertThrows(
			IllegalArgumentException.class,
			() -> {
				jdbcExtension.processCriterion(sqlCriterion, CONNECTOR_ID, telemetryManager);
			}
		);

		assertEquals("Hostname test-host - Cannot process criterion <null>.", exception.getMessage());
	}

	@Test
	void testSqlCriterionSuccessWithCsvFormatting() throws Exception {
		initSql();
		final SqlCriterion sqlCriterion = SqlCriterion.builder().query(SQL_QUERY).build();

		doReturn(List.of(List.of("value1", "value2")))
			.when(sqlRequestExecutorMock)
			.executeSql(anyString(), any(JdbcConfiguration.class), anyString(), anyBoolean());

		CriterionTestResult result = jdbcExtension.processCriterion(sqlCriterion, CONNECTOR_ID, telemetryManager);

		assertTrue(result.isSuccess());
		assertEquals("value1;value2;", result.getResult().trim());
	}

	@Test
	void testProcessSourceThrowsIllegalArgumentException() {
		initSql();
		final WbemSource wbemSource = WbemSource.builder().query("SELECT Name FROM testDb").build();
		assertThrows(
			IllegalArgumentException.class,
			() -> jdbcExtension.processSource(wbemSource, CONNECTOR_ID, telemetryManager)
		);
	}

	@Test
	void testProcessCriterionThrowsIllegalArgumentException() {
		initSql();

		final WbemCriterion wbemCriterion = WbemCriterion.builder().query("SELECT Name FROM testDB").build();

		assertThrows(
			IllegalArgumentException.class,
			() -> {
				jdbcExtension.processCriterion(wbemCriterion, CONNECTOR_ID, telemetryManager);
			}
		);
	}

	@Test
	void testBuildConfigurationWithDecrypt() throws InvalidConfigurationException {
		final ObjectNode configuration = JsonNodeFactory.instance.objectNode();
		configuration.set("password", new TextNode("testPassword"));
		configuration.set("url", new TextNode("jdbc:mysql://localhost:3306/dbname"));
		configuration.set("timeout", new TextNode("2m"));
		configuration.set("username", new TextNode("testUser"));
		configuration.set("port", new IntNode(161));

		assertEquals(
			JdbcConfiguration
				.builder()
				.password("testPassword".toCharArray())
				.url("jdbc:mysql://localhost:3306/dbname".toCharArray())
				.timeout(120L)
				.port(161)
				.username("testUser")
				.build(),
			jdbcExtension.buildConfiguration("sql", configuration, value -> value)
		);
		assertEquals(
			JdbcConfiguration
				.builder()
				.password("testPassword".toCharArray())
				.url("jdbc:mysql://localhost:3306/dbname".toCharArray())
				.timeout(120L)
				.port(161)
				.username("testUser")
				.build(),
			jdbcExtension.buildConfiguration("sql", configuration, null)
		);
		assertEquals(
			JdbcConfiguration
				.builder()
				.password("testPassword".toCharArray())
				.url("jdbc:mysql://localhost:3306/dbname".toCharArray())
				.timeout(120L)
				.port(161)
				.username("testUser")
				.build(),
			jdbcExtension.buildConfiguration("sql", configuration, null)
		);
	}

	@Test
	void testGetIdentifier() {
		assertEquals("jdbc", jdbcExtension.getIdentifier());
	}

	@Test
	void testProcessSourceWithNullSource() {
		initSql();

		final IllegalArgumentException exception = assertThrows(
			IllegalArgumentException.class,
			() -> jdbcExtension.processSource(null, CONNECTOR_ID, telemetryManager)
		);

		assertEquals("Hostname test-host - Cannot process source <null>.", exception.getMessage());
	}

	@Test
	void testIsSupportedConfigurationTypeForUnsupportedType() {
		assertFalse(jdbcExtension.isSupportedConfigurationType("unsupported_type"));
	}

	@Test
	void tesExecuteQuery() throws Exception {
		initSql();

		doReturn(SQL_RESULT)
			.when(sqlRequestExecutorMock)
			.executeSql(anyString(), any(JdbcConfiguration.class), anyString(), anyBoolean());

		final ObjectNode queryNode = JsonNodeFactory.instance.objectNode();
		queryNode.set("query", new TextNode(SQL_QUERY));
		JdbcConfiguration configuration = JdbcConfiguration
			.builder()
			.hostname(HOST_NAME)
			.username(USERNAME)
			.password(PASSWORD)
			.timeout(120L)
			.build();
		final String result = jdbcExtension.executeQuery(configuration, queryNode);
		final String expectedResult = TextTableHelper.generateTextTable(StringHelper.extractColumns(SQL_QUERY), SQL_RESULT);
		assertEquals(expectedResult, result);
	}

	@Test
	void tesExecuteQueryThrow() throws Exception {
		initSql();

		doThrow(ClientException.class)
			.when(sqlRequestExecutorMock)
			.executeSql(anyString(), any(JdbcConfiguration.class), anyString(), anyBoolean());

		final ObjectNode queryNode = JsonNodeFactory.instance.objectNode();
		queryNode.set("query", new TextNode(SQL_QUERY));
		JdbcConfiguration configuration = JdbcConfiguration
			.builder()
			.hostname(HOST_NAME)
			.username(USERNAME)
			.password(PASSWORD)
			.timeout(120L)
			.build();
		assertThrows(ClientException.class, () -> jdbcExtension.executeQuery(configuration, queryNode));
	}
}
