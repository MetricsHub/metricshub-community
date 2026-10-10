package org.metricshub.engine.strategy;

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
import static org.metricshub.engine.common.helpers.MetricsHubConstants.MONITOR_JOBS_PRIORITY;
import static org.metricshub.engine.common.helpers.MetricsHubConstants.OTHER_MONITOR_JOB_TYPES;
import static org.metricshub.engine.common.helpers.MetricsHubConstants.SOURCE_REF_PATTERN;
import static org.metricshub.engine.common.helpers.MetricsHubConstants.THREAD_TIMEOUT;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.metricshub.engine.client.ClientsExecutor;
import org.metricshub.engine.common.ConnectorMonitorTypeComparator;
import org.metricshub.engine.common.JobInfo;
import org.metricshub.engine.common.helpers.KnownMonitorType;
import org.metricshub.engine.connector.model.Connector;
import org.metricshub.engine.connector.model.monitor.MonitorJob;
import org.metricshub.engine.connector.model.monitor.SimpleMonitorJob;
import org.metricshub.engine.connector.model.monitor.StandardMonitorJob;
import org.metricshub.engine.connector.model.monitor.task.AbstractMonitorTask;
import org.metricshub.engine.connector.model.monitor.task.Discovery;
import org.metricshub.engine.connector.model.monitor.task.Mapping;
import org.metricshub.engine.connector.model.monitor.task.Simple;
import org.metricshub.engine.connector.model.monitor.task.source.EventLogSource;
import org.metricshub.engine.connector.model.monitor.task.source.FileSource;
import org.metricshub.engine.connector.model.monitor.task.source.FileSourceProcessingMode;
import org.metricshub.engine.connector.model.monitor.task.source.Source;
import org.metricshub.engine.extension.ExtensionManager;
import org.metricshub.engine.strategy.source.OrderedSources;
import org.metricshub.engine.strategy.source.SourceTable;
import org.metricshub.engine.strategy.surrounding.AfterAllStrategy;
import org.metricshub.engine.strategy.surrounding.BeforeAllStrategy;
import org.metricshub.engine.strategy.utils.MappingProcessor;
import org.metricshub.engine.strategy.utils.StrategyHelper;
import org.metricshub.engine.telemetry.MetricFactory;
import org.metricshub.engine.telemetry.Monitor;
import org.metricshub.engine.telemetry.MonitorFactory;
import org.metricshub.engine.telemetry.TelemetryManager;

/**
 * Abstract strategy class for processing all monitor jobs at once.
 * Extends {@link AbstractStrategy}.
 */
