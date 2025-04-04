package org.metricshub.engine.strategy.detection;

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

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.metricshub.engine.client.ClientsExecutor;
import org.metricshub.engine.configuration.HostConfiguration;
import org.metricshub.engine.connector.model.Connector;
import org.metricshub.engine.connector.model.ConnectorStore;
import org.metricshub.engine.extension.ExtensionManager;
import org.metricshub.engine.telemetry.TelemetryManager;

/**
 * The {@code ConnectorSelection} class represents a strategy for selecting specific connectors based on predefined criteria.
 * It extends the {@link AbstractConnectorProcessor} class and implements the connector selection logic.
 *
 * <p>
 * The connector selection process involves filtering connectors based on case-sensitive and case-insensitive lists of selected connectors.
 * The results of the selection are stored in {@link ConnectorTestResult} objects.
 * </p>
 *
 * <p>
 * The class checks the host configuration and connector store availability before proceeding with the selection.
 * It utilizes the configured lists of selected connectors, considering both case-sensitive and case-insensitive sets.
 * </p>
 */
@Slf4j
@NoArgsConstructor
public class ConnectorSelection extends AbstractConnectorProcessor {

	/**
	 * Constructs a new {@code ConnectorSelection} instance using the provided telemetry manager and clients executor.
	 *
	 * @param telemetryManager  The telemetry manager responsible for managing telemetry-related operations.
	 * @param clientsExecutor   The executor for managing clients used in the strategy.
	 * @param connectorIds      The set of connector identifiers that represent the connectors involved in the connector selection.
	 * @param extensionManager The extension manager where all the required extensions are handled.
	 */
	public ConnectorSelection(
		@NonNull final TelemetryManager telemetryManager,
		@NonNull final ClientsExecutor clientsExecutor,
		@NonNull final Set<String> connectorIds,
		@NonNull final ExtensionManager extensionManager
	) {
		super(telemetryManager, clientsExecutor, connectorIds, extensionManager);
	}

	@Override
	public List<ConnectorTestResult> run() {
		final HostConfiguration hostConfiguration = telemetryManager.getHostConfiguration();
		if (hostConfiguration == null) {
			log.error("Empty host configuration, aborting detection job.");
			return Collections.emptyList();
		}

		final String hostname = hostConfiguration.getHostname();
		log.debug("Hostname {} - Start connector selection.", hostname);

		final ConnectorStore telemetryManagerConnectorStore = telemetryManager.getConnectorStore();
		if (telemetryManagerConnectorStore == null) {
			log.error("Hostname {} - No connectorStore found. Stopping detection operation.", hostname);
			return Collections.emptyList();
		}

		final Map<String, Connector> connectorStore = telemetryManagerConnectorStore.getStore();
		if (connectorStore == null) {
			log.error("Hostname {} - No connectorStore found. Stopping detection operation.", hostname);
			return Collections.emptyList();
		}

		if (connectorIds.isEmpty()) {
			log.error(
				"Hostname {} - No connectors have been selected for the detection. Stopping discovery operation.",
				hostname
			);
			return Collections.emptyList();
		}

		return runAllConnectorsDetectionCriteria(
			connectorStore.values().stream().filter(connector -> isConnectorContainedInSet(connector, connectorIds)),
			hostConfiguration
		)
			.collect(Collectors.toList()); //NOSONAR
	}
}
