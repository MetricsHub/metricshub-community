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

import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Aggregated response wrapper for MCP tools that operate on multiple hosts.
 *
 * @param <T> the type of the payload returned for each targeted host
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MultiHostToolResponse<T> {

	@Builder.Default
	private List<HostToolResponse<T>> hosts = new ArrayList<>();

	private String errorMessage;

	/**
	 * Builds a {@link MultiHostToolResponse} containing only the supplied error message.
	 *
	 * @param message error message to propagate to the caller
	 * @param <T>     expected response payload type
	 * @return a response wrapper populated with the provided message
	 */
	public static <T> MultiHostToolResponse<T> buildError(final String message) {
		return MultiHostToolResponse.<T>builder().errorMessage(message).build();
	}
}
