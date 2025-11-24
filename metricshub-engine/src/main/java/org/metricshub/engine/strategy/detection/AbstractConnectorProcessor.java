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

import static org.metricshub.engine.common.helpers.MetricsHubConstants.HOSTNAME_EXCEPTION_MESSAGE;
import static org.metricshub.engine.common.helpers.MetricsHubConstants.MAX_THREADS_COUNT;
import static org.metricshub.engine.common.helpers.MetricsHubConstants.THREAD_TIMEOUT;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.metricshub.engine.client.ClientsExecutor;
import org.metricshub.engine.common.helpers.StringHelper;
import org.metricshub.engine.configuration.HostConfiguration;
import org.metricshub.engine.connector.model.Connector;
import org.metricshub.engine.connector.model.identity.ConnectorIdentity;
import org.metricshub.engine.connector.model.identity.Detection;
import org.metricshub.engine.connector.model.identity.criterion.Criterion;
import org.metricshub.engine.connector.model.identity.criterion.SnmpGetCriterion;
import org.metricshub.engine.connector.model.identity.criterion.SnmpGetNextCriterion;
import org.metricshub.engine.connector.model.monitor.MonitorJob;
import org.metricshub.engine.connector.model.monitor.SimpleMonitorJob;
import org.metricshub.engine.connector.model.monitor.StandardMonitorJob;
import org.metricshub.engine.extension.ExtensionManager;
import org.metricshub.engine.strategy.utils.EmulationHelper;
import org.metricshub.engine.strategy.utils.ForceSerializationHelper;
import org.metricshub.engine.telemetry.TelemetryManager;

/**
 * Abstract base class for processing and detecting connectors based on specific criteria.
 * Implementations of this class define the logic for running detection jobs and processing connector criteria.
 * <p>
 * This class provides methods for running detection criteria sequentially or simultaneously and filtering connectors based on criteria results.
 * It also includes utilities for updating supersedes information and handling last resort connectors.
 * </p>
 * <p>
 * Connector detection involves evaluating criteria defined in the {@link Detection} object associated with a {@link Connector}.
 * </p>
 */
@Slf4j
@AllArgsConstructor
@NoArgsConstructor
public abstract class AbstractConnectorProcessor {

	@NonNull
	protected TelemetryManager telemetryManager;

	@NonNull
	protected ClientsExecutor clientsExecutor;

	@NonNull
	protected Set<String> connectorIds;

	@NonNull
	protected ExtensionManager extensionManager;

	/**
	 * Run the Detection job and returns the detected {@link ConnectorTestResult}
	 *
	 * @return The {@link List} of {@link ConnectorTestResult}
	 */
	public abstract List<ConnectorTestResult> run();

	/**
	 * Run all detection criteria of the {@link Connector} on the {@link HostConfiguration}
	 * @param connectors          The stream of connectors to run detection on.
	 * @param hostConfiguration   The host configuration providing information about the host.
	 * @return A stream of {@link ConnectorTestResult}s representing the results of connector detection.
	 */
	public Stream<ConnectorTestResult> runAllConnectorsDetectionCriteria(
		@NonNull Stream<Connector> connectors,
		@NonNull HostConfiguration hostConfiguration
	) {
		final String hostname = hostConfiguration.getHostname();

		return (
			hostConfiguration.isSequential()
				? runConnectorsSequentially(connectors, hostname)
				: runConnectorsSimultaneously(connectors, hostname)
		).stream();
	}

	/**
	 * Run all connectors sequentially
	 * @param connectors The connectors to run
	 * @param hostname   The name of the host
	 * @return The result of each connector
	 */
	private List<ConnectorTestResult> runConnectorsSequentially(
		@NonNull Stream<Connector> connectors,
		@NonNull String hostname
	) {
		final List<ConnectorTestResult> connectorTestResults = new ArrayList<>();

		connectors.forEach(connector -> connectorTestResults.add(runConnectorDetectionCriteria(connector, hostname)));

		return connectorTestResults;
	}

