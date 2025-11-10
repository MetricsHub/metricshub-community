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
		// Two top-level containers
		assertEquals(2, root.getChildren().size(), "Root should have two children");
		assertEquals("resource-groups", root.getChildren().get(0).getName(), "First child should be 'resource-groups'");
		assertEquals("resources", root.getChildren().get(1).getName(), "Second child should be 'resources'");

		// Group subtree
		final AgentTelemetry groups = root.getChildren().get(0);
		assertEquals(1, groups.getChildren().size(), "Resource-groups should have one child");
		assertEquals("resource-group", groups.getChildren().get(0).getType(), "Child should be of type 'resource-group'");
		assertEquals("GroupA", groups.getChildren().get(0).getName(), "Resource-group name should be 'GroupA'");
		final AgentTelemetry groupResources = groups.getChildren().get(0).getChildren().get(0);
		assertEquals("resources", groupResources.getName(), "Resource-group should have 'resources' child");
		assertEquals(1, groupResources.getChildren().size(), "Resources should have one child");
		final AgentTelemetry resourceA = groupResources.getChildren().get(0);
		assertEquals("resource", resourceA.getType(), "Resource should be of type 'resource'");
		assertEquals("serverA", resourceA.getName(), "Resource name should be 'serverA'");
		final AgentTelemetry connectorsA = resourceA.getChildren().get(0);
		assertEquals("connectors", connectorsA.getName(), "Resource should have 'connectors' child");
		assertEquals(1, connectorsA.getChildren().size(), "Connectors should have one child");
		final AgentTelemetry connector = connectorsA.getChildren().get(0);
		assertEquals("connector", connector.getType(), "Connector should be of type 'connector'");
		assertEquals("SNMP", connector.getName(), "Connector name should be 'SNMP'");
		final AgentTelemetry monitorsA = connector.getChildren().get(0);
		assertEquals("monitors", monitorsA.getName(), "Connector should have 'monitors' child");
		// Types are sorted alphabetically: cpu, mem
		assertEquals(
			List.of("cpu", "mem"),
			monitorsA.getChildren().stream().map(AgentTelemetry::getName).toList(),
			"Monitors should include 'cpu' and 'mem'"
		);

		// Top-level resources subtree
		final AgentTelemetry topResources = root.getChildren().get(1);
		assertEquals(1, topResources.getChildren().size(), "Top-level resources should have one child");
		assertEquals("top1", topResources.getChildren().get(0).getName(), "Top-level resource name should be 'top1'");
		final AgentTelemetry topConnectorNode = topResources.getChildren().get(0).getChildren().get(0).getChildren().get(0);
		assertEquals("TopConn", topConnectorNode.getName(), "Top-level connector name should be 'TopConn'");
		final AgentTelemetry topMonitors = topConnectorNode.getChildren().get(0);
		assertEquals(
			List.of("os"),
			topMonitors.getChildren().stream().map(AgentTelemetry::getName).toList(),
			"Top-level monitors should include 'os'"
		);
	}

	@Test
	void testGetHierarchyWithNoTelemetryManagers() {
		final Map<String, String> agentAttrs = Map.of(AGENT_RESOURCE_HOST_NAME_ATTRIBUTE_KEY, "Hosty");
		final ExplorerService service = new ExplorerService(newMockedAgentContextHolder(null, agentAttrs));
		final AgentTelemetry root = service.getHierarchy();
		assertEquals("Hosty", root.getName(), "Agent name should match host name attribute");
		assertEquals(2, root.getChildren().size(), "Root should have two children");
		assertEquals(0, root.getChildren().get(0).getChildren().size(), "Resource-groups should have no children");
		assertEquals(0, root.getChildren().get(1).getChildren().size(), "Resources should have no children");
	}

	@Test
	void testAgentNameDefaultsWhenNoAttributes() {
		final ExplorerService service = new ExplorerService(newMockedAgentContextHolder(Map.of(), Map.of()));
		final AgentTelemetry root = service.getHierarchy();
		assertEquals("MetricsHub", root.getName(), "Agent name should default to 'MetricsHub'");
	}

	@Test
	void testGetResourcesAggregatesAll() {
		// Group resource
		final String connectorId = "c1";
		final Monitor cpu = newMonitor("m1", "cpu", Map.of(MONITOR_ATTRIBUTE_CONNECTOR_ID, connectorId));
		final Monitor mem = newMonitor("m2", "mem", Map.of(MONITOR_ATTRIBUTE_CONNECTOR_ID, connectorId));
		final Monitor host = newMonitor("h1", "host", Map.of()); // should be excluded
		final Map<String, List<Monitor>> groupTypeToMonitors2 = Map.<String, List<Monitor>>of(
			"cpu",
			List.<Monitor>of(cpu),
			"mem",
			List.<Monitor>of(mem),
			"host",
			List.<Monitor>of(host)
		);
		final TelemetryManager tmGroup = newTelemetryManager(connectorId, "SNMP", groupTypeToMonitors2);

		// Top-level resource
		final String topConnectorId = "tc1";
		final Monitor os = newMonitor("m3", "os", Map.of(MONITOR_ATTRIBUTE_CONNECTOR_ID, topConnectorId));
		final Map<String, List<Monitor>> topTypeToMonitors2 = Map.<String, List<Monitor>>of("os", List.<Monitor>of(os));
		final TelemetryManager tmTop = newTelemetryManager(topConnectorId, "TopConn", topTypeToMonitors2);

		final Map<String, Map<String, TelemetryManager>> telemetryManagers = new HashMap<>();
		telemetryManagers.put("GroupA", Map.of("serverA", tmGroup));
		telemetryManagers.put(TOP_LEVEL_VIRTUAL_RESOURCE_GROUP_KEY, Map.of("top1", tmTop));

		final ExplorerService service = new ExplorerService(
			newMockedAgentContextHolder(telemetryManagers, Map.<String, String>of())
		);
		final AgentTelemetry resources = service.getResources();

		assertNotNull(resources);
		assertEquals("resources", resources.getName());
		assertEquals("resources", resources.getType());
		// Contains both resources (order not enforced across groups)
		final List<String> resourceNames = resources.getChildren().stream().map(AgentTelemetry::getName).toList();
		assertEquals(2, resourceNames.size());
		// verify presence
		org.junit.jupiter.api.Assertions.assertTrue(resourceNames.containsAll(List.of("serverA", "top1")));

		// Validate a resource subtree (serverA)
		final AgentTelemetry resourceA = resources
			.getChildren()
			.stream()
			.filter(r -> r.getName().equals("serverA"))
			.findFirst()
			.orElseThrow();
		assertEquals("resource", resourceA.getType());
		final AgentTelemetry connectorsA = resourceA.getChildren().get(0);
		assertEquals("connectors", connectorsA.getName());
		assertEquals(1, connectorsA.getChildren().size());
		final AgentTelemetry connectorNode = connectorsA.getChildren().get(0);
		assertEquals("connector", connectorNode.getType());
		assertEquals("SNMP", connectorNode.getName());
		final AgentTelemetry monitorsA = connectorNode.getChildren().get(0);
		assertEquals("monitors", monitorsA.getName());
		assertEquals(List.of("cpu", "mem"), monitorsA.getChildren().stream().map(AgentTelemetry::getName).toList());

		// Validate top1 subtree
		final AgentTelemetry top1 = resources
			.getChildren()
			.stream()
			.filter(r -> r.getName().equals("top1"))
			.findFirst()
			.orElseThrow();
		final AgentTelemetry topConnContainer = top1.getChildren().get(0);
		final AgentTelemetry topConn = topConnContainer.getChildren().get(0);
		assertEquals("TopConn", topConn.getName());
		final AgentTelemetry topMonitors = topConn.getChildren().get(0);
		assertEquals(List.of("os"), topMonitors.getChildren().stream().map(AgentTelemetry::getName).toList());
	}

	@Test
	void testGetResourcesWithNoTelemetryManagers() {
		final ExplorerService service = new ExplorerService(newMockedAgentContextHolder(null, Map.<String, String>of()));
		final AgentTelemetry resources = service.getResources();
		assertNotNull(resources);
		assertEquals("resources", resources.getName());
		assertEquals(0, resources.getChildren().size());
	}

	@Test
	void testGetResourcesWithNoConnectorMonitors() {
		final TelemetryManager tm = Mockito.mock(TelemetryManager.class);
		when(tm.findMonitorsByType(Mockito.anyString())).thenReturn(Map.of());
		when(tm.getMonitors()).thenReturn(Map.of());

		final Map<String, Map<String, TelemetryManager>> telemetryManagers = new HashMap<>();
		telemetryManagers.put("GroupX", Map.of("res1", tm));

		final ExplorerService service = new ExplorerService(
			newMockedAgentContextHolder(telemetryManagers, Map.<String, String>of())
		);
		final AgentTelemetry resources = service.getResources();

		assertEquals(1, resources.getChildren().size());
		final AgentTelemetry res1 = resources.getChildren().get(0);
		assertEquals("res1", res1.getName());
		final AgentTelemetry connectors = res1.getChildren().get(0);
		assertEquals("connectors", connectors.getName());
		assertEquals(0, connectors.getChildren().size());
	}
}
