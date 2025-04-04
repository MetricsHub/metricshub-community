package org.metricshub.agent.service.scheduling;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * MetricsHub Agent
 * ჻჻჻჻჻჻
 * Copyright 2023 - 2024 Sentry Software
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

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;
import lombok.Builder;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.metricshub.agent.config.ResourceConfig;
import org.metricshub.agent.context.MetricDefinitions;
import org.metricshub.agent.helper.ConfigHelper;
import org.metricshub.agent.opentelemetry.MetricsExporter;
import org.metricshub.agent.service.task.MonitoringTask;
import org.metricshub.agent.service.task.MonitoringTaskInfo;
import org.metricshub.engine.extension.ExtensionManager;
import org.metricshub.engine.telemetry.TelemetryManager;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.support.PeriodicTrigger;

/**
 * Scheduling class responsible for scheduling monitoring tasks for a specific
 * resource in MetricsHub. This class creates and schedules a
 * {@link MonitoringTask} to collect metrics for the specified resource at a
 * periodic interval based on the configured collect period.
 */
@Slf4j
public class ResourceScheduling extends AbstractScheduling {

	/**
	 * Format for creating unique keys for identifying the scheduled tasks related
	 * to a specific resource.
	 */
	public static final String METRICSHUB_RESOURCE_KEY_FORMAT = "metricshub-resource-%s-%s";

	@NonNull
	private String resourceGroupKey;

	@NonNull
	private String resourceKey;

	@NonNull
	private ResourceConfig resourceConfig;

	@NonNull
	private TelemetryManager telemetryManager;

	@NonNull
	private MetricDefinitions hostMetricDefinitions;

	@NonNull
	private ExtensionManager extensionManager;

	/**
	 * Constructs a new instance of {@code ResourceScheduling}.
	 *
	 * @param taskScheduler         The task scheduler to use for scheduling.
	 * @param schedules             The map to store scheduled tasks.
	 * @param metricsExporter       The exporter to use for exporting metrics.
	 * @param resourceGroupKey      Key for identifying the resource group to which the resource belongs.
	 * @param resourceKey           Key for identifying the resource.
	 * @param resourceConfig        Configuration for the monitored resource.
	 * @param telemetryManager      Telemetry manager responsible for collecting and processing metrics for the resource.
	 * @param hostMetricDefinitions Definitions of metrics for the host.
	 * @param extensionManager      Manages and aggregates various types of extensions used within MetricsHub.
	 */
	@Builder(setterPrefix = "with")
	public ResourceScheduling(
		@NonNull final TaskScheduler taskScheduler,
		@NonNull final Map<String, ScheduledFuture<?>> schedules,
		@NonNull final MetricsExporter metricsExporter,
		@NonNull final String resourceGroupKey,
		@NonNull final String resourceKey,
		@NonNull final ResourceConfig resourceConfig,
		@NonNull final TelemetryManager telemetryManager,
		@NonNull final MetricDefinitions hostMetricDefinitions,
		@NonNull final ExtensionManager extensionManager
	) {
		super(taskScheduler, schedules, metricsExporter);
		this.resourceGroupKey = resourceGroupKey;
		this.resourceKey = resourceKey;
		this.resourceConfig = resourceConfig;
		this.telemetryManager = telemetryManager;
		this.hostMetricDefinitions = hostMetricDefinitions;
		this.extensionManager = extensionManager;
	}

	@Override
	public void schedule() {
		// Need a periodic trigger because we need the job to be scheduled based on the configured collect period
		final PeriodicTrigger trigger = new PeriodicTrigger(
			Duration.of(resourceConfig.getCollectPeriod(), ChronoUnit.SECONDS)
		);

		// Create the monitoring task
		final MonitoringTask monitoringTask = new MonitoringTask(
			MonitoringTaskInfo
				.builder()
				.telemetryManager(telemetryManager)
				.resourceConfig(resourceConfig)
				.resourceGroupKey(resourceGroupKey)
				.resourceKey(resourceKey)
				.metricsExporter(metricsExporter)
				.hostMetricDefinitions(hostMetricDefinitions)
				.extensionManager(extensionManager)
				.isSuppressZerosCompression(ConfigHelper.isSuppressZerosCompression(resourceConfig.getStateSetCompression()))
				.build()
		);

		// Here we go
		final ScheduledFuture<?> scheduledFuture = taskScheduler.schedule(monitoringTask, trigger);

		// Don't forget to store the scheduled task in case we want to cancel it due to a configuration change
		schedules.put(String.format(METRICSHUB_RESOURCE_KEY_FORMAT, resourceGroupKey, resourceKey), scheduledFuture);

		log.info("Scheduled job for resource id {} defined in resource group id {}.", resourceKey, resourceGroupKey);
	}
}
