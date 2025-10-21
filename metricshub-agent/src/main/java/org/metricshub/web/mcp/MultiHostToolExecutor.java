package org.metricshub.web.mcp;

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

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Utility methods for executing MCP tool actions concurrently against multiple hosts.
 */
public final class MultiHostToolExecutor {

	/**
	 * Utility class constructor hidden to prevent instantiation.
	 */
	private MultiHostToolExecutor() {
		// utility class
	}

	/**
	 * Executes a per-host task concurrently for each hostname provided.
	 *
	 * @param hostnames             the hostnames to target
	 * @param nullHostnameSupplier  supplier used when the hostname entry is {@code null}
	 * @param perHostTask           function executed for each non-null hostname
	 * @param poolSize              maximum size of the thread pool used for
	 *                              concurrent execution
	 * @param <T>                   type of the per-host response
	 * @return the aggregated list of responses, one per requested hostname
	 */
	public static <T> List<MultiHostToolResponse<T>> executeForHosts(
		final List<String> hostnames,
		final Supplier<MultiHostToolResponse<T>> nullHostnameSupplier,
		final Function<String, MultiHostToolResponse<T>> perHostTask,
		final int poolSize
	) {
		if (hostnames == null || hostnames.isEmpty()) {
			return List.of();
		}

		Objects.requireNonNull(nullHostnameSupplier, "nullHostnameSupplier must not be null");
		Objects.requireNonNull(perHostTask, "perHostTask must not be null");

		final int resolvedPoolSize = poolSize > 0 ? poolSize : 1;

		if (resolvedPoolSize <= 1) {
			return hostnames
				.stream()
				.map(hostname -> hostname == null ? nullHostnameSupplier.get() : perHostTask.apply(hostname))
				.collect(Collectors.toList());
		}

		final ExecutorService executor = Executors.newFixedThreadPool(resolvedPoolSize);

		try {
			final List<CompletableFuture<MultiHostToolResponse<T>>> futures = hostnames
				.stream()
				.map(hostname ->
					CompletableFuture.supplyAsync(
						() -> hostname == null ? nullHostnameSupplier.get() : perHostTask.apply(hostname),
						executor
					)
				)
				.collect(Collectors.toList());

			CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).join();

			return futures.stream().map(CompletableFuture::join).collect(Collectors.toList());
		} finally {
			executor.shutdown();
			try {
				if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
					executor.shutdownNow();
				}
			} catch (InterruptedException interruptedException) {
				executor.shutdownNow();
				Thread.currentThread().interrupt();
			}
		}
	}
}
