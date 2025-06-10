package org.metricshub.web.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.metricshub.web.dto.ApplicationStatus;
import org.metricshub.web.service.ApplicationStatusService;
import org.mockito.Mockito;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class ApplicationStatusControllerTest {

	private MockMvc mockMvc;
	private ApplicationStatusService applicationStatusService;

	@BeforeEach
	void setup() {
		applicationStatusService = Mockito.mock(ApplicationStatusService.class);
		ApplicationStatusController controller = new ApplicationStatusController(applicationStatusService);
		mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
	}

	@Test
	void testShouldReportApplicationStatus() throws Exception {
		final ApplicationStatus mockStatus = ApplicationStatus
			.builder()
			.status(ApplicationStatus.Status.UP)
			.agentInfo(Map.of("version", "1.0.0"))
			.build();
		when(applicationStatusService.reportApplicationStatus()).thenReturn(mockStatus);

		mockMvc
			.perform(get("/api/status"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("UP"))
			.andExpect(jsonPath("$.agentInfo.version").value("1.0.0"));
	}
}
