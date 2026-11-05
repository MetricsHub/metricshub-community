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
import org.metricshub.engine.connector.model.tool.ConnectorTool;
import org.metricshub.engine.connector.model.common.DeviceKind;
import org.metricshub.engine.extension.ExtensionManager;
import org.metricshub.engine.extension.IProtocolExtension;
import org.metricshub.engine.telemetry.TelemetryManager;
import org.metricshub.engine.tool.ToolDefinition;
import org.metricshub.web.mcp.tool.ConnectorToolCallbacks;
import org.metricshub.web.mcp.tool.DynamicToolCallback;
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
		return ToolRegistrySnapshot.from(provider, Set.of("ipmi_chassis"), agentContext);
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
			agentContext
		);

		assertEquals(List.of(), snapshot.tools());
		assertFalse(snapshot.callbacks().containsKey(HostToolsLookup.NAME));
		// Nor is any tool it would have listed: a name learned before cannot reach them
		assertFalse(snapshot.callbacks().containsKey("ipmi_sel"));
	}

	@Test
	void aConnectorToolDeclaredOtherwiseOnAHostIsNotListedThere() throws Exception {
		final ConnectorStore connectorStore = agentContext.getConnectorStore();
		// The same additional connector on two hosts, its variables giving its tool another default on host-03
		final TelemetryManager omega = host("host-01", resolved(connectorStore, Map.of("myThing", "omega")), Map.of());
		final TelemetryManager delta = host("host-03", resolved(connectorStore, Map.of("myThing", "delta")), Map.of());
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

	@Test
	void aToolIsListedOnlyForTheConnectorThatDeclaresIt() throws Exception {
		// foo.bar and foo_bar yield the same tool name, and the snapshot keeps one callback for it
		final ConnectorStore both = resolved(
			agentContext.getConnectorStore(),
			Map.of("foo.bar", "omega", "foo_bar", "omega")
		);
		final TelemetryManager dotted = host("host-01", both, Map.of());
		final TelemetryManager underscored = host("host-03", both, Map.of());
		dotted.getHostProperties().getConnectorNamespace("foo.bar").setStatusOk(true);
		underscored.getHostProperties().getConnectorNamespace("foo_bar").setStatusOk(true);
		final Map<String, TelemetryManager> resources = new LinkedHashMap<>();
		resources.put("resource-0", dotted);
		resources.put("resource-1", underscored);
		when(agentContext.getTelemetryManagers()).thenReturn(Map.of("group", resources));

		final ToolRegistrySnapshot snapshot = snapshot();
		final String owner = ((DynamicToolCallback) snapshot.callbacks().get("foo_bar_showThing")).getOwner();

		// Only the host whose detected connector owns the kept callback is offered it: on the other, it would run
		// another connector than the one detected there
		final String ownersHost = "foo.bar".equals(owner) ? "host-01" : "host-03";
		final String otherHost = "foo.bar".equals(owner) ? "host-03" : "host-01";
		assertEquals(List.of("foo_bar_showThing=read"), toolsOf(snapshot, ownersHost));
		assertEquals(List.of(), toolsOf(snapshot, otherHost));
	}

	@Test
	void aConnectorToolSpelledOtherwiseOnAHostIsNotListedThere() throws Exception {
		// host-03's store has its own copy of the connector, whose tool is spelled ListThings: a connector's tools are
		// looked up by exact name, so the callback, built for listThings, finds nothing to run there
		final ConnectorStore otherSpelling = new ConnectorStore(Paths.get("src", "test", "resources", "mcp", "tool"));
		final Map<String, ConnectorTool> tools = otherSpelling.getStore().get(CONNECTOR).getTools();
		final Map<String, ConnectorTool> respelled = new LinkedHashMap<>();
		tools.forEach((name, tool) -> respelled.put("listThings".equals(name) ? "ListThings" : name, tool));
		tools.clear();
		tools.putAll(respelled);
		final TelemetryManager host03 = host("host-03", otherSpelling, Map.of());
		host03.getHostProperties().getConnectorNamespace(CONNECTOR).setStatusOk(true);
		when(agentContext.getTelemetryManagers()).thenReturn(Map.of("group", Map.of("host-03", host03)));

		assertEquals(List.of("TestConnectorWithTool_reboot=write"), toolsOf(snapshot(), "host-03"));
	}

	/**
	 * @return a copy of the store with additional connectors using the template, each with its thing
	 */
	private static ConnectorStore resolved(final ConnectorStore connectorStore, final Map<String, String> things) {
		final ConnectorStore resourceConnectorStore = connectorStore.newConnectorStore();
		resourceConnectorStore.setConnectorsWithVariables(List.of("TestTemplateWithTool"));
		final Map<String, AdditionalConnector> additional = new LinkedHashMap<>();
		things.forEach(
			(id, thing) -> additional.put(
				id,
				AdditionalConnector.builder().uses("TestTemplateWithTool").variables(Map.of("thing", thing)).build()
			)
		);
		resourceConnectorStore.addMany(
			ConfigHelper.buildAdditionalConnectors(
				resourceConnectorStore,
				additional,
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
