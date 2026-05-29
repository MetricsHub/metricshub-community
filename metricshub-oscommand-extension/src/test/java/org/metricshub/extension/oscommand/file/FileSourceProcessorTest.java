package org.metricshub.extension.oscommand.file;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
import org.metricshub.extension.oscommand.OsCommandService;
import org.metricshub.extension.oscommand.SshConfiguration;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FileSourceProcessorTest {

	private final String LINUX_ABSOLUTE_PATH = "/opt/metricshub/logs/*.log";

	private final String WINDOWS_ABSOLUTE_PATH = "C:\\Program Files\\MetricsHub\\logs\\*.log";

	private final String HOSTNAME = "hostname";

	private final String CONNECTOR_ID = "connectorId";

	private final String USERNAME = "username";

	private final String PASSWORD = "password";

	private final String SOURCE_KEY = "sourceKey";

	@Mock
	private RemoteFilesRequestExecutor mockRequestExecutor;

	private static String expectedMarkedLogCell(final String path, final String rawContent) {
		final StringBuilder logBlock = new StringBuilder();
		FileHelper.appendLogBlock(logBlock, path, FileHelper.escapeSemiColon(rawContent));
		return logBlock.toString();
	}

	/**
	 * Test subclass of FileSourceProcessor that overrides the factory method
	 * to inject the mocked RemoteFilesRequestExecutor.
	 */
	private static class TestableFileSourceProcessor extends FileSourceProcessor {

		private final RemoteFilesRequestExecutor mockRequestExecutor;

		TestableFileSourceProcessor(RemoteFilesRequestExecutor mockRequestExecutor, OsCommandService osCommandService) {
			super(osCommandService);
			this.mockRequestExecutor = mockRequestExecutor;
		}

		@Override
		protected RemoteFilesRequestExecutor createRemoteFilesRequestExecutor(
			final String hostname,
			final SshConfiguration sshConfiguration
		) {
			return mockRequestExecutor;
		}
	}

	/**
	 * Test subclass that overrides createLocalFileOperations to inject a mock.
	 */
	private static class TestableFileSourceProcessorForLocalhost extends FileSourceProcessor {

		private final FileOperations mockLocalFileOperations;

		TestableFileSourceProcessorForLocalhost(FileOperations mockLocalFileOperations) {
			super(new OsCommandService());
			this.mockLocalFileOperations = mockLocalFileOperations;
		}

		@Override
		protected FileOperations createLocalFileOperations(final String hostname) {
			return mockLocalFileOperations;
		}
	}

	@Test
	void testProcessWithWindowsHostFlatMode() throws Exception {
		final OsCommandService osCommandService = mock(OsCommandService.class);

		// Setup configuration for remote Windows host
		final SshConfiguration sshConfiguration = SshConfiguration.sshConfigurationBuilder()
			.hostname(HOSTNAME)
			.username(USERNAME)
			.password(PASSWORD.toCharArray())
			.build();
		final HostProperties hostProperties = HostProperties.builder().isLocalhost(false).build();
		final HostConfiguration hostConfiguration = HostConfiguration.builder()
			.hostname(HOSTNAME)
			.configurations(Map.of(SshConfiguration.class, sshConfiguration))
			.hostType(DeviceKind.WINDOWS)
			.build();
		final TelemetryManager telemetryManager = TelemetryManager.builder()
			.hostProperties(hostProperties)
			.hostConfiguration(hostConfiguration)
			.build();
		final FileSource fileSource = FileSource.builder()
			.maxSizePerPoll(100L * 1024 * 1024)
			.key(SOURCE_KEY)
			.mode(FileSourceProcessingMode.FLAT)
			.paths(Set.of(WINDOWS_ABSOLUTE_PATH))
			.build();

		// Resolved file path (what resolveRemoteFiles would return)
		final String resolvedPath = "C:\\Program Files\\MetricsHub\\logs\\test.log";
		final String initialContent = "Initial file content";
		final String newContent = "Initial file content\nNew content added";

		// Setup mocks for RemoteFilesRequestExecutor
		when(mockRequestExecutor.connectSshClient()).thenReturn(true);
		when(mockRequestExecutor.authenticateSshClient()).thenReturn(true);

		doReturn(resolvedPath)
			.when(osCommandService)
			.runSshCommand(
				anyString(),
				eq(HOSTNAME),
				eq(sshConfiguration),
				anyLong(),
				any(),
				anyString(),
				eq(DeviceKind.WINDOWS)
			);

		// Create testable processor with injected mock
		final FileSourceProcessor processor = new TestableFileSourceProcessor(mockRequestExecutor, osCommandService);

		// Iteration 1: First read
		when(mockRequestExecutor.readRemoteFileOffsetContent(anyString(), eq(null), eq(null))).thenReturn(initialContent);

		SourceTable result1 = processor.process(fileSource, CONNECTOR_ID, telemetryManager);

		// Assertions for iteration 1
		assertNotNull(result1);
		assertEquals(expectedMarkedLogCell(resolvedPath, initialContent), result1.getRawData());

		// Iteration 2: Second read with new content
		when(mockRequestExecutor.readRemoteFileOffsetContent(anyString(), eq(null), eq(null))).thenReturn(newContent);

		SourceTable result2 = processor.process(fileSource, CONNECTOR_ID, telemetryManager);

		// Assertions for iteration 2
		assertNotNull(result2);
		assertEquals(expectedMarkedLogCell(resolvedPath, newContent), result2.getRawData());
	}

	@Test
	void testProcessWithWindowsHostLogMode() throws Exception {
		final OsCommandService osCommandService = mock(OsCommandService.class);

		// Setup configuration for remote Windows host
		final SshConfiguration sshConfiguration = SshConfiguration.sshConfigurationBuilder()
			.hostname(HOSTNAME)
			.username(USERNAME)
			.password(PASSWORD.toCharArray())
			.build();
		final HostProperties hostProperties = HostProperties.builder().isLocalhost(false).build();
		final HostConfiguration hostConfiguration = HostConfiguration.builder()
			.hostname(HOSTNAME)
			.configurations(Map.of(SshConfiguration.class, sshConfiguration))
			.hostType(DeviceKind.WINDOWS)
			.build();
		final TelemetryManager telemetryManager = TelemetryManager.builder()
			.hostProperties(hostProperties)
			.hostConfiguration(hostConfiguration)
			.build();
		final FileSource fileSource = FileSource.builder()
			.maxSizePerPoll(1000L * 1024 * 1024)
			.key(SOURCE_KEY)
			.mode(FileSourceProcessingMode.LOG)
			.paths(Set.of(WINDOWS_ABSOLUTE_PATH))
			.build();

		// Resolved file path
		final String resolvedPath = "C:\\Program Files\\MetricsHub\\logs\\test.log";
		final long initialFileSize = 50L;
		final String newContent = "New content added\n";
		final long newFileSize = initialFileSize + newContent.length();

		// Setup mocks for RemoteFilesRequestExecutor
		when(mockRequestExecutor.connectSshClient()).thenReturn(true);
		when(mockRequestExecutor.authenticateSshClient()).thenReturn(true);

		doReturn(resolvedPath)
			.when(osCommandService)
			.runSshCommand(
				anyString(),
				eq(HOSTNAME),
				eq(sshConfiguration),
				anyLong(),
				any(),
				anyString(),
				eq(DeviceKind.WINDOWS)
			);

		// Create testable processor with injected mock
		final FileSourceProcessor processor = new TestableFileSourceProcessor(mockRequestExecutor, osCommandService);

		// Iteration 1: First read - should set cursor but return empty table
		when(mockRequestExecutor.getRemoteFileSize(anyString())).thenReturn(initialFileSize);

		SourceTable result1 = processor.process(fileSource, CONNECTOR_ID, telemetryManager);

		// Assertions for iteration 1
		assertNotNull(result1);
		assertTrue(result1.isEmpty());

		// Verify cursor was set correctly
		Map<String, Long> cursors = telemetryManager
			.getHostProperties()
			.getConnectorNamespace(CONNECTOR_ID)
			.getFileSourceCursors(SOURCE_KEY);
		assertEquals(initialFileSize, cursors.get(resolvedPath));

		// Iteration 2: Second read with new content
		when(mockRequestExecutor.getRemoteFileSize(anyString())).thenReturn(newFileSize);
		when(mockRequestExecutor.readRemoteFileOffsetContent(eq(resolvedPath), eq(initialFileSize), anyInt())).thenReturn(
			newContent
		);

		SourceTable result2 = processor.process(fileSource, CONNECTOR_ID, telemetryManager);

		// Assertions for iteration 2
		assertNotNull(result2);
		assertEquals(expectedMarkedLogCell(resolvedPath, newContent), result2.getRawData());

		// Verify cursor was updated correctly
		assertEquals(newFileSize, cursors.get(resolvedPath));
	}

	@Test
	void testProcessWithLinuxHostFlatMode() throws Exception {
		final OsCommandService osCommandService = mock(OsCommandService.class);

		// Setup configuration for remote Linux host
		final SshConfiguration sshConfiguration = SshConfiguration.sshConfigurationBuilder()
			.hostname(HOSTNAME)
			.username(USERNAME)
			.password(PASSWORD.toCharArray())
			.build();
		final HostProperties hostProperties = HostProperties.builder().isLocalhost(false).build();
		final HostConfiguration hostConfiguration = HostConfiguration.builder()
			.hostname(HOSTNAME)
			.configurations(Map.of(SshConfiguration.class, sshConfiguration))
			.hostType(DeviceKind.LINUX)
			.build();
		final TelemetryManager telemetryManager = TelemetryManager.builder()
			.hostProperties(hostProperties)
			.hostConfiguration(hostConfiguration)
			.build();
		final FileSource fileSource = FileSource.builder()
			.maxSizePerPoll(100L * 1024 * 1024)
			.key(SOURCE_KEY)
			.mode(FileSourceProcessingMode.FLAT)
			.paths(Set.of(LINUX_ABSOLUTE_PATH))
			.build();

		// Resolved file path (what resolveRemoteFiles would return)
		final String resolvedPath = "/opt/metricshub/logs/test.log";
		final String initialContent = "Initial log content\nLine 2";
		final String newContent = "Initial log content\nLine 2\nNew content added";

		// Setup mocks for RemoteFilesRequestExecutor
		when(mockRequestExecutor.connectSshClient()).thenReturn(true);
		when(mockRequestExecutor.authenticateSshClient()).thenReturn(true);

		doReturn(resolvedPath)
			.when(osCommandService)
			.runSshCommand(
				anyString(),
				eq(HOSTNAME),
				eq(sshConfiguration),
				anyLong(),
				any(),
				anyString(),
				eq(DeviceKind.LINUX)
			);

		// Create testable processor with injected mock
		final FileSourceProcessor processor = new TestableFileSourceProcessor(mockRequestExecutor, osCommandService);

		// Iteration 1: First read
		when(mockRequestExecutor.readRemoteFileOffsetContent(anyString(), eq(null), eq(null))).thenReturn(initialContent);

		SourceTable result1 = processor.process(fileSource, CONNECTOR_ID, telemetryManager);

		// Assertions for iteration 1
		assertNotNull(result1);
		assertEquals(expectedMarkedLogCell(resolvedPath, initialContent), result1.getRawData());

		// Iteration 2: Second read with new content
		when(mockRequestExecutor.readRemoteFileOffsetContent(anyString(), eq(null), eq(null))).thenReturn(newContent);

		SourceTable result2 = processor.process(fileSource, CONNECTOR_ID, telemetryManager);

		// Assertions for iteration 2
		assertNotNull(result2);
		assertEquals(expectedMarkedLogCell(resolvedPath, newContent), result2.getRawData());
	}

	@Test
	void testProcessWithLinuxHostLogMode() throws Exception {
		final OsCommandService osCommandService = mock(OsCommandService.class);

		// Setup configuration for remote Linux host
		final SshConfiguration sshConfiguration = SshConfiguration.sshConfigurationBuilder()
			.hostname(HOSTNAME)
			.username(USERNAME)
			.password(PASSWORD.toCharArray())
			.timeout(30L)
			.port(22)
			.build();
		final HostProperties hostProperties = HostProperties.builder().isLocalhost(false).build();
		final HostConfiguration hostConfiguration = HostConfiguration.builder()
			.hostname(HOSTNAME)
			.configurations(Map.of(SshConfiguration.class, sshConfiguration))
			.hostType(DeviceKind.LINUX)
			.build();
		final TelemetryManager telemetryManager = TelemetryManager.builder()
			.hostProperties(hostProperties)
			.hostConfiguration(hostConfiguration)
			.build();
		final FileSource fileSource = FileSource.builder()
			.maxSizePerPoll(1000L * 1024 * 1024)
			.key(SOURCE_KEY)
			.mode(FileSourceProcessingMode.LOG)
			.paths(Set.of(LINUX_ABSOLUTE_PATH))
			.build();

		// Resolved file path
		final String resolvedPath = "/opt/metricshub/logs/test.log";
		final long initialFileSize = 45L;
		final String newContent = "New log line added\n";
		final long newFileSize = initialFileSize + newContent.length();

		// Setup mocks for RemoteFilesRequestExecutor
		when(mockRequestExecutor.connectSshClient()).thenReturn(true);
		when(mockRequestExecutor.authenticateSshClient()).thenReturn(true);

		doReturn(resolvedPath)
			.when(osCommandService)
			.runSshCommand(
				anyString(),
				eq(HOSTNAME),
				eq(sshConfiguration),
				anyLong(),
				any(),
				anyString(),
				eq(DeviceKind.LINUX)
			);

		// Create testable processor with injected mock
		final FileSourceProcessor processor = new TestableFileSourceProcessor(mockRequestExecutor, osCommandService);

		// Iteration 1: First read - should set cursor but return empty table
		when(mockRequestExecutor.getRemoteFileSize(anyString())).thenReturn(initialFileSize);

		SourceTable result1 = processor.process(fileSource, CONNECTOR_ID, telemetryManager);

		// Assertions for iteration 1
		assertNotNull(result1);
		assertTrue(result1.isEmpty());

		// Verify cursor was set correctly
		Map<String, Long> cursors = telemetryManager
			.getHostProperties()
			.getConnectorNamespace(CONNECTOR_ID)
			.getFileSourceCursors(SOURCE_KEY);
		assertEquals(initialFileSize, cursors.get(resolvedPath));

		// Iteration 2: Second read with new content
		when(mockRequestExecutor.getRemoteFileSize(anyString())).thenReturn(newFileSize);
		when(mockRequestExecutor.readRemoteFileOffsetContent(eq(resolvedPath), eq(initialFileSize), anyInt())).thenReturn(
			newContent
		);

		SourceTable result2 = processor.process(fileSource, CONNECTOR_ID, telemetryManager);

		// Assertions for iteration 2
		assertNotNull(result2);
		assertEquals(expectedMarkedLogCell(resolvedPath, newContent), result2.getRawData());

		// Verify cursor was updated correctly
		assertEquals(newFileSize, cursors.get(resolvedPath));
	}

	@Test
	void testProcessWithLocalhostFlatMode() throws Exception {
		final String resolvedPath = "/opt/metricshub/logs/test.log";
		final String content = "Initial file content\nLine 2";

		final HostProperties hostProperties = HostProperties.builder().isLocalhost(true).build();
		final HostConfiguration hostConfiguration = HostConfiguration.builder()
			.hostname(HOSTNAME)
			.hostType(DeviceKind.LINUX)
			.build();
		final TelemetryManager telemetryManager = TelemetryManager.builder()
			.hostProperties(hostProperties)
			.hostConfiguration(hostConfiguration)
			.build();
		final FileSource fileSource = FileSource.builder()
			.maxSizePerPoll(100L * 1024 * 1024)
			.key(SOURCE_KEY)
			.mode(FileSourceProcessingMode.FLAT)
			.paths(Set.of(LINUX_ABSOLUTE_PATH))
			.build();

		final FileOperations mockFileOps = mock(FileOperations.class);
		when(mockFileOps.readFileContent(anyString())).thenReturn(content);

		try (MockedStatic<FileHelper> mockedFileHelper = mockStatic(FileHelper.class)) {
			mockedFileHelper
				.when(() -> FileHelper.findFilesByPattern(eq(HOSTNAME), any(), eq(DeviceKind.LINUX)))
				.thenReturn(Set.of(resolvedPath));
			mockedFileHelper.when(() -> FileHelper.escapeNewLines(any())).thenCallRealMethod();
			mockedFileHelper.when(() -> FileHelper.buildLogBlock(anyList(), anySet(), anySet())).thenCallRealMethod();
			mockedFileHelper.when(() -> FileHelper.isSinglePathMapping(anySet(), anySet(), anyList())).thenCallRealMethod();
			mockedFileHelper.when(() -> FileHelper.escapeSemiColon(anyString())).thenCallRealMethod();
			mockedFileHelper.when(() -> FileHelper.appendLogBlock(any(), anyString(), anyString())).thenCallRealMethod();

			final FileSourceProcessor processor = new TestableFileSourceProcessorForLocalhost(mockFileOps);

			SourceTable result1 = processor.process(fileSource, CONNECTOR_ID, telemetryManager);
			assertNotNull(result1);
			assertEquals(expectedMarkedLogCell(resolvedPath, content), result1.getRawData());

			SourceTable result2 = processor.process(fileSource, CONNECTOR_ID, telemetryManager);
			assertNotNull(result2);
			assertEquals(expectedMarkedLogCell(resolvedPath, content), result2.getRawData());
		}
	}

	@Test
	void testProcessWithLocalhostLogMode() throws Exception {
		final String resolvedPath = "/opt/metricshub/logs/test.log";
		final long initialFileSize = 45L;
		final String newContent = "New log line added\n";
		final long newFileSize = initialFileSize + newContent.length();

		final HostProperties hostProperties = HostProperties.builder()
			.isLocalhost(true)
			.connectorNamespaces(new HashMap<>(Map.of(CONNECTOR_ID, ConnectorNamespace.builder().build())))
			.build();
		final HostConfiguration hostConfiguration = HostConfiguration.builder()
			.hostname(HOSTNAME)
			.hostType(DeviceKind.LINUX)
			.build();
		final TelemetryManager telemetryManager = TelemetryManager.builder()
			.hostProperties(hostProperties)
			.hostConfiguration(hostConfiguration)
			.build();
		final FileSource fileSource = FileSource.builder()
			.maxSizePerPoll(1000L * 1024 * 1024)
			.key(SOURCE_KEY)
			.mode(FileSourceProcessingMode.LOG)
			.paths(Set.of(LINUX_ABSOLUTE_PATH))
			.build();

		final FileOperations mockFileOps = mock(FileOperations.class);
		when(mockFileOps.getFileSize(anyString())).thenReturn(initialFileSize).thenReturn(newFileSize);
		when(mockFileOps.readFromOffset(eq(resolvedPath), anyLong(), anyInt())).thenReturn(newContent);

		try (MockedStatic<FileHelper> mockedFileHelper = mockStatic(FileHelper.class)) {
			mockedFileHelper
				.when(() -> FileHelper.findFilesByPattern(eq(HOSTNAME), any(), eq(DeviceKind.LINUX)))
				.thenReturn(Set.of(resolvedPath));
			mockedFileHelper.when(() -> FileHelper.escapeNewLines(any())).thenCallRealMethod();
			mockedFileHelper.when(() -> FileHelper.buildLogBlock(anyList(), anySet(), anySet())).thenCallRealMethod();
			mockedFileHelper.when(() -> FileHelper.isSinglePathMapping(anySet(), anySet(), anyList())).thenCallRealMethod();
			mockedFileHelper.when(() -> FileHelper.escapeSemiColon(anyString())).thenCallRealMethod();
			mockedFileHelper.when(() -> FileHelper.appendLogBlock(any(), anyString(), anyString())).thenCallRealMethod();

			final FileSourceProcessor processor = new TestableFileSourceProcessorForLocalhost(mockFileOps);

			SourceTable result1 = processor.process(fileSource, CONNECTOR_ID, telemetryManager);
			assertNotNull(result1);
			assertTrue(result1.isEmpty());

			Map<String, Long> cursors = telemetryManager
				.getHostProperties()
				.getConnectorNamespace(CONNECTOR_ID)
				.getFileSourceCursors(SOURCE_KEY);
			assertEquals(initialFileSize, cursors.get(resolvedPath));

			SourceTable result2 = processor.process(fileSource, CONNECTOR_ID, telemetryManager);
			assertNotNull(result2);
			assertEquals(expectedMarkedLogCell(resolvedPath, newContent), result2.getRawData());
			assertEquals(newFileSize, cursors.get(resolvedPath));
		}
	}

	@Test
	void testProcessWithLocalhostLogModeUnlimitedMaxSizePerPoll() throws Exception {
		final String resolvedPath = "/opt/metricshub/logs/test.log";
		final long initialFileSize = 45L;
		final String newContent = "New log line added\nMore lines when unlimited\n";
		final long newFileSize = initialFileSize + newContent.length();

		final HostProperties hostProperties = HostProperties.builder()
			.isLocalhost(true)
			.connectorNamespaces(new HashMap<>(Map.of(CONNECTOR_ID, ConnectorNamespace.builder().build())))
			.build();
		final HostConfiguration hostConfiguration = HostConfiguration.builder()
			.hostname(HOSTNAME)
			.hostType(DeviceKind.LINUX)
			.build();
		final TelemetryManager telemetryManager = TelemetryManager.builder()
			.hostProperties(hostProperties)
			.hostConfiguration(hostConfiguration)
			.build();
		final FileSource fileSource = FileSource.builder()
			.maxSizePerPoll(FileSource.UNLIMITED_SIZE_PER_POLL)
			.key(SOURCE_KEY)
			.mode(FileSourceProcessingMode.LOG)
			.paths(Set.of(LINUX_ABSOLUTE_PATH))
			.build();

		final FileOperations mockFileOps = mock(FileOperations.class);
		when(mockFileOps.getFileSize(anyString())).thenReturn(initialFileSize).thenReturn(newFileSize);
		when(mockFileOps.readFromOffset(eq(resolvedPath), anyLong(), anyInt())).thenReturn(newContent);

		try (MockedStatic<FileHelper> mockedFileHelper = mockStatic(FileHelper.class)) {
			mockedFileHelper
				.when(() -> FileHelper.findFilesByPattern(eq(HOSTNAME), any(), eq(DeviceKind.LINUX)))
				.thenReturn(Set.of(resolvedPath));
			mockedFileHelper.when(() -> FileHelper.escapeNewLines(any())).thenCallRealMethod();
			mockedFileHelper.when(() -> FileHelper.buildLogBlock(anyList(), anySet(), anySet())).thenCallRealMethod();
			mockedFileHelper.when(() -> FileHelper.isSinglePathMapping(anySet(), anySet(), anyList())).thenCallRealMethod();
			mockedFileHelper.when(() -> FileHelper.escapeSemiColon(anyString())).thenCallRealMethod();
			mockedFileHelper.when(() -> FileHelper.appendLogBlock(any(), anyString(), anyString())).thenCallRealMethod();

			final FileSourceProcessor processor = new TestableFileSourceProcessorForLocalhost(mockFileOps);

			SourceTable result1 = processor.process(fileSource, CONNECTOR_ID, telemetryManager);
			assertNotNull(result1);
			assertTrue(result1.isEmpty());

			Map<String, Long> cursors = telemetryManager
				.getHostProperties()
				.getConnectorNamespace(CONNECTOR_ID)
				.getFileSourceCursors(SOURCE_KEY);
			assertEquals(initialFileSize, cursors.get(resolvedPath));

			SourceTable result2 = processor.process(fileSource, CONNECTOR_ID, telemetryManager);
			assertNotNull(result2);
			assertEquals(expectedMarkedLogCell(resolvedPath, newContent), result2.getRawData());
			assertEquals(newFileSize, cursors.get(resolvedPath));
		}
	}

	@Test
	void testProcessWithLocalhostFlatModeSkipsPathWhenReadThrows() throws Exception {
		final String path1 = "/opt/metricshub/logs/ok.log";
		final String path2 = "/opt/metricshub/logs/ko.log";
		final String content1 = "Content one";

		final HostProperties hostProperties = HostProperties.builder().isLocalhost(true).build();
		final HostConfiguration hostConfiguration = HostConfiguration.builder()
			.hostname(HOSTNAME)
			.hostType(DeviceKind.LINUX)
			.build();
		final TelemetryManager telemetryManager = TelemetryManager.builder()
			.hostProperties(hostProperties)
			.hostConfiguration(hostConfiguration)
			.build();
		final FileSource fileSource = FileSource.builder()
			.maxSizePerPoll(100L * 1024 * 1024)
			.key(SOURCE_KEY)
			.mode(FileSourceProcessingMode.FLAT)
			.paths(Set.of(LINUX_ABSOLUTE_PATH))
			.build();

		final FileOperations mockFileOps = mock(FileOperations.class);
		when(mockFileOps.readFileContent(eq(path1))).thenReturn(content1);
		when(mockFileOps.readFileContent(eq(path2))).thenThrow(new IOException("boom"));

		try (MockedStatic<FileHelper> mockedFileHelper = mockStatic(FileHelper.class)) {
			mockedFileHelper
				.when(() -> FileHelper.findFilesByPattern(eq(HOSTNAME), any(), eq(DeviceKind.LINUX)))
				.thenReturn(Set.of(path1, path2));
			mockedFileHelper.when(() -> FileHelper.escapeNewLines(any())).thenCallRealMethod();
			mockedFileHelper.when(() -> FileHelper.buildLogBlock(anyList(), anySet(), anySet())).thenCallRealMethod();
			mockedFileHelper.when(() -> FileHelper.isSinglePathMapping(anySet(), anySet(), anyList())).thenCallRealMethod();
			mockedFileHelper.when(() -> FileHelper.escapeSemiColon(anyString())).thenCallRealMethod();
			mockedFileHelper.when(() -> FileHelper.appendLogBlock(any(), anyString(), anyString())).thenCallRealMethod();

			final FileSourceProcessor processor = new TestableFileSourceProcessorForLocalhost(mockFileOps);
			final SourceTable result = processor.process(fileSource, CONNECTOR_ID, telemetryManager);

			assertNotNull(result);
			assertEquals(expectedMarkedLogCell(path1, content1), result.getRawData());
		}
	}
}
