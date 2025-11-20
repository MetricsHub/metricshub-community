package org.metricshub.engine.strategy.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.function.UnaryOperator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.metricshub.engine.configuration.HostConfiguration;
import org.metricshub.engine.connector.model.identity.criterion.Criterion;
import org.metricshub.engine.connector.model.identity.criterion.HttpCriterion;
import org.metricshub.engine.connector.model.monitor.task.source.SnmpGetSource;
import org.metricshub.engine.connector.model.monitor.task.source.SnmpTableSource;
import org.metricshub.engine.connector.model.monitor.task.source.Source;
import org.metricshub.engine.strategy.detection.CriterionTestResult;
import org.metricshub.engine.strategy.source.SourceTable;
import org.metricshub.engine.telemetry.TelemetryManager;

class EmulationHelperTest {

	private static final String HOSTNAME = "test-host";
	private static final String CONNECTOR_ID = "connector-id";

	@Test
	void testPersistIfRequiredShouldPersistForNonSnmpSource(@TempDir Path tempDir) throws IOException {
		final Source nonSnmpSource = new TestSource("source:key");
		final SourceTable sourceTable = SourceTable
			.builder()
			.table(List.of(List.of("v1", "v2")))
			.headers(List.of("h1", "h2"))
			.build();

		final TelemetryManager telemetryManager = TelemetryManager
			.builder()
			.hostConfiguration(HostConfiguration.builder().hostname(HOSTNAME).build())
			.recordOutputDirectory(tempDir.toString())
			.build();

		EmulationHelper.persistIfRequired(nonSnmpSource, CONNECTOR_ID, telemetryManager, sourceTable, s -> true);

		final Optional<SourceTable> result = EmulationHelper.readEmulatedSourceTable(
			CONNECTOR_ID,
			nonSnmpSource,
			tempDir.toString(),
			telemetryManager
		);

		assertTrue(result.isPresent(), "Expected SourceTable to be persisted and readable");
		assertEquals(sourceTable.getTable(), result.get().getTable(), "Table data should match");
		assertEquals(sourceTable.getHeaders(), result.get().getHeaders(), "Headers should match");

		final String expectedFileName = HOSTNAME + "-" + CONNECTOR_ID + "-" + "source-key" + ".yaml";
		assertTrue(Files.exists(tempDir.resolve(expectedFileName)), "Expected emulated source file to exist");
	}

	@Test
	void testPersistIfRequiredShouldNotPersistForSnmpSources(@TempDir Path tempDir) throws IOException {
		final SourceTable sourceTable = SourceTable.builder().table(List.of(List.of("value"))).build();

		final TelemetryManager telemetryManager = TelemetryManager
			.builder()
			.hostConfiguration(HostConfiguration.builder().hostname(HOSTNAME).build())
			.recordOutputDirectory(tempDir.toString())
			.build();

		final SnmpGetSource snmpGetSource = SnmpGetSource
			.builder()
			.type("snmpGet")
			.oid("1.2.3.4")
			.key("snmpGet:key")
			.build();

		final SnmpTableSource snmpTableSource = SnmpTableSource
			.builder()
			.type("snmpTable")
			.oid("1.2.3.4")
			.selectColumns("1,2")
			.key("snmpTable:key")
			.build();

		EmulationHelper.persistIfRequired(snmpGetSource, CONNECTOR_ID, telemetryManager, sourceTable, s -> false);
		EmulationHelper.persistIfRequired(snmpTableSource, CONNECTOR_ID, telemetryManager, sourceTable, s -> false);

		try (var paths = Files.list(tempDir)) {
			assertFalse(paths.findAny().isPresent(), "Expected no files to be created for SNMP sources");
		}
	}

	@Test
	void testReadEmulatedSourceTableShouldReturnEmptyWhenFileMissing(@TempDir Path tempDir) {
		final Source nonSnmpSource = new TestSource("missing-source");

		final TelemetryManager telemetryManager = TelemetryManager
			.builder()
			.hostConfiguration(HostConfiguration.builder().hostname(HOSTNAME).build())
			.build();

		final Optional<SourceTable> result = EmulationHelper.readEmulatedSourceTable(
			CONNECTOR_ID,
			nonSnmpSource,
			tempDir.toString(),
			telemetryManager
		);

		assertTrue(result.isEmpty(), "Expected empty result when emulated file is missing");
	}

