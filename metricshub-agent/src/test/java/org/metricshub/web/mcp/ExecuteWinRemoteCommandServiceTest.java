package org.metricshub.web.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.metricshub.agent.context.AgentContext;
import org.metricshub.engine.configuration.HostConfiguration;
import org.metricshub.engine.extension.ExtensionManager;
import org.metricshub.engine.telemetry.TelemetryManager;
import org.metricshub.extension.win.WinCommandService;
import org.metricshub.extension.win.detection.WmiDetectionService;
import org.metricshub.extension.wmi.WmiConfiguration;
import org.metricshub.extension.wmi.WmiExtension;
import org.metricshub.extension.wmi.WmiRequestExecutor;
import org.metricshub.web.AgentContextHolder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ExecuteWinRemoteCommandServiceTest {

	/**
	 * Hostname against which the Windows remote command is executed.
	 */
	private static final String HOSTNAME = "hostname";

	/**
	 * The Windows OS command (CMD command) to execute.
	 */
	private static final String COMMAND = "ipconfig /all";

	/**
	 * WMI protocol identifier.
	 */
	private static final String WMI_PROTOCOL = "wmi";

	/**
	 * A Timeout for the command execution.
	 */
	private static final Long TIMEOUT = 10L;

	/**
	 * Mocked successful provider response.
	 */
	private static final String SUCCESS_RESPONSE = "Success";

	private AgentContextHolder agentContextHolder;
	private AgentContext agentContext;
	private ExtensionManager extensionManager;
	private ExecuteWinRemoteCommandService winRemoteCommandService;

	@InjectMocks
	private WmiExtension wmiExtension;

	@Mock
	private WmiRequestExecutor wmiRequestExecutorMock;

	@Mock
	private WmiDetectionService wmiDetectionServiceMock;

	@Mock
	private WinCommandService winCommandServiceMock;

	void setup() {
		// Mocking the agent context holder
		agentContextHolder = mock(AgentContextHolder.class);

		// Mocking the agent context
		agentContext = mock(AgentContext.class);

		// Mocking the Wmi Extension
		wmiExtension = mock(WmiExtension.class);

		// Creating an extension manager with the WMI Extension
		extensionManager = ExtensionManager.builder().withProtocolExtensions(List.of(wmiExtension)).build();

		// Injecting the agent context holder in the Windows remote command service.
		winRemoteCommandService = new ExecuteWinRemoteCommandService(agentContextHolder);
	}

	@Test
	void testExecuteWinRemoteCommandWithoutConfiguration() {
		setup();

		// Mocking agent context and agent context holder
		when(agentContextHolder.getAgentContext()).thenReturn(agentContext);
		when(agentContext.getExtensionManager()).thenReturn(extensionManager);
		when(wmiExtension.isSupportedConfigurationType(WMI_PROTOCOL)).thenReturn(true);

		// creating a host configuration without configuration
		HostConfiguration hostConfiguration = HostConfiguration
			.builder()
			.hostname(HOSTNAME)
			.configurations(Map.of())
			.build();

		// Creating a telemetry manager with a host configuration
		TelemetryManager telemetryManager = TelemetryManager.builder().hostConfiguration(hostConfiguration).build();

		// Mocking agent context
		when(agentContext.getTelemetryManagers()).thenReturn(Map.of("Paris", Map.of(HOSTNAME, telemetryManager)));

		// Enable Windows remote on MCP tools
		System.setProperty("metricshub.mcp.tool.win.remote.enabled", "true");

		// Calling execute query
		final MultiHostToolResponse<QueryResponse> result = winRemoteCommandService.executeQuery(
			List.of(HOSTNAME),
			COMMAND,
			WMI_PROTOCOL,
			TIMEOUT,
			null
		);

		assertEquals(1, result.getHosts().size(), "Result should not be null when executing a query");
		final HostToolResponse<QueryResponse> hostResponse = result.getHosts().get(0);
		assertEquals(
			"No valid configuration found for remote Windows on %s.".formatted(HOSTNAME),
			hostResponse.getResponse().getError(),
			() -> "Unexpected error message when host has no configurations."
		);
	}

	@Test
	void testExecuteWinRemoteCommandWithoutExtension() {
		setup();

		// Mocking agent context and agent context holder
		when(agentContextHolder.getAgentContext()).thenReturn(agentContext);
		when(agentContext.getExtensionManager()).thenReturn(extensionManager);

		// Removing the extension to test
		extensionManager.setProtocolExtensions(List.of());

		// Enable Windows remote on MCP tools
		System.setProperty("metricshub.mcp.tool.win.remote.enabled", "true");

		// Calling execute query
		final MultiHostToolResponse<QueryResponse> result = winRemoteCommandService.executeQuery(
			List.of(HOSTNAME),
			COMMAND,
			WMI_PROTOCOL,
			TIMEOUT,
			null
		);

		assertEquals(0, result.getHosts().size(), () -> "No host should respond");
		assertTrue(
			result.getErrorMessage().contains("No Extension found for remote Windows commands"),
			() -> "Unexpected error message when the Windows remote extension isn't found"
		);
	}

	@Test
	void testExecuteWinRemoteCommandRequest() throws Exception {
		setup();

		// Mocking agent context and agent context holder
		when(agentContextHolder.getAgentContext()).thenReturn(agentContext);
		when(agentContext.getExtensionManager()).thenReturn(extensionManager);
		when(wmiExtension.isValidConfiguration(any(WmiConfiguration.class))).thenReturn(true);
		when(wmiExtension.isSupportedConfigurationType(WMI_PROTOCOL)).thenReturn(true);

		// Creating a WMI Configuration for the host
		WmiConfiguration wmiConfiguration = WmiConfiguration
			.builder()
			.hostname(HOSTNAME)
			.username("username")
			.password("password".toCharArray())
			.build();

		// Creating a host configuration with the WMI configuration
		HostConfiguration hostConfiguration = HostConfiguration
			.builder()
			.hostname(HOSTNAME)
			.configurations(Map.of(WmiConfiguration.class, wmiConfiguration))
			.build();

		// Creating a telemetry manager with a host configuration
		TelemetryManager telemetryManager = TelemetryManager.builder().hostConfiguration(hostConfiguration).build();

		// Mocking agent context
		when(agentContext.getTelemetryManagers()).thenReturn(Map.of("Paris", Map.of(HOSTNAME, telemetryManager)));

		// Enable Windows remote on MCP tools
		System.setProperty("metricshub.mcp.tool.win.remote.enabled", "true");

		when(wmiExtension.executeQuery(any(WmiConfiguration.class), any(ObjectNode.class))).thenReturn(SUCCESS_RESPONSE);

		final MultiHostToolResponse<QueryResponse> result = winRemoteCommandService.executeQuery(
			List.of(HOSTNAME),
			COMMAND,
			WMI_PROTOCOL,
			TIMEOUT,
			null
		);

		assertEquals(1, result.getHosts().size(), "Result should not be null when executing a query");

		final HostToolResponse<QueryResponse> hostResponse = result.getHosts().get(0);
		assertEquals(
			SUCCESS_RESPONSE,
			hostResponse.getResponse().getResponse(),
			() -> "Windows remote command response mismatch for mocked value `Success`"
		);
	}

	@Test
	void testExecuteWinRemoteCommandRequestThrows() throws Exception {
		setup();

		// Mocking agent context and agent context holder
		when(agentContextHolder.getAgentContext()).thenReturn(agentContext);
		when(agentContext.getExtensionManager()).thenReturn(extensionManager);
		when(wmiExtension.isValidConfiguration(any(WmiConfiguration.class))).thenReturn(true);
		when(wmiExtension.isSupportedConfigurationType(WMI_PROTOCOL)).thenReturn(true);

		// Creating a WMI Configuration for the host
		WmiConfiguration wmiConfiguration = WmiConfiguration
			.builder()
			.hostname(HOSTNAME)
			.username("username")
			.password("password".toCharArray())
			.build();

		// Creating a host configuration with the WMI configuration
		HostConfiguration hostConfiguration = HostConfiguration
			.builder()
			.hostname(HOSTNAME)
			.configurations(Map.of(WmiConfiguration.class, wmiConfiguration))
			.build();

		// Creating a telemetry manager with a host configuration
		TelemetryManager telemetryManager = TelemetryManager.builder().hostConfiguration(hostConfiguration).build();

		// Mocking agent context
		when(agentContext.getTelemetryManagers()).thenReturn(Map.of("Paris", Map.of(HOSTNAME, telemetryManager)));

		// Enable Windows remote on MCP tools
		System.setProperty("metricshub.mcp.tool.win.remote.enabled", "true");

		when(wmiExtension.executeQuery(any(WmiConfiguration.class), any(ObjectNode.class)))
			.thenThrow(new IllegalArgumentException("An error has occurred"));

		final MultiHostToolResponse<QueryResponse> result = winRemoteCommandService.executeQuery(
			List.of(HOSTNAME),
			COMMAND,
			WMI_PROTOCOL,
			TIMEOUT,
			null
		);
		assertEquals(1, result.getHosts().size(), "Result should not be null when executing a query");

		final HostToolResponse<QueryResponse> hostResponse = result.getHosts().get(0);
		final String errorMessage = hostResponse.getResponse().getError();
		assertNotNull(errorMessage, () -> "Error message should be returned when an exception is throws");
		assertTrue(
			errorMessage.contains("An error has occurred when executing the commandline"),
			() -> "Error message should contain 'An error has occurred'"
		);
	}

	@Test
	void testExecuteWinRemoteCommandRequestWhenWinRemoteDisabled() throws Exception {
		setup();

		// Disable Windows remote on MCP tools
		System.setProperty("metricshub.mcp.tool.win.remote.enabled", "false");

		final MultiHostToolResponse<QueryResponse> result = winRemoteCommandService.executeQuery(
			List.of(HOSTNAME),
			COMMAND,
			WMI_PROTOCOL,
			TIMEOUT,
			null
		);
		assertEquals(0, result.getHosts().size(), "Result should not be null when executing a query");

		assertEquals(
			"The remote Windows connections are disabled for MCP.",
			result.getErrorMessage(),
			() -> "Windows remote command shouldn't be executed as Windows remote is disabled."
		);
	}

	@Test
	void testIsWinRemoteEnabledForMCP() {
		System.clearProperty("metricshub.mcp.tool.win.remote.enabled");
		assertFalse(
			ExecuteWinRemoteCommandService.isWinRemoteEnabledForMCP(),
			() -> "Windows remote is disabled, the response should be false"
		);

		System.setProperty("metricshub.mcp.tool.win.remote.enabled", "true");
		assertTrue(
			ExecuteWinRemoteCommandService.isWinRemoteEnabledForMCP(),
			() -> "Windows remote is enabled, the response should be true"
		);

		System.setProperty("metricshub.mcp.tool.win.remote.enabled", "false");
		assertFalse(
			ExecuteWinRemoteCommandService.isWinRemoteEnabledForMCP(),
			() -> "Windows remote is disabled, the response should be false"
		);
	}
}
