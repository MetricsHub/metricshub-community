package org.metricshub.extension.emulation.wmi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.metricshub.engine.common.exception.ClientException;
import org.metricshub.engine.strategy.source.SourceTable;
import org.metricshub.engine.telemetry.RequestStats;
import org.metricshub.extension.emulation.EmulationImageCacheManager;
import org.metricshub.extension.emulation.EmulationRoundRobinManager;
import org.metricshub.extension.emulation.NoRecordedResponseException;
import org.metricshub.extension.wmi.WmiConfiguration;

/**
 * Tests for {@link EmulationWmiRequestExecutor}.
 */
class EmulationWmiRequestExecutorTest {

	private static final String HOSTNAME = "test-host";

	private final EmulationWmiRequestExecutor executor = new EmulationWmiRequestExecutor(
		new EmulationRoundRobinManager(),
		new EmulationImageCacheManager()
	);

	private WmiEmulationConfig buildWmiEmulationConfig(final String emulationInputDir) {
		return new WmiEmulationConfig(WmiConfiguration.builder().hostname(HOSTNAME).build(), emulationInputDir);
	}

	@Test
	void testIsAcceptableExceptionAcceptsOnlyAMissingRecording() {
		assertTrue(executor.isAcceptableException(new NoRecordedResponseException("No recorded WMI response.")));
		assertTrue(executor.isAcceptableException(new ClientException("Failed", new NoRecordedResponseException("None"))));
		assertFalse(executor.isAcceptableException(new ClientException("Timeout")));
		assertFalse(executor.isAcceptableException(null));
	}

	@Test
	void testExecuteWmiMatch(@TempDir final Path tempDir) throws Exception {
		Files.writeString(tempDir.resolve("image.yaml"), """
			image:
			  - request:
			      wql: SELECT Name FROM Win32_ComputerSystem
			      namespace: root\\cimv2
			    response: r1.txt
			""", StandardCharsets.UTF_8);
		Files.writeString(
			tempDir.resolve("r1.txt"),
			SourceTable.tableToCsv(List.of(List.of("value1", "value2")), ";", true),
			StandardCharsets.UTF_8
		);

		final RequestStats requestStats = new RequestStats();
		final List<List<String>> result = executor.executeWmi(
			HOSTNAME,
			buildWmiEmulationConfig(tempDir.toString()),
			"SELECT Name FROM Win32_ComputerSystem",
			"root\\cimv2",
			null,
			requestStats
		);

		// A replay sends nothing: nothing is counted
		assertEquals(Map.of(), requestStats.snapshot());
		assertEquals(1, result.size());
		assertEquals("value1", result.get(0).get(0));
		assertEquals("value2", result.get(0).get(1));
	}

	@Test
	void testExecuteWmiRoundRobin(@TempDir final Path tempDir) throws Exception {
		Files.writeString(tempDir.resolve("image.yaml"), """
			image:
			  - request:
			      wql: SELECT Name FROM Win32_Process
			      namespace: root\\cimv2
			    response: r1.txt
			  - request:
			      wql: SELECT Name FROM Win32_Process
			      namespace: root\\cimv2
			    response: r2.txt
			""", StandardCharsets.UTF_8);
		Files.writeString(
			tempDir.resolve("r1.txt"),
			SourceTable.tableToCsv(List.of(List.of("first")), ";", true),
			StandardCharsets.UTF_8
		);
		Files.writeString(
			tempDir.resolve("r2.txt"),
			SourceTable.tableToCsv(List.of(List.of("second")), ";", true),
			StandardCharsets.UTF_8
		);

		final WmiEmulationConfig config = buildWmiEmulationConfig(tempDir.toString());

		final List<List<String>> result1 = executor.executeWmi(
			HOSTNAME,
			config,
			"SELECT Name FROM Win32_Process",
			"root\\cimv2",
			null,
			null
		);
		final List<List<String>> result2 = executor.executeWmi(
			HOSTNAME,
			config,
			"SELECT Name FROM Win32_Process",
			"root\\cimv2",
			null,
			null
		);
		final List<List<String>> result3 = executor.executeWmi(
			HOSTNAME,
			config,
			"SELECT Name FROM Win32_Process",
			"root\\cimv2",
			null,
			null
		);

		assertEquals("first", result1.get(0).get(0));
		assertEquals("second", result2.get(0).get(0));
		assertEquals("first", result3.get(0).get(0));
	}

