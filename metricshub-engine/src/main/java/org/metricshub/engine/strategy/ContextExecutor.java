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

/**
 * Executor for executing a strategy in a separate thread with timeout handling.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ContextExecutor {

	private IStrategy strategy;

	/**
	 * This method prepares the strategy, runs the run method it in a separate thread.
	 * Upon thread completion, it calls the post method of the IStrategy instance and ensures proper termination of the task
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
			executorService.shutdownNow();
		}
	}
}
