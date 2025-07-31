package org.metricshub.web.mcp;

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

import java.util.Optional;
import org.metricshub.engine.client.ClientsExecutor;
import org.metricshub.engine.strategy.detection.DetectionStrategy;
import org.metricshub.engine.telemetry.TelemetryManager;
import org.metricshub.web.AgentContextHolder;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Service that triggers resource detection for a specified hostname.
 */
@Service
public class TriggerResourceDetectionService {

	/**
	 * Holds contextual information about the current agent instance.
	 */
	private AgentContextHolder agentContextHolder;

	/**
	 * Creates a new instance of {@link TriggerResourceDetectionService}.
	 *
	 * @param agentContextHolder the {@link AgentContextHolder} used to access the current agent context
	 */
	@Autowired
	public TriggerResourceDetectionService(AgentContextHolder agentContextHolder) {
		this.agentContextHolder = agentContextHolder;
	}

	/**
	 * Triggers resource detection for a specified hostname with optional connector
	 * configuration.
	 *
	 * @param hostname   the hostname for which to trigger resource detection
	 * @return a message indicating the result of the operation
	 */
	@Tool(name = "TriggerResourceDetection", description = "Triggers resource detection for a specified hostname.")
	public String triggerResourceDetection(final String hostname) {
		final Optional<TelemetryManager> maybeTelemetryManager = MCPConfigHelper.findTelemetryManagerByHostname(
			hostname,
			agentContextHolder
		);
		if (maybeTelemetryManager.isEmpty()) {
			return "The hostname %s is not currently monitored by MetricsHub. Please configure a new resource for this host to begin monitoring.".formatted(
					hostname
				);
		}

		final var newTelemetryManager = MCPConfigHelper.newFrom(maybeTelemetryManager.get());

		// Instantiate a new ClientsExecutor
		final var clientsExecutor = new ClientsExecutor(newTelemetryManager);

		newTelemetryManager.run(
			new DetectionStrategy(
				newTelemetryManager,
				System.currentTimeMillis(),
				clientsExecutor,
				agentContextHolder.getAgentContext().getExtensionManager()
			)
		);

		return newTelemetryManager.toJson();
	}
}
