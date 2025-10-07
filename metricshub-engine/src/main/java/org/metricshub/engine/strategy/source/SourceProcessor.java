package org.metricshub.engine.strategy.source;

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

import static org.metricshub.engine.common.helpers.MetricsHubConstants.NEW_LINE;
import static org.metricshub.engine.common.helpers.MetricsHubConstants.SEMICOLON;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.opentelemetry.instrumentation.annotations.SpanAttribute;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.metricshub.engine.client.ClientsExecutor;
import org.metricshub.engine.common.helpers.JsonHelper;
import org.metricshub.engine.common.helpers.TextTableHelper;
import org.metricshub.engine.connector.model.common.DeviceKind;
import org.metricshub.engine.connector.model.monitor.task.source.CommandLineSource;
import org.metricshub.engine.connector.model.monitor.task.source.CopySource;
import org.metricshub.engine.connector.model.monitor.task.source.HttpSource;
import org.metricshub.engine.connector.model.monitor.task.source.InternalDbQuerySource;
import org.metricshub.engine.connector.model.monitor.task.source.IpmiSource;
import org.metricshub.engine.connector.model.monitor.task.source.JawkSource;
import org.metricshub.engine.connector.model.monitor.task.source.JmxSource;
import org.metricshub.engine.connector.model.monitor.task.source.SnmpGetSource;
import org.metricshub.engine.connector.model.monitor.task.source.SnmpTableSource;
import org.metricshub.engine.connector.model.monitor.task.source.Source;
import org.metricshub.engine.connector.model.monitor.task.source.SqlSource;
import org.metricshub.engine.connector.model.monitor.task.source.StaticSource;
import org.metricshub.engine.connector.model.monitor.task.source.TableJoinSource;
import org.metricshub.engine.connector.model.monitor.task.source.TableUnionSource;
import org.metricshub.engine.connector.model.monitor.task.source.WbemSource;
import org.metricshub.engine.connector.model.monitor.task.source.WmiSource;
import org.metricshub.engine.extension.ExtensionManager;
import org.metricshub.engine.extension.ICompositeSourceScriptExtension;
import org.metricshub.engine.extension.IProtocolExtension;
import org.metricshub.engine.extension.ISourceComputationExtension;
import org.metricshub.engine.telemetry.TelemetryManager;

