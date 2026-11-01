package org.metricshub.web.mcp.tool;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
import org.metricshub.agent.helper.ConfigHelper;
import org.metricshub.engine.configuration.AdditionalConnector;
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
	private static final String TEMPLATE = "TestTemplateWithTool";

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
	void testDestructiveToolsAreBuiltWhateverTheFlag() {
		// The flag applies where the tools are exposed, not where they are built: Central gets them all
		final List<ToolCallback> callbacks = ConnectorToolCallbacks.from(agentContextHolder.getAgentContext());

		assertEquals(List.of("TestConnectorWithTool_listThings", "TestConnectorWithTool_reboot"), names(callbacks));
		assertTrue(((DynamicToolCallback) callbacks.get(1)).isDestructive());
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
	void testToolsOfConnectorsWithVariablesRunWithTheVariablesOfTheHost() throws Exception {
		// What the agent does for each resource: the connectors with variables resolved in a store of its own
		final ConnectorStore resourceConnectorStore = telemetryManager.getConnectorStore().newConnectorStore();
		resourceConnectorStore.setConnectorsWithVariables(List.of(TEMPLATE));
		resourceConnectorStore.addMany(
			ConfigHelper.buildAdditionalConnectors(
				resourceConnectorStore,
				Map.of("myThing", AdditionalConnector.builder().uses(TEMPLATE).variables(Map.of("thing", "omega")).build()),
				resourceConnectorStore.getRawConnectorStore()
			).getCustomConnectorsMap()
		);
		telemetryManager.setConnectorStore(resourceConnectorStore);

		// A host without the template, under another resource
		final TelemetryManager otherTelemetryManager = TelemetryManager.builder()
			.connectorStore(new ConnectorStore(Paths.get("src", "test", "resources", "mcp", "tool")))
			.hostConfiguration(
				HostConfiguration.builder().hostId("host-02").hostname("host-02").hostType(DeviceKind.LINUX).build()
			)
			.build();
		when(agentContextHolder.getAgentContext().getTelemetryManagers()).thenReturn(
			Map.of("group", Map.of(HOSTNAME, telemetryManager, "host-02", otherTelemetryManager))
		);

		final List<ToolCallback> callbacks = ConnectorToolCallbacks.from(agentContextHolder.getAgentContext());

		assertEquals(
			List.of(
				"myThing_showThing",
				"TestConnectorWithTool_listThings",
				"TestConnectorWithTool_reboot",
				"TestTemplateWithTool_showThing"
			),
			names(callbacks)
		);

		final JsonNode result = MAPPER.readTree(callbacks.get(0).call("{\"hostname\":[\"host-01\",\"host-02\"]}"));
		assertEquals(List.of(List.of("omega")), MAPPER.convertValue(response(result, HOSTNAME).get("rows"), List.class));
		assertEquals(
			"Connector myThing has no tool showThing on host host-02.",
			response(result, "host-02").get("error").asText()
		);

		// The template itself, with its default variables
		final JsonNode defaultResult = MAPPER.readTree(callbacks.get(3).call("{\"hostname\":[\"host-01\"]}"));
		assertEquals(
			List.of(List.of("alpha")),
			MAPPER.convertValue(response(defaultResult, HOSTNAME).get("rows"), List.class)
		);
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

	private static JsonNode response(final JsonNode result, final String hostname) {
		for (final JsonNode host : result.get("hosts")) {
			if (hostname.equals(host.get("hostname").asText())) {
				return host.get("response");
			}
		}
		throw new AssertionError("No response for " + hostname + " in " + result);
	}
}
