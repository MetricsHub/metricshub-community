package org.metricshub.extension.emulation.wbem;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
import org.metricshub.engine.configuration.HostConfiguration;
import org.metricshub.engine.connector.model.common.DeviceKind;
import org.metricshub.engine.strategy.source.SourceResult;
import org.metricshub.engine.telemetry.TelemetryManager;
import org.metricshub.extension.emulation.EmulationConfiguration;
import org.metricshub.extension.emulation.EmulationImageCacheManager;
import org.metricshub.extension.emulation.EmulationRoundRobinManager;
import org.metricshub.extension.emulation.NoRecordedResponseException;
import org.metricshub.extension.wbem.WbemConfiguration;

/**
 * Tests for {@link EmulationWbemRequestExecutor}.
 */
class EmulationWbemRequestExecutorTest {

	private static final String HOSTNAME = "test-host";

	private final EmulationWbemRequestExecutor executor = new EmulationWbemRequestExecutor(
		new EmulationRoundRobinManager(),
		new EmulationImageCacheManager()
	);

	private TelemetryManager buildTelemetryManager(final String emulationInputDir) {
		return TelemetryManager.builder()
			.hostConfiguration(
				HostConfiguration.builder()
					.hostname(HOSTNAME)
					.hostId(HOSTNAME)
					.hostType(DeviceKind.WINDOWS)
					.configurations(
						Map.of(
							EmulationConfiguration.class,
							EmulationConfiguration.builder()
								.hostname(HOSTNAME)
								.wbem(
									new WbemEmulationConfig(WbemConfiguration.builder().hostname(HOSTNAME).build(), emulationInputDir)
								)
								.build()
						)
					)
					.build()
			)
			.build();
	}

	@Test
	void testIsAcceptableExceptionAcceptsAMissingRecording() {
		assertTrue(executor.isAcceptableException(new NoRecordedResponseException("No recorded WBEM response.")));
		assertTrue(executor.isAcceptableException(new ClientException("Failed", new NoRecordedResponseException("None"))));
		assertFalse(executor.isAcceptableException(new ClientException("Timeout")));
	}

	@Test
	void testExecuteWbemMatch(@TempDir final Path tempDir) throws Exception {
		Files.writeString(tempDir.resolve("image.yaml"), """
			image:
			  - request:
			      wql: SELECT Name FROM CIM_ManagedElement
			      namespace: root/emc
			    response: r1.txt
			""", StandardCharsets.UTF_8);
		Files.writeString(
			tempDir.resolve("r1.txt"),
			SourceResult.tableToCsv(List.of(List.of("value1", "value2")), ";", true),
			StandardCharsets.UTF_8
		);

		final TelemetryManager telemetryManager = buildTelemetryManager(tempDir.toString());
		final List<List<String>> result = executor.executeWbem(
			HOSTNAME,
			WbemConfiguration.builder().hostname(HOSTNAME).build(),
			"SELECT Name FROM CIM_ManagedElement",
			"root/emc",
			telemetryManager
		);

		assertEquals(1, result.size());
		assertEquals("value1", result.get(0).get(0));
		assertEquals("value2", result.get(0).get(1));
		// Emulation sends no request: nothing is counted
		assertTrue(telemetryManager.getRequestStats().snapshot().isEmpty());
	}

	@Test
	void testExecuteWbemRoundRobin(@TempDir final Path tempDir) throws Exception {
		Files.writeString(tempDir.resolve("image.yaml"), """
			image:
			  - request:
			      wql: SELECT Name FROM CIM_ManagedElement
			      namespace: root/emc
			    response: r1.txt
			  - request:
			      wql: SELECT Name FROM CIM_ManagedElement
			      namespace: root/emc
			    response: r2.txt
			""", StandardCharsets.UTF_8);
		Files.writeString(
			tempDir.resolve("r1.txt"),
			SourceResult.tableToCsv(List.of(List.of("first")), ";", true),
			StandardCharsets.UTF_8
		);
		Files.writeString(
			tempDir.resolve("r2.txt"),
			SourceResult.tableToCsv(List.of(List.of("second")), ";", true),
			StandardCharsets.UTF_8
		);

		final TelemetryManager telemetryManager = buildTelemetryManager(tempDir.toString());
		final WbemConfiguration wbemConfig = WbemConfiguration.builder().hostname(HOSTNAME).build();

		final List<List<String>> result1 = executor.executeWbem(
			HOSTNAME,
			wbemConfig,
			"SELECT Name FROM CIM_ManagedElement",
			"root/emc",
			telemetryManager
		);
		final List<List<String>> result2 = executor.executeWbem(
			HOSTNAME,
			wbemConfig,
			"SELECT Name FROM CIM_ManagedElement",
			"root/emc",
			telemetryManager
		);
		final List<List<String>> result3 = executor.executeWbem(
			HOSTNAME,
			wbemConfig,
			"SELECT Name FROM CIM_ManagedElement",
			"root/emc",
			telemetryManager
		);

		assertEquals("first", result1.get(0).get(0));
		assertEquals("second", result2.get(0).get(0));
		assertEquals("first", result3.get(0).get(0));
	}

