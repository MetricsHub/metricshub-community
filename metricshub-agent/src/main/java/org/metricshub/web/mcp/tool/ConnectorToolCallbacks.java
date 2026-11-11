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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Stream;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.metricshub.agent.context.AgentContext;
import org.metricshub.engine.client.ClientsExecutor;
import org.metricshub.engine.common.helpers.MetricsHubConstants;
import org.metricshub.engine.connector.model.Connector;
import org.metricshub.engine.connector.model.ConnectorStore;
import org.metricshub.engine.connector.model.common.ContentType;
import org.metricshub.engine.connector.model.tool.ConnectorTool;
import org.metricshub.engine.strategy.source.SourceTable;
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

	private static final String OTHER_DECLARATION_ERROR = "Connector %s declares tool %s otherwise on host %s, through its variables: another description, other parameters or another destructive flag.";

	/**
	 * What a connector tool returns for one host.
	 *
	 * @param columns the column names the tool declares, empty when it declares none
	 * @param rows    the rows of the tool's result source
	 */
	public record ToolTable(List<String> columns, List<List<String>> rows) {}

	/**
	 * What a tool returns when its result is text (a command output): the text as it is, for the LLM to read.
	 *
	 * @param text the text of the result source
	 */
	public record ToolText(String text) {}

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
		// and under the identifier of each additional connector using them, which may name another connector in
		// another resource: one tool per connector identifier and tool name, declared by the first store that has
		// it, each host running its own (see run)
		final Map<String, Map<String, ConnectorTool>> tools = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
		Stream.concat(
			Stream.of(connectorStore),
			agentContext.getTelemetryManagers()
				.values()
				.stream()
				.flatMap(telemetryManagers -> telemetryManagers.values().stream())
				.map(TelemetryManager::getConnectorStore)
		)
			.filter(store -> store != null && store.getStore() != null)
			.forEach(
				store -> store.getStore()
					.forEach(
						(connectorId, connector) -> connector.getTools()
							.forEach(
								(toolName, tool) -> tools.computeIfAbsent(connectorId, id -> new LinkedHashMap<>())
									.putIfAbsent(toolName, tool)
							)
					)
			);

		tools.forEach((connectorId, connectorTools) -> connectorTools.forEach((toolName, tool) -> {
			// Destructive tools included: the interfaces decide what they expose
			try {
				final ToolDefinition definition = tool.toDefinition(toolName);
				callbacks.add(
					HostTools.perHost(
						connectorId,
						HostTools.toolName(connectorId, toolName),
						definition,
						(hostname, arguments) -> run(agentContext, connectorId, toolName, definition, hostname, arguments)
					)
				);
			} catch (IllegalArgumentException e) {
				// One bad declaration must not take the connector's other tools down
				log.warn("Connector {}: tool {} is not exposed: {}", connectorId, toolName, e.getMessage());
			}
		}));
		return callbacks;
	}

	/**
	 * Runs a connector tool on one host, with the host's own connector: a connector with variables is resolved
	 * with the variables of the host's resource.
	 *
	 * @param agentContext the agent context the tool was built from, where the host's configuration is
	 * @param connectorId  the connector identifier in the host's store
	 * @param toolName     the tool name within the connector
	 * @param definition   the advertised definition, which the arguments were checked and completed with
	 * @param hostname     the host
	 * @param arguments    the resolved arguments
	 * @return the table of the tool's result source, or an error when the host is not configured, when none of its
	 *         resources has the tool, or when none declares it as advertised
	 */
	static Object run(
		final AgentContext agentContext,
		final String connectorId,
		final String toolName,
		final ToolDefinition definition,
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

		if (telemetryManagers.stream().noneMatch(candidate -> toolOf(candidate, connectorId, toolName) != null)) {
			return QueryResponse.builder()
				.error("Connector %s has no tool %s on host %s.".formatted(connectorId, toolName, hostname))
				.build();
		}

		// A host may belong to several resources: the first one whose variables leave the tool as advertised, since
		// the arguments were completed with its defaults and the assistant chose it for its description
		final TelemetryManager hostTelemetryManager = telemetryManagers.stream()
			.filter(candidate -> declaresAsAdvertised(candidate, connectorId, toolName, definition))
			.findFirst()
			.orElse(null);
		if (hostTelemetryManager == null) {
			return QueryResponse.builder().error(OTHER_DECLARATION_ERROR.formatted(connectorId, toolName, hostname)).build();
		}
		final Connector connector = hostTelemetryManager.getConnectorStore().getStore().get(connectorId);
		final ConnectorTool tool = connector.getTools().get(toolName);

		// A telemetry manager of its own: the tool's source tables must not land in the collected one
		final TelemetryManager telemetryManager = MCPConfigHelper.newFrom(hostTelemetryManager, connectorId);
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

		return toolResponse(tool, strategy.getResult());
	}

	/**
	 * What a tool returns for its result source. A tool without columns returns its text when the desugaring found
	 * its result ends in text, and also when the result is a non-empty text at run time only: a table union whose
	 * members converted to tables all came back empty leaves the text of its pure-text member. Any other result is
	 * returned as rows, a text split as any text read as a table.
	 *
	 * @param tool   the tool
	 * @param result the result source's table
	 * @return a {@link ToolText} or a {@link ToolTable}
	 */
	static Object toolResponse(final ConnectorTool tool, final SourceTable result) {
		final List<String> columns = tool.getResult().columns();
		final boolean noColumns = columns == null || columns.isEmpty();
		if (tool.isTextResult() || (noColumns && result.getContentType() == ContentType.TEXT && !result.isEmpty())) {
			return new ToolText(result.asText().replace("\r\n", "\n"));
		}
		return new ToolTable(columns, result.asTable());
	}

	/**
	 * Whether a host's own declaration of a connector tool, through its connector variables, is the one a callback
	 * advertises: the only one the callback runs there.
	 *
	 * @param telemetryManager the host's telemetry manager
	 * @param connectorId      the connector identifier in the host's store
	 * @param toolName         the tool name within the connector
	 * @param callback         the callback built for the tool
	 * @return whether the callback runs the tool on that host
	 */
	public static boolean runsAsAdvertised(
		final TelemetryManager telemetryManager,
		final String connectorId,
		final String toolName,
		final ToolCallback callback
	) {
		final ConnectorTool tool = toolOf(telemetryManager, connectorId, toolName);
		if (tool == null) {
			return false;
		}
		try {
			// The very predicate run() applies: the declarations equal, whatever order the parameters come in
			return callback instanceof final DynamicToolCallback dynamic &&
				tool.toDefinition(toolName).equals(dynamic.getDeclaration());
		} catch (IllegalArgumentException e) {
			// Variables that make the declaration invalid
			return false;
		}
	}

	private static ConnectorTool toolOf(
		final TelemetryManager telemetryManager,
		final String connectorId,
		final String toolName
	) {
		final Connector connector = telemetryManager.getConnectorStore().getStore().get(connectorId);
		return connector == null ? null : connector.getTools().get(toolName);
	}

	private static boolean declaresAsAdvertised(
		final TelemetryManager telemetryManager,
		final String connectorId,
		final String toolName,
		final ToolDefinition definition
	) {
		final ConnectorTool tool = toolOf(telemetryManager, connectorId, toolName);
		try {
			return tool != null && definition.equals(tool.toDefinition(toolName));
		} catch (IllegalArgumentException e) {
			// Variables that make the declaration invalid
			return false;
		}
	}
}