	@Test
	void testExecuteWmiNoMatch(@TempDir final Path tempDir) throws Exception {
		Files.writeString(tempDir.resolve("image.yaml"), """
			image:
			  - request:
			      wql: SELECT Name FROM Win32_Service
			      namespace: root\\cimv2
			    response: r1.txt
			""", StandardCharsets.UTF_8);
		Files.writeString(
			tempDir.resolve("r1.txt"),
			SourceTable.tableToCsv(List.of(List.of("other")), ";", true),
			StandardCharsets.UTF_8
		);

		final ClientException e = assertThrows(
			ClientException.class,
			() -> executor.executeWmi(
				HOSTNAME,
				buildWmiEmulationConfig(tempDir.toString()),
				"SELECT Name FROM Win32_Process",
				"root\\cimv2",
				null,
				null
			)
		);
		assertTrue(e.getMessage().startsWith("No recorded WMI response for "), e.getMessage());

	}

	@Test
	void testExecuteWmiReturnsEmptyWhenQueryIsNull() throws ClientException {
		final List<List<String>> result = executor.executeWmi(
			HOSTNAME,
			buildWmiEmulationConfig("any"),
			null,
			"root\\cimv2",
			null,
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
			null,
			null
		);

		assertTrue(result.isEmpty());
	}

	@Test
	void testExecuteWmiFailsWhenDirectoryNotConfigured() throws ClientException {
		final ClientException e = assertThrows(
			ClientException.class,
			() -> executor.executeWmi(
				HOSTNAME,
				WmiConfiguration.builder().hostname(HOSTNAME).build(),
				"SELECT Name FROM Win32_Process",
				"root\\cimv2",
				null,
				null
			)
		);
		assertTrue(e.getMessage().endsWith(": the emulation input directory is not configured."), e.getMessage());

	}

	@Test
	void testExecuteWmiFailsWhenIndexIsMissing(@TempDir final Path tempDir) throws Exception {
		final ClientException e = assertThrows(
			ClientException.class,
			() -> executor.executeWmi(
				HOSTNAME,
				buildWmiEmulationConfig(tempDir.toString()),
				"SELECT Name FROM Win32_Process",
				"root\\cimv2",
				null,
				null
			)
		);
		assertTrue(e.getMessage().contains(": index file not found: "), e.getMessage());

	}

	@Test
	void testExecuteWmiFailsWhenIndexIsInvalid(@TempDir final Path tempDir) throws Exception {
		Files.writeString(tempDir.resolve("image.yaml"), "invalid: yaml: [[[", StandardCharsets.UTF_8);

		final ClientException e = assertThrows(
			ClientException.class,
			() -> executor.executeWmi(
				HOSTNAME,
				buildWmiEmulationConfig(tempDir.toString()),
				"SELECT Name FROM Win32_Process",
				"root\\cimv2",
				null,
				null
			)
		);
		assertTrue(e.getMessage().contains(": index parse error: "), e.getMessage());

	}

	@Test
	void testExecuteWmiNamespaceCaseSensitive(@TempDir final Path tempDir) throws Exception {
		Files.writeString(tempDir.resolve("image.yaml"), """
			image:
			  - request:
			      wql: SELECT Name FROM Win32_Process
			      namespace: ROOT\\CIMV2
			    response: r1.txt
			""", StandardCharsets.UTF_8);
		Files.writeString(
			tempDir.resolve("r1.txt"),
			SourceTable.tableToCsv(List.of(List.of("matched")), ";", true),
			StandardCharsets.UTF_8
		);

		final List<List<String>> result = executor.executeWmi(
			HOSTNAME,
			buildWmiEmulationConfig(tempDir.toString()),
			"SELECT Name FROM Win32_Process",
			"ROOT\\CIMV2",
			null,
			null
		);

		assertEquals(1, result.size());
		assertEquals("matched", result.get(0).get(0));
	}
}
