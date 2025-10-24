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
import org.metricshub.agent.context.AgentContext;
import org.metricshub.engine.configuration.HostConfiguration;
import org.metricshub.engine.extension.ExtensionManager;
import org.metricshub.engine.telemetry.TelemetryManager;
import org.metricshub.extension.oscommand.OsCommandExtension;
import org.metricshub.extension.oscommand.SshConfiguration;
import org.metricshub.web.AgentContextHolder;

class ExecuteSshCommandlineServiceTest {

	/**
	 * Hostname against which the commandline is executed.
	 */
	private static final String HOSTNAME = "hostname";

	/**
	 * The commandline to execute through SSH
	 */
	private static final String COMMANDLINE = "echo Success";

	/**
	 * A Timeout for the commandline execution.
	 */
	private static final Long TIMEOUT = 10L;

	/**
	 * Mocked successful provider response.
	 */
	private static final String SUCCESS_RESPONSE = "Success";

	private AgentContextHolder agentContextHolder;
	private AgentContext agentContext;
	private ExtensionManager extensionManager;
	private ExecuteSshCommandlineService sshCommandlineService;

	private OsCommandExtension osCommandExtension;

	void setup() {
		// Mocking the agent context holder
		agentContextHolder = mock(AgentContextHolder.class);

		// Mocking the agent context
		agentContext = mock(AgentContext.class);

		// Create an instance of OsCommand Extension
		osCommandExtension = mock(OsCommandExtension.class);

		// Creating an extension manager with the OsCommand Extension
		extensionManager = ExtensionManager.builder().withProtocolExtensions(List.of(osCommandExtension)).build();

		// Mocking agent context and agent context holder
		when(agentContextHolder.getAgentContext()).thenReturn(agentContext);
		when(agentContext.getExtensionManager()).thenReturn(extensionManager);
		when(osCommandExtension.isValidConfiguration(any(SshConfiguration.class))).thenReturn(true);
		when(osCommandExtension.isSupportedConfigurationType("ssh")).thenReturn(true);

		// Injecting the agent context holder in the SSH commandline service.
		sshCommandlineService = new ExecuteSshCommandlineService(agentContextHolder);
	}

	@Test
	void testExecuteSshCommandlineWithoutConfiguration() {
		setup();
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

		// Enable SSH on MCP tools
		System.setProperty("metricshub.tools.ssh.enabled", "true");

		// Calling execute query
		final MultiHostToolResponse<QueryResponse> result = sshCommandlineService.executeQuery(
			List.of(HOSTNAME),
			COMMANDLINE,
			TIMEOUT,
			null
		);

		assertEquals(1, result.getHosts().size(), "Result should not be null when executing a query");
		final HostToolResponse<QueryResponse> hostResponse = result.getHosts().get(0);
		assertEquals(
			"No valid configuration found for SSH on %s.".formatted(HOSTNAME),
			hostResponse.getResponse().getError(),
			() -> "Unexpected error message when host has no configurations. "
		);
	}

	@Test
	void testExecuteSshCommandlineWithoutExtension() {
		setup();
		// Removing the extension to test
		extensionManager.setProtocolExtensions(List.of());

		// Calling execute query
		final MultiHostToolResponse<QueryResponse> result = sshCommandlineService.executeQuery(
			List.of(HOSTNAME),
			COMMANDLINE,
			TIMEOUT,
			null
		);

		assertEquals(0, result.getHosts().size(), () -> "No host should respond");
		assertTrue(
			result.getErrorMessage().contains("No Extension found for SSH"),
			() -> "Unexpected error message when the SSH extension isn't found"
		);
	}

