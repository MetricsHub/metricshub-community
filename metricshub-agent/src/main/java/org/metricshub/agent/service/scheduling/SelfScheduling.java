package org.metricshub.agent.service.scheduling;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * MetricsHub Agent
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

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;
import lombok.Builder;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.metricshub.agent.config.AgentConfig;
import org.metricshub.agent.context.AgentInfo;
import org.metricshub.agent.helper.ConfigHelper;
import org.metricshub.agent.opentelemetry.MetricsExporter;
import org.metricshub.agent.opentelemetry.ResourceMeter;
import org.metricshub.agent.opentelemetry.ResourceMeterProvider;
import org.metricshub.agent.opentelemetry.metric.MetricContext;
import org.metricshub.engine.connector.model.metric.MetricType;
import org.metricshub.engine.telemetry.metric.NumberMetric;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.support.PeriodicTrigger;

/**
 * Scheduling class responsible for self-recording tasks in MetricsHub.
 * This class creates and schedules a recorder for collecting MetricsHub agent information.
 */
@Slf4j
public class SelfScheduling extends AbstractScheduling {

	/**
	 * Key used to identify the self-recording task in the schedules map.
	 */
	public static final String METRICSHUB_OVERALL_SELF_TASK_KEY = "metricshub-overall-self-task";
	/**
	 * Description for the overall MetricsHub agent information metric.
	 */
	static final String METRICS_HUB_AGENT_INFORMATION = "MetricsHub agent information.";

	@NonNull
	private AgentInfo agentInfo;

	@NonNull
	private AgentConfig agentConfig;

	/**
	 * Constructs a new instance of {@code SelfScheduling}.
	 *
	 * @param taskScheduler     The task scheduler to use for scheduling.
	 * @param schedules         The map to store scheduled tasks.
	 * @param metricsExporter   The exporter to use for exporting metrics.
	 * @param agentInfo         The information about the MetricsHub agent.
	 * @param agentConfig       The configuration for the MetricsHub agent.
	 */
	@Builder(setterPrefix = "with")
	public SelfScheduling(
		@NonNull final TaskScheduler taskScheduler,
		@NonNull final Map<String, ScheduledFuture<?>> schedules,
		@NonNull final MetricsExporter metricsExporter,
		@NonNull final AgentInfo agentInfo,
		@NonNull final AgentConfig agentConfig
	) {
		super(taskScheduler, schedules, metricsExporter);
		this.agentConfig = agentConfig;
		this.agentInfo = agentInfo;
	}

	@Override
	public void schedule() {
		// Need a periodic trigger because we need the job to be scheduled based on the configured collect period
		final PeriodicTrigger trigger = new PeriodicTrigger(
			Duration.of(agentConfig.getCollectPeriod(), ChronoUnit.SECONDS)
		);

		// Here we go
		final ScheduledFuture<?> scheduledFuture = taskScheduler.schedule(this::recordAndExport, trigger);

		// Save the delayed result-bearing action that can be cancelled
		schedules.put(METRICSHUB_OVERALL_SELF_TASK_KEY, scheduledFuture);

		log.info("Self Recorder scheduled.");
	}

	/**
	 * Records and pushes the agent metric {@value AgentInfo#METRICS_HUB_AGENT_METRIC_NAME}.
	 */
	void recordAndExport() {
		final Map<String, String> resourceAttributes = new HashMap<>();

		// Add our attributes
		ConfigHelper.mergeAttributes(agentInfo.getAttributes(), resourceAttributes);

		// Override with the user's attributes
		ConfigHelper.mergeAttributes(agentConfig.getAttributes(), resourceAttributes);

		// Create a new meter provider with the metrics exporter
		final ResourceMeterProvider meterProvider = new ResourceMeterProvider(metricsExporter);

		// Create a new resource meter
		final ResourceMeter meter = meterProvider.newResourceMeter("org.metricshub.agent", resourceAttributes);

		// Register the metric recorder
		meter.registerRecorder(
			MetricContext.builder().withDescription(METRICS_HUB_AGENT_INFORMATION).withType(MetricType.GAUGE).build(),
			NumberMetric
				.builder()
				.value(1.0)
				.name(AgentInfo.METRICS_HUB_AGENT_METRIC_NAME)
				.collectTime(System.currentTimeMillis())
				.build()
		);

		// Export the metric
		meterProvider.exportMetrics(() ->
			ConfigHelper.configureGlobalLogger(agentConfig.getLoggerLevel(), agentConfig.getOutputDirectory())
		);
	}
}
