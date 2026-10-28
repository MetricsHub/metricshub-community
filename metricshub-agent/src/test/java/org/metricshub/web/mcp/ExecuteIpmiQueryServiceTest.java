package org.metricshub.web.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.metricshub.agent.context.AgentContext;
import org.metricshub.engine.configuration.HostConfiguration;
import org.metricshub.engine.connector.model.monitor.task.source.IpmiRawSource;
import org.metricshub.engine.extension.ExtensionManager;
import org.metricshub.engine.telemetry.TelemetryManager;
import org.metricshub.extension.ipmi.IpmiConfiguration;
import org.metricshub.extension.ipmi.IpmiExtension;
import org.metricshub.extension.ipmi.IpmiRequestExecutor;
import org.metricshub.ipmi.core.coding.commands.chassis.PowerCommand;
import org.metricshub.web.AgentContextHolder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ExecuteIpmiQueryServiceTest {

	/**
	 * A hostname where the IPMI query is executed.
	 */
	private static final String HOSTNAME = "hostname";

	private AgentContextHolder agentContextHolder;
	private AgentContext agentContext;
	private ExtensionManager extensionManager;
	private ExecuteIpmiQueryService ipmiQueryService;

	@InjectMocks
	private IpmiExtension ipmiExtension;

	@Mock
	private IpmiRequestExecutor ipmiRequestExecutorMock;

	@BeforeEach
	void setup() {
		// Mocking the agent context holder
		agentContextHolder = mock(AgentContextHolder.class);

		// Mocking the agent context
		agentContext = mock(AgentContext.class);

		// Creating an extension manager with the IPMI Extension
		extensionManager = ExtensionManager.builder().withProtocolExtensions(List.of(ipmiExtension)).build();

		// Mocking agent context and agent context holder
		when(agentContextHolder.getAgentContext()).thenReturn(agentContext);
		when(agentContext.getExtensionManager()).thenReturn(extensionManager);

		// Injecting the agent context holder in the IPMI query service.
		ipmiQueryService = new ExecuteIpmiQueryService(agentContextHolder);
	}

	@Test
	void testExecuteIpmiQueryWithoutConfiguration() {
		// creating a host configuration without configuration
		HostConfiguration hostConfiguration = HostConfiguration.builder()
			.hostname(HOSTNAME)
			.configurations(Map.of())
			.build();

		// Creating a telemetry manager with a host configuration
		TelemetryManager telemetryManager = TelemetryManager.builder().hostConfiguration(hostConfiguration).build();

		// Mocking agent context
		when(agentContext.getTelemetryManagers()).thenReturn(Map.of("Paris", Map.of(HOSTNAME, telemetryManager)));

		// Calling execute query
		final MultiHostToolResponse<QueryResponse> result = ipmiQueryService.executeQuery(
			List.of(HOSTNAME),
			null,
			null,
			null,
			null,
			null,
			null,
			null,
			null,
			null
		);

		assertEquals(1, result.getHosts().size(), () -> "One host response expected");
		assertEquals("No IPMI configuration found for hostname.", result.getHosts().get(0).getResponse().getError(), () ->
			"Should report missing IPMI configuration"
		);
	}

	@Test
	void testExecuteIpmiQueryWithoutExtension() {
		// Removing the extension to test
		extensionManager.setProtocolExtensions(List.of());

		// Calling execute query
		final MultiHostToolResponse<QueryResponse> result = ipmiQueryService.executeQuery(
			List.of(HOSTNAME),
			null,
			null,
			null,
			null,
			null,
			null,
			null,
			null,
			null
		);

		assertEquals("The IPMI extension is not available", result.getErrorMessage());
		assertTrue(result.getHosts().isEmpty(), () -> "No per-host responses expected when extension is missing");
	}

	@Test
	void testExecuteIpmiQuery() throws Exception {
		// Creating a IPMI Configuration for the host
		IpmiConfiguration ipmiConfiguration = IpmiConfiguration.builder()
			.hostname(HOSTNAME)
			.username("username")
			.password("password".toCharArray())
			.build();

		// Creating a host configuration with the IPMI configuration
		HostConfiguration hostConfiguration = HostConfiguration.builder()
			.hostname(HOSTNAME)
			.configurations(Map.of(IpmiConfiguration.class, ipmiConfiguration))
			.build();

		// Creating a telemetry manager with a host configuration
		TelemetryManager telemetryManager = TelemetryManager.builder().hostConfiguration(hostConfiguration).build();

		// Mocking agent context
		when(agentContext.getTelemetryManagers()).thenReturn(Map.of("Paris", Map.of(HOSTNAME, telemetryManager)));

		// Mocking executeIpmi query on IPMI request executor
		when(ipmiRequestExecutorMock.executeIpmiGetSensors(eq(HOSTNAME), any(IpmiConfiguration.class))).thenReturn(
			"Success"
		);

		final MultiHostToolResponse<QueryResponse> result = ipmiQueryService.executeQuery(
			List.of(HOSTNAME),
			null,
			8,
			null,
			null,
			null,
			null,
			null,
			null,
			null
		);

		assertEquals(1, result.getHosts().size(), () -> "One host response expected");
		assertEquals("Success", result.getHosts().get(0).getResponse().getResponse(), () ->
			"The query result should be equals to `Success`"
		);
	}

	@Test
	void testExecuteIpmiQueryExtensionThrows() throws Exception {
		// Creating a IPMI Configuration for the host
		IpmiConfiguration ipmiConfiguration = IpmiConfiguration.builder()
			.hostname(HOSTNAME)
			.username("username")
			.password("password".toCharArray())
			.build();

		// Creating a host configuration with the IPMI configuration
		HostConfiguration hostConfiguration = HostConfiguration.builder()
			.hostname(HOSTNAME)
			.configurations(Map.of(IpmiConfiguration.class, ipmiConfiguration))
			.build();

		// Creating a telemetry manager with a host configuration
		TelemetryManager telemetryManager = TelemetryManager.builder().hostConfiguration(hostConfiguration).build();

		// Mocking agent context
		when(agentContext.getTelemetryManagers()).thenReturn(Map.of("Paris", Map.of(HOSTNAME, telemetryManager)));

		// Make the executor throw when the extension tries to execute
		when(ipmiRequestExecutorMock.executeIpmiGetSensors(eq(HOSTNAME), any(IpmiConfiguration.class))).thenThrow(
			new RuntimeException("An error has occurred")
		);

		// Call the execute query method
		QueryResponse result = ipmiQueryService
			.executeQuery(List.of(HOSTNAME), null, null, null, null, null, null, null, null, null)
			.getHosts()
			.get(0)
			.getResponse();

		// Assertions
		assertNotNull(result.getError(), () -> "Error should be returned when executor throws");
		assertTrue(result.getError().contains("An error has occurred"), () ->
			"Error message should include the thrown text"
		);
	}

	@Test
	void testExecuteIpmiRawQuery() throws Exception {
		final IpmiConfiguration ipmiConfiguration = IpmiConfiguration.builder()
			.hostname(HOSTNAME)
			.username("username")
			.password("password".toCharArray())
			.build();
		final HostConfiguration hostConfiguration = HostConfiguration.builder()
			.hostname(HOSTNAME)
			.configurations(Map.of(IpmiConfiguration.class, ipmiConfiguration))
			.build();
		final TelemetryManager telemetryManager = TelemetryManager.builder().hostConfiguration(hostConfiguration).build();
		when(agentContext.getTelemetryManagers()).thenReturn(Map.of("Paris", Map.of(HOSTNAME, telemetryManager)));

		when(
			ipmiRequestExecutorMock.executeIpmiRaw(
				eq(HOSTNAME),
				any(IpmiConfiguration.class),
				eq(IpmiRawSource.builder().netFn("0x06").command("0x01").build())
			)
		).thenReturn("00;20 01");

		// Raw commands are off by default
		System.clearProperty("metricshub.mcp.tool.ipmi.raw.enabled");
		final MultiHostToolResponse<QueryResponse> disabled = ipmiQueryService.executeQuery(
			List.of(HOSTNAME),
			null,
			null,
			"raw",
			null,
			null,
			null,
			"0x06",
			"0x01",
			null
		);
		assertEquals("The raw IPMI commands are disabled for MCP.", disabled.getErrorMessage());
		assertTrue(disabled.getHosts().isEmpty());

		System.setProperty("metricshub.mcp.tool.ipmi.raw.enabled", "true");
		try {
			final MultiHostToolResponse<QueryResponse> result = ipmiQueryService.executeQuery(
				List.of(HOSTNAME),
				null,
				null,
				"RAW",
				null,
				null,
				null,
				"0x06",
				"0x01",
				null
			);
			assertEquals("00;20 01", result.getHosts().get(0).getResponse().getResponse());
		} finally {
			System.clearProperty("metricshub.mcp.tool.ipmi.raw.enabled");
		}
		assertFalse(ExecuteIpmiQueryService.isIpmiRawEnabledForMCP());

		// The query node of each query type
		assertNull(ExecuteIpmiQueryService.buildQueryNode(null, "1h", null, null, null, null, null));
		assertNull(ExecuteIpmiQueryService.buildQueryNode(" ", null, null, null, null, null, null));
		final JsonNode sel = ExecuteIpmiQueryService.buildQueryNode("sel", "1h", null, null, null, null, null);
		assertEquals("sel", sel.get("query").asText());
		assertEquals("1h", sel.get("since").asText());
		assertFalse(sel.has("netFn"));
		final JsonNode raw = ExecuteIpmiQueryService.buildQueryNode("raw", null, "6", "1", "0x00", null, null);
		assertEquals("0x00", raw.get("data").asText());
		assertFalse(raw.has("since"));
	}

	@Test
	void testExecuteIpmiChassisQuery() throws Exception {
		final IpmiConfiguration ipmiConfiguration = IpmiConfiguration.builder()
			.hostname(HOSTNAME)
			.username("username")
			.password("password".toCharArray())
			.build();
		final HostConfiguration hostConfiguration = HostConfiguration.builder()
			.hostname(HOSTNAME)
			.configurations(Map.of(IpmiConfiguration.class, ipmiConfiguration))
			.build();
		final TelemetryManager telemetryManager = TelemetryManager.builder().hostConfiguration(hostConfiguration).build();
		when(agentContext.getTelemetryManagers()).thenReturn(Map.of("Paris", Map.of(HOSTNAME, telemetryManager)));

		// The power state is read without the opt-in
		System.clearProperty("metricshub.mcp.tool.ipmi.chassis.enabled");
		when(ipmiRequestExecutorMock.executeIpmiDetection(eq(HOSTNAME), any(IpmiConfiguration.class))).thenReturn(
			"System power state is up"
		);
		assertEquals(
			"System power state is up",
			ipmiQueryService
				.executeQuery(List.of(HOSTNAME), null, null, "chassis", "status", null, null, null, null, null)
				.getHosts()
				.get(0)
				.getResponse()
				.getResponse()
		);

		// A power action needs it
		final MultiHostToolResponse<QueryResponse> disabled = ipmiQueryService.executeQuery(
			List.of(HOSTNAME),
			null,
			null,
			"chassis",
			"off",
			null,
			null,
			null,
			null,
			null
		);
		assertEquals("The IPMI chassis actions are disabled for MCP.", disabled.getErrorMessage());
		assertTrue(disabled.getHosts().isEmpty());

		System.setProperty("metricshub.mcp.tool.ipmi.chassis.enabled", "true");
		try {
			when(
				ipmiRequestExecutorMock.executeIpmiChassisControl(
					eq(HOSTNAME),
					any(IpmiConfiguration.class),
					eq(PowerCommand.PowerDown)
				)
			).thenReturn("System power state is down");
			assertEquals(
				"System power state is down",
				ipmiQueryService
					.executeQuery(List.of(HOSTNAME), null, null, "chassis", "off", null, null, null, null, null)
					.getHosts()
					.get(0)
					.getResponse()
					.getResponse()
			);
		} finally {
			System.clearProperty("metricshub.mcp.tool.ipmi.chassis.enabled");
		}
		assertFalse(ExecuteIpmiQueryService.isIpmiChassisEnabledForMCP());

		final JsonNode chassis = ExecuteIpmiQueryService.buildQueryNode(
			"chassis",
			null,
			null,
			null,
			null,
			"identify",
			"30"
		);
		assertEquals("identify", chassis.get("action").asText());
		assertEquals("30", chassis.get("seconds").asText());
	}
}
