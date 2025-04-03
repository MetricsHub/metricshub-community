package org.metricshub.extension.win.detection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.metricshub.extension.win.IWinConfiguration;
import org.metricshub.extension.win.WinCommandService;
import org.metricshub.extension.win.WmiTestConfiguration;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.sentrysoftware.metricshub.engine.common.exception.NoCredentialProvidedException;
import org.sentrysoftware.metricshub.engine.configuration.HostConfiguration;
import org.sentrysoftware.metricshub.engine.connector.model.Connector;
import org.sentrysoftware.metricshub.engine.connector.model.ConnectorStore;
import org.sentrysoftware.metricshub.engine.connector.model.common.DeviceKind;
import org.sentrysoftware.metricshub.engine.connector.model.identity.criterion.CommandLineCriterion;
import org.sentrysoftware.metricshub.engine.strategy.detection.CriterionTestResult;
import org.sentrysoftware.metricshub.engine.strategy.utils.OsCommandResult;
import org.sentrysoftware.metricshub.engine.telemetry.HostProperties;
import org.sentrysoftware.metricshub.engine.telemetry.TelemetryManager;

@ExtendWith(MockitoExtension.class)
class WinCommandLineCriterionProcessorTest {

	private static final String COMMAND_LINE =
		"naviseccli -User %{USERNAME} -Password %{PASSWORD} -Address %{HOSTNAME} -Scope 1 getagent";

	private static final String CONNECTOR_ID = "connectorId";

	@Mock
	WinCommandService winCommandServiceMock;

	@Mock
	Function<TelemetryManager, IWinConfiguration> configurationRetrieverMock;

	WinCommandLineCriterionProcessor winCommandLineCriterionProcessor;

	@BeforeEach
	void setup() {
		winCommandLineCriterionProcessor =
			new WinCommandLineCriterionProcessor(winCommandServiceMock, configurationRetrieverMock, CONNECTOR_ID);
	}

	private static final String HOST_NAME = "test-host" + UUID.randomUUID().toString();
	private static final char[] PASSWORD = "pwd".toCharArray();
	private static final String USERNAME = "user";

	@Test
	void testProcessCommandLineCriterionExpectedResultEmpty() {
		final CommandLineCriterion commandLineCriterion = new CommandLineCriterion();
		commandLineCriterion.setCommandLine(COMMAND_LINE);
		commandLineCriterion.setExpectedResult("");
		commandLineCriterion.setExecuteLocally(false);
		commandLineCriterion.setErrorMessage("Unable to connect using Navisphere");

		final TelemetryManager telemetryManager = TelemetryManager.builder().build();

		final CriterionTestResult criterionTestResult = winCommandLineCriterionProcessor.process(
			commandLineCriterion,
			telemetryManager
		);

		assertNotNull(criterionTestResult);
		assertTrue(criterionTestResult.isSuccess());
		assertEquals("CommandLine or ExpectedResult are empty. Skipping this test.", criterionTestResult.getResult());
	}

	@Test
	void testProcessCommandLineCriterionExecuteLocallyTrue() {
		final CommandLineCriterion commandLineCriterion = new CommandLineCriterion();
		commandLineCriterion.setCommandLine(COMMAND_LINE);
		commandLineCriterion.setExpectedResult("value");
		commandLineCriterion.setExecuteLocally(true);
		commandLineCriterion.setErrorMessage("Unable to connect using Navisphere");

		final TelemetryManager telemetryManager = TelemetryManager.builder().build();

		final CriterionTestResult criterionTestResult = winCommandLineCriterionProcessor.process(
			commandLineCriterion,
			telemetryManager
		);

		assertFalse(criterionTestResult.isSuccess());
	}

	@Test
	void testProcessCommandLineCriterionHostNotWindows() {
		final CommandLineCriterion commandLineCriterion = new CommandLineCriterion();
		commandLineCriterion.setCommandLine(COMMAND_LINE);
		commandLineCriterion.setExpectedResult("value");
		commandLineCriterion.setExecuteLocally(false);
		commandLineCriterion.setErrorMessage("Unable to connect using Navisphere");

		final WmiTestConfiguration wmiConfiguration = WmiTestConfiguration
			.builder()
			.username(USERNAME)
			.password(PASSWORD)
			.build();

		final HostConfiguration hostConfiguration = HostConfiguration
			.builder()
			.hostId(HOST_NAME)
			.hostname(HOST_NAME)
			.hostType(DeviceKind.LINUX)
			.configurations(Map.of(WmiTestConfiguration.class, wmiConfiguration))
			.build();

		final HostProperties hostProperties = HostProperties.builder().isLocalhost(false).build();

		final TelemetryManager telemetryManager = TelemetryManager
			.builder()
			.hostConfiguration(hostConfiguration)
			.hostProperties(hostProperties)
			.build();

		final CriterionTestResult criterionTestResult = winCommandLineCriterionProcessor.process(
			commandLineCriterion,
			telemetryManager
		);

		assertFalse(criterionTestResult.isSuccess());
	}