@Slf4j
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public abstract class AbstractAllAtOnceStrategy extends AbstractStrategy {

	/**
	 * Initializes a new instance of {@code AbstractAllAtOnceStrategy} with the necessary components for executing the strategy.
	 * @param telemetryManager The telemetry manager responsible for managing telemetry data (monitors and metrics).
	 * @param strategyTime     The execution time of the strategy, used for timing purpose.
	 * @param clientsExecutor  An executor service for handling client operations within the strategy.
	 * @param extensionManager The extension manager where all the required extensions are handled.
	 */
	protected AbstractAllAtOnceStrategy(
		@NonNull final TelemetryManager telemetryManager,
		final long strategyTime,
		@NonNull final ClientsExecutor clientsExecutor,
		@NonNull final ExtensionManager extensionManager
	) {
		super(telemetryManager, strategyTime, clientsExecutor, extensionManager);
	}

	/**
	 * This method processes each connector
	 *
	 * @param currentConnector The current connector
	 * @param hostname		   The host name
	 * @param runStartTime     The {@link System#nanoTime()} at which this strategy run started, used to check the strategy timeout
	 */
	private void process(final Connector currentConnector, final String hostname, final long runStartTime) {
		// Check whether the strategy job name matches at least one of the monitor jobs names of the current connector
		final boolean connectorHasExpectedJobTypes = hasExpectedJobTypes(currentConnector, getJobName());
		// If the connector doesn't define any monitor job that matches the given strategy job name, log a message then exit the current discovery or simple operation
		if (!connectorHasExpectedJobTypes) {
			log.debug("Connector doesn't define any monitor job of type {}.", getJobName());
			return;
		}
		if (!validateConnectorDetectionCriteria(currentConnector, hostname, getJobName())) {
			log.error(
				"Hostname {} - The connector {} no longer matches the host after {} consecutive detection failures." +
					" Stopping the connector's {} job.",
				hostname,
				currentConnector.getCompiledFilename(),
				MAX_CONSECUTIVE_DETECTION_FAILURES,
				getJobName()
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

		// Sort the connector monitor jobs according to the priority map
		final Map<String, MonitorJob> connectorMonitorJobs = currentConnector
			.getMonitors()
			.entrySet()
			.stream()
			.sorted(
				Comparator.comparing(entry ->
					MONITOR_JOBS_PRIORITY.containsKey(entry.getKey())
						? MONITOR_JOBS_PRIORITY.get(entry.getKey())
						: MONITOR_JOBS_PRIORITY.get(OTHER_MONITOR_JOB_TYPES)
				)
			)
			.collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (oldValue, _) -> oldValue, LinkedHashMap::new));

		final Map<String, MonitorJob> sequentialMonitorJobs = connectorMonitorJobs
			.entrySet()
			.stream()
			.filter(entry -> MONITOR_JOBS_PRIORITY.containsKey(entry.getKey()))
			.collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (oldValue, _) -> oldValue, LinkedHashMap::new));

		final Map<String, MonitorJob> otherMonitorJobs = connectorMonitorJobs
			.entrySet()
			.stream()
			.filter(entry -> !MONITOR_JOBS_PRIORITY.containsKey(entry.getKey()))
			.collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (oldValue, _) -> oldValue, LinkedHashMap::new));

		// Set when this run stops waiting for its thread pool: the jobs still running are abandoned
		boolean poolAbandoned = false;

		// The monitor types whose job ran a trusted pass, i.e. whose monitors that were not rediscovered can be removed
		final Set<String> trustedTypes = ConcurrentHashMap.newKeySet();
		final Consumer<Map.Entry<String, MonitorJob>> runJob = entry -> {
			if (processMonitorJob(currentConnector, hostname, entry)) {
				trustedTypes.add(entry.getKey());
			}
		};

		// Run monitor jobs defined in monitor jobs priority map (host, enclosure, blade, disk_controller and cpu)  in sequential mode
		sequentialMonitorJobs.entrySet().forEach(runJob);

		final boolean isSequential = telemetryManager.getHostConfiguration().isSequential();
		final String mode = isSequential ? "sequential" : "parallel";

		log.info(
			"Hostname {} - Running {} in {} mode. Connector: {}.",
			hostname,
			getJobName(),
			mode,
			currentConnector.getConnectorIdentity().getCompiledFilename()
		);

		// If monitor jobs execution is set to "sequential", execute monitor jobs one by one
		if (isSequential) {
			otherMonitorJobs.entrySet().forEach(runJob);
		} else {
			// Execute monitor jobs in parallel
			// Create a thread pool with a fixed number of threads
			final ExecutorService threadsPool = Executors.newFixedThreadPool(
				Math.max(1, Math.min(MAX_THREADS_COUNT, otherMonitorJobs.size()))
			);

			otherMonitorJobs.entrySet().forEach(entry -> threadsPool.execute(() -> runJob.accept(entry)));

			// Two-phase shutdown: first graceful, then forced
			threadsPool.shutdown();

			try {
				// Blocks until all tasks have completed execution after a shutdown request
				if (!threadsPool.awaitTermination(THREAD_TIMEOUT, TimeUnit.SECONDS)) {
					log.warn(
						"Hostname {} - Thread pool did not terminate within {} seconds. Forcing shutdown.",
						hostname,
						THREAD_TIMEOUT
					);
					poolAbandoned = true;
					threadsPool.shutdownNow();
				}
			} catch (Exception e) {
				poolAbandoned = true;
				threadsPool.shutdownNow();
				if (e instanceof InterruptedException) {
					Thread.currentThread().interrupt();
				}
				log.debug("Hostname {} - Waiting for threads' termination aborted with an error.", hostname, e);
			}
		}

		// An abandoned run (thread pool or strategy timeout) must not remove monitors: its jobs may have been cut short
		if (!poolAbandoned && isWithinStrategyTimeout(runStartTime)) {
			removeMonitorsNotRediscovered(currentConnector, trustedTypes, hostname);
		}

		// Run AfterAllStrategy that executes afterAll sources
		final AfterAllStrategy afterAllStrategy = AfterAllStrategy.builder()
			.clientsExecutor(clientsExecutor)
			.strategyTime(strategyTime)
			.telemetryManager(telemetryManager)
			.connector(currentConnector)
			.extensionManager(extensionManager)
			.build();

		afterAllStrategy.run();
	}

	/**
	 * This method processes a monitor job
	 *
	 * @param currentConnector The connector defining the monitor job
	 * @param hostname         The host name of the monitored resource
	 * @param monitorJobEntry  The monitor type and its monitor job
	 * @return {@code true} when the job ran a trusted pass: every source answered, at least one monitor was mapped and the
	 *         job has no incremental source
	 */
	private boolean processMonitorJob(
		final Connector currentConnector,
		final String hostname,
		final Map.Entry<String, MonitorJob> monitorJobEntry
	) {
		final long jobStartTime = System.currentTimeMillis();

		final MonitorJob monitorJob = monitorJobEntry.getValue();

		// Get the monitor task
		AbstractMonitorTask monitorTask = retrieveTask(monitorJob);

		if (monitorTask == null) {
			return false;
		}

		final String monitorType = monitorJobEntry.getKey();

		if (isMonitorFiltered(monitorType)) {
			return false;
		}

		final JobInfo jobInfo = JobInfo.builder()
			.hostname(hostname)
			.connectorId(currentConnector.getCompiledFilename())
			.jobName(getJobName())
			.monitorType(monitorType)
			.build();

		// Build the ordered sources
		final OrderedSources orderedSources = OrderedSources.builder()
			.sources(
				monitorTask.getSources(),
				monitorTask.getExecutionOrder().stream().collect(Collectors.toList()), // NOSONAR
				monitorTask.getSourceDep(),
				jobInfo
			)
			.build();

		// Create the sources and the computes for a connector
		final boolean sourcesAnswered = processSourcesAndComputes(orderedSources.getSources(), jobInfo);

		// Create the monitors
		final Mapping mapping = monitorTask.getMapping();

		// Only discovery metrics are flagged so that PrepareCollectStrategy refreshes their collect time on each
		// collect cycle, as they are never re-collected. Simple tasks run on every collect cycle, so their metrics
		// must not be flagged, otherwise a metric that is no longer collected would be exported with a stale value.
		final boolean isDiscovery = monitorTask instanceof Discovery;

		final boolean mapped = processSameTypeMonitors(
			currentConnector,
			mapping,
			monitorType,
			hostname,
			monitorJob,
			isDiscovery
		);
		final long jobEndTime = System.currentTimeMillis();
		// Set the job duration metric in the host monitor
		setJobDurationMetric(getJobName(), monitorType, currentConnector.getCompiledFilename(), jobStartTime, jobEndTime);

		return sourcesAnswered && mapped && !hasIncrementalSource(currentConnector, monitorTask);
	}

	/**
	 * Whether the monitors of the given task come from an incremental source: one of the task's sources, or a source
	 * reached, directly or not, through the mapping source and the source references (another job, beforeAll).
	 * eventLog and file (LOG mode) sources only return the entries added since the previous poll: a missing row does
	 * not mean a missing entity.
	 *
	 * @param connector   The connector defining the task
	 * @param monitorTask The monitor task defining the sources and the mapping
	 * @return {@code true} if one of the sources the task's monitors come from is incremental
	 */
	private static boolean hasIncrementalSource(final Connector connector, final AbstractMonitorTask monitorTask) {
		final Map<String, Source> sourcesByKey = getSourcesByKey(connector);
		final Deque<Source> pending = new ArrayDeque<>(monitorTask.getSources().values());
		if (monitorTask.getMapping() != null) {
			addReferencedSources(monitorTask.getMapping().getSource(), sourcesByKey, pending);
		}

		final Set<Source> visited = Collections.newSetFromMap(new IdentityHashMap<>());
		while (!pending.isEmpty()) {
			final Source source = pending.pop();
			if (!visited.add(source)) {
				continue;
			}
			if (isIncremental(source)) {
				return true;
			}
			source.getReferences().forEach(reference -> addReferencedSources(reference, sourcesByKey, pending));
		}
		return false;
	}

	/**
	 * Whether the given source is incremental: an eventLog source, or a file source in LOG mode.
	 *
	 * @param source The source to check
	 * @return {@code true} if the source only returns the entries added since the previous poll
	 */
	private static boolean isIncremental(final Source source) {
		if (source instanceof FileSource fileSource) {
			return fileSource.getMode() == FileSourceProcessingMode.LOG;
		}
		return source instanceof EventLogSource;
	}

	/**
	 * Add the sources referenced (<code>${source::...}</code>) in the given value to the given queue.
	 *
	 * @param value        A mapping source or a source reference value, possibly {@code null}
	 * @param sourcesByKey The sources of the connector, by source key
	 * @param pending      The queue of sources to examine
	 */
	private static void addReferencedSources(
		final String value,
		final Map<String, Source> sourcesByKey,
		final Deque<Source> pending
	) {
		if (value == null) {
			return;
		}
		final Matcher matcher = SOURCE_REF_PATTERN.matcher(value);
		while (matcher.find()) {
			final Source source = sourcesByKey.get(matcher.group());
			if (source != null) {
				pending.push(source);
			}
		}
	}

	/**
	 * Get all the sources of the given connector (beforeAll, afterAll and the tasks of every monitor job), by source key.
	 *
	 * @param connector The connector defining the sources
	 * @return a map of source key to source
	 */
	private static Map<String, Source> getSourcesByKey(final Connector connector) {
		final Map<String, Source> sourcesByKey = new HashMap<>();
		final Stream<Map<String, Source>> jobSources = connector
			.getMonitors()
			.values()
			.stream()
			.flatMap(job -> {
				if (job instanceof SimpleMonitorJob simpleMonitorJob) {
					return Stream.<AbstractMonitorTask>of(simpleMonitorJob.getSimple());
				}
				if (job instanceof StandardMonitorJob standardMonitorJob) {
					return Stream.<AbstractMonitorTask>of(standardMonitorJob.getDiscovery(), standardMonitorJob.getCollect());
				}
				return Stream.<AbstractMonitorTask>empty();
			})
			.filter(Objects::nonNull)
			.map(AbstractMonitorTask::getSources);
		Stream.concat(Stream.of(connector.getBeforeAll(), connector.getAfterAll()), jobSources)
			.filter(Objects::nonNull)
			.flatMap(sources -> sources.values().stream())
			.filter(source -> source.getKey() != null)
			.forEach(source -> sourcesByKey.put(source.getKey(), source));
		return sourcesByKey;
	}

	/**
	 * This method processes same type monitors
	 *
	 * @param connector   The connector instance defining the ID and also used to collect monitor's metrics
	 * @param mapping     The mapping instance defining the attributes, metrics, conditional collection, legacy text parameters and resource
	 * @param monitorType The type of the monitor we currently process
	 * @param hostname    The host name of the monitored resource
	 * @param monitorJob  The monitor job defining the discovery or simple task
	 * @param isDiscovery Whether the task is a discovery, in which case the collected metrics are flagged for a collect time reset
	 * @return {@code true} if at least one monitor was created or updated
	 */
	private boolean processSameTypeMonitors(
		final Connector connector,
		final Mapping mapping,
		final String monitorType,
		final String hostname,
		final MonitorJob monitorJob,
		final boolean isDiscovery
	) {
		final String connectorId = connector.getCompiledFilename();

		// Check the source, so that, we can create the monitor later
		final String source = mapping.getSource();
		if (source == null) {
			log.warn(
				"Hostname {} - No instance tables found with {} during the {} job for the connector {}. Skip processing.",
				hostname,
				monitorType,
				getJobName(),
				connectorId
			);
			return false;
		}

		// Checking for defined attributes to create monitors based on them
		// If no attributes are found, skip processing
		// This may indicate a job created to consolidate source values
		if (mapping.getAttributes() == null) {
			log.info(
				"Hostname {} - No mapping attributes defined with {} during the {} job for the connector {}. Skip processing.",
				hostname,
				monitorType,
				getJobName(),
				connectorId
			);
			return false;
		}

		// Call lookupSourceTable to find the source table
		final Optional<SourceTable> maybeSourceTable = SourceTable.lookupSourceTable(source, connectorId, telemetryManager);

		if (maybeSourceTable.isEmpty()) {
			log.warn(
				"Hostname {} - The source table {} is not found during the {} job for the connector {}. Skip processing.",
				hostname,
				source,
				getJobName(),
				connectorId
			);
			return false;
		}

		// If the source table is not empty, loop over the source table rows
		final List<List<String>> table = maybeSourceTable.get().getTable();

		log.debug(
			"Hostname {} - Start {} {} mapping with source {}, attributes {}, metrics {}, conditional collection {} and legacy text parameters {}" +
				". Connector ID: {}.",
			hostname,
			monitorType,
			getJobName(),
			mapping.getSource(),
			mapping.getAttributes(),
			mapping.getMetrics(),
			mapping.getConditionalCollection(),
			mapping.getLegacyTextParameters(),
			connectorId
		);

		int mappedMonitors = 0;
		for (int i = 0; i < table.size(); i++) {
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
						.jobName(getJobName())
						.build()
				)
				.collectTime(strategyTime)
				.row(row)
				.indexCounter(i + 1)
				.build();

			// Use the mapping processor to extract attributes and resource
			final Map<String, String> noContextAttributeInterpretedValues =
				mappingProcessor.interpretNonContextMappingAttributes();

			// Initialize a monitor factory with the previously created attributes and resources

			// Get the identifying attribute keys
			final Set<String> identifyingAttributeKeys = monitorJob.getKeys();

			final MonitorFactory monitorFactory = MonitorFactory.builder()
				.monitorType(monitorType)
				.telemetryManager(telemetryManager)
				.attributes(noContextAttributeInterpretedValues)
				.connectorId(connectorId)
				.discoveryTime(strategyTime)
				.keys(identifyingAttributeKeys)
				.build();

			// The identifying attributes should be available
			if (!hasAllIdentifyingAttributes(identifyingAttributeKeys, noContextAttributeInterpretedValues)) {
				log.info(
					"Hostname {} - No identifying attributes {} found with {} during the {} job for the connector {}. Processed row: {}. The monitor will not be created.",
					hostname,
					identifyingAttributeKeys,
					monitorType,
					getJobName(),
					connectorId,
					row
				);
				continue;
			}

			// Create or update the monitor
			final Monitor monitor = monitorFactory.createOrUpdateMonitor();
			mappedMonitors++;

			final Map<String, String> contextAttributes = mappingProcessor.interpretContextMappingAttributes(monitor);

			// Update the monitor's attributes by adding the context attributes
			monitor.addAttributes(contextAttributes);

			// Collect conditional collection
			monitor.addConditionalCollection(mappingProcessor.interpretNonContextMappingConditionalCollection());
			monitor.addConditionalCollection(mappingProcessor.interpretContextMappingConditionalCollection(monitor));

			// Collect metrics
			final Map<String, String> metrics = mappingProcessor.interpretNonContextMappingMetrics();

			metrics.putAll(mappingProcessor.interpretContextMappingMetrics(monitor));

			final MetricFactory metricFactory = new MetricFactory(hostname, telemetryManager.getConnectorStore());

			metricFactory.collectMonitorMetrics(
				monitorType,
				connector,
				monitor,
				connectorId,
				metrics,
				strategyTime,
				isDiscovery
			);

			// Collect legacy parameters
			monitor.addLegacyParameters(mappingProcessor.interpretNonContextMappingLegacyTextParameters());
			monitor.addLegacyParameters(mappingProcessor.interpretContextMappingLegacyTextParameters(monitor));
		}

		return mappedMonitors > 0;
	}

	/**
	 * This method checks if the identifying attributes are available
	 *
	 * @param identifyingAttributeKeys The identifying attribute keys
	 * @param attributes               The attribute interpreted values
	 * @return True if the identifying attributes are available, false otherwise
	 */
	public boolean hasAllIdentifyingAttributes(
		final Set<String> identifyingAttributeKeys,
		final Map<String, String> attributes
	) {
		// All the identifying attributes should be available
		return identifyingAttributeKeys.stream().noneMatch(key -> attributes.get(key) == null);
	}

	/**
	 * This is the main method. It runs the all the job operations
	 */
	public void run() {
		// Capture the start of this strategy run, used to check the strategy timeout before removing monitors
		final long runStartTime = System.nanoTime();

		// Get the host name from telemetry manager
		final String hostname = telemetryManager.getHostname();

		// Get the endpoint host monitor
		final Monitor endpointHost = telemetryManager.getEndpointHostMonitor();
		if (endpointHost == null) {
			log.info("Hostname {} - No endpoint host found during {} strategy.", hostname, getJobName());
		} else {
			endpointHost.setDiscoveryTime(strategyTime);
		}

		//Retrieve connector Monitor instances from TelemetryManager
		final Map<String, Monitor> connectorMonitors = telemetryManager
			.getMonitors()
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
		final List<Connector> connectorsWithMonitorJobs = detectedConnectors
			.stream()
			.filter(connector -> !connector.getMonitors().isEmpty())
			.collect(Collectors.toList()); //NOSONAR

		// Sort connectors by monitor job type: first put hosts then enclosures. If two connectors have the same type of monitor job, sort them by name
		final List<Connector> sortedConnectors = connectorsWithMonitorJobs
			.stream()
			.sorted(new ConnectorMonitorTypeComparator())
			.collect(Collectors.toList()); //NOSONAR

		// Process each connector
		sortedConnectors.forEach(connector -> process(connector, hostname, runStartTime));

		// Collect the metricshub.host.configured metric
		collectHostConfigured(hostname);

		// Collect per-host request metrics (completed/timeout by operation type)
		collectRequestMetrics(hostname);
	}

	/**
	 * Whether this strategy run is still within its timeout. {@link ContextExecutor} interrupts and abandons a strategy
	 * thread after {@link #getStrategyTimeout()} seconds. An abandoned thread must not remove monitors: its jobs may have
	 * been cut short and it would race with the next run of the same jobs. RetryOperation clears the
	 * interrupt flag before it retries, hence the elapsed time, measured with {@link System#nanoTime()} like the timeout
	 * of {@link ContextExecutor} so that a clock step does not change it.
	 *
	 * @param runStartTime The {@link System#nanoTime()} at which this strategy run started
	 * @return {@code true} if the thread is not interrupted and the strategy timeout has not elapsed
	 */
	private boolean isWithinStrategyTimeout(final long runStartTime) {
		if (Thread.currentThread().isInterrupted()) {
			return false;
		}
		return System.nanoTime() - runStartTime < TimeUnit.SECONDS.toNanos(getStrategyTimeout());
	}

	/**
	 * Removes the monitors that the trusted jobs of the given connector did not rediscover during this strategy run.
	 * Nothing is removed when the connector status is not OK. The connector monitors and the hardware missing device
	 * detection types are never removed.
	 *
	 * @param connector    The connector whose jobs ran
	 * @param trustedTypes The monitor types whose job ran a trusted pass
	 * @param hostname     The host name of the monitored resource
	 */
	private void removeMonitorsNotRediscovered(
		final Connector connector,
		final Set<String> trustedTypes,
		final String hostname
	) {
		final String connectorId = connector.getCompiledFilename();
		if (!telemetryManager.getHostProperties().getConnectorNamespace(connectorId).isStatusOk()) {
			return;
		}
		trustedTypes
			.stream()
			.filter(type -> !KnownMonitorType.CONNECTOR.getKey().equals(type))
			// Missing devices are kept and reported by HardwarePostDiscoveryStrategy (present = 0)
			.filter(type -> !StrategyHelper.isMissingDeviceDetectionCandidate(connector, type))
			.forEach(type -> {
				// removalDelay (seconds, monitor level): a monitor is removed once not rediscovered for longer than the delay
				final long removalDelay = Optional.ofNullable(connector.getMonitors().get(type))
					.map(MonitorJob::getRemovalDelay)
					.orElse(0L);
				final int removed = telemetryManager.removeMonitorsNotDiscoveredAt(
					type,
					connectorId,
					strategyTime - TimeUnit.SECONDS.toMillis(removalDelay)
				);
				if (removed > 0) {
					log.info(
						"Hostname {} - Removed {} {} monitor(s) no longer discovered by connector {} during the {} job.",
						hostname,
						removed,
						type,
						connectorId,
						getJobName()
					);
				}
			});
	}

	/**
	 * Get the name of the job
	 * @return String value
	 */
	protected abstract String getJobName();

	/**
	 * Retrieve the task of the given {@link MonitorJob}. E.g. {@link Discovery}
	 * or {@link Simple}
	 *
	 * @param monitorJob
	 * @return The {@link AbstractMonitorTask} implementation
	 */
	protected abstract AbstractMonitorTask retrieveTask(MonitorJob monitorJob);
}
