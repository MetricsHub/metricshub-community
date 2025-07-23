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

import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.KeyStore.PasswordProtection;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import lombok.extern.slf4j.Slf4j;
import org.metricshub.agent.context.AgentContext;
import org.metricshub.agent.helper.AgentConstants;
import org.metricshub.agent.security.PasswordEncrypt;
import org.metricshub.engine.security.SecurityManager;
import org.metricshub.web.security.ApiKeyRegistry;
import org.metricshub.web.security.ApiKeyRegistry.ApiKey;
import org.slf4j.bridge.SLF4JBridgeHandler;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * Main application class for the MetricsHub Agent Server.
 */
@SpringBootApplication
@Slf4j
public class MetricsHubAgentServer {
	static {
		TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
	}

	private static ConfigurableApplicationContext context;

	/**
	 * Starts the server with the given AgentContext.
	 *
	 * @param agentContext the AgentContext to be used by the server
	 */
	public static void startServer(final AgentContext agentContext) {
		try {
			// Install the SLF4J bridge for Java Util Logging (JUL)
			installJavaUtilLoggingBridge();

			final Set<String> args = new HashSet<>();

			// Fill the args set with the necessary web configuration parameters
			final Map<String, String> webConfig = agentContext.getAgentConfig().getWebConfig();
			webConfig.forEach((key, value) -> args.add("--" + key + "=" + value));

			// Get the application port number
			final var applicationPort = webConfig.getOrDefault("server.port", "8080");

			// Build the Spring application context with the provided AgentContextHolder
			// and the application arguments then run it
			context =
				new SpringApplicationBuilder()
					.sources(MetricsHubAgentServer.class)
					.initializers((ConfigurableApplicationContext applicationContext) -> {
						applicationContext
							.getBeanFactory()
							.registerSingleton("agentContextHolder", new AgentContextHolder(agentContext));
						applicationContext
							.getBeanFactory()
							.registerSingleton("apiKeyRegistry", new ApiKeyRegistry(resolveApiKeys()));
					})
					.run(args.toArray(String[]::new));

			log.info("Started Spring application - Tomcat started on port: {}", applicationPort);
		} catch (Exception e) {
			log.error("Failed to start REST API server", e);
		}
	}

	/**
	 * Resolves API keys from the KeyStore.
	 *
	 * @return a map of API key names to their corresponding IDs
	 */
	private static Map<String, ApiKey> resolveApiKeys() {
		final Map<String, ApiKey> apiKeys = new HashMap<>();
		try {
			final var keyStoreFile = PasswordEncrypt.getKeyStoreFile(true);
			final var ks = SecurityManager.loadKeyStore(keyStoreFile);

			final var aliases = ks.aliases();
			while (aliases.hasMoreElements()) {
				final var alias = aliases.nextElement();
				if (!alias.startsWith(AgentConstants.API_KEY_PREFIX)) {
					continue;
				}

				final var entry = ks.getEntry(alias, new PasswordProtection(new char[] { 's', 'e', 'c', 'r', 'e', 't' }));
				if (entry instanceof KeyStore.SecretKeyEntry secreKeyEntry) {
					final var secretKey = secreKeyEntry.getSecretKey();
					final var apiKeyId = new String(secretKey.getEncoded(), StandardCharsets.UTF_8);

					final var parts = apiKeyId.split("__");
					final var key = parts[0];
					LocalDateTime expirationDateTime = null;
					if (parts.length > 1) {
						expirationDateTime = LocalDateTime.parse(parts[1]);
					}
					final var apiKeyAlias = alias.substring(AgentConstants.API_KEY_PREFIX.length());
					apiKeys.put(apiKeyAlias, new ApiKey(apiKeyAlias, key, expirationDateTime));
				}
			}
		} catch (Exception e) {
			log.error("Failed to resolve API keys from KeyStore");
			log.debug("Exception details: ", e);
		}

		return apiKeys;
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
			// Close the application context to stop the server
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
			// Retrieve the AgentContextHolder bean from the spring application context
			final AgentContextHolder holder = context.getBean(AgentContextHolder.class);
			holder.setAgentContext(agentContext);
			log.info("Updated AgentContext through the AgentContextHolder.");
		} else {
			log.warn("Application context is not initialized. Cannot update AgentContext.");
		}
	}
}
