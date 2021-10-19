package com.sentrysoftware.hardware.prometheus.service;

import static com.sentrysoftware.hardware.prometheus.service.HostMonitoringCollectorService.LABELS;
import static com.sentrysoftware.matrix.common.helpers.HardwareConstants.DEVICE_ID;
import static com.sentrysoftware.matrix.common.helpers.HardwareConstants.ENERGY_PARAMETER;
import static com.sentrysoftware.matrix.common.helpers.HardwareConstants.ENERGY_USAGE_PARAMETER;
import static com.sentrysoftware.matrix.common.helpers.HardwareConstants.FAN_TYPE;
import static com.sentrysoftware.matrix.common.helpers.HardwareConstants.IDENTIFYING_INFORMATION;
import static com.sentrysoftware.matrix.common.helpers.HardwareConstants.MODEL;
import static com.sentrysoftware.matrix.common.helpers.HardwareConstants.PRESENT_PARAMETER;
import static com.sentrysoftware.matrix.common.helpers.HardwareConstants.SERIAL_NUMBER;
import static com.sentrysoftware.matrix.common.helpers.HardwareConstants.STATUS_PARAMETER;
import static com.sentrysoftware.matrix.common.helpers.HardwareConstants.TARGET_FQDN;
import static com.sentrysoftware.matrix.common.helpers.HardwareConstants.TEST_REPORT_PARAMETER;
import static com.sentrysoftware.matrix.common.helpers.HardwareConstants.TYPE;
import static com.sentrysoftware.matrix.common.helpers.HardwareConstants.UNALLOCATED_SPACE_PARAMETER;
import static com.sentrysoftware.matrix.common.helpers.HardwareConstants.VENDOR;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mockStatic;

import java.io.IOException;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.sentrysoftware.hardware.prometheus.dto.MultiHostsConfigurationDTO;
import com.sentrysoftware.hardware.prometheus.dto.PrometheusParameter;
import com.sentrysoftware.hardware.prometheus.dto.PrometheusParameter.PrometheusMetricType;
import com.sentrysoftware.hardware.prometheus.service.metrics.HardwareCounterMetric;
import com.sentrysoftware.hardware.prometheus.service.metrics.HardwareGaugeMetric;
import com.sentrysoftware.matrix.common.helpers.ResourceHelper;
import com.sentrysoftware.matrix.common.meta.monitor.Enclosure;
import com.sentrysoftware.matrix.common.meta.monitor.MetaConnector;
import com.sentrysoftware.matrix.common.meta.monitor.Voltage;
import com.sentrysoftware.matrix.common.meta.parameter.state.Status;
import com.sentrysoftware.matrix.connector.model.monitor.MonitorType;
import com.sentrysoftware.matrix.model.monitor.Monitor;
import com.sentrysoftware.matrix.model.monitoring.HostMonitoring;
import com.sentrysoftware.matrix.model.monitoring.IHostMonitoring;
import com.sentrysoftware.matrix.model.parameter.DiscreteParam;
import com.sentrysoftware.matrix.model.parameter.IParameter;
import com.sentrysoftware.matrix.model.parameter.NumberParam;
import com.sentrysoftware.matrix.model.parameter.TextParam;

import io.prometheus.client.Collector.MetricFamilySamples;
import io.prometheus.client.Collector.MetricFamilySamples.Sample;
import io.prometheus.client.CollectorRegistry;
import io.prometheus.client.exporter.common.TextFormat;

@SpringBootTest
@ExtendWith(MockitoExtension.class)
class HostMonitoringCollectorServiceTest {

	private static final String FQDN_VALUE = "server1.domain.net";
	private static final String MAXIMUM_SPEED = "maximumSpeed";
	private static final String ENCLOSURE_NAME = "Enclosure ECS";
	private static final String ECS = "ecs";
	private static final String ENCLOSURE_ID = "connector1.connector_enclosure_ecs_1.1";
	private static final String HELP_DEFAULT = "help";
	private static final String MONITOR_STATUS_METRIC = "monitor_status";
	private static final String MONITOR_ENERGY_METRIC = "monitor_energy_total";
	private static final String LABEL_VALUE = "monitor";
	private static final String PARENT_ID_VALUE = "parent_id";
	private static final String ID_VALUE = "id";
	private static final String FAN_ID = "connector1.connector_fan_ecs_1.1";
	private static final String FAN_NAME = "Fan 1.1";

	@Mock
	private Map<String, IHostMonitoring> hostMonitoringMap;

	@Mock
	private MultiHostsConfigurationDTO multiHostsConfigurationDTO;

	@InjectMocks
	@Autowired
	private HostMonitoringCollectorService hostMonitoringCollectorService;

