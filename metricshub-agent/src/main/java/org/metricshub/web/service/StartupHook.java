package org.metricshub.web.service;

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

/**
 * Extension point for logic that must run once, when the MetricsHub agent has fully started.
 * <p>
 * Any Spring bean that implements this interface is discovered and executed by
 * {@link AgentStartupRunner} on application startup. Because both the community and the enterprise
 * agents boot the same Spring context (both call
 * {@code MetricsHubAgentServer.startServer(...)}), a {@code StartupHook} runs in <b>both</b>
 * editions with no edition-specific wiring — adding startup logic never requires touching the
 * enterprise bootstrap.
 * </p>
 * <p>
 * This is the startup-time counterpart of the restart hooks registered through
 * {@code AgentLifecycleService.addPreRestartHook(...)} / {@code addPostRestartHook(...)}.
 * </p>
 * <p>
 * Implementations that need a specific execution order may be annotated with
 * {@link org.springframework.core.annotation.Order} (lower values run first). A hook must not rely
 * on other hooks having run, and any exception it throws is isolated by the runner so it never
 * prevents the agent from starting or blocks the other hooks.
 * </p>
 */
@FunctionalInterface
public interface StartupHook {
	/**
	 * Invoked once, after the agent has fully started. Implementations should return quickly;
	 * long-running work should be handed off to a background executor.
	 */
	void onStartup();
}
