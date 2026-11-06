package org.metricshub.agent.tunnel;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * MetricsHub Agent
 * ჻჻჻჻჻჻
 * Copyright 2023 - 2026 MetricsHub
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

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.metricshub.agent.context.AgentContext;
import org.metricshub.agent.tunnel.protocol.ToolDescriptor;
import org.metricshub.engine.configuration.IConfiguration;
import org.metricshub.engine.connector.model.Connector;
import org.metricshub.engine.extension.IProtocolExtension;
import org.metricshub.engine.telemetry.TelemetryManager;
import org.metricshub.web.mcp.MCPConfigHelper;
import org.metricshub.web.mcp.ToolAccess.Access;
import org.metricshub.web.mcp.tool.ConnectorToolCallbacks;
import org.metricshub.web.mcp.tool.DynamicToolCallback;
import org.metricshub.web.mcp.tool.HostTools;
import org.metricshub.web.mcp.tool.ProductToolCallback;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;

/**
 * The tool Central asks, over the tunnel, which protocol-extension and connector tools apply to one host: the
 * tools of the protocols configured on it and of the connectors detected on it. Those tools are not in the
 * registration; Central offers them per host, for one conversation turn, and never stores them.
 * <p>
 * Advertised to Central only, never on the agent's own MCP server, whose clients see every tool already.
 * </p>
 */
final class HostToolsLookup {

	/**
	 * The name Central calls it by.
	 */
	static final String NAME = "ListHostTools";

	/**
	 * The argument naming the host.
	 */
	static final String HOSTNAME = "hostname";

	private static final String SCHEMA = """
		{"type":"object","properties":{"hostname":{"type":"string",\
		"description":"The host, as configured in MetricsHub."}},"required":["hostname"]}""";

	/**
	 * What the lookup answers.
	 *
	 * @param hostname the host asked about
	 * @param tools    the tools that apply to it, sorted by name, each with its access class
	 */
	record HostToolList(String hostname, List<ToolDescriptor> tools) {}

	private HostToolsLookup() {}

	/**
	 * @param hostScoped the protocol-extension and connector tools of this snapshot, by name
	 * @param context    the agent context those tools were built from: the lookup answers from it, so that a tool
	 *                   it lists is one the snapshot's callback can run, until the next snapshot replaces both
	 * @return the lookup, a read tool
	 */
	static ToolCallback callback(final Map<String, ToolCallback> hostScoped, final AgentContext context) {
		// Matched ignoring case, as the tool provider deduplicates names: a connector identifier may be spelled
		// differently in the host's store and in the store the tool was built from
		final Map<String, ToolCallback> byName = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
		byName.putAll(hostScoped);
		final ToolDefinition definition = ToolDefinition.builder()
			.name(NAME)
			.description(
				"Lists the protocol and connector tools that apply to one monitored host: those of the protocols " +
					"configured on it and of the connectors detected on it."
			)
			.inputSchema(SCHEMA)
			.build();
		return new ProductToolCallback(
			new DynamicToolCallback(definition, arguments -> lookUp(hostnameOf(arguments), context, byName)),
			Access.READ
		);
	}

	/**
	 * @param hostname the host
	 * @param context  the running agent context
	 * @param byName   the host-scoped tools, by name ignoring case
	 * @return the tools that apply to the host
	 * @throws IllegalStateException when the host is not configured: answered EXECUTION_ERROR
	 */
	static HostToolList lookUp(
		final String hostname,
		final AgentContext context,
		final Map<String, ToolCallback> byName
	) {
		final List<TelemetryManager> managers = context == null
			? List.of()
			: MCPConfigHelper.findTelemetryManagerByHostname(hostname, context);
		if (managers.isEmpty()) {
			throw new IllegalStateException("Host %s is not configured.".formatted(hostname));
		}
		// Only what this snapshot carries: a tool excluded from the tunnel, or one whose declaration was refused,
		// is not offered through the host either
		final Map<String, ToolCallback> listed = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
		// The protocols configured on the host: an extension's tools need one of its configurations there
		final Set<IConfiguration> configurations = MCPConfigHelper.resolveAllHostConfigurationCopies(hostname, context);
		if (context.getExtensionManager() != null) {
			for (final IProtocolExtension extension : context.getExtensionManager().getProtocolExtensions()) {
				if (configurations.stream().anyMatch(extension::isToolConfiguration)) {
					extension.getTools()
						.forEach(
							tool -> listIfCarried(
								listed,
								byName,
								extension.getIdentifier(),
								HostTools.toolName(extension.getIdentifier(), tool.name())
							)
						);
				}
			}
		}
		// The connectors detected on the host: their namespace is OK once detection matched them
		for (final TelemetryManager manager : managers) {
			if (manager.getHostProperties() == null || manager.getConnectorStore() == null) {
				continue;
			}
			final Map<String, Connector> store = manager.getConnectorStore().getStore();
			manager.getHostProperties().getConnectorNamespaces().forEach((connectorId, namespace) -> {
				final Connector connector = store == null ? null : store.get(connectorId);
				if (namespace.isStatusOk() && connector != null) {
					connector.getTools().keySet().forEach(tool -> {
						// The host's own declaration, through its connector variables, must be the one the callback
						// advertises: the callback refuses to run any other
						final ToolCallback callback = byName.get(HostTools.toolName(connectorId, tool));
						if (
							ownedBy(callback, connectorId) &&
								spelledAs(callback, tool) &&
								ConnectorToolCallbacks.runsAsAdvertised(manager, connectorId, tool, callback)
						) {
							listed.put(callback.getToolDefinition().name(), callback);
						}
					});
				}
			});
		}
		return new HostToolList(hostname, listed.values().stream().map(ToolRegistrySnapshot::describe).toList());
	}

	private static void listIfCarried(
		final Map<String, ToolCallback> listed,
		final Map<String, ToolCallback> byName,
		final String owner,
		final String name
	) {
		final ToolCallback callback = byName.get(name);
		if (ownedBy(callback, owner)) {
			listed.put(callback.getToolDefinition().name(), callback);
		}
	}

	/**
	 * @param callback a connector tool the host's connector owns
	 * @param tool     the tool's name in the host's connector
	 * @return whether the callback runs the tool under that very name: a connector's tools are looked up by exact
	 *         name, while the advertised names were matched ignoring case
	 */
	private static boolean spelledAs(final ToolCallback callback, final String tool) {
		final String owner = ((DynamicToolCallback) callback).getOwner();
		return callback.getToolDefinition().name().equals(HostTools.toolName(owner, tool));
	}

	/**
	 * @param callback a host-scoped tool, possibly {@code null}
	 * @param owner    the extension or connector the host has
	 * @return whether the callback is that owner's: two owners may yield the same name, and the provider kept one
	 */
	private static boolean ownedBy(final ToolCallback callback, final String owner) {
		return callback instanceof final DynamicToolCallback dynamic && owner.equalsIgnoreCase(dynamic.getOwner());
	}

	private static String hostnameOf(final JsonNode arguments) {
		final JsonNode hostname = arguments == null ? null : arguments.get(HOSTNAME);
		if (hostname == null || !hostname.isTextual() || hostname.asText().isBlank()) {
			throw new IllegalArgumentException("The hostname argument is required.");
		}
		return hostname.asText();
	}
}
