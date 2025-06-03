package org.metricshub.rest;

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

import lombok.extern.slf4j.Slf4j;
import org.metricshub.agent.context.AgentContext;
import org.metricshub.rest.mcp.PingToolService;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;

/**
 * Main application class for the MetricsHub REST API.
 */
@SpringBootApplication
@Slf4j
public class RestApplication {

	private static ConfigurableApplicationContext context;

	/**
	 * Starts the REST API server with the given AgentContext and port number.
	 *
	 * @param agentContext the AgentContext to be used by the server
	 * @param portNumber   the port number on which the server will run
	 */
	public static void startServer(final AgentContext agentContext, final int portNumber) {
		try {
			context =
				new SpringApplicationBuilder()
					.sources(RestApplication.class)
					.initializers(applicationContext -> {
						applicationContext.getBeanFactory().registerSingleton("agentContext", agentContext);
					})
					.run(
						"--server.port=8081",
						"--spring.ai.mcp.server.enabled=true",
						"--spring.ai.mcp.server.stdio=false",
						"--spring.ai.mcp.server.name=metricshub-sse-mcp-server",
						"--spring.ai.mcp.server.version=1.0.0",
						"--spring.ai.mcp.server.resource-change-notification=true",
						"--spring.ai.mcp.server.tool-change-notification=true",
						"--spring.ai.mcp.server.prompt-change-notification=true",
						"--spring.ai.mcp.server.sse-endpoint=/sse",
						"--spring.ai.mcp.server.sse-message-endpoint=/mcp/message",
						"--spring.ai.mcp.server.type=async",
						"--spring.ai.mcp.server.capabilities.completion=true",
						"--spring.ai.mcp.server.capabilities.prompt=true",
						"--spring.ai.mcp.server.capabilities.resource=true",
						"--spring.ai.mcp.server.capabilities.tool=true"
					);

			log.info("Application Context Class: {}", context.getClass().getName());
		} catch (Exception e) {
			log.error("Failed to start REST API server", e);
		}
	}

	/**
	 * Stops the server if it is running.
	 */
	public void stopServer() {
		if (context != null) {
			context.close();
		}
	}

	/**
	 * Provides a ToolCallbackProvider for the PingToolService.
	 * @param pingToolService the PingToolService to be used
	 * @return a ToolCallbackProvider for the PingToolService
	 */
	@Bean
	public ToolCallbackProvider metricshubTools(PingToolService pingToolService) {
		return MethodToolCallbackProvider.builder().toolObjects(pingToolService).build();
	}
}
