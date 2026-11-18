package org.metricshub.web.mcp.tool;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;
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
import org.metricshub.engine.tool.ToolTable;
import org.metricshub.engine.tool.ToolText;
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
					Map.of(
						"since",
						new ToolParameter("string", "A duration", false, null),
						"limit",
						new ToolParameter("integer", "How many entries", false, null)
					),
					false
				),
				new ToolDefinition("chassis", "Powers the chassis.", Map.of(), true)
			)
		);

		configuration = mock(IConfiguration.class);
		when(configuration.copy()).thenReturn(configuration);
		when(extension.isToolConfiguration(configuration)).thenReturn(true);

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
	void testDestructiveToolsAreBuiltWhateverTheFlag() {
		// The flag applies where the tools are exposed, not where they are built: Central gets them all
		final List<ToolCallback> callbacks = ProtocolToolCallbacks.from(agentContextHolder.getAgentContext());

		assertEquals(List.of("ipmi_sel", "ipmi_chassis"), names(callbacks));
		assertFalse(((DynamicToolCallback) callbacks.get(0)).isDestructive());
		assertTrue(((DynamicToolCallback) callbacks.get(1)).isDestructive());
	}

	@Test
	void testToolRunsThroughTheExtensionWithTheHostConfiguration() throws Exception {
		when(extension.executeTool(eq("sel"), eq(configuration), any())).thenReturn(new ToolText("header\nrow"));
		final ToolCallback callback = ProtocolToolCallbacks.from(agentContextHolder.getAgentContext()).get(0);

		final JsonNode result = MAPPER.readTree(callback.call("{\"hostname\":[\"host-01\"],\"since\":\"1h\",\"limit\":5}"));

		assertEquals("header\nrow", result.get("hosts").get(0).get("response").get("text").asText());
		verify(configuration).setHostname(HOSTNAME);
		final ArgumentCaptor<JsonNode> arguments = ArgumentCaptor.forClass(JsonNode.class);
		verify(extension).executeTool(eq("sel"), eq(configuration), arguments.capture());
		assertEquals("1h", arguments.getValue().get("since").asText());
		// Declared as an integer, forwarded as one
		assertTrue(arguments.getValue().get("limit").isIntegralNumber());
		assertEquals(5, arguments.getValue().get("limit").asInt());
	}

	@Test
	void testTimeoutIsAppliedToTheConfigurationNotPassedToTheExtension() throws Exception {
		when(extension.executeTool(eq("sel"), eq(configuration), any())).thenReturn(new ToolText("ok"));
		final ToolCallback callback = ProtocolToolCallbacks.from(agentContextHolder.getAgentContext()).get(0);
		assertTrue(callback.getToolDefinition().inputSchema().contains("\"timeout\""));

		MAPPER.readTree(callback.call("{\"hostname\":[\"host-01\"],\"timeout\":45}"));

		verify(configuration).setTimeout(45L);
		final ArgumentCaptor<JsonNode> arguments = ArgumentCaptor.forClass(JsonNode.class);
		verify(extension).executeTool(eq("sel"), eq(configuration), arguments.capture());
		assertFalse(arguments.getValue().has("timeout"));

		// Not an integer: refused before any host is contacted
		final JsonNode bad = MAPPER.readTree(callback.call("{\"hostname\":[\"host-01\"],\"timeout\":\"soon\"}"));
		assertEquals("The argument timeout must be of type integer", bad.get("errorMessage").asText());
		final JsonNode zero = MAPPER.readTree(callback.call("{\"hostname\":[\"host-01\"],\"timeout\":0}"));
		assertEquals(
			"The timeout must be a positive number of seconds: 0",
			zero.get("hosts").get(0).get("response").get("error").asText()
		);
		verify(configuration, never()).setTimeout(0L);
	}

	@Test
	void testUnknownHostIsReported() throws Exception {
		final ToolCallback callback = ProtocolToolCallbacks.from(agentContextHolder.getAgentContext()).get(0);

		final JsonNode result = MAPPER.readTree(callback.call("{\"hostname\":[\"nope\"]}"));

		assertEquals(
			"No valid ipmi configuration found for nope.",
			result.get("hosts").get(0).get("response").get("error").asText()
		);
	}

	@Test
	void testExtensionFailureIsReported() throws Exception {
		when(extension.executeTool(eq("sel"), eq(configuration), any())).thenThrow(new IllegalStateException("timeout"));
		final ToolCallback callback = ProtocolToolCallbacks.from(agentContextHolder.getAgentContext()).get(0);

		final JsonNode result = MAPPER.readTree(callback.call("{\"hostname\":[\"host-01\"]}"));

		assertEquals(
			"An error has occurred when running the tool: timeout",
			result.get("hosts").get(0).get("response").get("error").asText()
		);
	}

	@Test
	void testExtensionFailureWithoutMessageIsNamedAfterItsException() throws Exception {
		// The timeout of a request has no message
		when(extension.executeTool(eq("sel"), eq(configuration), any())).thenThrow(new TimeoutException());
		final ToolCallback callback = ProtocolToolCallbacks.from(agentContextHolder.getAgentContext()).get(0);

		assertEquals(
			"{\"hosts\":[{\"hostname\":\"host-01\"," +
				"\"response\":{\"error\":\"An error has occurred when running the tool: TimeoutException\"}}]," +
				"\"errorMessage\":null}",
			callback.call("{\"hostname\":[\"host-01\"]}")
		);
	}

	/**
	 * What MetricsHub Central and the agent's chat receive: the typed result of each host, or its error.
	 */
	@Test
	void testJsonCarriesTheTypeOfTheResult() throws Exception {
		when(extension.executeTool(eq("sel"), eq(configuration), any())).thenReturn(
			new ToolTable(List.of("ID", "2"), List.of(List.of("1", "eth0")))
		);
		when(extension.executeTool(eq("chassis"), eq(configuration), any())).thenReturn(new ToolText("on"));
		final List<ToolCallback> callbacks = ProtocolToolCallbacks.from(agentContextHolder.getAgentContext());

		final String table = callbacks.get(0).call("{\"hostname\":[\"host-01\",\"nope\"]}");
		final String text = callbacks.get(1).call("{\"hostname\":[\"host-01\"]}");

		assertEquals(
			"{\"hosts\":[],\"errorMessage\":\"The hostname parameter is required.\"}",
			callbacks.get(0).call("{}")
		);
		assertEquals(
			"{\"hosts\":[" +
				"{\"hostname\":\"host-01\"," +
				"\"response\":{\"type\":\"table\",\"columns\":[\"ID\",\"2\"],\"rows\":[[\"1\",\"eth0\"]]}}," +
				"{\"hostname\":\"nope\",\"response\":{\"error\":\"No valid ipmi configuration found for nope.\"}}" +
				"],\"errorMessage\":null}",
			table
		);
		assertEquals(
			"{\"hosts\":[{\"hostname\":\"host-01\",\"response\":{\"type\":\"text\",\"text\":\"on\"}}],\"errorMessage\":null}",
			text
		);
	}

	/**
	 * What a client of the agent's own MCP servers reads: the result rendered, as for a connector tool.
	 */
	@Test
	void testMcpServersRenderTheResult() throws Exception {
		when(extension.executeTool(eq("sel"), eq(configuration), any())).thenReturn(
			new ToolTable(List.of("ID", "2"), List.of(List.of("1", "eth0")))
		);
		final ToolCallback callback = ProtocolToolCallbacks.from(agentContextHolder.getAgentContext()).get(0);
		assertTrue(((DynamicToolCallback) callback).isRendered());

		assertEquals(
			List.of(
				"## host-01\n\n| ID | 2 |\n| --- | --- |\n| 1 | eth0 |",
				"## nope\n\nError: No valid ipmi configuration found for nope."
			),
			McpToolRegistrySync.toSpecification(callback)
				.callHandler()
				.apply(null, new CallToolRequest("ipmi_sel", Map.of("hostname", List.of("host-01", "nope"))))
				.content()
				.stream()
				.map(content -> ((TextContent) content).text())
				.toList()
		);
	}

	private static List<String> names(final List<ToolCallback> callbacks) {
		return callbacks.stream().map(callback -> callback.getToolDefinition().name()).toList();
	}
}
