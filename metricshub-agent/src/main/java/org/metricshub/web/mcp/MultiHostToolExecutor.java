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
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
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
	 * @throws CancellationException if the calling thread is interrupted, in which case the per-host
	 *                               tasks are cancelled and their threads interrupted. The call
	 *                               returns only once the pool it created has terminated, so a host
	 *                               operation that ignores its interruption holds its caller rather
	 *                               than outliving it
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
			for (final String hostname : hostnames) {
				// Nothing waits interruptibly on this path, so a pending interruption is what there
				// is to go on. An operation that consumed one and did not restore it leaves nothing
				// for anyone to observe, here or anywhere else
				if (Thread.currentThread().isInterrupted()) {
					throw new CancellationException("Multi-host execution was cancelled");
				}
				aggregatedResponse.getHosts().add(hostname == null ? nullHostnameSupplier.get() : perHostTask.apply(hostname));
			}
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

			try {
				// get() instead of join() so that cancelling the calling task interrupts this wait
				CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).get();
			} catch (ExecutionException executionException) {
				// Per-host failures are surfaced by the join() calls below, as CompletionException
			} catch (InterruptedException interruptedException) {
				futures.forEach(future -> future.cancel(true));
				// What actually interrupts the running host operations
				executor.shutdownNow();
				awaitTermination(executor);
				Thread.currentThread().interrupt();
				throw new CancellationException("Multi-host execution was cancelled");
			}

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

	/**
	 * Waits for an already shut down pool to terminate, ignoring further interruptions.
	 *
	 * <p>Host operations that observe their interruption are gone in milliseconds. One that does not
	 * — a blocking socket read — keeps its thread whatever the caller does, and holding the caller
	 * here is what keeps such threads under the caller's own ceiling, instead of freeing the caller
	 * to start another fan-out and leaving a live pool behind for every cancelled invocation.
	 *
	 * @param executor the pool to wait for
	 */
	private static void awaitTermination(final ExecutorService executor) {
		while (!executor.isTerminated()) {
			try {
				executor.awaitTermination(1, TimeUnit.HOURS);
			} catch (InterruptedException interruptedException) {
				// Already cancelling: another interruption cannot bring these threads back sooner
			}
		}
	}
}
