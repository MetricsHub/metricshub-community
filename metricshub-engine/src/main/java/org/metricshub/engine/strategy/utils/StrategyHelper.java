package org.metricshub.engine.strategy.utils;

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

import static org.metricshub.engine.common.helpers.MetricsHubConstants.MONITOR_ATTRIBUTE_ID;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.metricshub.engine.connector.model.Connector;
import org.metricshub.engine.connector.model.ConnectorStore;
import org.metricshub.engine.connector.model.identity.ConnectorIdentity;
import org.metricshub.engine.connector.model.identity.Detection;
import org.metricshub.engine.telemetry.Monitor;

/**
 * The {@code StrategyHelper} class is a utility class that provides helper methods for strategy-related operations.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class StrategyHelper {

	/**
	 * This method retrieves the connectors from the connector store by their connector identifiers
	 * available in the connectorMonitors collection
	 *
	 * @param connectorStore    The connector store instance wrapping the {@link Connector} instances
	 * @param connectorMonitors The collection of {@link Monitor} objects typed as connector
	 * @return {@link List} of {@link Connector} instances
	 */
	public static List<Connector> getConnectorsFromStoreByMonitorIds(
		final ConnectorStore connectorStore,
		final Collection<Monitor> connectorMonitors
	) {
		if (connectorMonitors == null || connectorMonitors.isEmpty()) {
			return List.of();
		}

		// Build a set of connector identifiers and retrieve the corresponding connectors from the connector store

		// Retrieve the connector identifiers
		final Set<String> connectorIds = connectorMonitors
			.stream()
			.map(monitor -> monitor.getAttributes().get(MONITOR_ATTRIBUTE_ID))
			.collect(Collectors.toSet());

		// Keep only connectors that match the connector identifiers
		return connectorStore
			.getStore()
			.entrySet()
			.stream()
			.filter(entry -> connectorIds.contains(entry.getKey()))
			.map(Map.Entry::getValue)
			.toList();
	}

	/**
	 * Checks if the connector is a hardware connector.
	 *
	 * @param connector The connector to check
	 * @return true if the connector is a hardware connector, false otherwise
	 */
	public static boolean isHardwareConnector(final Connector connector) {
		final ConnectorIdentity connectorIdentity = connector.getConnectorIdentity();
		final Detection detection = connectorIdentity != null ? connectorIdentity.getDetection() : null;
		final Set<String> connectorTags = detection != null ? detection.getTags() : null;
		return connectorTags != null && connectorTags.stream().anyMatch(tag -> tag.equalsIgnoreCase("hardware"));
	}
}
