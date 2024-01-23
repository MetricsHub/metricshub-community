package org.sentrysoftware.metricshub.engine.telemetry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doReturn;
import static org.sentrysoftware.metricshub.engine.common.helpers.MetricsHubConstants.CONNECTOR_STATUS_METRIC_KEY;
import static org.sentrysoftware.metricshub.engine.common.helpers.MetricsHubConstants.DEFAULT_JOB_TIMEOUT;
import static org.sentrysoftware.metricshub.engine.common.helpers.MetricsHubConstants.HOST_NAME;
import static org.sentrysoftware.metricshub.engine.common.helpers.MetricsHubConstants.MONITOR_ATTRIBUTE_ID;
import static org.sentrysoftware.metricshub.engine.common.helpers.MetricsHubConstants.MONITOR_ATTRIBUTE_NAME;
import static org.sentrysoftware.metricshub.engine.common.helpers.MetricsHubConstants.STATE_SET_METRIC_OK;
import static org.sentrysoftware.metricshub.engine.constants.Constants.AGENT_HOSTNAME_VALUE;
import static org.sentrysoftware.metricshub.engine.constants.Constants.COMPUTE;
import static org.sentrysoftware.metricshub.engine.constants.Constants.HOST;
import static org.sentrysoftware.metricshub.engine.constants.Constants.HOST_ID;
import static org.sentrysoftware.metricshub.engine.constants.Constants.HOST_ID_ATTRIBUTE;
import static org.sentrysoftware.metricshub.engine.constants.Constants.HOST_TYPE;
import static org.sentrysoftware.metricshub.engine.constants.Constants.ID;
import static org.sentrysoftware.metricshub.engine.constants.Constants.LINUX;
import static org.sentrysoftware.metricshub.engine.constants.Constants.LOCATION;
import static org.sentrysoftware.metricshub.engine.constants.Constants.MONITOR_ID_ATTRIBUTE_VALUE;
import static org.sentrysoftware.metricshub.engine.constants.Constants.OS_TYPE;
import static org.sentrysoftware.metricshub.engine.constants.Constants.STATE_SET;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.sentrysoftware.metricshub.engine.common.HostLocation;
import org.sentrysoftware.metricshub.engine.common.helpers.KnownMonitorType;
import org.sentrysoftware.metricshub.engine.configuration.HostConfiguration;
import org.sentrysoftware.metricshub.engine.connector.model.common.DeviceKind;
import org.sentrysoftware.metricshub.engine.telemetry.metric.AbstractMetric;
import org.sentrysoftware.metricshub.engine.telemetry.metric.NumberMetric;
import org.sentrysoftware.metricshub.engine.telemetry.metric.StateSetMetric;

@ExtendWith(MockitoExtension.class)
class MonitorFactoryTest {

	@Mock
	private TelemetryManager telemetryManagerMock;

	@InjectMocks
	private MonitorFactory monitorFactory = MonitorFactory.builder().discoveryTime(System.currentTimeMillis()).build();

	@InjectMocks
	private MetricFactory metricFactoryMock;

	@Test
	void testCreateOrUpdateMonitorExists() {
		// Set monitor attributes
		final Map<String, String> monitorAttributes = new HashMap<>();
		monitorAttributes.put(MONITOR_ATTRIBUTE_ID, MONITOR_ID_ATTRIBUTE_VALUE);

		// Create a monitor with the previously created attributes
		final Monitor monitor = Monitor.builder().attributes(monitorAttributes).build();

		// Mock findMonitorByTypeAndId response
		doReturn(monitor)
			.when(telemetryManagerMock)
			.findMonitorByTypeAndId(KnownMonitorType.CONNECTOR.getKey(), MONITOR_ID_ATTRIBUTE_VALUE);

		// Call method createOrUpdateMonitor in MonitorFactory
		assertEquals(
			monitor,
			monitorFactory.createOrUpdateMonitor(
				monitorAttributes,
				null,
				KnownMonitorType.CONNECTOR.getKey(),
				MONITOR_ID_ATTRIBUTE_VALUE
			)
		);

		// Check the found monitor
		assertEquals(KnownMonitorType.CONNECTOR.getKey(), monitor.getType());
	}

	@Test
	void testCreateOrUpdateMonitorNotExists() {
		// Set monitor attributes
		final Map<String, String> monitorAttributes = new HashMap<>();
		monitorAttributes.put(MONITOR_ATTRIBUTE_ID, MONITOR_ID_ATTRIBUTE_VALUE);

		// Mock findMonitorByTypeAndId response
		doReturn(null)
			.when(telemetryManagerMock)
			.findMonitorByTypeAndId(KnownMonitorType.CONNECTOR.getKey(), MONITOR_ID_ATTRIBUTE_VALUE);

		// Call method createOrUpdateMonitor in MonitorFactory and retrieve the created monitor
		final Monitor createdMonitor = monitorFactory.createOrUpdateMonitor(
			monitorAttributes,
			null,
			KnownMonitorType.CONNECTOR.getKey(),
			MONITOR_ID_ATTRIBUTE_VALUE
		);

		// Check the created monitor
		assertEquals(monitorAttributes, createdMonitor.getAttributes());
		assertEquals(KnownMonitorType.CONNECTOR.getKey(), createdMonitor.getType());
	}

