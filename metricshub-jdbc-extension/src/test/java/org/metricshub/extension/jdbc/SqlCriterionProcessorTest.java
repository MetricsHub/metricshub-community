package org.metricshub.extension.jdbc;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
import org.sentrysoftware.metricshub.engine.connector.model.identity.criterion.SqlCriterion;
import org.sentrysoftware.metricshub.engine.strategy.detection.CriterionTestResult;
import org.sentrysoftware.metricshub.engine.telemetry.TelemetryManager;

@ExtendWith(MockitoExtension.class)
class SqlCriterionProcessorTest {

	@Mock
	private SqlRequestExecutor sqlRequestExecutor;

	@Mock
	private JdbcConfiguration jdbcConfiguration;

	private SqlCriterionProcessor sqlCriterionProcessor;

	@BeforeEach
	void setUp() {
		sqlCriterionProcessor = new SqlCriterionProcessor(sqlRequestExecutor);
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

	// Test case for successful SQL query execution and returning success.
	@Test
	void testProcessSuccess() throws Exception {
		TelemetryManager telemetryManager = createTelemetryManagerWithHostConfiguration();
		String query = "SELECT * FROM test_table";

		String expectedResult = List.of(List.of("row1_col1", "row1_col2")).toString();
		SqlCriterion sqlCriterion = SqlCriterion.builder().query(query).expectedResult(expectedResult).build();

		when(sqlRequestExecutor.executeSql(any(), eq(jdbcConfiguration), eq(query), eq(false)))
			.thenReturn(List.of(List.of("row1_col1", "row1_col2")));

		CriterionTestResult criterionTestResult = sqlCriterionProcessor.process(sqlCriterion, telemetryManager);
		assertNotNull(criterionTestResult);
		assertTrue(criterionTestResult.isSuccess());
	}

	// Test case when the SqlCriterion is null
	@Test
	void testProcess_NullCriterion() {
		TelemetryManager telemetryManager = createTelemetryManagerWithHostConfiguration();
		CriterionTestResult result = sqlCriterionProcessor.process(null, telemetryManager);
		assertFalse(result.isSuccess());
	}

	// Test case when the SqlConfiguration is null
	@Test
	void testProcessNullSqlConfiguration() {
		Map<Class<? extends IConfiguration>, IConfiguration> configurations = new HashMap<>();
		configurations.put(JdbcConfiguration.class, null);
		HostConfiguration hostConfiguration = HostConfiguration
			.builder()
			.hostname("test-host")
			.configurations(configurations)
			.build();
		TelemetryManager telemetryManager = TelemetryManager.builder().hostConfiguration(hostConfiguration).build();

		SqlCriterion sqlCriterion = SqlCriterion.builder().query("SELECT * FROM test_table").build();
		CriterionTestResult result = sqlCriterionProcessor.process(sqlCriterion, telemetryManager);

		assertFalse(result.isSuccess());
	}

	// Test case when SQL execution throws an exception,
	@Test
	void testProcessSqlRequestException() throws Exception {
		TelemetryManager telemetryManager = createTelemetryManagerWithHostConfiguration();

		when(
			sqlRequestExecutor.executeSql(
				any(String.class),
				any(JdbcConfiguration.class),
				any(String.class),
				any(Boolean.class)
			)
		)
			.thenThrow(new RuntimeException("Test exception"));

		SqlCriterion sqlCriterion = SqlCriterion.builder().query("SELECT * FROM test_table").build();

		CriterionTestResult result = sqlCriterionProcessor.process(sqlCriterion, telemetryManager);

		assertFalse(result.isSuccess());
	}

	// Test case when SQL query returns null result
	@Test
	void testProcessNullResult() throws Exception {
		TelemetryManager telemetryManager = createTelemetryManagerWithHostConfiguration();
		SqlCriterion sqlCriterion = SqlCriterion.builder().query("SELECT * FROM test_table").build();

		when(
			sqlRequestExecutor.executeSql(
				any(String.class),
				any(JdbcConfiguration.class),
				any(String.class),
				any(Boolean.class)
			)
		)
			.thenReturn(null);

		CriterionTestResult result = sqlCriterionProcessor.process(sqlCriterion, telemetryManager);

		assertFalse(result.isSuccess());
	}
}
