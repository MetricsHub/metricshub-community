package org.metricshub.extension.win.source;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.metricshub.engine.common.exception.ClientException;
import org.metricshub.engine.connector.model.monitor.task.source.EventLogLevel;
import org.metricshub.engine.connector.model.monitor.task.source.EventLogSource;
import org.metricshub.engine.strategy.source.SourceTable;
import org.metricshub.engine.telemetry.ConnectorNamespace;
import org.metricshub.engine.telemetry.HostProperties;
import org.metricshub.engine.telemetry.TelemetryManager;
import org.metricshub.extension.win.IWinConfiguration;
import org.metricshub.extension.win.IWinRequestExecutor;
import org.metricshub.extension.win.WmiTestConfiguration;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EventLogSourceProcessorTest {

	private static final String CONNECTOR_ID = "connector_id";
	private static final String HOSTNAME = "hostname";

	private EventLogSourceProcessor processor;

	@Mock
	private IWinRequestExecutor executorMock;

	@Mock
	Function<TelemetryManager, IWinConfiguration> configurationRetrieverMock;

	@Mock
	private TelemetryManager telemetryManagerMock;

	private IWinConfiguration wmiConfiguration;

	private ConnectorNamespace connectorNamespace;

	@BeforeEach
	void setup() {
		processor = new EventLogSourceProcessor(executorMock, configurationRetrieverMock, CONNECTOR_ID);
	}

	void setupIT() {
		connectorNamespace = ConnectorNamespace.builder().build();
		final HostProperties hostProperties = HostProperties
			.builder()
			.connectorNamespaces(new HashMap<>(Map.of(CONNECTOR_ID, connectorNamespace)))
			.build();
		wmiConfiguration = WmiTestConfiguration.builder().hostname(HOSTNAME).build();

		when(telemetryManagerMock.getHostProperties()).thenReturn(hostProperties);
		when(telemetryManagerMock.getHostname()).thenReturn(HOSTNAME);
		when(telemetryManagerMock.getHostname(any())).thenReturn(HOSTNAME);
		when(configurationRetrieverMock.apply(any(TelemetryManager.class))).thenReturn(wmiConfiguration);
	}

	@Test
	void processTest() throws ClientException {
		setupIT();

		final EventLogSource source = EventLogSource
			.builder()
			.key("sourceKey")
			.logName("System")
			.sources(Set.of("Security-Auditing"))
			.eventIds(Set.of("4702"))
			.maxEventsPerPoll(2)
			.build();

		List<List<String>> wmiRequestResults = new ArrayList<>(
			List.of(
				new ArrayList<>(
					List.of(
						"8",
						"1767087780",
						"1767087780",
						"6005",
						"3",
						"2147489653",
						"EventLog",
						"",
						"The Event log service was started.",
						"System"
					)
				),
				new ArrayList<>(
					List.of(
						"2",
						"1767087755",
						"1767087755",
						"6005",
						"3",
						"2147489653",
						"EventLog",
						"",
						"The Event log service was started.",
						"System"
					)
				),
				new ArrayList<>(
					List.of(
						"5",
						"1767087770",
						"1767087770",
						"6005",
						"3",
						"2147489653",
						"EventLog",
						"",
						"The Event log service was started.",
						"System"
					)
				),
				new ArrayList<>(
					List.of(
						"4",
						"1767087755",
						"1767087755",
						"6005",
						"3",
						"2147489653",
						"EventLog",
						"",
						"The Event log service was started.",
						"System"
					)
				)
			)
		);

		String query =
			"SELECT RecordNumber FROM Win32_NTLogEvent " +
			"WHERE LogFile = 'System' AND (EventCode = '4702') AND (SourceName = 'Security-Auditing')";

		when(executorMock.executeWmi(anyString(), any(IWinConfiguration.class), eq(query), anyString()))
			.thenReturn(wmiRequestResults);

		// ITERATION 1:
		// WMI request result contains 4 records
		// Cursors not set yet: setting up the cursors and not returning any results
		SourceTable results = processor.process(source, telemetryManagerMock);
		assertEquals(
			8,
			connectorNamespace.getEventLogCursor("sourceKey"),
			"Cursor should be set to 8 after first iteration."
		);
		assertTrue(results.getTable().isEmpty(), "First iteration should not return any data (avoid backlog).");

		// SETTINGS before iteration 2
		wmiRequestResults.clear();
		wmiRequestResults.add(
			new ArrayList<>(
				List.of(
					"15",
					"1767087999",
					"1767087999",
					"6005",
					"3",
					"2147489653",
					"EventLog",
					"",
					"The Event log service was started.",
					"System"
				)
			)
		);
		wmiRequestResults.add(
			new ArrayList<>(
				List.of(
					"10",
					"1767087980",
					"1767087980",
					"6005",
					"3",
					"2147489653",
					"EventLog",
					"",
					"The Event log service was started.",
					"System"
				)
			)
		);
		wmiRequestResults.add(
			new ArrayList<>(
				List.of(
					"11",
					"1767087985",
					"1767087985",
					"6005",
					"3",
					"2147489653",
					"EventLog",
					"",
					"The Event log service was started.",
					"System"
				)
			)
		);

		query =
			"SELECT RecordNumber, TimeGenerated, TimeWritten, EventCode, EventType, " +
			"EventIdentifier, SourceName, InsertionStrings, Message, LogFile " +
			"FROM Win32_NTLogEvent WHERE LogFile = 'System' AND (EventCode = '4702') AND (SourceName = 'Security-Auditing') AND RecordNumber > 8";

		when(executorMock.executeWmi(anyString(), any(IWinConfiguration.class), eq(query), anyString()))
			.thenReturn(wmiRequestResults);

		// ITERATION 2:
		// WMI request result contains 3 records
		// Cursors are set: returning 2 (max) results and setting up the cursor to the highest record number
		results = processor.process(source, telemetryManagerMock);
		List<List<String>> resultTable = results.getTable();
		assertEquals(
			11,
			connectorNamespace.getEventLogCursor("sourceKey"),
			"Cursor should be set to 11 after processing iteration 2."
		);
		assertEquals(2, resultTable.size(), "Result size should be equal to maxEventsPerPoll=2.");
		assertEquals(
			"10",
			resultTable.get(0).get(0),
			"Result table should contain RecordNumber 10 as first element (sorted)."
		);
		assertEquals(
			"11",
			resultTable.get(1).get(0),
			"Result table should contain RecordNumber 11 as second element (sorted)."
		);

		// ITERATION 3:
		// A ClientException is thrown, the client returns null
		// Cursors are set, they remain unchanged
		when(executorMock.executeWmi(anyString(), any(IWinConfiguration.class), eq(query), anyString()))
			.thenThrow(ClientException.class);
		results = processor.process(source, telemetryManagerMock);
		assertEquals(
			11,
			connectorNamespace.getEventLogCursor("sourceKey"),
			"Cursor should remain unchanged when an exception occurs."
		);
		assertTrue(results.getTable().isEmpty(), "Result table should be empty when an exception is thrown.");
	}

	@Test
	void testProcessWithNegativeMax() throws ClientException {
		setupIT();

		final EventLogSource source = EventLogSource
			.builder()
			.key("sourceKey")
			.logName("System")
			.sources(Set.of("Security-Auditing"))
			.eventIds(Set.of("4702"))
			.maxEventsPerPoll(-1)
			.build();

		List<List<String>> wmiRequestResults = new ArrayList<>(
			List.of(
				new ArrayList<>(
					List.of(
						"8",
						"1767087780",
						"1767087780",
						"6005",
						"3",
						"2147489653",
						"EventLog",
						"",
						"The Event log service was started.",
						"System"
					)
				),
				new ArrayList<>(
					List.of(
						"2",
						"1767087755",
						"1767087755",
						"6005",
						"3",
						"2147489653",
						"EventLog",
						"",
						"The Event log service was started.",
						"System"
					)
				),
				new ArrayList<>(
					List.of(
						"5",
						"1767087770",
						"1767087770",
						"6005",
						"3",
						"2147489653",
						"EventLog",
						"",
						"The Event log service was started.",
						"System"
					)
				),
				new ArrayList<>(
					List.of(
						"4",
						"1767087755",
						"1767087755",
						"6005",
						"3",
						"2147489653",
						"EventLog",
						"",
						"The Event log service was started.",
						"System"
					)
				)
			)
		);

		String query =
			"SELECT RecordNumber FROM Win32_NTLogEvent " +
			"WHERE LogFile = 'System' AND (EventCode = '4702') AND (SourceName = 'Security-Auditing')";

		when(executorMock.executeWmi(anyString(), any(IWinConfiguration.class), eq(query), anyString()))
			.thenReturn(wmiRequestResults);

		// ITERATION 1:
		// WMI request result contains 4 records
		// Cursors not set yet: setting up the cursors and not returning any results
		SourceTable results = processor.process(source, telemetryManagerMock);
		assertEquals(
			8,
			connectorNamespace.getEventLogCursor("sourceKey"),
			"Cursor should be set to 8 after first iteration (even with max=-1)."
		);
		assertTrue(
			results.getTable().isEmpty(),
			"First iteration should not return any data (avoid backlog), even with max=-1."
		);

		// SETTINGS before iteration 2
		wmiRequestResults.clear();
		wmiRequestResults.add(
			new ArrayList<>(
				List.of(
					"15",
					"1767087999",
					"1767087999",
					"6005",
					"3",
					"2147489653",
					"EventLog",
					"",
					"The Event log service was started.",
					"System"
				)
			)
		);
		wmiRequestResults.add(
			new ArrayList<>(
				List.of(
					"10",
					"1767087980",
					"1767087980",
					"6005",
					"3",
					"2147489653",
					"EventLog",
					"",
					"The Event log service was started.",
					"System"
				)
			)
		);
		wmiRequestResults.add(
			new ArrayList<>(
				List.of(
					"11",
					"1767087985",
					"1767087985",
					"6005",
					"3",
					"2147489653",
					"EventLog",
					"",
					"The Event log service was started.",
					"System"
				)
			)
		);

		query =
			"SELECT RecordNumber, TimeGenerated, TimeWritten, EventCode, EventType, " +
			"EventIdentifier, SourceName, InsertionStrings, Message, LogFile " +
			"FROM Win32_NTLogEvent WHERE LogFile = 'System' AND (EventCode = '4702') AND (SourceName = 'Security-Auditing') AND RecordNumber > 8";

		when(executorMock.executeWmi(anyString(), any(IWinConfiguration.class), eq(query), anyString()))
			.thenReturn(wmiRequestResults);

		// ITERATION 2:
		// WMI request result contains 3 records
		// Cursors are set: returning all results and setting up the cursor to the highest record number (15)
		results = processor.process(source, telemetryManagerMock);
		List<List<String>> resultTable = results.getTable();
		assertEquals(
			15,
			connectorNamespace.getEventLogCursor("sourceKey"),
			"Cursor should be set to the highest RecordNumber when max=-1."
		);
		assertEquals(3, resultTable.size(), "Result size should be equal to the number of returned events when max=-1.");
		assertEquals(
			"10",
			resultTable.get(0).get(0),
			"Result table should contain RecordNumber 10 as first element (sorted)."
		);
		assertEquals(
			"11",
			resultTable.get(1).get(0),
			"Result table should contain RecordNumber 11 as second element (sorted)."
		);
		assertEquals(
			"15",
			resultTable.get(2).get(0),
			"Result table should contain RecordNumber 15 as third element (sorted)."
		);
	}

	@Test
	void testBuildEventLogQuery_logNameAndEventId_firstIteration_selectsMinimalColumns() {
		final EventLogSource source = EventLogSource.builder().logName("System").eventIds(Set.of("8005", "9010")).build();

		final String result = processor.buildEventLogQuery(source, null);

		final String expected =
			"SELECT RecordNumber " +
			"FROM Win32_NTLogEvent " +
			"WHERE LogFile = 'System' AND (EventCode = '8005' OR EventCode = '9010')";

		assertEquals(
			expected,
			result,
			"Query should select minimal columns on first iteration (stopMark=0, stopTime=0) and include logName + eventId filters."
		);
	}

	@Test
	void testBuildEventLogQuery_onlyLogName_firstIteration_selectsMinimalColumns() {
		final EventLogSource source = EventLogSource.builder().logName("System").build();

		final String result = processor.buildEventLogQuery(source, null);

		final String expected = "SELECT RecordNumber FROM Win32_NTLogEvent WHERE LogFile = 'System'";

		assertEquals(
			expected,
			result,
			"Query should select minimal columns on first iteration and include only the logName filter."
		);
	}

	@Test
	void testBuildEventLogQuery_onlyEventId_firstIteration_selectsMinimalColumns() {
		final EventLogSource source = EventLogSource.builder().eventIds(Set.of("6009", "6005")).build();

		final String result = processor.buildEventLogQuery(source, null);

		final String expected =
			"SELECT RecordNumber " + "FROM Win32_NTLogEvent " + "WHERE (EventCode = '6005' OR EventCode = '6009')";

		assertEquals(
			expected,
			result,
			"Query should select minimal columns on first iteration and include only the eventId predicate."
		);
	}

	@Test
	void testBuildEventLogQuery_onlySources_firstIteration_selectsMinimalColumns() {
		final EventLogSource source = EventLogSource
			.builder()
			.sources(Set.of("Winlogon", "Service Control Manager"))
			.build();

		final String result = processor.buildEventLogQuery(source, null);

		final String expected =
			"SELECT RecordNumber " +
			"FROM Win32_NTLogEvent " +
			"WHERE (SourceName = 'Service Control Manager' OR SourceName = 'Winlogon')";

		assertEquals(
			expected,
			result,
			"Query should select minimal columns on first iteration and include only the source predicate."
		);
	}

	@Test
	void testBuildEventLogQuery_onlyLevels_firstIteration_selectsMinimalColumns() {
		// Assumes mapLevelToEventType("Error") -> "1" and ("Warning") -> "2"
		final EventLogSource source = EventLogSource
			.builder()
			.levels(Set.of(EventLogLevel.ERROR, EventLogLevel.WARNING))
			.build();

		final String result = processor.buildEventLogQuery(source, null);

		final String expected = "SELECT RecordNumber FROM Win32_NTLogEvent WHERE (EventType = '1' OR EventType = '2')";

		assertEquals(
			expected,
			result,
			"Query should select minimal columns on first iteration and include only the level predicate mapped to EventType."
		);
	}

	@Test
	void testBuildEventLogQuery_logNameSourcesLevels() {
		final EventLogSource source = EventLogSource
			.builder()
			.logName("System")
			.sources(Set.of("Winlogon"))
			.levels(Set.of(EventLogLevel.INFORMATION))
			.build();

		final String result = processor.buildEventLogQuery(source, 1234);

		final String expected =
			"SELECT RecordNumber, TimeGenerated, TimeWritten, EventCode, EventType, " +
			"EventIdentifier, SourceName, InsertionStrings, Message, LogFile " +
			"FROM Win32_NTLogEvent " +
			"WHERE LogFile = 'System' " +
			"AND (SourceName = 'Winlogon') " +
			"AND (EventType = '3') " +
			"AND RecordNumber > 1234";

		assertEquals(
			expected,
			result,
			"Query should select full columns when cursor is set (stopMark/stopTime) and include cursor predicates + filters."
		);
	}

	@Test
	void testEscapeWqlString_escapesSingleQuotes() {
		assertEquals(
			"Service Control Manager''s",
			EventLogSourceProcessor.escapeWqlString("Service Control Manager's"),
			"escapeWqlString() must escape single quotes by doubling them."
		);
		assertEquals(
			"",
			EventLogSourceProcessor.escapeWqlString(null),
			"escapeWqlString(null) must return an empty string."
		);
	}

	@Test
	void testJoinWithOrString_trimsDistinctAndSorts() {
		final String predicate = EventLogSourceProcessor.joinWithOrString(
			"EventCode",
			new java.util.LinkedHashSet<>(List.of(" 6009 ", "6005", " 6005 ", ""))
		);

		// Order must be deterministic (sorted) to avoid flaky query assertions.
		assertEquals(
			"(EventCode = '6005' OR EventCode = '6009')",
			predicate,
			"joinWithOrString() must trim, drop blanks, de-duplicate, and sort values for deterministic output."
		);
	}

	@Test
	void testResolveMarker_emptyResultsKeepsCursorWhenSet() {
		final EventLogSource source = EventLogSource.builder().maxEventsPerPoll(3).build();

		assertEquals(
			0,
			processor.resolveMarker(List.of(), source, null),
			"resolveMarker() must return 0 when there are no results and no existing cursor."
		);
		assertEquals(
			12,
			processor.resolveMarker(List.of(), source, 12),
			"resolveMarker() must keep the existing cursor unchanged when there are no results."
		);
	}

	@Test
	void testPostProcessEventLogs_doesNotThrowOnImmutableRows_andReplacesLevelAlias_andAppliesLimit() {
		// cursor != null => replace event type numeric with alias + apply limit
		final EventLogSource source = EventLogSource.builder().maxEventsPerPoll(2).build();

		// Use List.of(...) rows (immutable) to ensure post-processing does not rely on row mutability.
		final List<List<String>> requestResult = List.of(
			List.of("8", "1767087780", "1767087780", "6005", "3", "2147489653", "EventLog", "", "msg", "System"),
			List.of("2", "1767087755", "1767087755", "6005", "1", "2147489653", "EventLog", "", "msg", "System"),
			List.of("5", "1767087770", "1767087770", "6005", "2", "2147489653", "EventLog", "", "msg", "System")
		);

		final PostProcessingResult output = assertDoesNotThrow(
			() -> processor.postProcessEventLogs(requestResult, source, 10),
			"postProcessEventLogs() must not rely on row mutability (List.of(...) rows are immutable)."
		);

		// Sorted by RecordNumber ascending, then limited to 2 rows
		assertEquals(2, output.getResults().size(), "Results must be limited to maxEventsPerPoll=2 when cursor is set.");
		assertEquals("2", output.getResults().get(0).get(0), "Results must be sorted ascending by RecordNumber.");
		assertEquals("5", output.getResults().get(1).get(0), "Results must be sorted ascending by RecordNumber.");

		// EventType column replaced with user-friendly alias
		assertEquals("Error", output.getResults().get(0).get(4), "EventType code 1 must be mapped to alias 'Error'.");
		assertEquals("Warning", output.getResults().get(1).get(4), "EventType code 2 must be mapped to alias 'Warning'.");

		// Cursor should be set to the last kept record number (maxEventsPerPoll=2 => record number 5)
		assertEquals(5, output.getCursor(), "Cursor must advance to the last kept record number when limiting.");
	}

	@Test
	void testPostProcessEventLogs_encodesInsertionStringsAndMessageToBase64_whenNotFirstPoll() {
		// cursor != null => level alias replacement + Base64 encoding of InsertionStrings and Message
		final EventLogSource source = EventLogSource.builder().maxEventsPerPoll(10).build();

		final String insertionStrings = "S-1-5-21-1098790905-104752506-1616813648-500|Administrator|EC-WIN-01|0xe2ba137|3|";
		final String message =
			"An account was logged off.\n\nSubject:\n\tSecurity ID:\t\tS-1-5-21-1098790905\n\tAccount Name:\t\tAdministrator";
		final String expectedInsertionStringsBase64 = Base64.getEncoder().encodeToString(insertionStrings.getBytes());
		final String expectedMessageBase64 = Base64.getEncoder().encodeToString(message.getBytes());

		// RecordNumber, TimeGenerated, TimeWritten, EventCode, EventType, EventIdentifier, SourceName,
		// InsertionStrings, Message, LogFile
		final List<List<String>> requestResult = List.of(
			List.of(
				"2",
				"1767087755",
				"1767087755",
				"6005",
				"3",
				"2147489653",
				"EventLog",
				insertionStrings,
				message,
				"System"
			)
		);

		final PostProcessingResult output = processor.postProcessEventLogs(requestResult, source, 10);

		assertEquals(1, output.getResults().size(), "Expected one returned row.");
		final List<String> row = output.getResults().get(0);

		assertEquals(
			expectedInsertionStringsBase64,
			row.get(7),
			"InsertionStrings must be Base64-encoded by postProcessEventLogs()."
		);
		assertEquals(
			insertionStrings,
			new String(Base64.getDecoder().decode(row.get(7))),
			"Encoded InsertionStrings must be decodable back to the original value."
		);

		assertEquals(expectedMessageBase64, row.get(8), "Message must be Base64-encoded by postProcessEventLogs().");
		assertEquals(
			message,
			new String(Base64.getDecoder().decode(row.get(8))),
			"Encoded Message must be decodable back to the original value."
		);
	}

	@Test
	void testPostProcessEventLogs_firstPollReturnsEmptyResultsButInitializesCursor() {
		final EventLogSource source = EventLogSource.builder().maxEventsPerPoll(3).build();

		final List<List<String>> requestResult = List.of(
			List.of("8", "1767087780"),
			List.of("2", "1767087755"),
			List.of("5", "1767087770")
		);

		final PostProcessingResult output = processor.postProcessEventLogs(requestResult, source, null);

		// First poll => no results returned (avoid backlog), but cursor initialized to highest record number.
		assertTrue(
			output.getResults().isEmpty(),
			"On first poll (cursor is null), results must be empty to avoid sending an initial backlog."
		);
		assertEquals(
			8,
			output.getCursor(),
			"On first poll (cursor is null), the cursor must be initialized to the highest RecordNumber."
		);
	}
}
