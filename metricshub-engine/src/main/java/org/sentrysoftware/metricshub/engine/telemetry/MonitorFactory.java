package org.sentrysoftware.metricshub.engine.telemetry;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * MetricsHub Engine
 * ჻჻჻჻჻჻
 * Copyright 2023 - 2024 Sentry Software
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

import static org.sentrysoftware.metricshub.engine.common.helpers.MetricsHubConstants.EMPTY;
import static org.sentrysoftware.metricshub.engine.common.helpers.MetricsHubConstants.HOST_NAME;
import static org.sentrysoftware.metricshub.engine.common.helpers.MetricsHubConstants.HOST_TYPE_TO_OTEL_HOST_TYPE;
import static org.sentrysoftware.metricshub.engine.common.helpers.MetricsHubConstants.HOST_TYPE_TO_OTEL_OS_TYPE;
import static org.sentrysoftware.metricshub.engine.common.helpers.MetricsHubConstants.IS_ENDPOINT;
import static org.sentrysoftware.metricshub.engine.common.helpers.MetricsHubConstants.MONITOR_ATTRIBUTE_ID;
import static org.sentrysoftware.metricshub.engine.common.helpers.MetricsHubConstants.MONITOR_ATTRIBUTE_NAME;
import static org.sentrysoftware.metricshub.engine.common.helpers.MetricsHubConstants.UNDERSCORE;

import java.net.InetAddress;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.sentrysoftware.metricshub.engine.alert.AlertRule;
import org.sentrysoftware.metricshub.engine.common.HostLocation;
import org.sentrysoftware.metricshub.engine.common.helpers.KnownMonitorType;
import org.sentrysoftware.metricshub.engine.common.helpers.MetricsHubConstants;
import org.sentrysoftware.metricshub.engine.common.helpers.NetworkHelper;
import org.sentrysoftware.metricshub.engine.common.helpers.StringHelper;
import org.sentrysoftware.metricshub.engine.configuration.HostConfiguration;
import org.sentrysoftware.metricshub.engine.connector.model.common.DeviceKind;
import org.sentrysoftware.metricshub.engine.telemetry.metric.AbstractMetric;

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

	private Resource resource;

	private Map<String, List<AlertRule>> alertRules;

	private TelemetryManager telemetryManager;

	private String monitorType;

	private String connectorId;

	@NonNull
	private Long discoveryTime;

	/**
	 * This method creates or updates the monitor
	 *
	 * @param id identifier of the monitor
	 * @return created or updated {@link Monitor} instance
	 */
	public Monitor createOrUpdateMonitor(final String id) {
		return createOrUpdateMonitor(attributes, resource, monitorType, id);
	}

	/**
	 * This method creates or updates the monitor
	 *
	 * @return created or updated {@link Monitor} instance
	 */
	public Monitor createOrUpdateMonitor() {
		// Build the monitor unique identifier
		final String id = buildMonitorId(connectorId, monitorType, attributes.get(MONITOR_ATTRIBUTE_ID));

		return createOrUpdateMonitor(attributes, resource, monitorType, id);
	}

	/**
	 * This method creates or updates the monitor
	 *
	 * @param attributes  monitor attributes
	 * @param resource    monitor resource
	 * @param monitorType the type of the monitor
	 * @param id          unique identifier of the monitor
	 * @return Monitor instance
	 */
	Monitor createOrUpdateMonitor(
		final Map<String, String> attributes,
		final Resource resource,
		final String monitorType,
		final String id
	) {
		return createOrUpdateMonitor(attributes, resource, monitorType, id, discoveryTime);
	}

	/**
	 * This method creates or updates the monitor
	 *
	 * @param attributes    monitor attributes
	 * @param resource      monitor resource
	 * @param monitorType   the type of the monitor
	 * @param id            unique identifier of the monitor
	 * @param discoveryTime The time of discovery
	 * @return Monitor instance
	 */
	Monitor createOrUpdateMonitor(
		final Map<String, String> attributes,
		final Resource resource,
		final String monitorType,
		final String id,
		final long discoveryTime
	) {
		final Monitor foundMonitor = telemetryManager.findMonitorByTypeAndId(monitorType, id);

		if (foundMonitor != null) {
			foundMonitor.setAttributes(attributes);
			foundMonitor.setResource(resource);
			foundMonitor.setType(monitorType);
			foundMonitor.setDiscoveryTime(discoveryTime);

			return foundMonitor;
		} else {
			final Monitor newMonitor = Monitor
				.builder()
				.resource(resource)
				.attributes(attributes)
				.type(monitorType)
				.id(id)
				.discoveryTime(discoveryTime)
				.build();

			if (connectorId != null) {
				newMonitor.addAttribute(MetricsHubConstants.MONITOR_ATTRIBUTE_CONNECTOR_ID, connectorId);
			}

			telemetryManager.addNewMonitor(newMonitor, monitorType, id);

			return newMonitor;
		}
	}

	/**
	 * Creates the endpoint Host monitor
	 *
	 * @param isLocalhost Whether the host should be localhost or not.
	 * @return Monitor instance
	 */
	public Monitor createEndpointHostMonitor(final boolean isLocalhost) {
		// Get the host configuration
		final HostConfiguration hostConfiguration = telemetryManager.getHostConfiguration();

		final String hostname = hostConfiguration.getHostname();

		// Create the host
		final Map<String, String> monitorAttributes = Map.of(
			MONITOR_ATTRIBUTE_ID,
			telemetryManager.getHostConfiguration().getHostId(),
			"location",
			isLocalhost ? HostLocation.LOCAL.getKey() : HostLocation.REMOTE.getKey(),
			IS_ENDPOINT,
			"true",
			MONITOR_ATTRIBUTE_NAME,
			telemetryManager.getHostname()
		);

		final DeviceKind deviceKind = hostConfiguration.getHostType();

		// The host resource os.type
		final String osType = HOST_TYPE_TO_OTEL_OS_TYPE.getOrDefault(deviceKind, deviceKind.getDisplayName().toLowerCase());

		// The host resource host.type
		final String hostType = HOST_TYPE_TO_OTEL_HOST_TYPE.getOrDefault(
			deviceKind,
			deviceKind.getDisplayName().toLowerCase()
		);

		final Map<String, String> resourceAttributes = Map.of(
			"host.id",
			hostConfiguration.getHostId(),
			HOST_NAME,
			NetworkHelper.getFqdn(hostname),
			"host.type",
			hostType,
			"os.type",
			osType,
			"agent.host.name",
			StringHelper.getValue(() -> InetAddress.getLocalHost().getCanonicalHostName(), "unknown")
		);
		final Resource monitorResource = Resource.builder().type("host").attributes(resourceAttributes).build();

		// Create the monitor using createOrUpdateMonitor
		final Monitor monitor = createOrUpdateMonitor(
			monitorAttributes,
			monitorResource,
			KnownMonitorType.HOST.getKey(),
			telemetryManager.getHostConfiguration().getHostId()
		);

		// Flag the host as endpoint
		monitor.setAsEndpoint();

		log.debug("Hostname {} - Created endpoint host ID: {} ", hostname, hostConfiguration.getHostId());

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
