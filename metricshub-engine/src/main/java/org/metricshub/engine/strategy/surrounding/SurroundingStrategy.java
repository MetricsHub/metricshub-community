package org.metricshub.engine.strategy.surrounding;

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

import static org.metricshub.engine.common.helpers.MetricsHubConstants.SOURCE_REF_PATTERN;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.stream.Collectors;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NonNull;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.metricshub.engine.client.ClientsExecutor;
import org.metricshub.engine.common.JobInfo;
import org.metricshub.engine.common.helpers.ThreadHelper;
import org.metricshub.engine.connector.model.Connector;
import org.metricshub.engine.connector.model.monitor.task.source.Source;
import org.metricshub.engine.extension.ExtensionManager;
import org.metricshub.engine.strategy.AbstractStrategy;
import org.metricshub.engine.strategy.source.OrderedSources;
import org.metricshub.engine.strategy.source.SourceTable;
import org.metricshub.engine.telemetry.ConnectorNamespace;
import org.metricshub.engine.telemetry.TelemetryManager;

/**
 * Strategy for executing tasks that surround the core processing of sources, such as "beforeAll"
 * and "afterAll" operations. Inherits from {@link AbstractStrategy} and manages source execution
 * for a given {@link Connector}.
 * <p>
 * Leverages Lombok's {@link Slf4j} for logging, {@link Data} for auto-generating getters and setters,
 * and {@link EqualsAndHashCode} to ensure proper equality and hash code generation.
 * </p>
 */
@Slf4j
@Data
@EqualsAndHashCode(callSuper = true)
public abstract class SurroundingStrategy extends AbstractStrategy {

	/**
	 * How long a beforeAll may still run once the strategy thread is interrupted, in seconds.
	 */
	static final long BEFORE_ALL_GRACE_SECONDS = 5;

	/**
	 * How long an afterAll may still run once the strategy thread is interrupted, in seconds.
	 */
	static final long AFTER_ALL_GRACE_SECONDS = 2;

	protected Connector connector;

	/**
	 * Whether the last {@link #run()} of the beforeAll gave this run a session the afterAll must close: it stored every
	 * beforeAll table the afterAll references (at least one beforeAll table when the afterAll references none), or it had
	 * no sources and ran on a thread not interrupted. A beforeAll cut short before its login answered leaves the table
	 * of an earlier run: the afterAll would send that run's token. Read by the strategy that runs the beforeAll.
	 */
	@EqualsAndHashCode.Exclude
	@ToString.Exclude
	private volatile boolean opened;

	/**
	 * Constructs a {@code SurroundingStrategy} with the necessary components for handling
	 * surrounding strategies (E.g. "beforeAll" and "afterAll") source processing.
	 *
	 * @param telemetryManager  The manager responsible for telemetry (monitors and metrics).
	 * @param strategyTime      The time allocated for strategy execution.
	 * @param clientsExecutor   Executor service for managing client-related tasks.
	 * @param connector         The connector defining sources and their dependencies.
	 * @param extensionManager  The manager handling various extensions for strategy execution.
	 */
	protected SurroundingStrategy(
		@NonNull final TelemetryManager telemetryManager,
		@NonNull final Long strategyTime,
		@NonNull final ClientsExecutor clientsExecutor,
		@NonNull final Connector connector,
		@NonNull final ExtensionManager extensionManager
	) {
		super(telemetryManager, strategyTime, clientsExecutor, extensionManager);
		this.connector = connector;
	}