	@Test
	void testCollect() throws IOException {

		final DiscreteParam statusParam = DiscreteParam.builder().name(STATUS_PARAMETER).state(Status.OK).build();
		final NumberParam numberParam = NumberParam.builder().name(ENERGY_PARAMETER).value(3000D).build();

		Monitor enclosureMonitor = Monitor.builder()
			.id(ENCLOSURE_ID)
			.parentId(ECS)
			.name(ENCLOSURE_NAME)
			.parameters(Map.of(
				STATUS_PARAMETER, statusParam,
				ENERGY_PARAMETER, numberParam))
			.monitorType(MonitorType.ENCLOSURE)
			.build();
		enclosureMonitor.addMetadata(TARGET_FQDN, TARGET_FQDN);
		enclosureMonitor.addMetadata(DEVICE_ID, "1.1");
		enclosureMonitor.addMetadata(SERIAL_NUMBER, "XXX888");
		enclosureMonitor.addMetadata(VENDOR, "Dell");
		enclosureMonitor.addMetadata(MODEL, "PowerEdge R630");
		enclosureMonitor.addMetadata(TYPE, "Computer");
		enclosureMonitor.addMetadata(IDENTIFYING_INFORMATION, "Identifying info test");
		Map<String, Monitor> enclosures = Map.of(ENCLOSURE_ID, enclosureMonitor);

		Monitor fan1Monitor = Monitor.builder()
			.id(FAN_ID + 1)
			.parentId(ENCLOSURE_ID)
			.name(FAN_NAME + 1)
			.parameters(Map.of(STATUS_PARAMETER, statusParam))
			.monitorType(MonitorType.FAN)
			.build();
		fan1Monitor.addMetadata(TARGET_FQDN, TARGET_FQDN);
		fan1Monitor.addMetadata(DEVICE_ID, "1.11");
		fan1Monitor.addMetadata(FAN_TYPE, "default cooling");

		Monitor fan2Monitor = Monitor.builder()
			.id(FAN_ID + 2)
			.parentId(ENCLOSURE_ID)
			.name(FAN_NAME + 2)
			.parameters(Map.of(STATUS_PARAMETER, statusParam))
			.monitorType(MonitorType.FAN)
			.build();
		fan2Monitor.addMetadata(TARGET_FQDN, TARGET_FQDN);
		fan2Monitor.addMetadata(DEVICE_ID, "1.12");
		fan2Monitor.addMetadata(FAN_TYPE, "default cooling");

		Map<String, Monitor> fans = new LinkedHashMap<>();
		fans.put(FAN_ID + 1, fan1Monitor);
		fans.put(FAN_ID + 2, fan2Monitor);

		Map<MonitorType, Map<String, Monitor>> monitors = new LinkedHashMap<>();
		monitors.put(MonitorType.ENCLOSURE, enclosures);
		monitors.put(MonitorType.FAN, fans);

		IHostMonitoring hostMonitoring = new HostMonitoring();
		hostMonitoring.setMonitors(monitors);
		doReturn(Map.of(ECS, hostMonitoring).entrySet()).when(hostMonitoringMap).entrySet();

		CollectorRegistry.defaultRegistry.clear();

		hostMonitoringCollectorService.register();

		final StringWriter writer = new StringWriter();
		TextFormat.write004(writer, CollectorRegistry.defaultRegistry.metricFamilySamples());
		writer.flush();

		assertEquals(ResourceHelper.getResourceAsString("/data/metrics.txt", HostMonitoringCollectorService.class), writer.toString());
	}

	@Test
	void testCollectNoSpecificInfo() {

		final DiscreteParam statusParam = DiscreteParam.builder().name(STATUS_PARAMETER).state(Status.OK).build();
		final NumberParam numberParam = NumberParam.builder().name(ENERGY_USAGE_PARAMETER).value(3000D).build();

		Monitor enclosureMonitor = Monitor.builder()
			.id(ENCLOSURE_ID)
			.parentId(ECS)
			.name(ENCLOSURE_NAME)
			.parameters(Map.of(
				STATUS_PARAMETER, statusParam,
				ENERGY_USAGE_PARAMETER, numberParam))
			.monitorType(MonitorType.ENCLOSURE)
			.build();
		enclosureMonitor.addMetadata(TARGET_FQDN, TARGET_FQDN);
		Map<String, Monitor> enclosures = Map.of(ENCLOSURE_ID, enclosureMonitor);

		Map<MonitorType, Map<String, Monitor>> monitors = new LinkedHashMap<>();
		monitors.put(MonitorType.ENCLOSURE, enclosures);

		IHostMonitoring hostMonitoring = new HostMonitoring();
		hostMonitoring.setMonitors(monitors);
		doReturn(Map.of(ECS, hostMonitoring).entrySet()).when(hostMonitoringMap).entrySet();

		try(MockedStatic<PrometheusSpecificities> prometheusSpecificities = mockStatic(PrometheusSpecificities.class)){
			prometheusSpecificities.when(() -> PrometheusSpecificities.getInfoMetricName(MonitorType.ENCLOSURE)).thenReturn("enclosure_info");
			prometheusSpecificities.when(() -> PrometheusSpecificities.getLabels(MonitorType.ENCLOSURE)).thenReturn(null);
			CollectorRegistry.defaultRegistry.clear();

			assertThrows(IllegalStateException.class, () -> hostMonitoringCollectorService.register());

		}

		try (MockedStatic<PrometheusSpecificities> prometheusSpecificities = mockStatic(PrometheusSpecificities.class)) {
			prometheusSpecificities.when(() -> PrometheusSpecificities.getInfoMetricName(MonitorType.ENCLOSURE)).thenReturn("enclosure_info");
			prometheusSpecificities.when(() -> PrometheusSpecificities.getLabels(MonitorType.ENCLOSURE)).thenReturn(Collections.emptyList());
			CollectorRegistry.defaultRegistry.clear();

			assertThrows(IllegalStateException.class, () -> hostMonitoringCollectorService.register());

		}

	}