	@Test
	void testExecuteSshCommandlineRequest() throws Exception {
		setup();
		// Creating a SSH Configuration for the host
		SshConfiguration sshConfiguration = SshConfiguration
			.sshConfigurationBuilder()
			.hostname(HOSTNAME)
			.username("username")
			.password("password".toCharArray())
			.build();

		// Creating a host configuration with the SSH configuration
		HostConfiguration hostConfiguration = HostConfiguration
			.builder()
			.hostname(HOSTNAME)
			.configurations(Map.of(SshConfiguration.class, sshConfiguration))
			.build();

		// Creating a telemetry manager with a host configuration
		TelemetryManager telemetryManager = TelemetryManager.builder().hostConfiguration(hostConfiguration).build();

		// Mocking agent context
		when(agentContext.getTelemetryManagers()).thenReturn(Map.of("Paris", Map.of(HOSTNAME, telemetryManager)));

		// Enable SSH on MCP tools
		System.setProperty("metricshub.tools.ssh.enabled", "true");

		when(osCommandExtension.executeQuery(any(SshConfiguration.class), any(ObjectNode.class)))
			.thenReturn(SUCCESS_RESPONSE);

		final MultiHostToolResponse<QueryResponse> result = sshCommandlineService.executeQuery(
			List.of(HOSTNAME),
			COMMANDLINE,
			TIMEOUT,
			null
		);

		assertEquals(1, result.getHosts().size(), "Result should not be null when executing a query");

		final HostToolResponse<QueryResponse> hostResponse = result.getHosts().get(0);
		assertEquals(
			SUCCESS_RESPONSE,
			hostResponse.getResponse().getResponse(),
			() -> "SSH Commandline response mismatch for mocked value `Success`"
		);
	}

	@Test
	void testExecuteSshCommandlineRequestThrows() throws Exception {
		setup();
		// Creating a SSH Configuration for the host
		SshConfiguration sshConfiguration = SshConfiguration
			.sshConfigurationBuilder()
			.hostname(HOSTNAME)
			.username("username")
			.password("password".toCharArray())
			.build();

		// Creating a host configuration with the SSH configuration
		HostConfiguration hostConfiguration = HostConfiguration
			.builder()
			.hostname(HOSTNAME)
			.configurations(Map.of(SshConfiguration.class, sshConfiguration))
			.build();

		// Creating a telemetry manager with a host configuration
		TelemetryManager telemetryManager = TelemetryManager.builder().hostConfiguration(hostConfiguration).build();

		// Mocking agent context
		when(agentContext.getTelemetryManagers()).thenReturn(Map.of("Paris", Map.of(HOSTNAME, telemetryManager)));

		// Enable SSH on MCP tools
		System.setProperty("metricshub.tools.ssh.enabled", "true");

		when(osCommandExtension.executeQuery(any(SshConfiguration.class), any(ObjectNode.class)))
			.thenThrow(new IllegalArgumentException("An error has occurred"));

		final MultiHostToolResponse<QueryResponse> result = sshCommandlineService.executeQuery(
			List.of(HOSTNAME),
			COMMANDLINE,
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
	void testExecuteSshCommandlineRequestWhenSshDisabled() throws Exception {
		setup();
		// Creating a SSH Configuration for the host
		SshConfiguration sshConfiguration = SshConfiguration
			.sshConfigurationBuilder()
			.hostname(HOSTNAME)
			.username("username")
			.password("password".toCharArray())
			.build();

		// Creating a host configuration with the SSH configuration
		HostConfiguration hostConfiguration = HostConfiguration
			.builder()
			.hostname(HOSTNAME)
			.configurations(Map.of(SshConfiguration.class, sshConfiguration))
			.build();

		// Creating a telemetry manager with a host configuration
		TelemetryManager telemetryManager = TelemetryManager.builder().hostConfiguration(hostConfiguration).build();

		// Mocking agent context
		when(agentContext.getTelemetryManagers()).thenReturn(Map.of("Paris", Map.of(HOSTNAME, telemetryManager)));

		// Disable SSH on MCP tools
		System.setProperty("metricshub.tools.ssh.enabled", "false");

		final MultiHostToolResponse<QueryResponse> result = sshCommandlineService.executeQuery(
			List.of(HOSTNAME),
			COMMANDLINE,
			TIMEOUT,
			null
		);
		assertEquals(0, result.getHosts().size(), "Result should not be null when executing a query");

		assertEquals(
			"The SSH connections are disabled for MCP.",
			result.getErrorMessage(),
			() -> "SSH commandline shouldn't be executed as SSH is disabled."
		);
	}

	@Test
	void testIsSshEnabledForMCP() {
		System.clearProperty("metricshub.tools.ssh.enabled");
		assertFalse(
			ExecuteSshCommandlineService.isSshEnabledForMCP(),
			() -> "SSH is disabled, the response should be false"
		);

		System.setProperty("metricshub.tools.ssh.enabled", "true");
		assertTrue(ExecuteSshCommandlineService.isSshEnabledForMCP(), () -> "SSH is enabled, the response should be true");

		System.setProperty("metricshub.tools.ssh.enabled", "false");
		assertFalse(
			ExecuteSshCommandlineService.isSshEnabledForMCP(),
			() -> "SSH is disabled, the response should be false"
		);
	}
}
