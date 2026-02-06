package org.metricshub.agent.service.task;

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

import static org.metricshub.agent.helper.ConfigHelper.getLoggerLevel;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import lombok.Data;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.ThreadContext;
import org.metricshub.agent.config.ResourceConfig;
import org.metricshub.agent.helper.ConfigHelper;
import org.metricshub.agent.helper.OtelHelper;
import org.metricshub.agent.opentelemetry.ResourceMeter;
import org.metricshub.agent.opentelemetry.ResourceMeterProvider;
import org.metricshub.agent.opentelemetry.metric.MetricContext;
import org.metricshub.engine.client.ClientsExecutor;
import org.metricshub.engine.common.helpers.MetricsHubConstants;
import org.metricshub.engine.connector.model.ConnectorStore;
import org.metricshub.engine.connector.model.metric.MetricDefinition;
import org.metricshub.engine.extension.ExtensionManager;
import org.metricshub.engine.extension.IMetricEnrichmentExtension;
import org.metricshub.engine.strategy.collect.CollectStrategy;
import org.metricshub.engine.strategy.collect.PrepareCollectStrategy;
import org.metricshub.engine.strategy.collect.ProtocolHealthCheckStrategy;
import org.metricshub.engine.strategy.detection.DetectionStrategy;
import org.metricshub.engine.strategy.discovery.DiscoveryStrategy;
import org.metricshub.engine.strategy.simple.SimpleStrategy;
import org.metricshub.engine.telemetry.MetricFactory;
import org.metricshub.engine.telemetry.Monitor;
import org.metricshub.engine.telemetry.TelemetryManager;
import org.metricshub.engine.telemetry.metric.AbstractMetric;
import org.metricshub.hardware.strategy.HardwareMonitorNameGenerationStrategy;
import org.metricshub.hardware.strategy.HardwarePostCollectStrategy;
import org.metricshub.hardware.strategy.HardwarePostDiscoveryStrategy;
import org.metricshub.hardware.strategy.HardwareStrategy;

/**
 * Task responsible for running the monitoring process, including detection, discovery, and collection strategies.
 */
@RequiredArgsConstructor
@Data
@Slf4j
public class MonitoringTask implements Runnable {

	private static final String GENERIC_METRIC_DESCRIPTION_FORMAT = "Reports Metric %s";

	@NonNull
	private final MonitoringTaskInfo monitoringTaskInfo;

	private int numberOfCollects;

	private Map<String, String> hostResourceAttributes = new HashMap<>();

	@Override
	public void run() {
		final TelemetryManager telemetryManager = monitoringTaskInfo.getTelemetryManager();
		final ResourceConfig resourceConfig = monitoringTaskInfo.getResourceConfig();
		final int discoveryCycle = resourceConfig.getDiscoveryCycle();
		final ExtensionManager extensionManager = monitoringTaskInfo.getExtensionManager();
		final List<IMetricEnrichmentExtension> metricEnrichmentExtensions = extensionManager.resolveMetricEnrichmentExtensions(
			resourceConfig.getEnrichments()
		);

		final String hostId = telemetryManager.getHostConfiguration().getHostId();

		configureLoggerContext(hostId);

		final ClientsExecutor clientsExecutor = new ClientsExecutor(telemetryManager);

		// Are we supposed to run the discovery?
		final long discoveryTime = System.currentTimeMillis();
		if (numberOfCollects == 0) {
			log.info("Calling the engine to discover resource: {}.", hostId);

			// Run detection and discovery strategies first

			telemetryManager.run(
				new DetectionStrategy(telemetryManager, discoveryTime, clientsExecutor, extensionManager),
				new DiscoveryStrategy(telemetryManager, discoveryTime, clientsExecutor, extensionManager),
				new SimpleStrategy(telemetryManager, discoveryTime, clientsExecutor, extensionManager),
				new HardwarePostDiscoveryStrategy(telemetryManager, discoveryTime, clientsExecutor, extensionManager),
				new HardwareMonitorNameGenerationStrategy(telemetryManager, discoveryTime, clientsExecutor, extensionManager)
			);

			applyShiftLeftEnrichments(telemetryManager, metricEnrichmentExtensions);

			/*
			 * Metrics are recorded and exported after each collection and are only refreshed when they are explicitly updated.
			 * During the collection cycle, the discovery-related metrics may expire due to the "collect time".
			 * To prevent this expiration, send the metrics at the moment of discovery, ensuring they have the correct "collect time".
			 * This guarantees that the metrics remain valid and are not prematurely expired.
			 */

			// Initialize the host resource attributes since they are required for the host monitor as well as the other monitors
			initHostAttributes(telemetryManager, resourceConfig);

			// Record metrics and export them all
			registerTelemetryManagerRecorders(telemetryManager, metricEnrichmentExtensions)
				.exportMetrics(() -> configureLoggerContext(hostId));
		}

		log.info("Calling the engine to collect resource: {}.", hostId);

		final long collectTime = System.currentTimeMillis();

		// One more, run only prepare, collect simple and post strategies
		telemetryManager.run(
			new PrepareCollectStrategy(telemetryManager, collectTime, clientsExecutor, extensionManager),
			new ProtocolHealthCheckStrategy(telemetryManager, collectTime, clientsExecutor, extensionManager),
			new CollectStrategy(telemetryManager, collectTime, clientsExecutor, extensionManager),
			new SimpleStrategy(telemetryManager, collectTime, clientsExecutor, extensionManager),
			new HardwarePostCollectStrategy(telemetryManager, collectTime, clientsExecutor, extensionManager),
			new HardwareMonitorNameGenerationStrategy(telemetryManager, collectTime, clientsExecutor, extensionManager)
		);

		// Run the hardware strategy
		telemetryManager.run(new HardwareStrategy(telemetryManager, collectTime));

		applyShiftLeftEnrichments(telemetryManager, metricEnrichmentExtensions);

		// Record metrics and export them all
		registerTelemetryManagerRecorders(telemetryManager, metricEnrichmentExtensions)
			.exportMetrics(() -> configureLoggerContext(hostId));

		// Increment the number of collects
		numberOfCollects++;

		// Reset the number of collects
		if (numberOfCollects >= discoveryCycle) {
			numberOfCollects = 0;
		}
	}

