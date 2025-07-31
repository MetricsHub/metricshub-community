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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.metricshub.engine.common.exception.InvalidConfigurationException;
import org.metricshub.engine.configuration.HostConfiguration;
import org.metricshub.engine.configuration.IConfiguration;
import org.metricshub.engine.extension.IProtocolExtension;
import org.metricshub.engine.telemetry.TelemetryManager;
import org.metricshub.web.AgentContextHolder;

/**
 * Utility class for resolving configuration-related data from the {@link AgentContextHolder}.
 * <p>
 * Provides helper methods to access and extract telemetry and host configuration details
 * associated with the agent's runtime context.
 * </p>
 *
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class MCPConfigHelper {

	/**
	 * Retrieves all configurations associated with the given hostname from the
	 * provided agent context, without applying any protocol-specific validation.
	 *
	 * @param hostname     the target host
	 * @param contextHolder the agent context holder containing telemetry managers
	 * @return a set of all {@link IConfiguration} instances associated with the
	 *         host
	 */
	public static Set<IConfiguration> resolveAllHostConfigurationsFromContext(
		final String hostname,
		final AgentContextHolder contextHolder
	) {
		// Retrieve all telemetry managers grouped by agent from the context
		final Map<String, Map<String, TelemetryManager>> telemetryManagersByAgent = contextHolder
			.getAgentContext()
			.getTelemetryManagers();

		return telemetryManagersByAgent
			.values()
			.stream()
			// Flatten inner maps of TelemetryManagers into a single stream
			.flatMap((Map<String, TelemetryManager> innerMap) -> innerMap.values().stream())
			// Match telemetry managers by hostname
			.filter((TelemetryManager telemetryManager) -> hostname.equalsIgnoreCase(telemetryManager.getHostname()))
			// Extract IConfiguration instances from matching TelemetryManagers
			.flatMap((TelemetryManager telemetryManager) -> {
				final HostConfiguration hostConfiguration = telemetryManager.getHostConfiguration();

				// Skip if no host configuration is available
				if (hostConfiguration == null) {
					return Stream.empty();
				}

				final Map<Class<? extends IConfiguration>, ? extends IConfiguration> configMap =
					hostConfiguration.getConfigurations();

				// Skip if no configuration map is present
				if (configMap == null) {
					return Stream.empty();
				}

				// Stream the IConfiguration instances
				return configMap.values().stream();
			})
			// Collect all configurations into a Set
			.collect(Collectors.toSet());
	}

	/**
	 * Finds a {@link TelemetryManager} by its hostname within the provided agent context.
	 * @param hostname      the hostname to search for
	 * @param contextHolder the agent context holder containing telemetry managers
	 * @return an {@link Optional} containing the found {@link TelemetryManager}, or empty if not found
	 */
	public static Optional<TelemetryManager> findTelemetryManagerByHostname(
		final String hostname,
		final AgentContextHolder contextHolder
	) {
		return contextHolder
			.getAgentContext()
			.getTelemetryManagers()
			.values()
			.stream()
			.flatMap((Map<String, TelemetryManager> innerMap) -> innerMap.values().stream())
			.filter((TelemetryManager telemetryManager) -> hostname.equalsIgnoreCase(telemetryManager.getHostname()))
			.findFirst();
	}

	/**
	 * Creates a new {@link TelemetryManager} instance by copying the connector store and host configuration
	 * from an existing telemetry manager.
	 * @param telemetryManager the existing telemetry manager to copy from
	 * @return a new {@link TelemetryManager} instance with the same connector store and host configuration
	 */
	public static TelemetryManager newFrom(final TelemetryManager telemetryManager) {
		return TelemetryManager
			.builder()
			.connectorStore(telemetryManager.getConnectorStore())
			.hostConfiguration(telemetryManager.getHostConfiguration())
			.build();
	}

	/**
	 * Builds a new configuration for the specified protocol by extracting shared values
	 * (such as username, password, and timeout) from an existing configuration of a different type.
	 *
	 * This is useful when switching protocols (e.g., HTTP → SSH) but wanting to reuse common credentials.
	 *
	 * @param configuration the source configuration to extract credentials and timeout from
	 * @param protocol      the target protocol for the new configuration
	 * @param extension     the extension responsible for creating the new configuration
	 * @return a new {@link IConfiguration} instance populated with extracted values
	 */
	public static IConfiguration convertConfigurationForProtocol(
		final IConfiguration configuration,
		final String protocol,
		final IProtocolExtension extension
	) {
		// Convert the source configuration to a JsonNode tree
		final ObjectMapper mapper = new ObjectMapper();
		final JsonNode configurationNode = mapper.valueToTree(configuration);

		// Create a new configuration node with only the required shared fields
		final ObjectNode newConfigurationNode = JsonNodeFactory.instance.objectNode();
		newConfigurationNode.set("username", configurationNode.get("username"));
		newConfigurationNode.set("password", configurationNode.get("password"));

		// Build the new configuration using the extracted credentials
		try {
			return extension.buildConfiguration(protocol, newConfigurationNode, null);
		} catch (InvalidConfigurationException e) {
			return null;
		}
	}
}
