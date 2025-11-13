package org.metricshub.web.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.metricshub.web.dto.AgentTelemetry;
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
		final AgentTelemetry monitors = AgentTelemetry.builder().name("monitors").type("monitors").build();
		final AgentTelemetry connector = AgentTelemetry.builder().name("SNMP").type("connector").build();
		connector.getChildren().add(monitors);
		final AgentTelemetry connectors = AgentTelemetry.builder().name("connectors").type("connectors").build();
		connectors.getChildren().add(connector);
		final AgentTelemetry resource = AgentTelemetry.builder().name("server1").type("resource").build();
		resource.getChildren().add(connectors);
		final AgentTelemetry resources = AgentTelemetry.builder().name("resources").type("resources").build();
		resources.getChildren().add(resource);
		final AgentTelemetry groups = AgentTelemetry.builder().name("resource-groups").type("resource-groups").build();
		final AgentTelemetry root = AgentTelemetry.builder().name("AgentOne").type("agent").build();
		root.getChildren().add(groups);
		root.getChildren().add(resources);

		when(explorerService.getHierarchy()).thenReturn(root);

		mockMvc
			.perform(get("/api/hierarchy"))
			.andExpect(status().isOk())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
			.andExpect(jsonPath("$.name").value("AgentOne"))
			.andExpect(jsonPath("$.type").value("agent"))
			.andExpect(jsonPath("$.children[0].name").value("resource-groups"))
			.andExpect(jsonPath("$.children[1].name").value("resources"))
			.andExpect(jsonPath("$.children[1].children[0].name").value("server1"))
			.andExpect(jsonPath("$.children[1].children[0].children[0].name").value("connectors"))
			.andExpect(jsonPath("$.children[1].children[0].children[0].children[0].name").value("SNMP"))
			.andExpect(jsonPath("$.children[1].children[0].children[0].children[0].children[0].name").value("monitors"));
	}

	@Test
	void testShouldReturnEmptyHierarchy() throws Exception {
		final AgentTelemetry root = AgentTelemetry.builder().name("MetricsHub").type("agent").build();
		root.getChildren().add(AgentTelemetry.builder().name("resource-groups").type("resource-groups").build());
		root.getChildren().add(AgentTelemetry.builder().name("resources").type("resources").build());
		when(explorerService.getHierarchy()).thenReturn(root);

		mockMvc
			.perform(get("/api/hierarchy"))
			.andExpect(status().isOk())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
			.andExpect(jsonPath("$.name").value("MetricsHub"))
			.andExpect(jsonPath("$.children.length()").value(2))
			.andExpect(jsonPath("$.children[0].children.length()").value(0))
			.andExpect(jsonPath("$.children[1].children.length()").value(0));
	}
}
