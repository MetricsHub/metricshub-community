package org.metricshub.extension.win.source;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * MetricsHub Win Extension Common
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

import static org.metricshub.engine.common.helpers.MetricsHubConstants.WMI_DEFAULT_NAMESPACE;
import static org.metricshub.engine.common.helpers.StringHelper.nonNullNonBlank;
import static org.metricshub.engine.connector.model.monitor.task.source.EventLogSource.UNLIMITED_EVENTS_PER_POLL;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.metricshub.engine.common.helpers.LoggingHelper;
import org.metricshub.engine.connector.model.monitor.task.source.EventLogLevel;
import org.metricshub.engine.connector.model.monitor.task.source.EventLogSource;
import org.metricshub.engine.strategy.source.SourceTable;
import org.metricshub.engine.telemetry.ConnectorNamespace;
import org.metricshub.engine.telemetry.TelemetryManager;
import org.metricshub.extension.win.IWinConfiguration;
import org.metricshub.extension.win.IWinRequestExecutor;

/**
 * Processes {@link EventLogSource} definitions and returns a {@link SourceTable} built from Windows Event Log data.
 * <p>
 * This processor:
 * <ul>
 *   <li>Builds a WQL query against {@code Win32_NTLogEvent} (WMI) based on {@link EventLogSource} filters</li>
 *   <li>Executes the query via {@link IWinRequestExecutor} using either WMI or WinRM credentials</li>
 *   <li>Post-processes results to support incremental polling using a per-host cursor stored in
 *       {@link ConnectorNamespace}</li>
 * </ul>
 * <p>
 * Incremental polling strategy:
 * <ul>
 *   <li><strong>First poll</strong> (cursor is {@code null}): query selects minimal columns and the processor
 *       initializes the cursor but returns an empty result table to avoid sending a backlog</li>
 *   <li><strong>Subsequent polls</strong> (cursor is set): query selects full columns, filters with
 *       {@code RecordNumber > cursor}, returns data, and advances the cursor based on the returned events</li>
 * </ul>
 */
@Slf4j
@RequiredArgsConstructor
public class EventLogSourceProcessor {

	/**
	 * Column index for {@code RecordNumber} in the WMI result rows.
	 */
	private static final int RECORD_NUMBER_COLUMN = 0;

	/**
	 * Column index for {@code EventType} (event log level) in the WMI result rows.
	 */
	private static final int EVENT_LOG_LEVEL_COLUMN = 4;

	/**
	 * Column index for {@code InsertionStrings} in the WMI result rows.
	 */
	private static final int INSERTION_STRINGS_COLUMN = 7;

	/**
	 * Column index for {@code Message} in the WMI result rows.
	 */
	private static final int MESSAGE_COLUMN = 8;

	/**
	 * Escape string for new lines.
	 */
	private static final String NEW_LINE_ESCAPE_STRING = "@{newLine}@";

	/**
	 * Returns whether this is the first poll for a given host/source.
	 *
	 * @param cursor the current cursor value
	 * @return {@code true} if the cursor is {@code null}, {@code false} otherwise
	 */
	private static boolean isFirstPoll(final Integer cursor) {
		return cursor == null;
	}

	/**
	 * Returns whether the polling is configured as unlimited (no max-per-poll limit).
	 *
	 * @param maxEventsPerPoll the configured max events per poll
	 * @return {@code true} if unlimited, {@code false} otherwise
	 */
	private static boolean isUnlimited(final int maxEventsPerPoll) {
		return maxEventsPerPoll == UNLIMITED_EVENTS_PER_POLL;
	}

	@NonNull
	private IWinRequestExecutor winRequestExecutor;

	@NonNull
	private Function<TelemetryManager, IWinConfiguration> configurationRetriever;

	@NonNull
	private String connectorId;

