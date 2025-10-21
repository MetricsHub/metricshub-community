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
	String NULL_HOSTNAME_ERROR = "Hostname must not be null";

	default <T> MultiHostToolResponse<T> buildNullHostnameResponse(final Supplier<T> responseSupplier) {
		return MultiHostToolResponse.<T>builder().response(responseSupplier.get()).build();
	}

	default int resolvePoolSize(final Integer requestedPoolSize, final int defaultPoolSize) {
		return requestedPoolSize != null && requestedPoolSize > 0 ? requestedPoolSize : defaultPoolSize;
	}

	default <T> List<MultiHostToolResponse<T>> executeForHosts(
		final List<String> hostnames,
		final Supplier<MultiHostToolResponse<T>> nullHostnameSupplier,
		final Function<String, MultiHostToolResponse<T>> perHostTask,
		final int poolSize
	) {
		Objects.requireNonNull(nullHostnameSupplier, "nullHostnameSupplier must not be null");
		Objects.requireNonNull(perHostTask, "perHostTask must not be null");

		return MultiHostToolExecutor.executeForHosts(hostnames, nullHostnameSupplier, perHostTask, poolSize);
	}
}
