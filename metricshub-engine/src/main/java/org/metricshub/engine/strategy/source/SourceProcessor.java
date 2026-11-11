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
import static org.metricshub.engine.common.helpers.MetricsHubConstants.WINDOWS_NEW_LINE;

import io.opentelemetry.instrumentation.annotations.SpanAttribute;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.metricshub.engine.client.ClientsExecutor;
import org.metricshub.engine.common.helpers.TextTableHelper;
import org.metricshub.engine.connector.model.common.ContentType;
import org.metricshub.engine.connector.model.monitor.task.source.CommandLineSource;
import org.metricshub.engine.connector.model.monitor.task.source.CopySource;
import org.metricshub.engine.connector.model.monitor.task.source.EventLogSource;
import org.metricshub.engine.connector.model.monitor.task.source.FileSource;
import org.metricshub.engine.connector.model.monitor.task.source.HttpSource;
import org.metricshub.engine.connector.model.monitor.task.source.InternalDbQuerySource;
import org.metricshub.engine.connector.model.monitor.task.source.IpmiRawSource;
import org.metricshub.engine.connector.model.monitor.task.source.IpmiSelSource;
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

	private TelemetryManager telemetryManager;
	private String connectorId;
	private ClientsExecutor clientsExecutor;
	private ExtensionManager extensionManager;

	/**
	 * Monitor attributes of the current mono-instance collect, used to resolve ${attribute::...} references.
	 */
	private Map<String, String> attributes;

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

		final Optional<SourceTable> maybeOrigin = SourceTable.lookupSourceTable(copyFrom, connectorId, telemetryManager);

		if (maybeOrigin.isEmpty()) {
			return SourceTable.empty();
		}

		final SourceTable origin = maybeOrigin.get();

		// A text is copied as is, a table row by row (a deep copy), without its rows of no cell
		final SourceTable sourceTable = origin.getContentType() == ContentType.TEXT
			? SourceTable.ofText(origin.getText())
			: SourceTable.ofTable(
				origin.getTable().stream().map(ArrayList::new).filter(row -> !row.isEmpty()).collect(Collectors.toList()) // NOSONAR
			);

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
		return extensionManager.findSourceExtension(source, telemetryManager)
			.map(ext -> ext.processSource(source, connectorId, telemetryManager))
			.orElseGet(SourceTable::empty);
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
		return maybeExtension.map(extension -> extension.processSource(source, connectorId, telemetryManager))
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
		final Optional<ICompositeSourceScriptExtension> maybeExtension = extensionManager
			.findCompositeSourceScriptExtension(source);
		return maybeExtension.map(extension -> extension.processSource(source, connectorId, telemetryManager, this))
			.orElseGet(SourceTable::empty);
	}

	@WithSpan("Source IPMI Exec")
	@Override
	public SourceTable process(@SpanAttribute("source.definition") final IpmiSource ipmiSource) {
		return processSourceThroughExtension(ipmiSource);
	}

	@WithSpan("Source IPMI SEL Exec")
	@Override
	public SourceTable process(@SpanAttribute("source.definition") final IpmiSelSource ipmiSelSource) {
		return processSourceThroughExtension(ipmiSelSource);
	}

	@WithSpan("Source IPMI Raw Exec")
	@Override
	public SourceTable process(@SpanAttribute("source.definition") final IpmiRawSource ipmiRawSource) {
		return processSourceThroughExtension(ipmiRawSource);
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
		final List<List<String>> rows;

		if (staticSource.getCells() != null) {
			// The list form is the single row of exactly these cells. A line break a reference resolved into at run time
			// becomes a space, so the row stays one line for a text consumer (a toText)
			rows = List.of(
				staticSource.getCells().stream().map(cell -> cell == null ? null : cell.replaceAll("[\r\n]+", " ")).toList()
			);
		} else if (staticValue == null || staticValue.isEmpty()) {
			log.error(
				"Hostname {} - Static Source reference cannot be null. Returning an empty table for source {}.",
				hostname,
				staticSource
			);
			return SourceTable.empty();
		} else {
			log.debug(
				"Hostname {} - Got Static Source value [{}] referenced in source [{}].",
				hostname,
				staticValue,
				staticSource.getKey()
			);

			final Optional<SourceTable> maybeStaticTable = SourceTable.lookupSourceTable(
				staticValue,
				connectorId,
				telemetryManager
			);

			if (maybeStaticTable.isEmpty()) {
				return SourceTable.empty();
			}

			// Note: In case of the static source getSourceTable never returns null
			rows = maybeStaticTable.get().getTable();
		}

		return SourceTable.ofTable(
			rows.stream()
				// Map each row in the table to a new ArrayList, effectively performing a deep copy of each row.
				.map(ArrayList::new)
				.filter(row -> !row.isEmpty())
				.collect(Collectors.toList())
		);
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
				hostname,
				tableJoinSource.getLeftKeyColumn(),
				tableJoinSource.getRightKeyColumn()
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
			// The list form is the exact cells; the string form is split on ';' as it always was
			tableJoinSource.getDefaultRightLineCells() != null
				? tableJoinSource.getDefaultRightLineCells()
				: SourceTable.lineToList(tableJoinSource.getDefaultRightLine(), SEMICOLON),
			"wbem".equalsIgnoreCase(tableJoinSource.getKeyType()),
			true
		);

		return SourceTable.ofTable(executeTableJoin);
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
			TextTableHelper.generateTextTable(leftTable.getTable()),
			rightSourceKey,
			TextTableHelper.generateTextTable(rightTable.getTable())
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

		final List<SourceTable> sourceTablesToConcat = unionTables.stream()
			.map(key -> SourceTable.lookupSourceTable(key, connectorId, telemetryManager))
			.filter(Optional::isPresent)
			.map(Optional::get)
			.collect(Collectors.toList()); //NOSONAR

		// A text union when every member that holds something is a text: the non-empty texts, CRLF converted to LF, one
		// after the other, a line feed added only after a member that does not end with one; the blank lines of the
		// members are kept. Otherwise the rows of the members, copied (a compute on the union must not change them)
		final boolean textUnion = sourceTablesToConcat.stream()
			.anyMatch(member -> member.getContentType() == ContentType.TEXT) &&
			sourceTablesToConcat.stream().allMatch(member -> member.getContentType() == ContentType.TEXT || member.isEmpty());
		if (textUnion) {
			final StringBuilder text = new StringBuilder();
			sourceTablesToConcat.stream()
				.filter(member -> member.getContentType() == ContentType.TEXT && !member.isEmpty())
				.map(member -> member.getText().replace(WINDOWS_NEW_LINE, NEW_LINE))
				.forEach(memberText -> {
					if (!text.isEmpty() && text.charAt(text.length() - 1) != '\n') {
						text.append(NEW_LINE);
					}
					text.append(memberText);
				});
			return SourceTable.ofText(text.toString());
		}

		return SourceTable.ofTable(
			sourceTablesToConcat.stream()
				.map(SourceTable::getTable)
				.flatMap(Collection::stream)
				.map(row -> row == null ? null : new ArrayList<>(row))
				.collect(Collectors.toList()) //NOSONAR
		);
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

		if (sourceTable.getContentType() == ContentType.TEXT) {
			log.debug(
				"Hostname {} - Got Source [{}] referenced in Source [{}]. Connector: [{}].\nRaw result:\n{}\n",
				hostname,
				parentSourceKey,
				childSourceKey,
				connectorId,
				sourceTable.getText()
			);
			return;
		}

		log.debug(
			"Hostname {} - Got Source [{}] referenced in Source [{}]. Connector: [{}].\nTable result:\n{}\n",
			hostname,
			parentSourceKey,
			childSourceKey,
			connectorId,
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
	@WithSpan("Source JmxSource Exec")
	@Override
	public SourceTable process(@SpanAttribute("source.definition") JmxSource jmxSource) {
		return processSourceThroughExtension(jmxSource);
	}

	/**
	 * This method processes {@link EventLogSource} source
	 *
	 * @param eventLogSource {@link EventLogSource} source instance
	 * @return {@link SourceTable} instance
	 */
	@WithSpan("Source EventLogSource Exec")
	@Override
	public SourceTable process(@SpanAttribute("source.definition") EventLogSource eventLogSource) {
		return processSourceThroughExtension(eventLogSource);
	}

	/**
	 * This method processes {@link FileSource} source
	 *
	 * @param fileSource {@link FileSource} source instance
	 * @return {@link SourceTable} instance
	 */
	@WithSpan("Source FileSource Exec")
	@Override
	public SourceTable process(@SpanAttribute("source.definition") FileSource fileSource) {
		return processSourceThroughExtension(fileSource);
	}
}
