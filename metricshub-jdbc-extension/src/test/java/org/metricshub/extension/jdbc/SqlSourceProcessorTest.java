package org.metricshub.extension.jdbc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.metricshub.engine.common.helpers.MetricsHubConstants.TABLE_SEP;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.metricshub.engine.configuration.HostConfiguration;
import org.metricshub.engine.connector.model.monitor.task.source.SqlSource;
import org.metricshub.engine.strategy.source.SourceTable;
import org.metricshub.engine.telemetry.TelemetryManager;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
		final SqlSource sqlSource = SqlSource.builder().query("SELECT * FROM test_table").build();

		final HostConfiguration hostConfiguration = HostConfiguration
			.builder()
			.hostname("hostname")
			.configurations(new HashMap<>())
			.build();

		final TelemetryManager telemetryManager = TelemetryManager.builder().hostConfiguration(hostConfiguration).build();

		final SourceTable result = sqlSourceProcessor.process(sqlSource, telemetryManager);

		assertNotNull(result);
		assertTrue(result.getTable().isEmpty());
		assertTrue(result.getHeaders().isEmpty());
		assertNull(result.getRawData());
	}

	@Test
	void testProcessWhenRequestExecutorThrowsExceptionReturnsEmptySourceTable() throws Exception {
		// Test case when the requestExecutor throws an exception
		when(jdbcConfiguration.copy()).thenReturn(jdbcConfiguration);
		when(jdbcConfiguration.getHostname()).thenReturn("hostname");
		final SqlSource sqlSource = SqlSource.builder().query("SELECT * FROM test_table").build();
		final TelemetryManager telemetryManager = createTelemetryManagerWithHostConfiguration();

		doThrow(new RuntimeException("SQL execution error"))
			.when(sqlRequestExecutor)
			.executeSql(any(), any(JdbcConfiguration.class), any(), any(Boolean.class));

		final SourceTable result = sqlSourceProcessor.process(sqlSource, telemetryManager);
		assertNotNull(result);
		assertTrue(result.getTable().isEmpty());
		assertTrue(result.getHeaders().isEmpty());
		assertNull(result.getRawData());
	}

	@Test
	void testProcessWhenReturnsSourceTableWithResult() throws Exception {
		// Test case when requestExecutor returns a valid result
		when(jdbcConfiguration.copy()).thenReturn(jdbcConfiguration);

		final SqlSource sqlSource = SqlSource.builder().query("SELECT * FROM test_table").build();
		final TelemetryManager telemetryManager = createTelemetryManagerWithHostConfiguration();

		final List<List<String>> expectedResults = List.of(List.of("row1_col1", "row1_col2"));
		when(sqlRequestExecutor.executeSql(any(), any(JdbcConfiguration.class), any(), any(Boolean.class)))
			.thenReturn(expectedResults);
		when(jdbcConfiguration.getHostname()).thenReturn("hostname");
		final SourceTable result = sqlSourceProcessor.process(sqlSource, telemetryManager);

		assertNotNull(result);
		assertNotNull(result.getRawData());
		assertEquals(SourceTable.tableToCsv(expectedResults, TABLE_SEP, true), result.getRawData());
		assertEquals(1, result.getTable().size());
		assertEquals("row1_col1", result.getTable().get(0).get(0));
		assertEquals("row1_col2", result.getTable().get(0).get(1));
	}

	/**
	 * Utility method to create a telemetryManager
	 *
	 * @return a configured telemetryManager instance
	 */
	private TelemetryManager createTelemetryManagerWithHostConfiguration() {
		final HostConfiguration hostConfiguration = HostConfiguration
			.builder()
			.hostname("hostname")
			.configurations(Map.of(JdbcConfiguration.class, jdbcConfiguration))
			.build();
		return TelemetryManager.builder().hostConfiguration(hostConfiguration).build();
	}
}
