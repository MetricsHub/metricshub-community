package org.metricshub.extension.emulation.wmi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.metricshub.engine.common.exception.ClientException;
import org.metricshub.engine.common.helpers.MetricsHubConstants;
import org.metricshub.engine.strategy.source.SourceTable;
import org.metricshub.extension.emulation.EmulationRoundRobinManager;
import org.metricshub.extension.emulation.WmiEmulationConfig;
import org.metricshub.extension.wmi.WmiConfiguration;

/**
 * Tests for {@link EmulationWmiRequestExecutor}.
 */
class EmulationWmiRequestExecutorTest {

	private static final String HOSTNAME = "test-host";

	private final EmulationWmiRequestExecutor executor = new EmulationWmiRequestExecutor(
		new EmulationRoundRobinManager()
	);

	private WmiEmulationConfig buildWmiEmulationConfig(final String emulationInputDir) {
		return new WmiEmulationConfig(WmiConfiguration.builder().hostname(HOSTNAME).build(), emulationInputDir);
	}

	@Test
	void testExecuteWmiMatch(@TempDir final Path tempDir) throws Exception {
		Files.writeString(
			tempDir.resolve("image.yaml"),
			"""
			image:
			  - request:
			      wql: SELECT Name FROM Win32_ComputerSystem
			      namespace: root\\cimv2
			    response: r1.txt
			""",
			StandardCharsets.UTF_8
		);
		Files.writeString(
			tempDir.resolve("r1.txt"),
			SourceTable.tableToCsv(List.of(List.of("value1", "value2")), MetricsHubConstants.TABLE_SEP, true),
			StandardCharsets.UTF_8
		);

		final List<List<String>> result = executor.executeWmi(
			HOSTNAME,
			buildWmiEmulationConfig(tempDir.toString()),
			"SELECT Name FROM Win32_ComputerSystem",
			"root\\cimv2",
			null
		);

		assertEquals(1, result.size());
		assertEquals("value1", result.get(0).get(0));
		assertEquals("value2", result.get(0).get(1));
	}

	@Test
	void testExecuteWmiRoundRobin(@TempDir final Path tempDir) throws Exception {
		Files.writeString(
			tempDir.resolve("image.yaml"),
			"""
			image:
			  - request:
			      wql: SELECT Name FROM Win32_Process
			      namespace: root\\cimv2
			    response: r1.txt
			  - request:
			      wql: SELECT Name FROM Win32_Process
			      namespace: root\\cimv2
			    response: r2.txt
			""",
			StandardCharsets.UTF_8
		);
		Files.writeString(
			tempDir.resolve("r1.txt"),
			SourceTable.tableToCsv(List.of(List.of("first")), MetricsHubConstants.TABLE_SEP, true),
			StandardCharsets.UTF_8
		);
		Files.writeString(
			tempDir.resolve("r2.txt"),
			SourceTable.tableToCsv(List.of(List.of("second")), MetricsHubConstants.TABLE_SEP, true),
			StandardCharsets.UTF_8
		);

		final WmiEmulationConfig config = buildWmiEmulationConfig(tempDir.toString());

		final List<List<String>> result1 = executor.executeWmi(
			HOSTNAME,
			config,
			"SELECT Name FROM Win32_Process",
			"root\\cimv2",
			null
		);
		final List<List<String>> result2 = executor.executeWmi(
			HOSTNAME,
			config,
			"SELECT Name FROM Win32_Process",
			"root\\cimv2",
			null
		);
		final List<List<String>> result3 = executor.executeWmi(
			HOSTNAME,
			config,
			"SELECT Name FROM Win32_Process",
			"root\\cimv2",
			null
		);

		assertEquals("first", result1.get(0).get(0));
		assertEquals("second", result2.get(0).get(0));
		assertEquals("first", result3.get(0).get(0));
	}

	@Test
	void testExecuteWmiNoMatch(@TempDir final Path tempDir) throws Exception {
		Files.writeString(
			tempDir.resolve("image.yaml"),
			"""
			image:
			  - request:
			      wql: SELECT Name FROM Win32_Service
			      namespace: root\\cimv2
			    response: r1.txt
			""",
			StandardCharsets.UTF_8
		);
		Files.writeString(
			tempDir.resolve("r1.txt"),
			SourceTable.tableToCsv(List.of(List.of("other")), MetricsHubConstants.TABLE_SEP, true),
			StandardCharsets.UTF_8
		);

		final List<List<String>> result = executor.executeWmi(
			HOSTNAME,
			buildWmiEmulationConfig(tempDir.toString()),
			"SELECT Name FROM Win32_Process",
			"root\\cimv2",
			null
		);

		assertTrue(result.isEmpty());
	}

	@Test
	void testExecuteWmiReturnsEmptyWhenQueryIsNull() throws ClientException {
		final List<List<String>> result = executor.executeWmi(
			HOSTNAME,
			buildWmiEmulationConfig("any"),
			null,
			"root\\cimv2",
			null
		);

		assertTrue(result.isEmpty());
	}

	@Test
	void testExecuteWmiReturnsEmptyWhenNamespaceIsNull() throws ClientException {
		final List<List<String>> result = executor.executeWmi(
			HOSTNAME,
			buildWmiEmulationConfig("any"),
			"SELECT Name FROM Win32_Process",
			null,
			null
		);

		assertTrue(result.isEmpty());
	}

	@Test
	void testExecuteWmiReturnsEmptyWhenDirectoryNotConfigured() throws ClientException {
		final List<List<String>> result = executor.executeWmi(
			HOSTNAME,
			WmiConfiguration.builder().hostname(HOSTNAME).build(),
			"SELECT Name FROM Win32_Process",
			"root\\cimv2",
			null
		);

		assertTrue(result.isEmpty());
	}

	@Test
	void testExecuteWmiReturnsEmptyWhenIndexIsMissing(@TempDir final Path tempDir) throws Exception {
		final List<List<String>> result = executor.executeWmi(
			HOSTNAME,
			buildWmiEmulationConfig(tempDir.toString()),
			"SELECT Name FROM Win32_Process",
			"root\\cimv2",
			null
		);

		assertTrue(result.isEmpty());
	}

	@Test
	void testExecuteWmiReturnsEmptyWhenIndexIsInvalid(@TempDir final Path tempDir) throws Exception {
		Files.writeString(tempDir.resolve("image.yaml"), "invalid: yaml: [[[", StandardCharsets.UTF_8);

		final List<List<String>> result = executor.executeWmi(
			HOSTNAME,
			buildWmiEmulationConfig(tempDir.toString()),
			"SELECT Name FROM Win32_Process",
			"root\\cimv2",
			null
		);

		assertTrue(result.isEmpty());
	}

	@Test
	void testExecuteWmiNamespaceCaseInsensitive(@TempDir final Path tempDir) throws Exception {
		Files.writeString(
			tempDir.resolve("image.yaml"),
			"""
			image:
			  - request:
			      wql: SELECT Name FROM Win32_Process
			      namespace: ROOT\\CIMV2
			    response: r1.txt
			""",
			StandardCharsets.UTF_8
		);
		Files.writeString(
			tempDir.resolve("r1.txt"),
			SourceTable.tableToCsv(List.of(List.of("matched")), MetricsHubConstants.TABLE_SEP, true),
			StandardCharsets.UTF_8
		);

		final List<List<String>> result = executor.executeWmi(
			HOSTNAME,
			buildWmiEmulationConfig(tempDir.toString()),
			"SELECT Name FROM Win32_Process",
			"root\\cimv2",
			null
		);

		assertEquals(1, result.size());
		assertEquals("matched", result.get(0).get(0));
	}
}
