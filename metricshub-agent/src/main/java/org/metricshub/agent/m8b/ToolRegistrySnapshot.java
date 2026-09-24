package org.metricshub.agent.m8b;

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
import org.metricshub.agent.m8b.protocol.M8bJson;
import org.metricshub.agent.m8b.protocol.ToolDescriptor;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.definition.ToolDefinition;

/**
 * The tools this agent advertises to the M8B Governor, derived from the runtime Spring AI
 * {@link ToolCallbackProvider}: every {@code IMCPToolService} tool not explicitly excluded.
 * <p>
 * Tools are sorted by name and the revision is a fingerprint of the descriptors, so the same tool set
 * always yields the same revision and the server can skip a registry rewrite on reconnect.
 * </p>
 *
 * @param tools     advertised tools, sorted by name
 * @param callbacks the callback executing each advertised tool, keyed by tool name
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
	 * @param excludedTools names never advertised
	 * @return the snapshot
	 */
	public static ToolRegistrySnapshot from(final ToolCallbackProvider provider, final Collection<String> excludedTools) {
		final Map<String, ToolCallback> callbacks = new TreeMap<>();
		for (final ToolCallback callback : provider.getToolCallbacks()) {
			final String name = callback.getToolDefinition().name();
			if (!excludedTools.contains(name)) {
				callbacks.put(name, callback);
			}
		}

		final List<ToolDescriptor> tools = new ArrayList<>(callbacks.size());
		callbacks.values().forEach(callback -> tools.add(describe(callback.getToolDefinition())));
		tools.sort(Comparator.comparing(ToolDescriptor::name));

		return new ToolRegistrySnapshot(List.copyOf(tools), Map.copyOf(callbacks), M8bJson.fingerprint(tools));
	}

	private static ToolDescriptor describe(final ToolDefinition definition) {
		final String description =
			definition.description() == null || definition.description().isBlank()
				? definition.name()
				: definition.description();
		return new ToolDescriptor(definition.name(), description, parseSchema(definition));
	}

	private static JsonNode parseSchema(final ToolDefinition definition) {
		final String schema =
			definition.inputSchema() == null || definition.inputSchema().isBlank() ? EMPTY_SCHEMA : definition.inputSchema();
		try {
			return M8bJson.MAPPER.readTree(schema);
		} catch (JsonProcessingException e) {
			throw new IllegalStateException("Invalid JSON schema for tool " + definition.name(), e);
		}
	}
}