	@Test
	void testProcessSameTypeMonitors() {
		final DiscreteParam statusParam = DiscreteParam.builder().name(STATUS_PARAMETER).state(Status.OK).build();
		final Monitor monitor1 = Monitor.builder()
				.id(ID_VALUE + 1)
				.parentId(PARENT_ID_VALUE)
				.name(LABEL_VALUE + 1)
				.parameters(Map.of(STATUS_PARAMETER, statusParam))
				.monitorType(MonitorType.ENCLOSURE)
				.build();
		final Monitor monitor2 = Monitor.builder()
				.id(ID_VALUE  + 2)
				.parentId(PARENT_ID_VALUE)
				.name(LABEL_VALUE + 2)
				.parameters(Map.of(STATUS_PARAMETER, statusParam))
				.monitorType(MonitorType.ENCLOSURE)
				.build();
		final Map<String, Monitor> monitors = Map.of(
				monitor1.getId(), monitor1,
				monitor2.getId(), monitor2);

		final List<MetricFamilySamples> mfs = new ArrayList<>();

		hostMonitoringCollectorService.processSameTypeMonitors(MonitorType.ENCLOSURE, monitors, mfs);

		Set<Sample> actual = new HashSet<>(mfs.get(0).samples);
		final Sample sample1 = new Sample("hw_enclosure_status", LABELS,
				Arrays.asList("", monitor1.getId(), monitor1.getName(), monitor1.getParentId()), Status.OK.getNumericValue());
		final Sample sample2 = new Sample("hw_enclosure_status", LABELS,
				Arrays.asList("", monitor2.getId(), monitor2.getName(), monitor2.getParentId()), Status.OK.getNumericValue());

		final Set<Sample> expected = Stream.of(sample1, sample2).collect(Collectors.toSet());

		assertEquals(expected, actual);

		// test metadata to metric on CPU
		final List<MetricFamilySamples> mfsCpu = new ArrayList<>();

		Map<String, String> cpuMetadata = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
		cpuMetadata.put("deviceid", "1.1");
		cpuMetadata.put("maximumspeed", "4000");
		cpuMetadata.put("model", "Xeon CPU E5-2620 v4 @ 2.10GHz");
		cpuMetadata.put("vendor", "Intel");
		final Monitor monitor3 = Monitor.builder().id(ID_VALUE + 3).parentId(PARENT_ID_VALUE).name(LABEL_VALUE + 3)
				.parameters(Map.of(STATUS_PARAMETER, statusParam)).monitorType(MonitorType.CPU)
				.metadata(cpuMetadata).build();
		final Map<String, Monitor> monitorCpu = Map.of(monitor3.getId(), monitor3);

		hostMonitoringCollectorService.processSameTypeMonitors(MonitorType.CPU, monitorCpu, mfsCpu);

		final Sample sample4 = new Sample("hw_cpu_maximum_speed_hertz", LABELS,
				Arrays.asList("", monitor3.getId(), monitor3.getName(), monitor3.getParentId()), 4000 * 1000000.0);
		final MetricFamilySamples actualCpu = mfsCpu.stream().filter(p -> p.name.equals("hw_cpu_maximum_speed_hertz"))
				.findFirst().orElse(null);
		assertEquals(sample4, actualCpu.samples.get(0));

		// mock PrometheusSpecificities to return counter instead of gauge
		try (MockedStatic<PrometheusSpecificities> utilities = Mockito.mockStatic(PrometheusSpecificities.class)) {
			final Map<String, PrometheusParameter> map = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

			map.put("raidLevel", PrometheusParameter.builder().name("hw_logicalDisk_raidlevel_counter").unit("joules")
					.type(PrometheusMetricType.COUNTER).build());
			final Map<MonitorType, Map<String, PrometheusParameter>> prometheusMetadataParametersMap = new EnumMap<>(
					MonitorType.class);

			prometheusMetadataParametersMap.put(MonitorType.LOGICAL_DISK, map);
			Optional<PrometheusParameter> param = Optional
					.of(PrometheusParameter.builder().name("hw_logicalDisk_raidlevel_counter").unit("joules")
							.type(PrometheusMetricType.COUNTER).build());
			utilities.when(PrometheusSpecificities::getPrometheusMetadataToParameters)
					.thenReturn(prometheusMetadataParametersMap);
			utilities.when(() -> PrometheusSpecificities.getPrometheusMetadataToParameters(MonitorType.LOGICAL_DISK,
					"raidLevel")).thenReturn(param);

			final List<MetricFamilySamples> mfsDisk = new ArrayList<>();

			Map<String, String> lgMetadata = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
			lgMetadata.put("deviceid", "1.1");
			lgMetadata.put("raidLevel", "5");
			final Monitor monitor4 = Monitor.builder().id(ID_VALUE + 3).parentId(PARENT_ID_VALUE).name(LABEL_VALUE + 3)
					.monitorType(MonitorType.LOGICAL_DISK).metadata(lgMetadata).build();
			final Map<String, Monitor> monitorsLg = Map.of(monitor4.getId(), monitor4);

			hostMonitoringCollectorService.processSameTypeMonitors(MonitorType.LOGICAL_DISK, monitorsLg, mfsDisk);

			final Sample sample5 = new Sample("hw_logicalDisk_raidlevel_counter_total",
					LABELS,
					Arrays.asList("", monitor4.getId(), monitor4.getName(), monitor4.getParentId()), 5);
			final MetricFamilySamples actualLg = mfsDisk.stream()
					.filter(p -> p.name.equals("hw_logicalDisk_raidlevel_counter")).findFirst().orElse(null);
			assertEquals(sample5, actualLg.samples.get(0));

		}
	}

	@Test
	void testProcessSameTypeMonitorsNoMonitors() {
		final List<MetricFamilySamples> mfs = new ArrayList<>();
		hostMonitoringCollectorService.processSameTypeMonitors(MonitorType.ENCLOSURE, Collections.emptyMap(), mfs);
		assertTrue(mfs.isEmpty());

		hostMonitoringCollectorService.processSameTypeMonitors(MonitorType.ENCLOSURE, null, mfs);
		assertTrue(mfs.isEmpty());
	}

	@Test
	void testProcessMonitorsMetric() {
		final DiscreteParam statusParam = DiscreteParam.builder().name(STATUS_PARAMETER).state(Status.OK).build();
		final Monitor monitor1 = Monitor.builder()
					.id(ID_VALUE + 1)
					.parentId(PARENT_ID_VALUE)
					.name(LABEL_VALUE)
					.parameters(Map.of(STATUS_PARAMETER, statusParam))
					.monitorType(MonitorType.ENCLOSURE)
					.build();
		final Monitor monitor2 = Monitor.builder()
				.id(ID_VALUE  + 2)
				.parentId(PARENT_ID_VALUE)
				.name(LABEL_VALUE)
				.parameters(Collections.emptyMap())
				.monitorType(MonitorType.ENCLOSURE)
				.build();
		final Map<String, Monitor> monitors = Map.of(
				monitor1.getId(), monitor1,
				monitor2.getId(), monitor2);

		final List<MetricFamilySamples> mfs = new ArrayList<>();

		hostMonitoringCollectorService.processMonitorsMetric(Enclosure.STATUS, MonitorType.ENCLOSURE, monitors, mfs);

		final HardwareGaugeMetric expected = new HardwareGaugeMetric(
				"hw_enclosure_status",
				"Metric: hw_enclosure_status - Unit: { 0 = OK ; 1 = Degraded ; 2 = Failed }",
				LABELS);
		expected.addMetric(Arrays.asList(null, monitor1.getId(), LABEL_VALUE, PARENT_ID_VALUE), 0.0, null);

		assertEquals(expected, mfs.get(0));
	}

