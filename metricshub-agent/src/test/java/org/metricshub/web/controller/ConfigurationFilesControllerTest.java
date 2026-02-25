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
import org.metricshub.agent.deserialization.DeserializationFailure;
import org.metricshub.web.dto.ConfigurationFile;
import org.metricshub.web.dto.FileNewName;
import org.metricshub.web.exception.ConfigFilesException;
import org.metricshub.web.service.ConfigurationFilesService;
import org.metricshub.web.service.VelocityTemplateService;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;

class ConfigurationFilesControllerTest {

	private static final String METRICSHUB_YAML_FILE_NAME = "metricshub.yaml";
	private MockMvc mockMvc;
	private ConfigurationFilesService configurationFilesService;
	private VelocityTemplateService velocityTemplateService;

	private final ObjectMapper mapper = new ObjectMapper();

	@BeforeEach
	void setup() {
		configurationFilesService = Mockito.mock(ConfigurationFilesService.class);
		velocityTemplateService = Mockito.mock(VelocityTemplateService.class);
		final ConfigurationFilesController controller = new ConfigurationFilesController(
			configurationFilesService,
			velocityTemplateService
		);
		mockMvc = MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new RestExceptionHandler()).build();
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
			.andExpect(jsonPath("$.errors").isEmpty());
	}

	@Test
	void testShouldValidateFileOnDiskWhenNoBody() throws Exception {
		when(configurationFilesService.getFileContent(METRICSHUB_YAML_FILE_NAME)).thenReturn("x: 2\n");

		ObjectNode result = JsonNodeFactory.instance.objectNode();
		result.put("valid", false).putArray("errors").add("Invalid YAML");

		when(configurationFilesService.getFileContent(METRICSHUB_YAML_FILE_NAME)).thenReturn("x: 2\n");
		final DeserializationFailure failure = new DeserializationFailure();
		failure.addError("Invalid YAML");
		when(configurationFilesService.validate("x: 2\n", METRICSHUB_YAML_FILE_NAME))
			.thenReturn(ConfigurationFilesService.Validation.fail(METRICSHUB_YAML_FILE_NAME, failure));

		mockMvc
			.perform(post("/api/config-files/metricshub.yaml").accept(MediaType.APPLICATION_JSON))
			.andExpect(status().isOk())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
			.andExpect(jsonPath("$.fileName").value(METRICSHUB_YAML_FILE_NAME))
			.andExpect(jsonPath("$.valid").value(false))
			.andExpect(jsonPath("$.errors[0].message").value("Invalid YAML"));
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

	@Test
	void testShouldSaveVmFileWithTwoStepValidation() throws Exception {
		final String vmContent = "#set($x = 1)\nresourceGroups: {}";
		final String generatedYaml = "resourceGroups: {}";
		final String vmFileName = "config.vm";

		when(velocityTemplateService.evaluate(vmFileName, vmContent)).thenReturn(generatedYaml);
		when(configurationFilesService.validate(generatedYaml, vmFileName))
			.thenReturn(ConfigurationFilesService.Validation.ok(vmFileName));

		final ConfigurationFile savedFile = ConfigurationFile
			.builder()
			.name(vmFileName)
			.size(100L)
			.lastModificationTime("2025-09-01T10:00:00Z")
			.build();
		doReturn(savedFile).when(configurationFilesService).saveOrUpdateFile(vmFileName, vmContent);

		mockMvc
			.perform(
				put("/api/config-files/config.vm")
					.contentType(MediaType.TEXT_PLAIN)
					.content(vmContent.getBytes(StandardCharsets.UTF_8))
			)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.name").value(vmFileName));

		verify(velocityTemplateService, times(1)).evaluate(vmFileName, vmContent);
		verify(configurationFilesService, times(1)).validate(generatedYaml, vmFileName);
		verify(configurationFilesService, times(1)).saveOrUpdateFile(vmFileName, vmContent);
	}

	@Test
	void testShouldRejectVmFileWithVelocitySyntaxError() throws Exception {
		final String vmContent = "#set($x = )";
		final String vmFileName = "bad-syntax.vm";

		when(velocityTemplateService.evaluate(vmFileName, vmContent))
			.thenThrow(
				new ConfigFilesException(
					ConfigFilesException.Code.VALIDATION_FAILED,
					"Velocity template evaluation failed: Encountered unexpected token"
				)
			);

		mockMvc
			.perform(
				put("/api/config-files/bad-syntax.vm")
					.contentType(MediaType.TEXT_PLAIN)
					.content(vmContent.getBytes(StandardCharsets.UTF_8))
			)
			.andExpect(status().isBadRequest());
	}

	@Test
	void testShouldRejectVmFileWithInvalidGeneratedYaml() throws Exception {
		final String vmContent = "#set($x = 1)\nbad-yaml: [";
		final String generatedYaml = "bad-yaml: [";
		final String vmFileName = "bad-output.vm";

		when(velocityTemplateService.evaluate(vmFileName, vmContent)).thenReturn(generatedYaml);
		final DeserializationFailure failure = new DeserializationFailure();
		failure.addError("Invalid YAML structure");
		when(configurationFilesService.validate(generatedYaml, vmFileName))
			.thenReturn(ConfigurationFilesService.Validation.fail(vmFileName, failure));

		mockMvc
			.perform(
				put("/api/config-files/bad-output.vm")
					.contentType(MediaType.TEXT_PLAIN)
					.content(vmContent.getBytes(StandardCharsets.UTF_8))
			)
			.andExpect(status().isBadRequest());
	}

	@Test
	void testShouldValidateVmFileWithTwoStepPipeline() throws Exception {
		final String vmContent = "#set($x = 1)\nresourceGroups: {}";
		final String generatedYaml = "resourceGroups: {}";
		final String vmFileName = "config.vm";

		when(velocityTemplateService.evaluate(vmFileName, vmContent)).thenReturn(generatedYaml);
		when(configurationFilesService.validate(generatedYaml, vmFileName))
			.thenReturn(ConfigurationFilesService.Validation.ok(vmFileName));

		mockMvc
			.perform(
				post("/api/config-files/config.vm")
					.contentType(MediaType.TEXT_PLAIN)
					.content(vmContent)
					.accept(MediaType.APPLICATION_JSON)
			)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.fileName").value(vmFileName))
			.andExpect(jsonPath("$.valid").value(true));
	}

	@Test
	void testShouldReturnValidationFailureWhenVmTemplateExecutionFails() throws Exception {
		final String vmContent = "#set($x = )";
		final String vmFileName = "bad-template.vm";

		when(velocityTemplateService.evaluate(vmFileName, vmContent))
			.thenThrow(
				new ConfigFilesException(
					ConfigFilesException.Code.VALIDATION_FAILED,
					"Velocity template evaluation failed: syntax error"
				)
			);

		mockMvc
			.perform(
				post("/api/config-files/bad-template.vm")
					.contentType(MediaType.TEXT_PLAIN)
					.content(vmContent)
					.accept(MediaType.APPLICATION_JSON)
			)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.fileName").value(vmFileName))
			.andExpect(jsonPath("$.valid").value(false))
			.andExpect(jsonPath("$.errors[0].message").value("Velocity template evaluation failed: syntax error"));
	}

	@Test
	void testShouldTestVelocityTemplateWithBodyContent() throws Exception {
		final String vmContent = "#set($x = 1)\nresourceGroups: {}";
		final String generatedYaml = "resourceGroups: {}";
		final String vmFileName = "config.vm";

		when(velocityTemplateService.evaluate(vmFileName, vmContent)).thenReturn(generatedYaml);

		mockMvc
			.perform(
				post("/api/config-files/test/config.vm")
					.contentType(MediaType.TEXT_PLAIN)
					.content(vmContent)
					.accept(MediaType.TEXT_PLAIN)
			)
			.andExpect(status().isOk())
			.andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_PLAIN))
			.andExpect(content().string(generatedYaml));

		verify(velocityTemplateService, times(1)).evaluate(vmFileName, vmContent);
	}

	@Test
	void testShouldTestVelocityTemplateWithoutBody() throws Exception {
		final String generatedYaml = "resourceGroups: {}";
		final String vmFileName = "config.vm";

		when(velocityTemplateService.evaluate(vmFileName, null)).thenReturn(generatedYaml);

		mockMvc
			.perform(post("/api/config-files/test/config.vm").contentType(MediaType.TEXT_PLAIN).accept(MediaType.TEXT_PLAIN))
			.andExpect(status().isOk())
			.andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_PLAIN))
			.andExpect(content().string(generatedYaml));
	}

	@Test
	void testShouldRejectTestForNonVmFile() throws Exception {
		mockMvc
			.perform(post("/api/config-files/test/config.yaml").contentType(MediaType.TEXT_PLAIN).content("a: 1"))
			.andExpect(status().isBadRequest());
	}

	@Test
	void testShouldReturnErrorWhenVelocityTestFails() throws Exception {
		final String vmContent = "#set($x = )";
		final String vmFileName = "bad.vm";

		when(velocityTemplateService.evaluate(vmFileName, vmContent))
			.thenThrow(
				new ConfigFilesException(
					ConfigFilesException.Code.VALIDATION_FAILED,
					"Velocity template evaluation failed: Encountered unexpected token"
				)
			);

		mockMvc
			.perform(post("/api/config-files/test/bad.vm").contentType(MediaType.TEXT_PLAIN).content(vmContent))
			.andExpect(status().isBadRequest());
	}
}
