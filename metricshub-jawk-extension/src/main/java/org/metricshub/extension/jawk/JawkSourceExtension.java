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

import static org.metricshub.engine.common.helpers.MetricsHubConstants.NEW_LINE;
import static org.metricshub.engine.common.helpers.MetricsHubConstants.TABLE_SEP;
import static org.metricshub.engine.common.helpers.MetricsHubConstants.WINDOWS_NEW_LINE;

import io.jawk.Awk;
import io.jawk.AwkProgram;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.metricshub.engine.awk.AwkExecutor;
import org.metricshub.engine.awk.AwkVariableHelper;
import org.metricshub.engine.awk.MetricsHubExtensionForJawk;
import org.metricshub.engine.awk.TableInputSource;
import org.metricshub.engine.common.helpers.LoggingHelper;
import org.metricshub.engine.connector.model.common.ContentType;
import org.metricshub.engine.connector.model.common.EmbeddedFile;
import org.metricshub.engine.connector.model.monitor.task.source.JawkSource;
import org.metricshub.engine.connector.model.monitor.task.source.Source;
import org.metricshub.engine.extension.ICompositeSourceScriptExtension;
import org.metricshub.engine.strategy.source.SourceProcessor;
import org.metricshub.engine.strategy.source.SourceTable;
import org.metricshub.engine.strategy.source.SourceUpdaterProcessor;
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

		try {
			maybeEmbeddedFile = EmbeddedFileHelper.findScript(
				script,
				() -> telemetryManager.getEmbeddedFiles(connectorId),
				hostname,
				connectorId
			);
		} catch (Exception exception) {
			log.warn("Hostname {} - Awk Operation script {} has not been set correctly.", hostname, script);
			return SourceTable.empty();
		}

		if (maybeEmbeddedFile.isEmpty()) {
			log.warn("Hostname {} - Awk Operation script {} embedded file can't be found.", hostname, script);
			return SourceTable.empty();
		}

		final EmbeddedFile embeddedFile = maybeEmbeddedFile.get();
		final String awkScript = embeddedFile.getContentAsString();

		log.debug("Hostname {} - Awk Operation. Awk Script:\n{}\n", hostname, awkScript);

		// The input has already been resolved by SourceUpdaterProcessor, like every other source field, unless it is a
		// single source reference: the script reads that source as it is (#1559), a table structured, a text (or a
		// source that is not available, logged) as the source updater expands it. A table row also gives $0 as the line
		// the reference expanded to before #1559 (cells joined with ";", the trailing ";" kept unless the row has one
		// cell), so a script reading $0 sees the same text (a password holding a ";" taken from $0 keeps it)
		final String input = jawkSource.getInput();
		final boolean reference = JawkSource.isSourceReference(input);
		final List<List<String>> inputTable = reference
			? SourceTable.lookupSourceTable(input.trim(), connectorId, telemetryManager)
				.filter(table -> table.getContentType() == ContentType.TABLE)
				.map(SourceTable::getTable)
				.orElse(null)
			: null;
		final boolean tableInput = inputTable != null;
		final String inputContent = tableInput
			? null
			: reference
				? SourceUpdaterProcessor.replaceSourceReferenceContent(
					input,
					telemetryManager,
					connectorId,
					"JawkSource",
					source.getKey()
				)
				: input;

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
			Awk awkEngine = AwkExecutor.newAwk(null, extension);
			// The program the connector holds. The first run compiles the script and leaves the program there
			AwkProgram program = jawkSource.getProgram();
			if (program == null) {
				program = AwkExecutor.compile(awkScript, awkEngine);
				jawkSource.setProgram(program);
			}
			if (jawkSource.getOutput() == ContentType.TABLE) {
				// Each print is a row
				return SourceTable.ofTable(
					tableInput
						? AwkExecutor.executeAwkToTable(
							program,
							TableInputSource.withRecordText(inputTable, JawkSourceExtension::referenceRow),
							awkEngine,
							variables
						)
						: AwkExecutor.executeAwkToTable(program, inputContent, awkEngine, variables)
				);
			}
			// The text written from a table input never holds CRLF (the legacy text path keeps its result as is)
			return SourceTable.ofText(
				tableInput
					? AwkExecutor.executeAwk(
						program,
						TableInputSource.withRecordText(inputTable, JawkSourceExtension::referenceRow),
						awkEngine,
						variables
					).replace(WINDOWS_NEW_LINE, NEW_LINE)
					: AwkExecutor.executeAwk(program, inputContent, awkEngine, variables)
			);
		} catch (Exception e) {
			LoggingHelper.logSourceError(connectorId, source.getKey(), "AwkSource script", hostname, e);
			return SourceTable.empty();
		}
	}

	/**
	 * The text of a table row as a source reference expands it: its cells joined with ";", with the trailing ";"
	 * unless the row has a single cell.
	 *
	 * @param cells The cells of the row
	 * @return the line
	 */
	static String referenceRow(final List<String> cells) {
		return SourceUpdaterProcessor.referenceLine(SourceTable.lineToCsv(cells, TABLE_SEP, false));
	}
}