	@Test
	void testExecuteWbemNoMatch(@TempDir final Path tempDir) throws Exception {
		Files.writeString(tempDir.resolve("image.yaml"), """
			image:
			  - request:
			      wql: SELECT Name FROM Other_Class
			      namespace: root/cimv2
			    response: r1.txt
			""", StandardCharsets.UTF_8);
		Files.writeString(
			tempDir.resolve("r1.txt"),
			SourceResult.tableToCsv(List.of(List.of("other")), ";", true),
			StandardCharsets.UTF_8
		);

		assertThrows(
			ClientException.class,
			() -> executor.executeWbem(
				HOSTNAME,
				WbemConfiguration.builder().hostname(HOSTNAME).build(),
				"SELECT Name FROM CIM_ManagedElement",
				"root/emc",
				buildTelemetryManager(tempDir.toString())
			)
		);

	}

	@Test
	void testExecuteWbemReturnsEmptyWhenQueryIsNull() throws ClientException {
		final List<List<String>> result = executor.executeWbem(
			HOSTNAME,
			WbemConfiguration.builder().hostname(HOSTNAME).build(),
			null,
			"root/emc",
			buildTelemetryManager("any")
		);

		assertTrue(result.isEmpty());
	}

	@Test
	void testExecuteWbemReturnsEmptyWhenNamespaceIsNull() throws ClientException {
		final List<List<String>> result = executor.executeWbem(
			HOSTNAME,
			WbemConfiguration.builder().hostname(HOSTNAME).build(),
			"SELECT Name FROM CIM_ManagedElement",
			null,
			buildTelemetryManager("any")
		);

		assertTrue(result.isEmpty());
	}

	@Test
	void testExecuteWbemReturnsEmptyWhenTelemetryManagerIsNull() throws ClientException {
		final List<List<String>> result = executor.executeWbem(
			HOSTNAME,
			WbemConfiguration.builder().hostname(HOSTNAME).build(),
			"SELECT Name FROM CIM_ManagedElement",
			"root/emc",
			null
		);

		assertTrue(result.isEmpty());
	}

	@Test
	void testExecuteWbemFailsWhenIndexIsMissing(@TempDir final Path tempDir) throws Exception {
		Files.writeString(
			tempDir.resolve("r1.txt"),
			SourceResult.tableToCsv(List.of(List.of("value1")), ";", true),
			StandardCharsets.UTF_8
		);

		assertThrows(
			ClientException.class,
			() -> executor.executeWbem(
				HOSTNAME,
				WbemConfiguration.builder().hostname(HOSTNAME).build(),
				"SELECT Name FROM CIM_ManagedElement",
				"root/emc",
				buildTelemetryManager(tempDir.toString())
			)
		);

	}

	@Test
	void testExecuteWbemFailsWhenIndexIsInvalid(@TempDir final Path tempDir) throws Exception {
		Files.writeString(tempDir.resolve("image.yaml"), "not: [valid", StandardCharsets.UTF_8);

		assertThrows(
			ClientException.class,
			() -> executor.executeWbem(
				HOSTNAME,
				WbemConfiguration.builder().hostname(HOSTNAME).build(),
				"SELECT Name FROM CIM_ManagedElement",
				"root/emc",
				buildTelemetryManager(tempDir.toString())
			)
		);

	}

	@Test
	void testExecuteWbemFailsWhenMatchedResponseIsBlank(@TempDir final Path tempDir) throws Exception {
		Files.writeString(tempDir.resolve("image.yaml"), """
			image:
			  - request:
			      wql: SELECT Name FROM CIM_ManagedElement
			      namespace: root/emc
			    response: " "
			""", StandardCharsets.UTF_8);

		assertThrows(
			ClientException.class,
			() -> executor.executeWbem(
				HOSTNAME,
				WbemConfiguration.builder().hostname(HOSTNAME).build(),
				"SELECT Name FROM CIM_ManagedElement",
				"root/emc",
				buildTelemetryManager(tempDir.toString())
			)
		);

	}

	@Test
	void testExecuteWbemFailsWhenResponseFileIsMissing(@TempDir final Path tempDir) throws Exception {
		Files.writeString(tempDir.resolve("image.yaml"), """
			image:
			  - request:
			      wql: SELECT Name FROM CIM_ManagedElement
			      namespace: root/emc
			    response: missing.txt
			""", StandardCharsets.UTF_8);

		assertThrows(
			ClientException.class,
			() -> executor.executeWbem(
				HOSTNAME,
				WbemConfiguration.builder().hostname(HOSTNAME).build(),
				"SELECT Name FROM CIM_ManagedElement",
				"root/emc",
				buildTelemetryManager(tempDir.toString())
			)
		);

	}

	@Test
	void testFindMatchingEntriesIgnoresNullEntriesAndMatchesNamespaceCaseSensitively() {
		final WbemEmulationRequest request = new WbemEmulationRequest();
		request.setWql("SELECT Name FROM CIM_ManagedElement");
		request.setNamespace("ROOT/EMC");

		final WbemEmulationEntry matchingEntry = new WbemEmulationEntry();
		matchingEntry.setRequest(request);
		matchingEntry.setResponse("r1.txt");

		final WbemEmulationEntry nullRequestEntry = new WbemEmulationEntry();

		final List<WbemEmulationEntry> entries = new ArrayList<>();
		entries.add(null);
		entries.add(nullRequestEntry);
		entries.add(matchingEntry);

		final List<WbemEmulationEntry> result = executor.findMatchingEntries(
			entries,
			"SELECT Name FROM CIM_ManagedElement",
			"ROOT/EMC"
		);

		assertEquals(1, result.size());
		assertEquals("r1.txt", result.get(0).getResponse());
	}
}
