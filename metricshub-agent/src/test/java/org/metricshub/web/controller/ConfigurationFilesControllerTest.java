package org.metricshub.web.controller;

import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.metricshub.web.dto.ConfigurationFile;
import org.metricshub.web.service.ConfigurationFilesService;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;

class ConfigurationFilesControllerTest {

	private MockMvc mockMvc;
	private ConfigurationFilesService configurationFilesService;

	private final ObjectMapper mapper = new ObjectMapper();

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

	@Test
	void testShouldGetFileContent() throws Exception {
		when(configurationFilesService.getFileContent("metricshub.yaml")).thenReturn("key: value\n");

		mockMvc
			.perform(get("/api/config-files/metricshub.yaml").accept(MediaType.TEXT_PLAIN))
			.andExpect(status().isOk())
			.andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_PLAIN))
			.andExpect(content().string("key: value\n"));
	}

	@Test
	void testGetFileContentNotFound() throws Exception {
		when(configurationFilesService.getFileContent("missing.yaml"))
			.thenThrow(new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "Not Found"));

		mockMvc.perform(get("/api/config-files/missing.yaml")).andExpect(status().isNotFound());
	}

	@Test
	void testShouldSaveOrUpdateFile() throws Exception {
		doNothing().when(configurationFilesService).saveOrUpdateFile("metricshub.yaml", "test: noo\n");

		mockMvc
			.perform(
				put("/api/config-files/metricshub.yaml?skipValidation=true")
					.contentType(MediaType.TEXT_PLAIN)
					.content("test: noo\n".getBytes(StandardCharsets.UTF_8))
			)
			.andExpect(status().isOk())
			.andExpect(content().string("Configuration file saved successfully."));

		verify(configurationFilesService).saveOrUpdateFile("metricshub.yaml", "test: noo\n");
	}

	@Test
	void testShouldValidateProvidedYaml() throws Exception {
		ObjectNode result = JsonNodeFactory.instance.objectNode();
		result.put("valid", true).putArray("errors");

		when(configurationFilesService.validate("a: 1\n", "metricshub.yaml"))
			.thenReturn(ConfigurationFilesService.Validation.ok("metricshub.yaml"));

		mockMvc
			.perform(
				post("/api/config-files/metricshub.yaml")
					.contentType(MediaType.TEXT_PLAIN)
					.content("a: 1\n")
					.accept(MediaType.APPLICATION_JSON)
			)
			.andExpect(status().isOk())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
			.andExpect(jsonPath("$.fileName").value("metricshub.yaml"))
			.andExpect(jsonPath("$.valid").value(true))
			.andExpect(jsonPath("$.error").doesNotExist());
	}

	@Test
	void testShouldValidateFileOnDiskWhenNoBody() throws Exception {
		when(configurationFilesService.getFileContent("metricshub.yaml")).thenReturn("x: 2\n");

		ObjectNode result = JsonNodeFactory.instance.objectNode();
		result.put("valid", false).putArray("errors").add("Invalid YAML");

		when(configurationFilesService.getFileContent("metricshub.yaml")).thenReturn("x: 2\n");
		when(configurationFilesService.validate("x: 2\n", "metricshub.yaml"))
			.thenReturn(ConfigurationFilesService.Validation.fail("metricshub.yaml", "Invalid YAML"));

		mockMvc
			.perform(post("/api/config-files/metricshub.yaml").accept(MediaType.APPLICATION_JSON))
			.andExpect(status().isOk())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
			.andExpect(jsonPath("$.fileName").value("metricshub.yaml"))
			.andExpect(jsonPath("$.valid").value(false))
			.andExpect(jsonPath("$.error").value("Invalid YAML"));
	}

	@Test
	void testShouldDeleteFile() throws Exception {
		doNothing().when(configurationFilesService).deleteFile("metricshub.yaml");

		mockMvc.perform(delete("/api/config-files/metricshub.yaml")).andExpect(status().isNoContent());

		verify(configurationFilesService).deleteFile("metricshub.yaml");
	}

	@Test
	void testShouldRenameFile() throws Exception {
		doNothing().when(configurationFilesService).renameFile("old.yaml", "new.yaml");

		String body = mapper.writeValueAsString(Map.of("newName", "new.yaml"));

		mockMvc
			.perform(patch("/api/config-files/old.yaml").contentType(MediaType.APPLICATION_JSON).content(body))
			.andExpect(status().isNoContent());

		verify(configurationFilesService).renameFile("old.yaml", "new.yaml");
	}

	@Test
	void testRenameFileMissingNewName() throws Exception {
		String body = mapper.writeValueAsString(Map.of()); // empty JSON

		mockMvc
			.perform(patch("/api/config-files/old.yaml").contentType(MediaType.APPLICATION_JSON).content(body))
			.andExpect(status().isBadRequest());
	}
}
