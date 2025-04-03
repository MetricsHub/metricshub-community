package org.metricshub.extension.jdbc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.sentrysoftware.metricshub.engine.configuration.HostConfiguration;
import org.sentrysoftware.metricshub.engine.configuration.IConfiguration;
import org.sentrysoftware.metricshub.engine.connector.model.monitor.task.source.SqlSource;
import org.sentrysoftware.metricshub.engine.strategy.source.SourceTable;
import org.sentrysoftware.metricshub.engine.telemetry.TelemetryManager;

@ExtendWith(MockitoExtension.class)
class SqlSourceProcessorTest {

	@Mock
	private SqlRequestExecutor sqlRequestExecutor;

	private SqlSourceProcessor sqlSourceProcessor;

	@Mock
	private JdbcConfiguration jdbcConfiguration;

	@BeforeEach
	void setUp() {
		sqlSourceProcessor = new SqlSourceProcessor(sqlRequestExecutor, "connectorId");
	}

	@Test
	void testProcessWhenConfigurationIsNullReturnsEmptySourceTable() {
		// Test case when jdbcConfiguration is null

		Map<Class<? extends IConfiguration>, IConfiguration> configurations = new HashMap<>();
		configurations.put(JdbcConfiguration.class, null);
		SqlSource sqlSource = SqlSource.builder().query("SELECT * FROM test_table").build();

		TelemetryManager telemetryManager = createTelemetryManagerWithHostConfiguration();
		SourceTable result = sqlSourceProcessor.process(sqlSource, telemetryManager);
		assertEquals(SourceTable.empty(), result);
	}

	@Test
	void testProcessWhenRequestExecutorThrowsExceptionReturnsEmptySourceTable() throws Exception {
		// Test case when the requestExecutor throws an exception

		SqlSource sqlSource = SqlSource.builder().query("SELECT * FROM test_table").build();
		TelemetryManager telemetryManager = createTelemetryManagerWithHostConfiguration();

		doThrow(new RuntimeException("SQL execution error"))
			.when(sqlRequestExecutor)
			.executeSql("hostname", jdbcConfiguration, "SELECT * FROM test_table", false);

		SourceTable result = sqlSourceProcessor.process(sqlSource, telemetryManager);
		assertEquals(SourceTable.empty(), result);
	}

	@Test
	void testProcessWhenReturnsSourceTableWithResult() throws Exception {
		// Test case when requestExecutor returns a valid result

		SqlSource sqlSource = SqlSource.builder().query("SELECT * FROM test_table").build();
		TelemetryManager telemetryManager = createTelemetryManagerWithHostConfiguration();

		List<List<String>> ExpectedResults = List.of(List.of("row1_col1", "row1_col2"));
		when(sqlRequestExecutor.executeSql("hostname", jdbcConfiguration, "SELECT * FROM test_table", false))
			.thenReturn(ExpectedResults);
		when(jdbcConfiguration.getHostname()).thenReturn("hostname");
		SourceTable result = sqlSourceProcessor.process(sqlSource, telemetryManager);

		assertNotNull(result);
		assertTrue(result.getTable().size() == 1);
		assertEquals("row1_col1", result.getTable().get(0).get(0));
		assertEquals("row1_col2", result.getTable().get(0).get(1));
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
			.configurations(Map.of(JdbcConfiguration.class, jdbcConfiguration))
			.build();
		return TelemetryManager.builder().hostConfiguration(hostConfiguration).build();
	}
}
