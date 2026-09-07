package org.metricshub.web.mcp.transport;

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

import java.time.Duration;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Settings of the legacy MCP "HTTP with SSE" transport served by {@link LegacySseServerTransportProvider}.
 * <p>
 * The endpoints ({@code spring.ai.mcp.server.sse-endpoint}, {@code spring.ai.mcp.server.sse-message-endpoint}), the
 * advertised base URL and the keep-alive interval ({@code spring.ai.mcp.server.keep-alive-interval}) keep their Spring
 * AI keys. The keys below only cover the MetricsHub hardening of the transport.
 * </p>
 */
@ConfigurationProperties(prefix = McpSseProperties.PREFIX)
@Data
@AllArgsConstructor
@NoArgsConstructor
public class McpSseProperties {

	/**
	 * Prefix of the properties.
	 */
	public static final String PREFIX = "mcp.sse";

	/**
	 * Whether the legacy SSE transport is served. When the MCP protocol is {@code STREAMABLE}, the legacy transport is
	 * served alongside the Streamable HTTP transport; when the protocol is {@code SSE}, the hardened transport replaces
	 * the SDK one.
	 */
	private boolean enabled = true;

	/**
	 * Maximum time a request thread waits for the handling of one JSON-RPC message posted on the message endpoint. A
	 * message whose handling has not completed in time is answered with HTTP 504; the handling itself is not cancelled
	 * (a synchronous tool execution is never interrupted and a late result is still delivered on the SSE stream).
	 */
	private Duration messageTimeout = LegacySseServerTransportProvider.DEFAULT_MESSAGE_TIMEOUT;

	/**
	 * Time granted to a client to answer a keep-alive ping (an error answer counts as an answer). A session whose ping
	 * cannot be delivered or is not answered in time is closed, which releases its connection. Keep it generous enough
	 * to survive a suspended client. Pings are sent every {@code spring.ai.mcp.server.keep-alive-interval}.
	 */
	private Duration pingTimeout = LegacySseServerTransportProvider.DEFAULT_PING_TIMEOUT;

	/**
	 * Time granted to a client to complete the {@code initialize} / {@code notifications/initialized} handshake after
	 * opening its SSE stream. Sessions that are still not initialized after this delay are closed.
	 */
	private Duration initializationTimeout = LegacySseServerTransportProvider.DEFAULT_INITIALIZATION_TIMEOUT;
}
