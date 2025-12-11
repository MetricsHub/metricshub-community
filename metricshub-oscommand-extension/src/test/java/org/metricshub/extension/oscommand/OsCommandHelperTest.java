package org.metricshub.extension.oscommand;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.metricshub.engine.strategy.utils.OsCommandHelper.TEMP_FILE_CREATOR;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeoutException;
import java.util.function.BiFunction;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.metricshub.engine.common.exception.ControlledSshException;
import org.metricshub.engine.common.exception.NoCredentialProvidedException;
import org.metricshub.engine.common.helpers.StringHelper;
import org.metricshub.engine.configuration.HostConfiguration;
import org.metricshub.engine.connector.model.common.DeviceKind;
import org.metricshub.engine.connector.model.common.EmbeddedFile;
import org.metricshub.engine.strategy.utils.EmbeddedFileHelper;
import org.metricshub.engine.strategy.utils.OsCommandHelper;
import org.metricshub.engine.strategy.utils.OsCommandResult;
import org.metricshub.engine.strategy.utils.SudoInformation;
import org.metricshub.engine.telemetry.SshSemaphoreFactory;
import org.metricshub.engine.telemetry.TelemetryManager;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OsCommandHelperTest {

	private static final Map<Integer, EmbeddedFile> EMPTY_EMBEDDED_FILE_MAP = Collections.emptyMap();
	private static Map<Integer, EmbeddedFile> commandLineEmbeddedFiles;

	/****************************************/

	public static final String USERNAME = "testUser";
	public static final String PASSWORD = "testPassword";
	public static final String EMPTY = "";
	public static final String SINGLE_SPACE = " ";
	public static final String CMD = "cmd";
	public static final String ID = "id";
	public static final String BAT = "bat";
	public static final String ECHO_OS = "ECHO %OS%";
	public static final String ARCCONF_PATH = "/[opt|usr]/StorMan/arcconf";
	public static final String PWD_COMMAND = "pwd";
	public static final String AGENT_REV_RESULT = "Agent Rev:";
	public static final String NAVISECCLI_COMMAND =
		"%{SUDO:naviseccli} naviseccli -User %{USERNAME} -Password %{PASSWORD} -Address %{HOSTNAME} -Scope 1 getagent";
	public static final String ECHO_HELLO_WORLD = "echo Hello World";
	public static final String WINDOWS_NT_HELLO_WORLD = "Windows_NT\nHello World";
	public static final String END_OF_LINE = "\n";
	public static final String END_OF_LINE_IN_BRACKETS = "[\r\n]";
	public static final String TEXT = "text";
	public static final String KEY = "key";
	public static final String SPACE_KEY = SINGLE_SPACE + KEY;
	public static final String SUDO_KEY = "%{SUDO:key} key";
	public static final String SUDO_KEY_RESULT = "sudo key\nsudo key";
	public static final String PAUSE = "PAUSE";
	public static final String NAVISECCLI_CAMEL_CASE = "NaviSecCli";
	public static final String SLEEP_5 = "sleep 5";
	public static final String ECHO_TEST_UPPER_CASE = "ECHO Test";
	public static final String ECHO_TEST_LOWER_CASE = "echo Test";
	public static final String TEST_RESULT = "Test";
	public static final String RAIDCTL_PATH = "/usr/sbin/raidctl";
	public static final String HARD_DRIVE = "Hard drive";

	// Embedded files
	public static final String AWK_EMBEDDED_CONTENT_PERCENT_SUDO =
		"# Awk (or nawk)\n" +
		"if [ -f /usr/xpg4/bin/awk ]; then\n" +
		"	AWK=\"/usr/xpg4/bin/awk\";\n" +
		"elif [ -f /usr/bin/nawk ]; then\n" +
		"	AWK=\"/usr/bin/nawk\";\n" +
		"else\n" +
		"	AWK=\"awk\";\n" +
		"fi\n" +
		"if [ -f /opt/StorMan/arcconf ]; then\n" +
		"       STORMAN=\"/opt/StorMan\";\n" +
		"elif [ -f /usr/StorMan/arcconf ]; then\n" +
		"       STORMAN=\"/usr/StorMan\";\n" +
		"else\n" +
		"	echo No Storman Installed; exit;\n" +
		"fi\n" +
		"DEVICES=`%{SUDO:/[opt|usr]/StorMan/arcconf} $STORMAN/arcconf getversion | $AWK '($1 ~ /Controller/ && $2 ~ /#[0-9]/) {controller=$2;gsub(/#/,\"\",controller);print(controller)}'`\n" +
		"for CTRL in $DEVICES\n" +
		"                do\n" +
		"                echo MSHWController $CTRL\n" +
		"                %{SUDO:/[opt|usr]/StorMan/arcconf} $STORMAN/arcconf getconfig $CTRL PD\n" +
		"                done";
	public static final String AWK_EMBEDDED_CONTENT_SUDO =
		"# Awk (or nawk)\n" +
		"if [ -f /usr/xpg4/bin/awk ]; then\n" +
		"	AWK=\"/usr/xpg4/bin/awk\";\n" +
		"elif [ -f /usr/bin/nawk ]; then\n" +
		"	AWK=\"/usr/bin/nawk\";\n" +
		"else\n" +
		"	AWK=\"awk\";\n" +
		"fi\n" +
		"if [ -f /opt/StorMan/arcconf ]; then\n" +
		"       STORMAN=\"/opt/StorMan\";\n" +
		"elif [ -f /usr/StorMan/arcconf ]; then\n" +
		"       STORMAN=\"/usr/StorMan\";\n" +
		"else\n" +
		"	echo No Storman Installed; exit;\n" +
		"fi\n" +
		"DEVICES=`sudo $STORMAN/arcconf getversion | $AWK '($1 ~ /Controller/ && $2 ~ /#[0-9]/) {controller=$2;gsub(/#/,\"\",controller);print(controller)}'`\n" +
		"for CTRL in $DEVICES\n" +
		"                do\n" +
		"                echo MSHWController $CTRL\n" +
		"                sudo $STORMAN/arcconf getconfig $CTRL PD\n" +
		"                done";
	public static final String SH_EMBEDDED_FILE_1 = "/bin/sh ${file::1}";
	public static final Integer EMBEDDED_FILE_1_REF = 1;
	public static final Integer EMBEDDED_FILE_2_REF = 2;
	public static final String EMBEDDED_FILE_1_COPY_COMMAND_LINE =
		"copy ${file::1} ${file::1}.bat > NUL & ${file::1}.bat %{USERNAME} %{PASSWORD} %{HOSTNAME} & del /F /Q ${file::1}.bat & del /F /Q ${file::2}.bat ";
	public static final String NO_PASSWORD_COMMAND =
		" naviseccli -User testUser -Password ******** -Address host -Scope 1 getagent";
	public static final String CLEAR_PASSWORD_COMMAND =
		" naviseccli -User testUser -Password pwd -Address host -Scope 1 getagent";
	public static final String COMMAND_TO_UPDATE =
		"copy ${file::2} ${file::2}.bat > NUL" +
		" & ${file::1}" +
		" & ${file::2}.bat" +
		" & del /F /Q ${file::1}" +
		" & del /F /Q ${file::2}.bat";
	public static final String RAIDCTL_COMMAND = "/usr/sbin/raidctl -S";
	public static final String SUDO_RAIDCTL_COMMAND = "%{SUDO:/usr/sbin/raidctl} /usr/sbin/raidctl -S";
	public static final String SUDO_NAVISECCLI_COMMAND =
		"%{Sudo:NaviSecCli} NaviSecCli -User %{USERNAME} -Password %{PASSWORD} -Address host -Scope 1 getagent";
	public static final String SEN_EMBEDDED_0001_PATH = "/tmp/metricshub_embedded_0001";
	public static final String SH_SEN_EMBEDDED_0001_PATH = "/bin/sh /tmp/metricshub_embedded_0001";
	public static final String EMBEDDED_TEMP_FILE_PREFIX = "metricshub_embedded_";
	public static final String AWK_FILE_EXTENSION = "\\w+\\.awk";
	public static final String AWK_FILE_NAME = "script.awk";
	public static final String AWK_SCRIPT_BASE_NAME = "script";

	// Host information
	public static final String HOST = "host";
	public static final String HOSTNAME = "hostname";

	public static final String SUDO_KEYWORD = "sudo";

	public static final String ERROR_IN_FILE1 = "error in file1";
	public static final String WMI_EXCEPTION_OTHER_MESSAGE = "other";

	public static final String RESULT = "result";

	public static final int PORT = 2222;

	/****************************************/

	@TempDir
	static File tempDir;

	static BiFunction<String, String, File> jUnitTempFileCreator;

	/**
	 * Setup unit tests.
	 */
	@BeforeAll
	static void setup() {
		// Initialize temporary file creator for JUnit tests.
		jUnitTempFileCreator =
			(name, extension) -> {
				try {
					return File.createTempFile(EMBEDDED_TEMP_FILE_PREFIX + name, extension, tempDir);
				} catch (IOException e) {
					throw new OsCommandService.TempFileCreationException(e);
				}
			};
		commandLineEmbeddedFiles = new HashMap<>();
	}

	@BeforeEach
	void clearEmbeddedFiles() {
		commandLineEmbeddedFiles.clear();
	}

	/**
	 * Retrieve temporary embedded files using the {@link FilenameFilter}.
	 *
	 * @return Array of {@link File} instances
	 */
	private static File[] getTempEmbeddedFiles() {
		return tempDir.listFiles((directory, fileName) -> fileName.startsWith(EMBEDDED_TEMP_FILE_PREFIX));
	}

	/**
	 * Assert that temporary embedded files are removed correctly.
	 */
	private static void checkNoTempEmbeddedFileExist() {
		assertEquals(0, getTempEmbeddedFiles().length);
	}

	@Test
	void testCreateOsCommandEmbeddedFiles() throws Exception {
		checkNoTempEmbeddedFileExist();

		assertThrows(
			IllegalArgumentException.class,
			() -> OsCommandHelper.createOsCommandEmbeddedFiles(null, null, EMPTY_EMBEDDED_FILE_MAP, jUnitTempFileCreator)
		);

		// Embedded files are referenced in the command line but cannot be found
		assertThrows(
			IllegalStateException.class,
			() ->
				OsCommandHelper.createOsCommandEmbeddedFiles(
					EMBEDDED_FILE_1_COPY_COMMAND_LINE,
					null,
					EMPTY_EMBEDDED_FILE_MAP,
					jUnitTempFileCreator
				)
		);

		assertEquals(
			Collections.emptyMap(),
			OsCommandHelper.createOsCommandEmbeddedFiles(CMD, null, EMPTY_EMBEDDED_FILE_MAP, jUnitTempFileCreator)
		);

		// case embeddedFile not found
		commandLineEmbeddedFiles.put(EMBEDDED_FILE_1_REF, new EmbeddedFile());
		assertThrows(
			IllegalStateException.class,
			() ->
				OsCommandHelper.createOsCommandEmbeddedFiles(
					EMBEDDED_FILE_1_COPY_COMMAND_LINE,
					null,
					commandLineEmbeddedFiles,
					jUnitTempFileCreator
				)
		);

		// case embeddedFile content null
		assertThrows(
			IllegalStateException.class,
			() ->
				OsCommandHelper.createOsCommandEmbeddedFiles(
					EMBEDDED_FILE_1_COPY_COMMAND_LINE,
					null,
					commandLineEmbeddedFiles,
					jUnitTempFileCreator
				)
		);

		checkNoTempEmbeddedFileExist();

		// case IOException in temp file creation
		try (final MockedStatic<OsCommandHelper> mockedOsCommandHelper = mockStatic(OsCommandHelper.class)) {
			commandLineEmbeddedFiles.put(EMBEDDED_FILE_1_REF, new EmbeddedFile(ECHO_OS.getBytes(), BAT, EMBEDDED_FILE_1_REF));
			commandLineEmbeddedFiles.put(
				EMBEDDED_FILE_2_REF,
				new EmbeddedFile(ECHO_HELLO_WORLD.getBytes(), null, EMBEDDED_FILE_2_REF)
			);

			mockedOsCommandHelper
				.when(() -> OsCommandHelper.createTempFileWithEmbeddedFileContent(any(EmbeddedFile.class), isNull(), any()))
				.thenThrow(IOException.class);

			mockedOsCommandHelper
				.when(() ->
					OsCommandHelper.createOsCommandEmbeddedFiles(
						EMBEDDED_FILE_1_COPY_COMMAND_LINE,
						null,
						commandLineEmbeddedFiles,
						jUnitTempFileCreator
					)
				)
				.thenCallRealMethod();

			assertThrows(
				IOException.class,
				() ->
					OsCommandHelper.createOsCommandEmbeddedFiles(
						EMBEDDED_FILE_1_COPY_COMMAND_LINE,
						null,
						commandLineEmbeddedFiles,
						jUnitTempFileCreator
					)
			);
		}

		// case OK
		{
			checkNoTempEmbeddedFileExist();

			commandLineEmbeddedFiles.put(
				EMBEDDED_FILE_2_REF,
				new EmbeddedFile(AWK_EMBEDDED_CONTENT_PERCENT_SUDO.getBytes(), AWK_FILE_NAME, EMBEDDED_FILE_2_REF)
			);

			final Map<String, File> embeddedTempFiles = OsCommandHelper.createOsCommandEmbeddedFiles(
				EMBEDDED_FILE_1_COPY_COMMAND_LINE,
				new SudoInformation(true, Set.of(ARCCONF_PATH), SudoInformation.SUDO),
				commandLineEmbeddedFiles,
				jUnitTempFileCreator
			);

			assertEquals(2, embeddedTempFiles.size());

			{
				final File file = embeddedTempFiles.get(String.format("${file::%d}", EMBEDDED_FILE_1_REF));
				assertNotNull(file);
				assertTrue(file.exists());
				assertTrue(file.getName().matches(EMBEDDED_TEMP_FILE_PREFIX + BAT + "\\w+"));
				assertEquals(
					ECHO_OS,
					Files.readAllLines(Paths.get(file.getAbsolutePath())).stream().collect(Collectors.joining())
				);
				file.delete();
			}
			{
				final File file = embeddedTempFiles.get(String.format("${file::%d}", EMBEDDED_FILE_2_REF));
				assertNotNull(file);
				assertTrue(file.exists());
				assertTrue(file.getName().matches(EMBEDDED_TEMP_FILE_PREFIX + AWK_SCRIPT_BASE_NAME + AWK_FILE_EXTENSION));
				/*
				 * assertEquals(
-						expectedContent.replaceAll("[\r\n]", EMPTY),
-						Files.readAllLines(Paths.get(file.getAbsolutePath())).stream().collect(Collectors.joining()));
				 */
				assertEquals(
					AWK_EMBEDDED_CONTENT_SUDO.replaceAll(END_OF_LINE_IN_BRACKETS, EMPTY),
					Files.readAllLines(Paths.get(file.getAbsolutePath())).stream().collect(Collectors.joining())
				);
				file.delete();
			}
		}
	}

	@Test
	void testReplaceSudo() {
		assertNull(OsCommandHelper.replaceSudo(null, null));
		final SudoInformation defaultSudoInformation = new SudoInformation(false, Set.of(), SudoInformation.SUDO);
		assertNull(OsCommandHelper.replaceSudo(null, defaultSudoInformation));

		assertEquals(EMPTY, OsCommandHelper.replaceSudo(EMPTY, null));
		assertEquals(EMPTY, OsCommandHelper.replaceSudo(EMPTY, defaultSudoInformation));
		assertEquals(SINGLE_SPACE, OsCommandHelper.replaceSudo(SINGLE_SPACE, null));
		assertEquals(SINGLE_SPACE, OsCommandHelper.replaceSudo(SINGLE_SPACE, defaultSudoInformation));

		assertEquals(TEXT, OsCommandHelper.replaceSudo(TEXT, null));
		assertEquals(TEXT, OsCommandHelper.replaceSudo(TEXT, defaultSudoInformation));

		// Check replace sudo tag with empty string.
		assertEquals(SPACE_KEY, OsCommandHelper.replaceSudo(SUDO_KEY, null));
		assertEquals(SPACE_KEY, OsCommandHelper.replaceSudo(SUDO_KEY, defaultSudoInformation));
		final SudoInformation useSudoInformation = new SudoInformation(true, Set.of(), SudoInformation.SUDO);
		assertEquals(SPACE_KEY, OsCommandHelper.replaceSudo(SUDO_KEY, useSudoInformation));
		assertEquals(
			SPACE_KEY + END_OF_LINE + SPACE_KEY,
			OsCommandHelper.replaceSudo(SUDO_KEY + END_OF_LINE + SUDO_KEY, useSudoInformation)
		);

		final SudoInformation useSudoKeyInformation = new SudoInformation(true, Set.of(KEY), SudoInformation.SUDO);
		assertEquals(SUDO_KEYWORD + SPACE_KEY, OsCommandHelper.replaceSudo(SUDO_KEY, useSudoKeyInformation));

		assertEquals(
			SUDO_KEY_RESULT,
			OsCommandHelper.replaceSudo(SUDO_KEY + END_OF_LINE + SUDO_KEY, useSudoKeyInformation)
		);
	}

	@Test
	void testRunLocalCommandKO() throws Exception {
		assertThrows(IllegalArgumentException.class, () -> OsCommandService.runLocalCommand(null, 1, null));
		assertThrows(IllegalArgumentException.class, () -> OsCommandService.runLocalCommand(CMD, -1, null));
		assertThrows(IllegalArgumentException.class, () -> OsCommandService.runLocalCommand(CMD, 0, null));

		// Case IllegalStateException when the process cannot be created
		final ProcessBuilder processBuilderMock = mock(ProcessBuilder.class);
		try (final MockedStatic<OsCommandService> mockedOsCommandService = mockStatic(OsCommandService.class);) {
			mockedOsCommandService.when(() -> OsCommandService.runLocalCommand(CMD, 1, null)).thenCallRealMethod();
			mockedOsCommandService.when(() -> OsCommandService.createProcessBuilder(CMD)).thenReturn(processBuilderMock);
			when(processBuilderMock.start()).thenReturn(null);

			assertThrows(IllegalStateException.class, () -> OsCommandService.runLocalCommand(CMD, 1, null));
		}
	}

	@Test
	@EnabledOnOs(OS.WINDOWS)
	void testCreateProcessBuilderWindows() {
		final ProcessBuilder processBuilder = OsCommandService.createProcessBuilder(CMD);
		assertNotNull(processBuilder);
		final List<String> command = processBuilder.command();
		assertNotNull(command.get(0));
		assertEquals("/C", command.get(1));
		assertEquals(CMD, command.get(2));
	}

	@Test
	@EnabledOnOs(OS.LINUX)
	void testCreateProcessBuilderLinux() {
		final ProcessBuilder processBuilder = OsCommandService.createProcessBuilder(CMD);
		assertNotNull(processBuilder);
		final List<String> command = processBuilder.command();
		assertNotNull(command.get(0));
		assertEquals("-c", command.get(1));
		assertEquals(CMD, command.get(2));
	}

	@Test
	@EnabledOnOs(OS.WINDOWS)
	void testRunLocalCommandWindowsTimeout() throws Exception {
		assertThrows(TimeoutException.class, () -> OsCommandService.runLocalCommand(PAUSE, 1, null));
	}

	@Test
	@EnabledOnOs(OS.LINUX)
	void testRunLocalCommandLinuxTimeout() throws Exception {
		assertThrows(TimeoutException.class, () -> OsCommandService.runLocalCommand(SLEEP_5, 1, null));
	}

	@Test
	@EnabledOnOs(OS.WINDOWS)
	void testRunLocalCommandWindows() throws Exception {
		assertEquals(TEST_RESULT, OsCommandService.runLocalCommand(ECHO_TEST_UPPER_CASE, 1, null));
	}

	@Test
	@EnabledOnOs(OS.LINUX)
	void testRunLocalCommandLinux() throws Exception {
		assertEquals(TEST_RESULT, OsCommandService.runLocalCommand(ECHO_TEST_LOWER_CASE, 1, null));
	}

	@Test
	void testRunSshCommand() throws Exception {
		final SshConfiguration sshConfiguration = mock(SshConfiguration.class);
		final int timeout = 1000;

		assertThrows(
			IllegalArgumentException.class,
			() -> OsCommandService.runSshCommand(null, HOST, sshConfiguration, timeout, null, null, DeviceKind.LINUX)
		);
		assertThrows(
			IllegalArgumentException.class,
			() -> OsCommandService.runSshCommand(CMD, null, sshConfiguration, timeout, null, null, DeviceKind.LINUX)
		);
		assertThrows(
			IllegalArgumentException.class,
			() -> OsCommandService.runSshCommand(CMD, HOST, null, timeout, null, null, DeviceKind.LINUX)
		);
		assertThrows(
			IllegalArgumentException.class,
			() -> OsCommandService.runSshCommand(CMD, HOST, sshConfiguration, -1, null, null, DeviceKind.LINUX)
		);
		assertThrows(
			IllegalArgumentException.class,
			() -> OsCommandService.runSshCommand(CMD, HOST, sshConfiguration, 0, null, null, DeviceKind.LINUX)
		);

		try (
			final MockedStatic<OsCommandRequestExecutor> mockedClientsExecutor = mockStatic(OsCommandRequestExecutor.class)
		) {
			when(sshConfiguration.getUsername()).thenReturn(USERNAME);
			when(sshConfiguration.getPassword()).thenReturn(PASSWORD.toCharArray());
			when(sshConfiguration.getPort()).thenReturn(PORT);

			mockedClientsExecutor
				.when(() ->
					OsCommandRequestExecutor.runRemoteSshCommand(
						HOST,
						USERNAME,
						PASSWORD.toCharArray(),
						null,
						CMD,
						timeout,
						PORT,
						null,
						null,
						DeviceKind.LINUX
					)
				)
				.thenReturn(RESULT);

			assertEquals(
				RESULT,
				OsCommandService.runSshCommand(CMD, HOST, sshConfiguration, timeout, null, null, DeviceKind.LINUX)
			);
		}
	}

	@Test
	void testGetFileNameFromSudoCommand() {
		assertThrows(IllegalArgumentException.class, () -> OsCommandHelper.getFileNameFromSudoCommand(null));
		assertEquals(Optional.empty(), OsCommandHelper.getFileNameFromSudoCommand(RAIDCTL_COMMAND));
		assertEquals(Optional.of(RAIDCTL_PATH), OsCommandHelper.getFileNameFromSudoCommand(SUDO_RAIDCTL_COMMAND));
		assertEquals(
			Optional.of(NAVISECCLI_CAMEL_CASE),
			OsCommandHelper.getFileNameFromSudoCommand(SUDO_NAVISECCLI_COMMAND)
		);
	}

	@Test
	void testGetTimeout() {
		final OsCommandConfiguration osCommandConfig = new OsCommandConfiguration();
		osCommandConfig.setTimeout(2L);

		final SshConfiguration sshConfiguration = SshConfiguration
			.sshConfigurationBuilder()
			.username(USERNAME)
			.password(PASSWORD.toCharArray())
			.build();
		sshConfiguration.setTimeout(4L);

		assertEquals(1, OsCommandService.getTimeout(1L, osCommandConfig, sshConfiguration, 5));
		assertEquals(2, OsCommandService.getTimeout(null, osCommandConfig, sshConfiguration, 5));
		assertEquals(4, OsCommandService.getTimeout(null, null, sshConfiguration, 5));
		assertEquals(5, OsCommandService.getTimeout(null, null, null, 5));
		assertEquals(30, OsCommandService.getTimeout(null, new OsCommandConfiguration(), sshConfiguration, 5));
		assertEquals(
			30,
			OsCommandService.getTimeout(
				null,
				null,
				SshConfiguration.sshConfigurationBuilder().username(USERNAME).password(PASSWORD.toCharArray()).build(),
				5
			)
		);
	}

	@Test
	void testGetUsername() {
		assertEquals(Optional.empty(), OsCommandService.getUsername(null));
		assertEquals(Optional.empty(), OsCommandService.getUsername(new OsCommandConfiguration()));
		assertEquals(
			Optional.empty(),
			OsCommandService.getUsername(SshConfiguration.sshConfigurationBuilder().password(PASSWORD.toCharArray()).build())
		);

		assertEquals(
			Optional.of(USERNAME),
			OsCommandService.getUsername(
				SshConfiguration.sshConfigurationBuilder().username(USERNAME).password(PASSWORD.toCharArray()).build()
			)
		);
	}

	@Test
	void testGetPassword() {
		assertEquals(Optional.empty(), OsCommandService.getPassword(null));
		assertEquals(Optional.empty(), OsCommandService.getPassword(new OsCommandConfiguration()));
		assertEquals(
			Optional.empty(),
			OsCommandService.getPassword(SshConfiguration.sshConfigurationBuilder().username(USERNAME).build())
		);

		char[] charArrayPassword = PASSWORD.toCharArray();

		assertEquals(
			Optional.of(charArrayPassword),
			OsCommandService.getPassword(
				SshConfiguration.sshConfigurationBuilder().username(USERNAME).password(charArrayPassword).build()
			)
		);
	}

	@Test
	void testRunOsCommandCommandLineNull() {
		final TelemetryManager telemetryManager = TelemetryManager.builder().build();
		final Map<Integer, EmbeddedFile> connectorEmbeddedFiles = Map.of();
		assertThrows(
			IllegalArgumentException.class,
			() -> OsCommandService.runOsCommand(null, telemetryManager, 120L, false, false, connectorEmbeddedFiles)
		);
	}

	@Test
	void testRunOsCommandTelemetryManagerNull() {
		final Map<Integer, EmbeddedFile> connectorEmbeddedFiles = Map.of();
		assertThrows(
			IllegalArgumentException.class,
			() -> OsCommandService.runOsCommand(CMD, null, 120L, false, false, connectorEmbeddedFiles)
		);
	}

	@Test
	void testRunOsCommandRemoteNoUser() {
		final SshConfiguration sshConfiguration = SshConfiguration
			.sshConfigurationBuilder()
			.username(SINGLE_SPACE)
			.password(PWD_COMMAND.toCharArray())
			.build();

		final HostConfiguration hostConfiguration = HostConfiguration
			.builder()
			.hostId(ID)
			.hostname(HOST)
			.hostType(DeviceKind.WINDOWS)
			.configurations(Map.of(sshConfiguration.getClass(), sshConfiguration))
			.build();

		final TelemetryManager telemetryManager = TelemetryManager.builder().hostConfiguration(hostConfiguration).build();

		assertThrows(
			NoCredentialProvidedException.class,
			() -> OsCommandService.runOsCommand(NAVISECCLI_COMMAND, telemetryManager, 120L, false, false, Map.of())
		);
	}

	@Test
	@EnabledOnOs(OS.WINDOWS)
	void testRunOsCommandWindowsError() {
		final Map<Integer, EmbeddedFile> connectorEmbeddedFiles = Map.of();
		final SshConfiguration sshConfiguration = SshConfiguration
			.sshConfigurationBuilder()
			.username(USERNAME)
			.password(PWD_COMMAND.toCharArray())
			.build();

		final HostConfiguration hostConfiguration = HostConfiguration
			.builder()
			.hostId(ID)
			.hostname(HOST)
			.hostType(DeviceKind.WINDOWS)
			.configurations(Map.of(sshConfiguration.getClass(), sshConfiguration))
			.build();

		final TelemetryManager telemetryManager = TelemetryManager.builder().hostConfiguration(hostConfiguration).build();

		assertThrows(
			TimeoutException.class,
			() -> OsCommandService.runOsCommand(PAUSE, telemetryManager, 1L, false, true, connectorEmbeddedFiles)
		);
	}

	@Test
	void testRunOsCommandRemoteWindowsEmbeddedFilesError() {
		final Map<Integer, EmbeddedFile> embeddedFiles = new HashMap<>();
		embeddedFiles.put(EMBEDDED_FILE_1_REF, new EmbeddedFile(ECHO_OS.getBytes(), BAT, EMBEDDED_FILE_1_REF));
		embeddedFiles.put(EMBEDDED_FILE_2_REF, new EmbeddedFile(ECHO_HELLO_WORLD.getBytes(), null, EMBEDDED_FILE_2_REF));

		final SshConfiguration osCommandConfiguration = new SshConfiguration();
		osCommandConfiguration.setUsername(USERNAME);
		osCommandConfiguration.setPassword(PWD_COMMAND.toCharArray());

		final HostConfiguration hostConfiguration = HostConfiguration
			.builder()
			.hostId(ID)
			.hostname(HOST)
			.hostType(DeviceKind.WINDOWS)
			.configurations(Map.of(osCommandConfiguration.getClass(), osCommandConfiguration))
			.build();

		final TelemetryManager telemetryManager = TelemetryManager.builder().hostConfiguration(hostConfiguration).build();

		try (
			final MockedStatic<OsCommandService> mockedOsCommandService = mockStatic(OsCommandService.class);
			final MockedStatic<OsCommandHelper> mockedOsCommandHelper = mockStatic(OsCommandHelper.class);
			final MockedStatic<EmbeddedFileHelper> mockedEmbeddedFileHelper = mockStatic(EmbeddedFileHelper.class)
		) {
			mockedOsCommandService.when(() -> OsCommandService.getUsername(osCommandConfiguration)).thenCallRealMethod();
			mockedOsCommandService
				.when(() ->
					OsCommandService.runOsCommand(COMMAND_TO_UPDATE, telemetryManager, 120L, false, false, embeddedFiles)
				)
				.thenCallRealMethod();

			mockedEmbeddedFileHelper
				.when(() -> EmbeddedFileHelper.findEmbeddedFiles(anyString(), anyMap()))
				.thenReturn(commandLineEmbeddedFiles);

			mockedOsCommandHelper
				.when(() ->
					OsCommandHelper.createOsCommandEmbeddedFiles(
						COMMAND_TO_UPDATE,
						null,
						commandLineEmbeddedFiles,
						TEMP_FILE_CREATOR
					)
				)
				.thenThrow(new IOException(ERROR_IN_FILE1));

			assertThrows(
				IOException.class,
				() -> OsCommandService.runOsCommand(COMMAND_TO_UPDATE, telemetryManager, 120L, false, false, embeddedFiles)
			);
		}
	}

	@Test
	@EnabledOnOs(OS.LINUX)
	void testRunOsCommandLinuxError() {
		final HostConfiguration hostConfiguration = HostConfiguration
			.builder()
			.hostId(ID)
			.hostname(HOST)
			.hostType(DeviceKind.LINUX)
			.build();

		final TelemetryManager telemetryManager = TelemetryManager.builder().hostConfiguration(hostConfiguration).build();

		assertThrows(
			TimeoutException.class,
			() -> OsCommandService.runOsCommand(SLEEP_5, telemetryManager, 1L, false, true, Map.of())
		);
	}

	@Test
	@EnabledOnOs(OS.WINDOWS)
	void testRunOsCommandLocalWindows() throws Exception {
		final HostConfiguration hostConfiguration = HostConfiguration
			.builder()
			.hostId(ID)
			.hostname(HOST)
			.hostType(DeviceKind.WINDOWS)
			.build();

		final TelemetryManager telemetryManager = TelemetryManager.builder().hostConfiguration(hostConfiguration).build();

		final OsCommandResult expect = new OsCommandResult(TEST_RESULT, ECHO_TEST_UPPER_CASE);

		assertEquals(
			expect,
			OsCommandService.runOsCommand(ECHO_TEST_UPPER_CASE, telemetryManager, 120L, false, true, Map.of())
		);
	}

	@Test
	@EnabledOnOs(OS.LINUX)
	void testRunOsCommandLocalLinux() throws Exception {
		final SshConfiguration sshConfiguration = SshConfiguration
			.sshConfigurationBuilder()
			.username(USERNAME)
			.password(PWD_COMMAND.toCharArray())
			.build();

		final HostConfiguration hostConfiguration = HostConfiguration
			.builder()
			.hostId(ID)
			.hostname(HOST)
			.hostType(DeviceKind.LINUX)
			.configurations(Map.of(sshConfiguration.getClass(), sshConfiguration))
			.build();

		final TelemetryManager telemetryManager = TelemetryManager.builder().hostConfiguration(hostConfiguration).build();

		final OsCommandResult expect = new OsCommandResult(TEST_RESULT, ECHO_TEST_LOWER_CASE);

		assertEquals(
			expect,
			OsCommandService.runOsCommand(ECHO_TEST_LOWER_CASE, telemetryManager, 120L, false, true, Map.of())
		);
	}

	@Test
	@EnabledOnOs(OS.WINDOWS)
	void testRunOsCommandRemoteExecutedLocallyWindows() throws Exception {
		final OsCommandResult expect = new OsCommandResult(TEST_RESULT, ECHO_TEST_UPPER_CASE);

		final HostConfiguration hostConfiguration = HostConfiguration
			.builder()
			.hostId(ID)
			.hostname(HOST)
			.hostType(DeviceKind.WINDOWS)
			.build();

		final TelemetryManager telemetryManager = TelemetryManager.builder().hostConfiguration(hostConfiguration).build();

		assertEquals(
			expect,
			OsCommandService.runOsCommand(ECHO_TEST_UPPER_CASE, telemetryManager, 120L, true, false, Map.of())
		);
	}

	@Test
	@EnabledOnOs(OS.LINUX)
	void testRunOsCommandRemoteExecutedLocallyLinux() throws Exception {
		final OsCommandResult expect = new OsCommandResult(TEST_RESULT, ECHO_TEST_LOWER_CASE);

		final HostConfiguration hostConfiguration = HostConfiguration
			.builder()
			.hostId(ID)
			.hostname(HOST)
			.hostType(DeviceKind.LINUX)
			.build();

		final TelemetryManager telemetryManager = TelemetryManager.builder().hostConfiguration(hostConfiguration).build();

		assertEquals(
			expect,
			OsCommandService.runOsCommand(ECHO_TEST_LOWER_CASE, telemetryManager, 120L, true, false, Map.of())
		);
	}

	@Test
	void testRunOsCommandRemoteLinuxOSCommandConfigNull() throws Exception {
		final SshConfiguration sshConfiguration = SshConfiguration
			.sshConfigurationBuilder()
			.username(USERNAME)
			.password(PWD_COMMAND.toCharArray())
			.build();

		final HostConfiguration hostConfiguration = HostConfiguration
			.builder()
			.hostId(ID)
			.hostname(HOST)
			.hostType(DeviceKind.LINUX)
			.configurations(Map.of(sshConfiguration.getClass(), sshConfiguration))
			.build();

		final TelemetryManager telemetryManager = TelemetryManager.builder().hostConfiguration(hostConfiguration).build();

		try (
			final MockedStatic<OsCommandService> mockedOsCommandService = mockStatic(OsCommandService.class);
			final MockedStatic<OsCommandHelper> mockedOsCommandHelper = mockStatic(OsCommandHelper.class);
			final MockedStatic<StringHelper> mockedStringHelper = mockStatic(StringHelper.class)
		) {
			mockedOsCommandService.when(() -> OsCommandService.getUsername(sshConfiguration)).thenCallRealMethod();
			mockedOsCommandService
				.when(() -> OsCommandService.getTimeout(120L, null, sshConfiguration, 300))
				.thenCallRealMethod();
			mockedOsCommandService.when(() -> OsCommandService.getPassword(sshConfiguration)).thenCallRealMethod();
			mockedOsCommandHelper.when(() -> OsCommandHelper.replaceSudo(anyString(), isNull())).thenCallRealMethod();
			mockedOsCommandHelper.when(() -> OsCommandHelper.getFileNameFromSudoCommand(anyString())).thenCallRealMethod();
			mockedStringHelper.when(() -> StringHelper.protectCaseInsensitiveRegex(anyString())).thenCallRealMethod();
			mockedOsCommandHelper
				.when(() ->
					OsCommandHelper.createOsCommandEmbeddedFiles(
						NAVISECCLI_COMMAND,
						null,
						commandLineEmbeddedFiles,
						TEMP_FILE_CREATOR
					)
				)
				.thenCallRealMethod();

			mockedOsCommandService
				.when(() -> {
					OsCommandService.runSshCommand(
						CLEAR_PASSWORD_COMMAND,
						HOST,
						sshConfiguration,
						120,
						Collections.emptyList(),
						NO_PASSWORD_COMMAND,
						DeviceKind.LINUX
					);
				})
				.thenReturn(AGENT_REV_RESULT);

			mockedOsCommandService
				.when(() -> OsCommandService.runOsCommand(NAVISECCLI_COMMAND, telemetryManager, 120L, false, false, Map.of()))
				.thenCallRealMethod();

			final OsCommandResult expect = new OsCommandResult(AGENT_REV_RESULT, NO_PASSWORD_COMMAND);

			assertEquals(
				expect,
				OsCommandService.runOsCommand(NAVISECCLI_COMMAND, telemetryManager, 120L, false, false, Map.of())
			);
		}
	}

	@Test
	void testRunOsCommandRemoteLinuxNoSudo() throws Exception {
		final SshConfiguration sshConfiguration = SshConfiguration
			.sshConfigurationBuilder()
			.username(USERNAME)
			.password(PWD_COMMAND.toCharArray())
			.build();

		final OsCommandConfiguration osCommandConfiguration = new OsCommandConfiguration();
		osCommandConfiguration.setUseSudoCommands(Collections.singleton(NAVISECCLI_CAMEL_CASE.toLowerCase()));

		final HostConfiguration hostConfiguration = HostConfiguration
			.builder()
			.hostId(ID)
			.hostname(HOST)
			.hostType(DeviceKind.LINUX)
			.configurations(
				Map.of(sshConfiguration.getClass(), sshConfiguration, osCommandConfiguration.getClass(), osCommandConfiguration)
			)
			.build();

		final SudoInformation sudoInformation = new SudoInformation(
			osCommandConfiguration.isUseSudo(),
			osCommandConfiguration.getUseSudoCommands(),
			osCommandConfiguration.getSudoCommand()
		);
		final TelemetryManager telemetryManager = TelemetryManager.builder().hostConfiguration(hostConfiguration).build();

		try (
			final MockedStatic<OsCommandService> mockedOsCommandService = mockStatic(OsCommandService.class);
			final MockedStatic<OsCommandHelper> mockedOsCommandHelper = mockStatic(OsCommandHelper.class);
			final MockedStatic<StringHelper> mockedStringHelper = mockStatic(StringHelper.class)
		) {
			mockedOsCommandService.when(() -> OsCommandService.getUsername(sshConfiguration)).thenCallRealMethod();
			mockedOsCommandService
				.when(() -> OsCommandService.getTimeout(120L, osCommandConfiguration, sshConfiguration, 300))
				.thenCallRealMethod();
			mockedOsCommandService.when(() -> OsCommandService.getPassword(sshConfiguration)).thenCallRealMethod();
			mockedOsCommandHelper
				.when(() -> OsCommandHelper.replaceSudo(anyString(), eq(sudoInformation)))
				.thenCallRealMethod();
			mockedOsCommandHelper.when(() -> OsCommandHelper.getFileNameFromSudoCommand(anyString())).thenCallRealMethod();
			mockedStringHelper.when(() -> StringHelper.protectCaseInsensitiveRegex(anyString())).thenCallRealMethod();
			mockedOsCommandHelper
				.when(() ->
					OsCommandHelper.createOsCommandEmbeddedFiles(
						NAVISECCLI_COMMAND,
						sudoInformation,
						commandLineEmbeddedFiles,
						TEMP_FILE_CREATOR
					)
				)
				.thenCallRealMethod();

			mockedOsCommandService
				.when(() ->
					OsCommandService.runSshCommand(
						CLEAR_PASSWORD_COMMAND,
						HOST,
						sshConfiguration,
						120L,
						Collections.emptyList(),
						NO_PASSWORD_COMMAND,
						DeviceKind.LINUX
					)
				)
				.thenReturn(AGENT_REV_RESULT);

			mockedOsCommandService
				.when(() -> OsCommandService.runOsCommand(NAVISECCLI_COMMAND, telemetryManager, 120L, false, false, Map.of()))
				.thenCallRealMethod();

			final OsCommandResult expect = new OsCommandResult(AGENT_REV_RESULT, NO_PASSWORD_COMMAND);

			assertEquals(
				expect,
				OsCommandService.runOsCommand(NAVISECCLI_COMMAND, telemetryManager, 120L, false, false, Map.of())
			);
		}
	}

	@Test
	void testRunOsCommandRemoteLinuxNotInUseSudoCommands() throws Exception {
		final SshConfiguration sshConfiguration = SshConfiguration
			.sshConfigurationBuilder()
			.username(USERNAME)
			.password(PWD_COMMAND.toCharArray())
			.build();

		final OsCommandConfiguration osCommandConfiguration = new OsCommandConfiguration();
		osCommandConfiguration.setUseSudo(true);
		osCommandConfiguration.setUseSudoCommands(Collections.singleton(WMI_EXCEPTION_OTHER_MESSAGE));

		final HostConfiguration hostConfiguration = HostConfiguration
			.builder()
			.hostId(ID)
			.hostname(HOST)
			.hostType(DeviceKind.LINUX)
			.configurations(
				Map.of(sshConfiguration.getClass(), sshConfiguration, osCommandConfiguration.getClass(), osCommandConfiguration)
			)
			.build();

		final SudoInformation sudoInformation = new SudoInformation(
			osCommandConfiguration.isUseSudo(),
			osCommandConfiguration.getUseSudoCommands(),
			osCommandConfiguration.getSudoCommand()
		);
		final TelemetryManager telemetryManager = TelemetryManager.builder().hostConfiguration(hostConfiguration).build();

		try (
			final MockedStatic<OsCommandService> mockedOsCommandService = mockStatic(OsCommandService.class);
			final MockedStatic<OsCommandHelper> mockedOsCommandHelper = mockStatic(OsCommandHelper.class);
			final MockedStatic<StringHelper> mockedStringHelper = mockStatic(StringHelper.class)
		) {
			mockedOsCommandService.when(() -> OsCommandService.getUsername(sshConfiguration)).thenCallRealMethod();
			mockedOsCommandService
				.when(() -> OsCommandService.getTimeout(120L, osCommandConfiguration, sshConfiguration, 300))
				.thenCallRealMethod();
			mockedOsCommandService.when(() -> OsCommandService.getPassword(sshConfiguration)).thenCallRealMethod();
			mockedOsCommandHelper
				.when(() -> OsCommandHelper.replaceSudo(anyString(), eq(sudoInformation)))
				.thenCallRealMethod();
			mockedOsCommandHelper.when(() -> OsCommandHelper.getFileNameFromSudoCommand(anyString())).thenCallRealMethod();
			mockedStringHelper.when(() -> StringHelper.protectCaseInsensitiveRegex(anyString())).thenCallRealMethod();
			mockedOsCommandHelper
				.when(() ->
					OsCommandHelper.createOsCommandEmbeddedFiles(
						NAVISECCLI_COMMAND,
						sudoInformation,
						commandLineEmbeddedFiles,
						TEMP_FILE_CREATOR
					)
				)
				.thenCallRealMethod();

			mockedOsCommandService
				.when(() ->
					OsCommandService.runSshCommand(
						CLEAR_PASSWORD_COMMAND,
						HOST,
						sshConfiguration,
						120,
						Collections.emptyList(),
						NO_PASSWORD_COMMAND,
						DeviceKind.LINUX
					)
				)
				.thenReturn(AGENT_REV_RESULT);

			mockedOsCommandService
				.when(() -> OsCommandService.runOsCommand(NAVISECCLI_COMMAND, telemetryManager, 120L, false, false, Map.of()))
				.thenCallRealMethod();

			final OsCommandResult expect = new OsCommandResult(AGENT_REV_RESULT, NO_PASSWORD_COMMAND);

			assertEquals(
				expect,
				OsCommandService.runOsCommand(NAVISECCLI_COMMAND, telemetryManager, 120L, false, false, Map.of())
			);
		}
	}

	@Test
	void testRunOsCommandRemoteLinuxWithSudoReplaced() throws Exception {
		final SshConfiguration sshConfiguration = SshConfiguration
			.sshConfigurationBuilder()
			.username(USERNAME)
			.password(PWD_COMMAND.toCharArray())
			.build();

		final OsCommandConfiguration osCommandConfiguration = new OsCommandConfiguration();
		osCommandConfiguration.setUseSudo(true);
		osCommandConfiguration.setUseSudoCommands(Collections.singleton(NAVISECCLI_CAMEL_CASE.toLowerCase()));

		final HostConfiguration hostConfiguration = HostConfiguration
			.builder()
			.hostId(ID)
			.hostname(HOST)
			.hostType(DeviceKind.LINUX)
			.configurations(
				Map.of(sshConfiguration.getClass(), sshConfiguration, osCommandConfiguration.getClass(), osCommandConfiguration)
			)
			.build();

		final SudoInformation sudoInformation = new SudoInformation(
			osCommandConfiguration.isUseSudo(),
			osCommandConfiguration.getUseSudoCommands(),
			osCommandConfiguration.getSudoCommand()
		);
		final TelemetryManager telemetryManager = TelemetryManager.builder().hostConfiguration(hostConfiguration).build();

		try (
			final MockedStatic<OsCommandService> mockedOsCommandService = mockStatic(OsCommandService.class);
			final MockedStatic<OsCommandHelper> mockedOsCommandHelper = mockStatic(OsCommandHelper.class);
			final MockedStatic<StringHelper> mockedStringHelper = mockStatic(StringHelper.class)
		) {
			mockedOsCommandService.when(() -> OsCommandService.getUsername(sshConfiguration)).thenCallRealMethod();
			mockedOsCommandService
				.when(() -> OsCommandService.getTimeout(120L, osCommandConfiguration, sshConfiguration, 300))
				.thenCallRealMethod();
			mockedOsCommandService.when(() -> OsCommandService.getPassword(sshConfiguration)).thenCallRealMethod();
			mockedOsCommandHelper
				.when(() -> OsCommandHelper.replaceSudo(anyString(), eq(sudoInformation)))
				.thenCallRealMethod();
			mockedOsCommandHelper.when(() -> OsCommandHelper.getFileNameFromSudoCommand(anyString())).thenCallRealMethod();
			mockedStringHelper.when(() -> StringHelper.protectCaseInsensitiveRegex(anyString())).thenCallRealMethod();
			mockedOsCommandHelper
				.when(() ->
					OsCommandHelper.createOsCommandEmbeddedFiles(
						NAVISECCLI_COMMAND,
						sudoInformation,
						commandLineEmbeddedFiles,
						TEMP_FILE_CREATOR
					)
				)
				.thenCallRealMethod();

			mockedOsCommandService
				.when(() -> {
					OsCommandService.runSshCommand(
						SUDO_KEYWORD + CLEAR_PASSWORD_COMMAND,
						HOST,
						sshConfiguration,
						120,
						Collections.emptyList(),
						SUDO_KEYWORD + NO_PASSWORD_COMMAND,
						DeviceKind.LINUX
					);
				})
				.thenReturn(AGENT_REV_RESULT);

			mockedOsCommandService
				.when(() -> OsCommandService.runOsCommand(NAVISECCLI_COMMAND, telemetryManager, 120L, false, false, Map.of()))
				.thenCallRealMethod();

			final OsCommandResult expect = new OsCommandResult(AGENT_REV_RESULT, SUDO_KEYWORD + NO_PASSWORD_COMMAND);

			assertEquals(
				expect,
				OsCommandService.runOsCommand(NAVISECCLI_COMMAND, telemetryManager, 120L, false, false, Map.of())
			);
		}
	}

	@Test
	void testRunOsCommandRemoteLinuxWithEmbeddedFilesReplaced() throws Exception {
		commandLineEmbeddedFiles.put(
			EMBEDDED_FILE_1_REF,
			new EmbeddedFile(AWK_EMBEDDED_CONTENT_PERCENT_SUDO.getBytes(), null, EMBEDDED_FILE_1_REF)
		);

		final File localFile = mock(File.class);
		final Map<String, File> embeddedTempFiles = new HashMap<>();
		embeddedTempFiles.put(String.format("${file::%d}", EMBEDDED_FILE_1_REF), localFile);

		final OsCommandConfiguration osCommandConfiguration = OsCommandConfiguration
			.builder()
			.useSudo(true)
			.useSudoCommands(Collections.singleton(ARCCONF_PATH))
			.build();

		final SshConfiguration sshConfiguration = SshConfiguration
			.sshConfigurationBuilder()
			.username(USERNAME)
			.password(PWD_COMMAND.toCharArray())
			.build();

		final HostConfiguration hostConfiguration = HostConfiguration
			.builder()
			.hostId(ID)
			.hostname(HOST)
			.hostType(DeviceKind.LINUX)
			.configurations(
				Map.of(sshConfiguration.getClass(), sshConfiguration, osCommandConfiguration.getClass(), osCommandConfiguration)
			)
			.build();

		final SudoInformation sudoInformation = new SudoInformation(
			osCommandConfiguration.isUseSudo(),
			osCommandConfiguration.getUseSudoCommands(),
			osCommandConfiguration.getSudoCommand()
		);
		final TelemetryManager telemetryManager = TelemetryManager.builder().hostConfiguration(hostConfiguration).build();

		try (
			final MockedStatic<OsCommandService> mockedOsCommandService = mockStatic(OsCommandService.class);
			final MockedStatic<OsCommandHelper> mockedOsCommandHelper = mockStatic(OsCommandHelper.class);
			final MockedStatic<EmbeddedFileHelper> mockedEmbeddedFileHelper = mockStatic(EmbeddedFileHelper.class);
			final MockedStatic<StringHelper> mockedStringHelper = mockStatic(StringHelper.class)
		) {
			mockedOsCommandService.when(() -> OsCommandService.getUsername(sshConfiguration)).thenCallRealMethod();
			mockedOsCommandService
				.when(() -> OsCommandService.getTimeout(120L, osCommandConfiguration, sshConfiguration, 300))
				.thenCallRealMethod();
			mockedOsCommandService.when(() -> OsCommandService.getPassword(sshConfiguration)).thenCallRealMethod();
			mockedStringHelper.when(() -> StringHelper.protectCaseInsensitiveRegex(anyString())).thenCallRealMethod();
			mockedOsCommandHelper.when(() -> OsCommandHelper.getFileNameFromSudoCommand(anyString())).thenCallRealMethod();
			mockedOsCommandHelper
				.when(() -> OsCommandHelper.replaceSudo(anyString(), eq(sudoInformation)))
				.thenCallRealMethod();

			mockedOsCommandHelper
				.when(() ->
					OsCommandHelper.createOsCommandEmbeddedFiles(
						SH_EMBEDDED_FILE_1,
						sudoInformation,
						commandLineEmbeddedFiles,
						TEMP_FILE_CREATOR
					)
				)
				.thenReturn(embeddedTempFiles);

			mockedEmbeddedFileHelper
				.when(() -> EmbeddedFileHelper.findEmbeddedFiles(anyString(), anyMap()))
				.thenReturn(commandLineEmbeddedFiles);

			doReturn(SEN_EMBEDDED_0001_PATH).when(localFile).getAbsolutePath();

			mockedOsCommandHelper
				.when(() -> {
					OsCommandService.runSshCommand(
						SH_SEN_EMBEDDED_0001_PATH,
						HOST,
						sshConfiguration,
						120,
						List.of(localFile),
						SH_SEN_EMBEDDED_0001_PATH,
						DeviceKind.LINUX
					);
				})
				.thenReturn(HARD_DRIVE);

			mockedOsCommandHelper
				.when(() ->
					OsCommandService.runOsCommand(
						SH_EMBEDDED_FILE_1,
						telemetryManager,
						120L,
						false,
						false,
						commandLineEmbeddedFiles
					)
				)
				.thenCallRealMethod();

			final OsCommandResult expect = new OsCommandResult(HARD_DRIVE, SH_SEN_EMBEDDED_0001_PATH);
			final OsCommandResult actual = OsCommandService.runOsCommand(
				SH_EMBEDDED_FILE_1,
				telemetryManager,
				120L,
				false,
				false,
				commandLineEmbeddedFiles
			);
			assertEquals(expect, actual);
		}
	}

	@Test
	void testRunControlledSshCommand() throws InterruptedException, ControlledSshException {
		Semaphore semaphore = SshSemaphoreFactory.getInstance().createOrGetSempahore(HOSTNAME);

		assertEquals(7, OsCommandService.runControlledSshCommand(semaphore::availablePermits, HOSTNAME, 30));

		assertDoesNotThrow(() -> OsCommandService.runControlledSshCommand(LocalDate.MIN::toString, HOSTNAME, 30));

		semaphore.acquire(8);
		assertThrows(
			ControlledSshException.class,
			() -> OsCommandService.runControlledSshCommand(LocalDate.MIN::toString, HOSTNAME, 1)
		);
	}

	@Test
	void testRunControlledSshCommandClearsInterruptFlag() {
		Semaphore semaphore = SshSemaphoreFactory.getInstance().createOrGetSempahore(HOSTNAME);

		Thread.currentThread().interrupt();

		assertThrows(
			InterruptedException.class,
			() -> OsCommandService.runControlledSshCommand(LocalDate.MIN::toString, HOSTNAME, 30)
		);

		assertFalse(Thread.currentThread().isInterrupted());
	}
}
