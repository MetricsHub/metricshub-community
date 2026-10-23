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
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.Assert;

/**
 * Spring AI-compatible endpoint settings retained for MetricsHub's legacy MCP SSE transport.
 * <p>
 * Spring AI 2.0 deprecated its {@code McpServerSseProperties} type for removal together with the legacy SSE
 * transport. MetricsHub still serves that transport for compatibility, so these properties preserve the existing
 * {@code spring.ai.mcp.server.*} keys without depending on the deprecated Spring AI class.
 * </p>
 */
@Getter
@ConfigurationProperties(prefix = LegacySseEndpointProperties.PREFIX)
public class LegacySseEndpointProperties {

	/** Prefix shared with the Spring AI MCP server settings. */
	public static final String PREFIX = "spring.ai.mcp.server";

	private String baseUrl = "";
	private String sseEndpoint = LegacySseServerTransportProvider.DEFAULT_SSE_ENDPOINT;
	private String sseMessageEndpoint = LegacySseServerTransportProvider.DEFAULT_MESSAGE_ENDPOINT;

	@Setter
	private Duration keepAliveInterval;

	public void setBaseUrl(final String baseUrl) {
		Assert.notNull(baseUrl, "Base URL must not be null");
		this.baseUrl = baseUrl;
	}

	public void setSseEndpoint(final String sseEndpoint) {
		Assert.hasText(sseEndpoint, "SSE endpoint must not be empty");
		this.sseEndpoint = sseEndpoint;
	}

	public void setSseMessageEndpoint(final String sseMessageEndpoint) {
		Assert.hasText(sseMessageEndpoint, "SSE message endpoint must not be empty");
		this.sseMessageEndpoint = sseMessageEndpoint;
	}
}
