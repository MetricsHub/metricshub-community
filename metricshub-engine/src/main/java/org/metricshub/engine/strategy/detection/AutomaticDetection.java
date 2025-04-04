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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.metricshub.engine.client.ClientsExecutor;
import org.metricshub.engine.configuration.HostConfiguration;
import org.metricshub.engine.connector.model.Connector;
import org.metricshub.engine.connector.model.ConnectorStore;
import org.metricshub.engine.connector.model.common.DeviceKind;
import org.metricshub.engine.connector.model.identity.ConnectionType;
import org.metricshub.engine.connector.model.monitor.task.source.Source;
import org.metricshub.engine.extension.ExtensionManager;
import org.metricshub.engine.telemetry.TelemetryManager;

/**
 * The {@code AutomaticDetection} class represents a strategy for automatically detecting connectors based on predefined criteria.
 * It extends the {@link AbstractConnectorProcessor} class and implements the connector detection logic.
 *
 * <p>
 * The automatic detection process involves filtering connectors based on various criteria such as device kind, connection type,
 * accepted sources, and exclusion tags. The results of the detection are stored in {@link ConnectorTestResult} objects.
 * </p>
 *
 * <p>
 * The detection process includes device kind filtering, connection type filtering, and accepted sources filtering.
 * It also checks if auto-detection is disabled for connectors.
 * </p>
 */
@Slf4j
@NoArgsConstructor
public class AutomaticDetection extends AbstractConnectorProcessor {

	/**
	 * Constructs a new {@code AutomaticDetection} instance using the provided telemetry manager and clients executor.
	 *
	 * @param telemetryManager The telemetry manager responsible for managing telemetry-related operations.
	 * @param clientsExecutor  The executor for managing clients used in the strategy.
	 * @param connectorIds     The set of connector identifiers that represent the connectors involved in the automatic detection.
	 * @param extensionManager The extension manager where all the required extensions are handled.
	 */
	public AutomaticDetection(
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
		log.debug("Hostname {} - Start automatic detection.", hostname);

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

		final DeviceKind deviceKind = hostConfiguration.getHostType();

		final boolean isLocalhost = telemetryManager.getHostProperties().isLocalhost();
		final Set<Class<? extends Source>> acceptedSources = hostConfiguration.determineAcceptedSources(
			isLocalhost,
			extensionManager
		);

		if (connectorStore.isEmpty()) {
			log.error("Hostname {} - No connector to detect. Stopping detection operation.", hostname);
			return new ArrayList<>();
		}

		final List<Connector> connectors = connectorStore
			.entrySet()
			.stream()
			.filter(connectorEntry -> connectorIds.contains(connectorEntry.getKey()))
			.map(Entry::getValue)
			.filter(connector -> connector.getConnectorIdentity().getDetection() != null)
			// No Auto Detection Filtering
			.filter(connector -> !connector.getConnectorIdentity().getDetection().isDisableAutoDetection())
			// DeviceKind Filtering
			.filter(connector -> connector.getConnectorIdentity().getDetection().getAppliesTo().contains(deviceKind))
			// ConnectionType Filtering
			.filter(connector -> hasSameConnectionTypeAsHost(connector, isLocalhost))
			// Accepted Sources Filtering
			.filter(connector -> hasMatchingSourceTypeInAcceptedSources(connector.getSourceTypes(), acceptedSources))
			.collect(Collectors.toList()); //NOSONAR

		final Set<String> supersedes = new HashSet<>();
		List<ConnectorTestResult> connectorTestResults = runAllConnectorsDetectionCriteria(
			connectors.stream(),
			hostConfiguration
		)
			// Keep Only Success Connectors
			.filter(ConnectorTestResult::isSuccess)
			.collect(Collectors.toList()); //NOSONAR

		// Find all Superseded connectors
		connectorTestResults.forEach(connectorTestResult -> updateSupersedes(supersedes, connectorTestResult));

		// Filter Superseded connectors
		connectorTestResults =
			connectorTestResults
				.stream()
				.filter(connectorTestResult ->
					!supersedes.contains(
						connectorTestResult.getConnector().getConnectorIdentity().getCompiledFilename().toLowerCase()
					)
				)
				.collect(Collectors.toList()); //NOSONAR

		// Filter onLastResort Connectors
		filterLastResortConnectors(connectorTestResults, hostname);

		return connectorTestResults;
	}

	/**
	 * Return true if the connector has the same type of connection as the host (local or remote).
	 *
	 * @param connector   The connector to test.
	 * @param isLocalhost True if the host is a local machine.
	 * @return True if the connector has the same connection type as the host, false otherwise.
	 */
	private boolean hasSameConnectionTypeAsHost(final Connector connector, final boolean isLocalhost) {
		return connector
			.getConnectorIdentity()
			.getDetection()
			.getConnectionTypes()
			.contains(isLocalhost ? ConnectionType.LOCAL : ConnectionType.REMOTE);
	}

	/**
	 * Checks if there is a matching connector source type in the set of accepted sources.
	 *
	 * @param connectorSourceTypes The set of source types associated with the connector.
	 * @param acceptedSources      The set of accepted sources to check for matching source types.
	 * @return True if there is a matching connector source type in the accepted sources, false otherwise.
	 */
	private boolean hasMatchingSourceTypeInAcceptedSources(
		final Set<Class<? extends Source>> connectorSourceTypes,
		final Set<Class<? extends Source>> acceptedSources
	) {
		return acceptedSources.stream().anyMatch(connectorSourceTypes::contains);
	}
}
