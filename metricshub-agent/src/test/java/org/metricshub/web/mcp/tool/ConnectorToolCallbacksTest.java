package org.metricshub.web.mcp.tool;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.metricshub.agent.context.AgentContext;
import org.metricshub.engine.configuration.HostConfiguration;
import org.metricshub.engine.connector.model.ConnectorStore;
import org.metricshub.engine.connector.model.common.DeviceKind;
import org.metricshub.engine.extension.ExtensionManager;
import org.metricshub.engine.telemetry.TelemetryManager;
import org.metricshub.web.AgentContextHolder;
import org.springframework.ai.tool.ToolCallback;

class ConnectorToolCallbacksTest {

	private static final ObjectMapper MAPPER = new ObjectMapper();
	private static final String HOSTNAME = "host-01";

	private AgentContextHolder agentContextHolder;
	private TelemetryManager telemetryManager;

	@BeforeEach
	void setUp() {
		final ConnectorStore connectorStore = new ConnectorStore(Paths.get("src", "test", "resources", "mcp", "tool"));
		telemetryManager = TelemetryManager.builder()
			.connectorStore(connectorStore)
			.hostConfiguration(
				HostConfiguration.builder().hostId(HOSTNAME).hostname(HOSTNAME).hostType(DeviceKind.LINUX).build()
			)
			.build();

		final AgentContext agentContext = mock(AgentContext.class);
		when(agentContext.getConnectorStore()).thenReturn(connectorStore);
		when(agentContext.getExtensionManager()).thenReturn(ExtensionManager.empty());
		when(agentContext.getTelemetryManagers()).thenReturn(Map.of("group", Map.of(HOSTNAME, telemetryManager)));
		agentContextHolder = new AgentContextHolder(agentContext);
	}

	@AfterEach
	void tearDown() {
		System.clearProperty(HostTools.DESTRUCTIVE_TOOLS_FLAG);
	}

	@Test
	void testDestructiveToolsNeedTheFlag() {
		assertEquals(
			List.of("TestConnectorWithTool_listThings"),
			names(ConnectorToolCallbacks.from(agentContextHolder.getAgentContext()))
		);

		System.setProperty(HostTools.DESTRUCTIVE_TOOLS_FLAG, "true");
		assertEquals(
			List.of("TestConnectorWithTool_listThings", "TestConnectorWithTool_reboot"),
			names(ConnectorToolCallbacks.from(agentContextHolder.getAgentContext()))
		);
	}

	@Test
	void testToolRunsTheConnectorSourcesOnTheHost() throws Exception {
		final ToolCallback callback = ConnectorToolCallbacks.from(agentContextHolder.getAgentContext()).get(0);
		assertEquals("Lists the things whose name matches a pattern.", callback.getToolDefinition().description());

		final JsonNode result = MAPPER.readTree(
			callback.call("{\"hostname\":[\"host-01\"],\"pattern\":\"^b\",\"owner\":\"me\"}")
		);

		final JsonNode response = result.get("hosts").get(0).get("response");
		assertEquals(List.of("name", "value"), MAPPER.convertValue(response.get("columns"), List.class));
		assertEquals(List.of(List.of("beta", "2-me")), MAPPER.convertValue(response.get("rows"), List.class));

		// The collected telemetry manager was left alone
		assertEquals(0, telemetryManager.getMonitors().size());
	}

	@Test
	void testUnknownHostIsReported() throws Exception {
		final ToolCallback callback = ConnectorToolCallbacks.from(agentContextHolder.getAgentContext()).get(0);

		final JsonNode result = MAPPER.readTree(callback.call("{\"hostname\":[\"nope\"],\"owner\":\"me\"}"));

		assertEquals("Host nope is not configured.", result.get("hosts").get(0).get("response").get("error").asText());
	}

	private static List<String> names(final List<ToolCallback> callbacks) {
		return callbacks
			.stream()
			.map(callback -> callback.getToolDefinition().name())
			.toList();
	}
}
