package org.metricshub.web.controller;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * MetricsHub Agent
 * ჻჻჻჻჻჻
 * Copyright 2023 - 2025 MetricsHub
 * ჻჻჻჻჻჻
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 * ╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱
 */

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Path;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.metricshub.agent.context.AgentContext;
import org.metricshub.web.AgentContextHolder;
import org.metricshub.web.dto.RestartStatus;
import org.metricshub.web.service.AgentLifecycleService;
import org.metricshub.web.service.AgentLifecycleService.RestartRequestAck;
import org.metricshub.web.service.AgentLifecycleService.RestartRequestResult;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AgentControllerTest {

	private MockMvc mockMvc;
	private AgentContextHolder agentContextHolder;
	private AgentLifecycleService agentLifecycleService;

	@BeforeEach
	void setup() {
		agentContextHolder = mock(AgentContextHolder.class);
		agentLifecycleService = mock(AgentLifecycleService.class);
		final AgentController controller = new AgentController(agentContextHolder, agentLifecycleService);
		mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
	}

	@Test
	@SuppressWarnings("unchecked")
	void testRestartShouldBeScheduled() throws Exception {
		stubHolder();
		when(agentLifecycleService.restartAsync(any(Supplier.class))).thenReturn(
			new RestartRequestAck(RestartRequestResult.SCHEDULED, 1L)
		);

		mockMvc
			.perform(post("/api/agent/restart"))
			.andExpect(status().isAccepted())
			.andExpect(jsonPath("$.result").value("SCHEDULED"))
			.andExpect(jsonPath("$.message").value("MetricsHub Agent restart scheduled."))
			.andExpect(jsonPath("$.requestId").value(1));

		verify(agentLifecycleService).restartAsync(any(Supplier.class));
	}

	@Test
	@SuppressWarnings("unchecked")
	void testRestartShouldBeQueuedWhenAlreadyRunning() throws Exception {
		stubHolder();
		when(agentLifecycleService.restartAsync(any(Supplier.class))).thenReturn(
			new RestartRequestAck(RestartRequestResult.QUEUED, 2L)
		);

		mockMvc
			.perform(post("/api/agent/restart"))
			.andExpect(status().isAccepted())
			.andExpect(jsonPath("$.result").value("QUEUED"))
			.andExpect(
				jsonPath("$.message").value("A MetricsHub Agent restart is already running; the new request has been queued.")
			)
			.andExpect(jsonPath("$.requestId").value(2));
	}

	@Test
	@SuppressWarnings("unchecked")
	void testRestartShouldBeCoalescedWhenAnotherIsAlreadyPending() throws Exception {
		stubHolder();
		when(agentLifecycleService.restartAsync(any(Supplier.class))).thenReturn(
			new RestartRequestAck(RestartRequestResult.COALESCED, 3L)
		);

		mockMvc
			.perform(post("/api/agent/restart"))
			.andExpect(status().isAccepted())
			.andExpect(jsonPath("$.result").value("COALESCED"))
			.andExpect(
				jsonPath("$.message").value(
					"A MetricsHub Agent restart is already running and another was queued; " +
						"the newer request replaces the previously queued one."
				)
			)
			.andExpect(jsonPath("$.requestId").value(3));
	}

	@Test
	void testRestartShouldHandleException() throws Exception {
		when(agentContextHolder.getAgentContext()).thenThrow(new RuntimeException("Context error"));

		mockMvc
			.perform(post("/api/agent/restart"))
			.andExpect(status().isInternalServerError())
			.andExpect(jsonPath("$.error").value("Failed to restart MetricsHub Agent: Context error"));
	}

	@Test
	void testRestartStatusShouldReturnCurrentStatus() throws Exception {
		final RestartStatus restartStatus = RestartStatus.builder()
			.state(RestartStatus.State.SUCCEEDED)
			.message("MetricsHub Agent restarted successfully.")
			.contextGeneration(3L)
			.requestId(7L)
			.build();
		when(agentLifecycleService.getRestartStatus()).thenReturn(restartStatus);

		mockMvc
			.perform(get("/api/agent/restart/status"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.state").value("SUCCEEDED"))
			.andExpect(jsonPath("$.message").value("MetricsHub Agent restarted successfully."))
			.andExpect(jsonPath("$.contextGeneration").value(3))
			.andExpect(jsonPath("$.requestId").value(7));
	}

	/**
	 * Common holder stub used by the "restart is accepted" tests.
	 */
	private void stubHolder() {
		final AgentContext runningContext = mock(AgentContext.class);
		final Path mockPath = mock(Path.class);
		final Path absolutePath = mock(Path.class);
		when(agentContextHolder.getAgentContext()).thenReturn(runningContext);
		when(runningContext.getConfigDirectory()).thenReturn(mockPath);
		when(mockPath.toAbsolutePath()).thenReturn(absolutePath);
		when(absolutePath.toString()).thenReturn("/mock/config");
	}
}