	/**
	 * Executes the surrounding sources (E.g. beforeAll, afterAll) for the configured connector.
	 * This method logs relevant information and processes sources in a specific order based on their dependencies.
	 * <p>
	 * If no sources are available for processing, the method logs this information and returns early.
	 * </p>
	 */
	@Override
	public void run() {
		final long jobStartTime = System.currentTimeMillis();
		// Retrieve the connector's identifier and hostname for logging and processing.
		final String connectorId = connector.getCompiledFilename();
		final String hostname = telemetryManager.getHostname();
		final String jobName = getJobName();

		// Fetch surrounding sources for the connector.
		final Map<String, Source> sources = getSurroundingSources();

		// Nothing sent on a thread already interrupted, or until the sources start
		opened = false;
		if (sources == null || sources.isEmpty()) {
			opened = !isInterrupted();
			log.debug(
				"Hostname {} - Attempted to process {} sources, but none are available for connector {}.",
				hostname,
				jobName,
				connectorId
			);
			return;
		}

		final boolean beforeAll = this instanceof BeforeAllStrategy;

		// A run already cut short opens nothing (the shelter below clears the interrupt)
		if (beforeAll && isInterrupted()) {
			log.warn(
				"Hostname {} - Thread interrupted: skipping the beforeAll sources of connector {}.",
				hostname,
				connectorId
			);
			return;
		}

		// The tables of an earlier run: a table this run stores is a new instance
		final ConnectorNamespace namespace = telemetryManager.getHostProperties().getConnectorNamespace(connectorId);
		final Map<String, SourceTable> earlierTables = new HashMap<>();
		sources.values().forEach(source -> earlierTables.put(source.getKey(), namespace.getSourceTable(source.getKey())));

		// On a child thread that ignores the interrupt for a grace period: a login answered within it keeps its token,
		// and the logout of a job cut short is still sent (see AbstractStrategy.CLEANUP_WINDOW_MILLIS). A beforeAll whose
		// caller is interrupted only ends the source it is in, while an afterAll runs all its sources
		try {
			ThreadHelper.shelter(() -> {
				// Construct job information including job name, connector identifier, hostname and monitor type.
				final JobInfo jobInfo = JobInfo.builder()
					.hostname(hostname)
					.connectorId(connectorId)
					.jobName(jobName)
					.monitorType("none")
					.build();

				// Build and order sources based on dependencies.
				final OrderedSources orderedSources = OrderedSources.builder()
					.sources(sources, new ArrayList<>(), getSourceDependencies(), jobInfo)
					.build();

				// Process the ordered sources along with computes, based on the constructed job information.
				processSourcesAndComputes(orderedSources.getSources(), jobInfo);

				final long jobEndTime = System.currentTimeMillis();
				// Set the job duration metric in the host monitor
				setJobDurationMetric(jobName, connectorId, jobStartTime, jobEndTime);
			}, beforeAll ? BEFORE_ALL_GRACE_SECONDS : AFTER_ALL_GRACE_SECONDS, beforeAll);
		} finally {
			// Even when a source throws: a login stored before it still needs its logout
			if (beforeAll) {
				opened = storedWhatTheAfterAllNeeds(namespace, earlierTables);
			}
		}
	}

	/**
	 * Whether this run stored every beforeAll table that the afterAll sources reference, or at least one beforeAll table
	 * when they reference none.
	 *
	 * @param namespace     The connector namespace
	 * @param earlierTables The tables of the beforeAll sources before this run, by source key
	 * @return {@code true} if the afterAll reads tables of this run only
	 */
	private boolean storedWhatTheAfterAllNeeds(
		final ConnectorNamespace namespace,
		final Map<String, SourceTable> earlierTables
	) {
		final Set<String> stored = earlierTables.entrySet().stream().filter(entry -> {
			final SourceTable table = namespace.getSourceTable(entry.getKey());
			return table != null && table != entry.getValue();
		}).map(Map.Entry::getKey).collect(Collectors.toSet());

		final Set<String> needed = new HashSet<>();
		final Map<String, Source> afterAll = connector.getAfterAll();
		if (afterAll != null) {
			afterAll.values().forEach(source -> source.getReferences().forEach(reference -> {
				final Matcher matcher = SOURCE_REF_PATTERN.matcher(reference);
				while (matcher.find()) {
					if (earlierTables.containsKey(matcher.group())) {
						needed.add(matcher.group());
					}
				}
			}));
		}
		return needed.isEmpty() ? !stored.isEmpty() : stored.containsAll(needed);
	}

	/**
	 * Retrieves the surrounding sources for the strategy.
	 *
	 * @return A map of surrounding sources (E.g. beforeAll, afterAll) for the strategy.
	 */
	protected abstract Map<String, Source> getSurroundingSources();

	/**
	 * Retrieves the job name for the strategy.
	 *
	 * @return a string representing the job name for the strategy.
	 */
	protected abstract String getJobName();

	/**
	 * Retrieves the source dependencies for the strategy.
	 *
	 * @return a list of sets of strings representing the source dependencies.
	 */
	protected abstract List<Set<String>> getSourceDependencies();
}
