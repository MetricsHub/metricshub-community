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

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.metricshub.engine.configuration.HostConfiguration;
import org.metricshub.engine.configuration.IConfiguration;
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
}
