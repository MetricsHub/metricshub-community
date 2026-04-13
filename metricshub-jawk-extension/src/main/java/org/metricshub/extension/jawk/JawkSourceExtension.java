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
import static org.metricshub.engine.common.helpers.MetricsHubConstants.TABLE_SEP;

import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.metricshub.engine.awk.AwkExecutor;
import org.metricshub.engine.awk.MetricsHubExtensionForJawk;
import org.metricshub.engine.awk.UtilityExtensionForJawk;
import org.metricshub.engine.common.helpers.LoggingHelper;
import org.metricshub.engine.connector.model.common.EmbeddedFile;
import org.metricshub.engine.connector.model.monitor.task.source.JawkSource;
import org.metricshub.engine.connector.model.monitor.task.source.Source;
import org.metricshub.engine.extension.ICompositeSourceScriptExtension;
import org.metricshub.engine.strategy.source.SourceProcessor;
import org.metricshub.engine.strategy.source.SourceTable;
import org.metricshub.engine.strategy.source.SourceUpdaterProcessor;
import org.metricshub.engine.strategy.utils.EmbeddedFileHelper;
import org.metricshub.engine.telemetry.TelemetryManager;
import org.metricshub.jawk.Awk;

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

		if (!FILE_PATTERN.matcher(script).find()) {
			maybeEmbeddedFile = Optional.of(EmbeddedFile.fromString(script));
		} else {
			try {
				maybeEmbeddedFile =
					EmbeddedFileHelper.findEmbeddedFile(
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

		final String input = jawkSource.getInput();
		final String inputContent = (input != null && !input.isEmpty())
			? SourceUpdaterProcessor.replaceSourceReferenceContent(
				input,
				telemetryManager,
				connectorId,
				"Awk",
				source.getKey()
			)
			: input;

		// Instantiate the MetricsHub extension for Jawk with the proper context
		MetricsHubExtensionForJawk extension = new MetricsHubExtensionForJawk(sourceProcessor, hostname, connectorId);

		// Execute
		try {
			Awk awkEngine = new Awk(extension, UtilityExtensionForJawk.INSTANCE);
			String result = AwkExecutor.executeAwk(awkScript, inputContent, awkEngine);
			final SourceTable sourceTable = new SourceTable();
			sourceTable.setRawData(result);
			sourceTable.setTable(SourceTable.csvToTable(result, TABLE_SEP));
			return sourceTable;
		} catch (Exception e) {
			LoggingHelper.logSourceError(connectorId, source.getKey(), "AwkSource script", hostname, e);
			return SourceTable.empty();
		}
	}
}
