package org.metricshub.extension.jawk;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * MetricsHub Jawk Extension
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

import static org.metricshub.engine.common.helpers.MetricsHubConstants.FILE_PATTERN;
import static org.metricshub.engine.common.helpers.MetricsHubConstants.TABLE_SEP;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.metricshub.engine.awk.UniformPrintStream;
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
import org.metricshub.jawk.backend.AVM;
import org.metricshub.jawk.ext.JawkExtension;
import org.metricshub.jawk.intermediate.AwkTuples;
import org.metricshub.jawk.util.AwkSettings;
import org.metricshub.jawk.util.ScriptSource;

/**
 * This class implements the {@link ICompositeSourceScriptExtension} contract, reports the supported features,
 * processes HTTP sources and criteria.
 */
@Slf4j
public class JawkSourceExtension implements ICompositeSourceScriptExtension {

	// script to AwkTuple
	private static final Map<String, AwkTuples> AWK_CODE_MAP = new ConcurrentHashMap<>();

	private static org.metricshub.jawk.Awk awkInstance;

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

		log.debug("Hostname {} - Awk Operation. AWK Script:\n{}\n", hostname, awkScript);

		final AwkSettings settings = new AwkSettings();

		final String input = jawkSource.getInput();
		if (input != null && !input.isEmpty()) {
			final String inputContent = SourceUpdaterProcessor.replaceSourceReferenceContent(
				input,
				telemetryManager,
				connectorId,
				"Awk",
				source.getKey()
			);

			if (inputContent != null && !inputContent.isEmpty()) {
				settings.setInput(new ByteArrayInputStream(inputContent.getBytes(StandardCharsets.UTF_8)));
			}
		}

		// Create the OutputStream
		final ByteArrayOutputStream resultBytesStream = new ByteArrayOutputStream();
		final UniformPrintStream resultStream = new UniformPrintStream(resultBytesStream);
		settings.setOutputStream(resultStream);

		// We don't want to see error messages because of formatting issues
		settings.setCatchIllegalFormatExceptions(true);

		// We force \n as the Record Separator (RS) because even if running on Windows
		// we're passing Java strings, where end of lines are simple \n
		settings.setDefaultRS("\n");

		final MetricsHubExtensionForJawk metricsHubExtensionForJawk = MetricsHubExtensionForJawk
			.builder()
			.sourceProcessor(sourceProcessor)
			.hostname(telemetryManager.getHostname())
			.connectorId(connectorId)
			.build();

		final Map<String, JawkExtension> extensions = Arrays
			.stream(metricsHubExtensionForJawk.extensionKeywords())
			.collect(Collectors.toConcurrentMap(key -> key, key -> metricsHubExtensionForJawk));

		// Interpret
		final AVM avm = new AVM(settings, extensions);

		try {
			final AwkTuples tuple = AWK_CODE_MAP.computeIfAbsent(awkScript, code -> getIntermediateCode(code, extensions));
			avm.interpret(tuple);

			// Result
			final SourceTable sourceTable = new SourceTable();
			sourceTable.setRawData(resultBytesStream.toString(StandardCharsets.UTF_8));
			sourceTable.setTable(SourceTable.csvToTable(sourceTable.getRawData(), TABLE_SEP));
			return sourceTable;
		} catch (Exception e) {
			LoggingHelper.logSourceError(connectorId, source.getKey(), "AwkSource script", hostname, e);
			return SourceTable.empty();
		}
	}

	/**
	 * Generates the "Awk Tuples", i.e. the intermediate Awk code
	 * that can be interpreted afterward.
	 *
	 * @param script Awk script source code to be converted to intermediate code
	 * @param extensions The extensions to be used during compilation
	 * @return The actual AwkTuples to be interpreted
	 * @throws JawkSourceExtensionRuntimeException when the Awk script is wrong or an error occurs during the parsing
	 */
	public static AwkTuples getIntermediateCode(final String script, final Map<String, JawkExtension> extensions)
		throws JawkSourceExtensionRuntimeException {
		// All scripts need to be prefixed with an extra statement that sets the Record Separator (RS)
		// to the "normal" end-of-line (\n), because Jawk uses line.separator System property, which
		// is \r\n on Windows, thus preventing it from splitting lines properly.
		final ScriptSource awkHeader = new ScriptSource("Header", new StringReader("BEGIN { ORS = RS = \"\\n\"; }"));
		final ScriptSource awkSource = new ScriptSource("Body", new StringReader(script));
		final List<ScriptSource> sourceList = new ArrayList<>();
		sourceList.add(awkHeader);
		sourceList.add(awkSource);

		try {
			if (awkInstance == null) {
				awkInstance = new org.metricshub.jawk.Awk(extensions);
			}
			return awkInstance.compile(sourceList);
		} catch (IOException | ClassNotFoundException e) {
			throw new JawkSourceExtensionRuntimeException(e.getMessage(), e);
		}
	}
}
