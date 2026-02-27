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

import static org.metricshub.engine.common.helpers.MetricsHubConstants.CONNECTOR_STATUS_METRIC_KEY;
import static org.metricshub.engine.common.helpers.MetricsHubConstants.LOG_COMPUTE_KEY_SUFFIX_TEMPLATE;
import static org.metricshub.engine.common.helpers.MetricsHubConstants.STATE_SET_METRIC_FAILED;
import static org.metricshub.engine.common.helpers.MetricsHubConstants.STATE_SET_METRIC_OK;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.metricshub.engine.client.ClientsExecutor;
import org.metricshub.engine.common.JobInfo;
import org.metricshub.engine.common.exception.RetryableException;
import org.metricshub.engine.common.helpers.KnownMonitorType;
import org.metricshub.engine.common.helpers.TextTableHelper;
import org.metricshub.engine.configuration.HostConfiguration;
import org.metricshub.engine.connector.model.Connector;
import org.metricshub.engine.connector.model.monitor.SimpleMonitorJob;
import org.metricshub.engine.connector.model.monitor.StandardMonitorJob;
import org.metricshub.engine.connector.model.monitor.task.source.Source;
import org.metricshub.engine.connector.model.monitor.task.source.compute.Compute;
import org.metricshub.engine.extension.ExtensionManager;
import org.metricshub.engine.strategy.detection.ConnectorSelection;
import org.metricshub.engine.strategy.detection.ConnectorTestResult;
import org.metricshub.engine.strategy.detection.CriterionTestResult;
import org.metricshub.engine.strategy.source.ISourceProcessor;
import org.metricshub.engine.strategy.source.SourceProcessor;
import org.metricshub.engine.strategy.source.SourceTable;
import org.metricshub.engine.strategy.source.SourceUpdaterProcessor;
import org.metricshub.engine.strategy.source.compute.ComputeProcessor;
import org.metricshub.engine.strategy.source.compute.ComputeUpdaterProcessor;
import org.metricshub.engine.strategy.utils.ForceSerializationHelper;
import org.metricshub.engine.strategy.utils.RetryOperation;
import org.metricshub.engine.telemetry.ConnectorNamespace;
import org.metricshub.engine.telemetry.MetricFactory;
import org.metricshub.engine.telemetry.Monitor;
import org.metricshub.engine.telemetry.TelemetryManager;