	@Test
	void testProcessMonitorsMetadataMetric() {
		Map<String, String> cpuMetadata = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
		cpuMetadata.put("deviceid", "1.1");
		cpuMetadata.put("maximumspeed", "4");
		cpuMetadata.put("model", "Xeon CPU E5-2620 v4 @ 2.10GHz");
		cpuMetadata.put("vendor", "Intel");
		final Monitor monitor1 = Monitor.builder().id(ID_VALUE + 1).parentId(PARENT_ID_VALUE).name(LABEL_VALUE)
				.metadata(cpuMetadata).monitorType(MonitorType.CPU).build();
		final Monitor monitor2 = Monitor.builder().id(ID_VALUE + 2).parentId(PARENT_ID_VALUE).name(LABEL_VALUE)
				.parameters(Collections.emptyMap()).monitorType(MonitorType.CPU).build();
		Map<String, Monitor> monitors = Map.of(monitor1.getId(), monitor1, monitor2.getId(), monitor2);

		List<MetricFamilySamples> mfs = new ArrayList<>();

		hostMonitoringCollectorService.processMonitorsMetadataMetrics(MonitorType.CPU, monitors, mfs);

		HardwareGaugeMetric expected = new HardwareGaugeMetric("hw_cpu_maximum_speed_hertz",
				"Metric: hw_cpu_maximum_speed_hertz - Unit: hertz", LABELS);
		expected.addMetric(Arrays.asList(null, monitor1.getId(), LABEL_VALUE, PARENT_ID_VALUE), 4000000.0, null);
		assertEquals(expected, mfs.get(0));

		// CPU without maxSpeed, check we do not create metric if no value for metadata
		Map<String, String> cpuMetadata2 = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
		cpuMetadata.put("deviceid", "1.2");
		final Monitor monitor3 = Monitor.builder().id(ID_VALUE + 3).parentId(PARENT_ID_VALUE).name(LABEL_VALUE)
				.metadata(cpuMetadata2).monitorType(MonitorType.CPU).build();
		monitors = Map.of(monitor2.getId(), monitor2, monitor3.getId(), monitor3);
		mfs = new ArrayList<>();

		hostMonitoringCollectorService.processMonitorsMetadataMetrics(MonitorType.CPU, monitors, mfs);
		assertTrue(mfs.isEmpty());

	}

	@Test
	void testProcessMonitorsMetricNotAvailable() {
		final Monitor monitor1 = Monitor.builder()
				.id(ID_VALUE + 1)
				.parentId(PARENT_ID_VALUE)
				.name(LABEL_VALUE)
				.parameters(Collections.emptyMap())
				.build();
		final Monitor monitor2 = Monitor.builder()
				.id(ID_VALUE  + 2)
				.parentId(PARENT_ID_VALUE)
				.name(LABEL_VALUE)
				.parameters(Collections.emptyMap())
				.build();
		final Map<String, Monitor> monitors = Map.of(
				monitor1.getId(), monitor1,
				monitor2.getId(), monitor2);
		final List<MetricFamilySamples> mfs = new ArrayList<>();

		hostMonitoringCollectorService.processMonitorsMetric(Enclosure.STATUS, MonitorType.ENCLOSURE, monitors, mfs);

		assertTrue(mfs.isEmpty());
	}

	@Test
	void testBuildHelp() {
		assertEquals("Metric: hw_enclosure_energy_joules - Unit: joules",
				HostMonitoringCollectorService.buildHelp(PrometheusSpecificities.getPrometheusParameter(MonitorType.ENCLOSURE, Enclosure.ENERGY.getName()).get()));
		assertEquals("Metric: hw_voltage_volts - Unit: volts",
				HostMonitoringCollectorService.buildHelp(PrometheusSpecificities.getPrometheusParameter(MonitorType.VOLTAGE, Voltage._VOLTAGE.getName()).get()));
		assertEquals("Metric: hw_metric_bytes", HostMonitoringCollectorService
				.buildHelp(PrometheusParameter.builder().name("hw_metric_bytes").build()));
	}

	@Test
	void testIsParameterFamilyAvailableOnMonitors() {
		{
			final DiscreteParam statusParam = DiscreteParam.builder().name(STATUS_PARAMETER).state(Status.OK).build();
			final Monitor monitor1 = Monitor.builder()
						.id(ID_VALUE + 1)
						.parentId(PARENT_ID_VALUE)
						.name(LABEL_VALUE)
						.parameters(Map.of(STATUS_PARAMETER, statusParam))
						.build();
			final Monitor monitor2 = Monitor.builder()
					.id(ID_VALUE  + 2)
					.parentId(PARENT_ID_VALUE)
					.name(LABEL_VALUE)
					.parameters(Collections.emptyMap())
					.build();
			final Map<String, Monitor> monitors = Map.of(
					monitor1.getId(), monitor1,
					monitor2.getId(), monitor2);
			assertTrue(HostMonitoringCollectorService.isParameterFamilyAvailableOnMonitors(Enclosure.STATUS, monitors));
		}

		{
			final Monitor monitor1 = Monitor.builder()
						.id(ID_VALUE + 1)
						.parentId(PARENT_ID_VALUE)
						.name(LABEL_VALUE)
						.parameters(Collections.emptyMap())
						.build();
			final Monitor monitor2 = Monitor.builder()
					.id(ID_VALUE  + 2)
					.parentId(PARENT_ID_VALUE)
					.name(LABEL_VALUE)
					.parameters(Collections.emptyMap())
					.build();
			final Map<String, Monitor> monitors = Map.of(
					monitor1.getId(), monitor1,
					monitor2.getId(), monitor2);
			assertFalse(HostMonitoringCollectorService.isParameterFamilyAvailableOnMonitors(Enclosure.STATUS, monitors));
		}
		assertFalse(HostMonitoringCollectorService.isParameterFamilyAvailableOnMonitors(Enclosure.STATUS, null));
		assertFalse(HostMonitoringCollectorService.isParameterFamilyAvailableOnMonitors(Enclosure.STATUS, Collections.emptyMap()));
	}

