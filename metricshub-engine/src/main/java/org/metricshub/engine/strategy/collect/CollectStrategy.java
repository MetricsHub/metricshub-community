package org.metricshub.engine.strategy.collect;

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

import static org.metricshub.engine.common.helpers.MetricsHubConstants.MAX_CONSECUTIVE_DETECTION_FAILURES;
import static org.metricshub.engine.common.helpers.MetricsHubConstants.MAX_THREADS_COUNT;
import static org.metricshub.engine.common.helpers.MetricsHubConstants.MONITOR_ATTRIBUTE_CONNECTOR_ID;
import static org.metricshub.engine.common.helpers.MetricsHubConstants.MONITOR_JOBS_PRIORITY;
import static org.metricshub.engine.common.helpers.MetricsHubConstants.OTHER_MONITOR_JOB_TYPES;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.metricshub.engine.client.ClientsExecutor;
import org.metricshub.engine.common.ConnectorMonitorTypeComparator;
import org.metricshub.engine.common.JobInfo;
import org.metricshub.engine.common.helpers.KnownMonitorType;
import org.metricshub.engine.common.helpers.ThreadHelper;
import org.metricshub.engine.connector.model.Connector;
import org.metricshub.engine.connector.model.monitor.MonitorJob;
import org.metricshub.engine.connector.model.monitor.StandardMonitorJob;
import org.metricshub.engine.connector.model.monitor.task.AbstractCollect;
import org.metricshub.engine.connector.model.monitor.task.Mapping;
import org.metricshub.engine.connector.model.monitor.task.MultiInstanceCollect;
import org.metricshub.engine.extension.ExtensionManager;
import org.metricshub.engine.strategy.AbstractStrategy;
import org.metricshub.engine.strategy.source.OrderedSources;
import org.metricshub.engine.strategy.source.SourceTable;
import org.metricshub.engine.strategy.surrounding.BeforeAllStrategy;
import org.metricshub.engine.strategy.utils.MappingProcessor;
import org.metricshub.engine.strategy.utils.StrategyHelper;
import org.metricshub.engine.telemetry.MetricFactory;
import org.metricshub.engine.telemetry.Monitor;
import org.metricshub.engine.telemetry.MonitorFactory;
import org.metricshub.engine.telemetry.TelemetryManager;

/**
 * The {@code CollectStrategy} class represents a strategy for collecting metrics from various connectors
 * in a monitoring system.
 *
 * <p>
 * This class is part of a strategy design pattern and is responsible for executing the collection
 * of monitors and their metrics for a given host and connectors.
 * </p>
 *
 * <p>
 * It uses a combination of sequential and parallel execution based on the configuration to efficiently
 * collect metrics from different connectors.
 * </p>
 */
