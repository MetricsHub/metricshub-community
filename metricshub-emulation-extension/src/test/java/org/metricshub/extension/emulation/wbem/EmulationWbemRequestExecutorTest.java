package org.metricshub.extension.emulation.wbem;

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
import org.metricshub.extension.emulation.EmulationImageCacheManager;
import org.metricshub.extension.emulation.EmulationRoundRobinManager;
import org.metricshub.extension.emulation.WbemEmulationConfig;
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
	void testExecuteWbemMatch(@TempDir final Path tempDir) throws Exception {
		Files.writeString(
			tempDir.resolve("image.yaml"),
			"""
			image:
			  - request:
			      wql: SELECT Name FROM CIM_ManagedElement
			      namespace: root/emc
			    response: r1.txt
			""",
			StandardCharsets.UTF_8
		);
		Files.writeString(
			tempDir.resolve("r1.txt"),
			SourceTable.tableToCsv(List.of(List.of("value1", "value2")), MetricsHubConstants.TABLE_SEP, true),
			StandardCharsets.UTF_8
		);

		final List<List<String>> result = executor.executeWbem(
			HOSTNAME,
			WbemConfiguration.builder().hostname(HOSTNAME).build(),
			"SELECT Name FROM CIM_ManagedElement",
			"root/emc",
			buildTelemetryManager(tempDir.toString()),
			null
		);

		assertEquals(1, result.size());
		assertEquals("value1", result.get(0).get(0));
		assertEquals("value2", result.get(0).get(1));
	}

	@Test
	void testExecuteWbemRoundRobin(@TempDir final Path tempDir) throws Exception {
		Files.writeString(
			tempDir.resolve("image.yaml"),
			"""
			image:
			  - request:
			      wql: SELECT Name FROM CIM_ManagedElement
			      namespace: root/emc
			    response: r1.txt
			  - request:
			      wql: SELECT Name FROM CIM_ManagedElement
			      namespace: root/emc
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

		final TelemetryManager telemetryManager = buildTelemetryManager(tempDir.toString());
		final WbemConfiguration wbemConfig = WbemConfiguration.builder().hostname(HOSTNAME).build();

		final List<List<String>> result1 = executor.executeWbem(
			HOSTNAME,
			wbemConfig,
			"SELECT Name FROM CIM_ManagedElement",
			"root/emc",
			telemetryManager,
			null
		);
		final List<List<String>> result2 = executor.executeWbem(
			HOSTNAME,
			wbemConfig,
			"SELECT Name FROM CIM_ManagedElement",
			"root/emc",
			telemetryManager,
			null
		);
		final List<List<String>> result3 = executor.executeWbem(
			HOSTNAME,
			wbemConfig,
			"SELECT Name FROM CIM_ManagedElement",
			"root/emc",
			telemetryManager,
			null
		);

		assertEquals("first", result1.get(0).get(0));
		assertEquals("second", result2.get(0).get(0));
		assertEquals("first", result3.get(0).get(0));
	}

	@Test
	void testExecuteWbemNoMatch(@TempDir final Path tempDir) throws Exception {
		Files.writeString(
			tempDir.resolve("image.yaml"),
			"""
			image:
			  - request:
			      wql: SELECT Name FROM Other_Class
			      namespace: root/cimv2
			    response: r1.txt
			""",
			StandardCharsets.UTF_8
		);
		Files.writeString(
			tempDir.resolve("r1.txt"),
			SourceTable.tableToCsv(List.of(List.of("other")), MetricsHubConstants.TABLE_SEP, true),
			StandardCharsets.UTF_8
		);

		final List<List<String>> result = executor.executeWbem(
			HOSTNAME,
			WbemConfiguration.builder().hostname(HOSTNAME).build(),
			"SELECT Name FROM CIM_ManagedElement",
			"root/emc",
			buildTelemetryManager(tempDir.toString()),
			null
		);

		assertTrue(result.isEmpty());
	}

	@Test
	void testExecuteWbemReturnsEmptyWhenQueryIsNull() throws ClientException {
		final List<List<String>> result = executor.executeWbem(
			HOSTNAME,
			WbemConfiguration.builder().hostname(HOSTNAME).build(),
			null,
			"root/emc",
			buildTelemetryManager("any"),
			null
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
			buildTelemetryManager("any"),
			null
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
			null,
			null
		);

		assertTrue(result.isEmpty());
	}

	@Test
	void testExecuteWbemReturnsEmptyWhenIndexIsMissing(@TempDir final Path tempDir) throws Exception {
		Files.writeString(
			tempDir.resolve("r1.txt"),
			SourceTable.tableToCsv(List.of(List.of("value1")), MetricsHubConstants.TABLE_SEP, true),
			StandardCharsets.UTF_8
		);

		final List<List<String>> result = executor.executeWbem(
			HOSTNAME,
			WbemConfiguration.builder().hostname(HOSTNAME).build(),
			"SELECT Name FROM CIM_ManagedElement",
			"root/emc",
			buildTelemetryManager(tempDir.toString()),
			null
		);

		assertTrue(result.isEmpty());
	}

	@Test
	void testExecuteWbemReturnsEmptyWhenIndexIsInvalid(@TempDir final Path tempDir) throws Exception {
		Files.writeString(tempDir.resolve("image.yaml"), "not: [valid", StandardCharsets.UTF_8);

		final List<List<String>> result = executor.executeWbem(
			HOSTNAME,
			WbemConfiguration.builder().hostname(HOSTNAME).build(),
			"SELECT Name FROM CIM_ManagedElement",
			"root/emc",
			buildTelemetryManager(tempDir.toString()),
			null
		);

		assertTrue(result.isEmpty());
	}

	@Test
	void testExecuteWbemReturnsEmptyWhenMatchedResponseIsBlank(@TempDir final Path tempDir) throws Exception {
		Files.writeString(
			tempDir.resolve("image.yaml"),
			"""
			image:
			  - request:
			      wql: SELECT Name FROM CIM_ManagedElement
			      namespace: root/emc
			    response: " "
			""",
			StandardCharsets.UTF_8
		);

		final List<List<String>> result = executor.executeWbem(
			HOSTNAME,
			WbemConfiguration.builder().hostname(HOSTNAME).build(),
			"SELECT Name FROM CIM_ManagedElement",
			"root/emc",
			buildTelemetryManager(tempDir.toString()),
			null
		);

		assertTrue(result.isEmpty());
	}

	@Test
	void testExecuteWbemReturnsEmptyWhenResponseFileIsMissing(@TempDir final Path tempDir) throws Exception {
		Files.writeString(
			tempDir.resolve("image.yaml"),
			"""
			image:
			  - request:
			      wql: SELECT Name FROM CIM_ManagedElement
			      namespace: root/emc
			    response: missing.txt
			""",
			StandardCharsets.UTF_8
		);

		final List<List<String>> result = executor.executeWbem(
			HOSTNAME,
			WbemConfiguration.builder().hostname(HOSTNAME).build(),
			"SELECT Name FROM CIM_ManagedElement",
			"root/emc",
			buildTelemetryManager(tempDir.toString()),
			null
		);

		assertTrue(result.isEmpty());
	}

	@Test
	void testFindMatchingEntriesIgnoresNullEntriesAndMatchesNamespaceCaseInsensitively() {
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
			"root/emc"
		);

		assertEquals(1, result.size());
		assertEquals("r1.txt", result.get(0).getResponse());
	}
}
