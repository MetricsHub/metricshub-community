package org.metricshub.extension.emulation.jdbc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.metricshub.engine.common.exception.ClientException;
import org.metricshub.engine.common.helpers.MetricsHubConstants;
import org.metricshub.engine.configuration.HostConfiguration;
import org.metricshub.engine.connector.model.common.DeviceKind;
import org.metricshub.engine.strategy.source.SourceTable;
import org.metricshub.engine.telemetry.TelemetryManager;
import org.metricshub.extension.emulation.EmulationConfiguration;
import org.metricshub.extension.emulation.EmulationRoundRobinManager;
import org.metricshub.extension.emulation.JdbcEmulationConfig;
import org.metricshub.extension.jdbc.JdbcConfiguration;

/**
 * Tests for {@link EmulationSqlRequestExecutor}.
 */
class EmulationSqlRequestExecutorTest {

	private static final String HOSTNAME = "test-host";

	private final EmulationSqlRequestExecutor executor = new EmulationSqlRequestExecutor(
		new EmulationRoundRobinManager()
	);

	private TelemetryManager buildTelemetryManager(final String emulationInputDir) {
		return TelemetryManager
			.builder()
			.hostConfiguration(
				HostConfiguration
					.builder()
					.hostname(HOSTNAME)
					.hostId(HOSTNAME)
					.hostType(DeviceKind.WINDOWS)
					.configurations(
						Map.of(
							EmulationConfiguration.class,
							EmulationConfiguration
								.builder()
								.hostname(HOSTNAME)
								.jdbc(
									new JdbcEmulationConfig(JdbcConfiguration.builder().hostname(HOSTNAME).build(), emulationInputDir)
								)
								.build()
						)
					)
					.build()
			)
			.build();
	}

	@Test
	void testExecuteSqlMatch(@TempDir final Path tempDir) throws Exception {
		Files.writeString(
			tempDir.resolve("image.yaml"),
			"""
			image:
			  - query: SELECT * FROM test_table
			    response: r1.csv
			""",
			StandardCharsets.UTF_8
		);
		Files.writeString(
			tempDir.resolve("r1.csv"),
			SourceTable.tableToCsv(List.of(List.of("value1", "value2")), MetricsHubConstants.TABLE_SEP, true),
			StandardCharsets.UTF_8
		);

		final List<List<String>> result = executor.executeSql(
			HOSTNAME,
			JdbcConfiguration.builder().hostname(HOSTNAME).build(),
			"SELECT * FROM test_table",
			false,
			buildTelemetryManager(tempDir.toString())
		);

		assertEquals(1, result.size());
		assertEquals("value1", result.get(0).get(0));
		assertEquals("value2", result.get(0).get(1));
	}

	@Test
	void testExecuteSqlRoundRobin(@TempDir final Path tempDir) throws Exception {
		Files.writeString(
			tempDir.resolve("image.yaml"),
			"""
			image:
			  - query: SELECT * FROM test_table
			    response: r1.csv
			  - query: SELECT * FROM test_table
			    response: r2.csv
			""",
			StandardCharsets.UTF_8
		);
		Files.writeString(
			tempDir.resolve("r1.csv"),
			SourceTable.tableToCsv(List.of(List.of("first")), MetricsHubConstants.TABLE_SEP, true),
			StandardCharsets.UTF_8
		);
		Files.writeString(
			tempDir.resolve("r2.csv"),
			SourceTable.tableToCsv(List.of(List.of("second")), MetricsHubConstants.TABLE_SEP, true),
			StandardCharsets.UTF_8
		);

		final TelemetryManager telemetryManager = buildTelemetryManager(tempDir.toString());
		final JdbcConfiguration jdbcConfig = JdbcConfiguration.builder().hostname(HOSTNAME).build();

		final List<List<String>> result1 = executor.executeSql(
			HOSTNAME,
			jdbcConfig,
			"SELECT * FROM test_table",
			false,
			telemetryManager
		);
		final List<List<String>> result2 = executor.executeSql(
			HOSTNAME,
			jdbcConfig,
			"SELECT * FROM test_table",
			false,
			telemetryManager
		);
		final List<List<String>> result3 = executor.executeSql(
			HOSTNAME,
			jdbcConfig,
			"SELECT * FROM test_table",
			false,
			telemetryManager
		);

		assertEquals("first", result1.get(0).get(0));
		assertEquals("second", result2.get(0).get(0));
		assertEquals("first", result3.get(0).get(0));
	}

	@Test
	void testExecuteSqlNoMatch(@TempDir final Path tempDir) throws Exception {
		Files.writeString(
			tempDir.resolve("image.yaml"),
			"""
			image:
			  - query: SELECT * FROM other_table
			    response: r1.csv
			""",
			StandardCharsets.UTF_8
		);
		Files.writeString(
			tempDir.resolve("r1.csv"),
			SourceTable.tableToCsv(List.of(List.of("other")), MetricsHubConstants.TABLE_SEP, true),
			StandardCharsets.UTF_8
		);

		final List<List<String>> result = executor.executeSql(
			HOSTNAME,
			JdbcConfiguration.builder().hostname(HOSTNAME).build(),
			"SELECT * FROM test_table",
			false,
			buildTelemetryManager(tempDir.toString())
		);

		assertTrue(result.isEmpty());
	}

	@Test
	void testExecuteSqlReturnsEmptyWhenQueryIsNull() throws ClientException {
		final List<List<String>> result = executor.executeSql(
			HOSTNAME,
			JdbcConfiguration.builder().hostname(HOSTNAME).build(),
			null,
			false,
			buildTelemetryManager("any")
		);

		assertTrue(result.isEmpty());
	}

