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

import static org.metricshub.engine.common.helpers.MetricsHubConstants.COLUMN_REFERENCE_PATTERN;
import static org.metricshub.engine.common.helpers.MetricsHubConstants.EMPTY;
import static org.metricshub.engine.common.helpers.MetricsHubConstants.NEW_LINE;
import static org.metricshub.engine.common.helpers.MetricsHubConstants.SEMICOLON;
import static org.metricshub.engine.common.helpers.MetricsHubConstants.SOURCE_REF_PATTERN;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.metricshub.engine.connector.model.common.CustomConcatMethod;
import org.metricshub.engine.connector.model.common.EntryConcatMethod;
import org.metricshub.engine.connector.model.common.IEntryConcatMethod;
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
import org.metricshub.engine.strategy.utils.PslUtils;
import org.metricshub.engine.telemetry.TelemetryManager;

/**
 * The {@code SourceUpdaterProcessor} class is responsible for processing various source types, including HTTP, IPMI,
 * OS command, SNMP, static, table join, table union, WMI, and SNMP table sources. It performs operations such as
 * replacing attribute references, updating source references, and extracting HTTP authentication tokens.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Slf4j
public class SourceUpdaterProcessor implements ISourceProcessor {

	private static final Pattern MONO_INSTANCE_REPLACEMENT_PATTERN = Pattern.compile(
		"\\$\\{attribute::(\\w+)\\}",
		Pattern.CASE_INSENSITIVE
	);

	private ISourceProcessor sourceProcessor;
	private TelemetryManager telemetryManager;
	private String connectorId;
	private Map<String, String> attributes;

	@Override
	public SourceTable process(final HttpSource httpSource) {
		// Very important! otherwise we will overlap in multi-host mode
		final HttpSource copy = httpSource.copy();

		// Replace HTTP Authentication token
		copy.setAuthenticationToken(
			extractHttpTokenFromSource(copy.getKey(), copy.getAuthenticationToken(), "authenticationToken")
		);

		return processSource(copy);
	}

	@Override
	public SourceTable process(JmxSource src) throws Exception {
		return null;
	}

	@Override
	public SourceTable process(final CopySource copySource) {
		return processSource(copySource.copy());
	}

	@Override
	public SourceTable process(final IpmiSource ipmiSource) {
		return processSource(ipmiSource.copy());
	}

	@Override
	public SourceTable process(final CommandLineSource commandLineSource) {
		return processSource(commandLineSource.copy());
	}

	@Override
	public SourceTable process(final SnmpGetSource snmpGetSource) {
		return processSource(snmpGetSource.copy());
	}

	@Override
	public SourceTable process(final SnmpTableSource snmpTableSource) {
		return processSource(snmpTableSource.copy());
	}

	@Override
	public SourceTable process(final StaticSource staticSource) {
		return processSource(staticSource.copy());
	}

	@Override
	public SourceTable process(final TableJoinSource tableJoinSource) {
		return processSource(tableJoinSource.copy());
	}

	@Override
	public SourceTable process(final TableUnionSource tableUnionSource) {
		return processSource(tableUnionSource.copy());
	}

	@Override
	public SourceTable process(final WbemSource wbemSource) {
		return processSource(wbemSource.copy());
	}

	@Override
	public SourceTable process(final InternalDbQuerySource internalDbQuery) {
		return processSource(internalDbQuery.copy());
	}

	@Override
	public SourceTable process(final JawkSource jawkSource) {
		return processSource(jawkSource.copy());
	}

	/**
	 * This method processes {@link WmiSource} source
	 * @param wmiSource {@link WmiSource} instance
	 * @return {@link SourceTable} instance
	 */
	@Override
	public SourceTable process(final WmiSource wmiSource) {
		return processSource(wmiSource.copy());
	}

	/**
	 * This method processes {@link SqlSource} source
	 * @param sqlSource {@link SqlSource} instance
	 * @return {@link SourceTable} instance
	 */
	@Override
	public SourceTable process(final SqlSource sqlSource) {
		return processSource(sqlSource.copy());
	}

	/**
	 * Process the given source copy
	 *
	 * @param copy copy of the original source
	 * @return {@link SourceTable}
	 */
	private SourceTable processSource(final Source copy) {
		if (copy.isExecuteForEachEntryOf()) {
			return runExecuteForEachEntryOf(copy);
		}

		copy.update(value -> replaceAttributeReferences(value, attributes));

		copy.update(value -> replaceSourceReference(value, copy));

		copy.update(value -> value == null ? value : value.replace("$$", "$"));

		return copy.accept(sourceProcessor);
	}

	/**
	 * Extracts the HTTP authentication token from the specified foreign source identified by <em>foreignSourceKey</em>.
	 *
	 * @param originalSourceKey The original source key for debugging purposes.
	 * @param foreignSourceKey  The foreign source key used to extract the token.
	 * @param fieldLabel        The field label for debugging purposes.
	 * @return The extracted HTTP authentication token.
	 */
	public String extractHttpTokenFromSource(final String originalSourceKey, String foreignSourceKey, String fieldLabel) {
		// No token to replace
		if (foreignSourceKey == null) {
			return null;
		}

		if (foreignSourceKey.isEmpty()) {
			return EMPTY;
		}

		// Get the source table defining where we are going to fetch the value
		final Optional<SourceTable> maybeSourceTable = SourceTable.lookupSourceTable(
			foreignSourceKey,
			connectorId,
			telemetryManager
		);

		final String hostname = telemetryManager.getHostname();
		if (maybeSourceTable.isEmpty()) {
			log.error(
				"Hostname {} - Could not extract the foreign source table identified by {} and defined in original source {} to set the {} field.",
				hostname,
				foreignSourceKey,
				originalSourceKey,
				fieldLabel
			);
			return null;
		}

		final SourceTable sourceTable = maybeSourceTable.get();

		// Try the table
		final List<List<String>> table = sourceTable.getTable();
		String value = null;
		if (table != null && !table.isEmpty()) {
			log.debug("Hostname {} - Get {} defined in source {} from list table.", hostname, fieldLabel, foreignSourceKey);
			final List<String> firstRow = table.get(0);
			if (firstRow != null && !firstRow.isEmpty()) {
				// First column
				value = firstRow.get(0);
			}
		}

		// Try raw data
		final String rawData = sourceTable.getRawData();
		if (value == null && rawData != null) {
			log.debug("Hostname {} - Get {} defined in source {} from raw data.", hostname, fieldLabel, foreignSourceKey);
			// First column
			value = rawData.split(";")[0];
		}

		if (value == null) {
			log.error("Hostname {} - Couldn't extract the {} defined in source {}.", hostname, fieldLabel, originalSourceKey);
		}

		return value;
	}

	/**
	 * Run an execution for each entry in the {@link SourceTable} referenced by the given source copy.
	 *
	 * @param copy Must be a copy of the source.
	 * @return {@link SourceTable} result
	 */
	private SourceTable runExecuteForEachEntryOf(final Source copy) {
		final SourceTable result = processExecuteForEachEntryOf(copy, copy.getExecuteForEachEntryOf());

		if (result != null && isEntryConcatMethodJsonArrayOrExtended(copy)) {
			result.setRawData(String.format("[%s]", result.getRawData()));
		}

		return result;
	}

	/**
	 * Whether this source copy indicates and entry concatenation method for JSON array or JSON array extended
	 *
	 * @param copy {@link Source} instance copy
	 * @return boolean value
	 */
	private boolean isEntryConcatMethodJsonArrayOrExtended(final Source copy) {
		// CHECKSTYLE:OFF
		return (
			EntryConcatMethod.JSON_ARRAY_EXTENDED.equals(copy.getEntryConcatMethod()) ||
			EntryConcatMethod.JSON_ARRAY.equals(copy.getEntryConcatMethod())
		);
		// CHECKSTYLE:ON
	}

	/**
	 * Replace the attribute identifiers referenced in the key
	 * with the attribute values that need to be retrieved from the given attributes lookup.
	 *
	 * @param key        The key where to replace the deviceId.
	 * @param attributes Key-value pairs of the monitor's attributes
	 * @return String value
	 */
	public static String replaceAttributeReferences(final String key, final Map<String, String> attributes) {
		if (attributes == null || key == null) {
			return key;
		}

		final Matcher matcher = MONO_INSTANCE_REPLACEMENT_PATTERN.matcher(key);

		final StringBuffer sb = new StringBuffer();
		while (matcher.find()) {
			final String attributesValue = attributes.get(matcher.group(1));
			if (attributesValue != null) {
				matcher.appendReplacement(sb, Matcher.quoteReplacement(attributesValue));
			}
		}
		matcher.appendTail(sb);

		return sb.toString();
	}

	/**
	 * Replace referenced source in the given source attributes
	 *
	 * @param value The value containing a source reference such as %Enclosure.Discovery.Source(1)%.
	 * @param source {@link Source} instance we wish to update with the content of the referenced source
	 * @return String value
	 */
	String replaceSourceReference(final String value, final Source source) {
		// Source shouldn't be replaced when it is an instance of TableJoinSource, TableUnionSource and ReferenceSource
		// All these sources need the reference to perform the job correctly. See SourceVisitor implementation.
		if (value == null || isSourceWithProtectedReferences(source)) {
			return value;
		}

		return replaceSourceReferenceContent(
			value,
			telemetryManager,
			connectorId,
			source.getClass().getSimpleName(),
			source.getKey()
		);
	}

	/**
	 * Whether this source must have protected references that shouldn't be replaced by the current updater
	 *
	 * @param source {@link Source} instance
	 * @return boolean value
	 */
	private boolean isSourceWithProtectedReferences(final Source source) {
		// CHECKSTYLE:OFF
		return (
			source instanceof CopySource ||
			source instanceof StaticSource ||
			source instanceof TableUnionSource ||
			source instanceof TableJoinSource
		);
		// CHECKSTYLE:ON
	}

	/**
	 * Replace source reference content in the given value
	 *
	 * @param value            The value containing source key such as ${source::monitors.cpu.discovery.sources.source1}
	 * @param telemetryManager The current {@link TelemetryManager} instance wrapping the host configuration and the host monitoring instance
	 * @param connectorId      The connector's identifier
	 * @param operationType    The type of the operation required for debug purpose. E.g. Substring, SnmpGetTableSource, ...
	 * @param operationKey     The unique key of the operation used required for debug purpose
	 * @return {@link String} value
	 */
	public static String replaceSourceReferenceContent(
		final String value,
		final TelemetryManager telemetryManager,
		final String connectorId,
		final String operationType,
		final Object operationKey
	) {
		final Matcher matcher = SOURCE_REF_PATTERN.matcher(value);

		final StringBuffer sb = new StringBuffer();

		while (matcher.find()) {
			final String sourceKey = matcher.group();
			final String sourceReferenceContent = extractSourceReferenceContent(
				telemetryManager,
				connectorId,
				operationType,
				operationKey,
				sourceKey
			);
			matcher.appendReplacement(sb, Matcher.quoteReplacement(sourceReferenceContent));
		}

		matcher.appendTail(sb);

		return sb.toString();
	}

	/**
	 * Extract the source reference content
	 *
	 * @param telemetryManager The current {@link TelemetryManager} instance wrapping the host configuration and the host monitoring instance
	 * @param connectorId      The connector defining all the operations we currently try to interpret and execute
	 * @param operationType    The type of the operation required for debug purpose. E.g. Substring, SnmpGetTableSource, ...
	 * @param operationKey     The unique key of the operation required for debug purpose
	 * @param sourceRefKey     The unique id of the source to extract
	 * @return String value
	 */
	static String extractSourceReferenceContent(
		final TelemetryManager telemetryManager,
		final String connectorId,
		final String operationType,
		final Object operationKey,
		final String sourceRefKey
	) {
		// Get the source table from the connector namespace
		final SourceTable sourceTable = telemetryManager
			.getHostProperties()
			.getConnectorNamespace(connectorId)
			.getSourceTables()
			.get(sourceRefKey);

		// Hostname used for the debug messages
		final String hostname = telemetryManager.getHostname();

		if (sourceTable == null) {
			log.error(
				"Hostname {} - The source table is not available. Couldn't extract {} referenced in {} ({}). The source reference will be replaced with an empty value.",
				hostname,
				sourceRefKey,
				operationType,
				operationKey
			);
			return EMPTY;
		}

		String sourceReferenceContent = null;

		// Try the table
		final List<List<String>> table = sourceTable.getTable();
		if (table != null && !table.isEmpty()) {
			log.debug(
				"Hostname {} - Get {} referenced in {} ({}) from list table.",
				hostname,
				sourceRefKey,
				operationType,
				operationKey
			);
			sourceReferenceContent = SourceTable.tableToCsv(table, SEMICOLON, false);
		}

		// Try raw data
		final String rawData = sourceTable.getRawData();
		if (sourceReferenceContent == null && rawData != null) {
			log.debug(
				"Hostname {} - Get {} referenced in {} ({}) from raw data.",
				hostname,
				sourceRefKey,
				operationType,
				operationKey
			);
			sourceReferenceContent = rawData;
		}

		if (sourceReferenceContent != null) {
			// Remove last semicolon from the rows containing only one cell.
			// For instance the last semicolon leads to HTTP Errors when the reference is defined in the Header attribute
			return Stream
				.of(sourceReferenceContent.split(NEW_LINE))
				.map(val -> {
					final int indexOfSemicolon = val.indexOf(SEMICOLON);
					if (indexOfSemicolon == val.length() - 1) {
						return val.replace(SEMICOLON, EMPTY);
					}
					return val;
				})
				.collect(Collectors.joining(NEW_LINE));
		}

		log.error(
			"Hostname {} - Neither the raw data nor the table is available. Couldn't extract {} referenced in {} ({}). The source reference will be replaced with an empty value.",
			hostname,
			sourceRefKey,
			operationType,
			operationKey
		);

		return EMPTY;
	}

	/**
	 * Process the given {@link Source} for the source table entries in the source result of <em>sourceTableKey</em>
	 *
	 * @param source         The {@link Source} we wish to process
	 * @param sourceTableKey The key of the source table defining the entries
	 * @return {@link SourceTable} containing all the result concatenated using the
	 *         EntryConcatMethod defined in the original {@link Source}
	 */
	private SourceTable processExecuteForEachEntryOf(final Source source, final String sourceTableKey) {
		final Optional<SourceTable> maybeSourceTable = SourceTable.lookupSourceTable(
			sourceTableKey,
			connectorId,
			telemetryManager
		);
		final String hostname = telemetryManager.getHostname();
		if (maybeSourceTable.isEmpty()) {
			log.error(
				"Hostname {} - The SourceTable referenced in the ExecuteForEachEntryOf field cannot be found : {}.",
				hostname,
				sourceTableKey
			);
			return SourceTable.empty();
		}

		final SourceTable result = SourceTable.builder().rawData(EMPTY).build();

		final Integer sleep = source.getSleepExecuteForEachEntryOf();

		for (List<String> row : maybeSourceTable.get().getTable()) {
			final Source copy = source.copy();

			try {
				copy.update(dataValue -> replaceDynamicEntry(dataValue, row));
			} catch (NumberFormatException e) {
				log.warn("Hostname {} - The dynamic key from Source is incorrectly formatted : {}.", hostname, copy);
				continue;
			}

			copy.update(dataValue -> replaceAttributeReferences(dataValue, attributes));

			copy.update(value -> replaceSourceReference(value, copy));

			concatEntryResult(source, result, row, copy.accept(sourceProcessor));
			if (sleep != null && sleep != 0) {
				try {
					Thread.sleep(sleep);
				} catch (InterruptedException e) {
					log.error(
						"Hostname {} - Thread interrupted during sleep between two 'execute for each entry' requests.",
						hostname
					);
					Thread.currentThread().interrupt();
				}
			}
		}

		return result;
	}

	/**
	 * Replace the dynamic parts of the key by the right column from the row.
	 *
	 * @param dataValue Source's member value
	 * @param row       The row used to extract the value to replace
	 * @return String value
	 */
	static String replaceDynamicEntry(final String dataValue, @NonNull final List<String> row)
		throws NumberFormatException {
		if (dataValue == null) {
			return null;
		}

		final Matcher matcher = COLUMN_REFERENCE_PATTERN.matcher(dataValue);

		final StringBuffer sb = new StringBuffer();
		while (matcher.find()) {
			String value = row.get(Integer.parseInt(matcher.group(1)) - 1);
			matcher.appendReplacement(sb, Matcher.quoteReplacement(value));
		}

		matcher.appendTail(sb);

		return sb.toString().replace("$$", "$");
	}

	/**
	 * Based on the EntryConcatMethod, concatenate the <em>sourceTableToConcat</em> in the <em>result</em> source table
	 *
	 * @param source               The source used to get the entry concat start/end
	 * @param currentResult        The current result we wish to update
	 * @param row                  The row to concatenate in case we have the JSON_ARRAY_EXTENDED concatenation method
	 * @param sourceTableToConcat  The current source table result
	 */
	private void concatEntryResult(
		final Source source,
		final SourceTable currentResult,
		final List<String> row,
		final SourceTable sourceTableToConcat
	) {
		final IEntryConcatMethod iEntryConcatMethod = source.getEntryConcatMethod();
		final String rawData = sourceTableToConcat.getRawData();

		if (iEntryConcatMethod instanceof CustomConcatMethod customConcatMethod) {
			appendCustomEntryResult(customConcatMethod, currentResult, row, rawData);
		} else {
			final EntryConcatMethod entryConcatMethod = iEntryConcatMethod != null
				? (EntryConcatMethod) iEntryConcatMethod
				: EntryConcatMethod.LIST;

			switch (entryConcatMethod) {
				case JSON_ARRAY:
					appendJsonToArray(currentResult, rawData);
					break;
				case JSON_ARRAY_EXTENDED:
					appendExtendedJsonToArray(currentResult, row, sourceTableToConcat);
					break;
				default:
					if (rawData != null && !rawData.isBlank()) {
						joinStringValue(currentResult, rawData, "\n");
					}

					final List<List<String>> table = sourceTableToConcat.getTable();

					if (table != null && !table.isEmpty()) {
						currentResult
							.getTable()
							.addAll(table.stream().filter(line -> !line.isEmpty()).collect(Collectors.toCollection(ArrayList::new)));
					}
					break;
			}
		}
	}

	/**
	 * Append the custom entry result <em>rawData</em> to the given <em>currentResult</em> {@link SourceTable}.
	 *
	 * @param customConcatMethod The {@link CustomConcatMethod} instance.
	 * @param currentResult      The {@link SourceTable} result to update.
	 * @param row                The row to concatenate in case we have the JSON_ARRAY_EXTENDED concatenation method.
	 * @param rawData            The rawData to append.
	 */
	private void appendCustomEntryResult(
		final CustomConcatMethod customConcatMethod,
		final SourceTable currentResult,
		final List<String> row,
		final String rawData
	) {
		if (rawData == null) {
			return;
		}
		String entryConcatStart = customConcatMethod.getConcatStart() != null
			? replaceDynamicEntry(customConcatMethod.getConcatStart(), row)
			: EMPTY;
		String entryConcatEnd = customConcatMethod.getConcatEnd() != null
			? replaceDynamicEntry(customConcatMethod.getConcatEnd(), row)
			: EMPTY;

		currentResult.setRawData(
			currentResult.getRawData().concat(entryConcatStart).concat(rawData).concat(entryConcatEnd)
		);
	}

	/**
	 * Build the Extended JSON then append it to the <em>currentResult</em> {@link SourceTable}.
	 *
	 * @param currentResult       The {@link SourceTable} result to update
	 * @param row                 The row used to build the Extended JSON
	 * @param sourceTableToConcat The {@link SourceTable} defining the <em>rawData</em> to concatenate.
	 */
	private void appendExtendedJsonToArray(
		final SourceTable currentResult,
		final List<String> row,
		final SourceTable sourceTableToConcat
	) {
		final String formattedExtendedJSON = PslUtils.formatExtendedJSON(rowToCsv(row, ","), sourceTableToConcat);
		// This will mess the JSON Extended
		if (formattedExtendedJSON.isBlank()) {
			return;
		}

		joinStringValue(currentResult, formattedExtendedJSON, ",\n");
	}

	/**
	 * Append the given <em>json</em> node to the <em>currentResult</em> {@link SourceTable} with
	 * the intent of building a JSON Array
	 *
	 * @param currentResult The {@link SourceTable} result to update
	 * @param json      The JSON node as string to append
	 */
	private void appendJsonToArray(final SourceTable currentResult, final String json) {
		// Don't mess the JSON Array
		if (json == null || json.isBlank()) {
			return;
		}

		joinStringValue(currentResult, json, ",\n");
	}

	/**
	 * Append the string value to the <em>currentResult</em> {@link SourceTable} using the passed <em>separator</em>.
	 * The separator is not appended for the first value
	 *
	 * @param currentResult The {@link SourceTable} result to update
	 * @param string        String value to append
	 * @param separator     Separator of the appended values
	 */
	private void joinStringValue(final SourceTable currentResult, final String string, @NonNull String separator) {
		currentResult.setRawData(
			(currentResult.getRawData().isBlank() ? EMPTY : currentResult.getRawData().concat(separator)).concat(string)
		);
	}

	/**
	 * Transform the {@link List} row to a {@link String} representation
	 * [a1,b1,c2]
	 *  =>
	 * a1,b1,c1
	 *
	 * @param row             The row result we wish to parse
	 * @param separator       The cells separator on each line
	 * @return {@link String} value
	 */
	public static String rowToCsv(final List<String> row, final String separator) {
		if (row != null) {
			return row.stream().filter(Objects::nonNull).collect(Collectors.joining(separator));
		}

		return EMPTY;
	}
}
