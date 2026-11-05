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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.metricshub.agent.context.AgentContext;
import org.metricshub.agent.tunnel.protocol.ToolDescriptor;
import org.metricshub.agent.tunnel.protocol.TunnelJson;
import org.metricshub.web.mcp.ToolAccess.Access;
import org.metricshub.web.mcp.tool.DynamicToolCallback;
import org.metricshub.web.mcp.tool.ProductToolCallback;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.definition.ToolDefinition;

/**
 * The tools this agent offers Central, derived from the runtime Spring AI {@link ToolCallbackProvider}.
 * <p>
 * The registration advertises the agent's own tools ({@code IMCPToolService}) and {@link HostToolsLookup#NAME};
 * the protocol-extension and connector tools are not advertised but answered per host by that lookup, and stay
 * invokable here. Tools are sorted by name and the revision is a fingerprint of the advertised descriptors, so the
 * same tool set always yields the same revision and the server can skip a registry rewrite on reconnect.
 * </p>
 *
 * @param tools     advertised tools, sorted by name
 * @param callbacks the callback executing each tool Central may invoke, the host-scoped ones included, by name
 * @param revision  fingerprint of {@code tools}
 */
public record ToolRegistrySnapshot(List<ToolDescriptor> tools, Map<String, ToolCallback> callbacks, String revision) {
	/**
	 * Schema advertised for a tool without arguments.
	 */
	static final String EMPTY_SCHEMA = "{\"type\":\"object\",\"properties\":{}}";

	/**
	 * Builds the snapshot from the runtime callbacks.
	 *
	 * @param provider      the Spring AI tool callbacks
	 * @param excludedTools names never advertised nor invokable
	 * @param context       the agent context the provider's tools were built from, which the host lookup answers from
	 * @return the snapshot
	 */
	public static ToolRegistrySnapshot from(
		final ToolCallbackProvider provider,
		final Collection<String> excludedTools,
		final AgentContext context
	) {
		final Map<String, ToolCallback> callbacks = new TreeMap<>();
		final Map<String, ToolCallback> hostScoped = new TreeMap<>();
		for (final ToolCallback callback : provider.getToolCallbacks()) {
			final String name = callback.getToolDefinition().name();
			// A callback of no known kind has no access class: neither advertised nor invokable
			if (!excludedTools.contains(name) && accessOf(callback) != null) {
				callbacks.put(name, callback);
				if (callback instanceof DynamicToolCallback) {
					hostScoped.put(name, callback);
				}
			}
		}
		// Excluded like any other tool: without it, Central reaches none of the host-scoped tools
		if (!excludedTools.contains(HostToolsLookup.NAME)) {
			callbacks.put(HostToolsLookup.NAME, HostToolsLookup.callback(hostScoped, context));
		}

		final List<ToolDescriptor> tools = new ArrayList<>();
		callbacks.values()
			.stream()
			.filter(callback -> callback instanceof ProductToolCallback)
			.forEach(callback -> tools.add(describe(callback)));
		tools.sort(Comparator.comparing(ToolDescriptor::name));

		return new ToolRegistrySnapshot(List.copyOf(tools), Map.copyOf(callbacks), TunnelJson.fingerprint(tools));
	}

	/**
	 * @param callback a tool
	 * @return the class its {@code @ToolAccess} declares for the agent's own tool, write for a protocol-extension
	 *         or connector tool that changes a host and read for any other, or null for a callback of no known kind
	 */
	private static Access accessOf(final ToolCallback callback) {
		if (callback instanceof final ProductToolCallback product) {
			return product.access();
		}
		if (callback instanceof final DynamicToolCallback dynamic) {
			return dynamic.isDestructive() ? Access.WRITE : Access.READ;
		}
		return null;
	}

	/**
	 * @param callback a tool of a known kind
	 * @return its descriptor, with its access class
	 */
	static ToolDescriptor describe(final ToolCallback callback) {
		final Access access = accessOf(callback);
		final ToolDefinition definition = callback.getToolDefinition();
		final String description = definition.description() == null || definition.description().isBlank()
			? definition.name()
			: definition.description();
		return new ToolDescriptor(definition.name(), description, parseSchema(definition), access.wire());
	}

	private static JsonNode parseSchema(final ToolDefinition definition) {
		final String schema = definition.inputSchema() == null || definition.inputSchema().isBlank()
			? EMPTY_SCHEMA
			: definition.inputSchema();
		try {
			return TunnelJson.MAPPER.readTree(schema);
		} catch (JsonProcessingException e) {
			throw new IllegalStateException("Invalid JSON schema for tool " + definition.name(), e);
		}
	}
}