	/**
	 * Register all telemetry manager recorders.
	 *
	 * @param telemetryManager Wraps monitors and metrics
	 * @param metricEnrichmentExtensions metric enrichment extensions for export
	 * @return The {@link ResourceMeterProvider} instance
	 */
	ResourceMeterProvider registerTelemetryManagerRecorders(
		final TelemetryManager telemetryManager,
		final List<IMetricEnrichmentExtension> metricEnrichmentExtensions
	) {
		// Retrieve the connector store that has been prepared within the global context
		final ConnectorStore connectorStore = telemetryManager.getConnectorStore();
		// Create a new ResourceMeterProvider instance with the metrics exporter
		final ResourceMeterProvider provider = new ResourceMeterProvider(
			monitoringTaskInfo.getMetricsExporter(),
			metricEnrichmentExtensions
		);

		telemetryManager
			.getMonitors()
			.values()
			.stream()
			.map(Map::values)
			.flatMap(Collection::stream)
			.forEach(monitor -> {
				final boolean isEndpointHost = monitor.isEndpointHost();
				if (isEndpointHost) {
					// The host's metric definitions cannot be null because they are available as resources in metricshub-host-metrics.yaml
					final Map<String, MetricDefinition> hostMetricDefinitions = monitoringTaskInfo
						.getHostMetricDefinitions()
						.metrics();

					// New meter for this host
					final ResourceMeter meter = provider.newResourceMeter(monitor.getId(), hostResourceAttributes);

					// Initialize endpoint host metric recorders
					registerMonitorMetricRecorders(monitor, telemetryManager, hostMetricDefinitions, meter);
				} else {
					// Resource attributes
					final Map<String, String> attributes = new HashMap<>();
					ConfigHelper.mergeAttributes(hostResourceAttributes, attributes);
					ConfigHelper.mergeAttributes(monitor.getAttributes(), attributes);

					// New meter for this monitor
					final ResourceMeter meter = provider.newResourceMeter(monitor.getId(), attributes);

					registerMonitorMetricRecorders(
						monitor,
						telemetryManager,
						ConfigHelper.fetchMetricDefinitions(
							connectorStore,
							monitor.getAttribute(MetricsHubConstants.MONITOR_ATTRIBUTE_CONNECTOR_ID),
							monitor.getType()
						),
						meter
					);
				}
			});

		return provider;
	}

	/**
	 * Register all metric recorders for the monitor.
	 *
	 * @param monitor             {@link Monitor} instance
	 * @param telemetryManager    Wraps monitors and metrics
	 * @param metricDefinitionMap Map of Metric definitions
	 * @param meter               The resource meter used to record the metrics
	 */
	void registerMonitorMetricRecorders(
		final Monitor monitor,
		final TelemetryManager telemetryManager,
		final Map<String, MetricDefinition> metricDefinitionMap,
		final ResourceMeter meter
	) {
		monitor
			.getMetrics()
			.entrySet()
			.stream()
			.filter(entry -> Objects.nonNull(entry.getValue()))
			.filter(entry -> OtelHelper.isAcceptedKey(entry.getKey()))
			.forEach(metricEntry -> registerMetricRecorder(metricDefinitionMap, metricEntry, meter));
	}

