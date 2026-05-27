package org.metricshub.extension.win.source;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.metricshub.engine.common.exception.ClientException;
import org.metricshub.engine.common.helpers.FileHelper;
import org.metricshub.engine.configuration.HostConfiguration;
import org.metricshub.engine.connector.model.common.DeviceKind;
import org.metricshub.engine.connector.model.common.FileOperations;
import org.metricshub.engine.connector.model.monitor.task.source.FileSource;
import org.metricshub.engine.connector.model.monitor.task.source.FileSourceProcessingMode;
import org.metricshub.engine.strategy.source.SourceTable;
import org.metricshub.engine.telemetry.ConnectorNamespace;
import org.metricshub.engine.telemetry.HostProperties;
import org.metricshub.engine.telemetry.TelemetryManager;
import org.metricshub.extension.win.IWinConfiguration;
import org.metricshub.extension.win.IWinRequestExecutor;
import org.metricshub.extension.win.WmiTestConfiguration;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FileSourceProcessorTest {

	private static final String NEW_LINE_ESCAPE = "@{newLine}@";

	private static String escapeNewLines(String value) {
		return value == null
			? null
			: value.replace("\r\n", NEW_LINE_ESCAPE).replace("\n", NEW_LINE_ESCAPE).replace("\r", NEW_LINE_ESCAPE);
	}

	private static String expectedMarkedLogCell(final String path, final String rawContent) {
		final StringBuilder logBlock = new StringBuilder();
		FileHelper.appendLogBlock(logBlock, path, FileHelper.escapeSemiColon(rawContent));
		return logBlock.toString();
	}

	private final String WINDOWS_ABSOLUTE_PATH = "C:\\Program Files\\MetricsHub\\logs\\*.log";

	private final String HOSTNAME = "hostname";

	private final String CONNECTOR_ID = "connectorId";

	@Mock
	private IWinRequestExecutor mockWinRequestExecutor;

	@Mock
	private Function<TelemetryManager, IWinConfiguration> mockConfigurationRetriever;

	/**
	 * Test subclass that overrides createLocalFileOperations to inject a mock.
	 */
	private static class TestableFileSourceProcessorForLocalhost extends FileSourceProcessor {

		private final FileOperations mockLocalFileOperations;

		TestableFileSourceProcessorForLocalhost(
			final IWinRequestExecutor winRequestExecutor,
			final Function<TelemetryManager, IWinConfiguration> configurationRetriever,
			final String connectorId,
			final FileOperations mockLocalFileOperations
		) {
			super(winRequestExecutor, configurationRetriever, connectorId);
			this.mockLocalFileOperations = mockLocalFileOperations;
		}

		@Override
		protected FileOperations createLocalFileOperations(final String hostname) {
			return mockLocalFileOperations;
		}
	}

	@Test
	void testProcessWithWindowsHostFlatMode() throws Exception {
		// Setup configuration for remote Windows host
		final IWinConfiguration wmiConfiguration = WmiTestConfiguration.builder().hostname(HOSTNAME).build();
		final HostProperties hostProperties = HostProperties
			.builder()
			.isLocalhost(false)
			.connectorNamespaces(new HashMap<>(Map.of(CONNECTOR_ID, ConnectorNamespace.builder().build())))
			.build();
		final HostConfiguration hostConfiguration = HostConfiguration
			.builder()
			.hostname(HOSTNAME)
			.configurations(Map.of(IWinConfiguration.class, wmiConfiguration))
			.hostType(DeviceKind.WINDOWS)
			.build();
		final TelemetryManager telemetryManager = TelemetryManager
			.builder()
			.hostProperties(hostProperties)
			.hostConfiguration(hostConfiguration)
			.build();
		final FileSource fileSource = FileSource
			.builder()
			.maxSizePerPoll(100L * 1024 * 1024)
			.key("sourceKey")
			.mode(FileSourceProcessingMode.FLAT)
			.paths(Set.of(WINDOWS_ABSOLUTE_PATH))
			.build();

		// Resolved file path (what resolveRemoteFiles would return)
		final String resolvedPath = "C:\\Program Files\\MetricsHub\\logs\\test.log";
		final String initialContent = "Initial file content";
		final String newContent = "Initial file content\nNew content added";

		// Setup mocks: call order is 1) path resolution, 2) readFileContent (getContent.ps1)
		when(mockConfigurationRetriever.apply(any(TelemetryManager.class))).thenReturn(wmiConfiguration);
		final String initialContentBase64 = Base64.getEncoder().encodeToString(initialContent.getBytes());
		final String newContentBase64 = Base64.getEncoder().encodeToString(newContent.getBytes());
		when(mockWinRequestExecutor.executeWinRemoteCommand(eq(HOSTNAME), eq(wmiConfiguration), anyString(), anyList()))
			.thenReturn(resolvedPath, initialContentBase64);

		final FileSourceProcessor processor = new FileSourceProcessor(
			mockWinRequestExecutor,
			mockConfigurationRetriever,
			CONNECTOR_ID
		);

		// Iteration 1: First read
		SourceTable result1 = processor.process(fileSource, telemetryManager);

		assertNotNull(result1);
		assertEquals(expectedMarkedLogCell(resolvedPath, initialContent), result1.getRawData());

		// Iteration 2: Second read with new content (mock again for next two calls)
		when(mockWinRequestExecutor.executeWinRemoteCommand(eq(HOSTNAME), eq(wmiConfiguration), anyString(), anyList()))
			.thenReturn(resolvedPath, newContentBase64);

		SourceTable result2 = processor.process(fileSource, telemetryManager);

		assertNotNull(result2);
		assertEquals(expectedMarkedLogCell(resolvedPath, newContent), result2.getRawData());
	}

	@Test
	void testProcessWithWindowsHostLogMode() throws Exception {
		// Setup configuration for remote Windows host
		final IWinConfiguration wmiConfiguration = WmiTestConfiguration.builder().hostname(HOSTNAME).build();
		final HostProperties hostProperties = HostProperties
			.builder()
			.isLocalhost(false)
			.connectorNamespaces(new HashMap<>(Map.of(CONNECTOR_ID, ConnectorNamespace.builder().build())))
			.build();
		final HostConfiguration hostConfiguration = HostConfiguration
			.builder()
			.hostname(HOSTNAME)
			.configurations(Map.of(IWinConfiguration.class, wmiConfiguration))
			.hostType(DeviceKind.WINDOWS)
			.build();
		final TelemetryManager telemetryManager = TelemetryManager
			.builder()
			.hostProperties(hostProperties)
			.hostConfiguration(hostConfiguration)
			.build();
		final FileSource fileSource = FileSource
			.builder()
			.maxSizePerPoll(1000L * 1024 * 1024)
			.key("sourceKey")
			.mode(FileSourceProcessingMode.LOG)
			.paths(Set.of(WINDOWS_ABSOLUTE_PATH))
			.build();

		final String resolvedPath = "C:\\Program Files\\MetricsHub\\logs\\test.log";
		final long initialFileSize = 50L;
		final String newContent = "New content added\n";
		final long newFileSize = initialFileSize + newContent.length();
		final String newContentBase64 = Base64.getEncoder().encodeToString(newContent.getBytes());

		when(mockConfigurationRetriever.apply(any(TelemetryManager.class))).thenReturn(wmiConfiguration);
		// Call order: 1) path resolution, 2) getFileSize, 3) path resolution, 4) getFileSize, 5) readFromOffset
		when(mockWinRequestExecutor.executeWinRemoteCommand(eq(HOSTNAME), eq(wmiConfiguration), anyString(), anyList()))
			.thenReturn(
				resolvedPath,
				String.valueOf(initialFileSize),
				resolvedPath,
				String.valueOf(newFileSize),
				newContentBase64
			);

		final FileSourceProcessor processor = new FileSourceProcessor(
			mockWinRequestExecutor,
			mockConfigurationRetriever,
			CONNECTOR_ID
		);

		// Iteration 1: First read - should set cursor but return empty table
		SourceTable result1 = processor.process(fileSource, telemetryManager);

		assertNotNull(result1);
		assertTrue(result1.isEmpty());

		Map<String, Long> cursors = telemetryManager
			.getHostProperties()
			.getConnectorNamespace(CONNECTOR_ID)
			.getFileSourceCursors("sourceKey");
		assertEquals(initialFileSize, cursors.get(resolvedPath));

		// Iteration 2: Second read with new content
		SourceTable result2 = processor.process(fileSource, telemetryManager);

		assertNotNull(result2);
		assertEquals(expectedMarkedLogCell(resolvedPath, newContent), result2.getRawData());
		assertEquals(newFileSize, cursors.get(resolvedPath));
	}

	@Test
	void testProcessWithLocalhostFlatMode() throws Exception {
		final String resolvedPath = "C:\\temp\\test.log";
		final String content = "Initial file content\nLine 2";

		final HostProperties hostProperties = HostProperties
			.builder()
			.isLocalhost(true)
			.connectorNamespaces(new HashMap<>(Map.of(CONNECTOR_ID, ConnectorNamespace.builder().build())))
			.build();
		final HostConfiguration hostConfiguration = HostConfiguration
			.builder()
			.hostname(HOSTNAME)
			.hostType(DeviceKind.WINDOWS)
			.build();
		final TelemetryManager telemetryManager = TelemetryManager
			.builder()
			.hostProperties(hostProperties)
			.hostConfiguration(hostConfiguration)
			.build();
		final FileSource fileSource = FileSource
			.builder()
			.maxSizePerPoll(100L * 1024 * 1024)
			.key("sourceKey")
			.mode(FileSourceProcessingMode.FLAT)
			.paths(Set.of("C:\\temp\\*.log"))
			.build();

		final FileOperations mockFileOps = mock(FileOperations.class);
		when(mockFileOps.readFileContent(anyString())).thenReturn(content);

		try (MockedStatic<FileHelper> mockedFileHelper = mockStatic(FileHelper.class)) {
			mockedFileHelper
				.when(() -> FileHelper.findFilesByPattern(eq(HOSTNAME), any(), eq(DeviceKind.WINDOWS)))
				.thenReturn(Set.of(resolvedPath));
			mockedFileHelper
				.when(() -> FileHelper.escapeNewLines(any()))
				.thenAnswer(inv -> escapeNewLines(inv.getArgument(0)));
			mockedFileHelper.when(() -> FileHelper.buildLogBlock(anyList(), anySet(), anySet())).thenCallRealMethod();
			mockedFileHelper.when(() -> FileHelper.isSinglePathMapping(anySet(), anySet(), anyList())).thenCallRealMethod();
			mockedFileHelper.when(() -> FileHelper.escapeSemiColon(anyString())).thenCallRealMethod();
			mockedFileHelper.when(() -> FileHelper.appendLogBlock(any(), anyString(), anyString())).thenCallRealMethod();

			final FileSourceProcessor processor = new TestableFileSourceProcessorForLocalhost(
				mockWinRequestExecutor,
				mockConfigurationRetriever,
				CONNECTOR_ID,
				mockFileOps
			);

			SourceTable result1 = processor.process(fileSource, telemetryManager);
			assertNotNull(result1);
			assertEquals(expectedMarkedLogCell(resolvedPath, content), result1.getRawData());

			SourceTable result2 = processor.process(fileSource, telemetryManager);
			assertNotNull(result2);
			assertEquals(expectedMarkedLogCell(resolvedPath, content), result2.getRawData());
		}
	}

	@Test
	void testProcessWithLocalhostLogMode() throws Exception {
		final String resolvedPath = "C:\\temp\\test.log";
		final long initialFileSize = 50L;
		final String newContent = "New log line added\n";
		final long newFileSize = initialFileSize + newContent.length();

		final HostProperties hostProperties = HostProperties
			.builder()
			.isLocalhost(true)
			.connectorNamespaces(new HashMap<>(Map.of(CONNECTOR_ID, ConnectorNamespace.builder().build())))
			.build();
		final HostConfiguration hostConfiguration = HostConfiguration
			.builder()
			.hostname(HOSTNAME)
			.hostType(DeviceKind.WINDOWS)
			.build();
		final TelemetryManager telemetryManager = TelemetryManager
			.builder()
			.hostProperties(hostProperties)
			.hostConfiguration(hostConfiguration)
			.build();
		final FileSource fileSource = FileSource
			.builder()
			.maxSizePerPoll(1000L * 1024 * 1024)
			.key("sourceKey")
			.mode(FileSourceProcessingMode.LOG)
			.paths(Set.of("C:\\temp\\*.log"))
			.build();

		final FileOperations mockFileOps = mock(FileOperations.class);
		when(mockFileOps.getFileSize(anyString())).thenReturn(initialFileSize).thenReturn(newFileSize);
		when(mockFileOps.readFromOffset(eq(resolvedPath), eq(initialFileSize), anyInt())).thenReturn(newContent);

		try (MockedStatic<FileHelper> mockedFileHelper = mockStatic(FileHelper.class)) {
			mockedFileHelper
				.when(() -> FileHelper.findFilesByPattern(eq(HOSTNAME), any(), eq(DeviceKind.WINDOWS)))
				.thenReturn(Set.of(resolvedPath));
			mockedFileHelper.when(() -> FileHelper.buildLogBlock(anyList(), anySet(), anySet())).thenCallRealMethod();
			mockedFileHelper.when(() -> FileHelper.isSinglePathMapping(anySet(), anySet(), anyList())).thenCallRealMethod();
			mockedFileHelper.when(() -> FileHelper.escapeSemiColon(anyString())).thenCallRealMethod();
			mockedFileHelper.when(() -> FileHelper.appendLogBlock(any(), anyString(), anyString())).thenCallRealMethod();

			final FileSourceProcessor processor = new TestableFileSourceProcessorForLocalhost(
				mockWinRequestExecutor,
				mockConfigurationRetriever,
				CONNECTOR_ID,
				mockFileOps
			);

			// First Iteration, empty results are returned.
			SourceTable result1 = processor.process(fileSource, telemetryManager);
			assertNotNull(result1);
			assertTrue(result1.isEmpty());

			Map<String, Long> cursors = telemetryManager
				.getHostProperties()
				.getConnectorNamespace(CONNECTOR_ID)
				.getFileSourceCursors("sourceKey");
			assertEquals(initialFileSize, cursors.get(resolvedPath));

			// Second iteration, a log block is returned.
			SourceTable result2 = processor.process(fileSource, telemetryManager);
			assertNotNull(result2);
			assertEquals(expectedMarkedLogCell(resolvedPath, newContent), result2.getRawData());
			assertEquals(newFileSize, cursors.get(resolvedPath));
		}
	}

	@Test
	void testProcessWithLocalhostLogModeUnlimitedMaxSizePerPoll() throws Exception {
		final String resolvedPath = "C:\\temp\\test.log";
		final long initialFileSize = 50L;
		final String newContent = "New log line added\nMore lines when unlimited\n";
		final long newFileSize = initialFileSize + newContent.length();

		final HostProperties hostProperties = HostProperties
			.builder()
			.isLocalhost(true)
			.connectorNamespaces(new HashMap<>(Map.of(CONNECTOR_ID, ConnectorNamespace.builder().build())))
			.build();
		final HostConfiguration hostConfiguration = HostConfiguration
			.builder()
			.hostname(HOSTNAME)
			.hostType(DeviceKind.WINDOWS)
			.build();
		final TelemetryManager telemetryManager = TelemetryManager
			.builder()
			.hostProperties(hostProperties)
			.hostConfiguration(hostConfiguration)
			.build();
		final FileSource fileSource = FileSource
			.builder()
			.maxSizePerPoll(FileSource.UNLIMITED_SIZE_PER_POLL)
			.key("sourceKey")
			.mode(FileSourceProcessingMode.LOG)
			.paths(Set.of("C:\\temp\\*.log"))
			.build();

		final FileOperations mockFileOps = mock(FileOperations.class);
		when(mockFileOps.getFileSize(anyString())).thenReturn(initialFileSize).thenReturn(newFileSize);
		when(mockFileOps.readFromOffset(eq(resolvedPath), eq(initialFileSize), anyInt())).thenReturn(newContent);

		try (MockedStatic<FileHelper> mockedFileHelper = mockStatic(FileHelper.class)) {
			mockedFileHelper
				.when(() -> FileHelper.findFilesByPattern(eq(HOSTNAME), any(), eq(DeviceKind.WINDOWS)))
				.thenReturn(Set.of(resolvedPath));
			mockedFileHelper.when(() -> FileHelper.buildLogBlock(anyList(), anySet(), anySet())).thenCallRealMethod();
			mockedFileHelper.when(() -> FileHelper.isSinglePathMapping(anySet(), anySet(), anyList())).thenCallRealMethod();
			mockedFileHelper.when(() -> FileHelper.escapeSemiColon(anyString())).thenCallRealMethod();
			mockedFileHelper.when(() -> FileHelper.appendLogBlock(any(), anyString(), anyString())).thenCallRealMethod();

			final FileSourceProcessor processor = new TestableFileSourceProcessorForLocalhost(
				mockWinRequestExecutor,
				mockConfigurationRetriever,
				CONNECTOR_ID,
				mockFileOps
			);

			SourceTable result1 = processor.process(fileSource, telemetryManager);
			assertNotNull(result1);
			assertTrue(result1.isEmpty());

			Map<String, Long> cursors = telemetryManager
				.getHostProperties()
				.getConnectorNamespace(CONNECTOR_ID)
				.getFileSourceCursors("sourceKey");
			assertEquals(initialFileSize, cursors.get(resolvedPath));

			SourceTable result2 = processor.process(fileSource, telemetryManager);
			assertNotNull(result2);
			assertEquals(expectedMarkedLogCell(resolvedPath, newContent), result2.getRawData());
			assertEquals(newFileSize, cursors.get(resolvedPath));
		}
	}

	@Test
	void testProcessWithNullFileSource() {
		final HostProperties hostProperties = HostProperties.builder().isLocalhost(true).build();
		final HostConfiguration hostConfiguration = HostConfiguration
			.builder()
			.hostname(HOSTNAME)
			.hostType(DeviceKind.WINDOWS)
			.build();
		final TelemetryManager telemetryManager = TelemetryManager
			.builder()
			.hostProperties(hostProperties)
			.hostConfiguration(hostConfiguration)
			.build();

		final FileSourceProcessor processor = new FileSourceProcessor(
			mockWinRequestExecutor,
			mockConfigurationRetriever,
			CONNECTOR_ID
		);

		SourceTable result = processor.process(null, telemetryManager);

		assertNotNull(result);
		assertTrue(result.isEmpty());
	}

	@Test
	void testProcessWithNoWinConfigurationReturnsEmptyTable() {
		final HostProperties hostProperties = HostProperties
			.builder()
			.isLocalhost(false)
			.connectorNamespaces(new HashMap<>(Map.of(CONNECTOR_ID, ConnectorNamespace.builder().build())))
			.build();
		final HostConfiguration hostConfiguration = HostConfiguration
			.builder()
			.hostname(HOSTNAME)
			.hostType(DeviceKind.WINDOWS)
			.build();
		final TelemetryManager telemetryManager = TelemetryManager
			.builder()
			.hostProperties(hostProperties)
			.hostConfiguration(hostConfiguration)
			.build();
		final FileSource fileSource = FileSource
			.builder()
			.maxSizePerPoll(100L * 1024 * 1024)
			.key("sourceKey")
			.mode(FileSourceProcessingMode.FLAT)
			.paths(Set.of(WINDOWS_ABSOLUTE_PATH))
			.build();

		when(mockConfigurationRetriever.apply(any(TelemetryManager.class))).thenReturn(null);

		final FileSourceProcessor processor = new FileSourceProcessor(
			mockWinRequestExecutor,
			mockConfigurationRetriever,
			CONNECTOR_ID
		);

		SourceTable result = processor.process(fileSource, telemetryManager);

		assertNotNull(result);
		assertTrue(result.isEmpty());
	}

	@Test
	void testProcessWithClientExceptionDuringPathResolutionReturnsEmptyTable() throws Exception {
		final IWinConfiguration wmiConfiguration = WmiTestConfiguration.builder().hostname(HOSTNAME).build();
		final HostProperties hostProperties = HostProperties
			.builder()
			.isLocalhost(false)
			.connectorNamespaces(new HashMap<>(Map.of(CONNECTOR_ID, ConnectorNamespace.builder().build())))
			.build();
		final HostConfiguration hostConfiguration = HostConfiguration
			.builder()
			.hostname(HOSTNAME)
			.configurations(Map.of(IWinConfiguration.class, wmiConfiguration))
			.hostType(DeviceKind.WINDOWS)
			.build();
		final TelemetryManager telemetryManager = TelemetryManager
			.builder()
			.hostProperties(hostProperties)
			.hostConfiguration(hostConfiguration)
			.build();
		final FileSource fileSource = FileSource
			.builder()
			.maxSizePerPoll(100L * 1024 * 1024)
			.key("sourceKey")
			.mode(FileSourceProcessingMode.FLAT)
			.paths(Set.of(WINDOWS_ABSOLUTE_PATH))
			.build();

		when(mockConfigurationRetriever.apply(any(TelemetryManager.class))).thenReturn(wmiConfiguration);
		when(mockWinRequestExecutor.executeWinRemoteCommand(eq(HOSTNAME), eq(wmiConfiguration), anyString(), anyList()))
			.thenThrow(new ClientException("Remote command failed"));

		final FileSourceProcessor processor = new FileSourceProcessor(
			mockWinRequestExecutor,
			mockConfigurationRetriever,
			CONNECTOR_ID
		);

		SourceTable result = processor.process(fileSource, telemetryManager);

		assertNotNull(result);
		assertTrue(result.isEmpty());
	}

	@Test
	void testProcessWithPathResolutionReturnsMultiplePathsFlatMode() throws Exception {
		final IWinConfiguration wmiConfiguration = WmiTestConfiguration.builder().hostname(HOSTNAME).build();
		final HostProperties hostProperties = HostProperties
			.builder()
			.isLocalhost(false)
			.connectorNamespaces(new HashMap<>(Map.of(CONNECTOR_ID, ConnectorNamespace.builder().build())))
			.build();
		final HostConfiguration hostConfiguration = HostConfiguration
			.builder()
			.hostname(HOSTNAME)
			.configurations(Map.of(IWinConfiguration.class, wmiConfiguration))
			.hostType(DeviceKind.WINDOWS)
			.build();
		final TelemetryManager telemetryManager = TelemetryManager
			.builder()
			.hostProperties(hostProperties)
			.hostConfiguration(hostConfiguration)
			.build();
		final FileSource fileSource = FileSource
			.builder()
			.maxSizePerPoll(100L * 1024 * 1024)
			.key("sourceKey")
			.mode(FileSourceProcessingMode.FLAT)
			.paths(Set.of(WINDOWS_ABSOLUTE_PATH))
			.build();

		final String path1 = "C:\\Program Files\\MetricsHub\\logs\\test1.log";
		final String path2 = "C:\\Program Files\\MetricsHub\\logs\\test2.log";
		final String content1 = "Content one";
		final String content2 = "Content two";
		// Path resolution returns two paths (e.g. newline-separated)
		final String pathResolutionOutput = path1 + "\n" + path2;

		when(mockConfigurationRetriever.apply(any(TelemetryManager.class))).thenReturn(wmiConfiguration);
		when(mockWinRequestExecutor.executeWinRemoteCommand(eq(HOSTNAME), eq(wmiConfiguration), anyString(), anyList()))
			.thenReturn(
				pathResolutionOutput,
				Base64.getEncoder().encodeToString(content1.getBytes()),
				Base64.getEncoder().encodeToString(content2.getBytes())
			);

		final FileSourceProcessor processor = new FileSourceProcessor(
			mockWinRequestExecutor,
			mockConfigurationRetriever,
			CONNECTOR_ID
		);

		SourceTable result = processor.process(fileSource, telemetryManager);

		assertNotNull(result);
		final String rawData = result.getRawData();
		assertNotNull(rawData);
		assertTrue(rawData.contains("<<<LOG:file=\"" + path1 + "\">>>"), rawData);
		assertTrue(rawData.contains("<<<LOG:file=\"" + path2 + "\">>>"), rawData);
		assertTrue(rawData.contains(FileHelper.escapeSemiColon(content1)), rawData);
		assertTrue(rawData.contains(FileHelper.escapeSemiColon(content2)), rawData);
	}

	@Test
	void testProcessWithLocalhostFlatModeSkipsPathWhenReadThrows() throws Exception {
		final String path1 = "C:\\temp\\ok.log";
		final String path2 = "C:\\temp\\ko.log";
		final String content1 = "Content one";

		final HostProperties hostProperties = HostProperties
			.builder()
			.isLocalhost(true)
			.connectorNamespaces(new HashMap<>(Map.of(CONNECTOR_ID, ConnectorNamespace.builder().build())))
			.build();
		final HostConfiguration hostConfiguration = HostConfiguration
			.builder()
			.hostname(HOSTNAME)
			.hostType(DeviceKind.WINDOWS)
			.build();
		final TelemetryManager telemetryManager = TelemetryManager
			.builder()
			.hostProperties(hostProperties)
			.hostConfiguration(hostConfiguration)
			.build();
		final FileSource fileSource = FileSource
			.builder()
			.maxSizePerPoll(100L * 1024 * 1024)
			.key("sourceKey")
			.mode(FileSourceProcessingMode.FLAT)
			.paths(Set.of("C:\\temp\\*.log"))
			.build();

		final FileOperations mockFileOps = mock(FileOperations.class);
		when(mockFileOps.readFileContent(eq(path1))).thenReturn(content1);
		when(mockFileOps.readFileContent(eq(path2))).thenThrow(new IOException("boom"));

		try (MockedStatic<FileHelper> mockedFileHelper = mockStatic(FileHelper.class)) {
			mockedFileHelper
				.when(() -> FileHelper.findFilesByPattern(eq(HOSTNAME), any(), eq(DeviceKind.WINDOWS)))
				.thenReturn(Set.of(path1, path2));
			mockedFileHelper.when(() -> FileHelper.buildLogBlock(anyList(), anySet(), anySet())).thenCallRealMethod();
			mockedFileHelper.when(() -> FileHelper.isSinglePathMapping(anySet(), anySet(), anyList())).thenCallRealMethod();
			mockedFileHelper.when(() -> FileHelper.escapeSemiColon(anyString())).thenCallRealMethod();
			mockedFileHelper.when(() -> FileHelper.appendLogBlock(any(), anyString(), anyString())).thenCallRealMethod();
			final FileSourceProcessor processor = new TestableFileSourceProcessorForLocalhost(
				mockWinRequestExecutor,
				mockConfigurationRetriever,
				CONNECTOR_ID,
				mockFileOps
			);

			final SourceTable result = processor.process(fileSource, telemetryManager);
			assertNotNull(result);
			assertEquals(expectedMarkedLogCell(path1, content1), result.getRawData());
		}
	}
}
