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
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Marker interface for MetricsHub tools.
 * Each MCP Tool must implement this interface to be automatically registered.
 */
public interface IMCPToolService {
	/**
	 * Standard error message returned when a hostname entry is missing from the
	 * request payload.
	 */
	String NULL_HOSTNAME_ERROR = "Hostname must not be null";

	/**
	 * Builds a {@link HostToolResponse} describing the error generated when a hostname
	 * parameter is {@code null}.
	 *
	 * @param responseSupplier supplier that produces the error payload to wrap
	 * @param <T>              type of the tool response payload
	 * @return a host-level response containing the supplied error payload
	 */
	default <T> HostToolResponse<T> buildNullHostnameResponse(final Supplier<T> responseSupplier) {
		return HostToolResponse.<T>builder().response(responseSupplier.get()).build();
	}

	/**
	 * Resolves the executor pool size for a tool invocation, falling back to the
	 * provided default when the requested value is {@code null} or not positive.
	 *
	 * @param requestedPoolSize pool size requested by the caller
	 * @param defaultPoolSize   default pool size defined by the tool
	 * @return the validated pool size to use for execution
	 */
	default int resolvePoolSize(final Integer requestedPoolSize, final int defaultPoolSize) {
		return requestedPoolSize != null && requestedPoolSize > 0 ? requestedPoolSize : defaultPoolSize;
	}

	/**
	 * Executes a per-host task for each hostname requested by the caller, applying
	 * concurrency using the {@link MultiHostToolExecutor} utility.
	 *
	 * @param hostnames            hostnames supplied by the MCP request
	 * @param nullHostnameSupplier supplier used when an entry in {@code hostnames}
	 *                             is {@code null}
	 * @param perHostTask          function producing the tool response for a given
	 *                             hostname
	 * @param poolSize             maximum size of the thread pool used for
	 *                             concurrent execution
	 * @param <T>                  type of the tool response payload
	 * @return an aggregated response wrapper matching the requested hostnames
	 */
	default <T> MultiHostToolResponse<T> executeForHosts(
		final List<String> hostnames,
		final Supplier<HostToolResponse<T>> nullHostnameSupplier,
		final Function<String, HostToolResponse<T>> perHostTask,
		final int poolSize
	) {
		Objects.requireNonNull(nullHostnameSupplier, "nullHostnameSupplier must not be null");
		Objects.requireNonNull(perHostTask, "perHostTask must not be null");

		return MultiHostToolExecutor.executeForHosts(hostnames, nullHostnameSupplier, perHostTask, poolSize);
	}
}
