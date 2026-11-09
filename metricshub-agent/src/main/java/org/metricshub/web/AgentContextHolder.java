package org.metricshub.web;

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

import java.util.Objects;
import org.metricshub.agent.context.AgentContext;

/**
 * Single, mutable holder for the currently active {@link AgentContext} instance.
 * <p>
 * This class is the single source of truth for the running agent context. All web and MCP
 * services should retrieve the current context via {@link #getAgentContext()} (never cache it),
 * and every reload / restart path should publish the newly built context via
 * {@link #setAgentContext(AgentContext)}.
 * </p>
 * <p>
 * Each successful {@code setAgentContext(...)} call increments a monotonically increasing
 * {@link #getGeneration() generation} counter — helpful in logs and diagnostics to confirm
 * which context is currently being served after a reload.
 * </p>
 */
public class AgentContextHolder {

	/**
	 * A context with the generation it was published as.
	 *
	 * @param agentContext the context
	 * @param generation   its generation: {@code 1} for the bootstrap context, then one more per publication
	 */
	private record Published(AgentContext agentContext, long generation) {}

	/**
	 * The context and its generation in one field, so that a reader never pairs a context with another one's
	 * generation: two fields would let a reader see the new context while the generation still says the old one.
	 */
	private volatile Published published;

	/**
	 * Create a new holder pre-populated with the given (bootstrap) agent context.
	 *
	 * @param agentContext the initial {@link AgentContext}; must not be {@code null}
	 */
	public AgentContextHolder(final AgentContext agentContext) {
		this.published = new Published(Objects.requireNonNull(agentContext, "agentContext must not be null"), 1L);
	}

	/**
	 * @return the currently active {@link AgentContext}.
	 */
	public AgentContext getAgentContext() {
		return published.agentContext();
	}

	/**
	 * Publish a new {@link AgentContext} as the currently active one.
	 * <p>
	 * The {@link #getGeneration() generation counter} is incremented atomically on every call.
	 * Callers are expected to have started all services on the new context before publishing it,
	 * and to {@link AgentContext#close() close} the previous context <b>after</b> the swap.
	 * </p>
	 *
	 * @param agentContext the new {@link AgentContext}; must not be {@code null}
	 */
	public synchronized void setAgentContext(final AgentContext agentContext) {
		Objects.requireNonNull(agentContext, "agentContext must not be null");
		this.published = new Published(agentContext, published.generation() + 1);
	}

	/**
	 * @return the monotonically increasing generation of the currently held context. The
	 *         bootstrap context has generation {@code 1}; each subsequent
	 *         {@link #setAgentContext(AgentContext)} increments this value.
	 */
	public long getGeneration() {
		return published.generation();
	}
}