	@Test
	void testCollectNumberMetricNotExists() {
		// Set monitor attributes
		final Map<String, String> monitorAttributes = new HashMap<>();
		monitorAttributes.put(MONITOR_ATTRIBUTE_ID, MONITOR_ID_ATTRIBUTE_VALUE);

		// Mock findMonitorByTypeAndId response
		doReturn(null)
			.when(telemetryManagerMock)
			.findMonitorByTypeAndId(KnownMonitorType.CONNECTOR.getKey(), MONITOR_ID_ATTRIBUTE_VALUE);

		// Create the monitor and check its attributes
		final Monitor createdMonitor = monitorFactory.createOrUpdateMonitor(
			monitorAttributes,
			null,
			KnownMonitorType.CONNECTOR.getKey(),
			MONITOR_ID_ATTRIBUTE_VALUE
		);
		assertEquals(monitorAttributes, createdMonitor.getAttributes());

		// Call method collectNumberMetric in MonitorFactory
		metricFactoryMock.collectNumberMetric(createdMonitor, CONNECTOR_STATUS_METRIC_KEY, 1.0, DEFAULT_JOB_TIMEOUT);

		// Retrieve the resulting number metric
		final NumberMetric numberMetric = createdMonitor.getMetric(CONNECTOR_STATUS_METRIC_KEY, NumberMetric.class);

		// Check the resulting number metric
		assertNotNull(numberMetric);
		assertEquals(1.0, numberMetric.getValue());
	}

	@Test
	void testCollectNumberMetricExists() {
		// Set monitor attributes
		final Map<String, String> monitorAttributes = new HashMap<>();
		monitorAttributes.put(MONITOR_ATTRIBUTE_ID, MONITOR_ID_ATTRIBUTE_VALUE);

		// Set monitor metrics
		final Map<String, AbstractMetric> monitorMetrics = new HashMap<>();
		monitorMetrics.put(CONNECTOR_STATUS_METRIC_KEY, new NumberMetric());

		// Create the monitor
		final Monitor monitor = Monitor.builder().attributes(monitorAttributes).metrics(monitorMetrics).build();
		doReturn(monitor)
			.when(telemetryManagerMock)
			.findMonitorByTypeAndId(KnownMonitorType.CONNECTOR.getKey(), MONITOR_ID_ATTRIBUTE_VALUE);

		// Call method createOrUpdateMonitor in MonitorFactory, retrieve the created monitor then check its attributes
		final Monitor createdMonitor = monitorFactory.createOrUpdateMonitor(
			monitorAttributes,
			null,
			KnownMonitorType.CONNECTOR.getKey(),
			MONITOR_ID_ATTRIBUTE_VALUE
		);
		assertEquals(monitorAttributes, createdMonitor.getAttributes());

		// Call method collectNumberMetric in MonitorFactory
		metricFactoryMock.collectNumberMetric(createdMonitor, CONNECTOR_STATUS_METRIC_KEY, 1.0, DEFAULT_JOB_TIMEOUT);

		// Retrieve the resulting number metric
		final NumberMetric numberMetric = createdMonitor.getMetric(CONNECTOR_STATUS_METRIC_KEY, NumberMetric.class);

		// Check the resulting number metric
		assertNotNull(numberMetric);
		assertEquals(1.0, numberMetric.getValue());
	}

	@Test
	void testCollectStateSetMetricNotExists() {
		// Set monitor attributes
		final Map<String, String> monitorAttributes = new HashMap<>();
		monitorAttributes.put(MONITOR_ATTRIBUTE_ID, MONITOR_ID_ATTRIBUTE_VALUE);

		// Mock findMonitorByTypeAndId response
		doReturn(null)
			.when(telemetryManagerMock)
			.findMonitorByTypeAndId(KnownMonitorType.CONNECTOR.getKey(), MONITOR_ID_ATTRIBUTE_VALUE);

		// Create the monitor and check its attributes
		final Monitor createdMonitor = monitorFactory.createOrUpdateMonitor(
			monitorAttributes,
			null,
			KnownMonitorType.CONNECTOR.getKey(),
			MONITOR_ID_ATTRIBUTE_VALUE
		);
		assertEquals(monitorAttributes, createdMonitor.getAttributes());

		// Call collectStateSetMetric in MonitorFactory
		metricFactoryMock.collectStateSetMetric(
			createdMonitor,
			CONNECTOR_STATUS_METRIC_KEY,
			STATE_SET_METRIC_OK,
			STATE_SET,
			DEFAULT_JOB_TIMEOUT
		);

		// Retrieve the resulting stateSet metric
		final StateSetMetric stateSetMetric = createdMonitor.getMetric(CONNECTOR_STATUS_METRIC_KEY, StateSetMetric.class);

		// Check the resulting stateSet metric
		assertNotNull(stateSetMetric);
		assertEquals(STATE_SET_METRIC_OK, stateSetMetric.getValue());
	}

