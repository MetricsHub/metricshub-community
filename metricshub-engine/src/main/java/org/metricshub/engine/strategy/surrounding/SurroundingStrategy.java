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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.metricshub.engine.client.ClientsExecutor;
import org.metricshub.engine.common.JobInfo;
import org.metricshub.engine.connector.model.Connector;
import org.metricshub.engine.connector.model.monitor.task.source.Source;
import org.metricshub.engine.extension.ExtensionManager;
import org.metricshub.engine.strategy.AbstractStrategy;
import org.metricshub.engine.strategy.source.OrderedSources;
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

	protected Connector connector;

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

		if (sources == null || sources.isEmpty()) {
			log.debug(
				"Hostname {} - Attempted to process {} sources, but none are available for connector {}.",
				hostname,
				jobName,
				connectorId
			);
			return;
		}

		// Construct job information including job name, connector identifier, hostname and monitor type.
		final JobInfo jobInfo = JobInfo
			.builder()
			.hostname(hostname)
			.connectorId(connectorId)
			.jobName(jobName)
			.monitorType("none")
			.build();

		// Build and order sources based on dependencies.
		final OrderedSources orderedSources = OrderedSources
			.builder()
			.sources(sources, new ArrayList<>(), getSourceDependencies(), jobInfo)
			.build();

		// Process the ordered sources along with computes, based on the constructed job information.
		processSourcesAndComputes(orderedSources.getSources(), jobInfo);

		final long jobEndTime = System.currentTimeMillis();
		// Set the job duration metric in the host monitor
		setJobDurationMetric(jobName, connectorId, jobStartTime, jobEndTime);
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
