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
import lombok.NoArgsConstructor;
import org.metricshub.engine.common.helpers.NumberHelper;

/**
 * Utility methods for executing MCP tool actions concurrently against multiple hosts.
 */
@NoArgsConstructor(access = lombok.AccessLevel.PRIVATE)
public final class MultiHostToolExecutor {

	/**
	 * Executes a per-host task concurrently for each hostname provided.
	 *
	 * @param hostnames             the hostnames to target
	 * @param nullHostnameSupplier  supplier used when the hostname entry is {@code null}
	 * @param perHostTask           function executed for each non-null hostname
	 * @param poolSize              maximum size of the thread pool used for
	 *                              concurrent execution
	 * @param <T>                   type of the per-host response
	 * @return the aggregated response wrapper containing entries for each requested hostname
	 */
	public static <T> MultiHostToolResponse<T> executeForHosts(
		final List<String> hostnames,
		final Supplier<HostToolResponse<T>> nullHostnameSupplier,
		final Function<String, HostToolResponse<T>> perHostTask,
		final int poolSize
	) {
		final MultiHostToolResponse<T> aggregatedResponse = new MultiHostToolResponse<>();

		if (hostnames == null || hostnames.isEmpty()) {
			return aggregatedResponse;
		}

		Objects.requireNonNull(nullHostnameSupplier, "nullHostnameSupplier must not be null");
		Objects.requireNonNull(perHostTask, "perHostTask must not be null");

		final var resolvedPoolSize = NumberHelper.getPositiveOrDefault(poolSize, 1).intValue();

		if (resolvedPoolSize <= 1) {
			aggregatedResponse
				.getHosts()
				.addAll(
					hostnames
						.stream()
						.map(hostname -> hostname == null ? nullHostnameSupplier.get() : perHostTask.apply(hostname))
						.toList()
				);
			return aggregatedResponse;
		}

		final ExecutorService executor = Executors.newFixedThreadPool(resolvedPoolSize);

		try {
			final List<CompletableFuture<HostToolResponse<T>>> futures = hostnames
				.stream()
				.map(hostname ->
					CompletableFuture.supplyAsync(
						() -> hostname == null ? nullHostnameSupplier.get() : perHostTask.apply(hostname),
						executor
					)
				)
				.toList();

			CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).join();

			aggregatedResponse.getHosts().addAll(futures.stream().map(CompletableFuture::join).collect(Collectors.toList()));
			return aggregatedResponse;
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
