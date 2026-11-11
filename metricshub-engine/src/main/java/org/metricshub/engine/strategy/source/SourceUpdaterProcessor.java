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
import static org.metricshub.engine.common.helpers.MetricsHubConstants.MAX_THREADS_COUNT;
import static org.metricshub.engine.common.helpers.MetricsHubConstants.NEW_LINE;
import static org.metricshub.engine.common.helpers.MetricsHubConstants.SEMICOLON;
import static org.metricshub.engine.common.helpers.MetricsHubConstants.SOURCE_REF_PATTERN;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
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
import org.metricshub.engine.common.helpers.EscapeType;
import org.metricshub.engine.common.helpers.ProtocolPropertyReferenceHelper;
import org.metricshub.engine.connector.model.common.ContentType;
import org.metricshub.engine.connector.model.common.CustomConcatMethod;
import org.metricshub.engine.connector.model.common.EntryConcatMethod;
import org.metricshub.engine.connector.model.common.IEntryConcatMethod;
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

	/**
	 * The optional {@code esc(TYPE)::} prefix of a reference, as on the credential macros: the value is escaped
	 * with the {@link EscapeType} of that name before it is inserted.
	 */
	private static final String ESCAPE_PREFIX = "(?:esc\\((?<esc>[a-z0-9]+)\\)::)?";

	/**
	 * {@code ${attribute::name}}: a monitor attribute on a mono-instance collect. {@code ${param::name}}: an
	 * argument of a tool invocation. Both are served by the attribute map the strategy passes down.
	 */
	private static final Pattern MONO_INSTANCE_REPLACEMENT_PATTERN = Pattern.compile(
		"\\$\\{" + ESCAPE_PREFIX + "(?:attribute|param)::(?<key>[^}]+)\\}",
		Pattern.CASE_INSENSITIVE
	);

	private static final Pattern RESOURCE_ATTRIBUTE_REFERENCE_PATTERN = Pattern.compile(
		"\\$\\{" + ESCAPE_PREFIX + "resource\\.attribute::(?<key>[^}]+)\\}",
		Pattern.CASE_INSENSITIVE
	);

	private static final Pattern PROTOCOL_PATTERN = Pattern.compile("\\$\\{protocol::(.*?)\\}", Pattern.CASE_INSENSITIVE);

	private ISourceProcessor sourceProcessor;
	private TelemetryManager telemetryManager;
	private String connectorId;
	private Map<String, String> attributes;
	private ExtensionManager extensionManager;

	@Override
	public SourceTable process(final HttpSource httpSource) {
		// Very important! otherwise we will overlap in multi-host mode
		final var copy = httpSource.copy();

		// Replace HTTP Authentication token
		copy.setAuthenticationToken(
			extractHttpTokenFromSource(copy.getKey(), copy.getAuthenticationToken(), "authenticationToken")
		);

		return processSource(copy);
	}

	@Override
	public SourceTable process(EventLogSource eventLogSource) {
		return processSource(eventLogSource.copy());
	}

	@Override
	public SourceTable process(FileSource fileSource) {
		return processSource(fileSource.copy());
	}

	@Override
	public SourceTable process(JmxSource jmxSource) {
		return processSource(jmxSource.copy());
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
	public SourceTable process(final IpmiSelSource ipmiSelSource) {
		return processSource(ipmiSelSource.copy());
	}

	@Override
	public SourceTable process(final IpmiRawSource ipmiRawSource) {
		return processSource(ipmiRawSource.copy());
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

		copy.update(
			value -> replaceResourceAttributeReferences(value, telemetryManager.getHostConfiguration().getAttributes())
		);
		copy.update(value -> replaceAttributeReferences(value, attributes));

		copy.update(this::replaceProtocolPropertyReferences);

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

		String value = null;
		if (sourceTable.getContentType() == ContentType.TEXT) {
			// A text: up to the first separator
			log.debug("Hostname {} - Get {} defined in source {} from raw data.", hostname, fieldLabel, foreignSourceKey);
			value = sourceTable.getText().split(";")[0];
		} else if (!sourceTable.getTable().isEmpty()) {
			// A table: the first cell
			log.debug("Hostname {} - Get {} defined in source {} from list table.", hostname, fieldLabel, foreignSourceKey);
			final List<String> firstRow = sourceTable.getTable().get(0);
			if (firstRow != null && !firstRow.isEmpty()) {
				value = firstRow.get(0);
			}
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
			result.setText(String.format("[%s]", result.getText()));
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
		return (EntryConcatMethod.JSON_ARRAY_EXTENDED.equals(copy.getEntryConcatMethod()) ||
			EntryConcatMethod.JSON_ARRAY.equals(copy.getEntryConcatMethod()));
	}

	/**
	 * Replace the attribute identifiers referenced in the key with the attribute
	 * values that need to be retrieved from the given attributes lookup.
	 *
	 * @param key        the input string where replacements occur
	 * @param attributes attribute map
	 * @param pattern    regex with the groups {@code key} and {@code esc}, the escape type
	 * @return String value
	 */
	private static String replaceReferencesWithPattern(
		final String key,
		final Map<String, String> attributes,
		final Pattern pattern
	) {
		if (key == null || attributes == null) {
			return key;
		}

		final Matcher matcher = pattern.matcher(key);

		final StringBuffer sb = new StringBuffer();
		while (matcher.find()) {
			final String attrKey = matcher.group("key").trim();
			String attributesValue = attributes.get(attrKey);
			final String escapeName = matcher.group("esc");
			if (attributesValue != null && escapeName != null) {
				final EscapeType escapeType = EscapeType.fromString(escapeName);
				if (escapeType == null) {
					// Left as is rather than inserted unescaped
					log.warn(
						"Unknown escape type in {}: expected one of {}.",
						matcher.group(),
						Arrays.toString(EscapeType.values())
					);
					attributesValue = null;
				} else {
					attributesValue = escapeType.escape(attributesValue);
				}
			}
			// The text before the reference, so that the inserted value can be checked against its neighbors
			matcher.appendReplacement(sb, "");
			if (attributesValue != null && formsMacro(sb, attributesValue, key, matcher.end())) {
				// The macros are expanded after the references: a value must not reveal a credential or a source
				log.warn("The value of {} would form a macro (%{...} or ${...}): not inserted.", matcher.group());
				attributesValue = null;
			}
			sb.append(attributesValue != null ? attributesValue : matcher.group());
		}
		matcher.appendTail(sb);
		return sb.toString();
	}

	/**
	 * Whether a value inserted between the text already built and the rest of the template forms a <code>%{</code> or
	 * <code>${</code>, by itself or with a neighbor: {@code %} then {@code {PASSWORD}} is {@code %{PASSWORD}}.
	 *
	 * @param before   the text built so far
	 * @param value    the value to insert
	 * @param template the template
	 * @param end      the position in the template right after the reference
	 * @return {@code true} if the value must not be inserted
	 */
	private static boolean formsMacro(
		final StringBuffer before,
		final String value,
		final String template,
		final int end
	) {
		final String joined = (before.isEmpty() ? "" : before.substring(before.length() - 1)) +
			value +
			(end < template.length() ? template.substring(end, end + 1) : "");
		return joined.contains("%{") || joined.contains("${");
	}

	/**
	 * Replaces `${attribute::KEY}` in {@code key} using monitor attributes.
	 *
	 * @param key         input string
	 * @param attributes  monitor attributes
	 * @return resolved string, or original {@code key}
	 */
	public static String replaceAttributeReferences(final String key, final Map<String, String> attributes) {
		return replaceReferencesWithPattern(key, attributes, MONO_INSTANCE_REPLACEMENT_PATTERN);
	}

	/**
	 * Replaces `${resource.attributes::KEY}` in {@code key} using resource-level attributes.
	 *
	 * @param key                 input string
	 * @param resourceAttributes  resource attributes
	 * @return resolved string, or original {@code key}
	 */
	public static String replaceResourceAttributeReferences(
		final String key,
		final Map<String, String> resourceAttributes
	) {
		return replaceReferencesWithPattern(key, resourceAttributes, RESOURCE_ATTRIBUTE_REFERENCE_PATTERN);
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
		return (source instanceof CopySource ||
			source instanceof StaticSource ||
			source instanceof TableUnionSource ||
			source instanceof TableJoinSource ||
			// An awk source resolves an input that is a single source reference when the script runs, to the source as it is
			(source instanceof JawkSource jawk && JawkSource.isSourceReference(jawk.getInput())));
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
		final SourceTable sourceTable = telemetryManager.getHostProperties()
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

		// A table is serialized with ';' as is, a text is taken as is
		final boolean text = sourceTable.getContentType() == ContentType.TEXT;
		log.debug(
			"Hostname {} - Get {} referenced in {} ({}) from {}.",
			hostname,
			sourceRefKey,
			operationType,
			operationKey,
			text ? "raw data" : "list table"
		);
		final String sourceReferenceContent = text
			? sourceTable.getText()
			: SourceTable.tableToCsv(sourceTable.getTable(), SEMICOLON, false);

		// Remove last semicolon from the rows containing only one cell.
		// For instance the last semicolon leads to HTTP Errors when the reference is defined in the Header attribute
		return Stream.of(sourceReferenceContent.split(NEW_LINE)).map(val -> {
			final int indexOfSemicolon = val.indexOf(SEMICOLON);
			if (indexOfSemicolon == val.length() - 1) {
				return val.replace(SEMICOLON, EMPTY);
			}
			return val;
		}).collect(Collectors.joining(NEW_LINE));
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

		// A text for a text source or a text concatenation (JSON, custom), a table otherwise
		final SourceTable result = source.getResultContentType() == ContentType.TEXT
			? SourceTable.ofText(EMPTY)
			: SourceTable.empty();

		final Integer sleep = source.getSleepExecuteForEachEntryOf();
		final List<List<String>> table = maybeSourceTable.get().getTable();

		final int poolSize = computeExecuteForEachEntryOfPoolSize(source, sleep, table.size());
		final ExecutorService pool = poolSize > 1 ? Executors.newFixedThreadPool(poolSize) : null;
		final List<List<String>> pendingRows = new ArrayList<>();
		final List<Future<SourceTable>> pendingResults = new ArrayList<>();

		try {
			for (List<String> row : table) {
				if (row == null) {
					continue;
				}

				final Source copy = source.copy();

				try {
					copy.update(dataValue -> replaceDynamicEntry(dataValue, row));
				} catch (NumberFormatException e) {
					log.warn("Hostname {} - The dynamic key from Source is incorrectly formatted : {}.", hostname, copy);
					continue;
				}

				copy.update(
					value -> replaceResourceAttributeReferences(value, telemetryManager.getHostConfiguration().getAttributes())
				);
				copy.update(dataValue -> replaceAttributeReferences(dataValue, attributes));

				copy.update(value -> replaceProtocolPropertyReferences(value));

				copy.update(value -> replaceSourceReference(value, copy));

				if (pool != null) {
					pendingRows.add(row);
					pendingResults.add(pool.submit(() -> copy.accept(sourceProcessor)));
					continue;
				}

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

			// Concatenate the parallel results in the original row order, as the sequential run does
			for (int i = 0; i < pendingResults.size(); i++) {
				concatEntryResult(source, result, pendingRows.get(i), pendingResults.get(i).get());
			}
		} catch (InterruptedException e) {
			log.error(
				"Hostname {} - Thread interrupted while waiting for the parallel 'execute for each entry' requests.",
				hostname
			);
			Thread.currentThread().interrupt();
			return SourceTable.empty();
		} catch (ExecutionException e) {
			// Source.accept throws no checked exception: rethrow what the sequential run would have thrown
			if (e.getCause() instanceof Error error) {
				throw error;
			}
			throw (RuntimeException) e.getCause();
		} finally {
			if (pool != null) {
				pool.shutdownNow();
			}
		}

		return result;
	}

	/**
	 * Compute the number of threads processing the entries of an <em>executeForEachEntryOf</em> source.
	 * The entries are processed sequentially (1) unless <em>maxParallel</em> is set, and also when
	 * <em>sleep</em> is set (throttling), the source forces serialization or the host is configured as sequential.
	 *
	 * @param source     The {@link Source} defining the executeForEachEntryOf
	 * @param sleep      The sleep in milliseconds between two entries
	 * @param entryCount The number of entries to process
	 * @return The pool size, 1 or less meaning sequential processing
	 */
	private int computeExecuteForEachEntryOfPoolSize(final Source source, final Integer sleep, final int entryCount) {
		final Integer maxParallel = source.getMaxParallelExecuteForEachEntryOf();
		if (
			maxParallel == null ||
				(sleep != null && sleep != 0) ||
				source.isForceSerialization() ||
				telemetryManager.getHostConfiguration().isSequential()
		) {
			return 1;
		}
		return Math.min(Math.min(maxParallel, MAX_THREADS_COUNT), entryCount);
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

		if (iEntryConcatMethod == null || iEntryConcatMethod == EntryConcatMethod.LIST) {
			// LIST keeps the result type the source declares: an entry of the other type is converted, never dropped
			if (currentResult.getContentType() == ContentType.TEXT) {
				final String text = sourceTableToConcat.asText();
				if (!text.isBlank()) {
					joinStringValue(currentResult, text, "\n");
				}
			} else {
				final List<List<String>> rows = currentResult.getTable();
				sourceTableToConcat.asTable().stream().filter(line -> line != null && !line.isEmpty()).forEach(rows::add);
			}
			return;
		}

		// The text concatenations read each entry as a text (a table is serialized) and skip a failed entry: an empty
		// table, what a processor returns on a failure (an empty text is an answer, which the custom method wraps)
		if (sourceTableToConcat.getContentType() == ContentType.TABLE && sourceTableToConcat.isEmpty()) {
			return;
		}

		if (iEntryConcatMethod instanceof CustomConcatMethod customConcatMethod) {
			appendCustomEntryResult(customConcatMethod, currentResult, row, sourceTableToConcat.asText());
		} else if (iEntryConcatMethod == EntryConcatMethod.JSON_ARRAY) {
			appendJsonToArray(currentResult, sourceTableToConcat.asText());
		} else {
			appendExtendedJsonToArray(currentResult, row, sourceTableToConcat);
		}
	}

	/**
	 * Append the custom entry result <em>text</em> to the given <em>currentResult</em> {@link SourceTable}.
	 *
	 * @param customConcatMethod The {@link CustomConcatMethod} instance.
	 * @param currentResult      The {@link SourceTable} result to update.
	 * @param row                The row to concatenate in case we have the JSON_ARRAY_EXTENDED concatenation method.
	 * @param text               The text to append.
	 */
	private void appendCustomEntryResult(
		final CustomConcatMethod customConcatMethod,
		final SourceTable currentResult,
		final List<String> row,
		final String text
	) {
		String entryConcatStart = customConcatMethod.getConcatStart() != null
			? replaceDynamicEntry(customConcatMethod.getConcatStart(), row)
			: EMPTY;
		String entryConcatEnd = customConcatMethod.getConcatEnd() != null
			? replaceDynamicEntry(customConcatMethod.getConcatEnd(), row)
			: EMPTY;

		currentResult.setText(currentResult.getText().concat(entryConcatStart).concat(text).concat(entryConcatEnd));
	}

	/**
	 * Build the Extended JSON then append it to the <em>currentResult</em> {@link SourceTable}.
	 *
	 * @param currentResult       The {@link SourceTable} result to update
	 * @param row                 The row used to build the Extended JSON
	 * @param sourceTableToConcat The {@link SourceTable} defining the <em>text</em> to concatenate.
	 */
	private void appendExtendedJsonToArray(
		final SourceTable currentResult,
		final List<String> row,
		final SourceTable sourceTableToConcat
	) {
		final String formattedExtendedJSON = PslUtils.formatExtendedJSON(row, sourceTableToConcat);
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
		currentResult.setText(
			(currentResult.getText().isBlank() ? EMPTY : currentResult.getText().concat(separator)).concat(string)
		);
	}

	/**
	 * Replace the attribute identifiers referenced in the key
	 * with the protocol properties that need to be retrieved from the {@link TelemetryManager}.
	 *
	 * @param key The key where to replace the properties.
	 * @return String value
	 */
	public String replaceProtocolPropertyReferences(final String key) {
		return replaceProtocolPropertyReferences(key, telemetryManager);
	}

	/**
	 * Replaces <code>${protocol::PROPERTY}</code> references using the given telemetry manager.
	 *
	 * @param key              the value to update
	 * @param telemetryManager the telemetry manager holding the configurations
	 * @return String value
	 */
	public static String replaceProtocolPropertyReferences(final String key, final TelemetryManager telemetryManager) {
		if (key == null) {
			return key;
		}

		final Matcher matcher = PROTOCOL_PATTERN.matcher(key);

		final StringBuilder sb = new StringBuilder();
		while (matcher.find()) {
			final String protocolPropertyValue = matcher.group(1);
			if (protocolPropertyValue != null) {
				matcher.appendReplacement(
					sb,
					Matcher.quoteReplacement(
						ProtocolPropertyReferenceHelper.getProtocolProperty(protocolPropertyValue, telemetryManager)
					)
				);
			}
		}
		matcher.appendTail(sb);

		return sb.toString();
	}
}