	/**
	 * Register a recorder for the given metric entry
	 *
	 * @param metricDefinitionMap Map of Metric definitions (E.g. metric definitions from Hardware.yaml or Storage.yaml)
	 * @param metricEntry         Key-value where the key is the unique metric key and the value
	 *                            is the {@link AbstractMetric}
	 * @param meter               The resource meter used to record the metric
	 */
	void registerMetricRecorder(
		final Map<String, MetricDefinition> metricDefinitionMap,
		final Entry<String, AbstractMetric> metricEntry,
		final ResourceMeter meter
	) {
		// Retrieve the metric unique key
		final String metricKey = metricEntry.getKey();

		// Extract the metric name from the metric key. E.g. extract hw.power from hw.power{hw.type="fan"} // NOSONAR
		final String metricName = MetricFactory.extractName(metricKey);

		// Get the metric definition from the metric definition map
		final MetricDefinition metricDefinition = lookupMetricDefinition(metricName, metricDefinitionMap);

		final AbstractMetric metric = metricEntry.getValue();

		// Registers a metric recorder to be invoked when recording the metric
		meter.registerRecorder(
			MetricContext
				.builder()
				.withDescription(metricDefinition.getDescription())
				.withType(metricDefinition.getType().get())
				.withUnit(metricDefinition.getUnit())
				.withIsSuppressZerosCompression(monitoringTaskInfo.isSuppressZerosCompression())
				.build(),
			metric
		);
	}

	/**
	 * Search the {@link MetricDefinition} instance defined for the given metric.
	 *
	 * @param metricName          The name of the metric e.g. hw.status.
	 * @param metricDefinitionMap All the existing metric definitions.
	 * @return {@link MetricDefinition} instance, never <code>null</code>.
	 */
	static MetricDefinition lookupMetricDefinition(
		final String metricName,
		final Map<String, MetricDefinition> metricDefinitionMap
	) {
		return Optional
			.ofNullable(metricDefinitionMap.get(metricName))
			.orElseGet(() ->
				MetricDefinition.builder().description(String.format(GENERIC_METRIC_DESCRIPTION_FORMAT, metricName)).build()
			);
	}

	/**
	 * Apply shift-left enrichment using the configured extensions.
	 *
	 * @param telemetryManager telemetry manager to enrich
	 * @param enrichers configured metric enrichment extensions
	 */
	private void applyShiftLeftEnrichments(
		final TelemetryManager telemetryManager,
		final List<IMetricEnrichmentExtension> enrichers
	) {
		if (enrichers == null || enrichers.isEmpty()) {
			return;
		}
		for (IMetricEnrichmentExtension enricher : enrichers) {
			try {
				enricher.enrichShiftLeft(telemetryManager);
			} catch (Exception e) {
				log.error(
					"Failed to apply shift-left enrichment with {}. Error: {}",
					enricher != null ? enricher.getClass().getSimpleName() : "<null>",
					e.getMessage()
				);
				log.debug("Failed to apply shift-left enrichment. Stack trace:", e);
			}
		}
	}

	/**
	 * Initialize the host resource attributes since they are required for the host monitor as well as the other monitors.
	 *
	 * @param telemetryManager Wraps monitors
	 * @param resourceConfig   The resource configuration
	 */
	void initHostAttributes(final TelemetryManager telemetryManager, final ResourceConfig resourceConfig) {
		// Create a resource if it hasn't been created during the previous cycle
		if (hostResourceAttributes.isEmpty()) {
			// Create the resource
			final Monitor hostMonitor = telemetryManager.getEndpointHostMonitor();
			final Map<String, String> userAttributes = resourceConfig.getAttributes();
			hostResourceAttributes = OtelHelper.buildHostAttributes(hostMonitor.getAttributes(), userAttributes);
		}
	}

	/**
	 * Configure the logger context with the hostId, loggerLevel and outputDirectory.
	 *
	 * @param logId	Unique identifier of used by the logId context attribute.
	 */
	void configureLoggerContext(final String logId) {
		ThreadContext.put("logId", String.format("metricshub-agent-%s", logId));

		final ResourceConfig resourceConfig = monitoringTaskInfo.getResourceConfig();

		ThreadContext.put("loggerLevel", getLoggerLevel(resourceConfig.getLoggerLevel()).name());

		final String outputDirectory = resourceConfig.getOutputDirectory();
		if (outputDirectory != null) {
			ThreadContext.put("outputDirectory", outputDirectory);
		}
	}
}
