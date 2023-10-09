package com.sentrysoftware.matrix.strategy;

import static com.sentrysoftware.matrix.common.helpers.KnownMonitorType.HOST;
import static com.sentrysoftware.matrix.common.helpers.MatrixConstants.IS_ENDPOINT;
import static com.sentrysoftware.matrix.common.helpers.MatrixConstants.MAX_THREADS_COUNT;
import static com.sentrysoftware.matrix.common.helpers.MatrixConstants.MONITOR_ATTRIBUTE_ID;
import static com.sentrysoftware.matrix.common.helpers.MatrixConstants.MONITOR_JOBS_PRIORITY;
import static com.sentrysoftware.matrix.common.helpers.MatrixConstants.OTHER_MONITOR_JOB_TYPES;
import static com.sentrysoftware.matrix.common.helpers.MatrixConstants.THREAD_TIMEOUT;

import com.sentrysoftware.matrix.common.ConnectorMonitorTypeComparator;
import com.sentrysoftware.matrix.common.JobInfo;
import com.sentrysoftware.matrix.common.helpers.KnownMonitorType;
import com.sentrysoftware.matrix.connector.model.Connector;
import com.sentrysoftware.matrix.connector.model.ConnectorStore;
import com.sentrysoftware.matrix.connector.model.monitor.MonitorJob;
import com.sentrysoftware.matrix.connector.model.monitor.task.AbstractMonitorTask;
import com.sentrysoftware.matrix.connector.model.monitor.task.Discovery;
import com.sentrysoftware.matrix.connector.model.monitor.task.Mapping;
import com.sentrysoftware.matrix.connector.model.monitor.task.Simple;
import com.sentrysoftware.matrix.matsya.MatsyaClientsExecutor;
import com.sentrysoftware.matrix.strategy.source.OrderedSources;
import com.sentrysoftware.matrix.strategy.source.SourceTable;
import com.sentrysoftware.matrix.strategy.utils.MappingProcessor;
import com.sentrysoftware.matrix.telemetry.MetricFactory;
import com.sentrysoftware.matrix.telemetry.Monitor;
import com.sentrysoftware.matrix.telemetry.MonitorFactory;
import com.sentrysoftware.matrix.telemetry.Resource;
import com.sentrysoftware.matrix.telemetry.TelemetryManager;
import java.util.Collection;
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
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public abstract class AbstractAllAtOnceStrategy extends AbstractStrategy {

	AbstractAllAtOnceStrategy(
		@NonNull final TelemetryManager telemetryManager,
		final long strategyTime,
		@NonNull final MatsyaClientsExecutor matsyaClientsExecutor
	) {
		super(telemetryManager, strategyTime, matsyaClientsExecutor);
	}

	/**
	 * This method processes each connector
	 *
	 * @param currentConnector
	 * @param hostname
	 */
	private void process(final Connector currentConnector, final String hostname) {
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
			.collect(
				Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (oldValue, newValue) -> oldValue, LinkedHashMap::new)
			);

		final Map<String, MonitorJob> sequentialMonitorJobs = connectorMonitorJobs
			.entrySet()
			.stream()
			.filter(entry -> MONITOR_JOBS_PRIORITY.containsKey(entry.getKey()))
			.collect(
				Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (oldValue, newValue) -> oldValue, LinkedHashMap::new)
			);

		final Map<String, MonitorJob> otherMonitorJobs = connectorMonitorJobs
			.entrySet()
			.stream()
			.filter(entry -> !MONITOR_JOBS_PRIORITY.containsKey(entry.getKey()))
			.collect(
				Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (oldValue, newValue) -> oldValue, LinkedHashMap::new)
			);

		// Run monitor jobs defined in monitor jobs priority map (host, enclosure, blade, disk_controller and cpu)  in sequential mode
		sequentialMonitorJobs.entrySet().forEach(entry -> processMonitorJob(currentConnector, hostname, entry));

		// If monitor jobs execution is set to "sequential", execute monitor jobs one by one
		if (telemetryManager.getHostConfiguration().isSequential()) {
			otherMonitorJobs.entrySet().forEach(entry -> processMonitorJob(currentConnector, hostname, entry));
		} else {
			// Execute monitor jobs in parallel
			log.info(
				"Hostname {} - Running {} in parallel mode. Connector: {}.",
				hostname,
				getJobName(),
				currentConnector.getConnectorIdentity().getCompiledFilename()
			);

			final ExecutorService threadsPool = Executors.newFixedThreadPool(MAX_THREADS_COUNT);

			otherMonitorJobs
				.entrySet()
				.forEach(entry -> threadsPool.execute(() -> processMonitorJob(currentConnector, hostname, entry)));

			// Order the shutdown
			threadsPool.shutdown();

			try {
				// Blocks until all tasks have completed execution after a shutdown request
				threadsPool.awaitTermination(THREAD_TIMEOUT, TimeUnit.SECONDS);
			} catch (Exception e) {
				if (e instanceof InterruptedException) {
					Thread.currentThread().interrupt();
				}
				log.debug("Hostname {} - Waiting for threads' termination aborted with an error.", hostname, e);
			}
		}
	}

	/**
	 * This method processes a monitor job
	 *
	 * @param currentConnector
	 * @param hostname
	 * @param monitorJobEntry
	 */
	private void processMonitorJob(
		final Connector currentConnector,
		final String hostname,
		final Map.Entry<String, MonitorJob> monitorJobEntry
	) {
		final MonitorJob monitorJob = monitorJobEntry.getValue();

		// Get the monitor task
		AbstractMonitorTask monitorTask = retrieveTask(monitorJob);

		if (monitorTask == null) {
			return;
		}

		final String monitorType = monitorJobEntry.getKey();

		final JobInfo jobInfo = JobInfo
			.builder()
			.hostname(hostname)
			.connectorName(currentConnector.getCompiledFilename())
			.jobName(getJobName())
			.monitorType(monitorType)
			.build();

		// Build the ordered sources
		final OrderedSources orderedSources = OrderedSources
			.builder()
			.sources(
				monitorTask.getSources(),
				monitorTask.getExecutionOrder().stream().collect(Collectors.toList()), // NOSONAR
				monitorTask.getSourceDep(),
				jobInfo
			)
			.build();

		// Create the sources and the computes for a connector
		processSourcesAndComputes(orderedSources.getSources(), jobInfo);

		// Create the monitors
		final Mapping mapping = monitorTask.getMapping();

		processSameTypeMonitors(currentConnector, mapping, monitorType, hostname);
	}

	/**
	 * This method processes same type monitors
	 *
	 * @param connector
	 * @param mapping
	 * @param monitorType
	 * @param hostname
	 */
	private void processSameTypeMonitors(
		final Connector connector,
		final Mapping mapping,
		final String monitorType,
		final String hostname
	) {
		final String connectorId = connector.getConnectorIdentity().getCompiledFilename();

		// Check the source, so that, we can create the monitor later
		final String source = mapping.getSource();
		if (source == null) {
			log.warn(
				"Hostname {} - No instance tables found with {} during the {} for the connector {}.",
				hostname,
				monitorType,
				getJobName(),
				connectorId
			);
			return;
		}

		// Call lookupSourceTable to find the source table
		final Optional<SourceTable> maybeSourceTable = SourceTable.lookupSourceTable(source, connectorId, telemetryManager);

		if (maybeSourceTable.isEmpty()) {
			log.warn(
				"Hostname {} - The source table {} is not found during the {} for the connector {}.",
				hostname,
				source,
				getJobName(),
				connectorId
			);
			return;
		}

		// If the source table is not empty, loop over the source table rows
		final SourceTable sourceTable = maybeSourceTable.get();

		for (final List<String> row : sourceTable.getTable()) {
			// Init mapping processor
			final MappingProcessor mappingProcessor = MappingProcessor
				.builder()
				.telemetryManager(telemetryManager)
				.mapping(mapping)
				.jobInfo(
					JobInfo
						.builder()
						.connectorName(connectorId)
						.hostname(hostname)
						.monitorType(monitorType)
						.jobName(getJobName())
						.build()
				)
				.collectTime(strategyTime)
				.row(row)
				.build();

			// Use the mapping processor to extract attributes and resource
			final Map<String, String> noContextAttributeInterpretedValues = mappingProcessor.interpretNonContextMappingAttributes();

			final Resource resource = mappingProcessor.interpretMappingResource();

			// Init a monitor factory with the previously created attributes and resources

			final MonitorFactory monitorFactory = MonitorFactory
				.builder()
				.monitorType(monitorType)
				.telemetryManager(telemetryManager)
				.attributes(noContextAttributeInterpretedValues)
				.resource(resource)
				.connectorId(connectorId)
				.discoveryTime(strategyTime)
				.build();

			// Create or update the monitor
			final Monitor monitor = monitorFactory.createOrUpdateMonitor();

			final Map<String, String> contextAttributes = mappingProcessor.interpretContextMappingAttributes(monitor);

			// Update the monitor's attributes by adding the context attributes
			monitor.addAttributes(contextAttributes);

			// Collect conditional collection
			monitor.addConditionalCollection(mappingProcessor.interpretNonContextMappingConditionalCollection());
			monitor.addConditionalCollection(mappingProcessor.interpretContextMappingConditionalCollection(monitor));

			// Collect metrics
			final Map<String, String> metrics = mappingProcessor.interpretNonContextMappingMetrics();

			metrics.putAll(mappingProcessor.interpretContextMappingMetrics(monitor));

			final MetricFactory metricFactory = new MetricFactory(hostname);

			metricFactory.collectMonitorMetrics(monitorType, connector, monitor, connectorId, metrics, strategyTime, true);

			// Collect legacy parameters
			monitor.addLegacyParameters(mappingProcessor.interpretNonContextMappingLegacyTextParameters());
			monitor.addLegacyParameters(mappingProcessor.interpretContextMappingLegacyTextParameters(monitor));
		}
	}

	/**
	 * This is the main method. It runs the all the job operations
	 */
	public void run() {
		// Get the host name from telemetry manager
		final String hostname = telemetryManager.getHostConfiguration().getHostname();

		// Get host monitors
		final Map<String, Monitor> hostMonitors = telemetryManager.getMonitors().get(HOST.getKey());

		if (hostMonitors == null) {
			log.error("Hostname {} - No host found. Stopping {} strategy.", hostname, getJobName());
			return;
		}

		// Get the endpoint host
		final Monitor host = hostMonitors
			.values()
			.stream()
			.filter(hostMonitor -> "true".equals(hostMonitor.getAttributes().get(IS_ENDPOINT)))
			.findFirst()
			.orElse(null);

		if (host == null) {
			log.error("Hostname {} - No host found. Stopping {} strategy.", hostname, getJobName());
			return;
		}

		host.setDiscoveryTime(strategyTime);

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

		//Filter connectors by their connector_id value (compiled file name) from TelemetryManager's connector store and create a list of Connector instances.
		final ConnectorStore connectorStore = telemetryManager.getConnectorStore();

		// Retrieve the detected connectors file names
		final Set<String> detectedConnectorFileNames = connectorMonitors
			.values()
			.stream()
			.map(monitor -> monitor.getAttributes().get(MONITOR_ATTRIBUTE_ID))
			.collect(Collectors.toSet());

		// Keep only detected/selected connectors, in the store they are indexed by the compiled file name
		// Build the list of the connectors
		final List<Connector> detectedConnectors = connectorStore
			.getStore()
			.entrySet()
			.stream()
			.filter(entry -> detectedConnectorFileNames.contains(entry.getKey()))
			.map(Map.Entry::getValue)
			.collect(Collectors.toList()); //NOSONAR

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
		sortedConnectors.forEach(connector -> process(connector, hostname));
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

	@Override
	public void prepare() {
		// Not implemented
	}

	@Override
	public void post() {
		telemetryManager
			.getMonitors()
			.values()
			.stream()
			.map(Map::values)
			.flatMap(Collection::stream)
			.filter(monitor -> strategyTime != monitor.getDiscoveryTime())
			.forEach(monitor -> monitor.setAsMissing(telemetryManager.getHostname()));
	}
}