	/**
	 * Run all connectors simultaneously
	 * @param connectors The connectors to run
	 * @param hostname   The name of the host
	 * @return The result of each connector
	 */
	private List<ConnectorTestResult> runConnectorsSimultaneously(
		@NonNull Stream<Connector> connectors,
		@NonNull String hostname
	) {
		final List<ConnectorTestResult> connectorTestResults = new ArrayList<>();
		final List<ConnectorTestResult> connectorTestResultsSynchronized = Collections.synchronizedList(
			connectorTestResults
		);

		final ExecutorService threadsPool = Executors.newFixedThreadPool(MAX_THREADS_COUNT);

		connectors.forEach(connector ->
			threadsPool.execute(() -> connectorTestResultsSynchronized.add(runConnectorDetectionCriteria(connector, hostname))
			)
		);

		// Order the shutdown
		threadsPool.shutdown();

		try {
			// Blocks until all tasks have completed execution after a shutdown request
			threadsPool.awaitTermination(THREAD_TIMEOUT, TimeUnit.SECONDS);
		} catch (Exception e) {
			if (e instanceof InterruptedException) {
				Thread.currentThread().interrupt();
			}
			log.error("Hostname {} - Exception encountered while running connectors simultaneously.", hostname);
			log.debug(HOSTNAME_EXCEPTION_MESSAGE, hostname, e);
		}

		return connectorTestResults;
	}

	/**
	 * Update the given {@link Set} of supersedes connectors
	 *
	 * @param supersedes
	 * @param connectorTestResult
	 */
	void updateSupersedes(@NonNull final Set<String> supersedes, @NonNull final ConnectorTestResult connectorTestResult) {
		final Set<String> connectorSupersedes = connectorTestResult
			.getConnector()
			.getConnectorIdentity()
			.getDetection()
			.getSupersedes();
		if (connectorSupersedes == null || connectorSupersedes.isEmpty()) {
			return;
		}

		supersedes.addAll(connectorSupersedes.stream().map(String::toLowerCase).collect(Collectors.toSet()));
	}

	/**
	 * Removes detected connectors of type "last resort" if their specified "last resort" monitor type (enclosure, fan, etc.) is already
	 * discovered by a "regular" connector.
	 *
	 * @param matchingConnectorTestResultList The list of detected connectors, that match the host
	 * @param hostname                        The name of the host currently discovered
	 *
	 */
	void filterLastResortConnectors(
		final @NonNull List<ConnectorTestResult> matchingConnectorTestResultList,
		final @NonNull String hostname
	) {
		// Extract the list of last resort connectors from the list of matching connectors
		final List<ConnectorTestResult> lastResortConnectorTestResultList = matchingConnectorTestResultList
			.stream()
			.filter(connectorTestResult ->
				connectorTestResult.getConnector().getConnectorIdentity().getDetection().getOnLastResort() != null
			)
			.collect(Collectors.toList());

		if (lastResortConnectorTestResultList.isEmpty()) {
			return;
		}

		// Extract the list of regular connectors from the list of matching connectors
		final List<ConnectorTestResult> regularConnectorTestResultList = matchingConnectorTestResultList
			.stream()
			.filter(connectorTestResult ->
				connectorTestResult.getConnector().getConnectorIdentity().getDetection().getOnLastResort() == null
			)
			.collect(Collectors.toList());

		// Go through the list of last resort connectors and remove them if a regular connector discovers the same monitor type
		final String[] connectorIdHolder = new String[1];
		lastResortConnectorTestResultList.forEach(lastResortConnectorTestResult -> {
			boolean hasLastResortMonitor = regularConnectorTestResultList
				.stream()
				.anyMatch(regularConnectorTestResult ->
					hasLastResortMonitorJob(
						hostname,
						connectorIdHolder,
						regularConnectorTestResult,
						lastResortConnectorTestResult
					)
				);

			if (hasLastResortMonitor) {
				// The current connector discovers the same type has the defined last resort type. Discard last resort connector
				matchingConnectorTestResultList.remove(lastResortConnectorTestResult);

				log.info(
					"Hostname {} - {} is a \"last resort\" connector and its components are already discovered thanks to connector {}. Connector is therefore discarded.",
					hostname,
					lastResortConnectorTestResult.getConnector().getCompiledFilename(),
					connectorIdHolder[0]
				);
			} else {
				// Add the last resort connector to the list of "regular" connectors so that it prevents other
				// last resort connectors of the same type from matching (but that should never happen, right connector developers?)
				regularConnectorTestResultList.add(lastResortConnectorTestResult);
			}
		});
	}