	@Test
	void testIsMetadataFamilyAvailableOnMonitors() {
		{
			final Map<String, String> cpuMetadata = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
			cpuMetadata.put("deviceid", "1.1");
			cpuMetadata.put("maximumspeed", "4");
			cpuMetadata.put("model", "Xeon CPU E5-2620 v4 @ 2.10GHz");
			cpuMetadata.put("vendor", "Intel");
			final Monitor monitor1 = Monitor.builder().id(ID_VALUE + 1).parentId(PARENT_ID_VALUE).name(LABEL_VALUE)
					.metadata(cpuMetadata).build();
			final Monitor monitor2 = Monitor.builder().id(ID_VALUE + 2).parentId(PARENT_ID_VALUE).name(LABEL_VALUE)
					.metadata(Collections.emptyMap()).build();
			final Map<String, Monitor> monitors = Map.of(monitor1.getId(), monitor1, monitor2.getId(), monitor2);
			assertTrue(HostMonitoringCollectorService.isMetadataFamilyAvailableOnMonitors(MAXIMUM_SPEED, monitors));
		}

		{
			final Monitor monitor1 = Monitor.builder().id(ID_VALUE + 1).parentId(PARENT_ID_VALUE).name(LABEL_VALUE)
					.metadata(Collections.emptyMap()).build();
			final Monitor monitor2 = Monitor.builder().id(ID_VALUE + 2).parentId(PARENT_ID_VALUE).name(LABEL_VALUE)
					.metadata(Collections.emptyMap()).build();
			final Map<String, Monitor> monitors = Map.of(monitor1.getId(), monitor1, monitor2.getId(), monitor2);
			assertFalse(HostMonitoringCollectorService.isMetadataFamilyAvailableOnMonitors(MAXIMUM_SPEED, monitors));
		}
		assertFalse(HostMonitoringCollectorService.isMetadataFamilyAvailableOnMonitors(MAXIMUM_SPEED, null));
		assertFalse(HostMonitoringCollectorService.isMetadataFamilyAvailableOnMonitors(MAXIMUM_SPEED,
				Collections.emptyMap()));
	}

	@Test
	void testIsParameterAvailable() {

		{
			final DiscreteParam statusParam = DiscreteParam.builder().name(STATUS_PARAMETER).state(Status.OK).build();
			final Monitor monitor = Monitor.builder()
						.id(ID_VALUE)
						.parentId(PARENT_ID_VALUE)
						.name(LABEL_VALUE)
						.parameters(Map.of(STATUS_PARAMETER, statusParam))
						.build();
			assertTrue(HostMonitoringCollectorService.isParameterAvailable(monitor, Enclosure.STATUS.getName()));
		}

		{

			final DiscreteParam statusParamNotAvailable = DiscreteParam
					.builder()
					.name(STATUS_PARAMETER)
					.state(Status.OK)
					.build();

			statusParamNotAvailable.setState(null);

			final Monitor monitor = Monitor.builder()
						.id(ID_VALUE)
						.parentId(PARENT_ID_VALUE)
						.name(LABEL_VALUE)
						.parameters(Map.of(STATUS_PARAMETER, statusParamNotAvailable))
						.build();
			assertFalse(HostMonitoringCollectorService.isParameterAvailable(monitor, Enclosure.STATUS.getName()));

		}

		{
			TextParam textParam = TextParam.builder().name(TEST_REPORT_PARAMETER).value("text").build();
			final Monitor monitor = Monitor.builder()
						.id(ID_VALUE)
						.parentId(PARENT_ID_VALUE)
						.name(LABEL_VALUE)
						.parameters(Map.of(TEST_REPORT_PARAMETER, textParam))
						.build();
			assertFalse(HostMonitoringCollectorService.isParameterAvailable(monitor, MetaConnector.TEST_REPORT.getName()));

		}

	}

	@Test
	void testGetParameterValueStatus() {
		final Monitor monitor = Monitor.builder()
				.id(ID_VALUE)
				.parentId(PARENT_ID_VALUE)
				.name(LABEL_VALUE)
				.parameters(Map.of(STATUS_PARAMETER,
						DiscreteParam
						.builder()
						.name(STATUS_PARAMETER)
						.state(Status.OK)
						.build()))
				.build();
		assertEquals(0, HostMonitoringCollectorService.getParameterValue(monitor, STATUS_PARAMETER));
	}

	@Test
	void testGetParameterValueNumber() {
		final Monitor monitor = Monitor.builder()
				.id(ID_VALUE)
				.parentId(PARENT_ID_VALUE)
				.name(LABEL_VALUE)
				.parameters(Map.of(ENERGY_USAGE_PARAMETER,
						NumberParam.builder().name(ENERGY_USAGE_PARAMETER)
						.value(3000D).build()))
				.build();
		assertEquals(3000D, HostMonitoringCollectorService.getParameterValue(monitor, ENERGY_USAGE_PARAMETER));
	}

