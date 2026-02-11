package org.metricshub.engine.configuration;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * MetricsHub Engine
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

import static org.metricshub.engine.common.helpers.MetricsHubConstants.DEFAULT_JOB_TIMEOUT;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Builder.Default;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.metricshub.engine.alert.AlertInfo;
import org.metricshub.engine.connector.model.common.DeviceKind;
import org.metricshub.engine.connector.model.monitor.task.source.CommandLineSource;
import org.metricshub.engine.connector.model.monitor.task.source.Source;
import org.metricshub.engine.connector.model.monitor.task.source.WmiSource;
import org.metricshub.engine.extension.ExtensionManager;

/**
 * The HostConfiguration class represents the configuration for a host in the MetricsHub engine.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode
public class HostConfiguration {

	private String hostname;
	private String hostId;
	private DeviceKind hostType;
	private boolean resolveHostnameToFqdn;
	private Map<String, String> attributes;

	@Default
	private long strategyTimeout = DEFAULT_JOB_TIMEOUT;

	private Set<String> connectors;
	private boolean sequential;

	@Default
	private boolean enableSelfMonitoring = true;

	private Consumer<AlertInfo> alertTrigger;
	private long retryDelay;

	private Set<String> includedMonitors;
	private Set<String> excludedMonitors;

	// The map of connector variables. The key is the connector ID.
	private Map<String, ConnectorVariables> connectorVariables;

	@Default
	private Map<Class<? extends IConfiguration>, IConfiguration> configurations = new HashMap<>();

	private String configuredConnectorId;

	/**
	 * Determine the accepted sources that can be executed using the current engine configuration
	 *
	 * @param isLocalhost      Whether the host should be localhost or not.
	 * @param extensionManager Where all the extensions are managed.
	 * @return {@link Set} of accepted source types
	 */
	public Set<Class<? extends Source>> determineAcceptedSources(
		final boolean isLocalhost,
		final ExtensionManager extensionManager
	) {
		// Retrieve the configuration to Source mapping through the available extensions
		// @formatter:off
		final Map<Class<? extends IConfiguration>, Set<Class<? extends Source>>> configurationToSourceMappingFromExtensions =
			extensionManager.findConfigurationToSourceMapping();
		// @formatter:on

		final Map<Class<? extends IConfiguration>, Set<Class<? extends Source>>> configurationToSourceMapping =
			new HashMap<>();

		configurationToSourceMapping.putAll(configurationToSourceMappingFromExtensions);

		// protocolConfigurations and host cannot never be null
		final Set<Class<? extends IConfiguration>> protocolTypes = configurations.keySet();

		final Set<Class<? extends Source>> sources = configurationToSourceMapping
			.entrySet()
			.stream()
			.filter(protocolEntry -> protocolTypes.contains(protocolEntry.getKey()))
			.flatMap(v -> v.getValue().stream())
			.collect(Collectors.toSet());

		// Remove WMI for non-windows host
		if (!DeviceKind.WINDOWS.equals(hostType)) {
			sources.remove(WmiSource.class);
		}

		// Add OSCommand through Remote WMI Commands
		if (DeviceKind.WINDOWS.equals(hostType) && sources.contains(WmiSource.class) && !isLocalhost) {
			sources.add(CommandLineSource.class);
		}

		// Handle localhost protocols
		if (isLocalhost) {
			// OS Command always enabled locally
			sources.add(CommandLineSource.class);
		}

		return sources;
	}

	/**
	 * Creates a copy of the current HostConfiguration instance.
	 *
	 * @return a new HostConfiguration instance with the same properties as this one
	 */
	public HostConfiguration copy() {
		return HostConfiguration
			.builder()
			.hostname(hostname)
			.hostId(hostId)
			.hostType(hostType)
			.resolveHostnameToFqdn(resolveHostnameToFqdn)
			.strategyTimeout(strategyTimeout)
			.connectors(connectors)
			.sequential(sequential)
			.enableSelfMonitoring(enableSelfMonitoring)
			.alertTrigger(alertTrigger)
			.retryDelay(retryDelay)
			.includedMonitors(includedMonitors)
			.excludedMonitors(excludedMonitors)
			.connectorVariables(connectorVariables != null ? new HashMap<>(connectorVariables) : null)
			.configurations(configurations != null ? new HashMap<>(configurations) : null)
			.configuredConnectorId(configuredConnectorId)
			.attributes(attributes != null ? new HashMap<>(attributes) : null)
			.build();
	}
}