	/**
	 * Whether the regular connector test result has last resort monitor job defining a discovery or simple job and having a mapping section
	 *
	 * @param hostname                      The name of the host currently discovered
	 * @param connectorIdHolder             Holds connector identifier used to remember connector identifier through the lambda expression
	 * @param regularConnectorTestResult    Detected connector result, that matches the host and haven't a last resort monitor directive
	 * @param lastResortConnectorTestResult Detected connector result, that matches the host and have a last resort monitor directive
	 * @return boolean value
	 */
	private boolean hasLastResortMonitorJob(
		final String hostname,
		final String[] connectorIdHolder,
		final ConnectorTestResult regularConnectorTestResult,
		final ConnectorTestResult lastResortConnectorTestResult
	) {
		final Map<String, MonitorJob> monitorJobs = regularConnectorTestResult.getConnector().getMonitors();

		// Remember connector's identifier
		connectorIdHolder[0] = regularConnectorTestResult.getConnector().getCompiledFilename();

		if (monitorJobs == null || monitorJobs.isEmpty()) {
			log.warn(
				"Hostname {} - {} connector detection. On last resort filter: Connector {} has no monitors.",
				hostname,
				hostname,
				connectorIdHolder[0]
			);

			return false;
		}

		// The monitor's job and mapping must not be empty
		return monitorJobs
			.entrySet()
			.stream()
			.anyMatch(monitorJobEntry -> {
				final boolean hasLastResortMonitorType = lastResortConnectorTestResult
					.getConnector()
					.getConnectorIdentity()
					.getDetection()
					.getOnLastResort()
					.equals(monitorJobEntry.getKey());

				// If there is no monitor job entry having the last resort, we consider
				// that the regular connector test result hasn't the last resort monitor job
				if (!hasLastResortMonitorType) {
					return false;
				}

				// Make sure we really have monitoring job discovering the last resort monitor type
				final MonitorJob monitorJob = monitorJobEntry.getValue();
				if (monitorJob != null) {
					if (monitorJob instanceof SimpleMonitorJob simpleMonitorJob) {
						return simpleMonitorJob.getSimple() != null && simpleMonitorJob.getSimple().getMapping() != null;
					} else if (monitorJob instanceof StandardMonitorJob standardMonitorJob) {
						return standardMonitorJob.getDiscovery() != null && standardMonitorJob.getDiscovery().getMapping() != null;
					}
				}
				return false;
			});
	}

	/**
	 * Run the criterion processor which implements the logic that needs to be
	 * executed for this criterion instance.
	 *
	 * @param criterion The criterion we wish to process
	 * @param connector The {@link Connector} defining the criterion
	 * @return <code>true</code> if the criterion execution succeeded
	 */
	protected CriterionTestResult processCriterion(final Criterion criterion, final Connector connector) {
		// Instantiate criterionProcessor with clientsExecutor, telemetryManager and connector name
		final CriterionProcessor criterionProcessor = new CriterionProcessor(
			clientsExecutor,
			telemetryManager,
			connector.getConnectorIdentity().getCompiledFilename(),
			extensionManager
		);

		final Supplier<CriterionTestResult> executable;

		// Based on the type of criterion, store the call to the test method in the supplier
		executable = () -> criterionProcessor.test(criterion);

		// If isForceSerialization is true, call forceSerialization
		if (criterion.isForceSerialization()) {
			return ForceSerializationHelper.forceSerialization(
				executable,
				telemetryManager,
				connector.getCompiledFilename(),
				criterion,
				"criterion",
				CriterionTestResult.empty()
			);
		} else {
			return executable.get();
		}
	}

