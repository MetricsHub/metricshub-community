package org.metricshub.web.service;

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

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.metricshub.agent.context.AgentContext;
import org.metricshub.agent.service.OtelCollectorProcessService;
import org.metricshub.agent.service.TaskSchedulingService;
import org.metricshub.web.MetricsHubAgentServer;
import org.mockito.MockedStatic;

class AgentLifecycleServiceTest {

    private AgentLifecycleService agentLifecycleService;

    @BeforeEach
    void setUp() {
        agentLifecycleService = new AgentLifecycleService();
    }

    @Test
    void testRestartShouldStopOldServicesAndStartNewOnes() {
        // Mock contexts
        final AgentContext runningContext = mock(AgentContext.class);
        final AgentContext reloadedContext = mock(AgentContext.class);

        // Mock services
        final TaskSchedulingService runningSchedulingService = mock(TaskSchedulingService.class);
        final OtelCollectorProcessService runningOtelService = mock(OtelCollectorProcessService.class);
        final TaskSchedulingService reloadedSchedulingService = mock(TaskSchedulingService.class);
        final OtelCollectorProcessService reloadedOtelService = mock(OtelCollectorProcessService.class);

        when(runningContext.getTaskSchedulingService()).thenReturn(runningSchedulingService);
        when(runningContext.getOtelCollectorProcessService()).thenReturn(runningOtelService);
        when(reloadedContext.getTaskSchedulingService()).thenReturn(reloadedSchedulingService);
        when(reloadedContext.getOtelCollectorProcessService()).thenReturn(reloadedOtelService);

        try (MockedStatic<MetricsHubAgentServer> mockedServer = mockStatic(MetricsHubAgentServer.class)) {
            // Execute restart
            agentLifecycleService.restart(runningContext, reloadedContext);

            // Verify old services were stopped
            verify(runningSchedulingService).stop();
            verify(runningOtelService).stop();

            // Verify new services were started
            verify(reloadedOtelService).launch();
            verify(reloadedSchedulingService).start();

            // Verify context was updated in server
            mockedServer.verify(() -> MetricsHubAgentServer.updateAgentContext(reloadedContext));
        }
    }
}
