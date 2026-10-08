package org.metricshub.extension.oscommand.file;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
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
import org.metricshub.extension.oscommand.SshConfiguration;
import org.metricshub.ssh.SshClient;
import org.metricshub.ssh.SshClient.FileEntry;
import org.mockito.ArgumentCaptor;
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

	// Lenient: patterns resolve in Set order, and directories that are not stubbed must simply list nothing
	@Mock(strictness = Mock.Strictness.LENIENT)
	private SshClient sshClient;

	private static String expectedMarkedLogCell(final String path, final String rawContent) {
		final StringBuilder logBlock = new StringBuilder();
		FileHelper.appendLogBlock(logBlock, path, FileHelper.escapeSemiColon(rawContent));
		return logBlock.toString();
	}

	private SshConfiguration sshConfiguration() {
		return SshConfiguration.sshConfigurationBuilder()
			.hostname(HOSTNAME)
			.username(USERNAME)
			.password(PASSWORD.toCharArray())
			.build();
	}

	private TelemetryManager remoteTelemetryManager(final DeviceKind deviceKind) {
		return TelemetryManager.builder()
			.hostProperties(HostProperties.builder().isLocalhost(false).build())
			.hostConfiguration(
				HostConfiguration.builder()
					.hostname(HOSTNAME)
					.configurations(Map.of(SshConfiguration.class, sshConfiguration()))
					.hostType(deviceKind)
					.build()
			)
			.build();
	}

	private RemoteFilesRequestExecutor remoteFileOperations(final DeviceKind deviceKind) {
		return new RemoteFilesRequestExecutor(sshClient, sshConfiguration(), deviceKind);
	}

	private Set<String> resolve(final DeviceKind deviceKind, final String... paths) {
		return new FileSourceProcessor().resolveRemoteFiles(
			HOSTNAME,
			Set.of(paths),
			deviceKind,
			remoteFileOperations(deviceKind)
		);
	}

	private static FileEntry entry(final String path) {
		return new FileEntry(path, 1, 1);
	}

	/**
	 * @return the mask the files of {@code directory} were listed with
	 */
	private String fileMask(final String directory) throws IOException {
		final ArgumentCaptor<String> mask = ArgumentCaptor.forClass(String.class);
		verify(sshClient).listFiles(eq(directory), mask.capture(), eq(false));
		return mask.getValue();
	}

	/**
	 * @return the mask the subdirectories of {@code directory} were listed with
	 */
	private String directoryMask(final String directory) throws IOException {
		final ArgumentCaptor<String> mask = ArgumentCaptor.forClass(String.class);
		verify(sshClient).listSubdirectories(eq(directory), mask.capture());
		return mask.getValue();
	}

	/**
	 * Whether ssh-java lists a name with a mask: it matches names case-insensitively with {@code Matcher.find()}.
	 */
	private static boolean listed(final String mask, final String name) {
		return Pattern.compile(mask, Pattern.CASE_INSENSITIVE).matcher(name).find();
	}

	private static void assertListed(final String mask, final String... names) {
		for (final String name : names) {
			assertTrue(listed(mask, name), () -> mask + " should match " + name);
		}
	}

	private static void assertNotListed(final String mask, final String... names) {
		for (final String name : names) {
			assertFalse(listed(mask, name), () -> mask + " should not match " + name);
		}
	}

	private void verifyNoCommandRun() throws IOException {
		verify(sshClient, never()).executeCommand(anyString());
		verify(sshClient, never()).executeCommand(anyString(), anyInt());
	}

	@Test
	void resolveRemoteFiles_listsTheRootWithAnAnchoredCaseSensitiveFileNameMask() throws Exception {
		when(sshClient.listFiles(eq("/opt/metricshub/logs"), anyString(), eq(false))).thenReturn(
			List.of(entry("/opt/metricshub/logs/a.log"), entry("/opt/metricshub/logs/my app;1.log"))
		);

		assertEquals(
			Set.of("/opt/metricshub/logs/a.log", "/opt/metricshub/logs/my app;1.log"),
			resolve(DeviceKind.LINUX, LINUX_ABSOLUTE_PATH)
		);

		// Like find -name: case-sensitive, and a wildcard matches dot-prefixed names
		final String mask = fileMask("/opt/metricshub/logs");
		assertListed(mask, "a.log", ".hidden.log", "my app;1.log", ".log", "multi\nline.log");
		assertNotListed(mask, "a.LOG", "a.log.1", "a.txt", "log", "a.log\n");
		verify(sshClient, never()).listSubdirectories(anyString(), anyString());
		verifyNoCommandRun();
	}

	@Test
	void resolveRemoteFiles_literalFileNameAndWholeDirectory() throws Exception {
		resolve(DeviceKind.LINUX, "/opt/metricshub/logs/app.log", "/var/log/", "/var/tmp/*");

		final String literal = fileMask("/opt/metricshub/logs");
		assertListed(literal, "app.log");
		assertNotListed(literal, "APP.LOG", "xapp.log", "app.logx", "app_log");
		assertListed(fileMask("/var/log"), "messages", ".hidden", "a;b");
		assertListed(fileMask("/var/tmp"), "x");
		verify(sshClient, never()).listSubdirectories(anyString(), anyString());
	}

	@Test
	void resolveRemoteFiles_walksWildcardDirectorySegments() throws Exception {
		when(sshClient.listSubdirectories(eq("/opt/autosys"), anyString())).thenReturn(
			List.of("/opt/autosys/autouser01", "/opt/autosys/autouser02", "/opt/autosys/autouser03")
		);
		when(sshClient.listFiles(eq("/opt/autosys/autouser01/out"), anyString(), eq(false))).thenReturn(
			List.of(entry("/opt/autosys/autouser01/out/event_demon.PE2"))
		);
		when(sshClient.listFiles(eq("/opt/autosys/autouser02/out"), anyString(), eq(false))).thenReturn(
			List.of(entry("/opt/autosys/autouser02/out/event_demon_XPE2"))
		);
		// A matched directory without "out", or one that cannot be read, is skipped
		when(sshClient.listFiles(eq("/opt/autosys/autouser03/out"), anyString(), eq(false))).thenThrow(
			new IOException("No such file")
		);

		assertEquals(
			Set.of("/opt/autosys/autouser01/out/event_demon.PE2", "/opt/autosys/autouser02/out/event_demon_XPE2"),
			resolve(DeviceKind.LINUX, "/opt/autosys/autouser*/out/event_demon*PE2")
		);

		final String directories = directoryMask("/opt/autosys");
		assertListed(directories, "autouser01", "autouser");
		assertNotListed(directories, "Autouser01", "xautouser01", ".autouser01");
		final String files = fileMask("/opt/autosys/autouser01/out");
		assertListed(files, "event_demon.PE2", "event_demonPE2");
		assertNotListed(files, "event_demon.pe2", "event_demon.PE2.old");
		verifyNoCommandRun();
	}

	@Test
	void resolveRemoteFiles_walksTheRootAndConsecutiveWildcardSegments() throws Exception {
		when(sshClient.listSubdirectories(eq("/"), anyString())).thenReturn(List.of("/opt", "/opt2"));
		when(sshClient.listSubdirectories(eq("/opt"), anyString())).thenReturn(List.of("/opt/node1"));
		when(sshClient.listSubdirectories(eq("/opt2"), anyString())).thenReturn(List.of());
		when(sshClient.listFiles(eq("/opt/node1"), anyString(), eq(false))).thenReturn(List.of(entry("/opt/node1/x.log")));

		assertEquals(Set.of("/opt/node1/x.log"), resolve(DeviceKind.LINUX, "/opt*/node?/"));

		assertListed(directoryMask("/"), "opt", "opt2");
		final String node = directoryMask("/opt");
		assertListed(node, "node1");
		assertNotListed(node, "node12", "node");
		assertListed(fileMask("/opt/node1"), "x.log", ".x");
	}

	@Test
	void resolveRemoteFiles_wildcardDirectorySegmentsSkipHiddenDirectoriesLikeAShellGlob() throws Exception {
		resolve(DeviceKind.LINUX, "/opt/*/app.log", "/srv/.node*/app.log", "/data/?ode/app.log");

		final String any = directoryMask("/opt");
		assertListed(any, "node", "a.b");
		assertNotListed(any, ".git", ".", "..");
		final String dotted = directoryMask("/srv");
		assertListed(dotted, ".node1", ".node");
		assertNotListed(dotted, "node1");
		final String question = directoryMask("/data");
		assertListed(question, "node");
		assertNotListed(question, ".ode");
	}

	@Test
	void resolveRemoteFiles_takesSpecialCharactersLiterally() throws Exception {
		when(sshClient.listSubdirectories(eq("/opt/my app/it's/$x/\"q\""), anyString())).thenReturn(
			List.of("/opt/my app/it's/$x/\"q\"/node1")
		);
		when(sshClient.listSubdirectories(eq("/apps"), anyString())).thenReturn(List.of("/apps/node1"));
		when(sshClient.listFiles(eq("/apps/node1/[prod]"), anyString(), eq(false))).thenReturn(
			List.of(entry("/apps/node1/[prod]/app.log"))
		);

		assertEquals(
			Set.of("/apps/node1/[prod]/app.log"),
			resolve(
				DeviceKind.LINUX,
				"/opt/my app/it's/$x/\"q\"/node*/app?.log",
				"/apps/node*/[prod]/app.log",
				"/opt/logs/app[1].log",
				"/opt/logs2/a\\b*.log",
				"/opt/$x/`q`/$(touch pwned)*.log"
			)
		);

		final String question = fileMask("/opt/my app/it's/$x/\"q\"/node1");
		assertListed(question, "app1.log", "app$.log");
		assertNotListed(question, "app12.log", "app.log");
		assertListed(fileMask("/apps/node1/[prod]"), "app.log");
		final String brackets = fileMask("/opt/logs");
		assertListed(brackets, "app[1].log");
		assertNotListed(brackets, "app1.log");
		final String backslash = fileMask("/opt/logs2");
		assertListed(backslash, "a\\b.log", "a\\bc.log");
		assertNotListed(backslash, "ab.log");
		final String substitution = fileMask("/opt/$x/`q`");
		assertListed(substitution, "$(touch pwned).log", "$(touch pwned)1.log");
		assertNotListed(substitution, "pwned.log");
		verifyNoCommandRun();
	}

	@Test
	void resolveRemoteFiles_windowsPathsGoToTheSftpSubsystemAsSlashDrivePaths() throws Exception {
		when(sshClient.listFiles(eq("/C:/Program Files/MetricsHub/logs"), anyString(), eq(false))).thenReturn(
			List.of(entry("/C:/Program Files/MetricsHub/logs/test.log"))
		);
		when(sshClient.listSubdirectories(eq("/D:/Autosys_waae"), anyString())).thenReturn(
			List.of("/D:/Autosys_waae/AutoUser1")
		);
		when(sshClient.listFiles(eq("/D:/Autosys_waae/AutoUser1/out"), anyString(), eq(false))).thenReturn(
			List.of(entry("/D:/Autosys_waae/AutoUser1/out/event_demon_PE2"))
		);
		// A UNC path keeps its double slash: ///server/share is rejected by the Windows OpenSSH SFTP subsystem
		when(sshClient.listFiles(eq("//server/share/logs"), anyString(), eq(false))).thenReturn(
			List.of(entry("//server/share/logs/unc.log"))
		);

		assertEquals(
			Set.of(
				"C:\\Program Files\\MetricsHub\\logs\\test.log",
				"D:\\Autosys_waae\\AutoUser1\\out\\event_demon_PE2",
				"\\\\server\\share\\logs\\unc.log"
			),
			resolve(
				DeviceKind.WINDOWS,
				WINDOWS_ABSOLUTE_PATH,
				"D:\\Autosys_waae\\autouser*\\out\\event_demon*PE2",
				"C:\\*.log",
				"E:\\data\\*\\x.log",
				"\\\\server\\share\\logs\\*.log"
			)
		);

		// Windows names ignore case, and a wildcard matches dot-prefixed names
		assertListed(fileMask("/C:/Program Files/MetricsHub/logs"), "test.log", "TEST.LOG", ".hidden.log");
		assertListed(directoryMask("/D:/Autosys_waae"), "AutoUser1", "autouser");
		assertListed(directoryMask("/E:/data"), ".git", "node");
		assertListed(fileMask("/C:/"), "setup.log");
		verifyNoCommandRun();
	}

	@Test
	void resolveRemoteFiles_skipsInvalidPatternsAndRootsThatCannotBeListed() throws Exception {
		when(sshClient.listFiles(eq("/missing"), anyString(), eq(false))).thenThrow(new IOException("No such file"));
		when(sshClient.listFiles(eq("/opt"), anyString(), eq(false))).thenReturn(List.of(entry("/opt/a.log")));

		assertEquals(Set.of("/opt/a.log"), resolve(DeviceKind.LINUX, "/missing/*.log", "relative/path.log", "/opt/*.log"));
		verify(sshClient, times(2)).listFiles(anyString(), anyString(), eq(false));
	}

	@Test
	void remoteFileOperations_translateWindowsPathsAndReuseListedSizes() throws Exception {
		final RemoteFilesRequestExecutor operations = remoteFileOperations(DeviceKind.WINDOWS);
		when(sshClient.listFiles(eq("/C:/logs"), anyString(), eq(false))).thenReturn(
			List.of(new FileEntry("/C:/logs/listed.log", 42, 1))
		);
		operations.resolve(FileHelper.parsePathPattern("C:\\logs\\*.log", DeviceKind.WINDOWS));

		// The listing's size spares a stat; another file is stat'ed with its SFTP path
		assertEquals(42L, operations.getFileSize("C:\\logs\\listed.log"));
		when(sshClient.fileSize("/C:/logs/other.log")).thenReturn(7L);
		assertEquals(7L, operations.getFileSize("C:\\logs\\other.log"));
		when(sshClient.fileSize("/C:/logs/missing.log")).thenThrow(new IOException("No such file"));
		assertNull(operations.getFileSize("C:\\logs\\missing.log"));

		when(sshClient.readFile("/C:/logs/listed.log", 10L, 5)).thenReturn("range");
		assertEquals("range", operations.readFromOffset("C:\\logs\\listed.log", 10L, 5));
		when(sshClient.readFile("/C:/logs/listed.log", null, null)).thenReturn("whole");
		assertEquals("whole", operations.readFileContent("C:\\logs\\listed.log"));

		operations.close();
		verify(sshClient).close();
	}

	/**
	 * Test subclass of FileSourceProcessor that overrides the factory method
	 * to read through the mocked SshClient.
	 */
	private static class TestableFileSourceProcessor extends FileSourceProcessor {

		private final SshClient sshClient;

		TestableFileSourceProcessor(final SshClient sshClient) {
			this.sshClient = sshClient;
		}

		@Override
		protected RemoteFilesRequestExecutor createRemoteFilesRequestExecutor(
			final String hostname,
			final SshConfiguration sshConfiguration,
			final DeviceKind deviceKind
		) {
			return new RemoteFilesRequestExecutor(sshClient, sshConfiguration, deviceKind);
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

	private void authenticate() throws IOException {
		when(sshClient.authenticate(eq(USERNAME), any(char[].class))).thenReturn(true);
	}

	@Test
	void testProcessWithWindowsHostFlatMode() throws Exception {
		final TelemetryManager telemetryManager = remoteTelemetryManager(DeviceKind.WINDOWS);
		final FileSource fileSource = FileSource.builder()
			.maxSizePerPoll(100L * 1024 * 1024)
			.key(SOURCE_KEY)
			.mode(FileSourceProcessingMode.FLAT)
			.paths(Set.of(WINDOWS_ABSOLUTE_PATH))
			.build();

		final String resolvedPath = "C:\\Program Files\\MetricsHub\\logs\\test.log";
		final String sftpPath = "/C:/Program Files/MetricsHub/logs/test.log";
		final String initialContent = "Initial file content";
		final String newContent = "Initial file content\nNew content added";

		authenticate();
		when(sshClient.listFiles(eq("/C:/Program Files/MetricsHub/logs"), anyString(), eq(false))).thenReturn(
			List.of(entry(sftpPath))
		);
		when(sshClient.readFile(sftpPath, null, null)).thenReturn(initialContent, newContent);

		final FileSourceProcessor processor = new TestableFileSourceProcessor(sshClient);

		// Iteration 1: First read
		final SourceTable result1 = processor.process(fileSource, CONNECTOR_ID, telemetryManager);
		assertNotNull(result1);
		assertEquals(expectedMarkedLogCell(resolvedPath, initialContent), result1.getRawData());

		// Iteration 2: Second read with new content
		final SourceTable result2 = processor.process(fileSource, CONNECTOR_ID, telemetryManager);
		assertNotNull(result2);
		assertEquals(expectedMarkedLogCell(resolvedPath, newContent), result2.getRawData());

		// One SSH connection per poll, closed at its end, and no command run on the host
		verify(sshClient, times(2)).close();
		verifyNoCommandRun();
	}

	@Test
	void testProcessWithWindowsHostLogMode() throws Exception {
		final TelemetryManager telemetryManager = remoteTelemetryManager(DeviceKind.WINDOWS);
		final FileSource fileSource = FileSource.builder()
			.maxSizePerPoll(1000L * 1024 * 1024)
			.key(SOURCE_KEY)
			.mode(FileSourceProcessingMode.LOG)
			.paths(Set.of(WINDOWS_ABSOLUTE_PATH))
			.build();

		final String resolvedPath = "C:\\Program Files\\MetricsHub\\logs\\test.log";
		final String sftpPath = "/C:/Program Files/MetricsHub/logs/test.log";
		final long initialFileSize = 50L;
		final String newContent = "New content added\n";
		final long newFileSize = initialFileSize + newContent.length();

		authenticate();
		when(sshClient.listFiles(eq("/C:/Program Files/MetricsHub/logs"), anyString(), eq(false))).thenReturn(
			List.of(new FileEntry(sftpPath, initialFileSize, 1)),
			List.of(new FileEntry(sftpPath, newFileSize, 2))
		);
		when(sshClient.readFile(sftpPath, initialFileSize, newContent.length())).thenReturn(newContent);

		final FileSourceProcessor processor = new TestableFileSourceProcessor(sshClient);

		// Iteration 1: First read - should set cursor and return an empty log block
		final SourceTable result1 = processor.process(fileSource, CONNECTOR_ID, telemetryManager);
		assertNotNull(result1);
		assertEquals(expectedMarkedLogCell(resolvedPath, ""), result1.getRawData());

		final Map<String, Long> cursors = telemetryManager
			.getHostProperties()
			.getConnectorNamespace(CONNECTOR_ID)
			.getFileSourceCursors(SOURCE_KEY);
		assertEquals(initialFileSize, cursors.get(resolvedPath));

		// Iteration 2: Second read with new content
		final SourceTable result2 = processor.process(fileSource, CONNECTOR_ID, telemetryManager);
		assertNotNull(result2);
		assertEquals(expectedMarkedLogCell(resolvedPath, newContent), result2.getRawData());
		assertEquals(newFileSize, cursors.get(resolvedPath));

		// The sizes come from the listings: no stat round trip
		verify(sshClient, never()).fileSize(anyString());
		verifyNoCommandRun();
	}

	@Test
	void testProcessWithLinuxHostFlatMode() throws Exception {
		final TelemetryManager telemetryManager = remoteTelemetryManager(DeviceKind.LINUX);
		final FileSource fileSource = FileSource.builder()
			.maxSizePerPoll(100L * 1024 * 1024)
			.key(SOURCE_KEY)
			.mode(FileSourceProcessingMode.FLAT)
			.paths(Set.of(LINUX_ABSOLUTE_PATH))
			.build();

		final String resolvedPath = "/opt/metricshub/logs/test.log";
		final String initialContent = "Initial log content\nLine 2";
		final String newContent = "Initial log content\nLine 2\nNew content added";

		authenticate();
		when(sshClient.listFiles(eq("/opt/metricshub/logs"), anyString(), eq(false))).thenReturn(
			List.of(entry(resolvedPath))
		);
		when(sshClient.readFile(resolvedPath, null, null)).thenReturn(initialContent, newContent);

		final FileSourceProcessor processor = new TestableFileSourceProcessor(sshClient);

		// Iteration 1: First read
		final SourceTable result1 = processor.process(fileSource, CONNECTOR_ID, telemetryManager);
		assertNotNull(result1);
		assertEquals(expectedMarkedLogCell(resolvedPath, initialContent), result1.getRawData());

		// Iteration 2: Second read with new content
		final SourceTable result2 = processor.process(fileSource, CONNECTOR_ID, telemetryManager);
		assertNotNull(result2);
		assertEquals(expectedMarkedLogCell(resolvedPath, newContent), result2.getRawData());

		verify(sshClient, times(2)).close();
		verifyNoCommandRun();
	}

	@Test
	void testProcessWithLinuxHostLogMode() throws Exception {
		final TelemetryManager telemetryManager = remoteTelemetryManager(DeviceKind.LINUX);
		final FileSource fileSource = FileSource.builder()
			.maxSizePerPoll(1000L * 1024 * 1024)
			.key(SOURCE_KEY)
			.mode(FileSourceProcessingMode.LOG)
			.paths(Set.of(LINUX_ABSOLUTE_PATH))
			.build();

		final String resolvedPath = "/opt/metricshub/logs/test.log";
		final long initialFileSize = 45L;
		final String newContent = "New log line added\n";
		final long newFileSize = initialFileSize + newContent.length();

		authenticate();
		when(sshClient.listFiles(eq("/opt/metricshub/logs"), anyString(), eq(false))).thenReturn(
			List.of(new FileEntry(resolvedPath, initialFileSize, 1)),
			List.of(new FileEntry(resolvedPath, newFileSize, 2))
		);
		when(sshClient.readFile(resolvedPath, initialFileSize, newContent.length())).thenReturn(newContent);

		final FileSourceProcessor processor = new TestableFileSourceProcessor(sshClient);

		// Iteration 1: First read - should set cursor and return an empty log block
		final SourceTable result1 = processor.process(fileSource, CONNECTOR_ID, telemetryManager);
		assertNotNull(result1);
		assertEquals(expectedMarkedLogCell(resolvedPath, ""), result1.getRawData());

		final Map<String, Long> cursors = telemetryManager
			.getHostProperties()
			.getConnectorNamespace(CONNECTOR_ID)
			.getFileSourceCursors(SOURCE_KEY);
		assertEquals(initialFileSize, cursors.get(resolvedPath));

		// Iteration 2: Second read with new content
		final SourceTable result2 = processor.process(fileSource, CONNECTOR_ID, telemetryManager);
		assertNotNull(result2);
		assertEquals(expectedMarkedLogCell(resolvedPath, newContent), result2.getRawData());
		assertEquals(newFileSize, cursors.get(resolvedPath));

		verify(sshClient, never()).fileSize(anyString());
		verifyNoCommandRun();
	}

	@Test
	void testProcessReturnsAnEmptyTableWhenAuthenticationFails() throws Exception {
		final FileSource fileSource = FileSource.builder()
			.key(SOURCE_KEY)
			.mode(FileSourceProcessingMode.FLAT)
			.paths(Set.of(LINUX_ABSOLUTE_PATH))
			.build();

		final SourceTable result = new TestableFileSourceProcessor(sshClient).process(
			fileSource,
			CONNECTOR_ID,
			remoteTelemetryManager(DeviceKind.LINUX)
		);

		assertEquals(SourceTable.empty(), result);
		verify(sshClient).close();
		verify(sshClient, never()).listFiles(anyString(), any(), eq(false));
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
			assertEquals(expectedMarkedLogCell(resolvedPath, ""), result1.getRawData());

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
			assertEquals(expectedMarkedLogCell(resolvedPath, ""), result1.getRawData());

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

	@Test
	void testLogModeEmitsEmptyBlocksForInitialAndUnchangedFiles() throws Exception {
		final FileOperations fileOps = mock(FileOperations.class);
		final String path1 = "/logs/a.log";
		final String path2 = "/logs/b.log";
		final Set<String> paths = new java.util.LinkedHashSet<>(java.util.List.of(path1, path2));
		final Map<String, Long> cursors = new HashMap<>();
		final FileSource source = FileSource.builder().maxSizePerPoll(100L).build();
		final FileSourceProcessor processor = new FileSourceProcessor();
		when(fileOps.getFileSize(path1)).thenReturn(10L, 10L, 14L);
		when(fileOps.getFileSize(path2)).thenReturn(10L);
		when(fileOps.readFromOffset(path1, 10L, 4)).thenReturn("line");
		final String emptyBlocks =
			"<<<LOG:file=\"/logs/a.log\">>>\n<<<END_LOG>>>\n\n" + "<<<LOG:file=\"/logs/b.log\">>>\n<<<END_LOG>>>\n\n";

		for (int poll = 0; poll < 2; poll++) {
			final var rows = processor.processFilesInLogMode(fileOps, paths, cursors, source, HOSTNAME);
			assertEquals(emptyBlocks, FileHelper.buildLogBlock(rows, paths, paths));
			assertEquals(Map.of(path1, 10L, path2, 10L), cursors);
		}

		final var rows = processor.processFilesInLogMode(fileOps, paths, cursors, source, HOSTNAME);
		assertEquals(
			"<<<LOG:file=\"/logs/a.log\">>>\nline\n<<<END_LOG>>>\n\n" + "<<<LOG:file=\"/logs/b.log\">>>\n<<<END_LOG>>>\n\n",
			FileHelper.buildLogBlock(rows, paths, paths)
		);
		assertEquals(Map.of(path1, 14L, path2, 10L), cursors);
		verify(fileOps, times(1)).readFromOffset(path1, 10L, 4);
	}
}
