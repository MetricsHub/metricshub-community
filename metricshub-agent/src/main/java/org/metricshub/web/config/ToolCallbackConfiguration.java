package org.metricshub.web.config;

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

import org.metricshub.web.mcp.PingToolService;
import org.metricshub.web.mcp.ProtocolCheckService;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration class providing {@link ToolCallback} instances for tools defined in different sources.
 */
@Configuration
public class ToolCallbackConfiguration {

	/**
	 * Provides a ToolCallbackProvider for the PingToolService.
	 *
	 * @param pingToolService      the PingToolService to be used
	 * @param protocolCheckService the protocolCheckService to be used
	 * @return a ToolCallbackProvider for the PingToolService
	 */
	@Bean
	public ToolCallbackProvider metricshubTools(
		final PingToolService pingToolService,
		final ProtocolCheckService protocolCheckService
	) {
		return MethodToolCallbackProvider.builder().toolObjects(pingToolService, protocolCheckService).build();
	}
}