	@Test
	void testConvertParameterValueNumber() {
		final String logicalDiskMonitor = "LogicalDisk";
		final Monitor monitor = Monitor.builder()
				.id(ID_VALUE)
				.parentId(PARENT_ID_VALUE)
				.name(logicalDiskMonitor)
				.parameters(Map.of(UNALLOCATED_SPACE_PARAMETER,
						NumberParam.builder().name(UNALLOCATED_SPACE_PARAMETER)
						.value(100D).build()))
				.monitorType(MonitorType.LOGICAL_DISK)
				.build();
		// make sure that the conversion is well done : factor 1073741824.0
		// Note that the monitor parameter value can never be null when the convertParameterValue is called
		assertEquals(107374182400.0, HostMonitoringCollectorService.convertParameterValue(monitor, UNALLOCATED_SPACE_PARAMETER, 1073741824.0));

		final Monitor monitor2 = new Monitor();
		assertThrows(NullPointerException.class, () -> HostMonitoringCollectorService.convertParameterValue(monitor2, UNALLOCATED_SPACE_PARAMETER, 1073741824.0));
	}

	@Test
	void testConvertMetadataValueNumber() {
		final String cpuMonitor = "CPU";
		final Map<String, String> cpuMetadata = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
		cpuMetadata.put("maximumspeed", "4");
		cpuMetadata.put("vendor", "Intel");
		final Monitor monitor = Monitor.builder().id(ID_VALUE).parentId(PARENT_ID_VALUE).name(cpuMonitor)
				.metadata(cpuMetadata).monitorType(MonitorType.CPU).build();
		// make sure that the conversion is well done : factor 1000.0
		// Note that the monitor metadata value can never be null when the
		// convertMetadataValue is called
		assertEquals(4000.0, HostMonitoringCollectorService.convertMetadataValue(monitor.getMetadata(MAXIMUM_SPEED), 1000.0));

		final Monitor monitor2 = new Monitor();
		String maximumSpeed = monitor2.getMetadata(MAXIMUM_SPEED);
		assertThrows(IllegalArgumentException.class, () -> HostMonitoringCollectorService.convertMetadataValue(maximumSpeed, 1000.0));
	}

	@Test
	void testCheckParameter() {
		{
			final Monitor monitor = Monitor.builder()
					.id(ID_VALUE)
					.parentId(PARENT_ID_VALUE)
					.name(LABEL_VALUE)
					.parameters(Map.of(ENERGY_USAGE_PARAMETER,
							NumberParam.builder().name(ENERGY_USAGE_PARAMETER)
							.value(3000D).build()))
					.build();
			assertTrue(HostMonitoringCollectorService.checkParameter(monitor, ENERGY_USAGE_PARAMETER));
		}

		{
			final Monitor monitor = Monitor.builder()
					.id(ID_VALUE)
					.parentId(PARENT_ID_VALUE)
					.name(LABEL_VALUE)
					.parameters(Collections.emptyMap())
					.build();
			assertFalse(HostMonitoringCollectorService.checkParameter(monitor, ENERGY_USAGE_PARAMETER));
		}

		{
			final Monitor monitor = Monitor.builder()
					.id(ID_VALUE)
					.parentId(PARENT_ID_VALUE)
					.name(LABEL_VALUE)
					.parameters(null)
					.build();
			assertFalse(HostMonitoringCollectorService.checkParameter(monitor, ENERGY_USAGE_PARAMETER));
		}
		{
			assertFalse(HostMonitoringCollectorService.checkParameter(null, ENERGY_USAGE_PARAMETER));
		}
	}

	@Test
	void testCheckMetadata() {
		{

			final Map<String, String> cpuMetadata = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
			cpuMetadata.put("maximumspeed", "4");
			cpuMetadata.put("vendor", "Intel");
			final Monitor monitor = Monitor.builder().id(ID_VALUE).parentId(PARENT_ID_VALUE).name(LABEL_VALUE)
					.metadata(cpuMetadata).build();
			assertTrue(HostMonitoringCollectorService.checkMetadata(monitor, MAXIMUM_SPEED));
		}

		{
			final Monitor monitor = Monitor.builder().id(ID_VALUE).parentId(PARENT_ID_VALUE).name(LABEL_VALUE)
					.metadata(Collections.emptyMap()).build();
			assertFalse(HostMonitoringCollectorService.checkMetadata(monitor, MAXIMUM_SPEED));
		}

		{
			final Monitor monitor = Monitor.builder().id(ID_VALUE).parentId(PARENT_ID_VALUE).name(LABEL_VALUE)
					.metadata(null).build();
			assertFalse(HostMonitoringCollectorService.checkMetadata(monitor, MAXIMUM_SPEED));
		}
		{
			assertFalse(HostMonitoringCollectorService.checkMetadata(null, MAXIMUM_SPEED));
		}
	}

	@Test
	void testConvertMetadataInfoValue() {
		{

			final Map<String, String> cpuMetadata = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
			cpuMetadata.put("maximumspeed", "4");
			cpuMetadata.put("vendor", "Intel");
			final Monitor monitor = Monitor.builder().id(ID_VALUE).parentId(PARENT_ID_VALUE).name(LABEL_VALUE)
					.metadata(cpuMetadata).monitorType(MonitorType.CPU).build();
			assertEquals(Double.toString(4*1000000.0), HostMonitoringCollectorService.convertMetadataInfoValue(monitor, MAXIMUM_SPEED));
			assertEquals("", HostMonitoringCollectorService.convertMetadataInfoValue(monitor, ""));
			assertEquals("", HostMonitoringCollectorService.convertMetadataInfoValue(monitor, null));

			final Map<String, String> cpuMetadataEmpty = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
			cpuMetadataEmpty.put("maximumspeed", "");
			cpuMetadata.put("vendor", "Intel");
			final Monitor monitorCpu = Monitor.builder().id(ID_VALUE).parentId(PARENT_ID_VALUE).name(LABEL_VALUE)
					.metadata(cpuMetadataEmpty).monitorType(MonitorType.CPU).build();
			assertEquals("", HostMonitoringCollectorService.convertMetadataInfoValue(monitorCpu, MAXIMUM_SPEED));

		}

		{
			final Monitor monitor = Monitor.builder().id(ID_VALUE).parentId(PARENT_ID_VALUE).name(LABEL_VALUE)
					.metadata(Collections.emptyMap()).build();
			assertEquals("", HostMonitoringCollectorService.convertMetadataInfoValue(monitor, MAXIMUM_SPEED));
		}

		{
			final Monitor monitor = Monitor.builder().id(ID_VALUE).parentId(PARENT_ID_VALUE).name(LABEL_VALUE)
					.metadata(null).build();
			assertEquals("", HostMonitoringCollectorService.convertMetadataInfoValue(monitor, MAXIMUM_SPEED));
		}
		{
			assertEquals("", HostMonitoringCollectorService.convertMetadataInfoValue(null, MAXIMUM_SPEED));
		}
	}

