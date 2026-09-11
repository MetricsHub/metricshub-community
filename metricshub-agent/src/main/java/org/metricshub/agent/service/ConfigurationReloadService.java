package org.metricshub.agent.service;

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

import java.util.function.Supplier;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;
import org.metricshub.agent.context.AgentContext;
import org.metricshub.agent.service.ReloadService.ReloadResult;

/**
 * Applies a configuration change to the running agent, whatever noticed it.
 * <p>
 * Every trigger &mdash; the configuration file watcher, a scheduled template re-evaluation, an
 * on-demand reload &mdash; follows the same three steps: build a throwaway context to compare
 * against, diff it through {@link ReloadService}, then act on the outcome. This class owns that
 * sequence so the triggers cannot drift apart, and takes the parts that legitimately differ as
 * parameters.
 * </p>
 * <p>
 * The comparison context is closed as soon as the diff is done, in every outcome:
 * {@link ReloadService} has by then already applied the resource-level changes and grafted what the
 * running context needed, so nothing else is served from it. A global restart is handed
 * {@link #restartContextSupplier}, which is invoked lazily by the lifecycle service and only if the
 * request is actually run, so a request that gets coalesced away builds nothing and leaks nothing.
 * </p>
 */
@Slf4j
@Builder(setterPrefix = "with")
public class ConfigurationReloadService {

	/**
	 * Requests a global restart of the agent.
	 */
	@FunctionalInterface
	public interface RestartRequester {
		/**
		 * Requests a restart served by the given supplier.
		 *
		 * @param reloadedContextSupplier builds the context the restart will install, invoked lazily
		 * @return {@code true} when the restart was requested, {@code false} when it could not be
		 *         (typically because the lifecycle service is not available yet)
		 */
		boolean requestRestart(Supplier<AgentContext> reloadedContextSupplier);
	}

	/** The context currently serving the agent, compared against the rebuilt one. */
	private final AgentContext runningAgentContext;

	/** Builds the throwaway context the configuration is diffed against. */
	private final Supplier<AgentContext> comparisonContextSupplier;

	/** Builds the context a global restart installs. Invoked lazily, only if the restart runs. */
	private final Supplier<AgentContext> restartContextSupplier;

	/** Requests the global restart when the diff calls for one. */
	private final RestartRequester restartRequester;

	/** Extra work to run once resource-level changes were applied in place. */
	@Builder.Default
	private final Runnable afterLocalChanges = () -> {};

	/**
	 * Rebuilds the configuration, diffs it against the running one and applies the outcome.
	 *
	 * @return what the diff concluded
	 */
	public ReloadResult reload() {
		AgentContext comparisonContext = null;
		final ReloadResult result;
		try {
			comparisonContext = comparisonContextSupplier.get();

			result = ReloadService.builder()
				.withRunningAgentContext(runningAgentContext)
				.withReloadedAgentContext(comparisonContext)
				.build()
				.reload();
		} catch (Exception e) {
			closeQuietly(comparisonContext);
			throw e;
		}

		// The comparison context has served its purpose whatever the outcome: resource-level changes
		// were already applied from it, and a restart builds its own.
		closeQuietly(comparisonContext);

		switch (result) {
			case GLOBAL_RESTART_REQUIRED -> {
				if (!restartRequester.requestRestart(restartContextSupplier)) {
					log.warn("A global restart is required but could not be requested; the configuration is left as it stands.");
				}
			}
			case LOCAL_ONLY -> afterLocalChanges.run();
			case NO_CHANGE -> log.debug("The configuration did not change; nothing was applied.");
			default -> log.warn("Unknown reload result: {}", result);
		}

		return result;
	}

	/**
	 * Closes a context without letting a failure to release it mask the reload outcome.
	 *
	 * @param context the context to close, possibly {@code null}
	 */
	private static void closeQuietly(final AgentContext context) {
		if (context == null) {
			return;
		}
		try {
			context.close();
		} catch (Exception e) {
			log.debug("Failed to close the comparison AgentContext.", e);
		}
	}
}
