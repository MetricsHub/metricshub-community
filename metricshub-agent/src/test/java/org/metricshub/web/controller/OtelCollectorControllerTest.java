package org.metricshub.web.controller;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.metricshub.web.exception.OtelCollectorException;
import org.metricshub.web.service.OtelCollectorService;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class OtelCollectorControllerTest {

	private static final String BASE = "/api/otel/collector";

	private MockMvc mockMvc;
	private OtelCollectorService otelCollectorService;

	@BeforeEach
	void setup() {
		otelCollectorService = Mockito.mock(OtelCollectorService.class);
		final OtelCollectorController controller = new OtelCollectorController(otelCollectorService);
		mockMvc = MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new RestExceptionHandler()).build();
	}

	@Test
	void testRestartCollectorOk() throws Exception {
		doNothing().when(otelCollectorService).restartCollector();

		mockMvc
			.perform(post(BASE + "/restart"))
			.andExpect(status().isOk())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
			.andExpect(content().string(containsString("restarted successfully")));

		verify(otelCollectorService).restartCollector();
	}

	@Test
	void testRestartCollectorFails() throws Exception {
		doThrow(new OtelCollectorException(OtelCollectorException.Code.RESTART_FAILED, "Restart failed"))
			.when(otelCollectorService)
			.restartCollector();

		mockMvc.perform(post(BASE + "/restart")).andExpect(status().isInternalServerError());
	}

	@Test
	void testGetCollectorLogsOk() throws Exception {
		when(otelCollectorService.getCollectorLogTail(200)).thenReturn("line1\nline2\n");

		mockMvc
			.perform(get(BASE + "/logs").accept(MediaType.TEXT_PLAIN))
			.andExpect(status().isOk())
			.andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_PLAIN))
			.andExpect(content().string("line1\nline2\n"));

		verify(otelCollectorService).getCollectorLogTail(200);
	}

	@Test
	void testGetCollectorLogsWithTailLines() throws Exception {
		when(otelCollectorService.getCollectorLogTail(100)).thenReturn("tail\n");

		mockMvc
			.perform(get(BASE + "/logs").param("tailLines", "100").accept(MediaType.TEXT_PLAIN))
			.andExpect(status().isOk())
			.andExpect(content().string("tail\n"));

		verify(otelCollectorService).getCollectorLogTail(100);
	}

	@Test
	void testGetCollectorLogsNotFound() throws Exception {
		doThrow(new OtelCollectorException(OtelCollectorException.Code.LOG_FILE_NOT_FOUND))
			.when(otelCollectorService)
			.getCollectorLogTail(200);

		mockMvc.perform(get(BASE + "/logs")).andExpect(status().isNotFound());
	}
}