	/**
	 * Processes an {@link EventLogSource} and returns a {@link SourceTable} containing Windows Event Log data.
	 *
	 * @param eventLogSource the event log source definition
	 * @param telemetryManager the telemetry manager providing host configuration and connector namespace
	 * @return a {@link SourceTable} containing the event log results, or an empty table on error
	 */
	public SourceTable process(final EventLogSource eventLogSource, final TelemetryManager telemetryManager) {
		final String hostname = telemetryManager.getHostname();

		if (eventLogSource == null) {
			log.warn("Hostname {} - Malformed EventLog source {}. Returning an empty table.", hostname, eventLogSource);
			return SourceTable.empty();
		}

		// Find the configured protocol (WinRM or WMI)
		final IWinConfiguration winConfiguration = configurationRetriever.apply(telemetryManager);

		if (winConfiguration == null) {
			log.debug(
				"Hostname {} - Neither WMI nor WinRM credentials are configured for this host. Returning an empty table for EventLog source {}.",
				hostname,
				eventLogSource.getKey()
			);
			return SourceTable.empty();
		}

		// Retrieve the hostname from the IWinConfiguration, otherwise from the telemetryManager
		final String resolvedHostname = telemetryManager.getHostname(List.of(winConfiguration.getClass()));

		// Get the connector namespace where the cursor is stored
		final ConnectorNamespace connectorNamespace = telemetryManager
			.getHostProperties()
			.getConnectorNamespace(connectorId);

		final String sourceKey = eventLogSource.getKey();
		// Get the stored cursor from the connector namespace
		final Integer cursor = connectorNamespace.getEventLogCursor(sourceKey);

		// Build the WQL query for Windows Event Logs
		final String wqlQuery = buildEventLogQuery(eventLogSource, cursor);

		if (wqlQuery == null || wqlQuery.isBlank()) {
			log.error(
				"Hostname {} - Failed to build WQL query for EventLog source {}. Returning an empty table.",
				resolvedHostname,
				eventLogSource.getKey()
			);
			return SourceTable.empty();
		}

		log.info("Executing WMI query on {}: \n{}", hostname, wqlQuery);

		try {
			// Execute the WMI query which returns the Event Logs
			final List<List<String>> table = winRequestExecutor.executeWmi(
				resolvedHostname,
				winConfiguration,
				wqlQuery,
				WMI_DEFAULT_NAMESPACE
			);

			// Post process Event Logs
			final PostProcessingResult postProcessingResult = postProcessEventLogs(table, eventLogSource, cursor);

			log.info(
				"""
				Hostname {} - EventLog poll
				Source: {}
				Current RecordNumber cursor: {}
				New recordNumber cursor: {}
				Returning {} rows
				""",
				hostname,
				eventLogSource.getKey(),
				cursor,
				postProcessingResult.getCursor(),
				postProcessingResult.getResults() != null ? postProcessingResult.getResults().size() : 0
			);

			// Store the new marker in the telemetry manager to use in the next iteration.
			connectorNamespace.setEventLogCursor(sourceKey, postProcessingResult.getCursor());

			return SourceTable.builder().table(postProcessingResult.getResults()).build();
		} catch (Exception e) {
			LoggingHelper.logSourceError(
				connectorId,
				eventLogSource.getKey(),
				String.format(
					"EventLog query=%s, Username=%s, Timeout=%d, Namespace=%s",
					wqlQuery,
					winConfiguration.getUsername(),
					winConfiguration.getTimeout(),
					WMI_DEFAULT_NAMESPACE
				),
				resolvedHostname,
				e
			);

			return SourceTable.empty();
		}
	}

