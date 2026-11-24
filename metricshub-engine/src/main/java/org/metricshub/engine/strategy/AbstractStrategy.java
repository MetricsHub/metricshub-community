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
import static org.metricshub.engine.common.helpers.MetricsHubConstants.MAX_CONSECUTIVE_DETECTION_FAILURES;
import static org.metricshub.engine.common.helpers.MetricsHubConstants.SOURCE_REF_PATTERN;
import static org.metricshub.engine.common.helpers.MetricsHubConstants.STATE_SET_METRIC_FAILED;
import static org.metricshub.engine.common.helpers.MetricsHubConstants.STATE_SET_METRIC_OK;
import static org.metricshub.engine.common.helpers.MetricsHubConstants.THREAD_TIMEOUT;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.metricshub.engine.client.ClientsExecutor;
import org.metricshub.engine.common.JobInfo;
import org.metricshub.engine.common.exception.RetryableException;
import org.metricshub.engine.common.helpers.KnownMonitorType;
import org.metricshub.engine.common.helpers.ThreadHelper;
import org.metricshub.engine.configuration.HostConfiguration;
import org.metricshub.engine.connector.model.Connector;
import org.metricshub.engine.connector.model.common.ContentType;
import org.metricshub.engine.connector.model.identity.ConnectorIdentity;
import org.metricshub.engine.connector.model.identity.criterion.Criterion;
import org.metricshub.engine.connector.model.monitor.SimpleMonitorJob;
import org.metricshub.engine.connector.model.monitor.StandardMonitorJob;
import org.metricshub.engine.connector.model.monitor.task.source.EventLogSource;
import org.metricshub.engine.connector.model.monitor.task.source.FileSource;
import org.metricshub.engine.connector.model.monitor.task.source.FileSourceProcessingMode;
import org.metricshub.engine.connector.model.monitor.task.source.IpmiSelSource;
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
import org.metricshub.engine.strategy.surrounding.AfterAllStrategy;
import org.metricshub.engine.strategy.utils.ForceSerializationHelper;
import org.metricshub.engine.strategy.utils.RetryOperation;
import org.metricshub.engine.telemetry.ConnectorNamespace;
import org.metricshub.engine.telemetry.ConnectorNamespace.SourceCursors;
import org.metricshub.engine.telemetry.MetricFactory;
import org.metricshub.engine.telemetry.Monitor;
import org.metricshub.engine.telemetry.RequestOutcome;
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
	public static final String HOST_OBSERVED_METRIC_NAME = "metricshub.host.observed";

	@NonNull
	protected TelemetryManager telemetryManager;

	@NonNull
	protected Long strategyTime;

	@NonNull
	protected ClientsExecutor clientsExecutor;

	@NonNull
	protected ExtensionManager extensionManager;

	private static final String REQUESTS_METRIC_FORMAT = "metricshub.host.requests{state=\"%s\", operation_type=\"%s\"}";
	private static final String COMPUTE = "compute";
	private static final String SOURCE = "source";

	/**
	 * Stateless proxy for full source table log lines (used for non-file sources and for file sources when detail logging is on).
	 */
	private static final SourceTableLoggingProxy DEFAULT_SOURCE_TABLE_LOGGING_PROXY = new DefaultSourceTableLoggingProxy();

	/**
	 * Format for string value like: <em>connector_connector-id</em>
	 */
	public static final String CONNECTOR_ID_FORMAT = "%s_%s";

	/**
	 * How long after the strategy timeout an interrupted run may still start the afterAll of a connector whose
	 * beforeAll it entered, in milliseconds: the 5 s grace of a beforeAll cut short plus 1 s for its cancel, plus 0.5 s
	 * for the sources and computes that follow a login answered at the end of its grace, so that such a login is followed
	 * by its logout. The budget, from the interrupt I and {@link ContextExecutor}'s 10 s SHUTDOWN_GRACE_PERIOD_SECONDS,
	 * after which it abandons the strategy thread:
	 * <ul>
	 * <li>a beforeAll cut short ends by I+6 s, and the monitor jobs cut short get {@link #POOL_STOP_SECONDS} to stop,
	 * by I+1 s: an afterAll starts by I+6.5 s;</li>
	 * <li>that afterAll gets its 2 s grace plus 1 s for its cancel: the run ends by I+9.5 s, whether I is the strategy
	 * timeout or an earlier cancel (reload).</li>
	 * </ul>
	 * Only one afterAll of an interrupted run gets this grace, although the afterAll of several connectors may be
	 * deferred until their monitor jobs end. So the logout always reads this run's token, before the next run of the
	 * host can open another session. A login answered after its grace, or a session whose logout is skipped, leaks a
	 * session, which expires on the device.
	 */
	protected static final long CLEANUP_WINDOW_MILLIS = 6500;

	/**
	 * How long the monitor jobs of thread pools cut short may take to stop before the afterAll of their connector, in
	 * seconds, counted once per run from the first cut: the afterAll may close the session they use.
	 */
	static final long POOL_STOP_SECONDS = 1;

	/**
	 * How often a deferred thread pool checks the strategy thread and the strategy deadline, in milliseconds.
	 */
	private static final long POOL_WATCH_MILLIS = 100;

	/**
	 * Whether an afterAll of this interrupted run already used the cleanup grace, see {@link #CLEANUP_WINDOW_MILLIS}.
	 */
	@Getter(AccessLevel.NONE)
	@EqualsAndHashCode.Exclude
	@ToString.Exclude
	private final AtomicBoolean cleanupGraceUsed = new AtomicBoolean();

	/**
	 * The {@link System#nanoTime()} until which the thread pools of this run cut short may stop, 0 until the first cut,
	 * see {@link #POOL_STOP_SECONDS}.
	 */
	@Getter(AccessLevel.NONE)
	@EqualsAndHashCode.Exclude
	@ToString.Exclude
	private final AtomicLong poolStopDeadline = new AtomicLong();

	/**
	 * Execute each source in the given list of sources then for each source table apply all the attached computes.
	 * When the {@link SourceTable} is ready it is added to {@link TelemetryManager}
	 *
	 * @param sources The {@link List} of {@link Source} instances we wish to execute
	 * @param jobInfo Information about the job such as hostname, monitorType, job name and connectorName.
	 * @return the error of the failed source that stopped the job, null when the job goes on to its mapping
	 */
	protected String processSourcesAndComputes(final List<Source> sources, final JobInfo jobInfo) {
		return processSourcesAndComputes(sources, null, jobInfo);
	}

	/**
	 * Execute each source in the given list of sources then for each source table apply all the attached computes.
	 * When the {@link SourceTable} is ready it is added to {@link TelemetryManager}
	 *
	 * @param sources    The {@link List} of {@link Source} instances we wish to execute
	 * @param attributes Key-value pairs of the monitor's attributes used in the mono instance processing
	 * @param jobInfo    Information about the job such as hostname, monitorType, job name and connectorName.
	 * @return the error of the failed source that stopped the job, null when the job goes on to its mapping
	 */
	protected String processSourcesAndComputes(
		final List<Source> sources,
		final Map<String, String> attributes,
		final JobInfo jobInfo
	) {
		return processSourcesAndComputes(sources, attributes, jobInfo, new ArrayList<>());
	}

	/**
	 * Execute each source in the given list of sources then for each source table apply all the attached computes.
	 * When the {@link SourceTable} is ready it is added to {@link TelemetryManager}
	 *
	 * @param sources         The {@link List} of {@link Source} instances we wish to execute
	 * @param attributes      Key-value pairs of the monitor's attributes used in the mono instance processing
	 * @param jobInfo         Information about the job such as hostname, monitorType, job name and connectorName.
	 * @param cursorRollbacks The caller's list, to which the rollback of the cursors of each incremental source polled
	 *                        is added: the caller runs them when it skips the mapping, see
	 *                        {@link #skipMappingIfInterrupted(List, String, String)}
	 * @return the error of the failed source that stopped the job (see {@link Source#isContinueOnFailure()}), null
	 *         when the job goes on to its mapping, unless it is interrupted (see
	 *         {@link #skipMappingIfInterrupted(List, String, String)})
	 */
	protected String processSourcesAndComputes(
		final List<Source> sources,
		final Map<String, String> attributes,
		final JobInfo jobInfo,
		final List<Runnable> cursorRollbacks
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
			return null;
		}

		// Loop over all the sources and accept the SourceProcessor which is going to
		// process the source
		for (int sourceIndex = 0; sourceIndex < sources.size(); sourceIndex++) {
			// The first source always starts, unless the thread is interrupted: a sheltered beforeAll that started may open
			// a session. Then a beforeAll whose caller is interrupted starts no other source
			final boolean stop = sourceIndex > 0 ? ThreadHelper.isStopping() : isInterrupted();
			if (stop && skipSources(sources.subList(sourceIndex, sources.size()), cursorRollbacks, connectorId, hostname)) {
				return null;
			}

			final Source source = sources.get(sourceIndex);
			final String sourceKey = source.getKey();

			logBeginOperation(SOURCE, source, sourceKey, connectorId, hostname);

			final ConnectorNamespace connectorNamespace = telemetryManager.getHostProperties()
				.getConnectorNamespace(connectorId);
			final SourceTable previousSourceTable = connectorNamespace.getSourceTable(sourceKey);
			// The poll begins before its cursors are read: a late rollback of an older poll then leaves them alone
			final long poll = isIncremental(source) ? connectorNamespace.beginPoll(sourceKey) : 0L;
			final SourceCursors cursorsBefore = isIncremental(source) ? connectorNamespace.getSourceCursors(sourceKey) : null;

			// A source that reads a failed source fails as well. Otherwise, execute the source and retry it once when it
			// fails or gives nothing while it gave data before: the last attempt is the result
			final AtomicReference<SourceTable> lastResult = new AtomicReference<>(
				failureOfReferences(source, connectorNamespace)
			);
			if (lastResult.get() == null) {
				RetryOperation.<SourceTable>builder()
					.withMaxRetries(1)
					.withWaitStrategy(telemetryManager.getHostConfiguration().getRetryDelay())
					.withDescription(String.format("%s [%s]", SOURCE, sourceKey))
					.withHostname(hostname)
					.build()
					.run(() -> runSource(connectorId, attributes, source, previousSourceTable, lastResult));
			}
			SourceTable sourceTable = lastResult.get();

			if (cursorsBefore != null) {
				final SourceCursors cursorsAfter = connectorNamespace.getSourceCursors(sourceKey);
				cursorRollbacks.add(
					() -> connectorNamespace.rollBackSourceCursors(sourceKey, poll, cursorsBefore, cursorsAfter)
				);
			}

			// Interrupted while it ran, the source may have been cut short (an executeForEachEntryOf stops between two
			// entries): it did not answer, whatever it returned
			if (
				skipSourcesIfInterrupted(sources.subList(sourceIndex, sources.size()), cursorRollbacks, connectorId, hostname)
			) {
				return null;
			}

			if (sourceTable.isFailed()) {
				// Remembered for the jobs that read this source through a reference: a missing row is not a missing entity
				connectorNamespace.getUnansweredSourceKeys().add(sourceKey);
				if (!source.isContinueOnFailure()) {
					log.error(
						"Hostname {} - Source {} failed - Connector {} - Monitor {}. Skipped its computes, the sources {} and the mapping: {}",
						hostname,
						sourceKey,
						connectorId,
						monitorType,
						sources.subList(sourceIndex + 1, sources.size()).stream().map(Source::getKey).toList(),
						sourceTable.getError()
					);
					// Stored, so that the sources reading it fail as well. The entries of the incremental sources polled are
					// not mapped: they come again at the next poll
					connectorNamespace.addSourceTable(sourceKey, sourceTable);
					rollBackCursors(cursorRollbacks);
					return sourceTable.getError();
				}
				log.error(
					"Hostname {} - Source {} failed - Connector {} - Monitor {}. Continuing with an empty result: {}",
					hostname,
					sourceKey,
					connectorId,
					monitorType,
					sourceTable.getError()
				);
				sourceTable = emptyResult(source);
			} else {
				connectorNamespace.getUnansweredSourceKeys().remove(sourceKey);
				if (sourceTable.isEmpty()) {
					log.warn(
						"Hostname {} - Received empty source table for Source key {} - Connector {} - Monitor {}.",
						hostname,
						sourceKey,
						connectorId,
						monitorType
					);
					sourceTable = emptyResult(source);
				}
			}

			// log the source table
			logSourceTable(
				resolveSourceTableLoggingProxy(source),
				SOURCE,
				source.getClass().getSimpleName(),
				sourceKey,
				connectorId,
				sourceTable,
				hostname
			);

			final List<Compute> computes = source.getComputes();

			// Add the source table and stop if no compute is found
			if (computes == null || computes.isEmpty()) {
				telemetryManager.getHostProperties().getConnectorNamespace(connectorId).addSourceTable(sourceKey, sourceTable);
				continue;
			}

			final ComputeProcessor computeProcessor = ComputeProcessor.builder()
				.sourceKey(sourceKey)
				.sourceTable(sourceTable)
				.connectorId(connectorId)
				.hostname(hostname)
				.clientsExecutor(clientsExecutor)
				.telemetryManager(telemetryManager)
				.attributes(attributes)
				.build();

			final ComputeUpdaterProcessor computeUpdaterProcessor = ComputeUpdaterProcessor.builder()
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

				// Interrupted during a compute, the last one included: the table is half computed and is not stored
				if (
					skipSourcesIfInterrupted(sources.subList(sourceIndex, sources.size()), cursorRollbacks, connectorId, hostname)
				) {
					return null;
				}

				// log the updated source table
				logSourceTable(
					DEFAULT_SOURCE_TABLE_LOGGING_PROXY,
					COMPUTE,
					compute.getClass().getSimpleName(),
					computeKey,
					connectorId,
					computeProcessor.getSourceTable(),
					hostname
				);
			}

			telemetryManager.getHostProperties()
				.getConnectorNamespace(connectorId)
				.addSourceTable(sourceKey, computeProcessor.getSourceTable());
		}

		return null;
	}

	/**
	 * The failure of a source that reads a failed source: a <code>copy</code>, a <code>tableJoin</code>, a
	 * <code>tableUnion</code> or any <code>${source::...}</code> reference.
	 *
	 * @param source             The source
	 * @param connectorNamespace The namespace holding the tables of the sources
	 * @return a failed source table, null when no source it reads failed
	 */
	private static SourceTable failureOfReferences(final Source source, final ConnectorNamespace connectorNamespace) {
		for (final String reference : source.getReferences()) {
			final Matcher matcher = SOURCE_REF_PATTERN.matcher(reference);
			while (matcher.find()) {
				final SourceTable table = connectorNamespace.getSourceTable(matcher.group());
				if (table != null && table.isFailed()) {
					return SourceTable.failed("%s failed: %s".formatted(matcher.group(), table.getError()));
				}
			}
		}
		return null;
	}

	/**
	 * Whether the given source is incremental: an eventLog or ipmiSel source, or a file source in LOG mode.
	 *
	 * @param source The source to check
	 * @return {@code true} if the source only returns the entries added since the previous poll
	 */
	protected static boolean isIncremental(final Source source) {
		if (source instanceof FileSource fileSource) {
			return fileSource.getMode() == FileSourceProcessingMode.LOG;
		}
		return source instanceof EventLogSource || source instanceof IpmiSelSource;
	}

	/**
	 * Whether the thread running this strategy is interrupted: its deadline expired or it was cancelled. Such a thread
	 * may be an abandoned one, still running while the next run of the same host fills the same telemetry, so it
	 * sends no more request and writes nothing more. A sheltered beforeAll or afterAll given up on stays cancelled, even
	 * if a library swallowed its interrupt, see {@link ThreadHelper#isCancelled()}.
	 *
	 * @return {@code true} if the current thread is interrupted or its sheltered task was given up on
	 */
	protected static boolean isInterrupted() {
		return ThreadHelper.isCancelled();
	}

	/**
	 * Whether this run is still within its timeout plus {@link #CLEANUP_WINDOW_MILLIS}, whatever the interrupt status.
	 *
	 * @param runStartTime The {@link System#nanoTime()} at which this strategy run started
	 * @return {@code true} if an interrupted run may still start an afterAll
	 */
	protected boolean isWithinCleanupWindow(final long runStartTime) {
		return (System.nanoTime() - runStartTime < TimeUnit.SECONDS.toNanos(getStrategyTimeout()) +
			TimeUnit.MILLISECONDS.toNanos(CLEANUP_WINDOW_MILLIS));
	}

	/**
	 * Runs the afterAll sources of the given connector, unless the run is interrupted and either the beforeAll of this
	 * connector opened nothing, the cleanup window is over or another afterAll of this run already used it: a session
	 * this run opened is closed, never one of the next run.
	 *
	 * @param connector    The connector
	 * @param opened       Whether the beforeAll of the connector may have opened a session
	 * @param runStartTime The {@link System#nanoTime()} at which this strategy run started
	 */
	protected void runAfterAll(final Connector connector, final boolean opened, final long runStartTime) {
		// A connector without afterAll sources closes nothing: it must not use the cleanup grace of a connector that does
		final Map<String, Source> afterAll = connector.getAfterAll();
		if (afterAll == null || afterAll.isEmpty()) {
			return;
		}
		if (isInterrupted() && !(opened && isWithinCleanupWindow(runStartTime) && !cleanupGraceUsed.getAndSet(true))) {
			log.debug(
				"Hostname {} - Thread interrupted: skipping the afterAll sources of connector {}.",
				telemetryManager.getHostname(),
				connector.getCompiledFilename()
			);
			return;
		}
		AfterAllStrategy.builder()
			.clientsExecutor(clientsExecutor)
			.strategyTime(strategyTime)
			.telemetryManager(telemetryManager)
			.connector(connector)
			.extensionManager(extensionManager)
			.build()
			.run();
	}

	/**
	 * How long a connector waits for its parallel monitor jobs before the strategy goes on with the next connector, in
	 * seconds.
	 *
	 * @return {@code THREAD_TIMEOUT}
	 */
	protected long getThreadTimeout() {
		return THREAD_TIMEOUT;
	}

	/**
	 * Interrupts the monitor jobs of a connector that outlived {@link #getThreadTimeout()} once the strategy thread (the
	 * current one) is interrupted or the strategy deadline passes, even while that thread is busy with the next
	 * connectors or abandoned. A sheltered beforeAll or afterAll clears the interrupt of the strategy thread while it
	 * runs: the strategy cuts its deferred pools itself when that shelter sees the interrupt, see
	 * {@link ThreadHelper#onInterrupt(Runnable, Runnable)}.
	 *
	 * @param pool     The thread pool of the monitor jobs, already shut down
	 * @param deadline The {@link System#nanoTime()} of the strategy deadline
	 */
	protected void watchPool(final ExecutorService pool, final long deadline) {
		final Thread strategyThread = Thread.currentThread();
		Thread.ofVirtual().start(() -> {
			try {
				while (!pool.awaitTermination(POOL_WATCH_MILLIS, TimeUnit.MILLISECONDS)) {
					if (strategyThread.isInterrupted() || System.nanoTime() - deadline >= 0) {
						cutPool(pool);
						return;
					}
				}
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
		});
	}

	/**
	 * Interrupts the monitor jobs of a thread pool (shutdownNow), and starts the {@link #POOL_STOP_SECONDS} of this run
	 * if it is the first cut.
	 *
	 * @param pool The thread pool of the monitor jobs
	 */
	protected void cutPool(final ExecutorService pool) {
		poolStopDeadline.compareAndSet(0, System.nanoTime() + TimeUnit.SECONDS.toNanos(POOL_STOP_SECONDS));
		pool.shutdownNow();
	}

	/**
	 * Waits for the monitor jobs of the connectors that outlived {@link #getThreadTimeout()}, until the deadline of the
	 * strategy, and ends each connector as soon as its own jobs are done: a connector whose jobs complete in time never
	 * waits for another one, nor loses its afterAll to it. The jobs still running at the deadline, or once the strategy
	 * thread is interrupted, are cut, and their connectors are ended once the jobs stopped (see
	 * {@link #awaitPoolStop(ExecutorService, String)}). A connector whose end throws does not prevent the end of the next
	 * ones: the first exception is thrown afterwards.
	 *
	 * @param <P>      The type of the record of a connector whose jobs outlived the thread timeout
	 * @param pending  The records, in the order the connectors were processed
	 * @param poolOf   The thread pool of the monitor jobs of a record
	 * @param end      Ends the connector of a record, told whether all its jobs completed
	 * @param deadline The {@link System#nanoTime()} of the strategy deadline
	 * @param hostname The host name
	 */
	protected <P> void joinPools(
		final List<P> pending,
		final Function<P, ExecutorService> poolOf,
		final BiConsumer<P, Boolean> end,
		final long deadline,
		final String hostname
	) {
		final List<P> running = new ArrayList<>(pending);
		final List<RuntimeException> failures = new ArrayList<>();
		while (!running.isEmpty()) {
			final Iterator<P> iterator = running.iterator();
			while (iterator.hasNext()) {
				final P next = iterator.next();
				if (poolOf.apply(next).isTerminated()) {
					iterator.remove();
					endPending(end, next, true, failures);
				}
			}
			if (running.isEmpty() || isInterrupted() || System.nanoTime() - deadline >= 0) {
				break;
			}
			try {
				poolOf.apply(running.get(0))
					.awaitTermination(
						Math.min(TimeUnit.MILLISECONDS.toNanos(POOL_WATCH_MILLIS), deadline - System.nanoTime()),
						TimeUnit.NANOSECONDS
					);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
		}

		if (!running.isEmpty()) {
			log.warn("Hostname {} - Thread pool did not terminate within the strategy timeout. Forcing shutdown.", hostname);
			// Cut them all at once, so that they stop together
			running.forEach(next -> cutPool(poolOf.apply(next)));
			running.forEach(next -> {
				awaitPoolStop(poolOf.apply(next), hostname);
				endPending(end, next, false, failures);
			});
		}

		if (!failures.isEmpty()) {
			final RuntimeException first = failures.get(0);
			failures.subList(1, failures.size()).forEach(first::addSuppressed);
			throw first;
		}
	}

	/**
	 * Ends the connector of the given record, and keeps the exception it throws for later.
	 *
	 * @param <P>       The type of the record
	 * @param end       Ends the connector of a record, told whether all its jobs completed
	 * @param pending   The record
	 * @param completed Whether all the jobs of the connector completed
	 * @param failures  Where to keep the exception
	 */
	private static <P> void endPending(
		final BiConsumer<P, Boolean> end,
		final P pending,
		final boolean completed,
		final List<RuntimeException> failures
	) {
		try {
			end.accept(pending, completed);
		} catch (RuntimeException e) {
			failures.add(e);
		}
	}

	/**
	 * Waits for the monitor jobs of a thread pool cut short (see {@link #cutPool(ExecutorService)}) to stop, within
	 * {@link #POOL_STOP_SECONDS} for the whole run, so that the afterAll of their connector does not close the session they still use. A job that
	 * ignores its interrupt longer is left to end on its own, and the afterAll runs anyway: a session left open would
	 * leak. A request it sent on a thread of its own (see ThreadHelper#executeInterruptibly) ends with its protocol
	 * timeout.
	 *
	 * @param pool     The thread pool of the monitor jobs, cut short
	 * @param hostname The host name
	 */
	protected void awaitPoolStop(final ExecutorService pool, final String hostname) {
		poolStopDeadline.compareAndSet(0, System.nanoTime() + TimeUnit.SECONDS.toNanos(POOL_STOP_SECONDS));
		if (!ThreadHelper.awaitTermination(pool, poolStopDeadline.get() - System.nanoTime())) {
			log.warn(
				"Hostname {} - Monitor jobs cut short still running after {} second: the afterAll of their connector runs anyway.",
				hostname,
				POOL_STOP_SECONDS
			);
		}
	}

	/**
	 * Whether the thread of the job is interrupted (its deadline expired or it was cancelled), in which case the given
	 * sources are skipped. An interrupted thread may be an abandoned one, still running while the next run of the same
	 * host fills the same {@link ConnectorNamespace}: it writes nothing there, and its caller skips the mapping too, so
	 * that it never reads the tables it did not refresh. The cursors its incremental sources moved are rolled back, as
	 * their entries are not mapped.
	 *
	 * @param remainingSources The sources of the job not executed yet
	 * @param cursorRollbacks  The rollbacks of the cursors of the incremental sources the job polled
	 * @param connectorId      The connector identifier
	 * @param hostname         The host name
	 * @return {@code true} if the thread is interrupted and the remaining sources were skipped
	 */
	private static boolean skipSourcesIfInterrupted(
		final List<Source> remainingSources,
		final List<Runnable> cursorRollbacks,
		final String connectorId,
		final String hostname
	) {
		return isInterrupted() && skipSources(remainingSources, cursorRollbacks, connectorId, hostname);
	}

	/**
	 * Skips the given sources of a job cut short, and rolls back the cursors its incremental sources moved, see
	 * {@link #skipSourcesIfInterrupted(List, List, String, String)}.
	 *
	 * @param remainingSources The sources of the job not executed yet
	 * @param cursorRollbacks  The rollbacks of the cursors of the incremental sources the job polled
	 * @param connectorId      The connector identifier
	 * @param hostname         The host name
	 * @return {@code true}
	 */
	private static boolean skipSources(
		final List<Source> remainingSources,
		final List<Runnable> cursorRollbacks,
		final String connectorId,
		final String hostname
	) {
		rollBackCursors(cursorRollbacks);

		log.warn(
			"Hostname {} - The job was interrupted. Connector {} - Skipped sources: {}.",
			hostname,
			connectorId,
			remainingSources.stream().map(Source::getKey).toList()
		);
		return true;
	}

	/**
	 * The only check of the interrupt between the sources of a job and its mapping, made by the caller right before the
	 * mapping: an interrupted job is not mapped and the cursors its incremental sources moved are rolled back, in the
	 * same decision. A mapping that passed this check runs to its end, on the thread of the job, and the cursors stay.
	 *
	 * @param cursorRollbacks The rollbacks of the cursors of the incremental sources the job polled, filled by
	 *                        {@link #processSourcesAndComputes(List, Map, JobInfo, List)}
	 * @param connectorId     The connector identifier
	 * @param hostname        The host name
	 * @return {@code true} if the thread is interrupted and the mapping must be skipped
	 */
	protected static boolean skipMappingIfInterrupted(
		final List<Runnable> cursorRollbacks,
		final String connectorId,
		final String hostname
	) {
		if (!isInterrupted()) {
			return false;
		}

		rollBackCursors(cursorRollbacks);

		log.warn("Hostname {} - The job was interrupted. Connector {} - Skipped mapping.", hostname, connectorId);
		return true;
	}

	/**
	 * Run then clear the given cursor rollbacks of a job that is not mapped: the next poll of its incremental sources
	 * must return their entries again. Only a cursor still where this poll left it is moved back, and a second call
	 * does nothing.
	 *
	 * @param cursorRollbacks The rollbacks of the cursors of the incremental sources the job polled
	 */
	private static void rollBackCursors(final List<Runnable> cursorRollbacks) {
		cursorRollbacks.forEach(Runnable::run);
		cursorRollbacks.clear();
	}

	/**
	 * The fresh empty result of a source, typed by its static type: what a source that failed with
	 * <code>continueOnFailure</code> gives, an empty text for a text source, so that an awk still runs its BEGIN and END
	 * blocks, else an empty table. A <code>copy</code> has the type of the chain it copies, set when the connector loads.
	 *
	 * @param source The source
	 * @return a new empty text or empty table
	 */
	static SourceTable emptyResult(final Source source) {
		return source.getResultContentType() == ContentType.TEXT ? SourceTable.ofText(null) : SourceTable.empty();
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
	 * @param lastResult          Receives the result, also when a retry is asked
	 * @return new {@link SourceTable} instance
	 */
	private SourceTable runSource(
		final String connectorId,
		final Map<String, String> attributes,
		final Source source,
		final SourceTable previousSourceTable,
		final AtomicReference<SourceTable> lastResult
	) {
		final ISourceProcessor sourceProcessor = SourceProcessor.builder()
			.connectorId(connectorId)
			.clientsExecutor(clientsExecutor)
			.telemetryManager(telemetryManager)
			.extensionManager(extensionManager)
			.attributes(attributes)
			.build();

		final Supplier<SourceTable> executable = () -> source.accept(
			SourceUpdaterProcessor.builder()
				.connectorId(connectorId)
				.sourceProcessor(sourceProcessor)
				.telemetryManager(telemetryManager)
				.attributes(attributes)
				.extensionManager(extensionManager)
				.build()
		);

		// Process the source to get a source table

		final SourceTable result = source.isForceSerialization()
			? ForceSerializationHelper.forceSerialization(
				executable,
				telemetryManager,
				connectorId,
				source,
				SOURCE,
				SourceTable.failed(
					"Not executed: the force serialization lock is not available or an earlier request still runs on the device."
				)
			)
			: executable.get();
		final SourceTable sourceTable = result != null
			? result
			: SourceTable.failed("The source processor gave no result.");
		lastResult.set(sourceTable);

		// A retry must be attempted if this source has already produced results in the past (a failure is empty)
		if (!isNullOrEmptySourceTable(previousSourceTable) && sourceTable.isEmpty()) {
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
	 * Resolves the {@link SourceTableLoggingProxy} to use when logging a source result.
	 *
	 * @param source the executed source
	 * @return the proxy (never null)
	 */
	private SourceTableLoggingProxy resolveSourceTableLoggingProxy(final Source source) {
		if (source instanceof FileSource) {
			return new FileSourceTableLoggingProxy(DEFAULT_SOURCE_TABLE_LOGGING_PROXY, isLogFileSourceDetails());
		}
		return DEFAULT_SOURCE_TABLE_LOGGING_PROXY;
	}

	private boolean isLogFileSourceDetails() {
		final HostConfiguration hostConfiguration = telemetryManager.getHostConfiguration();
		return hostConfiguration != null && hostConfiguration.isLogFileSourceDetails();
	}

	/**
	 * Log the {@link SourceTable} result.
	 *
	 * @param loggingProxy         formats the table for the log line
	 * @param operationTag         the tag of the operation. E.g. source or compute
	 * @param executionClassName   the source or the compute class name we want to log
	 * @param executionKey         the key of the source or the compute we want to log
	 * @param connectorId          the compiled file name of the connector (identifier)
	 * @param sourceTable          the source's result we wish to log
	 * @param hostname             the hostname of the source we wish to log
	 */
	static void logSourceTable(
		final SourceTableLoggingProxy loggingProxy,
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
		final String message = loggingProxy.formatForLog(
			operationTag,
			executionClassName,
			executionKey,
			connectorId,
			sourceTable,
			hostname
		);
		log.info("{}", message);
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

		return currentConnector.getMonitors().values().stream().anyMatch(monitorJob -> {
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
	 * Validates the connector's detection criteria.
	 * Uses health checks when they are defined, otherwise falls back to detection criteria.
	 *
	 * Uses a consecutive failure threshold to avoid stopping a connector's job
	 * due to a single transient failure (e.g. SNMP timeout). The connector is only
	 * considered to no longer match the host after {@code MAX_CONSECUTIVE_DETECTION_FAILURES}
	 * consecutive failures. The counter is reset on each successful validation.
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
		final ConnectorIdentity connectorIdentity = currentConnector.getConnectorIdentity();
		final String connectorId = currentConnector.getCompiledFilename();
		final List<Criterion> healthChecks = connectorIdentity.getHealthChecks();
		final boolean useHealthChecks = healthChecks != null && !healthChecks.isEmpty();
		final String validationType = useHealthChecks ? "Health checks" : "Detection criteria";

		if (!useHealthChecks && connectorIdentity.getDetection() == null) {
			return true;
		}
		// Track the connector validation criteria execution start time
		final long jobStartTime = System.currentTimeMillis();

		final ConnectorSelection connectorSelection = new ConnectorSelection(
			telemetryManager,
			clientsExecutor,
			Collections.emptySet(),
			extensionManager,
			true
		);
		final ConnectorTestResult connectorTestResult = useHealthChecks
			? connectorSelection.runConnectorHealthCheckCriteria(currentConnector, hostname)
			: connectorSelection.runConnectorDetectionCriteria(currentConnector, hostname);

		// Track the connector validation criteria execution end time
		final long jobEndTime = System.currentTimeMillis();

		// Set the job duration metric of the connector monitor in the host monitor
		setJobDurationMetric(
			jobName,
			KnownMonitorType.CONNECTOR.getKey(),
			currentConnector.getCompiledFilename(),
			jobStartTime,
			jobEndTime
		);

		// A validation cut short by an interrupt (job timeout, reload) proves nothing: the connector status and its
		// failure counter are left as they are, and the job, whose sources are skipped, carries on
		if (Thread.currentThread().isInterrupted()) {
			log.warn(
				"Hostname {} - {} validation of connector {} interrupted. The connector status is left unchanged.",
				hostname,
				validationType,
				connectorId
			);
			return true;
		}

		final Monitor monitor = telemetryManager.findMonitorByTypeAndId(
			KnownMonitorType.CONNECTOR.getKey(),
			String.format(CONNECTOR_ID_FORMAT, KnownMonitorType.CONNECTOR.getKey(), connectorId)
		);

		// Add statusInformation to legacyTextParameters attribute of the connector monitor
		final String statusInformation = buildStatusInformation(hostname, connectorTestResult);
		final Map<String, String> legacyTextParameters = monitor.getLegacyTextParameters();
		legacyTextParameters.put("StatusInformation", statusInformation);

		final boolean validationSuccess = connectorTestResult.isSuccess();

		// Get the connector's namespace to track consecutive validation failures
		final ConnectorNamespace connectorNamespace = telemetryManager.getHostProperties()
			.getConnectorNamespace(connectorId);

		if (validationSuccess) {
			// Validation passed: reset the consecutive failure counter
			connectorNamespace.resetDetectionFailures();
			collectConnectorStatus(true, connectorId, monitor);
			return true;
		}

		// Validation failed: increment the counter and check against the threshold
		final int failureCount = connectorNamespace.incrementDetectionFailures();

		if (failureCount < MAX_CONSECUTIVE_DETECTION_FAILURES) {
			// Transient failure: log a warning but do NOT stop the connector's job
			log.warn(
				"Hostname {} - {} re-validation failed for connector {} (failure {}/{}). " +
					"Treating as transient; the connector's {} job will continue.",
				hostname,
				validationType,
				connectorId,
				failureCount,
				MAX_CONSECUTIVE_DETECTION_FAILURES,
				jobName
			);
			// Report the status as FAILED while we tolerate the transient failure and keep the job running
			collectConnectorStatus(false, connectorId, monitor);
			return true;
		}

		// Threshold exceeded: this is a persistent failure
		log.error(
			"Hostname {} - {} re-validation failed {} consecutive times for connector {}. " +
				"The connector no longer matches the host. Stopping the connector's {} job.",
			hostname,
			validationType,
			failureCount,
			connectorId,
			jobName
		);
		collectConnectorStatus(false, connectorId, monitor);
		return false;
	}

	/**
	 * Builds the status information for the connector
	 * @param hostname   Hostname of the resource being monitored
	 * @param testResult Test result of the connector
	 * @return String representing the status information
	 */
	protected String buildStatusInformation(final String hostname, final ConnectorTestResult testResult) {
		final StringBuilder value = new StringBuilder();
		final String builtTestResult = testResult.getCriterionTestResults()
			.stream()
			.filter(
				criterionTestResult -> !(criterionTestResult.getResult() == null && criterionTestResult.getMessage() == null)
			)
			.map(CriterionTestResult::displayCriterionMessage)
			.collect(Collectors.joining("\n"));
		value.append(builtTestResult)
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
		final ConnectorNamespace connectorNamespace = telemetryManager.getHostProperties()
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
		return ((includedMonitors != null && !includedMonitors.contains(monitorType)) ||
			(excludedMonitors != null && excludedMonitors.contains(monitorType)));
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
		// By default, self monitoring is enabled. An interrupted thread writes nothing (see isAbandoned)
		if (telemetryManager.getHostConfiguration().isEnableSelfMonitoring() && !isInterrupted()) {
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
		return new StringBuilder().append("metricshub.job.duration{job.type=\"")
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
		return new StringBuilder().append("metricshub.job.duration{job.type=\"")
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
		if (isInterrupted()) {
			return;
		}
		final Monitor endpointHostMonitor = telemetryManager.getEndpointHostMonitor();
		final MetricFactory metricFactory = new MetricFactory(
			telemetryManager.getHostname(),
			telemetryManager.getConnectorStore()
		);
		metricFactory.collectNumberMetric(endpointHostMonitor, HOST_CONFIGURED_METRIC_NAME, 1.0, strategyTime);
	}

	/**
	 * Collects the host observed metric.
	 *
	 * @param observed Whether the host is observed or not.
	 */
	protected void collectHostObserved(final Double observed) {
		if (isInterrupted()) {
			return;
		}
		final Monitor endpointHostMonitor = telemetryManager.getEndpointHostMonitor();
		final MetricFactory metricFactory = new MetricFactory(
			telemetryManager.getHostname(),
			telemetryManager.getConnectorStore()
		);
		metricFactory.collectNumberMetric(endpointHostMonitor, HOST_OBSERVED_METRIC_NAME, observed, strategyTime);
	}

	/**
	 * Collects the request counters of the resource ({@code metricshub.host.requests}, by state and operation
	 * type) on the endpoint host monitor, if self-monitoring is enabled. The three states are collected for every
	 * operation type that recorded at least one request.
	 */
	protected void collectRequestMetrics() {
		if (!telemetryManager.getHostConfiguration().isEnableSelfMonitoring() || isInterrupted()) {
			return;
		}
		final Map<String, Map<RequestOutcome, Long>> countersByOperation = telemetryManager.getRequestStats().snapshot();
		if (countersByOperation.isEmpty()) {
			return;
		}
		final Monitor endpointHostMonitor = telemetryManager.getEndpointHostMonitor();
		if (endpointHostMonitor == null) {
			return;
		}
		final MetricFactory metricFactory = new MetricFactory(
			telemetryManager.getHostname(),
			telemetryManager.getConnectorStore()
		);
		countersByOperation.forEach(
			(operationType, counters) -> counters.forEach(
				(outcome, count) -> metricFactory.collectNumberMetric(
					endpointHostMonitor,
					REQUESTS_METRIC_FORMAT.formatted(outcome.getLabel(), operationType),
					count.doubleValue(),
					strategyTime
				)
			)
		);
	}
}
