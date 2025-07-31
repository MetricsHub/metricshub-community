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
import org.metricshub.engine.strategy.collect.CollectStrategy;
import org.metricshub.engine.strategy.collect.PrepareCollectStrategy;
import org.metricshub.engine.strategy.collect.ProtocolHealthCheckStrategy;
import org.metricshub.engine.strategy.detection.DetectionStrategy;
import org.metricshub.engine.strategy.discovery.DiscoveryStrategy;
import org.metricshub.engine.strategy.simple.SimpleStrategy;
import org.metricshub.engine.telemetry.TelemetryManager;
import org.metricshub.hardware.strategy.HardwareMonitorNameGenerationStrategy;
import org.metricshub.hardware.strategy.HardwarePostCollectStrategy;
import org.metricshub.hardware.strategy.HardwarePostDiscoveryStrategy;
import org.metricshub.web.AgentContextHolder;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Service that triggers the resource metrics collection for a specified hostname.
 */
@Service
public class TriggerResourceCollectService {

	/**
	 * Holds contextual information about the current agent instance.
	 */
	private AgentContextHolder agentContextHolder;

	/**
	 * Creates a new instance of {@link TriggerResourceCollectService}.
	 *
	 * @param agentContextHolder the {@link AgentContextHolder} used to access the current agent context
	 */
	@Autowired
	public TriggerResourceCollectService(AgentContextHolder agentContextHolder) {
		this.agentContextHolder = agentContextHolder;
	}

	/**
	 * Triggers resource collect for a specified hostname with optional connector
	 * configuration.
	 *
	 * @param hostname   the hostname for which to trigger resource collection
	 * @return a message indicating the result of the operation
	 */
	@Tool(
		name = "TriggerResourceMetricCollection",
		description = "Triggers the collection of resource metrics on the specified host."
	)
	public String triggerResourceMetricCollection(final String hostname) {
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

		final long discoveryTime = System.currentTimeMillis();

		final var extensionManager = agentContextHolder.getAgentContext().getExtensionManager();

		// Run discovery
		newTelemetryManager.run(
			new DetectionStrategy(newTelemetryManager, discoveryTime, clientsExecutor, extensionManager),
			new DiscoveryStrategy(newTelemetryManager, discoveryTime, clientsExecutor, extensionManager),
			new SimpleStrategy(newTelemetryManager, discoveryTime, clientsExecutor, extensionManager),
			new HardwarePostDiscoveryStrategy(newTelemetryManager, discoveryTime, clientsExecutor, extensionManager),
			new HardwareMonitorNameGenerationStrategy(newTelemetryManager, discoveryTime, clientsExecutor, extensionManager)
		);

		final long collectTime = System.currentTimeMillis();

		// Run collect
		newTelemetryManager.run(
			new PrepareCollectStrategy(newTelemetryManager, collectTime, clientsExecutor, extensionManager),
			new ProtocolHealthCheckStrategy(newTelemetryManager, collectTime, clientsExecutor, extensionManager),
			new CollectStrategy(newTelemetryManager, collectTime, clientsExecutor, extensionManager),
			new SimpleStrategy(newTelemetryManager, collectTime, clientsExecutor, extensionManager),
			new HardwarePostCollectStrategy(newTelemetryManager, collectTime, clientsExecutor, extensionManager),
			new HardwareMonitorNameGenerationStrategy(newTelemetryManager, collectTime, clientsExecutor, extensionManager)
		);

		return newTelemetryManager.toJson();
	}
}
