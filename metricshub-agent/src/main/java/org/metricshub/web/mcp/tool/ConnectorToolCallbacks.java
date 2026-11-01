package org.metricshub.web.mcp.tool;

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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.metricshub.agent.context.AgentContext;
import org.metricshub.engine.client.ClientsExecutor;
import org.metricshub.engine.common.helpers.MetricsHubConstants;
import org.metricshub.engine.connector.model.Connector;
import org.metricshub.engine.connector.model.ConnectorStore;
import org.metricshub.engine.connector.model.tool.ConnectorTool;
import org.metricshub.engine.strategy.tool.ToolStrategy;
import org.metricshub.engine.telemetry.MonitorFactory;
import org.metricshub.engine.telemetry.TelemetryManager;
import org.metricshub.engine.tool.ToolDefinition;
import org.metricshub.web.mcp.MCPConfigHelper;
import org.metricshub.web.mcp.QueryResponse;
import org.springframework.ai.tool.ToolCallback;

/**
 * The tools the connectors declare under their {@code tools:} section, one MCP tool each, named
 * {@code <connector>_<tool>}. On each host the tool runs through {@link ToolStrategy} on a telemetry manager of
 * its own, built from the host's configuration, so that the collected monitors are left untouched.
 */
@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ConnectorToolCallbacks {

	/**
	 * What a connector tool returns for one host.
	 *
	 * @param columns the column names the tool declares, empty when it declares none
	 * @param rows    the rows of the tool's result source
	 */
	public record ToolTable(List<String> columns, List<List<String>> rows) {}

	/**
	 * @param agentContext the agent context to build the tools from, the one every callback stays on
	 * @return the tools of every connector in the store and in the resources' stores, destructive ones included
	 */
	public static List<ToolCallback> from(final AgentContext agentContext) {
		final List<ToolCallback> callbacks = new ArrayList<>();
		final ConnectorStore connectorStore = agentContext == null ? null : agentContext.getConnectorStore();
		if (connectorStore == null || connectorStore.getStore() == null) {
			return callbacks;
		}

		// Connectors with variables are only resolved in the store of each resource, under their own identifier
		// and under the identifier of each additional connector using them. The first one found declares the tool;
		// each host runs its own.
		final Map<String, Connector> connectors = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
		connectors.putAll(connectorStore.getStore());
		agentContext
			.getTelemetryManagers()
			.values()
			.stream()
			.flatMap(telemetryManagers -> telemetryManagers.values().stream())
			.map(TelemetryManager::getConnectorStore)
			.filter(resourceConnectorStore -> resourceConnectorStore != null && resourceConnectorStore.getStore() != null)
			.forEach(resourceConnectorStore -> resourceConnectorStore.getStore().forEach(connectors::putIfAbsent));

		connectors.forEach((connectorId, connector) ->
			connector
				.getTools()
				.forEach((toolName, tool) -> {
					// Destructive tools included: the interfaces decide what they expose
					try {
						final ToolDefinition definition = tool.toDefinition(toolName);
						callbacks.add(
							HostTools.perHost(HostTools.toolName(connectorId, toolName), definition, (hostname, arguments) ->
								run(agentContext, connectorId, toolName, hostname, arguments)
							)
						);
					} catch (IllegalArgumentException e) {
						// One bad declaration must not take the connector's other tools down
						log.warn("Connector {}: tool {} is not exposed: {}", connectorId, toolName, e.getMessage());
					}
				})
		);
		return callbacks;
	}

	/**
	 * Runs a connector tool on one host, with the host's own connector: a connector with variables is resolved
	 * with the variables of the host's resource.
	 *
	 * @param agentContext the agent context the tool was built from, where the host's configuration is
	 * @param connectorId  the connector identifier in the host's store
	 * @param toolName     the tool name within the connector
	 * @param hostname     the host
	 * @param arguments    the resolved arguments
	 * @return the table of the tool's result source, or an error when the host is not configured or its connector
	 *         store has no such tool
	 */
	static Object run(
		final AgentContext agentContext,
		final String connectorId,
		final String toolName,
		final String hostname,
		final Map<String, String> arguments
	) {
		final List<TelemetryManager> telemetryManagers = MCPConfigHelper.findTelemetryManagerByHostname(
			hostname,
			agentContext
		);
		if (telemetryManagers.isEmpty()) {
			return QueryResponse.builder().error("Host %s is not configured.".formatted(hostname)).build();
		}

		final Connector connector = telemetryManagers.get(0).getConnectorStore().getStore().get(connectorId);
		final ConnectorTool tool = connector == null ? null : connector.getTools().get(toolName);
		if (tool == null) {
			return QueryResponse.builder()
				.error("Connector %s has no tool %s on host %s.".formatted(connectorId, toolName, hostname))
				.build();
		}

		// A telemetry manager of its own: the tool's source tables must not land in the collected one
		final TelemetryManager telemetryManager = MCPConfigHelper.newFrom(telemetryManagers.get(0), connectorId);
		final long strategyTime = System.currentTimeMillis();
		// The job duration metrics of the connector's beforeAll sources need the endpoint host monitor
		MonitorFactory.builder()
			.telemetryManager(telemetryManager)
			.discoveryTime(strategyTime)
			.keys(new HashSet<>(Set.of(MetricsHubConstants.HOST_NAME)))
			.build()
			.createEndpointHostMonitor();

		final ToolStrategy strategy = ToolStrategy.builder()
			.telemetryManager(telemetryManager)
			.strategyTime(strategyTime)
			.clientsExecutor(new ClientsExecutor(telemetryManager))
			.extensionManager(agentContext.getExtensionManager())
			.connector(connector)
			.toolName(toolName)
			.arguments(arguments)
			.build();
		strategy.run();

		return new ToolTable(tool.getResult().columns(), strategy.getResult().getTable());
	}
}