	/**
	 * Post-processes raw WMI query results for an {@link EventLogSource}.
	 * <p>
	 * This method:
	 * <ul>
	 *   <li>Sorts events by {@code RecordNumber} (index {@value #RECORD_NUMBER_COLUMN}) ascending</li>
	 *   <li>When not first poll, maps the numeric {@code EventType} (index {@value #EVENT_LOG_LEVEL_COLUMN})
	 *       to a user-friendly alias (e.g. {@code 3 -> "Information"})</li>
	 *   <li>Applies {@link EventLogSource#getMaxEventsPerPoll()} when cursor is set (except unlimited)</li>
	 *   <li>Computes the next cursor using {@link #resolveMarker(List, EventLogSource, Integer)}</li>
	 * </ul>
	 * <p>
	 * First poll behavior (cursor is {@code null}):
	 * the method initializes the cursor from the returned data but returns an empty list to avoid emitting an initial backlog.
	 *
	 * @param queryResults  raw results returned by the WMI query
	 * @param eventLogSource the event log source definition (used for maxEventsPerPoll)
	 * @param cursor        current cursor; {@code null} indicates the first poll
	 * @return a {@link PostProcessingResult} containing the (possibly empty) results and the next cursor value
	 */
	PostProcessingResult postProcessEventLogs(
		final List<List<String>> queryResults,
		final EventLogSource eventLogSource,
		final Integer cursor
	) {
		if (queryResults == null || queryResults.isEmpty()) {
			return PostProcessingResult.builder().cursor(isFirstPoll(cursor) ? 0 : cursor).build();
		}

		// Sort the results, then retain only the maxEventsPerPoll elements
		final List<List<String>> finalResults = queryResults
			.stream()
			.sorted(Comparator.comparingLong((List<String> list) -> Integer.parseInt(list.get(RECORD_NUMBER_COLUMN))))
			// Make sure each row is mutable before performing in-place column replacement.
			// WMI results (and unit tests) may provide immutable rows (e.g. List.of(...)).
			.map(ArrayList::new)
			.collect(Collectors.toCollection(ArrayList::new));

		if (!isFirstPoll(cursor)) {
			finalResults.forEach(line -> {
				// Replace the int Event Log Level by its text equivalent for better readability.
				final int level = Integer.parseInt(line.get(EVENT_LOG_LEVEL_COLUMN));
				line.set(EVENT_LOG_LEVEL_COLUMN, EventLogLevel.detectFromCode(level).getAlias());

				// Escape the new line \r\n character by a special character to avoid any problem using AWK scripts.
				final String insertionStrings = line.get(INSERTION_STRINGS_COLUMN);
				if (insertionStrings != null && !insertionStrings.isBlank()) {
					line.set(INSERTION_STRINGS_COLUMN, escapeNewLines(insertionStrings));
				}

				// Escape the new line \r\n character by a special character to avoid any problem using AWK scripts.
				final String message = line.get(MESSAGE_COLUMN);
				if (message != null && !message.isBlank()) {
					line.set(MESSAGE_COLUMN, escapeNewLines(message));
				}
			});
		}

		final int maxEventsPerPoll = eventLogSource.getMaxEventsPerPoll();
		final int resultsSize = finalResults.size();

		// If it's not the first poll and max isn't unlimited (-1), keep at most `max` elements.
		if (!isUnlimited(maxEventsPerPoll) && !isFirstPoll(cursor)) {
			finalResults.subList(resultsSize > maxEventsPerPoll ? maxEventsPerPoll : resultsSize, resultsSize).clear();
		}

		// Compute next cursor
		final int newCursor = resolveMarker(finalResults, eventLogSource, cursor);

		return PostProcessingResult
			.builder()
			.cursor(newCursor)
			.results(isFirstPoll(cursor) ? new ArrayList<>() : finalResults)
			.build();
	}

	/**
	 * Computes the next event-log cursor (RecordNumber) based on the processed results.
	 * <p>
	 * Rules:
	 * <ul>
	 *   <li>If no results are returned: keep existing cursor (or return {@code 0} on first poll)</li>
	 *   <li>If first poll or unlimited: cursor becomes the last returned event record number</li>
	 *   <li>Otherwise: cursor becomes the record number of the last kept event (after maxEventsPerPoll limiting)</li>
	 * </ul>
	 *
	 * @param results       sorted event rows (RecordNumber at index {@value #RECORD_NUMBER_COLUMN})
	 * @param eventLogSource source definition holding maxEventsPerPoll
	 * @param cursor        current cursor; {@code null} indicates first poll
	 * @return next cursor value
	 */
	Integer resolveMarker(final List<List<String>> results, final EventLogSource eventLogSource, final Integer cursor) {
		if (results == null || results.isEmpty()) {
			return isFirstPoll(cursor) ? 0 : cursor;
		}

		final int maxEventsPerPoll = eventLogSource.getMaxEventsPerPoll();
		final int numberOfEvents = results.size();

		Integer lastEventPosition;

		// Either it's the first poll, or there is no limit, the cursor is set to the last event record number.
		// Example: results size is 10 -> cursor is set to the record number of the 10th element (position 9).
		if (isUnlimited(maxEventsPerPoll) || isFirstPoll(cursor)) {
			lastEventPosition = numberOfEvents - 1;
		} else {
			// If the number of returned events is superior than the maxEventsPerPoll, take "max" positioned element position
			// Otherwise, take the last event position
			// Examples:
			// - if results size is 10 and max = 5, cursor will be set as the record number of the 5th element (position 4)
			// - if results size is 10 and max = 50, cursor will be set as the record number of the 10th element (position 9)
			lastEventPosition =
				numberOfEvents > 0 && numberOfEvents > maxEventsPerPoll ? maxEventsPerPoll - 1 : numberOfEvents - 1;
		}

		final List<String> lastEvent = results.get(lastEventPosition);
		return Integer.parseInt(lastEvent.get(RECORD_NUMBER_COLUMN));
	}

