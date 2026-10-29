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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import lombok.extern.slf4j.Slf4j;
import org.metricshub.agent.context.AgentContext;
import org.metricshub.web.AgentContextHolder;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;

/**
 * Every tool the agent exposes: the product tools, which are annotated methods fixed for the JVM, followed by the
 * protocol and connector tools, which depend on the loaded extensions and connectors and are rebuilt whenever a
 * configuration reload swaps the agent context.
 */
@Slf4j
public final class MetricsHubToolCallbackProvider implements ToolCallbackProvider {

	private final ToolCallback[] staticCallbacks;
	private final AgentContextHolder agentContextHolder;
	private final List<Function<AgentContext, List<ToolCallback>>> dynamicFactories;

	/**
	 * The callbacks built for one generation of the agent context.
	 *
	 * @param generation the generation they were built for
	 * @param callbacks  the callbacks
	 */
	private record Built(long generation, ToolCallback[] callbacks) {}

	private volatile Built built;

	/**
	 * @param staticCallbacks    the product tools, fixed for the JVM
	 * @param agentContextHolder the running agent context
	 */
	public MetricsHubToolCallbackProvider(
		final ToolCallback[] staticCallbacks,
		final AgentContextHolder agentContextHolder
	) {
		this(staticCallbacks, agentContextHolder, List.of(ProtocolToolCallbacks::from, ConnectorToolCallbacks::from));
	}

	MetricsHubToolCallbackProvider(
		final ToolCallback[] staticCallbacks,
		final AgentContextHolder agentContextHolder,
		final List<Function<AgentContext, List<ToolCallback>>> dynamicFactories
	) {
		this.staticCallbacks = staticCallbacks.clone();
		this.agentContextHolder = agentContextHolder;
		this.dynamicFactories = dynamicFactories;
	}

	@Override
	public ToolCallback[] getToolCallbacks() {
		Built current = built;
		long generation = agentContextHolder.getGeneration();
		if (current == null || current.generation() != generation) {
			// One context for every factory: the generation is read before and after taking the context, so
			// that a reload in between is noticed and the snapshot is never half old, half new
			AgentContext agentContext = agentContextHolder.getAgentContext();
			if (generation != agentContextHolder.getGeneration()) {
				generation = agentContextHolder.getGeneration();
				agentContext = agentContextHolder.getAgentContext();
			}
			// Two threads may build the same generation at once: both results are equivalent
			current = new Built(generation, build(agentContext));
			built = current;
		}
		return current.callbacks().clone();
	}

	private ToolCallback[] build(final AgentContext agentContext) {
		final List<ToolCallback> callbacks = new ArrayList<>(List.of(staticCallbacks));
		for (final Function<AgentContext, List<ToolCallback>> factory : dynamicFactories) {
			try {
				callbacks.addAll(factory.apply(agentContext));
			} catch (Exception e) {
				// The product tools must stay available whatever an extension or connector declares
				log.error("Failed to build the dynamic MCP tools: {}", e.getMessage());
				log.debug("Failed to build the dynamic MCP tools:", e);
			}
		}
		// One callback per advertised name, whatever its case: the MCP and tunnel registries key on it, the
		// chat looks it up ignoring case, and a second definition would silently replace the first in one
		// place and not the other
		final Map<String, ToolCallback> byName = new LinkedHashMap<>();
		for (final ToolCallback callback : callbacks) {
			final String name = callback.getToolDefinition().name();
			if (byName.putIfAbsent(name.toLowerCase(Locale.ROOT), callback) != null) {
				log.error("Tool {} is declared twice; the second declaration is ignored.", name);
			}
		}
		return byName.values().toArray(ToolCallback[]::new);
	}
}
