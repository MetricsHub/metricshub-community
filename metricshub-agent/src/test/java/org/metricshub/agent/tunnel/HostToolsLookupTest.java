package org.metricshub.agent.tunnel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.metricshub.agent.context.AgentContext;
import org.metricshub.agent.helper.ConfigHelper;
import org.metricshub.agent.tunnel.protocol.ToolDescriptor;
import org.metricshub.engine.configuration.AdditionalConnector;
import org.metricshub.engine.configuration.HostConfiguration;
import org.metricshub.engine.configuration.IConfiguration;
import org.metricshub.engine.connector.model.ConnectorStore;
import org.metricshub.engine.connector.model.common.DeviceKind;
import org.metricshub.engine.extension.ExtensionManager;
import org.metricshub.engine.extension.IProtocolExtension;
import org.metricshub.engine.telemetry.TelemetryManager;
import org.metricshub.engine.tool.ToolDefinition;
import org.metricshub.web.mcp.tool.ConnectorToolCallbacks;
import org.metricshub.web.mcp.tool.ProtocolToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;

/**
 * The tools of one host, as Central asks for them over the tunnel.
 */
class HostToolsLookupTest {

	private static final ObjectMapper MAPPER = new ObjectMapper();
	private static final String CONNECTOR = "TestConnectorWithTool";

	private AgentContext agentContext;

	@BeforeEach
	void setUp() {
		final IProtocolExtension ipmi = mock(IProtocolExtension.class);
		when(ipmi.getIdentifier()).thenReturn("ipmi");
		when(ipmi.getTools()).thenReturn(
			List.of(
				new ToolDefinition("sel", "Reads the System Event Log.", Map.of(), false),
				new ToolDefinition("chassis", "Powers the chassis.", Map.of(), true)
			)
		);
		final IConfiguration ipmiConfiguration = mock(IConfiguration.class);
		when(ipmiConfiguration.copy()).thenReturn(ipmiConfiguration);
		when(ipmi.isToolConfiguration(ipmiConfiguration)).thenReturn(true);

		final ConnectorStore connectorStore = new ConnectorStore(Paths.get("src", "test", "resources", "mcp", "tool"));
		// host-01: IPMI configured, and the connector detected
		final TelemetryManager host01 = host("host-01", connectorStore, Map.of(IConfiguration.class, ipmiConfiguration));
		host01.getHostProperties().getConnectorNamespace(CONNECTOR).setStatusOk(true);
		// host-02: neither, though the connector is in its store and was tried
		final TelemetryManager host02 = host("host-02", connectorStore, Map.of());
		host02.getHostProperties().getConnectorNamespace(CONNECTOR).setStatusOk(false);

		agentContext = mock(AgentContext.class);
		when(agentContext.getConnectorStore()).thenReturn(connectorStore);
		when(agentContext.getExtensionManager()).thenReturn(
			ExtensionManager.builder().withProtocolExtensions(List.of(ipmi)).build()
		);
		when(agentContext.getTelemetryManagers()).thenReturn(Map.of("group", Map.of("host-01", host01, "host-02", host02)));
	}

	private static TelemetryManager host(
		final String hostname,
		final ConnectorStore connectorStore,
		final Map<Class<? extends IConfiguration>, IConfiguration> configurations
	) {
		return TelemetryManager.builder()
			.connectorStore(connectorStore)
			.hostConfiguration(
				HostConfiguration.builder()
					.hostId(hostname)
					.hostname(hostname)
					.hostType(DeviceKind.LINUX)
					.configurations(configurations)
					.build()
			)
			.build();
	}

	/**
	 * @return the snapshot Central is advertised, built from the agent's dynamic tools
	 */
	private ToolRegistrySnapshot snapshot() {
		final List<ToolCallback> callbacks = new ArrayList<>(ProtocolToolCallbacks.from(agentContext));
		callbacks.addAll(ConnectorToolCallbacks.from(agentContext));
		final ToolCallbackProvider provider = mock(ToolCallbackProvider.class);
		when(provider.getToolCallbacks()).thenReturn(callbacks.toArray(ToolCallback[]::new));
		return ToolRegistrySnapshot.from(provider, Set.of("ipmi_chassis"), () -> agentContext);
	}

	private List<String> toolsOf(final ToolRegistrySnapshot snapshot, final String hostname) throws Exception {
		final JsonNode answer = MAPPER.readTree(
			snapshot.callbacks().get(HostToolsLookup.NAME).call("{\"hostname\":\"" + hostname + "\"}")
		);
		assertEquals(hostname, answer.get("hostname").asText());
		final List<String> tools = new ArrayList<>();
		answer.get("tools").forEach(tool -> tools.add(tool.get("name").asText() + "=" + tool.get("access").asText()));
		return tools;
	}

