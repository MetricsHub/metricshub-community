package org.metricshub.extension.jawk;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * MetricsHub Jawk Extension
 * ჻჻჻჻჻჻
 * Copyright (C) 2023 - 2025 MetricsHub
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

import static org.metricshub.engine.common.helpers.MetricsHubConstants.FILE_PATTERN;
import static org.metricshub.engine.common.helpers.MetricsHubConstants.NEW_LINE;
import static org.metricshub.engine.common.helpers.MetricsHubConstants.TABLE_SEP;
import static org.metricshub.engine.common.helpers.MetricsHubConstants.WINDOWS_NEW_LINE;

import io.jawk.Awk;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.metricshub.engine.awk.AwkExecutor;
import org.metricshub.engine.awk.AwkVariableHelper;
import org.metricshub.engine.awk.MetricsHubExtensionForJawk;
import org.metricshub.engine.awk.TableInputSource;
import org.metricshub.engine.awk.UtilityExtensionForJawk;
import org.metricshub.engine.common.helpers.LoggingHelper;
import org.metricshub.engine.connector.model.common.ContentType;
import org.metricshub.engine.connector.model.common.EmbeddedFile;
import org.metricshub.engine.connector.model.monitor.task.source.JawkSource;
import org.metricshub.engine.connector.model.monitor.task.source.Source;
import org.metricshub.engine.extension.ICompositeSourceScriptExtension;
import org.metricshub.engine.strategy.source.SourceProcessor;
import org.metricshub.engine.strategy.source.SourceTable;
import org.metricshub.engine.strategy.utils.EmbeddedFileHelper;
import org.metricshub.engine.telemetry.TelemetryManager;

/**
 * This class implements the {@link ICompositeSourceScriptExtension} contract, reports the supported features,
 * processes HTTP sources and criteria.
 */
@Slf4j
public class JawkSourceExtension implements ICompositeSourceScriptExtension {

	@Override
	public boolean isValidSource(final Source source) {
		return source instanceof JawkSource;
	}

	/**
	 * The table of the source referenced by <code>input</code> when the script reads a table. A missing reference or
	 * an unknown source gives an empty table (and a warning).
	 */
	private static List<List<String>> lookupInputTable(
		final JawkSource jawkSource,
		final String connectorId,
		final TelemetryManager telemetryManager
	) {
		final String input = jawkSource.getInput();
		if (input == null || input.isBlank()) {
			log.warn(
				"Hostname {} - Awk source {} reads its input as a table but declares no input source, the script reads no record.",
				telemetryManager.getHostname(),
				jawkSource.getKey()
			);
			return List.of();
		}
		return SourceTable.lookupSourceTable(input.trim(), connectorId, telemetryManager)
			.map(SourceTable::getTable)
			.orElseGet(() -> {
				log.warn(
					"Hostname {} - Awk source {} reads the table of {} but that source is not available, the script reads no record.",
					telemetryManager.getHostname(),
					jawkSource.getKey(),
					input
				);
				return List.of();
			});
	}

	@Override
	public SourceTable processSource(
		final Source source,
		final String connectorId,
		final TelemetryManager telemetryManager,
		final SourceProcessor sourceProcessor
	) {
		final String hostname = telemetryManager.getHostname();

		if (source == null) {
			log.warn("Hostname {} - Awk Source cannot be null, the Awk operation will return an empty result.", hostname);
			return SourceTable.empty();
		}

		if (!(source instanceof JawkSource jawkSource)) {
			log.warn("Hostname {} - Awk Source is invalid, the Awk operation will return an empty result.", hostname);
			return SourceTable.empty();
		}

		final String script = jawkSource.getScript();

		// An Awk Script is supposed to be only the reference to the EmbeddedFile, so the map contains only one item which is our EmbeddedFile
		final Optional<EmbeddedFile> maybeEmbeddedFile;

		if (!FILE_PATTERN.matcher(script).find()) {
			maybeEmbeddedFile = Optional.of(EmbeddedFile.fromString(script));
		} else {
			try {
				maybeEmbeddedFile = EmbeddedFileHelper.findEmbeddedFile(
					script,
					telemetryManager.getEmbeddedFiles(connectorId),
					hostname,
					connectorId
				);
			} catch (Exception exception) {
				log.warn("Hostname {} - Awk Operation script {} has not been set correctly.", hostname, script);
				return SourceTable.empty();
			}
		}

		if (maybeEmbeddedFile.isEmpty()) {
			log.warn("Hostname {} - Awk Operation script {} embedded file can't be found.", hostname, script);
			return SourceTable.empty();
		}

		final EmbeddedFile embeddedFile = maybeEmbeddedFile.get();
		final String awkScript = embeddedFile.getContentAsString();

		log.debug("Hostname {} - Awk Operation. Awk Script:\n{}\n", hostname, awkScript);

		// The input has already been resolved by SourceUpdaterProcessor, like every other source field, unless the
		// script reads it as a table: the reference is then resolved to the table itself
		final boolean tableInput = jawkSource.getInputFormat() == ContentType.TABLE;
		final String inputContent = tableInput ? null : jawkSource.getInput();
		final List<List<String>> inputTable = tableInput
			? lookupInputTable(jawkSource, connectorId, telemetryManager)
			: null;

		// Resolve the variables declared on the source, in one pass: every textual reference is substituted and a
		// variable referencing a source is exposed to the script as an array holding that source's table
		final Map<String, Object> variables = AwkVariableHelper.resolveVariables(
			jawkSource.getVariables(),
			telemetryManager,
			connectorId,
			sourceProcessor.getAttributes(),
			source.getKey()
		);

		// Instantiate the MetricsHub extension for Jawk with the proper context
		MetricsHubExtensionForJawk extension = new MetricsHubExtensionForJawk(sourceProcessor, hostname, connectorId);

		// Execute
		try {
			Awk awkEngine = new Awk(List.of(extension, UtilityExtensionForJawk.INSTANCE), AwkExecutor.newAwkSettings(null));
			final SourceTable sourceTable = new SourceTable();
			if (jawkSource.getOutput() == ContentType.TABLE) {
				// Each print is a row
				final List<List<String>> rows = tableInput
					? AwkExecutor.executeAwkToTable(awkScript, new TableInputSource(inputTable), awkEngine, variables)
					: AwkExecutor.executeAwkToTable(awkScript, inputContent, awkEngine, variables);
				sourceTable.setTable(rows);
				// Serialized as toText does, so that a later text consumer cannot split a cell holding the separator,
				// and never with CRLF; no raw text at all for an empty table, which stays a table
				sourceTable.setRawData(
					rows.isEmpty() ? null : SourceTable.tableToCsv(rows, TABLE_SEP, true).replace(WINDOWS_NEW_LINE, NEW_LINE)
				);
				sourceTable.setContentType(ContentType.TABLE);
				return sourceTable;
			}
			// The text written from a table input never holds CRLF (the legacy text path keeps its result as is)
			String result = tableInput
				? AwkExecutor.executeAwk(awkScript, new TableInputSource(inputTable), awkEngine, variables)
					.replace(WINDOWS_NEW_LINE, NEW_LINE)
				: AwkExecutor.executeAwk(awkScript, inputContent, awkEngine, variables);
			sourceTable.setRawData(result);
			sourceTable.setTable(SourceTable.csvToTable(result, TABLE_SEP));
			return sourceTable;
		} catch (Exception e) {
			LoggingHelper.logSourceError(connectorId, source.getKey(), "AwkSource script", hostname, e);
			return SourceTable.empty();
		}
	}
}
