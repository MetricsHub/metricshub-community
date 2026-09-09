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

import io.modelcontextprotocol.server.McpSyncServer;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerResponse;

/**
 * Holds the MCP server that serves the legacy "HTTP with SSE" transport alongside the Streamable HTTP transport
 * auto-configured by Spring AI, and shuts it down with the application context.
 * <p>
 * The SSE streams are closed on {@link ContextClosedEvent}, i.e. before the embedded web server stops, so that a
 * graceful shutdown does not wait for open event streams; {@link DisposableBean#destroy()} is kept as a fallback.
 * </p>
 * <p>
 * The transport provider is deliberately not exposed as a Spring bean: Spring AI injects a single
 * {@code McpServerTransportProviderBase} bean into the auto-configured server, so a second provider bean would make
 * that injection ambiguous.
 * </p>
 */
@Slf4j
public class LegacySseMcpServer implements ApplicationListener<ContextClosedEvent>, DisposableBean {

	private final LegacySseServerTransportProvider transportProvider;
	private final McpSyncServer server;
	private final AtomicBoolean closed = new AtomicBoolean();

	/**
	 * Creates the holder.
	 *
	 * @param transportProvider the legacy SSE transport provider
	 * @param server            the MCP server bound to the transport provider
	 */
	public LegacySseMcpServer(final LegacySseServerTransportProvider transportProvider, final McpSyncServer server) {
		this.transportProvider = transportProvider;
		this.server = server;
	}

	/**
	 * @return the router function serving the SSE and message endpoints
	 */
	public RouterFunction<ServerResponse> getRouterFunction() {
		return transportProvider.getRouterFunction();
	}

	/**
	 * @return the number of SSE sessions currently open
	 */
	public int activeSessionCount() {
		return transportProvider.activeSessionCount();
	}

	@Override
	public void onApplicationEvent(final ContextClosedEvent event) {
		close();
	}

	@Override
	public void destroy() {
		close();
	}

	private void close() {
		if (closed.compareAndSet(false, true)) {
			log.debug("Shutting down the legacy MCP SSE server");
			server.closeGracefully();
		}
	}
}