	@Test
	void testPersistCriterionResultShouldCreateExpectedYamlFile(@TempDir Path tempDir) throws IOException {
		final Criterion criterion = HttpCriterion.builder().type("http").forceSerialization(false).build();
		final CriterionTestResult criterionTestResult = CriterionTestResult.success(criterion, "OK");

		final TelemetryManager telemetryManager = TelemetryManager
			.builder()
			.hostConfiguration(HostConfiguration.builder().hostname(HOSTNAME).build())
			.recordOutputDirectory(tempDir.toString())
			.build();

		final int criterionId = 1;

		EmulationHelper.persist(
			criterionTestResult,
			CONNECTOR_ID,
			criterion,
			tempDir.toString(),
			criterionId,
			telemetryManager
		);

		final String expectedFileName = String.format(
			"%s-%s-%s-criterion%d.yaml",
			HOSTNAME,
			CONNECTOR_ID,
			criterion.getType(),
			criterionId
		);

		final Path expectedFile = tempDir.resolve(expectedFileName);

		assertTrue(Files.exists(expectedFile), "Expected criterion result YAML file to be created");

		final String fileContent = Files.readString(expectedFile, StandardCharsets.UTF_8);

		assertTrue(
			fileContent.contains("result: OK"),
			"Expected persisted YAML to contain the criterion test result value"
		);
	}

	@Test
	void testReadEmulatedCriterionResultShouldReturnEmptyWhenFileMissing(@TempDir Path tempDir) {
		final Criterion criterion = HttpCriterion.builder().type("http").forceSerialization(false).build();

		final TelemetryManager telemetryManager = TelemetryManager
			.builder()
			.hostConfiguration(HostConfiguration.builder().hostname(HOSTNAME).build())
			.build();

		final Optional<CriterionTestResult> result = EmulationHelper.readEmulatedCriterionResult(
			CONNECTOR_ID,
			criterion,
			tempDir.toString(),
			1,
			telemetryManager
		);

		assertTrue(result.isEmpty(), "Expected empty result when emulated criterion file is missing");
	}

	@Test
	void testReadEmulatedCriterionResultShouldReturnResultWhenFileExists(@TempDir Path tempDir) {
		final Criterion criterion = HttpCriterion.builder().type("http").forceSerialization(false).build();
		final CriterionTestResult criterionTestResult = CriterionTestResult.success(criterion, "OK");

		final TelemetryManager telemetryManager = TelemetryManager
			.builder()
			.hostConfiguration(HostConfiguration.builder().hostname(HOSTNAME).build())
			.recordOutputDirectory(tempDir.toString())
			.build();

		final int criterionId = 1;

		EmulationHelper.persist(
			criterionTestResult,
			CONNECTOR_ID,
			criterion,
			tempDir.toString(),
			criterionId,
			telemetryManager
		);

		final Optional<CriterionTestResult> loaded = EmulationHelper.readEmulatedCriterionResult(
			CONNECTOR_ID,
			criterion,
			tempDir.toString(),
			criterionId,
			telemetryManager
		);

		assertTrue(loaded.isPresent(), "Expected criterion result to be readable from YAML file");
		assertEquals("OK", loaded.get().getResult(), "Criterion test result value should match");
		assertTrue(loaded.get().isSuccess(), "Criterion test result should indicate success");
	}

	@Test
	void testReadEmulatedCriterionResultShouldReturnEmptyWhenFileContentIsInvalid(@TempDir Path tempDir)
		throws IOException {
		final Criterion criterion = HttpCriterion.builder().type("http").forceSerialization(false).build();

		final TelemetryManager telemetryManager = TelemetryManager
			.builder()
			.hostConfiguration(HostConfiguration.builder().hostname(HOSTNAME).build())
			.build();

		final int criterionId = 1;

		final String expectedFileName = String.format(
			"%s-%s-%s-criterion%d.yaml",
			HOSTNAME,
			CONNECTOR_ID,
			criterion.getType(),
			criterionId
		);

		final Path expectedFile = tempDir.resolve(expectedFileName);
		Files.writeString(expectedFile, "this is not valid criterion test result yaml: :::", StandardCharsets.UTF_8);

		final Optional<CriterionTestResult> result = EmulationHelper.readEmulatedCriterionResult(
			CONNECTOR_ID,
			criterion,
			tempDir.toString(),
			criterionId,
			telemetryManager
		);

		assertTrue(result.isEmpty(), "Expected empty result when criterion YAML content is invalid");
	}

	/**
	 * Test Source implementation for testing purposes.
	 */
	private static final class TestSource extends Source {

		private static final long serialVersionUID = 1L;

		TestSource(final String key) {
			super("test", null, false, key, null);
		}

		@Override
		public Source copy() {
			return new TestSource(getKey());
		}

		@Override
		public void update(final UnaryOperator<String> updater) {
			// no-op for tests
		}

		@Override
		public SourceTable accept(final org.metricshub.engine.strategy.source.ISourceProcessor sourceProcessor) {
			return null;
		}
	}
}