@Slf4j
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class CollectStrategy extends AbstractStrategy {

	private static final String JOB_NAME = "collect";

	/**
	 * Constructs a new {@code CollectStrategy} using the provided telemetry manager, strategy time, and
	 * clients executor.
	 *
	 * @param telemetryManager The telemetry manager responsible for managing telemetry-related operations.
	 * @param strategyTime     The time when the strategy is executed.
	 * @param clientsExecutor  The executor for managing clients used in the strategy.
	 * @param extensionManager The extension manager where all the required extensions are handled.
	 */
	@Builder
	public CollectStrategy(
		@NonNull final TelemetryManager telemetryManager,
		@NonNull final Long strategyTime,
		@NonNull final ClientsExecutor clientsExecutor,
		@NonNull final ExtensionManager extensionManager
	) {
		super(telemetryManager, strategyTime, clientsExecutor, extensionManager);
	}

	/**
	 * This method collects the monitors and their metrics
	 * @param currentConnector Connector instance
	 * @param hostname The host name
	 * @param runStartTime The {@link System#nanoTime()} at which this strategy run started
	 * @param pendingPools Where to defer the end of this connector when its monitor jobs outlive the thread timeout
	 */
	private void collect(
		final Connector currentConnector,
		final String hostname,
		final long runStartTime,
		final List<PendingPool> pendingPools
	) {
		// Check whether the strategy job name matches at least one of the monitor jobs names of the current connector
		final boolean connectorHasExpectedJobTypes = hasExpectedJobTypes(currentConnector, JOB_NAME);
		// If the connector doesn't define any monitor job of type collect, log a message then exit the current collect operation
		if (!connectorHasExpectedJobTypes) {
			log.debug("Connector doesn't define any monitor job of type collect.");
			return;
		}
		if (!validateConnectorDetectionCriteria(currentConnector, hostname, JOB_NAME)) {
			log.error(
				"Hostname {} - The connector {} no longer matches the host after {} consecutive detection failures." +
					" Stopping the connector's collect job.",
				hostname,
				currentConnector.getCompiledFilename(),
				MAX_CONSECUTIVE_DETECTION_FAILURES
			);

			return;
		}

		// Run BeforeAllStrategy that executes beforeAll sources
		final BeforeAllStrategy beforeAllStrategy = BeforeAllStrategy.builder()
			.clientsExecutor(clientsExecutor)
			.strategyTime(strategyTime)
			.telemetryManager(telemetryManager)
			.connector(currentConnector)
			.extensionManager(extensionManager)
			.build();

		beforeAllStrategy.run();
		// A beforeAll skipped by an interrupt opened no session: there is none to close
		final boolean opened = beforeAllStrategy.isOpened();

		// Sort the connector monitor jobs according to the priority map
		final Map<String, MonitorJob> connectorMonitorJobs = currentConnector.getMonitors()
			.entrySet()
			.stream()
			.sorted(
				Comparator.comparing(
					entry -> MONITOR_JOBS_PRIORITY.containsKey(entry.getKey())
						? MONITOR_JOBS_PRIORITY.get(entry.getKey())
						: MONITOR_JOBS_PRIORITY.get(OTHER_MONITOR_JOB_TYPES)
				)
			)
			.collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (oldValue, _) -> oldValue, LinkedHashMap::new));

		final Map<String, MonitorJob> sequentialMonitorJobs = connectorMonitorJobs.entrySet()
			.stream()
			.filter(entry -> MONITOR_JOBS_PRIORITY.containsKey(entry.getKey()))
			.collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (oldValue, _) -> oldValue, LinkedHashMap::new));

		final Map<String, MonitorJob> otherMonitorJobs = connectorMonitorJobs.entrySet()
			.stream()
			.filter(entry -> !MONITOR_JOBS_PRIORITY.containsKey(entry.getKey()))
			.collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (oldValue, _) -> oldValue, LinkedHashMap::new));

		// Run monitor jobs defined in monitor jobs priority map (host, enclosure, blade, disk_controller and cpu)  in sequential mode
		sequentialMonitorJobs.entrySet().forEach(entry -> processMonitorJob(currentConnector, hostname, entry));

		final boolean isSequential = telemetryManager.getHostConfiguration().isSequential();
		final String mode = isSequential ? "sequential" : "parallel";

		log.info(
			"Hostname {} - Running collect in {} mode. Connector: {}.",
			hostname,
			mode,
			currentConnector.getConnectorIdentity().getCompiledFilename()
		);

		// If monitor jobs execution is set to "sequential", execute monitor jobs one by one
		if (isSequential) {
			otherMonitorJobs.entrySet().forEach(entry -> processMonitorJob(currentConnector, hostname, entry));
		} else if (Thread.currentThread().isInterrupted()) {
			// The new threads of a pool would not inherit the interrupt and would send the requests of an abandoned run
			log.warn(
				"Hostname {} - Thread interrupted: skipping the parallel monitor jobs. Connector: {}.",
				hostname,
				currentConnector.getCompiledFilename()
			);
		} else {
			// Execute monitor jobs in parallel
			// Create a thread pool with a fixed number of threads
			final ExecutorService threadsPool = Executors.newFixedThreadPool(
				Math.max(1, Math.min(MAX_THREADS_COUNT, otherMonitorJobs.size()))
			);

			otherMonitorJobs.entrySet()
				.forEach(entry -> threadsPool.execute(() -> processMonitorJob(currentConnector, hostname, entry)));

			// Two-phase shutdown: first graceful, then forced
			threadsPool.shutdown();

			try {
				// Blocks until all tasks have completed execution after a shutdown request
				if (!threadsPool.awaitTermination(getThreadTimeout(), TimeUnit.SECONDS)) {
					log.warn(
						"Hostname {} - Collect thread pool did not terminate within {} seconds. Waiting for it after the other connectors.",
						hostname,
						getThreadTimeout()
					);
					// The afterAll, which may close the session of the jobs, waits for the pool
					watchPool(threadsPool, runStartTime + TimeUnit.SECONDS.toNanos(getStrategyTimeout()));
					pendingPools.add(new PendingPool(threadsPool, currentConnector, opened));
					return;
				}
			} catch (Exception e) {
				cutPool(threadsPool);
				if (e instanceof InterruptedException) {
					Thread.currentThread().interrupt();
				}
				log.debug("Hostname {} - Waiting for threads' termination aborted with an error.", hostname, e);
				// The afterAll may close the session the jobs still use
				awaitPoolStop(threadsPool, hostname);
			}
		}
		runAfterAll(currentConnector, opened, runStartTime);
	}

	/**
	 * The monitor jobs of a connector still running at the thread timeout, and what the end of the connector needs.
	 *
	 * @param pool      The thread pool of the jobs, shut down
	 * @param connector The connector
	 * @param opened    Whether the beforeAll of the connector may have opened a session
	 */
	private record PendingPool(ExecutorService pool, Connector connector, boolean opened) {}

	/**
	 * This method processes a monitor job
	 *
	 * @param currentConnector The current connector we process its monitor job
	 * @param hostname         The host name we currently monitor
	 * @param monitorJob       The monitor job instance we wish to process
	 */
	private void processMonitorJob(
		final Connector currentConnector,
		final String hostname,
		final Map.Entry<String, MonitorJob> monitorJob
	) {
		final long jobStartTime = System.currentTimeMillis();
		if (monitorJob.getValue() instanceof StandardMonitorJob standardMonitorJob) {
			final AbstractCollect collect = standardMonitorJob.getCollect();

			// Check whether collect is null
			if (collect == null) {
				return;
			}

			final String monitorType = monitorJob.getKey();

			if (isMonitorFiltered(monitorType)) {
				return;
			}

			final JobInfo jobInfo = JobInfo.builder()
				.hostname(hostname)
				.connectorId(currentConnector.getCompiledFilename())
				.jobName(collect.getClass().getSimpleName())
				.monitorType(monitorType)
				.build();

			// Build the ordered sources
			final OrderedSources orderedSources = OrderedSources.builder()
				.sources(collect.getSources(), collect.getExecutionOrder().stream().toList(), collect.getSourceDep(), jobInfo)
				.build();

			if (monitorJob.getValue().isSingleton()) {
				// The singleton of a simple mapping, by its internal id: never a stale keyed instance kept by removalDelay
				final Monitor singleton = telemetryManager.findMonitorByTypeAndId(
					monitorType,
					MonitorFactory.buildSingletonId(currentConnector.getCompiledFilename(), monitorType)
				);
				if (singleton != null) {
					final List<Runnable> cursorRollbacks = new ArrayList<>();
					// A failed source stops the job: the metrics are left unset for this cycle
					if (
						processSourcesAndComputes(orderedSources.getSources(), singleton.getAttributes(), jobInfo, cursorRollbacks)
							== null &&
							!skipMappingIfInterrupted(cursorRollbacks, currentConnector.getCompiledFilename(), hostname)
					) {
						processMonitors(monitorType, collect.getMapping(), currentConnector, hostname, singleton);
					}
				}
			} else if (collect instanceof MultiInstanceCollect multiInstanceCollect) {
				final Map<String, Monitor> monitors = telemetryManager.findMonitorsByType(monitorType);
				if (monitors == null) {
					return;
				}

				// Create the sources and the computes for a connector
				final List<Runnable> cursorRollbacks = new ArrayList<>();
				if (
					processSourcesAndComputes(orderedSources.getSources(), null, jobInfo, cursorRollbacks) == null &&
						!skipMappingIfInterrupted(cursorRollbacks, currentConnector.getCompiledFilename(), hostname)
				) {
					// Retrieve monitor job keys
					final Set<String> monitorJobKeys = monitorJob.getValue().getKeys();
					processMonitors(monitorType, multiInstanceCollect.getMapping(), currentConnector, hostname, monitorJobKeys);
				}
			} else {
				// Get monitors by type and connectorId (connector id attribute)
				final Map<String, Monitor> sameTypeMonitors = telemetryManager.findMonitorsByType(monitorType);

				if (sameTypeMonitors != null && !sameTypeMonitors.isEmpty()) {
					sameTypeMonitors.values()
						.stream()
						.filter(
							monitor -> currentConnector.getCompiledFilename()
								.equals(monitor.getAttribute(MONITOR_ATTRIBUTE_CONNECTOR_ID))
						)
						.forEach(monitor -> {
							// One list per monitor: each monitor polls the sources again
							final List<Runnable> cursorRollbacks = new ArrayList<>();
							if (
								processSourcesAndComputes(
									orderedSources.getSources(),
									monitor.getAttributes(),
									jobInfo,
									cursorRollbacks
								) == null &&
									!skipMappingIfInterrupted(cursorRollbacks, currentConnector.getCompiledFilename(), hostname)
							) {
								processMonitors(monitorType, collect.getMapping(), currentConnector, hostname, monitor);
							}
						});
				}
			}
			final long jobEndTime = System.currentTimeMillis();
			// Set the job duration metric in the host monitor
			setJobDurationMetric(JOB_NAME, monitorType, currentConnector.getCompiledFilename(), jobStartTime, jobEndTime);
		}
	}

	/**
	 * This method processes multi instances collect
	 *
	 * @param monitorType Type of the monitor
	 * @param mapping     The collect's mapping used to collect metrics
	 * @param connector   The current connector instance
	 * @param hostname    The host name we currently monitor
	 * @param keys        {@link Set} of attribute keys used to find the monitor to collect
	 */
	private void processMonitors(
		final String monitorType,
		final Mapping mapping,
		final Connector connector,
		final String hostname,
		final Set<String> keys
	) {
		processMonitors(monitorType, mapping, connector, hostname, Optional.empty(), keys);
	}

	/**
	 * This method processes a mono instance collect
	 *
	 * @param monitorType Type of the monitor
	 * @param mapping     The collect's mapping used to collect metrics
	 * @param connector   The current connector instance
	 * @param hostname    The host name we currently monitor
	 * @param monitor     {@link Monitor} instance collect (mono instance)
	 */
	private void processMonitors(
		final String monitorType,
		final Mapping mapping,
		final Connector connector,
		final String hostname,
		final Monitor monitor
	) {
		processMonitors(monitorType, mapping, connector, hostname, Optional.of(monitor), null);
	}

	/**
	 * This method processes multi instances or mono instance monitor collect
	 *
	 * @param monitorType     type of the monitor
	 * @param mapping         the collect's mapping used to collect metrics
	 * @param connector       a given connector
	 * @param hostname        the host name we currently monitor
	 * @param maybeMonitor    empty in case of multi-instance processing otherwise an {@link Optional} of an existing
	 *                        {@link Monitor} instance used to process the mono instance collect
	 * @param attributeKeys  null in case of mono-instance processing  otherwise a {@link Set} of attribute keys
	 *                       used to find the monitor to collect in multi-instance mode
	 */
	private void processMonitors(
		final String monitorType,
		final Mapping mapping,
		final Connector connector,
		final String hostname,
		final Optional<Monitor> maybeMonitor,
		final Set<String> attributeKeys
	) {
		if (mapping == null) {
			return;
		}

		final String connectorId = connector.getCompiledFilename();

		final String mappingSource = mapping.getSource();

		// A simple mapping gives one row without any cell, read for its singleton
		final Optional<SourceTable> maybeSourceTable = SourceTable.lookupMappingTable(
			mapping,
			connectorId,
			telemetryManager
		);

		// No sourceTable no monitor
		if (maybeSourceTable.isEmpty()) {
			log.debug(
				"Hostname {} - Collect - No source table created with source key {} for connector {}.",
				hostname,
				mappingSource,
				connectorId
			);
			return;
		}

		final List<List<String>> table = maybeSourceTable.get().getTable();

		if (table.isEmpty()) {
			return;
		}

		log.debug(
			"Hostname {} - Start collect {} mapping with source {}, attributes {}, metrics {}, conditional collection {}" +
				" and legacy text parameters {}. Connector ID: {}.",
			hostname,
			monitorType,
			mapping.getSource(),
			mapping.getAttributes(),
			mapping.getMetrics(),
			mapping.getConditionalCollection(),
			mapping.getLegacyTextParameters(),
			connectorId
		);

		// If we process single monitor (monoInstance), we loop until first row.
		// Otherwise, (in case of multi-instance processing), we loop over all the source table rows
		final int rowCountLimit = maybeMonitor.isEmpty() ? table.size() : 1;

		final Map<String, Monitor> sameTypeMonitors = telemetryManager.findMonitorsByType(monitorType);

		// Loop over the source table rows
		for (int i = 0; i < rowCountLimit; i++) {
			final List<String> row = table.get(i);

			// Init mapping processor
			final MappingProcessor mappingProcessor = MappingProcessor.builder()
				.telemetryManager(telemetryManager)
				.mapping(mapping)
				.jobInfo(
					JobInfo.builder()
						.connectorId(connectorId)
						.hostname(hostname)
						.monitorType(monitorType)
						.jobName(JOB_NAME)
						.build()
				)
				.collectTime(strategyTime)
				.row(row)
				.indexCounter(i + 1)
				.build();

			// In case of multi-instance, maybeMonitor is empty. So, we try to find it by type, connector id and attribute keys
			maybeMonitor.or(
				() -> findMonitor(
					connectorId,
					sameTypeMonitors,
					mappingProcessor.interpretNonContextMappingAttributes(),
					attributeKeys
				)
			).ifPresent(monitor -> {
				// Collect metrics
				final Map<String, String> metrics = mappingProcessor.interpretNonContextMappingMetrics();

				metrics.putAll(mappingProcessor.interpretContextMappingMetrics(monitor));

				final MetricFactory metricFactory = new MetricFactory(hostname, telemetryManager.getConnectorStore());

				metricFactory.collectMonitorMetrics(monitorType, connector, monitor, connectorId, metrics, strategyTime, false);

				// Collect legacy parameters
				monitor.addLegacyParameters(mappingProcessor.interpretNonContextMappingLegacyTextParameters());
				monitor.addLegacyParameters(mappingProcessor.interpretContextMappingLegacyTextParameters(monitor));
			});
		}
	}

	/**
	 * Find monitor by attributes keys and connector identifier
	 *
	 * @param connectorId               Unique connector identifier used to find the monitor
	 * @param sameTypeMonitors          {@link Monitor} instances having the same type
	 * @param collectedAttributeValues  The collected attributes during the current cycle
	 * @param attributeKeys             The attribute keys used to find the monitor
	 * @return {@link Optional} instance containing the monitor
	 */
	private Optional<Monitor> findMonitor(
		final String connectorId,
		final Map<String, Monitor> sameTypeMonitors,
		final Map<String, String> collectedAttributeValues,
		final Set<String> attributeKeys
	) {
		return sameTypeMonitors.values()
			.stream()
			.filter(
				monitor -> matchMonitorAttributes(monitor, collectedAttributeValues, attributeKeys) &&
					connectorId.equals(monitor.getAttribute(MONITOR_ATTRIBUTE_CONNECTOR_ID))
			)
			.findFirst();
	}

	/**
	 * Checks if the attribute values of the given monitor match the collected attribute values.
	 *
	 * @param monitor                  The monitor instance we wish to verify its attributes
	 * @param collectedAttributeValues The collected attribute values
	 * @param attributeKeys            The attribute keys defined by the monitor job
	 * @return <code>true</code> if the monitor's attribute values identified by
	 *         the <em>attributeKeys</em> match the current collected attribute
	 *         values otherwise <code>false</code>
	 */
	private boolean matchMonitorAttributes(
		final Monitor monitor,
		final Map<String, String> collectedAttributeValues,
		final Set<String> attributeKeys
	) {
		return attributeKeys.stream().allMatch(key -> {
			// Get the existing monitor attribute value that should be set at the discovery
			final String monitorAttributeValue = monitor.getAttribute(key);

			// The absence of the value prevents us from progressing any further
			if (monitorAttributeValue == null) {
				return false;
			}

			// Get the collected attribute value
			final String collectedAttributeValue = collectedAttributeValues.get(key);

			// The absence of the value prevents us from progressing any further
			if (collectedAttributeValue == null) {
				return false;
			}

			// Compares the existing monitor attribute value to the collected attribute value
			return monitorAttributeValue.equals(collectedAttributeValue);
		});
	}

	/**
	 *  This method is the main collection step method
	 */
	@Override
	public void run() {
		// Capture the start of this strategy run, used to bound the wait for the monitor jobs and the afterAll
		final long runStartTime = System.nanoTime();

		// Get the host name from telemetry manager
		final String hostname = telemetryManager.getHostname();

		//Retrieve connector Monitor instances from TelemetryManager
		final Map<String, Monitor> connectorMonitors = telemetryManager.getMonitors()
			.get(KnownMonitorType.CONNECTOR.getKey());

		// Check whether the resulting map is null or empty
		if (connectorMonitors == null || connectorMonitors.isEmpty()) {
			log.error(
				"Hostname {} - Collect - No connectors detected in the detection operation. Collect operation will now be stopped.",
				hostname
			);
			return;
		}

		// Find Connector objects from the connector store using the connector monitors
		final List<Connector> detectedConnectors = StrategyHelper.getConnectorsFromStoreByMonitorIds(
			telemetryManager.getConnectorStore(),
			connectorMonitors.values()
		);

		// Get only connectors that define monitors
		final List<Connector> connectorsWithMonitorJobs = detectedConnectors.stream()
			.filter(connector -> !connector.getMonitors().isEmpty())
			.toList();

		// Sort connectors by monitor job type: first put hosts then enclosures. If two connectors have the same type of monitor job, sort them by name
		final List<Connector> sortedConnectors = connectorsWithMonitorJobs.stream()
			.sorted(new ConnectorMonitorTypeComparator())
			.toList();

		// Collect each connector
		final List<PendingPool> pendingPools = new ArrayList<>();
		// A sheltered beforeAll or afterAll clears the interrupt that watchPool looks for: it cuts the deferred jobs first
		ThreadHelper.onInterrupt(() -> pendingPools.forEach(pending -> cutPool(pending.pool())), () -> {
			try {
				sortedConnectors.forEach(connector -> collect(connector, hostname, runStartTime, pendingPools));
			} finally {
				// The jobs that outlived the thread timeout may still complete until the strategy deadline, even when a
				// later connector failed
				joinPools(
					pendingPools,
					PendingPool::pool,
					(pending, completed) -> runAfterAll(pending.connector(), pending.opened(), runStartTime),
					runStartTime + TimeUnit.SECONDS.toNanos(getStrategyTimeout()),
					hostname
				);
			}
		});

		// Collect the metricshub.host.configured metric
		collectHostConfigured(hostname);

		// Collect per-host request metrics (completed/timeout by operation type)
		collectRequestMetrics();
	}
}
