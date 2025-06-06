package org.metricshub.web;

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

import java.util.HashSet;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.metricshub.agent.context.AgentContext;
import org.slf4j.bridge.SLF4JBridgeHandler;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

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
	 */
	public static void startServer(final AgentContext agentContext) {
		try {
			// Install the SLF4J bridge for Java Util Logging (JUL)
			installJavaUtilLoggingBridge();

			final Set<String> args = new HashSet<>();

			// Fill the args set with the necessary web configuration parameters
			agentContext.getAgentConfig().getWebConfig().forEach((key, value) -> args.add("--" + key + "=" + value));

			context =
				new SpringApplicationBuilder()
					.sources(RestApplication.class)
					.initializers((ConfigurableApplicationContext applicationContext) ->
						applicationContext
							.getBeanFactory()
							.registerSingleton("agentContextHolder", new AgentContextHolder(agentContext))
					)
					.run(args.toArray(String[]::new));

			log.info("Started Spring application with context class: {}", context.getClass().getName());
		} catch (Exception e) {
			log.error("Failed to start REST API server", e);
		}
	}

	/**
	 * Installs the SLF4J bridge for Java Util Logging (JUL).
	 */
	public static void installJavaUtilLoggingBridge() {
		// Remove existing handlers
		SLF4JBridgeHandler.removeHandlersForRootLogger();

		// Install SLF4J bridge
		SLF4JBridgeHandler.install();
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
	 * Updates the AgentContext in the application context.
	 * This method retrieves the AgentContextHolder bean from the application context
	 * and updates its AgentContext with the provided one.
	 *
	 * @param agentContext the AgentContext to update
	 */
	public static void updateAgentContext(final AgentContext agentContext) {
		if (context != null) {
			final AgentContextHolder holder = context.getBean(AgentContextHolder.class);
			holder.setAgentContext(agentContext);
			log.info("Updated AgentContext via AgentContextHolder.");
		} else {
			log.warn("Application context is not initialized. Cannot update AgentContext.");
		}
	}
}
