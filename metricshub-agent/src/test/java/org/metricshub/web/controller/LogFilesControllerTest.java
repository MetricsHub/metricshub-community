package org.metricshub.web.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.metricshub.web.dto.LogFile;
import org.metricshub.web.service.LogFilesService;
import org.mockito.Mockito;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;

class LogFilesControllerTest {

	private static final String METRICSHUB_LOG_FILE_NAME = "metricshub.log";
	private MockMvc mockMvc;
	private LogFilesService logFilesService;

	@BeforeEach
	void setup() {
		logFilesService = Mockito.mock(LogFilesService.class);
		final LogFilesController controller = new LogFilesController(logFilesService);
		mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
	}

	@Test
	void testShouldListLogFiles() throws Exception {
		final LogFile f1 = LogFile
			.builder()
			.name(METRICSHUB_LOG_FILE_NAME)
			.size(1024L)
			.lastModificationTime("2025-09-01T10:15:30Z")
			.build();

		final LogFile f2 = LogFile
			.builder()
			.name("agent.log")
			.size(256L)
			.lastModificationTime("2025-09-02T08:00:00Z")
			.build();

		when(logFilesService.getAllLogFiles()).thenReturn(List.of(f2, f1));

		mockMvc
			.perform(get("/api/log-files"))
			.andExpect(status().isOk())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
			.andExpect(jsonPath("$.length()").value(2))
			.andExpect(jsonPath("$[0].name").value("agent.log"))
			.andExpect(jsonPath("$[0].size").value(256))
			.andExpect(jsonPath("$[0].lastModificationTime").value("2025-09-02T08:00:00Z"))
			.andExpect(jsonPath("$[1].name").value(METRICSHUB_LOG_FILE_NAME))
			.andExpect(jsonPath("$[1].size").value(1024))
			.andExpect(jsonPath("$[1].lastModificationTime").value("2025-09-01T10:15:30Z"));
	}

	@Test
	void testShouldReturnEmptyListWhenNoFiles() throws Exception {
		when(logFilesService.getAllLogFiles()).thenReturn(List.of());

		mockMvc
			.perform(get("/api/log-files"))
			.andExpect(status().isOk())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
			.andExpect(content().json("[]"));
	}

	@Test
	void testShouldGetFileTail() throws Exception {
		final String tailContent = "2025-01-30 10:00:00 INFO MetricsHub started\n";
		when(logFilesService.getFileTail(METRICSHUB_LOG_FILE_NAME, LogFilesService.DEFAULT_MAX_TAIL_BYTES))
			.thenReturn(tailContent);

		mockMvc
			.perform(get("/api/log-files/metricshub.log/tail").accept(MediaType.TEXT_PLAIN))
			.andExpect(status().isOk())
			.andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_PLAIN))
			.andExpect(content().string(tailContent));
	}

	@Test
	void testShouldGetFileTailWithCustomMaxBytes() throws Exception {
		final String tailContent = "Last 512 bytes of log";
		when(logFilesService.getFileTail(METRICSHUB_LOG_FILE_NAME, 512L)).thenReturn(tailContent);

		mockMvc
			.perform(get("/api/log-files/metricshub.log/tail?maxBytes=512").accept(MediaType.TEXT_PLAIN))
			.andExpect(status().isOk())
			.andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_PLAIN))
			.andExpect(content().string(tailContent));
	}

	@Test
	void testGetFileTailNotFound() throws Exception {
		when(logFilesService.getFileTail("missing.log", LogFilesService.DEFAULT_MAX_TAIL_BYTES))
			.thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Not Found"));

		mockMvc.perform(get("/api/log-files/missing.log/tail")).andExpect(status().isNotFound());
	}

	@Test
	void testShouldDownloadFile() throws Exception {
		final byte[] fileContent = "Full log file content\nLine 2\nLine 3".getBytes(StandardCharsets.UTF_8);
		when(logFilesService.getFileForDownload(METRICSHUB_LOG_FILE_NAME)).thenReturn(fileContent);

		mockMvc
			.perform(get("/api/log-files/metricshub.log/download"))
			.andExpect(status().isOk())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_OCTET_STREAM))
			.andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"metricshub.log\""))
			.andExpect(content().bytes(fileContent));
	}

	@Test
	void testDownloadFileNotFound() throws Exception {
		when(logFilesService.getFileForDownload("missing.log"))
			.thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Not Found"));

		mockMvc.perform(get("/api/log-files/missing.log/download")).andExpect(status().isNotFound());
	}
}