	@Test
	void testCollectStateSetMetricExists() {
		// Set monitor attributes
		final Map<String, String> monitorAttributes = new HashMap<>();
		monitorAttributes.put(MONITOR_ATTRIBUTE_ID, MONITOR_ID_ATTRIBUTE_VALUE);

		// Set monitor metrics
		final Map<String, AbstractMetric> monitorMetrics = new HashMap<>();
		monitorMetrics.put(CONNECTOR_STATUS_METRIC_KEY, new StateSetMetric());

		// Mock findMonitorByTypeAndId response
		final Monitor monitor = Monitor.builder().attributes(monitorAttributes).metrics(monitorMetrics).build();
		doReturn(monitor)
			.when(telemetryManagerMock)
			.findMonitorByTypeAndId(KnownMonitorType.CONNECTOR.getKey(), MONITOR_ID_ATTRIBUTE_VALUE);

		// Create the monitor and check its attributes
		final Monitor createdMonitor = monitorFactory.createOrUpdateMonitor(
			monitorAttributes,
			null,
			KnownMonitorType.CONNECTOR.getKey(),
			MONITOR_ID_ATTRIBUTE_VALUE
		);
		assertEquals(monitorAttributes, createdMonitor.getAttributes());

		// Call collectStateSetMetric in MonitorFactory
		metricFactoryMock.collectStateSetMetric(
			createdMonitor,
			CONNECTOR_STATUS_METRIC_KEY,
			STATE_SET_METRIC_OK,
			STATE_SET,
			DEFAULT_JOB_TIMEOUT
		);

		// Retrieve the resulting stateSet metric
		final StateSetMetric stateSetMetric = createdMonitor.getMetric(CONNECTOR_STATUS_METRIC_KEY, StateSetMetric.class);

		// Check the resulting stateSet metric
		assertNotNull(stateSetMetric);
		assertEquals(STATE_SET_METRIC_OK, stateSetMetric.getValue());
	}

	@Test
	void testCreateEndpointHostMonitor() {
		// Create a telemetry manager instance with necessary information in host configuration and host properties
		final TelemetryManager telemetryManager = TelemetryManager
			.builder()
			.hostConfiguration(
				HostConfiguration.builder().hostId(HOST_ID).hostname(HOST_NAME).hostType(DeviceKind.LINUX).build()
			)
			.hostProperties(HostProperties.builder().isLocalhost(Boolean.TRUE).build())
			.build();

		// Mock host configuration, hostname and host properties
		doReturn(telemetryManager.getHostConfiguration()).when(telemetryManagerMock).getHostConfiguration();
		doReturn(HOST_NAME).when(telemetryManagerMock).getHostname();

		// Call create host monitor
		final Monitor hostMonitor = monitorFactory.createEndpointHostMonitor(Boolean.TRUE);

		// Check that the created monitor is not null
		assertNotNull(hostMonitor);

		// Check host monitor attributes
		assertEquals(HOST_ID, hostMonitor.getAttributes().get(ID));
		assertEquals(HostLocation.LOCAL.getKey(), hostMonitor.getAttributes().get(LOCATION));
		assertEquals(HOST_NAME, hostMonitor.getAttributes().get(MONITOR_ATTRIBUTE_NAME));

		// Retrieve host monitor resource
		final Resource hostMonitorResource = hostMonitor.getResource();

		// Check host monitor resource type
		assertEquals(HOST, hostMonitorResource.getType());

		// Check host monitor resource attributes
		assertEquals(COMPUTE, hostMonitorResource.getAttributes().get(HOST_TYPE));
		assertEquals(HOST_ID, hostMonitorResource.getAttributes().get(HOST_ID_ATTRIBUTE));
		assertEquals(LINUX.toLowerCase(), hostMonitorResource.getAttributes().get(OS_TYPE));
		assertEquals(AGENT_HOSTNAME_VALUE, hostMonitorResource.getAttributes().get("agent.host.name"));
		assertEquals(HOST_NAME, hostMonitorResource.getAttributes().get(HOST_NAME));

		// Check that the monitor is an endpoint host monitor
		assertTrue(hostMonitor.isEndpointHost());
	}

	@Test
	void testExtractAttributesFromMetricName() {
		assertEquals(Map.of("hw.type", "cpu"), MetricFactory.extractAttributesFromMetricName("hw.metric{hw.type=\"cpu\"}"));

		assertEquals(
			Map.of("hw.type", "cpu", "host.id", "host"),
			MetricFactory.extractAttributesFromMetricName("hw.metric{hw.type=\"cpu\", host.id=\"host\"}")
		);

		assertEquals(
			Map.of("hw.type", "cpu", "host.id", "host"),
			MetricFactory.extractAttributesFromMetricName("hw.metric{hw.type=\"cpu\",host.id=\"host\"}")
		);
	}
}
