package org.metricshub.engine.strategy.utils;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * MetricsHub Engine
 * ჻჻჻჻჻჻
 * Copyright 2023 - 2025 MetricsHub
 * ჻჻჻჻჻჻
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 * ╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱
 */

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Optional;
import java.util.function.Predicate;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.metricshub.engine.common.helpers.JsonHelper;
import org.metricshub.engine.common.helpers.StringHelper;
import org.metricshub.engine.connector.model.identity.criterion.Criterion;
import org.metricshub.engine.connector.model.monitor.task.source.Source;
import org.metricshub.engine.strategy.detection.CriterionTestResult;
import org.metricshub.engine.strategy.source.SourceTable;
import org.metricshub.engine.telemetry.TelemetryManager;

/**
 * Helper class for persisting and reading emulated source and criterion results.
 */
@NoArgsConstructor(access = lombok.AccessLevel.PRIVATE)
@Slf4j
public class EmulationHelper {

	// Create a YAML ObjectMapper to serialize SourceTable to YAML format
	private static final ObjectMapper YAML_MAPPER = JsonHelper.buildYamlMapper();

	/**
	 * Persist the given {@link SourceTable} if required by the TelemetryManager configuration.
	 *
	 * @param copy             the {@link Source} that was processed
	 * @param connectorId      the identifier of the connector defining the source
	 * @param telemetryManager the {@link TelemetryManager} to use
	 * @param table            the {@link SourceTable} to persist
	 * @param shouldPersist    predicate to determine if the source should be persisted
	 */
	public static void persistIfRequired(
		final Source copy,
		final String connectorId,
		final TelemetryManager telemetryManager,
		final SourceTable table,
		final Predicate<Source> shouldPersist
	) {
		// Retrieve emulation output directory and persist source output if required and if not SNMP source
		final String recordOutputDirectory = telemetryManager.getRecordOutputDirectory();
		if (StringHelper.nonNullNonBlank(recordOutputDirectory) && shouldPersist.test(copy)) {
			EmulationHelper.persist(table, connectorId, copy, recordOutputDirectory, telemetryManager);
		}
	}

	/**
	 * Persist the given {@link SourceTable} to a YAML file in the specified output directory.
	 *
	 * @param sourceTable the {@link SourceTable} to persist
	 * @param connectorId the identifier of the connector defining the source
	 * @param source the {@link Source} that was processed
	 * @param recordOutputDirectory The directory to which we save recorded sources
	 * @param telemetryManager the {@link TelemetryManager} to use
	 */
	public static void persist(
		final SourceTable sourceTable,
		final String connectorId,
		final Source source,
		final String recordOutputDirectory,
		final TelemetryManager telemetryManager
	) {
		final String hostname = telemetryManager.getHostname();

		// Directory where we will store the sources results files
		// We will store 1 file per source, named as <connectorId><sourceKey>.yaml
		final var sourceResultOutputDirectory = Paths.get(recordOutputDirectory);
		try {
			Files.createDirectories(sourceResultOutputDirectory);

			final var cleanKey = source.getKey().replace(":", "-");
			final var fileName = String.join("-", hostname, connectorId, cleanKey) + ".yaml";

			writeContent(sourceTable, sourceResultOutputDirectory, fileName);
		} catch (Exception e) {
			log.warn(
				"Hostname {} - Could not write SourceTable to {}. Error: {}",
				hostname,
				sourceResultOutputDirectory,
				e.getMessage()
			);
			log.debug("Hostname {} - Could not write SourceTable to {}", hostname, source, e);
		}
	}

	/**
	 * Reads a {@link SourceTable} from the emulated source output directory based on the connector ID and source.
	 *
	 * @param connectorId The identifier of the connector defining the source.
	 * @param source      The source for which to read the {@link SourceTable}.
	 * @param emulationModeSourceOutputDirectory Source emulation input files directory.
	 * @param telemetryManager The {@link TelemetryManager} to use.
	 * @return An {@link Optional} containing the {@link SourceTable} if it exists, or empty if not found.
	 */
	public static Optional<SourceTable> readEmulatedSourceTable(
		final String connectorId,
		final Source source,
		final String emulationModeSourceOutputDirectory,
		final TelemetryManager telemetryManager
	) {
		final var outDir = Paths.get(emulationModeSourceOutputDirectory);
		final var cleanKey = source.getKey().replace(":", "-");
		final var hostname = telemetryManager.getHostname();

		// Content is YAML
		final Path file = outDir.resolve(hostname + "-" + connectorId + "-" + cleanKey + ".yaml");

		try (var in = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
			final SourceTable table = YAML_MAPPER.readValue(in, SourceTable.class);
			return Optional.of(table);
		} catch (Exception e) {
			log.warn(
				"Hostname {} - Could not read SourceTable from {}. Error: {}",
				hostname,
				emulationModeSourceOutputDirectory,
				e.getMessage()
			);
			log.debug("Hostname {} - Could not read SourceTable from {}", hostname, source, e);
			return Optional.empty();
		}
	}