	@Test
	void testProcessCommandLineCriterionHostLocalhost() {
		final CommandLineCriterion commandLineCriterion = new CommandLineCriterion();
		commandLineCriterion.setCommandLine(COMMAND_LINE);
		commandLineCriterion.setExpectedResult("value");
		commandLineCriterion.setExecuteLocally(false);
		commandLineCriterion.setErrorMessage("Unable to connect using Navisphere");

		final WmiTestConfiguration wmiConfiguration = WmiTestConfiguration
			.builder()
			.username(USERNAME)
			.password(PASSWORD)
			.build();

		final HostConfiguration hostConfiguration = HostConfiguration
			.builder()
			.hostId(HOST_NAME)
			.hostname(HOST_NAME)
			.hostType(DeviceKind.WINDOWS)
			.configurations(Map.of(WmiTestConfiguration.class, wmiConfiguration))
			.build();

		final HostProperties hostProperties = HostProperties.builder().isLocalhost(true).build();

		final TelemetryManager telemetryManager = TelemetryManager
			.builder()
			.hostConfiguration(hostConfiguration)
			.hostProperties(hostProperties)
			.build();

		final CriterionTestResult criterionTestResult = winCommandLineCriterionProcessor.process(
			commandLineCriterion,
			telemetryManager
		);

		assertFalse(criterionTestResult.isSuccess());
	}

	@Test
	void testProcessCommandLineCriterionNull() {
		final CommandLineCriterion commandLineCriterion = null;

		assertTrue(
			winCommandLineCriterionProcessor
				.process(commandLineCriterion, TelemetryManager.builder().build())
				.getMessage()
				.contains("Malformed CommandLine criterion.")
		);
	}

	@Test
	void testProcessCommandLineCriterionExpectedResultNull() {
		final CommandLineCriterion commandLineCriterion = new CommandLineCriterion();
		commandLineCriterion.setCommandLine(COMMAND_LINE);
		commandLineCriterion.setExecuteLocally(false);
		commandLineCriterion.setErrorMessage("Unable to connect using Navisphere");

		final TelemetryManager telemetryManager = TelemetryManager.builder().build();

		final CriterionTestResult criterionTestResult = winCommandLineCriterionProcessor.process(
			commandLineCriterion,
			telemetryManager
		);

		assertNotNull(criterionTestResult);
		assertTrue(criterionTestResult.isSuccess());
		assertEquals("CommandLine or ExpectedResult are empty. Skipping this test.", criterionTestResult.getResult());
	}

	@Test
	void testProcessCommandLineCmdLineEmpty() {
		final CommandLineCriterion commandLineCriterion = new CommandLineCriterion();
		commandLineCriterion.setCommandLine("");
		commandLineCriterion.setExpectedResult("Agent Rev:");
		commandLineCriterion.setExecuteLocally(false);
		commandLineCriterion.setErrorMessage("Unable to connect using Navisphere");

		final TelemetryManager telemetryManager = TelemetryManager.builder().build();

		final CriterionTestResult criterionTestResult = winCommandLineCriterionProcessor.process(
			commandLineCriterion,
			telemetryManager
		);

		assertNotNull(criterionTestResult);
		assertTrue(criterionTestResult.isSuccess());
		assertEquals("CommandLine or ExpectedResult are empty. Skipping this test.", criterionTestResult.getResult());
	}

	@Test
	void testProcessCommandLineCriterionRemoteOk() throws Exception {
		testCommandLineCriterionProcessing("Agent Rev:", "Agent Rev: v0.9.03", true);
	}

	/**
	 * Tests the processing of a {@link CommandLineCriterion} with predefined input and expected output.
	 * This method sets up a criterion with specified command line settings and expected results,
	 * then configures a simulated environment to process the criterion.
	 * It verifies whether the processing result matches the expected success state.
	 *
	 * @param expectedResult    The expected result string that the command line processing should match.
	 * @param commandLineResult The simulated result string from executing the command line.
	 * @param isSuccess         Indicates whether the processing should be considered successful.
	 * @throws Exception        If any error occurs. This means the test will fail.
	 */
	private void testCommandLineCriterionProcessing(String expectedResult, String commandLineResult, boolean isSuccess)
		throws Exception {
		final Connector connector = Connector.builder().build();

		final Map<String, Connector> store = Map.of(CONNECTOR_ID, connector);

		final ConnectorStore connectorStore = new ConnectorStore();
		connectorStore.setStore(store);

		final CommandLineCriterion commandLineCriterion = new CommandLineCriterion();
		commandLineCriterion.setCommandLine(COMMAND_LINE);
		commandLineCriterion.setExpectedResult(expectedResult);
		commandLineCriterion.setExecuteLocally(false);
		commandLineCriterion.setErrorMessage("Unable to connect using Navisphere");

		final WmiTestConfiguration wmiConfiguration = WmiTestConfiguration
			.builder()
			.username(USERNAME)
			.password(PASSWORD)
			.build();

		final HostConfiguration hostConfiguration = HostConfiguration
			.builder()
			.hostId(HOST_NAME)
			.hostname(HOST_NAME)
			.hostType(DeviceKind.WINDOWS)
			.configurations(Map.of(WmiTestConfiguration.class, wmiConfiguration))
			.build();

		final HostProperties hostProperties = HostProperties.builder().isLocalhost(false).build();

		final TelemetryManager telemetryManager = TelemetryManager
			.builder()
			.hostConfiguration(hostConfiguration)
			.hostProperties(hostProperties)
			.connectorStore(connectorStore)
			.build();

		doReturn(wmiConfiguration).when(configurationRetrieverMock).apply(telemetryManager);
		doReturn(new OsCommandResult(commandLineResult, COMMAND_LINE))
			.when(winCommandServiceMock)
			.runOsCommand(commandLineCriterion.getCommandLine(), HOST_NAME, wmiConfiguration, Map.of());

		final CriterionTestResult criterionTestResult = winCommandLineCriterionProcessor.process(
			commandLineCriterion,
			telemetryManager
		);

		assertEquals(isSuccess, criterionTestResult.isSuccess());
	}

