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

import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

/**
 * Runs every {@link StartupHook} once when the agent has fully started.
 * <p>
 * Spring injects all {@code StartupHook} beans (ordered by
 * {@link org.springframework.core.annotation.Order} when present) and this runner invokes them on
 * {@link ApplicationReadyEvent}. Because both the community and the enterprise agents boot the same
 * Spring context, every hook runs in <b>both</b> editions without any edition-specific wiring.
 * </p>
 * <p>
 * Each hook is executed inside its own try/catch: a failing hook is logged and skipped so it never
 * prevents the agent from starting nor blocks the remaining hooks.
 * </p>
 */
@Service
@Slf4j
public class AgentStartupRunner {

	/** All startup hooks discovered by Spring, in {@link org.springframework.core.annotation.Order} order. */
	private final List<StartupHook> startupHooks;

	/** Guards against running the hooks more than once. */
	private boolean started;

	/**
	 * Creates the runner.
	 *
	 * @param startupHooks all {@link StartupHook} beans (an empty list when none are declared)
	 */
	public AgentStartupRunner(final List<StartupHook> startupHooks) {
		this.startupHooks = startupHooks;
	}

	/**
	 * Runs every startup hook once, after the Spring application is ready. Safe to be invoked again
	 * (subsequent calls are no-ops), and safe when no hook is declared.
	 */
	@EventListener(ApplicationReadyEvent.class)
	public synchronized void runAll() {
		if (started) {
			return;
		}
		started = true;

		if (startupHooks.isEmpty()) {
			log.debug("No startup hooks to run.");
			return;
		}

		log.info("Running {} startup hook(s).", startupHooks.size());
		for (final StartupHook hook : startupHooks) {
			try {
				hook.onStartup();
			} catch (Exception e) {
				log.error("Startup hook '{}' failed: {}", hook.getClass().getName(), e.getMessage());
				log.debug("Startup hook error", e);
			}
		}
	}
}
