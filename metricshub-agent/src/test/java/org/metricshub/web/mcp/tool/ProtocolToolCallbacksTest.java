package org.metricshub.web.mcp.tool;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.metricshub.agent.context.AgentContext;
import org.metricshub.engine.configuration.HostConfiguration;
import org.metricshub.engine.configuration.IConfiguration;
import org.metricshub.engine.extension.ExtensionManager;
import org.metricshub.engine.extension.IProtocolExtension;
import org.metricshub.engine.telemetry.TelemetryManager;
import org.metricshub.engine.tool.ToolDefinition;
import org.metricshub.engine.tool.ToolParameter;
import org.metricshub.web.AgentContextHolder;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.tool.ToolCallback;

class ProtocolToolCallbacksTest {

	private static final ObjectMapper MAPPER = new ObjectMapper();
	private static final String HOSTNAME = "host-01";

	private IProtocolExtension extension;
	private IConfiguration configuration;
	private AgentContextHolder agentContextHolder;

	@BeforeEach
	void setUp() {
		extension = mock(IProtocolExtension.class);
		when(extension.getIdentifier()).thenReturn("ipmi");
		when(extension.getTools()).thenReturn(
			List.of(
				new ToolDefinition(
					"sel",
					"Reads the System Event Log.",
					Map.of("since", new ToolParameter("string", "A duration", false, null)),
					false
				),
				new ToolDefinition("chassis", "Powers the chassis.", Map.of(), true)
			)
		);

		configuration = mock(IConfiguration.class);
		when(configuration.copy()).thenReturn(configuration);
		when(extension.isValidConfiguration(configuration)).thenReturn(true);

		final TelemetryManager telemetryManager = TelemetryManager.builder()
			.hostConfiguration(
				HostConfiguration.builder()
					.hostname(HOSTNAME)
					.configurations(Map.of(IConfiguration.class, configuration))
					.build()
			)
			.build();

		final AgentContext agentContext = mock(AgentContext.class);
		when(agentContext.getExtensionManager()).thenReturn(
			ExtensionManager.builder().withProtocolExtensions(List.of(extension)).build()
		);
		when(agentContext.getTelemetryManagers()).thenReturn(Map.of("group", Map.of(HOSTNAME, telemetryManager)));
		agentContextHolder = new AgentContextHolder(agentContext);
	}

	@AfterEach
	void tearDown() {
		System.clearProperty(HostTools.DESTRUCTIVE_TOOLS_FLAG);
	}

	@Test
	void testDestructiveToolsNeedTheFlag() {
		assertEquals(List.of("ipmi_sel"), names(ProtocolToolCallbacks.from(agentContextHolder)));

		System.setProperty(HostTools.DESTRUCTIVE_TOOLS_FLAG, "true");
		assertEquals(List.of("ipmi_sel", "ipmi_chassis"), names(ProtocolToolCallbacks.from(agentContextHolder)));
	}

	@Test
	void testToolRunsThroughTheExtensionWithTheHostConfiguration() throws Exception {
		when(extension.executeTool(eq("sel"), eq(configuration), any())).thenReturn("header\nrow");
		final ToolCallback callback = ProtocolToolCallbacks.from(agentContextHolder).get(0);

		final JsonNode result = MAPPER.readTree(callback.call("{\"hostname\":[\"host-01\"],\"since\":\"1h\"}"));

		assertEquals("header\nrow", result.get("hosts").get(0).get("response").get("response").asText());
		verify(configuration).setHostname(HOSTNAME);
		final ArgumentCaptor<JsonNode> arguments = ArgumentCaptor.forClass(JsonNode.class);
		verify(extension).executeTool(eq("sel"), eq(configuration), arguments.capture());
		assertEquals("1h", arguments.getValue().get("since").asText());
	}

	@Test
	void testTimeoutIsAppliedToTheConfigurationNotPassedToTheExtension() throws Exception {
		when(extension.executeTool(eq("sel"), eq(configuration), any())).thenReturn("ok");
		final ToolCallback callback = ProtocolToolCallbacks.from(agentContextHolder).get(0);
		assertTrue(callback.getToolDefinition().inputSchema().contains("\"timeout\""));

		MAPPER.readTree(callback.call("{\"hostname\":[\"host-01\"],\"timeout\":45}"));

		verify(configuration).setTimeout(45L);
		final ArgumentCaptor<JsonNode> arguments = ArgumentCaptor.forClass(JsonNode.class);
		verify(extension).executeTool(eq("sel"), eq(configuration), arguments.capture());
		assertFalse(arguments.getValue().has("timeout"));

		final JsonNode bad = MAPPER.readTree(callback.call("{\"hostname\":[\"host-01\"],\"timeout\":\"soon\"}"));
		assertEquals(
			"The timeout must be a number of seconds: soon",
			bad.get("hosts").get(0).get("response").get("error").asText()
		);
	}

	@Test
	void testUnknownHostIsReported() throws Exception {
		final ToolCallback callback = ProtocolToolCallbacks.from(agentContextHolder).get(0);

		final JsonNode result = MAPPER.readTree(callback.call("{\"hostname\":[\"nope\"]}"));

		assertEquals(
			"No valid ipmi configuration found for nope.",
			result.get("hosts").get(0).get("response").get("error").asText()
		);
	}

	@Test
	void testExtensionFailureIsReported() throws Exception {
		when(extension.executeTool(eq("sel"), eq(configuration), any())).thenThrow(new IllegalStateException("timeout"));
		final ToolCallback callback = ProtocolToolCallbacks.from(agentContextHolder).get(0);

		final JsonNode result = MAPPER.readTree(callback.call("{\"hostname\":[\"host-01\"]}"));

		assertEquals(
			"An error has occurred when running the tool: timeout",
			result.get("hosts").get(0).get("response").get("error").asText()
		);
	}

	private static List<String> names(final List<ToolCallback> callbacks) {
		return callbacks
			.stream()
			.map(callback -> callback.getToolDefinition().name())
			.toList();
	}
}
