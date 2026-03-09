package org.metricshub.extension.oscommand.file;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

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

	/**
	 * Test subclass of FileSourceProcessor that overrides the factory method
	 * to inject the mocked RemoteFilesRequestExecutor.
	 */
	private static class TestableFileSourceProcessor extends FileSourceProcessor {

		private final RemoteFilesRequestExecutor mockRequestExecutor;

		TestableFileSourceProcessor(RemoteFilesRequestExecutor mockRequestExecutor) {
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
		final SshConfiguration sshConfiguration = SshConfiguration
			.sshConfigurationBuilder()
			.hostname(HOSTNAME)
			.username(USERNAME)
			.password(PASSWORD.toCharArray())
			.build();
		final HostProperties hostProperties = HostProperties.builder().isLocalhost(false).build();
		final HostConfiguration hostConfiguration = HostConfiguration
			.builder()
			.hostname(HOSTNAME)
			.configurations(Map.of(SshConfiguration.class, sshConfiguration))
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

		// Mock OsCommandService.runSshCommand for path resolution
		try (MockedStatic<OsCommandService> mockedOsCommandService = mockStatic(OsCommandService.class)) {
			mockedOsCommandService
				.when(() ->
					OsCommandService.runSshCommand(
						anyString(),
						eq(HOSTNAME),
						eq(sshConfiguration),
						anyLong(),
						any(),
						anyString(),
						eq(DeviceKind.WINDOWS)
					)
				)
				.thenReturn(resolvedPath);

			// Create testable processor with injected mock
			final FileSourceProcessor processor = new TestableFileSourceProcessor(mockRequestExecutor);

			// Iteration 1: First read
			when(mockRequestExecutor.readRemoteFileOffsetContent(anyString(), eq(null), eq(null))).thenReturn(initialContent);

			SourceTable result1 = processor.process(fileSource, CONNECTOR_ID, telemetryManager);

			// Assertions for iteration 1
			assertNotNull(result1);
			assertFalse(result1.getTable().isEmpty());
			assertEquals(1, result1.getTable().size());
			assertEquals(resolvedPath, result1.getTable().get(0).get(0));
			assertEquals(FileHelper.escapeNewLines(initialContent), result1.getTable().get(0).get(1));

			// Iteration 2: Second read with new content
			when(mockRequestExecutor.readRemoteFileOffsetContent(anyString(), eq(null), eq(null))).thenReturn(newContent);

			SourceTable result2 = processor.process(fileSource, CONNECTOR_ID, telemetryManager);

			// Assertions for iteration 2
			assertNotNull(result2);
			assertFalse(result2.getTable().isEmpty());
			assertEquals(1, result2.getTable().size());
			assertEquals(resolvedPath, result2.getTable().get(0).get(0));
			assertEquals(FileHelper.escapeNewLines(newContent), result2.getTable().get(0).get(1));
		}
	}

	@Test
	void testProcessWithWindowsHostLogMode() throws Exception {
		// Setup configuration for remote Windows host
		final SshConfiguration sshConfiguration = SshConfiguration
			.sshConfigurationBuilder()
			.hostname(HOSTNAME)
			.username(USERNAME)
			.password(PASSWORD.toCharArray())
			.build();
		final HostProperties hostProperties = HostProperties.builder().isLocalhost(false).build();
		final HostConfiguration hostConfiguration = HostConfiguration
			.builder()
			.hostname(HOSTNAME)
			.configurations(Map.of(SshConfiguration.class, sshConfiguration))
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

		// Mock OsCommandService.runSshCommand for path resolution
		try (MockedStatic<OsCommandService> mockedOsCommandService = mockStatic(OsCommandService.class)) {
			mockedOsCommandService
				.when(() ->
					OsCommandService.runSshCommand(
						anyString(),
						eq(HOSTNAME),
						eq(sshConfiguration),
						anyLong(),
						any(),
						anyString(),
						eq(DeviceKind.WINDOWS)
					)
				)
				.thenReturn(resolvedPath);

			// Create testable processor with injected mock
			final FileSourceProcessor processor = new TestableFileSourceProcessor(mockRequestExecutor);

			// Iteration 1: First read - should set cursor but return empty table
			when(mockRequestExecutor.getRemoteFileSize(anyString())).thenReturn(initialFileSize);

			SourceTable result1 = processor.process(fileSource, CONNECTOR_ID, telemetryManager);

			// Assertions for iteration 1
			assertNotNull(result1);
			assertTrue(result1.getTable().isEmpty());

			// Verify cursor was set correctly
			Map<String, Long> cursors = telemetryManager
				.getHostProperties()
				.getConnectorNamespace(CONNECTOR_ID)
				.getFileSourceCursors(SOURCE_KEY);
			assertEquals(initialFileSize, cursors.get(resolvedPath));

			// Iteration 2: Second read with new content
			when(mockRequestExecutor.getRemoteFileSize(anyString())).thenReturn(newFileSize);
			when(mockRequestExecutor.readRemoteFileOffsetContent(eq(resolvedPath), eq(initialFileSize), anyInt()))
				.thenReturn(newContent);

			SourceTable result2 = processor.process(fileSource, CONNECTOR_ID, telemetryManager);

			// Assertions for iteration 2
			assertNotNull(result2);
			assertFalse(result2.getTable().isEmpty());
			assertEquals(1, result2.getTable().size());
			assertEquals(resolvedPath, result2.getTable().get(0).get(0));
			assertEquals(FileHelper.escapeNewLines(newContent), result2.getTable().get(0).get(1));

			// Verify cursor was updated correctly
			assertEquals(newFileSize, cursors.get(resolvedPath));
		}
	}

	@Test
	void testProcessWithLinuxHostFlatMode() throws Exception {
		// Setup configuration for remote Linux host
		final SshConfiguration sshConfiguration = SshConfiguration
			.sshConfigurationBuilder()
			.hostname(HOSTNAME)
			.username(USERNAME)
			.password(PASSWORD.toCharArray())
			.build();
		final HostProperties hostProperties = HostProperties.builder().isLocalhost(false).build();
		final HostConfiguration hostConfiguration = HostConfiguration
			.builder()
			.hostname(HOSTNAME)
			.configurations(Map.of(SshConfiguration.class, sshConfiguration))
			.hostType(DeviceKind.LINUX)
			.build();
		final TelemetryManager telemetryManager = TelemetryManager
			.builder()
			.hostProperties(hostProperties)
			.hostConfiguration(hostConfiguration)
			.build();
		final FileSource fileSource = FileSource
			.builder()
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

		// Mock OsCommandService.runSshCommand for path resolution
		try (MockedStatic<OsCommandService> mockedOsCommandService = mockStatic(OsCommandService.class)) {
			mockedOsCommandService
				.when(() ->
					OsCommandService.runSshCommand(
						anyString(),
						eq(HOSTNAME),
						eq(sshConfiguration),
						anyLong(),
						any(),
						anyString(),
						eq(DeviceKind.LINUX)
					)
				)
				.thenReturn(resolvedPath);

			// Create testable processor with injected mock
			final FileSourceProcessor processor = new TestableFileSourceProcessor(mockRequestExecutor);

			// Iteration 1: First read
			when(mockRequestExecutor.readRemoteFileOffsetContent(anyString(), eq(null), eq(null))).thenReturn(initialContent);

			SourceTable result1 = processor.process(fileSource, CONNECTOR_ID, telemetryManager);

			// Assertions for iteration 1
			assertNotNull(result1);
			assertFalse(result1.getTable().isEmpty());
			assertEquals(1, result1.getTable().size());
			assertEquals(resolvedPath, result1.getTable().get(0).get(0));
			assertEquals(FileHelper.escapeNewLines(initialContent), result1.getTable().get(0).get(1));

			// Iteration 2: Second read with new content
			when(mockRequestExecutor.readRemoteFileOffsetContent(anyString(), eq(null), eq(null))).thenReturn(newContent);

			SourceTable result2 = processor.process(fileSource, CONNECTOR_ID, telemetryManager);

			// Assertions for iteration 2
			assertNotNull(result2);
			assertFalse(result2.getTable().isEmpty());
			assertEquals(1, result2.getTable().size());
			assertEquals(resolvedPath, result2.getTable().get(0).get(0));
			assertEquals(FileHelper.escapeNewLines(newContent), result2.getTable().get(0).get(1));
		}
	}

	@Test
	void testProcessWithLinuxHostLogMode() throws Exception {
		// Setup configuration for remote Linux host
		final SshConfiguration sshConfiguration = SshConfiguration
			.sshConfigurationBuilder()
			.hostname(HOSTNAME)
			.username(USERNAME)
			.password(PASSWORD.toCharArray())
			.timeout(30L)
			.port(22)
			.build();
		final HostProperties hostProperties = HostProperties.builder().isLocalhost(false).build();
		final HostConfiguration hostConfiguration = HostConfiguration
			.builder()
			.hostname(HOSTNAME)
			.configurations(Map.of(SshConfiguration.class, sshConfiguration))
			.hostType(DeviceKind.LINUX)
			.build();
		final TelemetryManager telemetryManager = TelemetryManager
			.builder()
			.hostProperties(hostProperties)
			.hostConfiguration(hostConfiguration)
			.build();
		final FileSource fileSource = FileSource
			.builder()
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

		// Mock OsCommandService.runSshCommand for path resolution
		try (MockedStatic<OsCommandService> mockedOsCommandService = mockStatic(OsCommandService.class)) {
			mockedOsCommandService
				.when(() ->
					OsCommandService.runSshCommand(
						anyString(),
						eq(HOSTNAME),
						eq(sshConfiguration),
						anyLong(),
						any(),
						anyString(),
						eq(DeviceKind.LINUX)
					)
				)
				.thenReturn(resolvedPath);

			// Create testable processor with injected mock
			final FileSourceProcessor processor = new TestableFileSourceProcessor(mockRequestExecutor);

			// Iteration 1: First read - should set cursor but return empty table
			when(mockRequestExecutor.getRemoteFileSize(anyString())).thenReturn(initialFileSize);

			SourceTable result1 = processor.process(fileSource, CONNECTOR_ID, telemetryManager);

			// Assertions for iteration 1
			assertNotNull(result1);
			assertTrue(result1.getTable().isEmpty());

			// Verify cursor was set correctly
			Map<String, Long> cursors = telemetryManager
				.getHostProperties()
				.getConnectorNamespace(CONNECTOR_ID)
				.getFileSourceCursors(SOURCE_KEY);
			assertEquals(initialFileSize, cursors.get(resolvedPath));

			// Iteration 2: Second read with new content
			when(mockRequestExecutor.getRemoteFileSize(anyString())).thenReturn(newFileSize);
			when(mockRequestExecutor.readRemoteFileOffsetContent(eq(resolvedPath), eq(initialFileSize), anyInt()))
				.thenReturn(newContent);

			SourceTable result2 = processor.process(fileSource, CONNECTOR_ID, telemetryManager);

			// Assertions for iteration 2
			assertNotNull(result2);
			assertFalse(result2.getTable().isEmpty());
			assertEquals(1, result2.getTable().size());
			assertEquals(resolvedPath, result2.getTable().get(0).get(0));
			assertEquals(FileHelper.escapeNewLines(newContent), result2.getTable().get(0).get(1));

			// Verify cursor was updated correctly
			assertEquals(newFileSize, cursors.get(resolvedPath));
		}
	}

	@Test
	void testProcessWithLocalhostFlatMode() throws Exception {
		final String resolvedPath = "/opt/metricshub/logs/test.log";
		final String content = "Initial file content\nLine 2";

		final HostProperties hostProperties = HostProperties.builder().isLocalhost(true).build();
		final HostConfiguration hostConfiguration = HostConfiguration
			.builder()
			.hostname(HOSTNAME)
			.hostType(DeviceKind.LINUX)
			.build();
		final TelemetryManager telemetryManager = TelemetryManager
			.builder()
			.hostProperties(hostProperties)
			.hostConfiguration(hostConfiguration)
			.build();
		final FileSource fileSource = FileSource
			.builder()
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

			final FileSourceProcessor processor = new TestableFileSourceProcessorForLocalhost(mockFileOps);

			SourceTable result1 = processor.process(fileSource, CONNECTOR_ID, telemetryManager);
			assertNotNull(result1);
			assertFalse(result1.getTable().isEmpty());
			assertEquals(1, result1.getTable().size());
			assertEquals(resolvedPath, result1.getTable().get(0).get(0));
			assertEquals(FileHelper.escapeNewLines(content), result1.getTable().get(0).get(1));

			SourceTable result2 = processor.process(fileSource, CONNECTOR_ID, telemetryManager);
			assertNotNull(result2);
			assertFalse(result2.getTable().isEmpty());
			assertEquals(1, result2.getTable().size());
			assertEquals(resolvedPath, result2.getTable().get(0).get(0));
			assertEquals(FileHelper.escapeNewLines(content), result2.getTable().get(0).get(1));
		}
	}

	@Test
	void testProcessWithLocalhostLogMode() throws Exception {
		final String resolvedPath = "/opt/metricshub/logs/test.log";
		final long initialFileSize = 45L;
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
			.hostType(DeviceKind.LINUX)
			.build();
		final TelemetryManager telemetryManager = TelemetryManager
			.builder()
			.hostProperties(hostProperties)
			.hostConfiguration(hostConfiguration)
			.build();
		final FileSource fileSource = FileSource
			.builder()
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

			final FileSourceProcessor processor = new TestableFileSourceProcessorForLocalhost(mockFileOps);

			SourceTable result1 = processor.process(fileSource, CONNECTOR_ID, telemetryManager);
			assertNotNull(result1);
			assertTrue(result1.getTable().isEmpty());

			Map<String, Long> cursors = telemetryManager
				.getHostProperties()
				.getConnectorNamespace(CONNECTOR_ID)
				.getFileSourceCursors(SOURCE_KEY);
			assertEquals(initialFileSize, cursors.get(resolvedPath));

			SourceTable result2 = processor.process(fileSource, CONNECTOR_ID, telemetryManager);
			assertNotNull(result2);
			assertFalse(result2.getTable().isEmpty());
			assertEquals(1, result2.getTable().size());
			assertEquals(resolvedPath, result2.getTable().get(0).get(0));
			assertEquals(FileHelper.escapeNewLines(newContent), result2.getTable().get(0).get(1));
			assertEquals(newFileSize, cursors.get(resolvedPath));
		}
	}

	@Test
	void testProcessWithLocalhostLogModeUnlimitedMaxSizePerPoll() throws Exception {
		final String resolvedPath = "/opt/metricshub/logs/test.log";
		final long initialFileSize = 45L;
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
			.hostType(DeviceKind.LINUX)
			.build();
		final TelemetryManager telemetryManager = TelemetryManager
			.builder()
			.hostProperties(hostProperties)
			.hostConfiguration(hostConfiguration)
			.build();
		final FileSource fileSource = FileSource
			.builder()
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

			final FileSourceProcessor processor = new TestableFileSourceProcessorForLocalhost(mockFileOps);

			SourceTable result1 = processor.process(fileSource, CONNECTOR_ID, telemetryManager);
			assertNotNull(result1);
			assertTrue(result1.getTable().isEmpty());

			Map<String, Long> cursors = telemetryManager
				.getHostProperties()
				.getConnectorNamespace(CONNECTOR_ID)
				.getFileSourceCursors(SOURCE_KEY);
			assertEquals(initialFileSize, cursors.get(resolvedPath));

			SourceTable result2 = processor.process(fileSource, CONNECTOR_ID, telemetryManager);
			assertNotNull(result2);
			assertFalse(result2.getTable().isEmpty());
			assertEquals(1, result2.getTable().size());
			assertEquals(resolvedPath, result2.getTable().get(0).get(0));
			assertEquals(FileHelper.escapeNewLines(newContent), result2.getTable().get(0).get(1));
			assertEquals(newFileSize, cursors.get(resolvedPath));
		}
	}
}
