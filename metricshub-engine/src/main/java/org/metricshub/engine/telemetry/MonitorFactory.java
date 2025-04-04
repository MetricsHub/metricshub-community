package org.metricshub.engine.telemetry;

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

import static org.metricshub.engine.common.helpers.MetricsHubConstants.EMPTY;
import static org.metricshub.engine.common.helpers.MetricsHubConstants.HOST_NAME;
import static org.metricshub.engine.common.helpers.MetricsHubConstants.HOST_TYPE_TO_OTEL_HOST_TYPE;
import static org.metricshub.engine.common.helpers.MetricsHubConstants.HOST_TYPE_TO_OTEL_OS_TYPE;
import static org.metricshub.engine.common.helpers.MetricsHubConstants.UNDERSCORE;

import java.net.InetAddress;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Builder.Default;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.metricshub.engine.alert.AlertRule;
import org.metricshub.engine.common.helpers.KnownMonitorType;
import org.metricshub.engine.common.helpers.MetricsHubConstants;
import org.metricshub.engine.common.helpers.NetworkHelper;
import org.metricshub.engine.common.helpers.StringHelper;
import org.metricshub.engine.configuration.HostConfiguration;
import org.metricshub.engine.connector.model.common.DeviceKind;
import org.metricshub.engine.telemetry.metric.AbstractMetric;

/**
 * Factory class for creating and updating {@link Monitor} instances.
 */
@Slf4j
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class MonitorFactory {

	private Map<String, AbstractMetric> metrics;

	private Map<String, String> attributes;

	private Map<String, List<AlertRule>> alertRules;

	private TelemetryManager telemetryManager;

	private String monitorType;

	private String connectorId;

	@NonNull
	private Long discoveryTime;

	// Monitor job keys
	@Default
	private Set<String> keys = new HashSet<>(MetricsHubConstants.DEFAULT_KEYS);

	/**
	 * This method creates or updates the monitor
	 *
	 * @param id identifier of the monitor
	 * @return created or updated {@link Monitor} instance
	 */
	public Monitor createOrUpdateMonitor(final String id) {
		return createOrUpdateMonitor(attributes, monitorType, id);
	}

	/**
	 * This method creates or updates the monitor
	 *
	 * @return created or updated {@link Monitor} instance
	 */
	public Monitor createOrUpdateMonitor() {
		// Retrieve the keys values. The keys are located under the corresponding
		// monitor section in the connector file
		final String keysString = keys
			.stream()
			.sorted()
			.map(key -> Optional.ofNullable(attributes.get(key)).orElse(""))
			.collect(Collectors.joining("_"));

		// Build the monitor unique identifier
		final String id = buildMonitorId(connectorId, monitorType, keysString);
		return createOrUpdateMonitor(attributes, monitorType, id);
	}

	/**
	 * This method creates or updates the monitor
	 *
	 * @param attributes        Monitor's attributes
	 * @param monitorType       Type of the monitor
	 * @param id                Unique identifier of the monitor
	 * @return Monitor instance
	 */
	Monitor createOrUpdateMonitor(final Map<String, String> attributes, final String monitorType, final String id) {
		return createOrUpdateMonitor(attributes, monitorType, id, discoveryTime);
	}

	/**
	 * This method creates or updates the monitor
	 *
	 * @param attributes    monitor attributes
	 * @param monitorType   the type of the monitor
	 * @param id            unique identifier of the monitor
	 * @param discoveryTime The time of discovery
	 * @return Monitor instance
	 */
	Monitor createOrUpdateMonitor(
		final Map<String, String> attributes,
		final String monitorType,
		final String id,
		final long discoveryTime
	) {
		final Monitor foundMonitor = telemetryManager.findMonitorByTypeAndId(monitorType, id);

		if (foundMonitor != null) {
			foundMonitor.setAttributes(attributes);
			foundMonitor.setType(monitorType);
			foundMonitor.setDiscoveryTime(discoveryTime);

			// Set the connector identifier attribute
			setConnectorIdAttribute(foundMonitor);

			return foundMonitor;
		} else {
			final Monitor newMonitor = Monitor
				.builder()
				.attributes(attributes)
				.type(monitorType)
				.id(id)
				.identifyingAttributeKeys(keys)
				.discoveryTime(discoveryTime)
				.build();

			// Set the connector identifier attribute
			setConnectorIdAttribute(newMonitor);

			telemetryManager.addNewMonitor(newMonitor, monitorType, id);

			return newMonitor;
		}
	}

	/**
	 * Assigns the connector ID to the specified monitor as an attribute if the connector ID is not null.
	 *
	 * @param monitor The {@link Monitor} instance to which the connector ID attribute should be added.
	 */
	private void setConnectorIdAttribute(final Monitor monitor) {
		if (connectorId != null) {
			monitor.addAttribute(MetricsHubConstants.MONITOR_ATTRIBUTE_CONNECTOR_ID, connectorId);
		}
	}

	/**
	 * Creates the endpoint Host monitor
	 *
	 * @return Monitor instance
	 */
	public Monitor createEndpointHostMonitor() {
		// Get the host configuration
		final HostConfiguration hostConfiguration = telemetryManager.getHostConfiguration();

		final String hostname = hostConfiguration.getHostname();

		final DeviceKind deviceKind = hostConfiguration.getHostType();

		// The host resource os.type
		final String osType = HOST_TYPE_TO_OTEL_OS_TYPE.getOrDefault(deviceKind, deviceKind.getDisplayName().toLowerCase());

		// The host resource host.type
		final String hostType = HOST_TYPE_TO_OTEL_HOST_TYPE.getOrDefault(
			deviceKind,
			deviceKind.getDisplayName().toLowerCase()
		);

		final Map<String, String> monitorAttributes = Map.of(
			HOST_NAME,
			hostConfiguration.isResolveHostnameToFqdn() ? NetworkHelper.getFqdn(hostname) : hostname,
			"host.type",
			hostType,
			"os.type",
			osType,
			"agent.host.name",
			StringHelper.getValue(() -> InetAddress.getLocalHost().getCanonicalHostName(), "unknown")
		);

		// Create the monitor using createOrUpdateMonitor
		final Monitor monitor = createOrUpdateMonitor(
			monitorAttributes,
			KnownMonitorType.HOST.getKey(),
			"endpoint_host_%s".formatted(telemetryManager.getHostname())
		);

		// Flag the host as endpoint
		monitor.setAsEndpoint();

		log.debug("Hostname {} - Created endpoint host: {} ", hostname, hostname);

		return monitor;
	}

	/**
	 * Build the monitor unique identifier [connectorid]_[monitorType]_[id]
	 * @param connectorId    The connector compiled file name (identifier)
	 * @param monitorType    The type of the monitor.
	 * @param id             The id of the monitor we wish to build its identifier
	 * @return {@link String} value containing the key of the monitor
	 */
	public static String buildMonitorId(
		@NonNull final String connectorId,
		@NonNull final String monitorType,
		@NonNull final String id
	) {
		return new StringBuilder()
			.append(connectorId)
			.append(UNDERSCORE)
			.append(monitorType)
			.append(UNDERSCORE)
			.append(id.replaceAll("\\s*", EMPTY))
			.toString();
	}
}