	/**
	 * Return true if the name of the {@link Connector} is in the set of connector names
	 * @param connector      The connector to check
	 * @param connectorIdSet The set of connector names
	 * @return boolean value indicating if the connector is contained in the set
	 */
	protected boolean isConnectorContainedInSet(
		@NonNull final Connector connector,
		@NonNull final Set<String> connectorIdSet
	) {
		final ConnectorIdentity connectorIdentity = connector.getConnectorIdentity();
		if (connectorIdentity == null) {
			return false;
		}

		final String connectorId = connectorIdentity.getCompiledFilename();
		return connectorId != null && connectorIdSet.contains(connectorId);
	}

	/**
	 * Run the detection using the criteria defined in the given connector.
	 *
	 * @param connector The connector we wish to test
	 * @param hostname  The hostname of the host device
	 *
	 * @return {@link ConnectorTestResult} instance which tells if the connector test succeeded or not.
	 */
	public ConnectorTestResult runConnectorDetectionCriteria(Connector connector, String hostname) {
		final Detection detection = connector.getConnectorIdentity().getDetection();

		final ConnectorTestResult connectorTestResult = ConnectorTestResult.builder().connector(connector).build();

		if (detection == null) {
			log.warn(
				"Hostname {} - The connector {} DOES NOT match the platform as it has no detection to test.",
				hostname,
				connector.getConnectorIdentity().getCompiledFilename()
			);
			return connectorTestResult;
		}

		final List<Criterion> criteria = detection.getCriteria();

		if (criteria == null || criteria.isEmpty()) {
			log.warn(
				"Hostname {} - The connector {} DOES NOT match the platform as it has no criteria to test.",
				hostname,
				connector.getConnectorIdentity().getCompiledFilename()
			);
			return connectorTestResult;
		}

		// Retrieve emulation input directory and read recorded criterion from it
		final String emulationInputDirectory = telemetryManager.getEmulationInputDirectory();

		// Persist source output if required and if not SNMP source
		final String recordOutputDirectory = telemetryManager.getRecordOutputDirectory();

		int criterionId = 1;

		for (final Criterion criterion : criteria) {
			CriterionTestResult criterionTestResult = null;

			// CHECKSTYLE:OFF
			if (
				StringHelper.nonNullNonBlank(emulationInputDirectory) &&
				!(criterion instanceof SnmpGetCriterion) &&
				!(criterion instanceof SnmpGetNextCriterion)
			) {
				criterionTestResult =
					EmulationHelper
						.readEmulatedCriterionResult(
							connector.getCompiledFilename(),
							criterion,
							emulationInputDirectory,
							criterionId,
							telemetryManager
						)
						.orElseGet(CriterionTestResult::empty);
			} else {
				criterionTestResult = processCriterion(criterion, connector);
				if (!criterionTestResult.isSuccess()) {
					log.debug(
						"Hostname {} - Detected failed criterion for connector {}. Message: {}.",
						hostname,
						connector.getConnectorIdentity().getCompiledFilename(),
						criterionTestResult.getMessage()
					);
				}
				if (
					StringHelper.nonNullNonBlank(recordOutputDirectory) &&
					!(criterion instanceof SnmpGetCriterion) &&
					!(criterion instanceof SnmpGetNextCriterion)
				) {
					EmulationHelper.persist(
						criterionTestResult,
						connector.getCompiledFilename(),
						criterion,
						recordOutputDirectory,
						criterionId,
						telemetryManager
					);
				}
				// CHECKSTYLE:ON
			}
			criterionId++;
			connectorTestResult.getCriterionTestResults().add(criterionTestResult);
		}

		return connectorTestResult;
	}
}
