package org.metricshub.web.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.metricshub.agent.helper.AgentConstants.AGENT_RESOURCE_HOST_NAME_ATTRIBUTE_KEY;
// Static imports of constants used by ExplorerService
import static org.metricshub.agent.helper.AgentConstants.AGENT_RESOURCE_SERVICE_NAME_ATTRIBUTE_KEY;
import static org.metricshub.agent.helper.ConfigHelper.TOP_LEVEL_VIRTUAL_RESOURCE_GROUP_KEY;
import static org.metricshub.engine.common.helpers.MetricsHubConstants.MONITOR_ATTRIBUTE_CONNECTOR_ID;
import static org.metricshub.engine.common.helpers.MetricsHubConstants.MONITOR_ATTRIBUTE_ID;
import static org.metricshub.engine.common.helpers.MetricsHubConstants.MONITOR_ATTRIBUTE_NAME;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.metricshub.agent.context.AgentContext;
import org.metricshub.engine.telemetry.Monitor;
import org.metricshub.engine.telemetry.TelemetryManager;
import org.metricshub.web.AgentContextHolder;
import org.metricshub.web.dto.AgentTelemetry;
import org.mockito.Mockito;

class ExplorerServiceTest {

	private AgentContextHolder holderWithContext(
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

	private static Monitor newMonitor(String id, String type, Map<String, String> attrs) {
		final Monitor m = Mockito.mock(Monitor.class);
		when(m.getId()).thenReturn(id);
		when(m.getType()).thenReturn(type);
		when(m.getAttributes()).thenReturn(attrs);
		return m;
	}

	private static TelemetryManager tmWith(
		String connectorId,
		String connectorName,
		Map<String, List<Monitor>> typeToMonitors
	) {
		final TelemetryManager tm = Mockito.mock(TelemetryManager.class);

		// Connector monitor
		final Map<String, String> connAttrs = new HashMap<>();
		connAttrs.put(MONITOR_ATTRIBUTE_ID, connectorId);
		connAttrs.put(MONITOR_ATTRIBUTE_NAME, connectorName);
		final Monitor connectorMonitor = newMonitor(connectorId, "connector", connAttrs);
		when(tm.findMonitorsByType(anyString())).thenReturn(Map.of(connectorId, connectorMonitor));

		// Flatten map expected by getMonitors(): Map<String, Map<String, Monitor>>
		final Map<String, Map<String, Monitor>> monitors = new HashMap<>();
		// include connector list too
		monitors.put("connector", Map.of(connectorId, connectorMonitor));
		for (Map.Entry<String, List<Monitor>> e : typeToMonitors.entrySet()) {
			final Map<String, Monitor> inner = new HashMap<>();
			int i = 0;
			for (Monitor m : e.getValue()) {
				inner.put(e.getKey() + "-" + (++i), m);
			}
			monitors.put(e.getKey(), inner);
		}
		when(tm.getMonitors()).thenReturn(monitors);
		return tm;
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
		final TelemetryManager tmGroup = tmWith(
			connectorId,
			"SNMP",
			Map.of("cpu", List.of(cpu), "mem", List.of(mem), "host", List.of(host))
		);

		// Prepare a top-level resource
		final String topConnectorId = "tc1";
		final Monitor os = newMonitor("m3", "os", Map.of(MONITOR_ATTRIBUTE_CONNECTOR_ID, topConnectorId));
		final TelemetryManager tmTop = tmWith(topConnectorId, "TopConn", Map.of("os", List.of(os)));

		final Map<String, Map<String, TelemetryManager>> telemetryManagers = new HashMap<>();
		telemetryManagers.put("GroupA", Map.of("serverA", tmGroup));
		telemetryManagers.put(TOP_LEVEL_VIRTUAL_RESOURCE_GROUP_KEY, Map.of("top1", tmTop));

		final Map<String, String> agentAttrs = new HashMap<>();
		agentAttrs.put(AGENT_RESOURCE_SERVICE_NAME_ATTRIBUTE_KEY, "AgentX");

		final ExplorerService service = new ExplorerService(holderWithContext(telemetryManagers, agentAttrs));

		final AgentTelemetry root = service.getHierarchy();
		assertNotNull(root);
		assertEquals("AgentX", root.getName());
		assertEquals("agent", root.getType());
		// Two top-level containers
		assertEquals(2, root.getChildren().size());
		assertEquals("resource-groups", root.getChildren().get(0).getName());
		assertEquals("resources", root.getChildren().get(1).getName());

		// Group subtree
		final AgentTelemetry groups = root.getChildren().get(0);
		assertEquals(1, groups.getChildren().size());
		assertEquals("resource-group", groups.getChildren().get(0).getType());
		assertEquals("GroupA", groups.getChildren().get(0).getName());
		final AgentTelemetry groupResources = groups.getChildren().get(0).getChildren().get(0);
		assertEquals("resources", groupResources.getName());
		assertEquals(1, groupResources.getChildren().size());
		final AgentTelemetry resourceA = groupResources.getChildren().get(0);
		assertEquals("resource", resourceA.getType());
		assertEquals("serverA", resourceA.getName());
		final AgentTelemetry connectorsA = resourceA.getChildren().get(0);
		assertEquals("connectors", connectorsA.getName());
		assertEquals(1, connectorsA.getChildren().size());
		final AgentTelemetry connector = connectorsA.getChildren().get(0);
		assertEquals("connector", connector.getType());
		assertEquals("SNMP", connector.getName());
		final AgentTelemetry monitorsA = connector.getChildren().get(0);
		assertEquals("monitors", monitorsA.getName());
		// Types are sorted alphabetically: cpu, mem
		assertEquals(List.of("cpu", "mem"), monitorsA.getChildren().stream().map(AgentTelemetry::getName).toList());

		// Top-level resources subtree
		final AgentTelemetry topResources = root.getChildren().get(1);
		assertEquals(1, topResources.getChildren().size());
		assertEquals("top1", topResources.getChildren().get(0).getName());
		final AgentTelemetry topConnectorNode = topResources.getChildren().get(0).getChildren().get(0).getChildren().get(0);
		assertEquals("TopConn", topConnectorNode.getName());
		final AgentTelemetry topMonitors = topConnectorNode.getChildren().get(0);
		assertEquals(List.of("os"), topMonitors.getChildren().stream().map(AgentTelemetry::getName).toList());
	}

	@Test
	void testGetHierarchyWithNoTelemetryManagers() {
		final Map<String, String> agentAttrs = Map.of(AGENT_RESOURCE_HOST_NAME_ATTRIBUTE_KEY, "Hosty");
		final ExplorerService service = new ExplorerService(holderWithContext(null, agentAttrs));
		final AgentTelemetry root = service.getHierarchy();
		assertEquals("Hosty", root.getName());
		assertEquals(2, root.getChildren().size());
		assertEquals(0, root.getChildren().get(0).getChildren().size());
		assertEquals(0, root.getChildren().get(1).getChildren().size());
	}

	@Test
	void testAgentNameDefaultsWhenNoAttributes() {
		final ExplorerService service = new ExplorerService(holderWithContext(Map.of(), Map.of()));
		final AgentTelemetry root = service.getHierarchy();
		assertEquals("MetricsHub", root.getName());
	}

	@Test
	void testGetResourcesAggregatesAll() {
		// Group resource
		final String connectorId = "c1";
		final Monitor cpu = newMonitor("m1", "cpu", Map.of(MONITOR_ATTRIBUTE_CONNECTOR_ID, connectorId));
		final Monitor mem = newMonitor("m2", "mem", Map.of(MONITOR_ATTRIBUTE_CONNECTOR_ID, connectorId));
		final Monitor host = newMonitor("h1", "host", Map.of()); // should be excluded
		final TelemetryManager tmGroup = tmWith(
			connectorId,
			"SNMP",
			Map.of("cpu", List.of(cpu), "mem", List.of(mem), "host", List.of(host))
		);

		// Top-level resource
		final String topConnectorId = "tc1";
		final Monitor os = newMonitor("m3", "os", Map.of(MONITOR_ATTRIBUTE_CONNECTOR_ID, topConnectorId));
		final TelemetryManager tmTop = tmWith(topConnectorId, "TopConn", Map.of("os", List.of(os)));

		final Map<String, Map<String, TelemetryManager>> telemetryManagers = new HashMap<>();
		telemetryManagers.put("GroupA", Map.of("serverA", tmGroup));
		telemetryManagers.put(TOP_LEVEL_VIRTUAL_RESOURCE_GROUP_KEY, Map.of("top1", tmTop));

		final ExplorerService service = new ExplorerService(holderWithContext(telemetryManagers, Map.of()));
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
		final ExplorerService service = new ExplorerService(holderWithContext(null, Map.of()));
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

		final ExplorerService service = new ExplorerService(holderWithContext(telemetryManagers, Map.of()));
		final AgentTelemetry resources = service.getResources();

		assertEquals(1, resources.getChildren().size());
		final AgentTelemetry res1 = resources.getChildren().get(0);
		assertEquals("res1", res1.getName());
		final AgentTelemetry connectors = res1.getChildren().get(0);
		assertEquals("connectors", connectors.getName());
		assertEquals(0, connectors.getChildren().size());
	}
}
