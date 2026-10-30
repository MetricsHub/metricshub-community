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

import java.util.Map;
import org.metricshub.web.AgentContextHolder;
import org.metricshub.web.mcp.IMCPToolService;
import org.metricshub.web.mcp.tool.MetricsHubToolCallbackProvider;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration class providing {@link ToolCallback} instances for tools defined in different sources.
 */
@Configuration
public class ToolCallbackConfiguration {

	/**
	 * Registers the {@link ToolCallbackProvider} exposing every tool: the beans implementing
	 * {@link IMCPToolService}, then the tools the loaded protocol extensions and connectors declare, which follow
	 * the agent context across configuration reloads.
	 *
	 * @param context            the Spring {@link ApplicationContext} to retrieve beans from
	 * @param agentContextHolder the running agent context
	 * @return the provider of every tool the agent exposes, filtered for its own MCP server and chat, whole for Central
	 */
	@Bean
	public MetricsHubToolCallbackProvider metricshubTools(
		final ApplicationContext context,
		final AgentContextHolder agentContextHolder
	) {
		// Automatically get all beans of type IMCPToolService
		final Map<String, IMCPToolService> toolMap = context.getBeansOfType(IMCPToolService.class);
		final var tools = toolMap.values();
		final ToolCallback[] staticCallbacks = MethodToolCallbackProvider.builder()
			.toolObjects(tools.toArray(new Object[tools.size()]))
			.build()
			.getToolCallbacks();
		return new MetricsHubToolCallbackProvider(staticCallbacks, agentContextHolder);
	}
}
