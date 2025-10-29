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
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.metricshub.engine.common.helpers.JsonHelper;
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
	 * Retrieves a deep copy of all configurations associated with the given hostname from the
	 * provided agent context, without applying any protocol-specific validation.
	 *
	 * @param hostname     the target host
	 * @param contextHolder the agent context holder containing telemetry managers
	 * @return a set of all {@link IConfiguration} instances associated with the
	 *         host
	 */
	public static Set<IConfiguration> resolveAllHostConfigurationCopiesFromContext(
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
			.flatMap(MCPConfigHelper::getConfigStream)
			// create a deep copy of the configuration
			.map(IConfiguration::copy)
			// Collect all configurations into a Set
			.collect(Collectors.toSet());
	}

	/**
	 * Streams all {@link IConfiguration} instances from the given {@link TelemetryManager}'s
	 *
	 * @param telemetryManager the telemetry manager to extract configurations from
	 * @return a stream of {@link IConfiguration} instances; empty if none are found
	 */
	private static Stream<IConfiguration> getConfigStream(final TelemetryManager telemetryManager) {
		final var hostConfiguration = telemetryManager.getHostConfiguration();

		// Skip if no host configuration is available
		if (hostConfiguration == null) {
			return Stream.empty();
		}

		return Optional
			.ofNullable(hostConfiguration.getConfigurations())
			.map(Map::values)
			.map(Collection::stream)
			.orElseGet(Stream::empty);
	}

	/**
	 * Finds a and return a list of {@link TelemetryManager}(s) by hostname within the provided agent context.
	 * @param hostname      the hostname to search for
	 * @param contextHolder the agent context holder containing telemetry managers
	 * @return a {@link List} containing the found {@link TelemetryManager}(s)
	 */
	public static List<TelemetryManager> findTelemetryManagerByHostname(
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
			.toList();
	}

	/**
	 * Creates a new {@link TelemetryManager} instance by copying the connector store and host configuration
	 * from an existing telemetry manager.
	 * @param telemetryManager the existing telemetry manager to copy from
	 * @param connectorId      the identifier of the connector to be used in the new instance
	 * @return a new {@link TelemetryManager} instance with the same connector store and host configuration
	 */
	public static TelemetryManager newFrom(final TelemetryManager telemetryManager, final String connectorId) {
		var hostConfiguration = telemetryManager.getHostConfiguration();

		// if the connectorId is not null, create a new HostConfiguration with the specified connector
		if (connectorId != null && !connectorId.isBlank()) {
			hostConfiguration = hostConfiguration.copy();
			hostConfiguration.setConnectors(new HashSet<>(Set.of("+" + connectorId)));
		}

		return TelemetryManager
			.builder()
			.connectorStore(telemetryManager.getConnectorStore())
			.hostConfiguration(hostConfiguration)
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
		final var mapper = JsonHelper.buildObjectMapper();
		final JsonNode configurationNode = mapper.valueToTree(configuration);

		// Create a new configuration node with only the required shared fields
		final var newConfigurationNode = JsonNodeFactory.instance.objectNode();
		newConfigurationNode.set("username", configurationNode.get("username"));
		newConfigurationNode.set("password", configurationNode.get("password"));

		// Build the new configuration using the extracted credentials
		try {
			return extension.buildConfiguration(protocol, newConfigurationNode, null);
		} catch (Exception e) {
			return null;
		}
	}
}
