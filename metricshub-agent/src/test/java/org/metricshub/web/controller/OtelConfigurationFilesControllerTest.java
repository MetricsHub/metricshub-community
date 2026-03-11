package org.metricshub.web.controller;

import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
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
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.metricshub.agent.deserialization.DeserializationFailure;
import org.metricshub.web.dto.ConfigurationFile;
import org.metricshub.web.dto.FileNewName;
import org.metricshub.web.service.OtelConfigurationFilesService;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class OtelConfigurationFilesControllerTest {

	private static final String BASE = "/api/otel/config-files";
	private static final String OTEL_CONFIG_FILE = "otel-config.yaml";

	private MockMvc mockMvc;
	private OtelConfigurationFilesService otelConfigurationFilesService;
	private final ObjectMapper mapper = new ObjectMapper();

	@BeforeEach
	void setup() {
		otelConfigurationFilesService = Mockito.mock(OtelConfigurationFilesService.class);
		final OtelConfigurationFilesController controller = new OtelConfigurationFilesController(
			otelConfigurationFilesService
		);
		mockMvc = MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new RestExceptionHandler()).build();
	}

	@Test
	void testShouldListConfigurationFiles() throws Exception {
		final ConfigurationFile f1 = ConfigurationFile
			.builder()
			.name(OTEL_CONFIG_FILE)
			.size(1024L)
			.lastModificationTime("2025-09-01T10:15:30Z")
			.build();

		when(otelConfigurationFilesService.getAllConfigurationFiles()).thenReturn(List.of(f1));

		mockMvc
			.perform(get(BASE))
			.andExpect(status().isOk())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
			.andExpect(jsonPath("$.length()").value(1))
			.andExpect(jsonPath("$[0].name").value(OTEL_CONFIG_FILE))
			.andExpect(jsonPath("$[0].size").value(1024))
			.andExpect(jsonPath("$[0].lastModificationTime").value("2025-09-01T10:15:30Z"));
	}

	@Test
	void testShouldReturnEmptyListWhenNoFiles() throws Exception {
		when(otelConfigurationFilesService.getAllConfigurationFiles()).thenReturn(List.of());

		mockMvc.perform(get(BASE)).andExpect(status().isOk()).andExpect(content().json("[]"));
	}

	@Test
	void testShouldGetFileContent() throws Exception {
		when(otelConfigurationFilesService.getFileContent(OTEL_CONFIG_FILE)).thenReturn("receivers:\n  otlp:\n");

		mockMvc
			.perform(get(BASE + "/otel-config.yaml").accept(MediaType.TEXT_PLAIN))
			.andExpect(status().isOk())
			.andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_PLAIN))
			.andExpect(content().string("receivers:\n  otlp:\n"));
	}

	@Test
	void testShouldSaveOrUpdateFile() throws Exception {
		final ConfigurationFile saved = ConfigurationFile
			.builder()
			.name(OTEL_CONFIG_FILE)
			.size(100L)
			.lastModificationTime("2025-09-03T12:00:00Z")
			.build();

		doReturn(saved).when(otelConfigurationFilesService).saveOrUpdateFile(OTEL_CONFIG_FILE, "receivers:\n  otlp:\n");

		mockMvc
			.perform(
				put(BASE + "/otel-config.yaml?skipValidation=true")
					.contentType(MediaType.TEXT_PLAIN)
					.content("receivers:\n  otlp:\n".getBytes(StandardCharsets.UTF_8))
			)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.name").value(OTEL_CONFIG_FILE))
			.andExpect(jsonPath("$.lastModificationTime").value("2025-09-03T12:00:00Z"));

		verify(otelConfigurationFilesService, Mockito.times(1)).saveOrUpdateFile(OTEL_CONFIG_FILE, "receivers:\n  otlp:\n");
	}

	@Test
	void testShouldValidateFile() throws Exception {
		when(otelConfigurationFilesService.validate("receivers: {}", OTEL_CONFIG_FILE))
			.thenReturn(OtelConfigurationFilesService.Validation.ok(OTEL_CONFIG_FILE));

		mockMvc
			.perform(
				post(BASE + "/otel-config.yaml")
					.contentType(MediaType.TEXT_PLAIN)
					.content("receivers: {}")
					.accept(MediaType.APPLICATION_JSON)
			)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.fileName").value(OTEL_CONFIG_FILE))
			.andExpect(jsonPath("$.valid").value(true));
	}

	@Test
	void testShouldDeleteFile() throws Exception {
		doNothing().when(otelConfigurationFilesService).deleteFile(OTEL_CONFIG_FILE);

		mockMvc.perform(delete(BASE + "/otel-config.yaml")).andExpect(status().isNoContent());

		verify(otelConfigurationFilesService).deleteFile(OTEL_CONFIG_FILE);
	}

	@Test
	void testShouldRenameFile() throws Exception {
		final ConfigurationFile renamed = ConfigurationFile
			.builder()
			.name("new-otel.yaml")
			.size(100L)
			.lastModificationTime("2025-09-03T12:00:00Z")
			.build();

		doReturn(renamed).when(otelConfigurationFilesService).renameFile(OTEL_CONFIG_FILE, "new-otel.yaml");

		String body = mapper.writeValueAsString(new FileNewName("new-otel.yaml"));

		mockMvc
			.perform(patch(BASE + "/otel-config.yaml").contentType(MediaType.APPLICATION_JSON).content(body))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.name").value("new-otel.yaml"));

		verify(otelConfigurationFilesService, Mockito.times(1)).renameFile(OTEL_CONFIG_FILE, "new-otel.yaml");
	}

	@Test
	void testShouldListBackupFiles() throws Exception {
		final ConfigurationFile backup = ConfigurationFile
			.builder()
			.name("backup-20250101-120000__otel-config.yaml")
			.size(50L)
			.lastModificationTime("2025-01-01T12:00:00Z")
			.build();

		when(otelConfigurationFilesService.listAllBackupFiles()).thenReturn(List.of(backup));

		mockMvc
			.perform(get(BASE + "/backup"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.length()").value(1))
			.andExpect(jsonPath("$[0].name").value("backup-20250101-120000__otel-config.yaml"));
	}

	@Test
	void testShouldGetBackupFileContent() throws Exception {
		String backupName = "backup-20250101-120000__otel-config.yaml";
		when(otelConfigurationFilesService.getBackupFileContent(backupName)).thenReturn("receivers: {}");

		mockMvc
			.perform(get(BASE + "/backup/" + backupName).accept(MediaType.TEXT_PLAIN))
			.andExpect(status().isOk())
			.andExpect(content().string("receivers: {}"));
	}

	@Test
	void testShouldSaveDraftFile() throws Exception {
		final ConfigurationFile draft = ConfigurationFile
			.builder()
			.name("otel-config.yaml.draft")
			.size(80L)
			.lastModificationTime("2025-09-03T12:00:00Z")
			.build();

		doReturn(draft)
			.when(otelConfigurationFilesService)
			.saveOrUpdateDraftFile("otel-config.yaml", "receivers:\n  otlp:\n");

		mockMvc
			.perform(
				put(BASE + "/draft/otel-config.yaml?skipValidation=true")
					.contentType(MediaType.TEXT_PLAIN)
					.content("receivers:\n  otlp:\n".getBytes(StandardCharsets.UTF_8))
			)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.name").value("otel-config.yaml.draft"));

		verify(otelConfigurationFilesService, Mockito.times(1))
			.saveOrUpdateDraftFile("otel-config.yaml", "receivers:\n  otlp:\n");
	}
}
