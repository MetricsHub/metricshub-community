package org.metricshub.web.controller;

import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
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
import org.metricshub.web.dto.FileNewName;
import org.metricshub.web.service.ConfigurationFilesService;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;

class ConfigurationFilesControllerTest {

	private static final String METRICSHUB_YAML_FILE_NAME = "metricshub.yaml";
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
			.name(METRICSHUB_YAML_FILE_NAME)
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
			.andExpect(jsonPath("$[1].name").value(METRICSHUB_YAML_FILE_NAME))
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
		when(configurationFilesService.getFileContent(METRICSHUB_YAML_FILE_NAME)).thenReturn("key: value\n");

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
		final String lastModificationTime = "2025-09-03T12:00:00Z";
		final long size = 1000L;
		final ConfigurationFile configurationFile = ConfigurationFile
			.builder()
			.lastModificationTime(lastModificationTime)
			.name(METRICSHUB_YAML_FILE_NAME)
			.size(size)
			.build();
		doReturn(configurationFile)
			.when(configurationFilesService)
			.saveOrUpdateFile(METRICSHUB_YAML_FILE_NAME, "test: noo\n");

		mockMvc
			.perform(
				put("/api/config-files/metricshub.yaml?skipValidation=true")
					.contentType(MediaType.TEXT_PLAIN)
					.content("test: noo\n".getBytes(StandardCharsets.UTF_8))
			)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.name").value(METRICSHUB_YAML_FILE_NAME))
			.andExpect(jsonPath("$.size").value(size))
			.andExpect(jsonPath("$.lastModificationTime").value(lastModificationTime));

		verify(configurationFilesService, times(1)).saveOrUpdateFile(METRICSHUB_YAML_FILE_NAME, "test: noo\n");
	}

	@Test
	void testShouldValidateProvidedYaml() throws Exception {
		ObjectNode result = JsonNodeFactory.instance.objectNode();
		result.put("valid", true).putArray("errors");

		when(configurationFilesService.validate("a: 1\n", METRICSHUB_YAML_FILE_NAME))
			.thenReturn(ConfigurationFilesService.Validation.ok(METRICSHUB_YAML_FILE_NAME));

		mockMvc
			.perform(
				post("/api/config-files/metricshub.yaml")
					.contentType(MediaType.TEXT_PLAIN)
					.content("a: 1\n")
					.accept(MediaType.APPLICATION_JSON)
			)
			.andExpect(status().isOk())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
			.andExpect(jsonPath("$.fileName").value(METRICSHUB_YAML_FILE_NAME))
			.andExpect(jsonPath("$.valid").value(true))
			.andExpect(jsonPath("$.error").doesNotExist());
	}

	@Test
	void testShouldValidateFileOnDiskWhenNoBody() throws Exception {
		when(configurationFilesService.getFileContent(METRICSHUB_YAML_FILE_NAME)).thenReturn("x: 2\n");

		ObjectNode result = JsonNodeFactory.instance.objectNode();
		result.put("valid", false).putArray("errors").add("Invalid YAML");

		when(configurationFilesService.getFileContent(METRICSHUB_YAML_FILE_NAME)).thenReturn("x: 2\n");
		when(configurationFilesService.validate("x: 2\n", METRICSHUB_YAML_FILE_NAME))
			.thenReturn(ConfigurationFilesService.Validation.fail(METRICSHUB_YAML_FILE_NAME, "Invalid YAML"));

		mockMvc
			.perform(post("/api/config-files/metricshub.yaml").accept(MediaType.APPLICATION_JSON))
			.andExpect(status().isOk())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
			.andExpect(jsonPath("$.fileName").value(METRICSHUB_YAML_FILE_NAME))
			.andExpect(jsonPath("$.valid").value(false))
			.andExpect(jsonPath("$.error").value("Invalid YAML"));
	}

	@Test
	void testShouldDeleteFile() throws Exception {
		doNothing().when(configurationFilesService).deleteFile(METRICSHUB_YAML_FILE_NAME);

		mockMvc.perform(delete("/api/config-files/metricshub.yaml")).andExpect(status().isNoContent());

		verify(configurationFilesService).deleteFile(METRICSHUB_YAML_FILE_NAME);
	}

	@Test
	void testShouldRenameFile() throws Exception {
		final String lastModificationTime = "2025-09-03T12:00:00Z";
		final long size = 1000L;
		final ConfigurationFile configurationFile = ConfigurationFile
			.builder()
			.lastModificationTime(lastModificationTime)
			.name(METRICSHUB_YAML_FILE_NAME)
			.size(size)
			.build();
		final String newName = "new.yaml";
		doReturn(configurationFile).when(configurationFilesService).renameFile(METRICSHUB_YAML_FILE_NAME, newName);

		String body = mapper.writeValueAsString(new FileNewName(newName));

		mockMvc
			.perform(patch("/api/config-files/metricshub.yaml").contentType(MediaType.APPLICATION_JSON).content(body))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.name").value(METRICSHUB_YAML_FILE_NAME))
			.andExpect(jsonPath("$.size").value(size))
			.andExpect(jsonPath("$.lastModificationTime").value(lastModificationTime));

		verify(configurationFilesService, times(1)).renameFile(METRICSHUB_YAML_FILE_NAME, newName);
	}

	@Test
	void testRenameFileMissingNewName() throws Exception {
		String body = mapper.writeValueAsString(Map.of()); // empty JSON

		mockMvc
			.perform(patch("/api/config-files/old.yaml").contentType(MediaType.APPLICATION_JSON).content(body))
			.andExpect(status().isBadRequest());
	}
}
