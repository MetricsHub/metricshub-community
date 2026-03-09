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

import lombok.extern.slf4j.Slf4j;
import org.metricshub.agent.context.AgentContext;
import org.metricshub.web.MetricsHubAgentServer;
import org.springframework.stereotype.Service;

/**
 * Service responsible for managing the lifecycle of the MetricsHub Agent.
 * This includes restarting the agent and its associated services.
 */
@Service
@Slf4j
public class AgentLifecycleService {

	/**
	 * Restarts the MetricsHub Agent by stopping all services in the running
	 * context,
	 * and starting them in the new context.
	 *
	 * @param runningContext  the current running agent context
	 * @param reloadedContext the new agent context to switch to
	 */
	public void restart(final AgentContext runningContext, final AgentContext reloadedContext) {
		log.info("Restart requested. Restarting MetricsHub Agent...");

		// Stop the current scheduler
		runningContext.getTaskSchedulingService().stop();

		// Stop the current OpenTelemetry Collector
		log.info("Restarting OpenTelemetry Collector...");
		runningContext.getOtelCollectorProcessService().stop();

		// Launch the new OpenTelemetry Collector from the reloaded context
		reloadedContext.getOtelCollectorProcessService().launch();

		// Start the new scheduler
		reloadedContext.getTaskSchedulingService().start();

		// Update the MetricsHub Agent Server with the new context
		// This ensures the web application and other components use the new context
		MetricsHubAgentServer.updateAgentContext(reloadedContext);

		log.info("MetricsHub Agent restarted successfully.");
	}
}