	/**
	 * Builds a WQL query for Windows Event Logs based on {@link EventLogSource} filters and the current cursor.
	 * <p>
	 * First poll (cursor is {@code null}): selects only {@code RecordNumber} to minimize payload.
	 * Subsequent polls: selects full columns and adds {@code RecordNumber > cursor}.
	 *
	 * @param eventLogSource event log source filters
	 * @param cursor current cursor; {@code null} indicates first poll
	 * @return a WQL query string for querying {@code Win32_NTLogEvent}
	 */
	String buildEventLogQuery(final EventLogSource eventLogSource, final Integer cursor) {
		final StringBuilder queryBuilder = new StringBuilder("SELECT ");

		if (isFirstPoll(cursor)) {
			queryBuilder.append("RecordNumber ");
		} else {
			queryBuilder.append("RecordNumber, TimeGenerated, TimeWritten, EventCode, EventType, ");
			queryBuilder.append("EventIdentifier, SourceName, InsertionStrings, Message, LogFile ");
		}

		queryBuilder.append("FROM Win32_NTLogEvent ");

		final List<String> whereConditions = new ArrayList<>();

		// Add log name filter if specified
		final String logName = eventLogSource.getLogName();

		if (nonNullNonBlank(logName)) {
			whereConditions.add(String.format("LogFile = '%s'", escapeWqlString(logName)));
		}

		// Add event ID filter if specified
		final Set<String> eventsIds = eventLogSource.getEventIds();

		if (!eventsIds.isEmpty()) {
			whereConditions.add(joinWithOrString("EventCode", eventsIds));
		}

		// Add source filter if specified
		final Set<String> sources = eventLogSource.getSources();

		if (!sources.isEmpty()) {
			whereConditions.add(joinWithOrString("SourceName", sources));
		}

		// Add level filter if specified
		final Set<String> eventTypes = eventLogSource
			.getLevels()
			.stream()
			.filter(Objects::nonNull)
			.map(level -> String.valueOf(level.getCode()))
			.collect(Collectors.toCollection(LinkedHashSet::new));

		if (!eventTypes.isEmpty()) {
			// EventType: 1=Error, 2=Warning, 3=Information, 4=Security Audit Success, 5=Security Audit Failure
			whereConditions.add(joinWithOrString("EventType", eventTypes));
		}

		// Add stop mark filter if specified (RecordNumber)
		if (!isFirstPoll(cursor)) {
			whereConditions.add(String.format("RecordNumber > %d", cursor));
		}

		// Add WHERE clause if we have conditions
		if (!whereConditions.isEmpty()) {
			queryBuilder.append("WHERE ");
			queryBuilder.append(String.join(" AND ", whereConditions));
		}

		return queryBuilder.toString();
	}

	/**
	 * Builds a WQL OR predicate from a set of values for a given attribute.
	 * Values are trimmed, deduplicated, and sorted for deterministic output.
	 *
	 * @param attribute the WQL attribute name
	 * @param values the set of values to join with OR
	 * @return a WQL predicate string like {@code "(attribute = 'value1' OR attribute = 'value2')"}, or null if empty
	 */
	static String joinWithOrString(@NonNull final String attribute, @NonNull final Set<String> values) {
		final String predicate = values
			.stream()
			.filter(Objects::nonNull)
			.map(String::trim)
			.filter(value -> !value.isEmpty())
			.distinct()
			.sorted()
			.map(value -> attribute + " = '" + escapeWqlString(value) + "'")
			.collect(Collectors.joining(" OR "));

		return predicate.isBlank() ? null : "(" + predicate + ")";
	}

	/**
	 * Escapes special characters in a WQL string value.
	 *
	 * @param value The string value to escape.
	 * @return The escaped string.
	 */
	static String escapeWqlString(final String value) {
		if (value == null) {
			return "";
		}
		// Escape single quotes by doubling them
		return value.replace("'", "''");
	}

	/**
	 * Escapes newline characters in a string by replacing them with a placeholder.
	 * Handles Windows line endings (\r\n) and Unix line endings (\n).
	 *
	 * @param value the string to escape
	 * @return the string with newlines replaced by {@value #NEW_LINE_ESCAPE_STRING}, or empty string if input is null
	 */
	static String escapeNewLines(final String value) {
		if (value == null) {
			return "";
		}

		// Replace \r\n first (Windows line endings), then handle any remaining \r or \n
		// Use replace() for literal replacements to avoid regex interpretation of $ in replacement string
		return value
			.replace("\r\n", NEW_LINE_ESCAPE_STRING)
			.replace("\n", NEW_LINE_ESCAPE_STRING)
			.replace("\r", NEW_LINE_ESCAPE_STRING);
	}
}