	@Test
	void testProcessCommandLineCriterionRemoteNok() throws Exception {
		testCommandLineCriterionProcessing("Agent Rev:", "does-not-match", false);
	}

	@Test
	void testProcessCommandLineCriterionThrowsNoCredentialProvidedException() throws Exception {
		final Connector connector = Connector.builder().build();

		final Map<String, Connector> store = Map.of(CONNECTOR_ID, connector);

		final ConnectorStore connectorStore = new ConnectorStore();
		connectorStore.setStore(store);

		final CommandLineCriterion commandLineCriterion = new CommandLineCriterion();
		commandLineCriterion.setCommandLine(COMMAND_LINE);
		commandLineCriterion.setExpectedResult("Agent Rev:");
		commandLineCriterion.setExecuteLocally(false);
		commandLineCriterion.setErrorMessage("Unable to connect using Navisphere");

		final WmiTestConfiguration wmiConfiguration = WmiTestConfiguration
			.builder()
			.username(USERNAME)
			.password(PASSWORD)
			.build();

		final HostConfiguration hostConfiguration = HostConfiguration
			.builder()
			.hostId(HOST_NAME)
			.hostname(HOST_NAME)
			.hostType(DeviceKind.WINDOWS)
			.configurations(Map.of(WmiTestConfiguration.class, wmiConfiguration))
			.build();

		final HostProperties hostProperties = HostProperties.builder().isLocalhost(false).build();

		final TelemetryManager telemetryManager = TelemetryManager
			.builder()
			.hostConfiguration(hostConfiguration)
			.hostProperties(hostProperties)
			.connectorStore(connectorStore)
			.build();

		doReturn(wmiConfiguration).when(configurationRetrieverMock).apply(telemetryManager);
		doThrow(new NoCredentialProvidedException())
			.when(winCommandServiceMock)
			.runOsCommand(commandLineCriterion.getCommandLine(), HOST_NAME, wmiConfiguration, Map.of());

		final CriterionTestResult criterionTestResult = winCommandLineCriterionProcessor.process(
			commandLineCriterion,
			telemetryManager
		);

		assertFalse(criterionTestResult.isSuccess());
	}

	@Test
	void testProcessCommandLineCriterionThrowsException() throws Exception {
		final Connector connector = Connector.builder().build();

		final Map<String, Connector> store = Map.of(CONNECTOR_ID, connector);

		final ConnectorStore connectorStore = new ConnectorStore();
		connectorStore.setStore(store);

		final CommandLineCriterion commandLineCriterion = new CommandLineCriterion();
		commandLineCriterion.setCommandLine(COMMAND_LINE);
		commandLineCriterion.setExpectedResult("Agent Rev:");
		commandLineCriterion.setExecuteLocally(false);
		commandLineCriterion.setErrorMessage("Unable to connect using Navisphere");

		final WmiTestConfiguration wmiConfiguration = WmiTestConfiguration
			.builder()
			.username(USERNAME)
			.password(PASSWORD)
			.build();

		final HostConfiguration hostConfiguration = HostConfiguration
			.builder()
			.hostId(HOST_NAME)
			.hostname(HOST_NAME)
			.hostType(DeviceKind.WINDOWS)
			.configurations(Map.of(WmiTestConfiguration.class, wmiConfiguration))
			.build();

		final HostProperties hostProperties = HostProperties.builder().isLocalhost(false).build();

		final TelemetryManager telemetryManager = TelemetryManager
			.builder()
			.hostConfiguration(hostConfiguration)
			.hostProperties(hostProperties)
			.connectorStore(connectorStore)
			.build();

		doReturn(wmiConfiguration).when(configurationRetrieverMock).apply(telemetryManager);
		doThrow(new IOException("error"))
			.when(winCommandServiceMock)
			.runOsCommand(commandLineCriterion.getCommandLine(), HOST_NAME, wmiConfiguration, Map.of());

		final CriterionTestResult criterionTestResult = winCommandLineCriterionProcessor.process(
			commandLineCriterion,
			telemetryManager
		);

		assertFalse(criterionTestResult.isSuccess());
	}
}
