package org.metricshub.extension.oscommand;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.metricshub.engine.common.helpers.KnownMonitorType.HOST;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;

import com.fasterxml.jackson.databind.node.BooleanNode;
import com.fasterxml.jackson.databind.node.IntNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.extension.ExtendWith;
import org.metricshub.engine.common.exception.ClientException;
import org.metricshub.engine.common.exception.ClientRuntimeException;
import org.metricshub.engine.common.exception.InvalidConfigurationException;
import org.metricshub.engine.common.exception.NoCredentialProvidedException;
import org.metricshub.engine.common.helpers.ResourceHelper;
import org.metricshub.engine.configuration.HostConfiguration;
import org.metricshub.engine.configuration.IConfiguration;
import org.metricshub.engine.connector.model.Connector;
import org.metricshub.engine.connector.model.ConnectorStore;
import org.metricshub.engine.connector.model.common.DeviceKind;
import org.metricshub.engine.connector.model.identity.criterion.CommandLineCriterion;
import org.metricshub.engine.connector.model.identity.criterion.IpmiCriterion;
import org.metricshub.engine.connector.model.monitor.task.source.CommandLineSource;
import org.metricshub.engine.connector.model.monitor.task.source.IpmiSource;
import org.metricshub.engine.strategy.detection.CriterionTestResult;
import org.metricshub.engine.strategy.source.SourceTable;
import org.metricshub.engine.strategy.utils.OsCommandResult;
import org.metricshub.engine.telemetry.HostProperties;
import org.metricshub.engine.telemetry.Monitor;
import org.metricshub.engine.telemetry.TelemetryManager;
import org.mockito.InjectMocks;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OsCommandExtensionTest {

	private static final String SUCCESS_RESPONSE = "success";
	private static final String HOSTNAME = "hostname";
	public static final String LOCALHOST = "localhost";
	public static final String MY_CONNECTOR_1_NAME = "myConnector1";
	public static final String UNIX_IPMI_FAIL_MESSAGE =
		"Hostname %s - " + "No OS command configuration for this host. Returning an empty result";
	public static final String TEST_COMMAND_LINE = "echo test";

	@InjectMocks
	private OsCommandExtension osCommandExtension;

	static Map<String, Map<String, Monitor>> monitors;

	@Test
	void testIsValidConfiguration() {
		assertTrue(osCommandExtension.isValidConfiguration(OsCommandConfiguration.builder().build()));
		assertTrue(osCommandExtension.isValidConfiguration(SshConfiguration.builder().build()));
		assertFalse(
			osCommandExtension.isValidConfiguration(
				new IConfiguration() {
					@Override
					public void validateConfiguration(String resourceKey) throws InvalidConfigurationException {}

					@Override
					public String getHostname() {
						return null;
					}

					@Override
					public void setHostname(String hostname) {}

					@Override
					public IConfiguration copy() {
						return null;
					}

					@Override
					public void setTimeout(Long timeout) {}
				}
			)
		);
	}

	@Test
	void testGetSupportedSources() {
		assertFalse(osCommandExtension.getSupportedSources().isEmpty());
		assertTrue(osCommandExtension.getSupportedSources().containsAll(Set.of(CommandLineSource.class, IpmiSource.class)));
	}

	@Test
	void testGetSupportedCriteria() {
		assertFalse(osCommandExtension.getSupportedCriteria().isEmpty());
		assertTrue(
			osCommandExtension.getSupportedCriteria().containsAll(Set.of(CommandLineCriterion.class, IpmiCriterion.class))
		);
	}

	@Test
	void testGetConfigurationToSourceMapping() {
		assertFalse(osCommandExtension.getConfigurationToSourceMapping().isEmpty());
	}

	@Test
	void testIsSupportedConfigurationType() {
		assertTrue(osCommandExtension.isSupportedConfigurationType("ssh"));
		assertTrue(osCommandExtension.isSupportedConfigurationType("oscommand"));
		assertFalse(osCommandExtension.isSupportedConfigurationType("http"));
	}

	@Test
	void testBuildConfiguration() throws InvalidConfigurationException {
		final ObjectNode osCommandconfiguration = JsonNodeFactory.instance.objectNode();
		osCommandconfiguration.set("useSudoCommands", JsonNodeFactory.instance.arrayNode().add("sudo"));
		osCommandconfiguration.set("useSudo", BooleanNode.TRUE);
		osCommandconfiguration.set("sudoCommand", new TextNode("sudo"));
		osCommandconfiguration.set("timeout", new TextNode("120"));

		assertEquals(
			OsCommandConfiguration
				.builder()
				.sudoCommand("sudo")
				.useSudo(true)
				.useSudoCommands(Set.of("sudo"))
				.timeout(120L)
				.build(),
			osCommandExtension.buildConfiguration("oscommand", osCommandconfiguration, value -> value)
		);
		assertEquals(
			OsCommandConfiguration
				.builder()
				.sudoCommand("sudo")
				.useSudo(true)
				.useSudoCommands(Set.of("sudo"))
				.timeout(120L)
				.build(),
			osCommandExtension.buildConfiguration("oscommand", osCommandconfiguration, null)
		);

		final ObjectNode sshConfiguration = JsonNodeFactory.instance.objectNode();
		sshConfiguration.set("username", new TextNode("username"));
		sshConfiguration.set("password", new TextNode("password"));
		sshConfiguration.set("timeout", new TextNode("120"));
		sshConfiguration.set("port", new IntNode(2222));
		sshConfiguration.set("privateKey", new TextNode("privateKey"));
		sshConfiguration.set("useSudoCommands", JsonNodeFactory.instance.arrayNode().add("sudo"));
		sshConfiguration.set("useSudo", BooleanNode.TRUE);
		sshConfiguration.set("sudoCommand", new TextNode("sudo"));
		sshConfiguration.set("timeout", new TextNode("120"));

		assertEquals(
			SshConfiguration
				.sshConfigurationBuilder()
				.username("username")
				.password("password".toCharArray())
				.timeout(120L)
				.port(2222)
				.privateKey("privateKey")
				.useSudoCommands(Set.of("sudo"))
				.useSudo(true)
				.sudoCommand("sudo")
				.timeout(120L)
				.build(),
			osCommandExtension.buildConfiguration("ssh", sshConfiguration, value -> value)
		);
		assertEquals(
			SshConfiguration
				.sshConfigurationBuilder()
				.username("username")
				.password("password".toCharArray())
				.timeout(120L)
				.port(2222)
				.privateKey("privateKey")
				.useSudoCommands(Set.of("sudo"))
				.useSudo(true)
				.sudoCommand("sudo")
				.timeout(120L)
				.build(),
			osCommandExtension.buildConfiguration("ssh", sshConfiguration, null)
		);
	}

	@Test
	void testBuildConfigurationKO() {
		{
			final ObjectNode sshConfiguration = JsonNodeFactory.instance.objectNode();
			sshConfiguration.set("timeout", new TextNode("NaN"));
			assertThrows(
				InvalidConfigurationException.class,
				() -> osCommandExtension.buildConfiguration("ssh", sshConfiguration, null)
			);
		}
		{
			final ObjectNode osCommandconfiguration = JsonNodeFactory.instance.objectNode();
			osCommandconfiguration.set("timeout", new TextNode("NaN"));
			assertThrows(
				InvalidConfigurationException.class,
				() -> osCommandExtension.buildConfiguration("oscommand", osCommandconfiguration, null)
			);
		}
		{
			final ObjectNode sshConfiguration = JsonNodeFactory.instance.objectNode();
			sshConfiguration.set("username", new TextNode("username"));
			sshConfiguration.set("password", new TextNode("password"));
			sshConfiguration.set("timeout", new TextNode("120"));
			sshConfiguration.set("privateKey", new TextNode("privateKey"));
			sshConfiguration.set("useSudoCommands", JsonNodeFactory.instance.arrayNode().add("sudo"));
			sshConfiguration.set("useSudo", BooleanNode.TRUE);
			sshConfiguration.set("sudoCommand", new TextNode("sudo"));
			sshConfiguration.set("timeout", new TextNode("120"));
			assertThrows(
				InvalidConfigurationException.class,
				() -> osCommandExtension.buildConfiguration("snmp", sshConfiguration, null)
			);
		}
	}

	void setup() {
		Monitor hostMonitor = Monitor.builder().type(HOST.getKey()).isEndpoint(true).build();
		monitors = new HashMap<>(Map.of(HOST.getKey(), Map.of(HOSTNAME, hostMonitor)));
	}

	/**
	 * Creates and returns a TelemetryManager instance with an SSH configuration.
	 *
	 * @return A TelemetryManager instance configured with an SSH configuration.
	 */
	private TelemetryManager createTelemetryManagerWithSshConfig() {
		// Create monitors
		setup();
		// Create a telemetry manager
		return TelemetryManager
			.builder()
			.monitors(monitors)
			.hostConfiguration(
				HostConfiguration
					.builder()
					.hostId(HOSTNAME)
					.hostname(HOSTNAME)
					.configurations(
						Map.of(
							SshConfiguration.class,
							SshConfiguration
								.sshConfigurationBuilder()
								.username("username")
								.password("password".toCharArray())
								.timeout(60L)
								.build()
						)
					)
					.build()
			)
			.build();
	}

	/**
	 * Creates and returns a TelemetryManager instance without any configuration.
	 *
	 * @return A TelemetryManager instance.
	 */
	private TelemetryManager createTelemetryManagerWithoutConfig() {
		// Create monitors
		setup();
		// Create a telemetry manager
		return TelemetryManager
			.builder()
			.monitors(monitors)
			.hostConfiguration(HostConfiguration.builder().hostId(HOSTNAME).hostname(HOSTNAME).build())
			.build();
	}

	@Test
	void testCheckSshHealthLocally() {
		// Create a telemetry manager using an SSH HostConfiguration.
		final TelemetryManager telemetryManager = createTelemetryManagerWithSshConfig();

		// Setting SSH host properties
		telemetryManager.getHostProperties().setMustCheckSshStatus(true);
		telemetryManager.getHostProperties().setOsCommandExecutesLocally(true);

		try (MockedStatic<OsCommandService> staticOsCommandHelper = Mockito.mockStatic(OsCommandService.class)) {
			staticOsCommandHelper
				.when(() -> OsCommandService.runLocalCommand(anyString(), anyLong(), any()))
				.thenReturn(SUCCESS_RESPONSE);

			// Start the SSH Health Check strategy
			Optional<Boolean> result = osCommandExtension.checkProtocol(telemetryManager);

			// Assert the result
			assertTrue(result.get());
		}

		try (MockedStatic<OsCommandService> staticOsCommandHelper = Mockito.mockStatic(OsCommandService.class)) {
			staticOsCommandHelper
				.when(() -> OsCommandService.runLocalCommand(anyString(), anyLong(), any()))
				.thenReturn(null);

			// Start the SSH Health Check strategy
			Optional<Boolean> result = osCommandExtension.checkProtocol(telemetryManager);

			// Assert the result
			assertFalse(result.get());
		}
	}

	@Test
	void testCheckSshUpHealthRemotely() {
		// Create a telemetry manager using an SSH HostConfiguration.
		final TelemetryManager telemetryManager = createTelemetryManagerWithSshConfig();

		// Setting SSH host properties
		telemetryManager.getHostProperties().setMustCheckSshStatus(true);
		telemetryManager.getHostProperties().setOsCommandExecutesLocally(false);
		telemetryManager.getHostProperties().setOsCommandExecutesRemotely(true);

		try (MockedStatic<OsCommandService> staticOsCommandHelper = Mockito.mockStatic(OsCommandService.class)) {
			staticOsCommandHelper
				.when(() ->
					OsCommandService.runSshCommand(anyString(), anyString(), any(SshConfiguration.class), anyLong(), any(), any())
				)
				.thenReturn(SUCCESS_RESPONSE);

			// Start the SSH Health Check strategy
			Optional<Boolean> result = osCommandExtension.checkProtocol(telemetryManager);

			// Assert the result
			assertTrue(result.get());
		}

		try (MockedStatic<OsCommandService> staticOsCommandHelper = Mockito.mockStatic(OsCommandService.class)) {
			staticOsCommandHelper
				.when(() ->
					OsCommandService.runSshCommand(anyString(), anyString(), any(SshConfiguration.class), anyLong(), any(), any())
				)
				.thenReturn(null);

			// Start the SSH Health Check strategy
			Optional<Boolean> result = osCommandExtension.checkProtocol(telemetryManager);

			// Assert the result
			assertFalse(result.get());
		}
	}

	@Test
	void testCheckSshUpHealthBothLocallyAndRemotely() {
		// Create a telemetry manager using an SSH HostConfiguration.
		final TelemetryManager telemetryManager = createTelemetryManagerWithSshConfig();

		// Setting SSH host properties
		telemetryManager.getHostProperties().setMustCheckSshStatus(true);
		telemetryManager.getHostProperties().setOsCommandExecutesLocally(true);
		telemetryManager.getHostProperties().setOsCommandExecutesRemotely(true);

		// Both local and remote commands working fine
		try (MockedStatic<OsCommandService> staticOsCommandHelper = Mockito.mockStatic(OsCommandService.class)) {
			staticOsCommandHelper
				.when(() ->
					OsCommandService.runSshCommand(anyString(), anyString(), any(SshConfiguration.class), anyLong(), any(), any())
				)
				.thenReturn(SUCCESS_RESPONSE);

			staticOsCommandHelper
				.when(() -> OsCommandService.runLocalCommand(anyString(), anyLong(), any()))
				.thenReturn(SUCCESS_RESPONSE);

			// Start the SSH Health Check strategy
			Optional<Boolean> result = osCommandExtension.checkProtocol(telemetryManager);

			// Assert the result
			assertTrue(result.get());
		}

		// Local commands not working
		try (MockedStatic<OsCommandService> staticOsCommandHelper = Mockito.mockStatic(OsCommandService.class)) {
			staticOsCommandHelper
				.when(() ->
					OsCommandService.runSshCommand(anyString(), anyString(), any(SshConfiguration.class), anyLong(), any(), any())
				)
				.thenReturn(SUCCESS_RESPONSE);

			staticOsCommandHelper
				.when(() -> OsCommandService.runLocalCommand(anyString(), anyLong(), any()))
				.thenReturn(null);

			// Start the SSH Health Check strategy
			Optional<Boolean> result = osCommandExtension.checkProtocol(telemetryManager);

			// Assert the result
			assertFalse(result.get());
		}
		// remote command not working
		try (MockedStatic<OsCommandService> staticOsCommandHelper = Mockito.mockStatic(OsCommandService.class)) {
			staticOsCommandHelper
				.when(() ->
					OsCommandService.runSshCommand(anyString(), anyString(), any(SshConfiguration.class), anyLong(), any(), any())
				)
				.thenReturn(null);

			staticOsCommandHelper
				.when(() -> OsCommandService.runLocalCommand(anyString(), anyLong(), any()))
				.thenReturn(SUCCESS_RESPONSE);

			// Start the SSH Health Check strategy
			Optional<Boolean> result = osCommandExtension.checkProtocol(telemetryManager);

			// Assert the result
			assertFalse(result.get());
		}

		// Both local and remote commands not working, but not throwing exceptions
		try (MockedStatic<OsCommandService> staticOsCommandHelper = Mockito.mockStatic(OsCommandService.class)) {
			staticOsCommandHelper
				.when(() ->
					OsCommandService.runSshCommand(anyString(), anyString(), any(SshConfiguration.class), anyLong(), any(), any())
				)
				.thenReturn(null);

			staticOsCommandHelper
				.when(() -> OsCommandService.runLocalCommand(anyString(), anyLong(), any()))
				.thenReturn(null);

			Optional<Boolean> result = osCommandExtension.checkProtocol(telemetryManager);

			// Assert the result
			assertFalse(result.get());
		}
	}

	@Test
	void testCheckSshNoHealthWhenMustCheckFalse() {
		// Create a telemetry manager using an SSH HostConfiguration.
		final TelemetryManager telemetryManager = createTelemetryManagerWithSshConfig();

		// Setting SSH host properties
		telemetryManager.getHostProperties().setMustCheckSshStatus(false);
		telemetryManager.getHostProperties().setOsCommandExecutesLocally(true);
		telemetryManager.getHostProperties().setOsCommandExecutesRemotely(true);

		// Start the SSH Health Check strategy
		Optional<Boolean> result = osCommandExtension.checkProtocol(telemetryManager);

		// Assert the result
		assertEquals(Optional.empty(), result);
	}

	@Test
	void testCheckSshNoHealthWhenNoConfiguration() {
		// Create a telemetry manager without configuration.
		final TelemetryManager telemetryManager = createTelemetryManagerWithoutConfig();

		// Setting SSH host properties
		telemetryManager.getHostProperties().setMustCheckSshStatus(false);
		telemetryManager.getHostProperties().setOsCommandExecutesLocally(true);
		telemetryManager.getHostProperties().setOsCommandExecutesRemotely(true);

		// Start the SSH Health Check strategy
		Optional<Boolean> result = osCommandExtension.checkProtocol(telemetryManager);

		// Assert the result
		assertEquals(Optional.empty(), result);
	}

	@Test
	void testProcessOsCommandSource() {
		final Connector connector = Connector.builder().build();

		final Map<String, Connector> store = Map.of(MY_CONNECTOR_1_NAME, connector);

		final ConnectorStore connectorStore = new ConnectorStore();
		connectorStore.setStore(store);

		final OsCommandConfiguration osCommandConfiguration = OsCommandConfiguration.builder().timeout(120L).build();

		final HostProperties hostProperties = HostProperties.builder().isLocalhost(true).build();

		final TelemetryManager telemetryManager = TelemetryManager
			.builder()
			.hostConfiguration(
				HostConfiguration
					.builder()
					.hostname(LOCALHOST)
					.hostId(LOCALHOST)
					.hostType(DeviceKind.LINUX)
					.configurations(Map.of(OsCommandConfiguration.class, osCommandConfiguration))
					.build()
			)
			.connectorStore(connectorStore)
			.hostProperties(hostProperties)
			.build();

		assertThrows(
			IllegalArgumentException.class,
			() -> osCommandExtension.processSource((CommandLineSource) null, MY_CONNECTOR_1_NAME, telemetryManager)
		);
		assertEquals(
			SourceTable.empty(),
			osCommandExtension.processSource(new CommandLineSource(), MY_CONNECTOR_1_NAME, telemetryManager)
		);
		assertEquals(
			SourceTable.empty(),
			osCommandExtension.processSource(
				CommandLineSource.builder().commandLine("").build(),
				MY_CONNECTOR_1_NAME,
				telemetryManager
			)
		);

		final String commandLine = "/usr/sbin/ioscan -kFC ext_bus";
		final String keepOnlyRegExp = ":ext_bus:";
		final String separators = ":";
		final String selectColumns = "2-4,5,6";

		final CommandLineSource commandSource = new CommandLineSource();
		commandSource.setCommandLine(commandLine);
		commandSource.setKeep(keepOnlyRegExp);
		commandSource.setSeparators(separators);
		commandSource.setSelectColumns(selectColumns);
		commandSource.setExecuteLocally(true);

		try (final MockedStatic<OsCommandService> mockedOsCommandHelper = mockStatic(OsCommandService.class)) {
			mockedOsCommandHelper
				.when(() ->
					OsCommandService.runOsCommand(
						commandLine,
						telemetryManager,
						commandSource.getTimeout(),
						commandSource.getExecuteLocally(),
						hostProperties.isLocalhost(),
						Map.of()
					)
				)
				.thenThrow(NoCredentialProvidedException.class);

			assertEquals(
				SourceTable.empty(),
				osCommandExtension.processSource(commandSource, MY_CONNECTOR_1_NAME, telemetryManager)
			);
		}

		try (final MockedStatic<OsCommandService> mockedOsCommandHelper = mockStatic(OsCommandService.class)) {
			mockedOsCommandHelper
				.when(() ->
					OsCommandService.runOsCommand(
						commandLine,
						telemetryManager,
						commandSource.getTimeout(),
						commandSource.getExecuteLocally(),
						hostProperties.isLocalhost(),
						Map.of()
					)
				)
				.thenThrow(IOException.class);

			assertEquals(
				SourceTable.empty(),
				osCommandExtension.processSource(commandSource, MY_CONNECTOR_1_NAME, telemetryManager)
			);
		}

		try (final MockedStatic<OsCommandService> mockedOsCommandHelper = mockStatic(OsCommandService.class)) {
			final String result = "xxxxxx\n" + "xxxxxx\n" + "0:1:ext_bus:3:4:5:6:7:8\n" + "xxxxxx\n" + "xxxxxx\n";
			final OsCommandResult commandResult = new OsCommandResult(result, commandLine);

			mockedOsCommandHelper
				.when(() ->
					OsCommandService.runOsCommand(
						commandLine,
						telemetryManager,
						commandSource.getTimeout(),
						commandSource.getExecuteLocally(),
						hostProperties.isLocalhost(),
						Map.of()
					)
				)
				.thenReturn(commandResult);

			final SourceTable expected = SourceTable
				.builder()
				.rawData("1;ext_bus;3;4;5")
				.table(List.of(List.of("1", "ext_bus", "3", "4", "5")))
				.build();
			assertEquals(expected, osCommandExtension.processSource(commandSource, MY_CONNECTOR_1_NAME, telemetryManager));
		}
	}

	@Test
	void testProcessUnixIpmiCriterionWrongCommand() {
		// classic case
		final SshConfiguration sshConfiguration = SshConfiguration
			.sshConfigurationBuilder()
			.username("root")
			.password("nationale".toCharArray())
			.build();
		final HostConfiguration hostConfiguration = HostConfiguration
			.builder()
			.hostname("localhost")
			.hostId("localhost")
			.hostType(DeviceKind.LINUX)
			.configurations(
				Map.of(
					OsCommandConfiguration.class,
					OsCommandConfiguration.builder().build(),
					SshConfiguration.class,
					sshConfiguration
				)
			)
			.build();

		final HostProperties hostProperties = HostProperties
			.builder()
			.isLocalhost(true)
			.ipmitoolCommand("ipmiCommand")
			.build();

		final TelemetryManager telemetryManager = TelemetryManager
			.builder()
			.hostConfiguration(hostConfiguration)
			.hostProperties(hostProperties)
			.build();

		IpmiCriterion ipmiCriterion = new IpmiCriterion("WRONG Command", false);

		final CriterionTestResult expected = CriterionTestResult
			.builder()
			.success(false)
			.message("ipmiCommand bmc info")
			.result("")
			.build();

		assertEquals(expected, osCommandExtension.processCriterion(ipmiCriterion, MY_CONNECTOR_1_NAME, telemetryManager));
	}

	@Test
	void testProcessUnixIpmiCriterionNoOsCommandConfig() {
		// classic case
		final SshConfiguration sshConfiguration = SshConfiguration
			.sshConfigurationBuilder()
			.username("root")
			.password("nationale".toCharArray())
			.build();
		final HostConfiguration hostConfiguration = HostConfiguration
			.builder()
			.hostname("localhost")
			.hostId("localhost")
			.hostType(DeviceKind.LINUX)
			.configurations(Map.of(SshConfiguration.class, sshConfiguration))
			.build();

		final HostProperties hostProperties = HostProperties
			.builder()
			.isLocalhost(true)
			.ipmitoolCommand("ipmiCommand")
			.build();

		final TelemetryManager telemetryManager = TelemetryManager
			.builder()
			.hostConfiguration(hostConfiguration)
			.hostProperties(hostProperties)
			.build();

		IpmiCriterion ipmiCriterion = new IpmiCriterion("WRONG Command", false);

		final CriterionTestResult expected = CriterionTestResult
			.builder()
			.success(false)
			.message(String.format(UNIX_IPMI_FAIL_MESSAGE, telemetryManager.getHostname()))
			.result("")
			.build();

		assertEquals(expected, osCommandExtension.processCriterion(ipmiCriterion, MY_CONNECTOR_1_NAME, telemetryManager));
	}

	@Test
	void testProcessUnixIpmiCriterionException() {
		// classic case
		final SshConfiguration sshConfiguration = SshConfiguration
			.sshConfigurationBuilder()
			.username("root")
			.password("nationale".toCharArray())
			.build();
		final HostConfiguration hostConfiguration = HostConfiguration
			.builder()
			.hostname("localhost")
			.hostId("localhost")
			.hostType(DeviceKind.LINUX)
			.configurations(
				Map.of(
					OsCommandConfiguration.class,
					OsCommandConfiguration.builder().build(),
					SshConfiguration.class,
					sshConfiguration
				)
			)
			.build();

		final HostProperties hostProperties = HostProperties
			.builder()
			.isLocalhost(true)
			.ipmitoolCommand("ipmiCommand")
			.build();

		final TelemetryManager telemetryManager = TelemetryManager
			.builder()
			.hostConfiguration(hostConfiguration)
			.hostProperties(hostProperties)
			.build();

		IpmiCriterion ipmiCriterion = new IpmiCriterion("WRONG Command", false);

		final CriterionTestResult expected = CriterionTestResult
			.builder()
			.success(false)
			.message("fail")
			.result("")
			.build();

		assertEquals(
			expected.getResult(),
			osCommandExtension.processCriterion(ipmiCriterion, MY_CONNECTOR_1_NAME, telemetryManager).getResult()
		);
	}

	@Test
	void testProcessUnixIpmiCriterionLocal() {
		// classic case
		final SshConfiguration sshConfiguration = SshConfiguration
			.sshConfigurationBuilder()
			.username("root")
			.password("nationale".toCharArray())
			.build();
		final HostConfiguration hostConfiguration = HostConfiguration
			.builder()
			.hostname("localhost")
			.hostId("localhost")
			.hostType(DeviceKind.LINUX)
			.configurations(
				Map.of(
					OsCommandConfiguration.class,
					OsCommandConfiguration.builder().build(),
					SshConfiguration.class,
					sshConfiguration
				)
			)
			.build();

		final HostProperties hostProperties = HostProperties
			.builder()
			.isLocalhost(true)
			.ipmitoolCommand("PATH= command")
			.build();

		final TelemetryManager telemetryManager = TelemetryManager
			.builder()
			.hostConfiguration(hostConfiguration)
			.hostProperties(hostProperties)
			.build();

		IpmiCriterion ipmiCriterion = new IpmiCriterion("ipmi", false);

		// Mock OsCommandHelper.runLocalCommand if local
		try (MockedStatic<OsCommandService> oscmd = mockStatic(OsCommandService.class)) {
			oscmd.when(() -> OsCommandService.runLocalCommand(anyString(), anyLong(), eq(null))).thenReturn("IPMI Version");

			final CriterionTestResult expected = CriterionTestResult
				.builder()
				.success(true)
				.result("IPMI Version")
				.message("Successfully connected to the IPMI BMC chip with the in-band driver interface.")
				.build();

			assertEquals(expected, osCommandExtension.processCriterion(ipmiCriterion, MY_CONNECTOR_1_NAME, telemetryManager));
		}
	}

	@Test
	void testProcessUnixIpmiCriterionRemote() {
		// classic case
		final SshConfiguration sshConfiguration = SshConfiguration
			.sshConfigurationBuilder()
			.username("root")
			.password("nationale".toCharArray())
			.build();
		final HostConfiguration hostConfiguration = HostConfiguration
			.builder()
			.hostname("localhost")
			.hostId("localhost")
			.hostType(DeviceKind.LINUX)
			.configurations(
				Map.of(
					OsCommandConfiguration.class,
					OsCommandConfiguration.builder().build(),
					SshConfiguration.class,
					sshConfiguration
				)
			)
			.build();

		final HostProperties hostProperties = HostProperties
			.builder()
			.isLocalhost(false)
			.ipmitoolCommand("PATH= command")
			.build();

		final TelemetryManager telemetryManager = TelemetryManager
			.builder()
			.hostConfiguration(hostConfiguration)
			.hostProperties(hostProperties)
			.build();

		IpmiCriterion ipmiCriterion = new IpmiCriterion("ipmi", false);

		// Mock OsCommandHelper.runLocalCommand if local
		try (MockedStatic<OsCommandService> oscmd = mockStatic(OsCommandService.class)) {
			oscmd
				.when(() -> OsCommandService.runSshCommand(eq("PATH= command bmc info"), any(), any(), anyLong(), any(), any()))
				.thenReturn("IPMI Version");

			final CriterionTestResult expected = CriterionTestResult
				.builder()
				.success(true)
				.result("IPMI Version")
				.message("Successfully connected to the IPMI BMC chip with the in-band driver interface.")
				.build();

			assertEquals(expected, osCommandExtension.processCriterion(ipmiCriterion, MY_CONNECTOR_1_NAME, telemetryManager));
		}
	}

	@Test
	void testProcessUnixIpmiCriterionRemoteFail() {
		// classic case
		final SshConfiguration sshConfiguration = SshConfiguration
			.sshConfigurationBuilder()
			.username("root")
			.password("nationale".toCharArray())
			.build();
		final HostConfiguration hostConfiguration = HostConfiguration
			.builder()
			.hostname("localhost")
			.hostId("localhost")
			.hostType(DeviceKind.LINUX)
			.configurations(
				Map.of(
					OsCommandConfiguration.class,
					OsCommandConfiguration.builder().build(),
					SshConfiguration.class,
					sshConfiguration
				)
			)
			.build();

		final HostProperties hostProperties = HostProperties
			.builder()
			.isLocalhost(false)
			.ipmitoolCommand("PATH= command")
			.build();

		final TelemetryManager telemetryManager = TelemetryManager
			.builder()
			.hostConfiguration(hostConfiguration)
			.hostProperties(hostProperties)
			.build();

		IpmiCriterion ipmiCriterion = new IpmiCriterion("ipmi", false);

		// Mock OsCommandHelper.runLocalCommand if local
		try (MockedStatic<OsCommandService> oscmd = mockStatic(OsCommandService.class)) {
			oscmd
				.when(() -> OsCommandService.runSshCommand(eq("PATH= command bmc info"), any(), any(), anyLong(), any(), any()))
				.thenThrow(ClientRuntimeException.class);

			final CriterionTestResult expected = CriterionTestResult
				.builder()
				.success(false)
				.result(null)
				.message("Hostname localhost - Cannot execute the IPMI tool command PATH= command bmc info. Exception: null.")
				.build();

			assertEquals(expected, osCommandExtension.processCriterion(ipmiCriterion, MY_CONNECTOR_1_NAME, telemetryManager));
		}
	}

	@Test
	void testProcessUnixIpmiSource() {
		// classic case
		final SshConfiguration sshConfiguration = SshConfiguration
			.sshConfigurationBuilder()
			.username("root")
			.password("nationale".toCharArray())
			.build();
		final HostConfiguration hostConfiguration = HostConfiguration
			.builder()
			.hostname("localhost")
			.hostId("localhost")
			.hostType(DeviceKind.LINUX)
			.configurations(
				Map.of(IConfiguration.class, OsCommandConfiguration.builder().build(), SshConfiguration.class, sshConfiguration)
			)
			.build();

		final HostProperties hostProperties = HostProperties
			.builder()
			.isLocalhost(true)
			.ipmitoolCommand("ipmiCommand")
			.build();
		final TelemetryManager telemetryManager = TelemetryManager
			.builder()
			.hostConfiguration(hostConfiguration)
			.hostProperties(hostProperties)
			.build();

		// local
		try (MockedStatic<OsCommandService> oscmd = mockStatic(OsCommandService.class)) {
			oscmd
				.when(() -> OsCommandService.runLocalCommand(eq("ipmiCommand fru"), anyLong(), eq(null)))
				.thenReturn("impiResultFru");
			oscmd
				.when(() -> OsCommandService.runLocalCommand(eq("ipmiCommand -v sdr elist all"), anyLong(), eq(null)))
				.thenReturn("impiResultSdr");
			final SourceTable ipmiResult = osCommandExtension.processSource(
				new IpmiSource(),
				MY_CONNECTOR_1_NAME,
				telemetryManager
			);
			assertEquals(SourceTable.empty(), ipmiResult);
		}

		String fru = "/data/IpmiFruBabbage";
		String sensor = "/data/IpmiSensorBabbage";
		String expected = "/data/ipmiProcessingResult";
		String fruResult = ResourceHelper.getResourceAsString(fru, this.getClass());
		String sensorResult = ResourceHelper.getResourceAsString(sensor, this.getClass());

		try (MockedStatic<OsCommandService> oscmd = mockStatic(OsCommandService.class)) {
			oscmd.when(() -> OsCommandService.runLocalCommand(eq("ipmiCommand fru"), anyLong(), any())).thenReturn(fruResult);
			oscmd
				.when(() -> OsCommandService.runLocalCommand(eq("ipmiCommand -v sdr elist all"), anyLong(), any()))
				.thenReturn(sensorResult);
			final SourceTable ipmiResult = osCommandExtension.processSource(
				new IpmiSource(),
				MY_CONNECTOR_1_NAME,
				telemetryManager
			);
			String expectedResult = ResourceHelper.getResourceAsString(expected, this.getClass());
			List<List<String>> result = new ArrayList<>();
			Stream.of(expectedResult.split("\n")).forEach(line -> result.add(Arrays.asList(line.split(";"))));
			assertEquals(result, ipmiResult.getTable());
		}

		// remote
		hostProperties.setLocalhost(false);

		try (MockedStatic<OsCommandService> oscmd = mockStatic(OsCommandService.class)) {
			oscmd
				.when(() -> OsCommandService.runSshCommand(eq("ipmiCommand fru"), any(), any(), anyLong(), any(), any()))
				.thenReturn("impiResultFru");
			oscmd
				.when(() ->
					OsCommandService.runSshCommand(eq("ipmiCommand -v sdr elist all"), any(), any(), anyLong(), any(), any())
				)
				.thenReturn("impiResultSdr");
			final SourceTable ipmiResult = osCommandExtension.processSource(
				new IpmiSource(),
				MY_CONNECTOR_1_NAME,
				telemetryManager
			);
			assertEquals(SourceTable.empty(), ipmiResult);
		}

		// ipmiToolCommand is empty
		hostProperties.setIpmitoolCommand("");
		SourceTable ipmiResultEmpty = osCommandExtension.processSource(
			new IpmiSource(),
			MY_CONNECTOR_1_NAME,
			telemetryManager
		);
		assertEquals(SourceTable.empty(), ipmiResultEmpty);

		// ipmiToolCommand is null
		hostProperties.setIpmitoolCommand(null);
		ipmiResultEmpty = osCommandExtension.processSource(new IpmiSource(), MY_CONNECTOR_1_NAME, telemetryManager);
		assertEquals(SourceTable.empty(), ipmiResultEmpty);

		// osCommandConfig is null
		hostProperties.setLocalhost(true);
		hostProperties.setIpmitoolCommand("ipmiCommand");
		ipmiResultEmpty = osCommandExtension.processSource(new IpmiSource(), MY_CONNECTOR_1_NAME, telemetryManager);
		assertEquals(SourceTable.empty(), ipmiResultEmpty);
	}

	@Test
	void testProcessCommandLineExpectedResultEmpty() {
		final CommandLineCriterion commandLineCriterion = new CommandLineCriterion();
		commandLineCriterion.setCommandLine(
			"naviseccli -User %{USERNAME} -Password %{PASSWORD} -Address %{HOSTNAME} -Scope 1 getagent"
		);
		commandLineCriterion.setExpectedResult("");
		commandLineCriterion.setExecuteLocally(true);
		commandLineCriterion.setErrorMessage("Unable to connect using Navisphere");

		final TelemetryManager telemetryManager = TelemetryManager.builder().build();

		final CriterionTestResult criterionTestResult = osCommandExtension.processCriterion(
			commandLineCriterion,
			MY_CONNECTOR_1_NAME,
			telemetryManager
		);

		assertNotNull(criterionTestResult);
		assertTrue(criterionTestResult.isSuccess());
		assertEquals(
			"CommandLineCriterion test succeeded:\n" +
			commandLineCriterion.toString() +
			"\n\n" +
			"Result: CommandLine or ExpectedResult are empty. Skipping this test.",
			criterionTestResult.getMessage()
		);
		assertEquals("CommandLine or ExpectedResult are empty. Skipping this test.", criterionTestResult.getResult());
	}

	@Test
	void testProcessCommandLineNull() {
		final CommandLineCriterion commandLineCriterion = null;

		final HostConfiguration hostConfiguration = HostConfiguration.builder().hostname(HOSTNAME).build();
		final TelemetryManager telemetryManager = TelemetryManager.builder().hostConfiguration(hostConfiguration).build();

		assertThrows(
			IllegalArgumentException.class,
			() -> osCommandExtension.processCriterion(commandLineCriterion, MY_CONNECTOR_1_NAME, telemetryManager)
		);
	}

	@Test
	void testProcessCommandLineExpectedResultNull() {
		final CommandLineCriterion commandLineCriterion = new CommandLineCriterion();
		commandLineCriterion.setCommandLine(
			"naviseccli -User %{USERNAME} -Password %{PASSWORD} -Address %{HOSTNAME} -Scope 1 getagent"
		);
		commandLineCriterion.setExecuteLocally(true);
		commandLineCriterion.setErrorMessage("Unable to connect using Navisphere");

		final TelemetryManager telemetryManager = TelemetryManager.builder().build();

		final CriterionTestResult criterionTestResult = osCommandExtension.processCriterion(
			commandLineCriterion,
			MY_CONNECTOR_1_NAME,
			telemetryManager
		);

		assertNotNull(criterionTestResult);
		assertTrue(criterionTestResult.isSuccess());
		assertEquals(
			"CommandLineCriterion test succeeded:\n" +
			commandLineCriterion.toString() +
			"\n\n" +
			"Result: CommandLine or ExpectedResult are empty. Skipping this test.",
			criterionTestResult.getMessage()
		);
		assertEquals("CommandLine or ExpectedResult are empty. Skipping this test.", criterionTestResult.getResult());
	}

	@Test
	void testProcessCommandLineLineEmpty() {
		final CommandLineCriterion commandLineCriterion = new CommandLineCriterion();
		commandLineCriterion.setCommandLine("");
		commandLineCriterion.setExpectedResult("Agent Rev:");
		commandLineCriterion.setExecuteLocally(true);
		commandLineCriterion.setErrorMessage("Unable to connect using Navisphere");

		final TelemetryManager telemetryManager = TelemetryManager.builder().build();

		final CriterionTestResult criterionTestResult = osCommandExtension.processCriterion(
			commandLineCriterion,
			MY_CONNECTOR_1_NAME,
			telemetryManager
		);

		assertNotNull(criterionTestResult);
		assertTrue(criterionTestResult.isSuccess());
		assertEquals(
			"CommandLineCriterion test succeeded:\n" +
			commandLineCriterion.toString() +
			"\n\n" +
			"Result: CommandLine or ExpectedResult are empty. Skipping this test.",
			criterionTestResult.getMessage()
		);
		assertEquals("CommandLine or ExpectedResult are empty. Skipping this test.", criterionTestResult.getResult());
	}

	@Test
	void testProcessCommandLineRemoteNoUser() {
		final Connector connector = Connector.builder().build();

		final Map<String, Connector> store = Map.of(MY_CONNECTOR_1_NAME, connector);

		final ConnectorStore connectorStore = new ConnectorStore();
		connectorStore.setStore(store);

		final CommandLineCriterion commandLineCriterion = new CommandLineCriterion();
		commandLineCriterion.setCommandLine(
			"naviseccli -User %{USERNAME} -Password %{PASSWORD} -Address %{HOSTNAME} -Scope 1 getagent"
		);
		commandLineCriterion.setExpectedResult("Agent Rev:");
		commandLineCriterion.setExecuteLocally(false);
		commandLineCriterion.setErrorMessage("Unable to connect using Navisphere");

		final SshConfiguration sshConfiguration = SshConfiguration
			.sshConfigurationBuilder()
			.username(" ")
			.password("pwd".toCharArray())
			.build();

		final HostConfiguration hostConfiguration = HostConfiguration
			.builder()
			.hostId("id")
			.hostname("host")
			.hostType(DeviceKind.LINUX)
			.configurations(Map.of(sshConfiguration.getClass(), sshConfiguration))
			.build();

		final HostProperties hostProperties = HostProperties.builder().isLocalhost(false).build();

		final TelemetryManager telemetryManager = TelemetryManager
			.builder()
			.hostConfiguration(hostConfiguration)
			.hostProperties(hostProperties)
			.connectorStore(connectorStore)
			.build();

		final CriterionTestResult criterionTestResult = osCommandExtension.processCriterion(
			commandLineCriterion,
			MY_CONNECTOR_1_NAME,
			telemetryManager
		);

		assertNotNull(criterionTestResult);
		assertFalse(criterionTestResult.isSuccess());
		assertEquals(
			"Error in CommandLineCriterion test:\n" + commandLineCriterion.toString() + "\n\n" + "No credentials provided.",
			criterionTestResult.getMessage()
		);
		assertNull(criterionTestResult.getResult());
	}

	@Test
	@EnabledOnOs(OS.LINUX)
	void testProcessCommandLineLinuxError() {
		final Connector connector = Connector.builder().build();

		final Map<String, Connector> store = Map.of(MY_CONNECTOR_1_NAME, connector);

		final ConnectorStore connectorStore = new ConnectorStore();
		connectorStore.setStore(store);

		final CommandLineCriterion commandLineCriterion = new CommandLineCriterion();
		commandLineCriterion.setCommandLine("sleep 5");
		commandLineCriterion.setExpectedResult(" ");
		commandLineCriterion.setExecuteLocally(true);
		commandLineCriterion.setErrorMessage("No date.");
		commandLineCriterion.setTimeout(1L);

		final SshConfiguration sshConfiguration = SshConfiguration
			.sshConfigurationBuilder()
			.username(" ")
			.password("pwd".toCharArray())
			.build();

		final OsCommandConfiguration osCommandConfiguration = new OsCommandConfiguration();
		osCommandConfiguration.setTimeout(1L);

		final HostConfiguration hostConfiguration = HostConfiguration
			.builder()
			.hostId("id")
			.hostname("localhost")
			.hostType(DeviceKind.WINDOWS)
			.configurations(
				Map.of(sshConfiguration.getClass(), sshConfiguration, osCommandConfiguration.getClass(), osCommandConfiguration)
			)
			.build();

		final HostProperties hostProperties = HostProperties.builder().isLocalhost(true).build();

		final TelemetryManager telemetryManager = TelemetryManager
			.builder()
			.hostConfiguration(hostConfiguration)
			.hostProperties(hostProperties)
			.connectorStore(connectorStore)
			.build();

		final CriterionTestResult criterionTestResult = osCommandExtension.processCriterion(
			commandLineCriterion,
			MY_CONNECTOR_1_NAME,
			telemetryManager
		);

		assertNotNull(criterionTestResult);
		assertFalse(criterionTestResult.isSuccess());
		assertEquals(
			"Error in CommandLineCriterion test:\n" +
			commandLineCriterion.toString() +
			"\n\n" +
			"TimeoutException: Command \"sleep 5\" execution has timed out after 1 s",
			criterionTestResult.getMessage()
		);
		assertNull(criterionTestResult.getResult());
	}

	@Test
	@EnabledOnOs(OS.LINUX)
	void testProcessCommandLineLocalLinuxFailedToMatchCriteria() {
		final Connector connector = Connector.builder().build();

		final Map<String, Connector> store = Map.of(MY_CONNECTOR_1_NAME, connector);

		final ConnectorStore connectorStore = new ConnectorStore();
		connectorStore.setStore(store);

		final String result = "Test";

		final CommandLineCriterion commandLineCriterion = new CommandLineCriterion();
		commandLineCriterion.setCommandLine("echo Test");
		commandLineCriterion.setExpectedResult("Nothing");
		commandLineCriterion.setExecuteLocally(true);
		commandLineCriterion.setErrorMessage("No display.");

		final SshConfiguration sshConfiguration = SshConfiguration
			.sshConfigurationBuilder()
			.username(" ")
			.password("pwd".toCharArray())
			.timeout(1L)
			.build();

		final HostConfiguration hostConfiguration = HostConfiguration
			.builder()
			.hostId("id")
			.hostname("localhost")
			.hostType(DeviceKind.LINUX)
			.configurations(Map.of(sshConfiguration.getClass(), sshConfiguration))
			.build();

		final HostProperties hostProperties = HostProperties.builder().isLocalhost(true).build();

		final TelemetryManager telemetryManager = TelemetryManager
			.builder()
			.hostConfiguration(hostConfiguration)
			.hostProperties(hostProperties)
			.connectorStore(connectorStore)
			.build();

		final CriterionTestResult criterionTestResult = osCommandExtension.processCriterion(
			commandLineCriterion,
			MY_CONNECTOR_1_NAME,
			telemetryManager
		);

		assertNotNull(criterionTestResult);
		assertFalse(criterionTestResult.isSuccess());
		assertEquals(
			"CommandLineCriterion test ran but failed:\n" +
			commandLineCriterion.toString() +
			"\n\n" +
			"Actual result:\n" +
			result,
			criterionTestResult.getMessage()
		);
		assertEquals(result, criterionTestResult.getResult());
	}

	@Test
	@EnabledOnOs(OS.LINUX)
	void testProcessCommandLineLocalLinux() {
		final Connector connector = Connector.builder().build();

		final Map<String, Connector> store = Map.of(MY_CONNECTOR_1_NAME, connector);

		final ConnectorStore connectorStore = new ConnectorStore();
		connectorStore.setStore(store);

		final String result = "Test";

		final CommandLineCriterion commandLineCriterion = new CommandLineCriterion();
		commandLineCriterion.setCommandLine("echo Test");
		commandLineCriterion.setExpectedResult(result);
		commandLineCriterion.setExecuteLocally(true);
		commandLineCriterion.setErrorMessage("No display.");

		final SshConfiguration sshConfiguration = SshConfiguration
			.sshConfigurationBuilder()
			.username(" ")
			.password("pwd".toCharArray())
			.timeout(1L)
			.build();

		final HostConfiguration hostConfiguration = HostConfiguration
			.builder()
			.hostId("id")
			.hostname("localhost")
			.hostType(DeviceKind.LINUX)
			.configurations(Map.of(sshConfiguration.getClass(), sshConfiguration))
			.build();

		final HostProperties hostProperties = HostProperties.builder().isLocalhost(true).build();

		final TelemetryManager telemetryManager = TelemetryManager
			.builder()
			.hostConfiguration(hostConfiguration)
			.hostProperties(hostProperties)
			.connectorStore(connectorStore)
			.build();

		final CriterionTestResult criterionTestResult = osCommandExtension.processCriterion(
			commandLineCriterion,
			MY_CONNECTOR_1_NAME,
			telemetryManager
		);

		assertNotNull(criterionTestResult);
		assertTrue(criterionTestResult.isSuccess());
		assertEquals(
			"CommandLineCriterion test succeeded:\n" + commandLineCriterion.toString() + "\n\n" + "Result: " + result,
			criterionTestResult.getMessage()
		);
		assertEquals(result, criterionTestResult.getResult());
	}

	@Test
	void testExecuteQuery() throws Exception {
		final SshConfiguration sshConfiguration = SshConfiguration.sshConfigurationBuilder().hostname(HOSTNAME).build();

		final ObjectNode queryNode = JsonNodeFactory.instance.objectNode();
		queryNode.set("commandLine", new TextNode(TEST_COMMAND_LINE));

		try (MockedStatic<OsCommandService> staticOsCommandHelper = Mockito.mockStatic(OsCommandService.class)) {
			staticOsCommandHelper
				.when(() ->
					OsCommandService.runSshCommand(
						anyString(), // commandLine
						anyString(), // hostname
						any(SshConfiguration.class), // sshConfiguration
						anyLong(), // timeout
						any(), // local files
						anyString() // no password command
					)
				)
				.thenReturn(SUCCESS_RESPONSE);

			// Start the SSH Health Check strategy
			final String result = osCommandExtension.executeQuery(sshConfiguration, queryNode, null);

			// Assert the result
			assertEquals(SUCCESS_RESPONSE, result);
		}
	}

	@Test
	void testExecuteQueryThrowsException() throws Exception {
		final SshConfiguration sshConfiguration = SshConfiguration.sshConfigurationBuilder().hostname(HOSTNAME).build();

		final ObjectNode queryNode = JsonNodeFactory.instance.objectNode();
		queryNode.set("commandLine", new TextNode(TEST_COMMAND_LINE));

		try (MockedStatic<OsCommandService> staticOsCommandHelper = Mockito.mockStatic(OsCommandService.class)) {
			staticOsCommandHelper
				.when(() ->
					OsCommandService.runSshCommand(
						anyString(), // commandLine
						anyString(), // hostname
						any(SshConfiguration.class), // sshConfiguration
						anyLong(), // timeout
						any(), // local files
						anyString() // no password command
					)
				)
				.thenThrow(ClientException.class);

			// Assert the result
			assertThrows(ClientException.class, () -> osCommandExtension.executeQuery(sshConfiguration, queryNode, null));
		}
	}
}
