package org.metricshub.web.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.metricshub.agent.helper.AgentConstants.AGENT_RESOURCE_HOST_NAME_ATTRIBUTE_KEY;
import static org.metricshub.agent.helper.AgentConstants.AGENT_RESOURCE_SERVICE_NAME_ATTRIBUTE_KEY;
import static org.metricshub.agent.helper.ConfigHelper.TOP_LEVEL_VIRTUAL_RESOURCE_GROUP_KEY;
import static org.metricshub.engine.common.helpers.MetricsHubConstants.MONITOR_ATTRIBUTE_CONNECTOR_ID;
import static org.metricshub.engine.common.helpers.MetricsHubConstants.MONITOR_ATTRIBUTE_ID;
import static org.metricshub.engine.common.helpers.MetricsHubConstants.MONITOR_ATTRIBUTE_NAME;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.metricshub.agent.context.AgentContext;
import org.metricshub.engine.telemetry.Monitor;
import org.metricshub.engine.telemetry.TelemetryManager;
import org.metricshub.web.AgentContextHolder;
import org.metricshub.web.dto.AgentTelemetry;
import org.mockito.Mockito;

class ExplorerServiceTest {

	/**
	 * Creates a mocked AgentContextHolder with the given telemetry managers and
	 * agent attributes.
	 *
	 * @param telemetryManagers the telemetry managers to return
	 * @param agentAttrs        the agent attributes to return
	 * @return the mocked AgentContextHolder
	 */
	private AgentContextHolder newMockedAgentContextHolder(
		Map<String, Map<String, TelemetryManager>> telemetryManagers,
		Map<String, String> agentAttrs
	) {
		final AgentContextHolder holder = Mockito.mock(AgentContextHolder.class);
		final AgentContext agentContext = Mockito.mock(AgentContext.class, Mockito.RETURNS_DEEP_STUBS);
		when(holder.getAgentContext()).thenReturn(agentContext);
		when(agentContext.getTelemetryManagers()).thenReturn(telemetryManagers);
		when(agentContext.getAgentInfo().getAttributes()).thenReturn(agentAttrs);
		return holder;
	}

	/**
	 * Creates a real Monitor with the given id, type, and attributes.
	 *
	 * @param id    the monitor ID
	 * @param type  the monitor type
	 * @param attrs the monitor attributes
	 * @return the created Monitor
	 */
	private static Monitor newMonitor(final String id, final String type, final Map<String, String> attrs) {
		final Monitor m = new Monitor();
		m.setId(id);
		m.setType(type);
		m.setAttributes(attrs);
		return m;
	}

	/**
	 * Creates a real TelemetryManager with the given connector and monitors.
	 *
	 * @param connectorId    the connector ID
	 * @param connectorName  the connector name
	 * @param typeToMonitors a map of monitor types to lists of monitors
	 */
	private static TelemetryManager newTelemetryManager(
		final String connectorId,
		final String connectorName,
		final Map<String, List<Monitor>> typeToMonitors
	) {
		final TelemetryManager tm = new TelemetryManager();

		// Connector monitor
		final Monitor connectorMonitor = newConnector(connectorId, connectorName);

		tm.addNewMonitor(connectorMonitor, "connector", connectorId);

		typeToMonitors.forEach((type, list) ->
			IntStream.range(0, list.size()).boxed().forEach(i -> tm.addNewMonitor(list.get(i), type, type + "-" + (i + 1)))
		);

		return tm;
	}

	/**
	 * Creates a connector Monitor with the given ID and name.
	 *
	 * @param connectorId   the unique identifier for the connector.
	 * @param connectorName the name of the connector.
	 * @return The created connector Monitor.
	 */
	private static Monitor newConnector(final String connectorId, final String connectorName) {
		final Map<String, String> connAttrs = new HashMap<>();
		connAttrs.put(MONITOR_ATTRIBUTE_ID, connectorId);
		connAttrs.put(MONITOR_ATTRIBUTE_NAME, connectorName);
		return newMonitor(connectorId, "connector", connAttrs);
	}