/**
 * Abstract class representing a strategy for handling connectors and their sources and computes.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Slf4j
public abstract class AbstractStrategy implements IStrategy {

	public static final String HOST_CONFIGURED_METRIC_NAME = "metricshub.host.configured";

	@NonNull
	protected TelemetryManager telemetryManager;

	@NonNull
	protected Long strategyTime;

	@NonNull
	protected ClientsExecutor clientsExecutor;

	@NonNull
	protected ExtensionManager extensionManager;

	private static final String COMPUTE = "compute";
	private static final String SOURCE = "source";

	/**
	 * Format for string value like: <em>connector_connector-id</em>
	 */
	public static final String CONNECTOR_ID_FORMAT = "%s_%s";

	/**
	 * Execute each source in the given list of sources then for each source table apply all the attached computes.
	 * When the {@link SourceTable} is ready it is added to {@link TelemetryManager}
	 *
	 * @param sources The {@link List} of {@link Source} instances we wish to execute
	 * @param jobInfo Information about the job such as hostname, monitorType, job name and connectorName.
	 */
	protected void processSourcesAndComputes(final List<Source> sources, final JobInfo jobInfo) {
		processSourcesAndComputes(sources, null, jobInfo);
	}

	/**
	 * Execute each source in the given list of sources then for each source table apply all the attached computes.
	 * When the {@link SourceTable} is ready it is added to {@link TelemetryManager}
	 *
	 * @param sources    The {@link List} of {@link Source} instances we wish to execute
	 * @param attributes Key-value pairs of the monitor's attributes used in the mono instance processing
	 * @param jobInfo    Information about the job such as hostname, monitorType, job name and connectorName.
	 */
	protected void processSourcesAndComputes(
		final List<Source> sources,
		final Map<String, String> attributes,
		final JobInfo jobInfo
	) {
		final String connectorId = jobInfo.getConnectorId();
		final String monitorType = jobInfo.getMonitorType();
		final String hostname = jobInfo.getHostname();

		if (sources == null || sources.isEmpty()) {
			log.debug(
				"Hostname {} - No sources found from connector {} with monitor {}.",
				hostname,
				connectorId,
				monitorType
			);
			return;
		}

		// Loop over all the sources and accept the SourceProcessor which is going to
		// process the source
		for (final Source source : sources) {
			final String sourceKey = source.getKey();

			logBeginOperation(SOURCE, source, sourceKey, connectorId, hostname);

			final SourceTable previousSourceTable = telemetryManager
				.getHostProperties()
				.getConnectorNamespace(connectorId)
				.getSourceTable(sourceKey);

			// Execute the source and retry the operation
			// in case the source fails but the previous source table didn't fail
			SourceTable sourceTable = RetryOperation
				.<SourceTable>builder()
				.withDefaultValue(SourceTable.empty())
				.withMaxRetries(1)
				.withWaitStrategy(telemetryManager.getHostConfiguration().getRetryDelay())
				.withDescription(String.format("%s [%s]", SOURCE, sourceKey))
				.withHostname(hostname)
				.build()
				.run(() -> runSource(connectorId, attributes, source, previousSourceTable));

			final boolean isNullSourceTable = sourceTable == null;
			if (isNullSourceTable || sourceTable.isEmpty()) {
				log.warn(
					"Hostname {} - Received {} source table for Source key {} - Connector {} - Monitor {}. The source table is set to empty.",
					hostname,
					isNullSourceTable ? "null" : "empty",
					sourceKey,
					connectorId,
					monitorType
				);
				// This ensures that the internal table (List<List<String>>) is not null and rawData integrity is maintained
				sourceTable =
					SourceTable
						.builder()
						.rawData(sourceTable != null ? sourceTable.getRawData() : null)
						.table(new ArrayList<>())
						.build();
			}

			// log the source table
			logSourceTable(SOURCE, source.getClass().getSimpleName(), sourceKey, connectorId, sourceTable, hostname);

			final List<Compute> computes = source.getComputes();

			// Add the source table and stop if no compute is found
			if (computes == null || computes.isEmpty()) {
				telemetryManager.getHostProperties().getConnectorNamespace(connectorId).addSourceTable(sourceKey, sourceTable);
				continue;
			}

			final ComputeProcessor computeProcessor = ComputeProcessor
				.builder()
				.sourceKey(sourceKey)
				.sourceTable(sourceTable)
				.connectorId(connectorId)
				.hostname(hostname)
				.clientsExecutor(clientsExecutor)
				.telemetryManager(telemetryManager)
				.build();

			final ComputeUpdaterProcessor computeUpdaterProcessor = ComputeUpdaterProcessor
				.builder()
				.computeProcessor(computeProcessor)
				.attributes(attributes)
				.connectorId(connectorId)
				.telemetryManager(telemetryManager)
				.build();

			// Loop over the computes to process each compute
			for (int index = 0; index < computes.size(); index++) {
				final Compute compute = computes.get(index);
				computeProcessor.setIndex(index);

				final String computeKey = String.format(LOG_COMPUTE_KEY_SUFFIX_TEMPLATE, sourceKey, index);

				logBeginOperation(COMPUTE, compute, computeKey, connectorId, hostname);

				// process the compute
				compute.accept(computeUpdaterProcessor);

				// log the updated source table
				logSourceTable(
					COMPUTE,
					compute.getClass().getSimpleName(),
					computeKey,
					connectorId,
					computeProcessor.getSourceTable(),
					hostname
				);
			}

			telemetryManager
				.getHostProperties()
				.getConnectorNamespace(connectorId)
				.addSourceTable(sourceKey, computeProcessor.getSourceTable());
		}
	}

	/**
	 * Whether the given source table is empty or not
	 *
	 * @param sourceTable The result produced after executing a source
	 * @return boolean value
	 */
	private boolean isNullOrEmptySourceTable(final SourceTable sourceTable) {
		return sourceTable == null || sourceTable.isEmpty();
	}

	/**
	 * Execute the given source. If the source is marked as serializable
	 * (ForceSerialization) The execution will be performed through
	 * <code>forceSerialization(...)</code> method.
	 *
	 * @param connectorId         The connector compiled filename (identifier) we currently process
	 * @param attributes          Key-value pairs of the monitor's attributes used
	 *                            in the mono instance processing
	 * @param source              The source we want to run
	 * @param previousSourceTable The source result produced in the past
	 * @return new {@link SourceTable} instance
	 */
	private SourceTable runSource(
		final String connectorId,
		final Map<String, String> attributes,
		final Source source,
		final SourceTable previousSourceTable
	) {
		final ISourceProcessor sourceProcessor = SourceProcessor
			.builder()
			.connectorId(connectorId)
			.clientsExecutor(clientsExecutor)
			.telemetryManager(telemetryManager)
			.extensionManager(extensionManager)
			.build();

		final Supplier<SourceTable> executable = () ->
			source.accept(
				SourceUpdaterProcessor
					.builder()
					.connectorId(connectorId)
					.sourceProcessor(sourceProcessor)
					.telemetryManager(telemetryManager)
					.attributes(attributes)
					.extensionManager(extensionManager)
					.build()
			);

		// Process the source to get a source table

		final SourceTable sourceTable;

		if (source.isForceSerialization()) {
			sourceTable =
				ForceSerializationHelper.forceSerialization(
					executable,
					telemetryManager,
					connectorId,
					source,
					SOURCE,
					SourceTable.empty()
				);
		} else {
			sourceTable = executable.get();
		}

		// A retry must be attempted if this source has already produced results in the past
		if (!isNullOrEmptySourceTable(previousSourceTable) && isNullOrEmptySourceTable(sourceTable)) {
			throw new RetryableException();
		}

		return sourceTable;
	}

	/**
	 * Log a begin entry for the given source
	 *
	 * @param <T>
	 *
	 * @param operationTag  the tag of the operation. E.g. source or compute
	 * @param execution     the source or the compute we want to log
	 * @param executionKey  the source or the compute unique key
	 * @param connectorId   the connector identifier
	 * @param hostname      the hostname
	 */
	private static <T> void logBeginOperation(
		final String operationTag,
		final T execution,
		final String executionKey,
		final String connectorId,
		final String hostname
	) {
		if (!log.isInfoEnabled()) {
			return;
		}

		log.info(
			"Hostname {} - Begin {} [{} {}] for connector [{}]:\n{}\n",
			hostname,
			operationTag,
			execution.getClass().getSimpleName(),
			executionKey,
			connectorId,
			execution.toString()
		);
	}

	/**
	 * Log the {@link SourceTable} result.
	 *
	 * @param operationTag   the tag of the operation. E.g. source or compute
	 * @param executionClassName the source or the compute class name we want to log
	 * @param executionKey   the key of the source or the compute we want to log
	 * @param connectorId    the compiled file name of the connector (identifier)
	 * @param sourceTable    the source's result we wish to log
	 * @param hostname       the hostname of the source we wish to log
	 */
	static void logSourceTable(
		final String operationTag,
		final String executionClassName,
		final String executionKey,
		final String connectorId,
		final SourceTable sourceTable,
		final String hostname
	) {
		if (!log.isInfoEnabled()) {
			return;
		}

		// Is there any raw data to log?
		if (sourceTable.getRawData() != null && (sourceTable.getTable() == null || sourceTable.getTable().isEmpty())) {
			log.info(
				"Hostname {} - End of {} [{} {}] for connector [{}].\nRaw result:\n{}\n",
				hostname,
				operationTag,
				executionClassName,
				executionKey,
				connectorId,
				sourceTable.getRawData()
			);
			return;
		}

		if (sourceTable.getRawData() == null) {
			log.info(
				"Hostname {} - End of {} [{} {}] for connector [{}].\nTable result:\n{}\n",
				hostname,
				operationTag,
				executionClassName,
				executionKey,
				connectorId,
				TextTableHelper.generateTextTable(sourceTable.getTable())
			);
			return;
		}

		log.info(
			"Hostname {} - End of {} [{} {}] for connector [{}].\nRaw result:\n{}\nTable result:\n{}\n",
			hostname,
			operationTag,
			executionClassName,
			executionKey,
			connectorId,
			sourceTable.getRawData(),
			TextTableHelper.generateTextTable(sourceTable.getTable())
		);
	}

	@Override
	public long getStrategyTimeout() {
		return telemetryManager.getHostConfiguration().getStrategyTimeout();
	}

	/**
	 * Determines if the given strategy job name matches any monitor job in the connector.
	 * Matching is case-insensitive and based on the job type and its components.
	 * Supported strategy job names:
	 * - "discovery": Matches a {@link StandardMonitorJob} with a non-null discovery component.
	 * - "collect": Matches a {@link StandardMonitorJob} with a non-null collect component.
	 * - "simple": Matches a {@link SimpleMonitorJob} with a non-null simple component.
	 *
	 * @param currentConnector the connector containing monitor jobs
	 * @param strategyJobName  the strategy job name to check (case-insensitive)
	 * @return {@code true} if a monitor job matches the strategy, {@code false} otherwise
	 */
	protected boolean hasExpectedJobTypes(final Connector currentConnector, final String strategyJobName) {
		if (currentConnector == null || currentConnector.getMonitors() == null) {
			return false;
		}

		return currentConnector
			.getMonitors()
			.values()
			.stream()
			.anyMatch(monitorJob -> {
				switch (strategyJobName.toLowerCase()) {
					case "discovery":
						return monitorJob instanceof StandardMonitorJob standardJob && standardJob.getDiscovery() != null;
					case "collect":
						return monitorJob instanceof StandardMonitorJob standardJob && standardJob.getCollect() != null;
					case "simple":
						return monitorJob instanceof SimpleMonitorJob simpleJob && simpleJob.getSimple() != null;
					default:
						throw new IllegalArgumentException("Unknown strategy job name: " + strategyJobName);
				}
			});
	}

	/**
	 * Validates the connector's detection criteria
	 *
	 * @param currentConnector	Connector instance
	 * @param hostname			Hostname
	 * @param jobName 			The strategy job name
	 * @return					boolean representing the success of the tests
	 */
	protected boolean validateConnectorDetectionCriteria(
		final Connector currentConnector,
		final String hostname,
		final String jobName
	) {
		if (currentConnector.getConnectorIdentity().getDetection() == null) {
			return true;
		}
		// Track the connector detection criteria execution start time
		final long jobStartTime = System.currentTimeMillis();

		final ConnectorTestResult connectorTestResult = new ConnectorSelection(
			telemetryManager,
			clientsExecutor,
			Collections.emptySet(),
			extensionManager
		)
			.runConnectorDetectionCriteria(currentConnector, hostname);

		// Track the connector detection criteria execution end time
		final long jobEndTime = System.currentTimeMillis();

		// Set the job duration metric of the connector monitor in the host monitor
		setJobDurationMetric(
			jobName,
			KnownMonitorType.CONNECTOR.getKey(),
			currentConnector.getCompiledFilename(),
			jobStartTime,
			jobEndTime
		);

		final String connectorId = currentConnector.getCompiledFilename();
		final Monitor monitor = telemetryManager.findMonitorByTypeAndId(
			KnownMonitorType.CONNECTOR.getKey(),
			String.format(CONNECTOR_ID_FORMAT, KnownMonitorType.CONNECTOR.getKey(), connectorId)
		);

		// Add statusInformation to legacyTextParameters attribute of the connector monitor
		final String statusInformation = buildStatusInformation(hostname, connectorTestResult);
		final Map<String, String> legacyTextParameters = monitor.getLegacyTextParameters();
		legacyTextParameters.put("StatusInformation", statusInformation);

		collectConnectorStatus(connectorTestResult.isSuccess(), connectorId, monitor);
		return connectorTestResult.isSuccess();
	}

	/**
	 * Builds the status information for the connector
	 * @param hostname   Hostname of the resource being monitored
	 * @param testResult Test result of the connector
	 * @return String representing the status information
	 */
	protected String buildStatusInformation(final String hostname, final ConnectorTestResult testResult) {
		final StringBuilder value = new StringBuilder();
		final String builtTestResult = testResult
			.getCriterionTestResults()
			.stream()
			.filter(criterionTestResult ->
				!(criterionTestResult.getResult() == null && criterionTestResult.getMessage() == null)
			)
			.map(CriterionTestResult::displayCriterionMessage)
			.collect(Collectors.joining("\n"));
		value
			.append(builtTestResult)
			.append("Conclusion:\n")
			.append(String.format("Test on %s %s", hostname, testResult.isSuccess() ? "SUCCEEDED" : "FAILED"));
		return value.toString();
	}

	/**
	 * Collects the connector status and sets the metric
	 *
	 * @param isSuccessCriteria Whether the connector's criteria are successfully executed or not
	 * @param connectorId       Connector ID
	 * @param monitor           Monitor instance
	 */
	protected void collectConnectorStatus(
		final boolean isSuccessCriteria,
		final String connectorId,
		final Monitor monitor
	) {
		// Initialize the metric factory to collect metrics
		final MetricFactory metricFactory = new MetricFactory(
			telemetryManager.getHostname(),
			telemetryManager.getConnectorStore()
		);

		// Get the connector's namespace containing related settings
		final ConnectorNamespace connectorNamespace = telemetryManager
			.getHostProperties()
			.getConnectorNamespace(connectorId);

		// Collect the metric
		metricFactory.collectStateSetMetric(
			monitor,
			CONNECTOR_STATUS_METRIC_KEY,
			isSuccessCriteria ? STATE_SET_METRIC_OK : STATE_SET_METRIC_FAILED,
			new String[] { STATE_SET_METRIC_OK, STATE_SET_METRIC_FAILED },
			strategyTime
		);

		// Set isStatusOk to true in ConnectorNamespace
		connectorNamespace.setStatusOk(isSuccessCriteria);
	}

	/**
	 * Return true if the monitor type is to be filtered and not processed.
	 * @param monitorType The monitor type to check.
	 * @return boolean value.
	 */
	public boolean isMonitorFiltered(final String monitorType) {
		final HostConfiguration hostConfiguration = telemetryManager.getHostConfiguration();
		final Set<String> includedMonitors = hostConfiguration.getIncludedMonitors();
		final Set<String> excludedMonitors = hostConfiguration.getExcludedMonitors();
		// CHECKSTYLE:OFF
		return (
			(includedMonitors != null && !includedMonitors.contains(monitorType)) ||
			(excludedMonitors != null && excludedMonitors.contains(monitorType))
		);
		// CHECKSTYLE:ON
	}

	/**
	 * Sets the job duration metric in the host monitor with a monitor type.
	 *
	 * @param jobName      the name of the job
	 * @param monitorType  the monitor type in the job
	 * @param connectorId  the ID of the connector
	 * @param jobStartTime the start time of the job in milliseconds
	 * @param jobEndTime   the end time of the job in milliseconds
	 */
	protected void setJobDurationMetric(
		final String jobName,
		final String monitorType,
		final String connectorId,
		final long jobStartTime,
		final long jobEndTime
	) {
		setJobDurationMetric(
			() -> generateJobDurationMetricKey(jobName, monitorType, connectorId),
			jobStartTime,
			jobEndTime
		);
	}

	/**
	 * Sets the job duration metric in the host monitor without a monitor type.
	 *
	 * @param jobName      the name of the job
	 * @param connectorId  the ID of the connector
	 * @param jobStartTime the start time of the job in milliseconds
	 * @param jobEndTime   the end time of the job in milliseconds
	 */
	protected void setJobDurationMetric(
		final String jobName,
		final String connectorId,
		final long jobStartTime,
		final long jobEndTime
	) {
		setJobDurationMetric(() -> generateJobDurationMetricKey(jobName, connectorId), jobStartTime, jobEndTime);
	}

	/**
	 * Sets the job duration metric in the host monitor with a monitor type.
	 *
	 * @param metricKeySupplier the supplier of the metric key
	 * @param startTime the start time of the job in milliseconds
	 * @param endTime   the end time of the job in milliseconds
	 */
	private void setJobDurationMetric(
		final Supplier<String> metricKeySupplier,
		final long startTime,
		final long endTime
	) {
		// If the enableSelfMonitoring flag is set to true, or it's not configured at all,
		// set the job duration metric on the monitor. Otherwise, don't set it.
		// By default, self monitoring is enabled
		if (telemetryManager.getHostConfiguration().isEnableSelfMonitoring()) {
			// Build the job duration metric key
			final String jobDurationMetricKey = metricKeySupplier.get();
			// Collect the job duration metric
			collectJobDurationMetric(jobDurationMetricKey, startTime, endTime);
		}
	}

	/**
	 * Generates the job duration metric key.
	 * @param jobName     the name of the job
	 * @param monitorType the monitor type
	 * @param connectorId the ID of the connector
	 * @return the job duration metric key.
	 */
	private String generateJobDurationMetricKey(
		final String jobName,
		final String monitorType,
		final String connectorId
	) {
		return new StringBuilder()
			.append("metricshub.job.duration{job.type=\"")
			.append(jobName)
			.append("\", monitor.type=\"")
			.append(monitorType)
			.append("\", connector_id=\"")
			.append(connectorId)
			.append("\"}")
			.toString();
	}

	/**
	 * Generate the job duration metric key.
	 * @param jobName      the name of the job
	 * @param connectorId  the ID of the
	 * @return the job duration metric key.
	 */
	private String generateJobDurationMetricKey(final String jobName, final String connectorId) {
		return new StringBuilder()
			.append("metricshub.job.duration{job.type=\"")
			.append(jobName)
			.append("\", connector_id=\"")
			.append(connectorId)
			.append("\"}")
			.toString();
	}

	/**
	 * Collects and records the job duration metric.
	 *
	 * @param jobDurationMetricKey the key identifying the job duration metric
	 * @param startTime the start time of the job in milliseconds
	 * @param endTime the end time of the job in milliseconds
	 */
	private void collectJobDurationMetric(final String jobDurationMetricKey, final long startTime, final long endTime) {
		final Monitor endpointHostMonitor = telemetryManager.getEndpointHostMonitor();
		final MetricFactory metricFactory = new MetricFactory(
			telemetryManager.getHostname(),
			telemetryManager.getConnectorStore()
		);
		metricFactory.collectNumberMetric(
			endpointHostMonitor,
			jobDurationMetricKey,
			(endTime - startTime) / 1000.0, // Job duration in seconds
			strategyTime
		);
	}

	/**
	 * Collects the host configured metric.
	 *
	 * @param hostname The resource hostname.
	 */
	protected void collectHostConfigured(final String hostname) {
		final Monitor endpointHostMonitor = telemetryManager.getEndpointHostMonitor();
		final MetricFactory metricFactory = new MetricFactory(
			telemetryManager.getHostname(),
			telemetryManager.getConnectorStore()
		);
		metricFactory.collectNumberMetric(endpointHostMonitor, HOST_CONFIGURED_METRIC_NAME, 1.0, strategyTime);
	}
}
