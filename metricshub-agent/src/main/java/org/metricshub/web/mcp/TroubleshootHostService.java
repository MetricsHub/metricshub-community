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

import java.util.ArrayList;
import java.util.List;
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
	 * Default pool size for troubleshooting operations.
	 */
	private static final int DEFAULT_TROUBLESHOOT_POOL_SIZE = 20;

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
	 * Triggers resource collection for the specified hostname(s), optionally forcing a connector.
	 * Returns one result per matching telemetry manager under each hostname, or an error when the hostname is unknown.
	 *
	 * @param hostname    the hostname(s) to collect from
	 * @param connectorId optional connector identifier to use for collection; if absent, selection is automatic
	 * @param poolSize    optional pool size for concurrent collection; defaults to {@value #DEFAULT_TROUBLESHOOT_POOL_SIZE} when {@code null} or ≤ 0
	 * @return a multi-host response mapping each input hostname to one or more {@link TelemetryResult}, or an error message
	 */
	@Tool(
		name = "CollectMetricsForHost",
		description = """
		Fetch and collect metrics for the specified host(s) using the configured protocols and credentials,
		and the applicable MetricsHub connectors (MIB2, Linux, Windows, Dell, RedFish, etc.).
		Returns the collected metrics and all attributes.
		Metrics follow OpenTelemetry semantic conventions.
		"""
	)
	public MultiHostToolResponse<TelemetryResult> collectMetricsForHost(
		@ToolParam(description = "The hostname(s) of the resource we are interested in", required = true) final List<
			String
		> hostname,
		@ToolParam(
			description = """
			Optional: The identifier of a specific connector to use for collecting metrics.
			If not specified, the system will attempt to select an appropriate connector automatically.
			""",
			required = false
		) final String connectorId,
		@ToolParam(
			description = "Optional pool size for concurrent metric collection. Defaults to 20.",
			required = false
		) final Integer poolSize
	) {
		final int resolvedPoolSize = resolvePoolSize(poolSize, DEFAULT_TROUBLESHOOT_POOL_SIZE);
		final MultiHostToolResponse<List<TelemetryResult>> hostsExecutionResults = executeForHosts(
			hostname,
			this::buildNullHostnameTelemetryResponse,
			host -> {
				final List<TelemetryResult> telemetryResults = new ArrayList<>();
				final List<TelemetryManager> telemetryManagers = MCPConfigHelper.findTelemetryManagerByHostname(
					host,
					agentContextHolder
				);
				if (telemetryManagers.isEmpty()) {
					return HostToolResponse
						.<List<TelemetryResult>>builder()
						.hostname(host)
						.response(List.of(new TelemetryResult(HOSTNAME_NOT_CONFIGURED_MSG.formatted(host))))
						.build();
				}
				telemetryManagers.forEach(telemetryManager ->
					telemetryResults.add(collectMetricsForHostInternal(telemetryManager, connectorId))
				);
				return HostToolResponse.<List<TelemetryResult>>builder().hostname(host).response(telemetryResults).build();
			},
			resolvedPoolSize
		);

		return MCPConfigHelper.flattenHostsResult(hostsExecutionResults);
	}

	/**
	 * Retrieves metrics from the MetricsHub cache for the specified hostname(s).
	 * Returns one result per matching telemetry manager under each hostname, or an error when the hostname is unknown.
	 *
	 * @param hostname the hostname(s) whose cached metrics are requested
	 * @param poolSize optional pool size for concurrent cache reads; defaults to {@value #DEFAULT_TROUBLESHOOT_POOL_SIZE} when {@code null} or ≤ 0
	 * @return a multi-host response mapping each input hostname to one or more {@link TelemetryResult}, or an error message
	 */
	@Tool(
		name = "GetMetricsFromCacheForHost",
		description = """
		Retrieves metrics from the MetricsHub cache for the specified host(s).
		Returns the collected metrics and all attributes.
		Metrics follow OpenTelemetry semantic conventions.
		"""
	)
	public MultiHostToolResponse<TelemetryResult> getMetricsFromCacheForHost(
		@ToolParam(description = "The hostname(s) of the resource we are interested in", required = true) final List<
			String
		> hostname,
		@ToolParam(
			description = "Optional pool size for concurrent cache reads. Defaults to 20.",
			required = false
		) final Integer poolSize
	) {
		final int resolvedPoolSize = resolvePoolSize(poolSize, DEFAULT_TROUBLESHOOT_POOL_SIZE);
		final MultiHostToolResponse<List<TelemetryResult>> hostsExecutionResults = executeForHosts(
			hostname,
			this::buildNullHostnameTelemetryResponse,
			host -> {
				final List<TelemetryResult> telemetryResults = new ArrayList<>();
				final List<TelemetryManager> telemetryManagers = MCPConfigHelper.findTelemetryManagerByHostname(
					host,
					agentContextHolder
				);
				if (telemetryManagers.isEmpty()) {
					return HostToolResponse
						.<List<TelemetryResult>>builder()
						.hostname(host)
						.response(List.of(new TelemetryResult(HOSTNAME_NOT_CONFIGURED_MSG.formatted(host))))
						.build();
				}
				telemetryManagers.forEach(telemetryManager ->
					telemetryResults.add(getMetricsFromCacheForHostInternal(telemetryManager))
				);
				return HostToolResponse.<List<TelemetryResult>>builder().hostname(host).response(telemetryResults).build();
			},
			resolvedPoolSize
		);

		return MCPConfigHelper.flattenHostsResult(hostsExecutionResults);
	}

	/**
	 * Tests available connectors for the specified hostname(s), optionally restricting to one connector.
	 * Returns one result per matching telemetry manager under each hostname, or an error when the hostname is unknown.
	 *
	 * @param hostname    the hostname(s) to test
	 * @param connectorId optional connector identifier to restrict the test
	 * @param poolSize    optional pool size for concurrent tests; defaults to {@value #DEFAULT_TROUBLESHOOT_POOL_SIZE} when {@code null} or ≤ 0
	 * @return a multi-host response mapping each input hostname to one or more {@link TelemetryResult}, or an error message
	 */
	@Tool(
		name = "TestAvailableConnectorsForHost",
		description = """
		Test all applicable MetricsHub connectors (MIB2, Linux, Windows, Dell, RedFish, etc.) against the specified host(s)
		using the configured credentials and return the list of connectors that work with these hosts.
		"""
	)
	public MultiHostToolResponse<TelemetryResult> testAvailableConnectorsForHost(
		@ToolParam(description = "The hostname(s) of the resource we are interested in") final List<String> hostname,
		@ToolParam(
			description = """
			Optional: The identifier of a specific connector to use for the test.
			If not specified, the system will attempt to select an appropriate connector automatically.
			""",
			required = false
		) final String connectorId,
		@ToolParam(
			description = "Optional pool size for concurrent connector tests. Defaults to 20.",
			required = false
		) final Integer poolSize
	) {
		final int resolvedPoolSize = resolvePoolSize(poolSize, DEFAULT_TROUBLESHOOT_POOL_SIZE);
		final MultiHostToolResponse<List<TelemetryResult>> hostsExecutionResults = executeForHosts(
			hostname,
			this::buildNullHostnameTelemetryResponse,
			host -> {
				final List<TelemetryResult> telemetryResults = new ArrayList<>();
				final List<TelemetryManager> telemetryManagers = MCPConfigHelper.findTelemetryManagerByHostname(
					host,
					agentContextHolder
				);
				if (telemetryManagers.isEmpty()) {
					return HostToolResponse
						.<List<TelemetryResult>>builder()
						.hostname(host)
						.response(List.of(new TelemetryResult(HOSTNAME_NOT_CONFIGURED_MSG.formatted(host))))
						.build();
				}
				telemetryManagers.forEach(telemetryManager ->
					telemetryResults.add(testAvailableConnectorsForHostInternal(telemetryManager, connectorId))
				);
				return HostToolResponse.<List<TelemetryResult>>builder().hostname(host).response(telemetryResults).build();
			},
			resolvedPoolSize
		);

		return MCPConfigHelper.flattenHostsResult(hostsExecutionResults);
	}

	/**
	 * Executes the troubleshoot pipeline and collects metrics for the supplied telemetry manager.
	 *
	 * @param telemetryManager the manager representing the host context
	 * @param connectorId      optional connector identifier to narrow the scope
	 * @return a {@link TelemetryResult} containing collected metrics
	 */
	private TelemetryResult collectMetricsForHostInternal(
		final TelemetryManager telemetryManager,
		final String connectorId
	) {
		final var newTelemetryManager = MCPConfigHelper.newFrom(telemetryManager, connectorId);

		final var clientsExecutor = new ClientsExecutor(newTelemetryManager);
		final long discoveryTime = System.currentTimeMillis();
		final var extensionManager = agentContextHolder.getAgentContext().getExtensionManager();

		newTelemetryManager.run(
			new DetectionStrategy(newTelemetryManager, discoveryTime, clientsExecutor, extensionManager),
			new DiscoveryStrategy(newTelemetryManager, discoveryTime, clientsExecutor, extensionManager),
			new SimpleStrategy(newTelemetryManager, discoveryTime, clientsExecutor, extensionManager),
			new HardwarePostDiscoveryStrategy(newTelemetryManager, discoveryTime, clientsExecutor, extensionManager),
			new HardwareMonitorNameGenerationStrategy(newTelemetryManager, discoveryTime, clientsExecutor, extensionManager)
		);

		final long collectTime = System.currentTimeMillis();

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
	 * Returns the cached telemetry result for the supplied telemetry manager.
	 *
	 * @param telemetryManager the manager representing the host context
	 * @return a {@link TelemetryResult} populated with the cached value
	 */
	private TelemetryResult getMetricsFromCacheForHostInternal(final TelemetryManager telemetryManager) {
		return new TelemetryResult(telemetryManager.getVo());
	}

	/**
	 * Runs detection to evaluate which connectors work for the supplied telemetry manager.
	 *
	 * @param telemetryManager the manager representing the host context
	 * @param connectorId      optional connector identifier to restrict testing
	 * @return a {@link TelemetryResult} reporting detection outcomes
	 */
	private TelemetryResult testAvailableConnectorsForHostInternal(
		final TelemetryManager telemetryManager,
		final String connectorId
	) {
		final var newTelemetryManager = MCPConfigHelper.newFrom(telemetryManager, connectorId);

		final var clientsExecutor = new ClientsExecutor(newTelemetryManager);
		final long detectionTime = System.currentTimeMillis();
		final var extensionManager = agentContextHolder.getAgentContext().getExtensionManager();

		newTelemetryManager.run(
			new DetectionStrategy(newTelemetryManager, detectionTime, clientsExecutor, extensionManager),
			new DiscoveryStrategy(newTelemetryManager, detectionTime, clientsExecutor, extensionManager),
			new SimpleStrategy(newTelemetryManager, detectionTime, clientsExecutor, extensionManager),
			new HardwarePostDiscoveryStrategy(newTelemetryManager, detectionTime, clientsExecutor, extensionManager),
			new HardwareMonitorNameGenerationStrategy(newTelemetryManager, detectionTime, clientsExecutor, extensionManager)
		);

		return new TelemetryResult(newTelemetryManager.getVo());
	}

	/**
	 * Builds a {@link HostToolResponse} reporting the error generated when a null hostname is processed.
	 *
	 * @return a host-level response containing a {@link TelemetryResult} that carries the missing-hostname error message
	 */
	private HostToolResponse<List<TelemetryResult>> buildNullHostnameTelemetryResponse() {
		return IMCPToolService.super.buildNullHostnameResponse(() -> List.of(new TelemetryResult(NULL_HOSTNAME_ERROR)));
	}
}