	@Test
	void testGetHierarchyWithGroupsAndTopLevel() {
		// Prepare a resource under a group
		final String connectorId = "c1";
		final Map<String, String> cpuAttrs = Map.of(MONITOR_ATTRIBUTE_CONNECTOR_ID, connectorId);
		final Map<String, String> memAttrs = Map.of(MONITOR_ATTRIBUTE_CONNECTOR_ID, connectorId);
		final Monitor cpu = newMonitor("m1", "cpu", cpuAttrs);
		final Monitor mem = newMonitor("m2", "mem", memAttrs);
		final Monitor host = newMonitor("h1", "host", Map.of()); // filtered out
		final TelemetryManager tmGroup = newTelemetryManager(
			connectorId,
			"SNMP",
			Map.of("cpu", List.of(cpu), "mem", List.of(mem), "host", List.of(host))
		);

		// Prepare a top-level resource
		final String topConnectorId = "tc1";
		final Monitor os = newMonitor("m3", "os", Map.of(MONITOR_ATTRIBUTE_CONNECTOR_ID, topConnectorId));
		final TelemetryManager tmTop = newTelemetryManager(topConnectorId, "TopConn", Map.of("os", List.of(os)));

		final Map<String, Map<String, TelemetryManager>> telemetryManagers = new HashMap<>();
		telemetryManagers.put("GroupA", Map.of("serverA", tmGroup));
		telemetryManagers.put(TOP_LEVEL_VIRTUAL_RESOURCE_GROUP_KEY, Map.of("top1", tmTop));

		final Map<String, String> agentAttrs = new HashMap<>();
		agentAttrs.put(AGENT_RESOURCE_SERVICE_NAME_ATTRIBUTE_KEY, "AgentX");

		final ExplorerService service = new ExplorerService(newMockedAgentContextHolder(telemetryManagers, agentAttrs));

		final AgentTelemetry root = service.getHierarchy();

		assertNotNull(root, "Hierarchy root should not be null");
		assertEquals("AgentX", root.getName(), "Agent name should match attribute");
		assertEquals("agent", root.getType(), "Root type should be 'agent'");

		// Group subtree (direct typed children)
		assertEquals(1, root.getResourceGroups().size(), "Should have one resource-group");
		final AgentTelemetry group = root.getResourceGroups().get(0);
		assertEquals("resource-group", group.getType(), "Child should be of type 'resource-group'");
		assertEquals("GroupA", group.getName(), "Resource-group name should be 'GroupA'");
		assertEquals(1, group.getResources().size(), "Group should contain one resource");
		final AgentTelemetry resourceA = group.getResources().get(0);
		assertEquals("resource", resourceA.getType(), "Resource should be of type 'resource'");
		assertEquals("serverA", resourceA.getName(), "Resource name should be 'serverA'");
		assertEquals(1, resourceA.getConnectors().size(), "Resource should have one connector");
		final AgentTelemetry connector = resourceA.getConnectors().get(0);
		assertEquals("connector", connector.getType(), "Connector should be of type 'connector'");
		assertEquals("SNMP", connector.getName(), "Connector name should be 'SNMP'");
		// Types are sorted alphabetically: cpu, mem
		assertEquals(
			List.of("cpu", "mem"),
			connector.getMonitors().stream().map(AgentTelemetry::getName).toList(),
			"Monitors should include 'cpu' and 'mem'"
		);

		// Top-level resources subtree
		assertEquals(1, root.getResources().size(), "Should have one top-level resource");
		assertEquals("top1", root.getResources().get(0).getName(), "Top-level resource name should be 'top1'");
		final AgentTelemetry topConnectorNode = root.getResources().get(0).getConnectors().get(0);
		assertEquals("TopConn", topConnectorNode.getName(), "Top-level connector name should be 'TopConn'");
		assertEquals(
			List.of("os"),
			topConnectorNode.getMonitors().stream().map(AgentTelemetry::getName).toList(),
			"Top-level monitors should include 'os'"
		);
	}

	@Test
	void testGetHierarchyWithNoTelemetryManagers() {
		final Map<String, String> agentAttrs = Map.of(AGENT_RESOURCE_HOST_NAME_ATTRIBUTE_KEY, "Hosty");
		final ExplorerService service = new ExplorerService(newMockedAgentContextHolder(null, agentAttrs));
		final AgentTelemetry root = service.getHierarchy();
		assertEquals("Hosty", root.getName(), "Agent name should match host name attribute");
		assertEquals(0, root.getResourceGroups().size(), "Resource-groups should have no children");
		assertEquals(0, root.getResources().size(), "Resources should have no children");
	}

	@Test
	void testAgentNameDefaultsWhenNoAttributes() {
		final ExplorerService service = new ExplorerService(newMockedAgentContextHolder(Map.of(), Map.of()));
		final AgentTelemetry root = service.getHierarchy();
		assertEquals("MetricsHub", root.getName(), "Agent name should default to 'MetricsHub'");
	}

	@Test
	void testGetTopLevelResource() {
		final String connectorId = "cTop";
		final Monitor os = newMonitor("m3", "os", Map.of(MONITOR_ATTRIBUTE_CONNECTOR_ID, connectorId));
		final TelemetryManager tmTop = newTelemetryManager(connectorId, "TopConn", Map.of("os", List.of(os)));

		final Map<String, Map<String, TelemetryManager>> telemetryManagers = new HashMap<>();
		telemetryManagers.put(TOP_LEVEL_VIRTUAL_RESOURCE_GROUP_KEY, Map.of("top1", tmTop));

		final ExplorerService service = new ExplorerService(newMockedAgentContextHolder(telemetryManagers, Map.of()));

		final AgentTelemetry resource = service.getTopLevelResource("top1");
		assertEquals("resource", resource.getType());
		assertEquals("top1", resource.getName());
		assertEquals("TopConn", resource.getConnectors().get(0).getName());
	}

	@Test
	void testGetGroupedResource() {
		final String connectorId = "c1";
		final Monitor cpu = newMonitor("m1", "cpu", Map.of(MONITOR_ATTRIBUTE_CONNECTOR_ID, connectorId));
		final TelemetryManager tmGroup = newTelemetryManager(connectorId, "SNMP", Map.of("cpu", List.of(cpu)));

		final Map<String, Map<String, TelemetryManager>> telemetryManagers = new HashMap<>();
		telemetryManagers.put("GroupA", Map.of("serverA", tmGroup));

		final ExplorerService service = new ExplorerService(newMockedAgentContextHolder(telemetryManagers, Map.of()));

		final AgentTelemetry resource = service.getGroupedResource("serverA", "GroupA");
		assertEquals("resource", resource.getType());
		assertEquals("serverA", resource.getName());
	}
}