	@Test
	void testGetValueOrElse() {
		assertEquals("value", HostMonitoringCollectorService.getValueOrElse("value", "other"));
		assertEquals("other", HostMonitoringCollectorService.getValueOrElse(null, "other"));
	}

	@Test
	void testAddMetricStatus() {
		final HardwareGaugeMetric gauge = new HardwareGaugeMetric(MONITOR_STATUS_METRIC, HELP_DEFAULT, LABELS);
		final Monitor monitor = Monitor.builder()
				.id(ID_VALUE)
				.parentId(PARENT_ID_VALUE)
				.name(LABEL_VALUE)
				.parameters(Map.of(STATUS_PARAMETER,
						DiscreteParam
						.builder()
						.name(STATUS_PARAMETER)
						.state(Status.OK)
						.build()))
				.monitorType(MonitorType.ENCLOSURE)
				.build();
		hostMonitoringCollectorService.addMetric(gauge, monitor, STATUS_PARAMETER, 1.0);
		final Sample actual = gauge.samples.get(0);
		final Sample expected = new Sample(MONITOR_STATUS_METRIC, LABELS,
				Arrays.asList("", ID_VALUE, LABEL_VALUE, PARENT_ID_VALUE), Status.OK.getNumericValue());

		assertEquals(expected, actual);
	}

	@Test
	void testAddMetricNumber() {
		final HardwareCounterMetric gauge = new HardwareCounterMetric(MONITOR_ENERGY_METRIC, HELP_DEFAULT, LABELS);
		final Monitor monitor = Monitor.builder()
				.id(ID_VALUE)
				.parentId(PARENT_ID_VALUE)
				.name(LABEL_VALUE)
				.parameters(Map.of(ENERGY_USAGE_PARAMETER,
						NumberParam.builder().name(ENERGY_USAGE_PARAMETER)
						.value(3000D).build()))
				.monitorType(MonitorType.ENCLOSURE)
				.build();
		hostMonitoringCollectorService.addMetric(gauge, monitor, ENERGY_USAGE_PARAMETER, 1.0);
		final Sample actual = gauge.samples.get(0);
		final Sample expected = new Sample(MONITOR_ENERGY_METRIC, LABELS,
				Arrays.asList("", ID_VALUE, LABEL_VALUE, PARENT_ID_VALUE), 3000D);

		assertEquals(expected, actual);
	}

	@Test
	void testAddMetadataAsMetric() {
		final HardwareGaugeMetric gauge = new HardwareGaugeMetric(MAXIMUM_SPEED, HELP_DEFAULT,
				LABELS);
		final Map<String, String> cpuMetadata = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
		cpuMetadata.put("maximumspeed", "4");
		final Monitor monitor = Monitor.builder().id(ID_VALUE).parentId(PARENT_ID_VALUE).name(LABEL_VALUE)
				.metadata(cpuMetadata).monitorType(MonitorType.ENCLOSURE).build();
		hostMonitoringCollectorService.addMetadataAsMetric(gauge, monitor, MAXIMUM_SPEED, 1.0);
		final Sample actual = gauge.samples.get(0);
		final Sample expected = new Sample(MAXIMUM_SPEED, LABELS,
				Arrays.asList("", ID_VALUE, LABEL_VALUE, PARENT_ID_VALUE), 4D);
		assertEquals(expected, actual);

		final HardwareCounterMetric counter = new HardwareCounterMetric(MAXIMUM_SPEED, HELP_DEFAULT,
				LABELS);
		hostMonitoringCollectorService.addMetadataAsMetric(counter, monitor, MAXIMUM_SPEED, 1.0);
		final Sample actualCounter = counter.samples.get(0);
		final Sample expectedCounter = new Sample("maximumSpeed_total", LABELS,
				Arrays.asList("", ID_VALUE, LABEL_VALUE, PARENT_ID_VALUE), 4D);
		assertEquals(expectedCounter, actualCounter);
	}

	@Test
	void testCreateLabels() {
		final List<String> actual = hostMonitoringCollectorService.createLabels(
				Monitor.builder().id(ID_VALUE).parentId(PARENT_ID_VALUE).name(LABEL_VALUE).build(),
				LABELS);
		// fqdn, id, label, parentId
		final List<String> expected = Arrays.asList(null, ID_VALUE, LABEL_VALUE, PARENT_ID_VALUE);
		assertEquals(expected, actual);
	}

	@Test
	void testCreateLabelsOverriddenByExtraLabels() {
		Monitor build = Monitor
				.builder()
				.id(ID_VALUE)
				.parentId(PARENT_ID_VALUE)
				.name(LABEL_VALUE)
				.metadata(Map.of("fqdn", FQDN_VALUE))
				.build();

		doReturn(Map.of("site", "Data Center 1"))
			.when(multiHostsConfigurationDTO).getExtraLabels();

		final List<String> actual = hostMonitoringCollectorService.createLabels(
				build,
				Stream.concat(LABELS.stream(), Stream.of("site")).collect(Collectors.toList()));
		// fqdn, id, label, parentId, site
		final List<String> expected = Arrays.asList(FQDN_VALUE, ID_VALUE, LABEL_VALUE, PARENT_ID_VALUE, "Data Center 1");

		assertEquals(expected, actual);
	}

