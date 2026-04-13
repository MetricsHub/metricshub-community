package org.metricshub.web.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.metricshub.web.dto.SearchMatch;
import org.metricshub.web.dto.telemetry.AgentTelemetry;
import org.metricshub.web.dto.telemetry.ConnectorTelemetry;
import org.metricshub.web.dto.telemetry.MonitorTypeTelemetry;
import org.metricshub.web.dto.telemetry.ResourceGroupTelemetry;
import org.metricshub.web.dto.telemetry.ResourceTelemetry;
import org.metricshub.web.service.ExplorerService;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class ExplorerControllerTest {

	private MockMvc mockMvc;
	private ExplorerService explorerService;

	@BeforeEach
	void setup() {
		explorerService = Mockito.mock(ExplorerService.class);
		final ExplorerController controller = new ExplorerController(explorerService);
		mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
	}

	@Test
	void testShouldReturnHierarchy() throws Exception {
		final ConnectorTelemetry connector = ConnectorTelemetry.builder().name("SNMP").build();
		connector.getMonitors().add(MonitorTypeTelemetry.builder().name("cpu").build());
		final ResourceTelemetry resource = ResourceTelemetry.builder().name("server1").build();
		resource.getConnectors().add(connector);
		final ResourceGroupTelemetry group = ResourceGroupTelemetry.builder().name("GroupA").build();
		group.getResources().add(resource);
		final AgentTelemetry root = AgentTelemetry.builder().name("AgentOne").build();
		root.getResourceGroups().add(group);
		root.getResources().add(ResourceTelemetry.builder().name("top0").build());

		when(explorerService.getHierarchy()).thenReturn(root);

		mockMvc
			.perform(get("/api/hierarchy"))
			.andExpect(status().isOk())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
			.andExpect(jsonPath("$.name").value("AgentOne"))
			.andExpect(jsonPath("$.type").value("agent"))
			.andExpect(jsonPath("$.resourceGroups[0].name").value("GroupA"))
			.andExpect(jsonPath("$.resourceGroups[0].resources[0].name").value("server1"))
			.andExpect(jsonPath("$.resourceGroups[0].resources[0].connectors[0].name").value("SNMP"))
			.andExpect(jsonPath("$.resourceGroups[0].resources[0].connectors[0].monitors[0].name").value("cpu"))
			.andExpect(jsonPath("$.resources[0].name").value("top0"));
	}

	@Test
	void testShouldReturnEmptyHierarchy() throws Exception {
		final AgentTelemetry root = AgentTelemetry.builder().name("MetricsHub").build();
		when(explorerService.getHierarchy()).thenReturn(root);

		mockMvc
			.perform(get("/api/hierarchy"))
			.andExpect(status().isOk())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
			.andExpect(jsonPath("$.name").value("MetricsHub"))
			.andExpect(jsonPath("$.resourceGroups").doesNotExist())
			.andExpect(jsonPath("$.resources").doesNotExist());
	}

	@Test
	void testShouldReturnTopLevelResource() throws Exception {
		final ConnectorTelemetry connector = ConnectorTelemetry.builder().name("SNMP").build();
		connector.getMonitors().add(MonitorTypeTelemetry.builder().name("cpu").build());
		final ResourceTelemetry resource = ResourceTelemetry.builder().name("top1").build();
		resource.getConnectors().add(connector);
		when(explorerService.getTopLevelResource("top1")).thenReturn(resource);

		mockMvc
			.perform(get("/api/resources/top1"))
			.andExpect(status().isOk())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
			.andExpect(jsonPath("$.name").value("top1"))
			.andExpect(jsonPath("$.type").value("resource"))
			.andExpect(jsonPath("$.connectors[0].name").value("SNMP"))
			.andExpect(jsonPath("$.connectors[0].monitors[0].name").value("cpu"));
	}

	@Test
	void testShouldReturnGroupedResource() throws Exception {
		final ConnectorTelemetry connector = ConnectorTelemetry.builder().name("SSH").build();
		connector.getMonitors().add(MonitorTypeTelemetry.builder().name("mem").build());
		final ResourceTelemetry resource = ResourceTelemetry.builder().name("serverA").build();
		resource.getConnectors().add(connector);
		when(explorerService.getGroupedResource("serverA", "GroupA")).thenReturn(resource);

		mockMvc
			.perform(get("/api/resource-groups/GroupA/resources/serverA"))
			.andExpect(status().isOk())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
			.andExpect(jsonPath("$.name").value("serverA"))
			.andExpect(jsonPath("$.connectors[0].name").value("SSH"))
			.andExpect(jsonPath("$.connectors[0].monitors[0].name").value("mem"));
	}

	@Test
	void testShouldSearch() throws Exception {
		final SearchMatch match1 = SearchMatch
			.builder()
			.name("serverA")
			.type("resource")
			.path("Agent/serverA")
			.jaroWinklerScore(1.0)
			.build();
		final SearchMatch match2 = SearchMatch
			.builder()
			.name("cpu")
			.type("monitor")
			.path("Agent/serverA/SNMP/cpu")
			.jaroWinklerScore(0.93)
			.build();
		when(explorerService.search("serverA")).thenReturn(java.util.List.of(match1, match2));

		mockMvc
			.perform(get("/api/search").param("q", "serverA"))
			.andExpect(status().isOk())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
			.andExpect(jsonPath("$[0].name").value("serverA"))
			.andExpect(jsonPath("$[0].type").value("resource"))
			.andExpect(jsonPath("$[0].jaroWinklerScore").value(1.0))
			.andExpect(jsonPath("$[1].name").value("cpu"))
			.andExpect(jsonPath("$[1].type").value("monitor"));
	}
}
