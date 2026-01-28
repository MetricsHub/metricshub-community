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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.metricshub.agent.context.AgentContext;
import org.metricshub.agent.helper.ConfigHelper;
import org.metricshub.engine.extension.ExtensionManager;
import org.metricshub.web.AgentContextHolder;
import org.metricshub.web.service.AgentLifecycleService;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
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
    @SuppressWarnings("resource")
    void testRestartShouldSucceed() throws Exception {
        final AgentContext runningContext = mock(AgentContext.class);
        final Path mockPath = mock(Path.class);
        final Path absolutePath = mock(Path.class);
        when(agentContextHolder.getAgentContext()).thenReturn(runningContext);
        when(runningContext.getConfigDirectory()).thenReturn(mockPath);
        when(mockPath.toAbsolutePath()).thenReturn(absolutePath);
        when(absolutePath.toString()).thenReturn("/mock/config");

        final ExtensionManager extensionManager = mock(ExtensionManager.class);

        try (
                MockedStatic<ConfigHelper> mockedConfigHelper = mockStatic(ConfigHelper.class);
                MockedConstruction<AgentContext> mockedContextConstruction = mockConstruction(AgentContext.class)) {
            mockedConfigHelper.when(ConfigHelper::loadExtensionManager).thenReturn(extensionManager);

            mockMvc
                    .perform(post("/api/agent/restart"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.message").value("MetricsHub Agent restarted successfully."));

            // Verify AgentContext was constructed
            assertEquals(1, mockedContextConstruction.constructed().size());
            final AgentContext reloadedContext = mockedContextConstruction.constructed().get(0);

            verify(agentLifecycleService).restart(eq(runningContext), eq(reloadedContext));
        }
    }

    @Test
    void testRestartShouldHandleException() throws Exception {
        when(agentContextHolder.getAgentContext()).thenThrow(new RuntimeException("Context error"));

        mockMvc
                .perform(post("/api/agent/restart"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").value("Failed to restart MetricsHub Agent: Context error"));
    }
}