/**
 * The {@code SourceProcessor} class is responsible for processing various types of monitor sources.
 * It implements the {@link ISourceProcessor} interface and provides methods for executing source-related tasks.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Slf4j
public class SourceProcessor implements ISourceProcessor {

	// Create a YAML ObjectMapper to serialize SourceTable to YAML format
	private static final ObjectMapper YAML_MAPPER = JsonHelper.buildYamlMapper();

	private TelemetryManager telemetryManager;
	private String connectorId;
	private ClientsExecutor clientsExecutor;
	private ExtensionManager extensionManager;

	@WithSpan("Source Copy Exec")
	@Override
	public SourceTable process(@SpanAttribute("source.definition") final CopySource copySource) {
		final String hostname = telemetryManager.getHostname();

		if (copySource == null) {
			log.error(
				"Hostname {} - CopySource cannot be null, the CopySource operation will return an empty result.",
				hostname
			);
			return SourceTable.empty();
		}

		final String copyFrom = copySource.getFrom();

		if (copyFrom == null || copyFrom.isEmpty()) {
			log.error(
				"Hostname {} - CopySource reference cannot be null. Returning an empty table for source {}.",
				hostname,
				copySource
			);
			return SourceTable.empty();
		}

		final SourceTable sourceTable = new SourceTable();

		final Optional<SourceTable> maybeOrigin = SourceTable.lookupSourceTable(copyFrom, connectorId, telemetryManager);

		if (maybeOrigin.isEmpty()) {
			return SourceTable.empty();
		}

		final SourceTable origin = maybeOrigin.get();

		final List<List<String>> table = origin
			.getTable()
			.stream()
			// Map each row in the table to a new ArrayList, effectively performing a deep copy of each row.
			.map(ArrayList::new)
			.filter(row -> !row.isEmpty())
			.collect(Collectors.toList()); // NOSONAR

		sourceTable.setTable(table);

		if (origin.getRawData() != null) {
			sourceTable.setRawData(origin.getRawData());
		}

		logSourceCopy(connectorId, copyFrom, copySource.getKey(), sourceTable, hostname);

		return sourceTable;
	}

	@WithSpan("Source HTTP Exec")
	@Override
	public SourceTable process(@SpanAttribute("source.definition") final HttpSource httpSource) {
		return processSourceThroughExtension(httpSource);
	}

	/**
	 * Processes a given {@link Source} by using an appropriate {@link IProtocolExtension} found through
	 * an {@link ExtensionManager}. This method delegates the processing of the source to the protocol extension
	 * if available, or returns an empty {@link SourceTable} if no suitable extension is found.
	 *
	 * @param source The source data to be processed.
	 * @return A {@link SourceTable} containing the results from processing the source through the extension,
	 *         or an empty table if no extension can process the source.
	 */
	private SourceTable processSourceThroughExtension(Source source) {
		// Retrieve emulation input directory and read recorded source from it
		final String emulationInputDirectory = telemetryManager.getEmulationInputDirectory();

		// CHECKSTYLE:OFF
		if (
			!(source instanceof SnmpTableSource || source instanceof SnmpGetSource) &&
			emulationInputDirectory != null &&
			!emulationInputDirectory.isBlank()
		) {
			Optional<SourceTable> emulatedSourceTable = readEmulatedSourceTable(connectorId, source, emulationInputDirectory);
			return emulatedSourceTable.orElseGet(SourceTable::empty);
		}

		SourceTable table = extensionManager
			.findSourceExtension(source, telemetryManager)
			.map(ext -> ext.processSource(source, connectorId, telemetryManager))
			.orElseGet(SourceTable::empty);

		// Retrieve emulation output directory and persist source output if required and if not SNMP source
		final String recordOutputDirectory = telemetryManager.getRecordOutputDirectory();
		if (
			!(source instanceof SnmpTableSource || source instanceof SnmpGetSource) &&
			recordOutputDirectory != null &&
			!recordOutputDirectory.isBlank()
		) {
			persist(table, connectorId, source, recordOutputDirectory);
		}
		return table;
		//CHECKSTYLE:ON
	}

	/**
	 *
	 * @param sourceTable the {@link SourceTable} to persist
	 * @param connectorId the identifier of the connector defining the source
	 * @param source the {@link Source} that was processed
	 * @param recordOutputDirectory The directory to which we save recorded sources
	 */
	private void persist(
		final SourceTable sourceTable,
		final String connectorId,
		final Source source,
		final String recordOutputDirectory
	) {
		// Directory where we will store the sources results files
		// We will store 1 file per source, named as <connectorId><sourceKey>.yaml
		final Path sourceResultOutputDirectory = Paths.get(recordOutputDirectory);
		try {
			Files.createDirectories(sourceResultOutputDirectory);

			final String cleanKey = source.getKey().replace(":", "-");
			final Path file = sourceResultOutputDirectory.resolve(
				telemetryManager.getHostname() + "-" + connectorId + "-" + cleanKey + ".yaml"
			); // keep extension if you wish

			try (
				BufferedWriter out = Files.newBufferedWriter(
					file,
					StandardCharsets.UTF_8,
					StandardOpenOption.CREATE,
					StandardOpenOption.TRUNCATE_EXISTING
				)
			) {
				// Jackson YAMLFactory handles it
				YAML_MAPPER.writeValue(out, sourceTable);
				out.newLine();
			}
		} catch (IOException e) {
			log.warn(
				"Hostname {} - Could not write SourceTable to {}. Error: {}",
				telemetryManager.getHostname(),
				sourceResultOutputDirectory,
				e.getMessage()
			);
			log.debug("Hostname {} - Could not write SourceTable to {}", telemetryManager.getHostname(), source, e);
		}
	}

	/**
	 * Reads a {@link SourceTable} from the emulated source output directory based on the connector ID and source.
	 *
	 * @param connectorId The identifier of the connector defining the source.
	 * @param source      The source for which to read the {@link SourceTable}.
	 * @param emulationModeSourceOutputDirectory Source emulation input files directory
	 * @return An {@link Optional} containing the {@link SourceTable} if it exists, or empty if not found.
	 */
	private Optional<SourceTable> readEmulatedSourceTable(
		String connectorId,
		Source source,
		String emulationModeSourceOutputDirectory
	) {
		final Path outDir = Paths.get(emulationModeSourceOutputDirectory);
		final String cleanKey = source.getKey().replace(":", "-");
		// Content is YAML
		final Path file = outDir.resolve(telemetryManager.getHostname() + "-" + connectorId + "-" + cleanKey + ".yaml");

		try (BufferedReader in = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
			final SourceTable table = YAML_MAPPER.readValue(in, SourceTable.class);
			return Optional.of(table);
		} catch (IOException e) {
			log.warn(
				"Hostname {} - Could not read SourceTable from {}. Error: {}",
				telemetryManager.getHostname(),
				emulationModeSourceOutputDirectory,
				e.getMessage()
			);
			log.debug("Hostname {} - Could not read SourceTable from {}", telemetryManager.getHostname(), source, e);
			return Optional.empty();
		}
	}

	/**
	 * Processes a given {@link Source} by using an appropriate {@link ISourceComputationExtension} found through
	 * an {@link ExtensionManager}. This method delegates the processing of the source to the extension
	 * if available, or returns an empty {@link SourceTable} if no suitable extension is found.
	 *
	 * @param source The source data to be processed.
	 * @return A {@link SourceTable} containing the results from processing the source through the extension,
	 *         or an empty table if no extension can process the source.
	 */
	private SourceTable processSourceComputationThroughExtension(final Source source) {
		final Optional<ISourceComputationExtension> maybeExtension = extensionManager.findSourceComputationExtension(
			source
		);
		return maybeExtension
			.map(extension -> extension.processSource(source, connectorId, telemetryManager))
			.orElseGet(SourceTable::empty);
	}

	/**
	 * Processes a given {@link Source} by using an appropriate {@link ICompositeSourceScriptExtension} found through
	 * an {@link ExtensionManager}. This method delegates the processing of the source to the extension
	 * if available, or returns an empty {@link SourceTable} if no suitable extension is found.
	 *
	 * @param source The source data to be processed.
	 * @return A {@link SourceTable} containing the results from processing the source through the extension,
	 *         or an empty table if no extension can process the source.
	 */
	private SourceTable processCompositeSourceScriptThroughExtension(final Source source) {
		final Optional<ICompositeSourceScriptExtension> maybeExtension =
			extensionManager.findCompositeSourceScriptExtension(source);
		return maybeExtension
			.map(extension -> extension.processSource(source, connectorId, telemetryManager, this))
			.orElseGet(SourceTable::empty);
	}

	@WithSpan("Source IPMI Exec")
	@Override
	public SourceTable process(@SpanAttribute("source.definition") final IpmiSource ipmiSource) {
		final String hostname = telemetryManager.getHostname();

		if (ipmiSource == null) {
			log.error("Hostname {} - IPMI Source cannot be null, the IPMI operation will return an empty result.", hostname);
			return SourceTable.empty();
		}

		final DeviceKind hostType = telemetryManager.getHostConfiguration().getHostType();

		if (
			DeviceKind.WINDOWS.equals(hostType) ||
			DeviceKind.LINUX.equals(hostType) ||
			DeviceKind.SOLARIS.equals(hostType) ||
			DeviceKind.OOB.equals(hostType)
		) {
			return processSourceThroughExtension(ipmiSource);
		}

		log.info(
			"Hostname {} - Failed to process IPMI source. {} is an unsupported OS for IPMI. Returning an empty table.",
			hostname,
			hostType.name()
		);

		return SourceTable.empty();
	}

	@WithSpan("Source OS Command Exec")
	@Override
	public SourceTable process(@SpanAttribute("source.definition") final CommandLineSource commandLineSource) {
		return processSourceThroughExtension(commandLineSource);
	}

	@WithSpan("Source SNMP Get Exec")
	@Override
	public SourceTable process(@SpanAttribute("source.definition") final SnmpGetSource snmpGetSource) {
		return processSourceThroughExtension(snmpGetSource);
	}

	@WithSpan("Source SNMP Table Exec")
	@Override
	public SourceTable process(@SpanAttribute("source.definition") final SnmpTableSource snmpTableSource) {
		return processSourceThroughExtension(snmpTableSource);
	}

	@WithSpan("Source Static Exec")
	@Override
	public SourceTable process(@SpanAttribute("source.definition") final StaticSource staticSource) {
		final String hostname = telemetryManager.getHostname();

		if (staticSource == null) {
			log.error(
				"Hostname {} - Static Source cannot be null, the StaticSource operation will return an empty result.",
				hostname
			);
			return SourceTable.empty();
		}

		final String staticValue = staticSource.getValue();

		if (staticValue == null || staticValue.isEmpty()) {
			log.error(
				"Hostname {} - Static Source reference cannot be null. Returning an empty table for source {}.",
				hostname,
				staticSource
			);
			return SourceTable.empty();
		}

		log.debug(
			"Hostname {} - Got Static Source value [{}] referenced in source [{}].",
			hostname,
			staticValue,
			staticSource.getKey()
		);

		final SourceTable sourceTable = new SourceTable();

		final Optional<SourceTable> maybeStaticTable = SourceTable.lookupSourceTable(
			staticValue,
			connectorId,
			telemetryManager
		);

		if (maybeStaticTable.isEmpty()) {
			return SourceTable.empty();
		}

		// Note: In case of the static source getSourceTable never returns null
		final List<List<String>> table = maybeStaticTable
			.get()
			.getTable()
			.stream()
			// Map each row in the table to a new ArrayList, effectively performing a deep copy of each row.
			.map(ArrayList::new)
			.filter(row -> !row.isEmpty())
			.collect(Collectors.toList());

		sourceTable.setTable(table);
		sourceTable.setRawData(SourceTable.tableToCsv(sourceTable.getTable(), SEMICOLON, false));

		return sourceTable;
	}

	@WithSpan("Source TableJoin Exec")
	@Override
	public SourceTable process(@SpanAttribute("source.definition") final TableJoinSource tableJoinSource) {
		final String hostname = telemetryManager.getHostname();

		if (tableJoinSource == null) {
			log.error(
				"Hostname {} - Table Join Source cannot be null, the Table Join will return an empty result.",
				hostname
			);
			return SourceTable.empty();
		}

		if (tableJoinSource.getLeftTable() == null) {
			log.debug(
				"Hostname {} - Left table cannot be null, the Join {} will return an empty result.",
				hostname,
				tableJoinSource
			);
			return SourceTable.empty();
		}

		final Optional<SourceTable> maybeLeftTable = SourceTable.lookupSourceTable(
			tableJoinSource.getLeftTable(),
			connectorId,
			telemetryManager
		);
		if (maybeLeftTable.isEmpty()) {
			log.debug(
				"Hostname {} - Reference to Left table cannot be found, the Join {} will return an empty result.",
				hostname,
				tableJoinSource
			);
			return SourceTable.empty();
		}

		if (tableJoinSource.getRightTable() == null) {
			log.debug(
				"Hostname {} - Right table cannot be null, the Join {} will return an empty result.",
				hostname,
				tableJoinSource
			);
			return SourceTable.empty();
		}

		final Optional<SourceTable> maybeRightTable = SourceTable.lookupSourceTable(
			tableJoinSource.getRightTable(),
			connectorId,
			telemetryManager
		);
		if (maybeRightTable.isEmpty()) {
			log.debug(
				"Hostname {} - Reference to Right table cannot be found, the Join {} will return an empty result.",
				hostname,
				tableJoinSource
			);
			return SourceTable.empty();
		}

		if (tableJoinSource.getLeftKeyColumn() < 1 || tableJoinSource.getRightKeyColumn() < 1) {
			log.error(
				"Hostname {} - Invalid key column number (leftKeyColumnNumber={}, rightKeyColumnNumber={}).",
				tableJoinSource.getLeftKeyColumn(),
				tableJoinSource.getDefaultRightLine(),
				hostname
			);
			return SourceTable.empty();
		}

		final SourceTable leftTable = maybeLeftTable.get();
		final SourceTable rightTable = maybeRightTable.get();
		logTableJoin(
			tableJoinSource.getKey(),
			tableJoinSource.getLeftTable(),
			tableJoinSource.getRightTable(),
			leftTable,
			rightTable,
			hostname
		);

		final List<List<String>> executeTableJoin = clientsExecutor.executeTableJoin(
			leftTable.getTable(),
			rightTable.getTable(),
			tableJoinSource.getLeftKeyColumn(),
			tableJoinSource.getRightKeyColumn(),
			SourceTable.lineToList(tableJoinSource.getDefaultRightLine(), SEMICOLON),
			"wbem".equalsIgnoreCase(tableJoinSource.getKeyType()),
			true
		);

		SourceTable sourceTable = new SourceTable();

		if (executeTableJoin != null) {
			sourceTable.setTable(executeTableJoin);
		}
		return sourceTable;
	}

	/**
	 * Log the table join left and right tables
	 *
	 * @param sourceKey      the table join source key
	 * @param leftSourceKey  the source key referencing the left source
	 * @param rightSourceKey the source key referencing the right source
	 * @param leftTable      the left table
	 * @param rightTable     the right table
	 */
	private static void logTableJoin(
		final String sourceKey,
		final String leftSourceKey,
		final String rightSourceKey,
		final SourceTable leftTable,
		final SourceTable rightTable,
		final String hostname
	) {
		if (!log.isDebugEnabled()) {
			return;
		}

		log.debug(
			"Hostname {} - Table Join Source [{}]:\nLeft table [{}]:\n{}\nRight table [{}]:\n{}\n",
			hostname,
			sourceKey,
			leftSourceKey,
			TextTableHelper.generateTextTable(leftTable.getHeaders(), leftTable.getTable()),
			rightSourceKey,
			TextTableHelper.generateTextTable(rightTable.getHeaders(), rightTable.getTable())
		);
	}

	@WithSpan("Source TableUnion Exec")
	@Override
	public SourceTable process(@SpanAttribute("source.definition") final TableUnionSource tableUnionSource) {
		final String hostname = telemetryManager.getHostname();

		if (tableUnionSource == null) {
			log.warn(
				"Hostname {} - Table Union Source cannot be null, the Table Union operation will return an empty result.",
				hostname
			);
			return SourceTable.empty();
		}

		final List<String> unionTables = tableUnionSource.getTables();
		if (unionTables == null) {
			log.debug(
				"Hostname {} - Table list in the Union cannot be null, the Union operation {} will return an empty result.",
				hostname,
				tableUnionSource
			);
			return SourceTable.empty();
		}

		final List<SourceTable> sourceTablesToConcat = unionTables
			.stream()
			.map(key -> SourceTable.lookupSourceTable(key, connectorId, telemetryManager))
			.filter(Optional::isPresent)
			.map(Optional::get)
			.collect(Collectors.toList()); //NOSONAR

		final SourceTable sourceTable = new SourceTable();
		final List<List<String>> executeTableUnion = sourceTablesToConcat
			.stream()
			.map(SourceTable::getTable)
			.flatMap(Collection::stream)
			.collect(Collectors.toList()); //NOSONAR

		sourceTable.setTable(executeTableUnion);

		String rawData = sourceTablesToConcat
			.stream()
			.map(SourceTable::getRawData)
			.filter(Objects::nonNull)
			.collect(Collectors.joining(NEW_LINE))
			.replace("\n\n", NEW_LINE);

		sourceTable.setRawData(rawData);

		return sourceTable;
	}

	/**
	 * This method processes {@link WbemSource} instance
	 * @param wbemSource {@link WbemSource} instance
	 * @return {@link SourceTable} instance
	 */

	@WithSpan("Source WBEM HTTP Exec")
	@Override
	public SourceTable process(@SpanAttribute("source.definition") final WbemSource wbemSource) {
		return processSourceThroughExtension(wbemSource);
	}

	/**
	 * This method processes {@link WmiSource} source
	 * @param wmiSource {@link WmiSource} source instance
	 * @return {@link SourceTable} instance
	 */
	@WithSpan("Source WMI Exec")
	@Override
	public SourceTable process(@SpanAttribute("source.definition") final WmiSource wmiSource) {
		return processSourceThroughExtension(wmiSource);
	}

	/**
	 * Log the source copy data
	 *
	 * @param connectorId     the identifier of the connector defining the source
	 * @param parentSourceKey the parent source key referenced in the source copy
	 * @param childSourceKey  the source key referencing the parent source
	 * @param sourceTable     the source's result we wish to log
	 * @param hostname        the host's name
	 */
	private static void logSourceCopy(
		final String connectorId,
		final String parentSourceKey,
		final String childSourceKey,
		final SourceTable sourceTable,
		final String hostname
	) {
		if (!log.isDebugEnabled()) {
			return;
		}

		// Is there any raw data to log?
		if (sourceTable.getRawData() != null && (sourceTable.getTable() == null || sourceTable.getTable().isEmpty())) {
			log.debug(
				"Hostname {} - Got Source [{}] referenced in Source [{}]. Connector: [{}].\nRaw result:\n{}\n",
				hostname,
				parentSourceKey,
				childSourceKey,
				connectorId,
				sourceTable.getRawData()
			);
			return;
		}

		if (sourceTable.getRawData() == null) {
			log.debug(
				"Hostname {} - Got Source [{}] referenced in Source [{}]. Connector: [{}].\nTable result:\n{}\n",
				hostname,
				parentSourceKey,
				childSourceKey,
				connectorId,
				TextTableHelper.generateTextTable(sourceTable.getTable())
			);
			return;
		}

		log.debug(
			"Hostname {} - Got Source [{}] referenced in Source [{}]. Connector: [{}].\nRaw result:\n{}\nTable result:\n{}\n",
			hostname,
			parentSourceKey,
			childSourceKey,
			connectorId,
			sourceTable.getRawData(),
			TextTableHelper.generateTextTable(sourceTable.getTable())
		);
	}

	@WithSpan("Source InternalDbQuerySource Exec")
	@Override
	public SourceTable process(@SpanAttribute("source.definition") final InternalDbQuerySource internalDbQuery) {
		return processSourceComputationThroughExtension(internalDbQuery);
	}

	@WithSpan("Source JawkSource Exec")
	@Override
	public SourceTable process(@SpanAttribute("source.definition") final JawkSource jawkSource) {
		return processCompositeSourceScriptThroughExtension(jawkSource);
	}

	/**
	 * This method processes {@link SqlSource} source
	 * @param sqlSource {@link SqlSource} source instance
	 * @return {@link SourceTable} instance
	 */
	@WithSpan("Source SQL Exec")
	@Override
	public SourceTable process(@SpanAttribute("source.definition") final SqlSource sqlSource) {
		return processSourceThroughExtension(sqlSource);
	}

	/**
	 * This method processes {@link JmxSource} source
	 *
	 * @param jmxSource {@link JmxSource} source instance
	 * @return {@link SourceTable} instance
	 */
	@WithSpan("Source JMX Exec")
	@Override
	public SourceTable process(@SpanAttribute("source.definition") JmxSource jmxSource) {
		return processSourceThroughExtension(jmxSource);
	}
}
