package org.metricshub.web.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.metricshub.web.dto.ConfigurationFile;
import org.metricshub.web.service.ConfigurationFilesService;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class ConfigurationFilesControllerTest {

	private MockMvc mockMvc;
	private ConfigurationFilesService configurationFilesService;

	@BeforeEach
	void setup() {
		configurationFilesService = Mockito.mock(ConfigurationFilesService.class);
		final ConfigurationFilesController controller = new ConfigurationFilesController(configurationFilesService);
		mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
	}

	@Test
	void testShouldListConfigurationFiles() throws Exception {
		final ConfigurationFile f1 = ConfigurationFile
			.builder()
			.name("metricshub.yaml")
			.size(1024L)
			.lastModificationTime("2025-09-01T10:15:30Z")
			.build();

		final ConfigurationFile f2 = ConfigurationFile
			.builder()
			.name("general-settings.yaml")
			.size(256L)
			.lastModificationTime("2025-09-02T08:00:00Z")
			.build();

		when(configurationFilesService.getAllConfigurationFiles()).thenReturn(List.of(f2, f1));

		mockMvc
			.perform(get("/api/config-files"))
			.andExpect(status().isOk())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
			.andExpect(jsonPath("$.length()").value(2))
			.andExpect(jsonPath("$[0].name").value("general-settings.yaml"))
			.andExpect(jsonPath("$[0].size").value(256))
			.andExpect(jsonPath("$[0].lastModificationTime").value("2025-09-02T08:00:00Z"))
			.andExpect(jsonPath("$[1].name").value("metricshub.yaml"))
			.andExpect(jsonPath("$[1].size").value(1024))
			.andExpect(jsonPath("$[1].lastModificationTime").value("2025-09-01T10:15:30Z"));
	}

	@Test
	void testShouldReturnEmptyListWhenNoFiles() throws Exception {
		when(configurationFilesService.getAllConfigurationFiles()).thenReturn(List.of());

		mockMvc
			.perform(get("/api/config-files"))
			.andExpect(status().isOk())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
			.andExpect(content().json("[]"));
	}
}
