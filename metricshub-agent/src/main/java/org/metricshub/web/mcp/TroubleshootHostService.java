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
import org.metricshub.web.dto.TelemetryResult;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Service that triggers the resource metrics collection for a specified hostname.
 */
@Service
public class TroubleshootHostService implements IMCPToolService {

	/**
	 * Message to be returned when the hostname is not configured in MetricsHub.
	 */
	protected static final String HOSTNAME_NOT_CONFIGURED_MSG =
		"The hostname %s is not currently monitored by MetricsHub. Please configure a new resource for this host to begin monitoring.";

	/**
	 * Holds contextual information about the current agent instance.
	 */
	private AgentContextHolder agentContextHolder;

	/**
	 * Creates a new instance of {@link TroubleshootHostService}.
	 *
	 * @param agentContextHolder the {@link AgentContextHolder} used to access the current agent context
	 */
	@Autowired
	public TroubleshootHostService(AgentContextHolder agentContextHolder) {
		this.agentContextHolder = agentContextHolder;
	}

	/**
	 * Triggers resource collect for a specified hostname with optional connector
	 * configuration.
	 *
	 * @param hostname    the hostname for which to trigger resource collection
	 * @param connectorId the identifier of a specific connector to use for collecting metrics.
	 * @return a message indicating the result of the operation
	 */
	@Tool(
		name = "CollectMetricsForHost",
		description = """
		Fetch and collect metrics for the specified host using the configured protocols and credentials,
		and the applicable MetricsHub connectors (MIB2, Linux, Windows, Dell, RedFish, etc.).
		Returns the collected metrics and all attributes.
		Metrics follow OpenTelemetry semantic conventions.
		"""
	)
	public TelemetryResult collectMetricsForHost(
		@ToolParam(
			description = "The hostname of the resource we are interested in",
			required = true
		) final String hostname,
		@ToolParam(
			description = """
			Optional: The identifier of a specific connector to use for collecting metrics.
			If not specified, the system will attempt to select an appropriate connector automatically.
			""",
			required = false
		) final String connectorId
	) {
		final Optional<TelemetryManager> maybeTelemetryManager = MCPConfigHelper.findTelemetryManagerByHostname(
			hostname,
			agentContextHolder
		);
		if (maybeTelemetryManager.isEmpty()) {
			return new TelemetryResult(HOSTNAME_NOT_CONFIGURED_MSG.formatted(hostname));
		}

		final var newTelemetryManager = MCPConfigHelper.newFrom(maybeTelemetryManager.get(), connectorId);

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

		return new TelemetryResult(newTelemetryManager.getVo());
	}

	/**
	 * Retrieves metrics from the MetricsHub cache for a specified hostname.
	 *
	 * @param hostname    the hostname for which to trigger resource collection
	 * @return a message indicating the result of the operation
	 */
	@Tool(
		name = "GetMetricsFromCacheForHost",
		description = """
		Retrieves metrics from the MetricsHub cache for a specified hostname.
		Returns the collected metrics and all attributes.
		Metrics follow OpenTelemetry semantic conventions.
		"""
	)
	public TelemetryResult getMetricsFromCacheForHost(
		@ToolParam(description = "The hostname of the resource we are interested in", required = true) final String hostname
	) {
		final Optional<TelemetryManager> maybeTelemetryManager = MCPConfigHelper.findTelemetryManagerByHostname(
			hostname,
			agentContextHolder
		);
		if (maybeTelemetryManager.isEmpty()) {
			return new TelemetryResult(HOSTNAME_NOT_CONFIGURED_MSG.formatted(hostname));
		}

		return new TelemetryResult(maybeTelemetryManager.get().getVo());
	}

	/**
	 * Triggers resource detection for a specified hostname.
	 *
	 * @param hostname    the hostname for which to trigger resource detection
	 * @param connectorId the identifier of a specific connector to use for detection.
	 * @return a message indicating the result of the operation
	 */
	@Tool(
		name = "TestAvailableConnectorsForHost",
		description = """
		Test all applicable MetricsHub connectors (MIB2, Linux, Windows, Dell, RedFish, etc.) against the specified host
		using the configured credentials and return the list of connectors that work with this host.
		"""
	)
	public TelemetryResult testAvailableConnectorsForHost(
		@ToolParam(description = "The hostname of the resource we are interested in") final String hostname,
		@ToolParam(
			description = """
			Optional: The identifier of a specific connector to use for the test.
			If not specified, the system will attempt to select an appropriate connector automatically.
			""",
			required = false
		) final String connectorId
	) {
		final Optional<TelemetryManager> maybeTelemetryManager = MCPConfigHelper.findTelemetryManagerByHostname(
			hostname,
			agentContextHolder
		);
		if (maybeTelemetryManager.isEmpty()) {
			return new TelemetryResult(HOSTNAME_NOT_CONFIGURED_MSG.formatted(hostname));
		}

		final var newTelemetryManager = MCPConfigHelper.newFrom(maybeTelemetryManager.get(), connectorId);

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

		return new TelemetryResult(newTelemetryManager.getVo());
	}
}