	@Test
	void testIsParameterAvailablePresent() {

		{
			final DiscreteParam presentParam = DiscreteParam.present();
			final Monitor monitor = Monitor.builder()
						.id(ID_VALUE)
						.parentId(PARENT_ID_VALUE)
						.name(LABEL_VALUE)
						.parameters(Map.of(PRESENT_PARAMETER, presentParam))
						.build();
			assertTrue(HostMonitoringCollectorService.isParameterAvailable(monitor, PRESENT_PARAMETER));
		}

		{
			final DiscreteParam presentParam = DiscreteParam.present();

			presentParam.save();
			presentParam.setState(null);

			final Monitor monitor = Monitor.builder()
						.id(ID_VALUE)
						.parentId(PARENT_ID_VALUE)
						.name(LABEL_VALUE)
						.parameters(Map.of(PRESENT_PARAMETER, presentParam))
						.build();
			assertFalse(HostMonitoringCollectorService.isParameterAvailable(monitor, PRESENT_PARAMETER));
		}

		{
			final Monitor monitor = Monitor.builder()
						.id(ID_VALUE)
						.parentId(PARENT_ID_VALUE)
						.name(LABEL_VALUE)
						.parameters(Collections.emptyMap())
						.build();
			assertFalse(HostMonitoringCollectorService.isParameterAvailable(monitor, PRESENT_PARAMETER));
		}
	}

	@Test
	void testAddMetricPresent() {
		final Monitor monitor = Monitor.builder()
					.id(ID_VALUE)
					.parentId(PARENT_ID_VALUE)
					.name(LABEL_VALUE)
					.monitorType(MonitorType.FAN)
					.build();
		monitor.setAsPresent();

		final HardwareGaugeMetric labeledGauge = new HardwareGaugeMetric("monitor_present",
				"Metric: Fan present - Unit: {0 = Missing ; 1 = Present}", LABELS);

		hostMonitoringCollectorService.addMetric(labeledGauge, monitor, PRESENT_PARAMETER, 1D);

		final HardwareGaugeMetric expected = new HardwareGaugeMetric(
				"monitor_present",
				"Metric: Fan present - Unit: {0 = Missing ; 1 = Present}",
				LABELS);
		expected.addMetric(Arrays.asList(null, monitor.getId(), LABEL_VALUE, PARENT_ID_VALUE), 1.0, null);

		assertEquals(expected, labeledGauge);
	}

	@Test
	void testProcessMonitorMetricInfoNoMetricName() {
		final List<MetricFamilySamples> mfs = new ArrayList<>();

		try(MockedStatic<PrometheusSpecificities> prometheusSpecificities = mockStatic(PrometheusSpecificities.class)){
			prometheusSpecificities.when(() -> PrometheusSpecificities.getInfoMetricName(MonitorType.ENCLOSURE)).thenReturn(null);
			assertTrue(mfs.isEmpty());
		}

		try(MockedStatic<PrometheusSpecificities> prometheusSpecificities = mockStatic(PrometheusSpecificities.class)){
			prometheusSpecificities.when(() -> PrometheusSpecificities.getInfoMetricName(MonitorType.ENCLOSURE)).thenReturn("");
			assertTrue(mfs.isEmpty());
		}

		try(MockedStatic<PrometheusSpecificities> prometheusSpecificities = mockStatic(PrometheusSpecificities.class)){
			prometheusSpecificities.when(() -> PrometheusSpecificities.getInfoMetricName(MonitorType.ENCLOSURE)).thenReturn(" 	");
			assertTrue(mfs.isEmpty());
		}
	}

	@Test
	void testCanParseDoubleMetadata() {

		assertFalse(HostMonitoringCollectorService.canParseDoubleValue(null));
		assertFalse(HostMonitoringCollectorService.canParseDoubleValue(""));
		assertFalse(HostMonitoringCollectorService.canParseDoubleValue(" "));
		assertFalse(HostMonitoringCollectorService.canParseDoubleValue("a"));
		assertTrue(HostMonitoringCollectorService.canParseDoubleValue("8"));
		assertTrue(HostMonitoringCollectorService.canParseDoubleValue("8 "));
		assertTrue(HostMonitoringCollectorService.canParseDoubleValue("8.0"));
	}

	@Test
	void testGetCollectTime() {
		{
			doReturn(false).when(multiHostsConfigurationDTO).isExportTimestamps();
			assertNull(hostMonitoringCollectorService.getCollectTime(Monitor.builder().build(), "parameter"));
		}

		{
			// Parameter missing
			doReturn(true).when(multiHostsConfigurationDTO).isExportTimestamps();
			assertNull(hostMonitoringCollectorService.getCollectTime(Monitor.builder().build(), "parameter"));
		}

		{
			// Parameter missing
			doReturn(true).when(multiHostsConfigurationDTO).isExportTimestamps();
			final Map<String, IParameter> parameters = Map.of("parameter",
					NumberParam.builder().collectTime(123456789L).build());
			assertEquals(123456789L, hostMonitoringCollectorService
					.getCollectTime(Monitor.builder().parameters(parameters).build(), "parameter"));
		}
	}

	@Test
	void testGetDiscoveryTime() {
		{
			doReturn(false).when(multiHostsConfigurationDTO).isExportTimestamps();
			assertNull(hostMonitoringCollectorService.getDiscoveryTime(Monitor.builder().build()));
		}

		{
			doReturn(true).when(multiHostsConfigurationDTO).isExportTimestamps();
			assertEquals(123456789L, hostMonitoringCollectorService
					.getDiscoveryTime(Monitor.builder().discoveryTime(123456789L).build()));
		}
	}
}
