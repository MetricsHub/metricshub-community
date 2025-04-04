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
import java.util.Objects;
import java.util.concurrent.ScheduledFuture;
import lombok.Builder;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.metricshub.agent.config.AgentConfig;
import org.metricshub.agent.config.ResourceGroupConfig;
import org.metricshub.agent.helper.ConfigHelper;
import org.metricshub.agent.opentelemetry.MetricsExporter;
import org.metricshub.agent.opentelemetry.ResourceMeter;
import org.metricshub.agent.opentelemetry.ResourceMeterProvider;
import org.metricshub.agent.opentelemetry.metric.MetricContext;
import org.metricshub.engine.connector.model.metric.MetricType;
import org.metricshub.engine.telemetry.MetricFactory;
import org.metricshub.engine.telemetry.metric.NumberMetric;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.support.PeriodicTrigger;

/**
 * Scheduling class responsible for scheduling resource group-related tasks in
 * MetricsHub. This class creates and schedules recorders for metrics defined in
 * a {@link ResourceGroupConfig}, and also schedules periodic flush tasks for
 * the resource group.
 */
@Slf4j
public class ResourceGroupScheduling extends AbstractScheduling {

	/**
	 * Format for creating unique keys for identifying the scheduled tasks related
	 * to a specific resource group.
	 */
	public static final String METRICSHUB_RESOURCE_GROUP_KEY_FORMAT = "metricshub-resource-group-%s";

	/**
	 * Power Usage Effectiveness
	 */
	public static final String HW_SITE_PUE_METRIC = "hw.site.pue";

	/**
	 * Electricity cost per kilowatt-hour
	 */
	public static final String HW_SITE_ELECTRICITY_COST_METRIC = "hw.site.electricity_cost";

	/**
	 * Carbon dioxide produced per kilowatt-hour
	 */
	public static final String HW_SITE_CARBON_INTENSITY_METRIC = "hw.site.carbon_intensity";

	/**
	 * Known metric units
	 */
	private static final Map<String, String> KNOWN_METRIC_UNITS = Map.of(
		HW_SITE_CARBON_INTENSITY_METRIC,
		"g",
		HW_SITE_ELECTRICITY_COST_METRIC,
		"",
		HW_SITE_PUE_METRIC,
		"1"
	);

	@NonNull
	private ResourceGroupConfig resourceGroupConfig;

	@NonNull
	private String resourceGroupKey;

	@NonNull
	private AgentConfig agentConfig;

	/**
	 * Constructs a new instance of {@code ResourceGroupScheduling}.
	 *
	 * @param taskScheduler       The task scheduler to use for scheduling.
	 * @param schedules           The map to store scheduled tasks.
	 * @param metricsExporter     The exporter to use for exporting metrics.
	 * @param resourceGroupKey    Key for identifying the resource group.
	 * @param resourceGroupConfig Configuration for the resource group.
	 * @param agentConfig         Configuration for the MetricsHub agent.
	 */
	@Builder(setterPrefix = "with")
	public ResourceGroupScheduling(
		@NonNull final TaskScheduler taskScheduler,
		@NonNull final Map<String, ScheduledFuture<?>> schedules,
		@NonNull final MetricsExporter metricsExporter,
		@NonNull final String resourceGroupKey,
		@NonNull final ResourceGroupConfig resourceGroupConfig,
		@NonNull final AgentConfig agentConfig
	) {
		super(taskScheduler, schedules, metricsExporter);
		this.resourceGroupConfig = resourceGroupConfig;
		this.resourceGroupKey = resourceGroupKey;
		this.agentConfig = agentConfig;
	}

	@Override
	public void schedule() {
		// Need a periodic trigger because we need the job to be scheduled based on the configured collect period
		final PeriodicTrigger trigger = new PeriodicTrigger(
			Duration.of(agentConfig.getCollectPeriod(), ChronoUnit.SECONDS)
		);

		// Schedule the flush task
		final ScheduledFuture<?> scheduledFuture = taskScheduler.schedule(this::recordAndExport, trigger);

		// Save the delayed result-bearing action that can be cancelled
		schedules.put(String.format(METRICSHUB_RESOURCE_GROUP_KEY_FORMAT, resourceGroupKey), scheduledFuture);

		log.info("Resource Group {} scheduled.", resourceGroupKey);
	}

	/**
	 * Records and pushes resource group metrics.
	 */
	void recordAndExport() {
		// Create a new meter provider with the metrics exporter
		final ResourceMeterProvider meterProvider = new ResourceMeterProvider(metricsExporter);

		final Map<String, String> resourceAttributes = new HashMap<>();
		ConfigHelper.mergeAttributes(agentConfig.getAttributes(), resourceAttributes);
		ConfigHelper.mergeAttributes(resourceGroupConfig.getAttributes(), resourceAttributes);

		// Create a new resource meter
		final ResourceMeter meter = meterProvider.newResourceMeter(
			"org.metricshub.resource.group.%s".formatted(resourceGroupKey),
			resourceAttributes
		);

		// Initialize a simple metric recorder for each metric defined in the configuration
		resourceGroupConfig
			.getMetrics()
			.entrySet()
			.stream()
			.filter(metricEntry -> Objects.nonNull(metricEntry.getValue()))
			.forEach(metricEntry -> {
				final String metricKey = metricEntry.getKey();

				// The metric name can define a set of attributes
				final String metricName = MetricFactory.extractName(metricKey);

				meter.registerRecorder(
					MetricContext
						.builder()
						.withDescription("Reports metric %s".formatted(metricName))
						.withType(MetricType.GAUGE)
						.withUnit(KNOWN_METRIC_UNITS.get(metricName))
						.build(),
					NumberMetric
						.builder()
						.value(metricEntry.getValue())
						.name(metricName)
						.attributes(MetricFactory.extractAttributesFromMetricName(metricKey))
						.collectTime(System.currentTimeMillis())
						.build()
				);
			});

		meterProvider.exportMetrics(() ->
			ConfigHelper.configureGlobalLogger(agentConfig.getLoggerLevel(), agentConfig.getOutputDirectory())
		);
	}
}
