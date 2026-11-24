package org.metricshub.agent.service.task;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.metricshub.agent.helper.TestConstants.COMPUTE_HOST_TYPE;
import static org.metricshub.agent.helper.TestConstants.HOSTNAME;
import static org.metricshub.agent.helper.TestConstants.HOST_TYPE_ATTRIBUTE_KEY;
import static org.metricshub.agent.helper.TestConstants.OS_LINUX;
import static org.metricshub.agent.helper.TestConstants.OS_TYPE_ATTRIBUTE_KEY;
import static org.metricshub.engine.common.helpers.MetricsHubConstants.HOST_NAME;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.opentelemetry.proto.collector.metrics.v1.ExportMetricsServiceRequest;
import io.opentelemetry.proto.common.v1.KeyValue;
import io.opentelemetry.proto.metrics.v1.AggregationTemporality;
import io.opentelemetry.proto.metrics.v1.Gauge;
import io.opentelemetry.proto.metrics.v1.Metric;
import io.opentelemetry.proto.metrics.v1.NumberDataPoint;
import io.opentelemetry.proto.metrics.v1.ResourceMetrics;
import io.opentelemetry.proto.metrics.v1.ScopeMetrics;
import io.opentelemetry.proto.resource.v1.Resource;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.metricshub.agent.config.ResourceConfig;
import org.metricshub.agent.config.StateSetMetricCompression;
import org.metricshub.agent.context.MetricDefinitions;
import org.metricshub.agent.helper.ConfigHelper;
import org.metricshub.agent.opentelemetry.MetricsExporter;
import org.metricshub.agent.opentelemetry.client.NoopClient;
import org.metricshub.agent.service.TestHelper;
import org.metricshub.engine.client.ClientsExecutor;
import org.metricshub.engine.common.helpers.KnownMonitorType;
import org.metricshub.engine.common.helpers.MapHelper;
import org.metricshub.engine.configuration.HostConfiguration;
import org.metricshub.engine.connector.model.Connector;
import org.metricshub.engine.connector.model.ConnectorStore;
import org.metricshub.engine.connector.model.common.DeviceKind;
import org.metricshub.engine.connector.model.metric.MetricDefinition;
import org.metricshub.engine.connector.model.metric.MetricType;
import org.metricshub.engine.extension.ExtensionManager;
import org.metricshub.engine.strategy.AbstractStrategy;
import org.metricshub.engine.strategy.IStrategy;
import org.metricshub.engine.strategy.collect.CollectStrategy;
import org.metricshub.engine.strategy.collect.PrepareCollectStrategy;
import org.metricshub.engine.strategy.collect.ProtocolHealthCheckStrategy;
import org.metricshub.engine.strategy.detection.DetectionStrategy;
import org.metricshub.engine.strategy.discovery.DiscoveryStrategy;
import org.metricshub.engine.strategy.simple.SimpleStrategy;
import org.metricshub.engine.telemetry.MetricFactory;
import org.metricshub.engine.telemetry.Monitor;
import org.metricshub.engine.telemetry.MonitorFactory;
import org.metricshub.engine.telemetry.RequestOutcome;
import org.metricshub.engine.telemetry.TelemetryManager;
import org.metricshub.extension.snmp.SnmpConfiguration;
import org.metricshub.hardware.strategy.HardwareMonitorNameGenerationStrategy;
import org.metricshub.hardware.strategy.HardwarePostCollectStrategy;
import org.metricshub.hardware.strategy.HardwarePostDiscoveryStrategy;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MonitoringTaskTest {

	@Mock
	private MonitoringTaskInfo monitoringTaskInfoMock;

	@InjectMocks
	private MonitoringTask monitoringTask;

	private static HostConfiguration hostConfiguration;

	@BeforeAll
	static void beforeAll() {
		hostConfiguration = HostConfiguration.builder()
			.hostname(HOSTNAME)
			.hostId(HOSTNAME)
			.hostType(DeviceKind.LINUX)
			.configurations(Map.of(SnmpConfiguration.class, SnmpConfiguration.builder().build()))
			.build();
	}

	@Test
	void testConfigureLoggerContext() {
		doReturn(ResourceConfig.builder().outputDirectory(null).build()).when(monitoringTaskInfoMock).getResourceConfig();

		final String logId = "test";

		assertDoesNotThrow(() -> monitoringTask.configureLoggerContext(logId));

		doReturn(ResourceConfig.builder().outputDirectory("dir").build()).when(monitoringTaskInfoMock).getResourceConfig();

		assertDoesNotThrow(() -> monitoringTask.configureLoggerContext(logId));
	}

	@Test
	void testRun() {
		final Monitor host = Monitor.builder().id("id").build();
		host.addAttribute(HOST_NAME, HOSTNAME);
		host.addAttribute(HOST_TYPE_ATTRIBUTE_KEY, COMPUTE_HOST_TYPE);
		host.addAttribute(OS_TYPE_ATTRIBUTE_KEY, OS_LINUX);

		final TelemetryManager telemetryManagerMock = spy(TelemetryManager.class);

		doReturn(telemetryManagerMock).when(monitoringTaskInfoMock).getTelemetryManager();
		doReturn(host).when(telemetryManagerMock).getEndpointHostMonitor();
		doReturn(
			ResourceConfig.builder()
				.loggerLevel("OFF")
				.attributes(Map.of(HOST_NAME, HOSTNAME, HOST_TYPE_ATTRIBUTE_KEY, OS_LINUX))
				.discoveryCycle(4)
				.resolveHostnameToFqdn(true)
				.stateSetCompression(StateSetMetricCompression.SUPPRESS_ZEROS)
				.build()
		).when(monitoringTaskInfoMock).getResourceConfig();
		doReturn(hostConfiguration).when(telemetryManagerMock).getHostConfiguration();

		doNothing().when(telemetryManagerMock).run(any(IStrategy[].class));

		doReturn(ExtensionManager.empty()).when(monitoringTaskInfoMock).getExtensionManager();

		doReturn(MetricsExporter.builder().withClient(new NoopClient()).build()).when(monitoringTaskInfoMock)
			.getMetricsExporter();

		monitoringTask.run(); // Discover + Collect
		monitoringTask.run(); // Collect
		monitoringTask.run(); // Collect
		monitoringTask.run(); // Collect

		verify(telemetryManagerMock, times(1)).run(
			any(DetectionStrategy.class),
			any(DiscoveryStrategy.class),
			any(SimpleStrategy.class),
			any(HardwarePostDiscoveryStrategy.class),
			any(HardwareMonitorNameGenerationStrategy.class)
		);
		verify(telemetryManagerMock, times(4)).run(
			any(PrepareCollectStrategy.class),
			any(ProtocolHealthCheckStrategy.class),
			any(CollectStrategy.class),
			any(SimpleStrategy.class),
			any(HardwarePostCollectStrategy.class),
			any(HardwareMonitorNameGenerationStrategy.class)
		);
	}

	@Test
	void testRegisterTelemetryManagerRecorders() {
		final Map<String, String> hostAttributes = Map.of(
			HOST_NAME,
			HOSTNAME,
			HOST_TYPE_ATTRIBUTE_KEY,
			COMPUTE_HOST_TYPE,
			OS_TYPE_ATTRIBUTE_KEY,
			OS_LINUX
		);

		final TelemetryManager telemetryManager = new TelemetryManager();
		final ConnectorStore connectorStore = new ConnectorStore();
		connectorStore.setStore(new HashMap<>());
		connectorStore.addOne(
			"connector",
			Connector.builder()
				.metrics(
					Map.of(
						"hw.power",
						MetricDefinition.builder().description("Device power in watts").type(MetricType.GAUGE).unit("W").build()
					)
				)
				.build()
		);
		telemetryManager.setConnectorStore(connectorStore);
		telemetryManager.setHostConfiguration(hostConfiguration);

		final MonitorFactory hostFactory = MonitorFactory.builder()
			.attributes(hostAttributes)
			.discoveryTime(System.currentTimeMillis())
			.monitorType(KnownMonitorType.HOST.getKey())
			.telemetryManager(telemetryManager)
			.build();
		final Monitor host = hostFactory.createOrUpdateMonitor(HOSTNAME);
		host.setIsEndpoint(true);

		final MetricFactory metricFactory = new MetricFactory(
			telemetryManager.getHostname(),
			telemetryManager.getConnectorStore()
		);
		metricFactory.collectNumberMetric(
			host,
			AbstractStrategy.HOST_CONFIGURED_METRIC_NAME,
			1.0,
			System.currentTimeMillis()
		);

		final Map<String, String> enclosureAttributes = new HashMap<>(
			Map.of("name", "enclosure", "id", "enclosure", "serial_number", "SN12345")
		);
		final MonitorFactory enclosureFactory = MonitorFactory.builder()
			.attributes(enclosureAttributes)
			.connectorId("connector")
			.discoveryTime(System.currentTimeMillis())
			.monitorType(KnownMonitorType.ENCLOSURE.getKey())
			.telemetryManager(telemetryManager)
			.build();
		final Monitor enclosure = enclosureFactory.createOrUpdateMonitor("enclosure");
		metricFactory.collectNumberMetric(enclosure, "hw.power{hw.type=\"enclosure\"}", 30.0, System.currentTimeMillis());

		final TestHelper.TestOtelClient otelClient = new TestHelper.TestOtelClient();

		final MetricsExporter metricsExporter = MetricsExporter.builder().withClient(otelClient).build();

		doReturn(metricsExporter).when(monitoringTaskInfoMock).getMetricsExporter();
		doReturn(
			new MetricDefinitions(
				Map.of(
					AbstractStrategy.HOST_CONFIGURED_METRIC_NAME,
					MetricDefinition.builder().description("Whether the host is configured or not").type(MetricType.GAUGE).build()
				)
			)
		).when(monitoringTaskInfoMock).getHostMetricDefinitions();

		final ResourceConfig resourceConfig = ResourceConfig.builder().attributes(hostAttributes).build();
		monitoringTask.initHostAttributes(telemetryManager, resourceConfig);

		monitoringTask.registerTelemetryManagerRecorders(telemetryManager, List.of()).exportMetrics(() -> {});

		final ExportMetricsServiceRequest request = otelClient.getRequest();
		assertNotNull(request);

		// Verify the host metrics
		final ResourceMetrics hostResourceMetrics = request.getResourceMetricsList()
			.stream()
			.filter(r -> r.getResource().getAttributesList().size() == hostAttributes.size())
			.findFirst()
			.orElseThrow();
		assertNotNull(hostResourceMetrics);
		final Resource hostResource = hostResourceMetrics.getResource();
		assertNotNull(hostResource);
		final List<KeyValue> hostKeyValueAttributes = hostResource.getAttributesList();
		final Map<String, String> hostAttributesResult = hostKeyValueAttributes.stream()
			.collect(Collectors.toMap(KeyValue::getKey, keyValue -> keyValue.getValue().getStringValue()));

		assertTrue(MapHelper.areEqual(hostAttributes, hostAttributesResult), "Host attributes are not equal");

		final List<ScopeMetrics> hostScopeMetricsList = hostResourceMetrics.getScopeMetricsList();
		assertEquals(1, hostScopeMetricsList.size());
		final ScopeMetrics scopeMetrics = hostScopeMetricsList.get(0);
		assertEquals(1, scopeMetrics.getMetricsCount());
		final List<Metric> hostMetrics = scopeMetrics.getMetricsList();
		assertEquals(1, hostMetrics.size());
		final Metric hostMetric = hostMetrics.get(0);
		assertEquals(AbstractStrategy.HOST_CONFIGURED_METRIC_NAME, hostMetric.getName());
		assertEquals("Whether the host is configured or not", hostMetric.getDescription());
		assertEquals("", hostMetric.getUnit());
		final Gauge gauge = hostMetric.getGauge();
		final List<NumberDataPoint> dataPointsList = gauge.getDataPointsList();
		assertEquals(1, dataPointsList.size());
		final NumberDataPoint dataPoint = dataPointsList.get(0);
		assertEquals(1.0, dataPoint.getAsDouble());

		// Verify the enclosure metrics
		final ResourceMetrics enclosureResourceMetrics = request.getResourceMetricsList()
			.stream()
			.filter(r -> r.getResource().getAttributesList().size() == enclosureAttributes.size() + hostAttributes.size())
			.findFirst()
			.orElseThrow();
		assertNotNull(enclosureResourceMetrics);
		final Resource enclosureResource = enclosureResourceMetrics.getResource();
		assertNotNull(enclosureResource);
		final List<KeyValue> enclosureKeyValueAttributes = enclosureResource.getAttributesList();
		final Map<String, String> expectedEnclosureAttributes = new HashMap<>(hostAttributes);
		expectedEnclosureAttributes.putAll(enclosureAttributes);

		final Map<String, String> enclosureAttributesResult = enclosureKeyValueAttributes.stream()
			.collect(Collectors.toMap(KeyValue::getKey, keyValue -> keyValue.getValue().getStringValue()));
		assertTrue(
			MapHelper.areEqual(expectedEnclosureAttributes, enclosureAttributesResult),
			"Enclosure attributes are not equal"
		);

		final List<ScopeMetrics> enclosureScopeMetricsList = enclosureResourceMetrics.getScopeMetricsList();
		assertEquals(1, enclosureScopeMetricsList.size());
		final ScopeMetrics enclosureScopeMetrics = enclosureScopeMetricsList.get(0);
		assertEquals(1, enclosureScopeMetrics.getMetricsCount());
		final List<Metric> enclosureMetrics = enclosureScopeMetrics.getMetricsList();
		assertEquals(1, enclosureMetrics.size());
		final Metric enclosureMetric = enclosureMetrics.get(0);
		assertEquals("hw.power", enclosureMetric.getName());
		assertEquals("Device power in watts", enclosureMetric.getDescription());
		assertEquals("W", enclosureMetric.getUnit());
		final Gauge enclosureGauge = enclosureMetric.getGauge();
		final List<NumberDataPoint> enclosureDataPointsList = enclosureGauge.getDataPointsList();
		assertEquals(1, enclosureDataPointsList.size());
		final NumberDataPoint enclosureDataPoint = enclosureDataPointsList.get(0);
		assertEquals(30.0, enclosureDataPoint.getAsDouble());
		final Map<String, Object> enclosureDataPointAttributes = enclosureDataPoint.getAttributesList()
			.stream()
			.collect(Collectors.toMap(KeyValue::getKey, keyValue -> keyValue.getValue().getStringValue()));
		assertEquals(Map.of("hw.type", "enclosure"), enclosureDataPointAttributes);
	}

	/**
	 * Builds a resource with an endpoint host monitor, as the agent does.
	 *
	 * @param hostAttributes The attributes of the host monitor.
	 * @return A new {@link TelemetryManager}.
	 */
	private static TelemetryManager newResourceTelemetryManager(final Map<String, String> hostAttributes) {
		final TelemetryManager telemetryManager = new TelemetryManager();
		final ConnectorStore connectorStore = new ConnectorStore();
		connectorStore.setStore(new HashMap<>());
		telemetryManager.setConnectorStore(connectorStore);
		telemetryManager.setHostConfiguration(hostConfiguration);
		MonitorFactory.builder()
			.attributes(hostAttributes)
			.discoveryTime(System.currentTimeMillis())
			.monitorType(KnownMonitorType.HOST.getKey())
			.telemetryManager(telemetryManager)
			.build()
			.createOrUpdateMonitor(HOSTNAME)
			.setIsEndpoint(true);
		return telemetryManager;
	}

	/**
	 * Exports the metrics of the resource and returns the data points of metricshub.host.requests by "state/operation_type".
	 *
	 * @param telemetryManager The resource.
	 * @param otelClient       The client that receives the export request.
	 * @return The metric and its data point values.
	 */
	private Map<String, Double> exportRequestDataPoints(
		final TelemetryManager telemetryManager,
		final TestHelper.TestOtelClient otelClient
	) {
		monitoringTask.registerTelemetryManagerRecorders(telemetryManager, List.of()).exportMetrics(() -> {});
		final Metric requests = otelClient.getRequest()
			.getResourceMetricsList()
			.stream()
			.flatMap(resourceMetrics -> resourceMetrics.getScopeMetricsList().stream())
			.flatMap(scopeMetrics -> scopeMetrics.getMetricsList().stream())
			.filter(metric -> "metricshub.host.requests".equals(metric.getName()))
			.findFirst()
			.orElseThrow();

		// A cumulative, monotonic sum described by the agent's metricshub-host-metrics.yaml
		assertTrue(requests.hasSum());
		assertTrue(requests.getSum().getIsMonotonic());
		assertEquals(
			AggregationTemporality.AGGREGATION_TEMPORALITY_CUMULATIVE,
			requests.getSum().getAggregationTemporality()
		);
		assertTrue(requests.getDescription().contains("completed, timeout or failed"), requests.getDescription());

		final Map<String, Double> values = new HashMap<>();
		for (final NumberDataPoint dataPoint : requests.getSum().getDataPointsList()) {
			final Map<String, String> attributes = dataPoint.getAttributesList()
				.stream()
				.collect(Collectors.toMap(KeyValue::getKey, keyValue -> keyValue.getValue().getStringValue()));
			assertEquals(Set.of("state", "operation_type"), attributes.keySet());
			values.put(attributes.get("state") + "/" + attributes.get("operation_type"), dataPoint.getAsDouble());
		}
		return values;
	}

	@Test
	void testExportRequestCountersThroughTheNormalCollectPath() throws Exception {
		final Map<String, String> hostAttributes = Map.of(
			HOST_NAME,
			HOSTNAME,
			HOST_TYPE_ATTRIBUTE_KEY,
			COMPUTE_HOST_TYPE,
			OS_TYPE_ATTRIBUTE_KEY,
			OS_LINUX
		);
		final TelemetryManager telemetryManager = newResourceTelemetryManager(hostAttributes);
		final TestHelper.TestOtelClient otelClient = new TestHelper.TestOtelClient();
		doReturn(MetricsExporter.builder().withClient(otelClient).build()).when(monitoringTaskInfoMock)
			.getMetricsExporter();
		doReturn(ConfigHelper.readHostMetricDefinitions()).when(monitoringTaskInfoMock).getHostMetricDefinitions();
		monitoringTask.initHostAttributes(telemetryManager, ResourceConfig.builder().attributes(hostAttributes).build());

		final ClientsExecutor clientsExecutor = new ClientsExecutor(telemetryManager);
		final ExtensionManager extensionManager = ExtensionManager.empty();

		// First cycle: the protocols recorded 2 completed SNMP requests and 1 failed HTTP request
		telemetryManager.getRequestStats().record("snmp", RequestOutcome.COMPLETED);
		telemetryManager.getRequestStats().record("snmp", RequestOutcome.COMPLETED);
		telemetryManager.getRequestStats().record("http", RequestOutcome.FAILED);
		final long firstCycle = System.currentTimeMillis();
		telemetryManager.run(
			new PrepareCollectStrategy(telemetryManager, firstCycle, clientsExecutor, extensionManager),
			new ProtocolHealthCheckStrategy(telemetryManager, firstCycle, clientsExecutor, extensionManager)
		);
		assertEquals(
			Map.of(
				"completed/snmp",
				2.0,
				"timeout/snmp",
				0.0,
				"failed/snmp",
				0.0,
				"completed/http",
				0.0,
				"timeout/http",
				0.0,
				"failed/http",
				1.0
			),
			exportRequestDataPoints(telemetryManager, otelClient)
		);

		// Second cycle: the values are cumulative
		telemetryManager.getRequestStats().record("snmp", RequestOutcome.COMPLETED);
		telemetryManager.getRequestStats().record("http", RequestOutcome.TIMEOUT);
		final long secondCycle = firstCycle + 120_000;
		telemetryManager.run(
			new PrepareCollectStrategy(telemetryManager, secondCycle, clientsExecutor, extensionManager),
			new ProtocolHealthCheckStrategy(telemetryManager, secondCycle, clientsExecutor, extensionManager)
		);
		assertEquals(
			Map.of(
				"completed/snmp",
				3.0,
				"timeout/snmp",
				0.0,
				"failed/snmp",
				0.0,
				"completed/http",
				0.0,
				"timeout/http",
				1.0,
				"failed/http",
				1.0
			),
			exportRequestDataPoints(telemetryManager, otelClient)
		);
	}
}