	/**
	 * Persists the {@link CriterionTestResult} to a YAML file.
	 *
	 * @param result the {@link CriterionTestResult} to persist
	 * @param connectorId the identifier of the connector defining the criterion
	 * @param criterion the {@link Criterion} that was processed
	 * @param recordOutputDirectory the directory to store the results
	 * @param criterionId the id of the criterion to persist
	 * @param telemetryManager the {@link TelemetryManager} to use
	 */
	public static void persist(
		final CriterionTestResult result,
		final String connectorId,
		final Criterion criterion,
		final String recordOutputDirectory,
		final int criterionId,
		final TelemetryManager telemetryManager
	) {
		final var outputDirectory = Paths.get(recordOutputDirectory);
		try {
			Files.createDirectories(outputDirectory);

			final var fileName = String.format(
				"%s-%s-%s-criterion%d.yaml",
				telemetryManager.getHostname(),
				connectorId,
				criterion.getType(),
				criterionId
			);

			writeContent(result, outputDirectory, fileName);
		} catch (Exception e) {
			log.warn(
				"Hostname {} - Could not write ConnectorTestResult to {}. Error: {}",
				telemetryManager.getHostname(),
				outputDirectory,
				e.getMessage()
			);
			log.debug(
				"Hostname {} - Could not write ConnectorTestResult to {}",
				telemetryManager.getHostname(),
				outputDirectory,
				e
			);
		}
	}

	/**
	 * Writes the given content to a file in the specified output directory.
	 *
	 * @param <T>             the type of the content to write
	 * @param result          the content to write
	 * @param outputDirectory the directory to write the file to
	 * @param fileName        the name of the file to write
	 * @throws IOException if an I/O error occurs
	 */
	private static <T> void writeContent(final T result, final Path outputDirectory, final String fileName)
		throws IOException {
		final Path file = outputDirectory.resolve(fileName);

		try (
			var out = Files.newBufferedWriter(
				file,
				StandardCharsets.UTF_8,
				StandardOpenOption.CREATE,
				StandardOpenOption.TRUNCATE_EXISTING
			)
		) {
			YAML_MAPPER.writeValue(out, result);
		}
	}

	/**
	 * Reads a {@link CriterionTestResult} from the emulated criterion output directory.
	 *
	 * @param connectorId the identifier of the connector defining the criterion
	 * @param criterion the {@link Criterion} to load results for
	 * @param emulationModeCriterionOutputDirectory directory containing emulated results
	 * @param criterionId the id of the criterion to load
	 * @return An {@link Optional} with the {@link CriterionTestResult} if found, or empty otherwise
	 */
	public static Optional<CriterionTestResult> readEmulatedCriterionResult(
		final String connectorId,
		final Criterion criterion,
		final String emulationModeCriterionOutputDirectory,
		final int criterionId,
		final TelemetryManager telemetryManager
	) {
		final var outDir = Paths.get(emulationModeCriterionOutputDirectory);
		final var expectedFileName = String.format(
			"%s-%s-%s-criterion%d.yaml",
			telemetryManager.getHostname(),
			connectorId,
			criterion.getType(),
			criterionId
		);

		final Path expectedFile = outDir.resolve(expectedFileName);

		if (!Files.exists(expectedFile)) {
			log.debug(
				"Hostname {} - No emulated CriterionTestResult found for {}",
				telemetryManager.getHostname(),
				expectedFileName
			);
			return Optional.empty();
		}

		try (var in = Files.newBufferedReader(expectedFile, StandardCharsets.UTF_8)) {
			final CriterionTestResult result = YAML_MAPPER.readValue(in, CriterionTestResult.class);
			return Optional.of(result);
		} catch (Exception e) {
			log.warn(
				"Hostname {} - Could not read CriterionTestResult from {}. Error: {}",
				telemetryManager.getHostname(),
				expectedFileName,
				e.getMessage()
			);
			log.debug(
				"Hostname {} - Could not read CriterionTestResult from {}",
				telemetryManager.getHostname(),
				expectedFileName,
				e
			);
			return Optional.empty();
		}
	}
}
