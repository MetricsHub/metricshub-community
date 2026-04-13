package org.metricshub.engine.strategy;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * MetricsHub Engine
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

import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Executor for executing a strategy in a separate thread with timeout handling.
 * Uses a two-phase shutdown to ensure threads are properly cleaned up.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Slf4j
public class ContextExecutor {

	/**
	 * Grace period in seconds to wait for tasks to complete after shutdownNow.
	 */
	private static final long SHUTDOWN_GRACE_PERIOD_SECONDS = 10L;

	private IStrategy strategy;

	/**
	 * This method prepares the strategy, runs the run method it in a separate thread.
	 * Upon thread completion, it calls the post method of the IStrategy instance and ensures proper termination of the task.
	 * Uses a two-phase shutdown: first attempts graceful shutdown, then forces termination if needed.
	 *
	 * @throws InterruptedException if the thread is interrupted while waiting
	 * @throws TimeoutException     if the wait timed out
	 * @throws ExecutionException   if the computation threw an exception
	 */
	public void execute() throws InterruptedException, ExecutionException, TimeoutException {
		final ExecutorService executorService = Executors.newSingleThreadExecutor();
		try {
			final Future<?> handler = executorService.submit(strategy);

			handler.get(strategy.getStrategyTimeout(), TimeUnit.SECONDS);
		} finally {
			// Two-phase shutdown: first shutdownNow, then await termination
			final List<Runnable> pendingTasks = executorService.shutdownNow();
			if (!pendingTasks.isEmpty()) {
				log.warn("ContextExecutor - {} pending tasks were cancelled during shutdown.", pendingTasks.size());
			}
			if (!executorService.awaitTermination(SHUTDOWN_GRACE_PERIOD_SECONDS, TimeUnit.SECONDS)) {
				log.warn(
					"ContextExecutor - Executor did not terminate within {} seconds after shutdownNow. Some threads may still be running.",
					SHUTDOWN_GRACE_PERIOD_SECONDS
				);
			}
		}
	}
}