	@Test
	void testExecuteSqlReturnsEmptyWhenTelemetryManagerIsNull() throws ClientException {
		final List<List<String>> result = executor.executeSql(
			HOSTNAME,
			JdbcConfiguration.builder().hostname(HOSTNAME).build(),
			"SELECT * FROM test_table",
			false,
			null
		);

		assertTrue(result.isEmpty());
	}

	@Test
	void testExecuteSqlReturnsEmptyWhenIndexIsMissing(@TempDir final Path tempDir) throws Exception {
		final List<List<String>> result = executor.executeSql(
			HOSTNAME,
			JdbcConfiguration.builder().hostname(HOSTNAME).build(),
			"SELECT * FROM test_table",
			false,
			buildTelemetryManager(tempDir.toString())
		);

		assertTrue(result.isEmpty());
	}

	@Test
	void testExecuteSqlReturnsEmptyWhenIndexIsInvalid(@TempDir final Path tempDir) throws Exception {
		Files.writeString(tempDir.resolve("image.yaml"), "not: [valid", StandardCharsets.UTF_8);

		final List<List<String>> result = executor.executeSql(
			HOSTNAME,
			JdbcConfiguration.builder().hostname(HOSTNAME).build(),
			"SELECT * FROM test_table",
			false,
			buildTelemetryManager(tempDir.toString())
		);

		assertTrue(result.isEmpty());
	}

	@Test
	void testExecuteSqlReturnsEmptyWhenMatchedResponseIsBlank(@TempDir final Path tempDir) throws Exception {
		Files.writeString(
			tempDir.resolve("image.yaml"),
			"""
			image:
			  - query: SELECT * FROM test_table
			    response: " "
			""",
			StandardCharsets.UTF_8
		);

		final List<List<String>> result = executor.executeSql(
			HOSTNAME,
			JdbcConfiguration.builder().hostname(HOSTNAME).build(),
			"SELECT * FROM test_table",
			false,
			buildTelemetryManager(tempDir.toString())
		);

		assertTrue(result.isEmpty());
	}

	@Test
	void testExecuteSqlReturnsEmptyWhenResponseFileIsMissing(@TempDir final Path tempDir) throws Exception {
		Files.writeString(
			tempDir.resolve("image.yaml"),
			"""
			image:
			  - query: SELECT * FROM test_table
			    response: missing.csv
			""",
			StandardCharsets.UTF_8
		);

		final List<List<String>> result = executor.executeSql(
			HOSTNAME,
			JdbcConfiguration.builder().hostname(HOSTNAME).build(),
			"SELECT * FROM test_table",
			false,
			buildTelemetryManager(tempDir.toString())
		);

		assertTrue(result.isEmpty());
	}

	@Test
	void testExecuteSqlReturnsEmptyWhenDirectoryIsNull() throws ClientException {
		final TelemetryManager telemetryManager = TelemetryManager
			.builder()
			.hostConfiguration(
				HostConfiguration
					.builder()
					.hostname(HOSTNAME)
					.hostId(HOSTNAME)
					.hostType(DeviceKind.WINDOWS)
					.configurations(
						Map.of(
							EmulationConfiguration.class,
							EmulationConfiguration
								.builder()
								.hostname(HOSTNAME)
								.jdbc(new JdbcEmulationConfig(JdbcConfiguration.builder().hostname(HOSTNAME).build(), null))
								.build()
						)
					)
					.build()
			)
			.build();

		final List<List<String>> result = executor.executeSql(
			HOSTNAME,
			JdbcConfiguration.builder().hostname(HOSTNAME).build(),
			"SELECT * FROM test_table",
			false,
			telemetryManager
		);

		assertTrue(result.isEmpty());
	}

	@Test
	void testExecuteSqlReturnsEmptyWhenJdbcConfigIsNull() throws ClientException {
		final TelemetryManager telemetryManager = TelemetryManager
			.builder()
			.hostConfiguration(
				HostConfiguration
					.builder()
					.hostname(HOSTNAME)
					.hostId(HOSTNAME)
					.hostType(DeviceKind.WINDOWS)
					.configurations(
						Map.of(EmulationConfiguration.class, EmulationConfiguration.builder().hostname(HOSTNAME).build())
					)
					.build()
			)
			.build();

		final List<List<String>> result = executor.executeSql(
			HOSTNAME,
			JdbcConfiguration.builder().hostname(HOSTNAME).build(),
			"SELECT * FROM test_table",
			false,
			telemetryManager
		);

		assertTrue(result.isEmpty());
	}

	@Test
	void testFindMatchingEntriesIgnoresNullEntries() {
		final JdbcEmulationEntry matchingEntry = new JdbcEmulationEntry("SELECT * FROM test_table", "r1.csv");
		final JdbcEmulationEntry nullQueryEntry = new JdbcEmulationEntry();

		final List<JdbcEmulationEntry> entries = new ArrayList<>();
		entries.add(null);
		entries.add(nullQueryEntry);
		entries.add(matchingEntry);

		final List<JdbcEmulationEntry> result = executor.findMatchingEntries(entries, "SELECT * FROM test_table");

		assertEquals(1, result.size());
		assertEquals("r1.csv", result.get(0).getResponse());
	}

	@Test
	void testFindMatchingEntriesReturnsEmptyForNullEntries() {
		assertTrue(executor.findMatchingEntries(null, "SELECT 1").isEmpty());
	}

	@Test
	void testFindMatchingEntriesReturnsEmptyForEmptyList() {
		assertTrue(executor.findMatchingEntries(List.of(), "SELECT 1").isEmpty());
	}
}