	@Test
	void aHostIsToldTheToolsOfItsProtocolsAndOfItsDetectedConnectors() throws Exception {
		final ToolRegistrySnapshot snapshot = snapshot();

		// ipmi_chassis is excluded from the tunnel, so not offered through the host either
		assertEquals(
			List.of("ipmi_sel=read", "TestConnectorWithTool_listThings=read", "TestConnectorWithTool_reboot=write"),
			toolsOf(snapshot, "host-01")
		);
		assertEquals(List.of(), toolsOf(snapshot, "host-02"), "no protocol of its, no connector detected");
	}

	@Test
	void theRegistrationAdvertisesTheLookupAndNotTheHostTools() {
		final ToolRegistrySnapshot snapshot = snapshot();

		assertEquals(
			List.of(HostToolsLookup.NAME + "=read"),
			snapshot.tools().stream().map(tool -> tool.name() + "=" + tool.access()).toList()
		);
		// Still invokable: Central calls them by name once the host told it they apply
		assertTrue(snapshot.callbacks().containsKey("ipmi_sel"));
		assertTrue(snapshot.callbacks().containsKey("TestConnectorWithTool_reboot"));
		assertFalse(snapshot.callbacks().containsKey("ipmi_chassis"));
	}

	@Test
	void aHostThisAgentDoesNotMonitorIsRefused() {
		final ToolCallback lookup = snapshot().callbacks().get(HostToolsLookup.NAME);

		// An unknown host is an execution error, a missing name an invalid argument: the bridge maps them so
		final IllegalStateException refused = assertThrows(
			IllegalStateException.class,
			() -> lookup.call("{\"hostname\":\"host-99\"}")
		);
		assertEquals("Host host-99 is not configured.", refused.getMessage());
		assertThrows(IllegalArgumentException.class, () -> lookup.call("{}"));
	}

	@Test
	void theLookupCanBeExcludedLikeAnyTool() {
		// Built first: a mock may not be called while another is being stubbed
		final ToolCallback[] callbacks = ProtocolToolCallbacks.from(agentContext).toArray(ToolCallback[]::new);
		final ToolCallbackProvider provider = mock(ToolCallbackProvider.class);
		when(provider.getToolCallbacks()).thenReturn(callbacks);

		final ToolRegistrySnapshot snapshot = ToolRegistrySnapshot.from(
			provider,
			Set.of(HostToolsLookup.NAME),
			() -> agentContext
		);

		assertEquals(List.of(), snapshot.tools());
		assertFalse(snapshot.callbacks().containsKey(HostToolsLookup.NAME));
	}

	@Test
	void aConnectorToolDeclaredOtherwiseOnAHostIsNotListedThere() throws Exception {
		final ConnectorStore connectorStore = agentContext.getConnectorStore();
		// The same additional connector on two hosts, its variables giving its tool another default on host-03
		final TelemetryManager omega = host("host-01", resolved(connectorStore, "omega"), Map.of());
		final TelemetryManager delta = host("host-03", resolved(connectorStore, "delta"), Map.of());
		omega.getHostProperties().getConnectorNamespace("myThing").setStatusOk(true);
		delta.getHostProperties().getConnectorNamespace("myThing").setStatusOk(true);
		final Map<String, TelemetryManager> resources = new LinkedHashMap<>();
		resources.put("resource-0", omega);
		resources.put("resource-1", delta);
		when(agentContext.getTelemetryManagers()).thenReturn(Map.of("group", resources));

		final ToolRegistrySnapshot snapshot = snapshot();

		// The tool was built from host-01's declaration, the first one: host-03 would be refused every call
		assertEquals(List.of("myThing_showThing=read"), toolsOf(snapshot, "host-01"));
		assertEquals(List.of(), toolsOf(snapshot, "host-03"));
	}

	/**
	 * @return a copy of the store with an additional connector myThing using the template with the given thing
	 */
	private static ConnectorStore resolved(final ConnectorStore connectorStore, final String thing) {
		final ConnectorStore resourceConnectorStore = connectorStore.newConnectorStore();
		resourceConnectorStore.setConnectorsWithVariables(List.of("TestTemplateWithTool"));
		resourceConnectorStore.addMany(
			ConfigHelper.buildAdditionalConnectors(
				resourceConnectorStore,
				Map.of(
					"myThing",
					AdditionalConnector.builder().uses("TestTemplateWithTool").variables(Map.of("thing", thing)).build()
				),
				resourceConnectorStore.getRawConnectorStore()
			).getCustomConnectorsMap()
		);
		return resourceConnectorStore;
	}

	@Test
	void eachToolComesWithItsSchema() throws Exception {
		final JsonNode answer = MAPPER.readTree(
			snapshot().callbacks().get(HostToolsLookup.NAME).call("{\"hostname\":\"host-01\"}")
		);

		final ToolDescriptor sel = MAPPER.treeToValue(answer.get("tools").get(0), ToolDescriptor.class);
		assertEquals("ipmi_sel", sel.name());
		assertEquals("array", sel.inputSchema().get("properties").get("hostname").get("type").asText());
	}
}
