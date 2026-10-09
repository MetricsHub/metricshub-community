package org.metricshub.hardware;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.metricshub.engine.common.helpers.MetricsHubConstants.MONITOR_ATTRIBUTE_CONNECTOR_ID;
import static org.metricshub.engine.common.helpers.MetricsHubConstants.MONITOR_ATTRIBUTE_ID;
import static org.metricshub.hardware.common.Constants.CPU_POWER_METRIC;
import static org.metricshub.hardware.common.Constants.DISK_CONTROLLER_ENERGY_METRIC;
import static org.metricshub.hardware.common.Constants.DISK_CONTROLLER_POWER_METRIC;
import static org.metricshub.hardware.common.Constants.FAN_ENERGY_METRIC;
import static org.metricshub.hardware.common.Constants.FAN_POWER_METRIC;
import static org.metricshub.hardware.common.Constants.FAN_SPEED_METRIC;
import static org.metricshub.hardware.common.Constants.HOST_1;
import static org.metricshub.hardware.common.Constants.HW_CPU_POWER;
import static org.metricshub.hardware.common.Constants.HW_HOST_AMBIENT_TEMPERATURE;
import static org.metricshub.hardware.common.Constants.HW_HOST_AVERAGE_CPU_TEMPERATURE;
import static org.metricshub.hardware.common.Constants.HW_MEMORY_POWER;
import static org.metricshub.hardware.common.Constants.HW_PHYSICAL_DISK_POWER;
import static org.metricshub.hardware.common.Constants.LOCALHOST;
import static org.metricshub.hardware.common.Constants.MEMORY_ENERGY_METRIC;
import static org.metricshub.hardware.common.Constants.MEMORY_POWER_METRIC;
import static org.metricshub.hardware.common.Constants.NETWORK_ENERGY_METRIC;
import static org.metricshub.hardware.common.Constants.NETWORK_LINK_SPEED_ATTRIBUTE;
import static org.metricshub.hardware.common.Constants.NETWORK_LINK_STATUS_METRIC;
import static org.metricshub.hardware.common.Constants.NETWORK_POWER_METRIC;
import static org.metricshub.hardware.common.Constants.NETWORK_TRANSMITTED_BANDWIDTH_UTILIZATION_METRIC;
import static org.metricshub.hardware.common.Constants.ON;
import static org.metricshub.hardware.common.Constants.PHYSICAL_DISK_ENERGY_METRIC;
import static org.metricshub.hardware.common.Constants.PHYSICAL_DISK_POWER_METRIC;
import static org.metricshub.hardware.common.Constants.ROBOTICS_ENERGY_METRIC;
import static org.metricshub.hardware.common.Constants.ROBOTICS_MOVE_COUNT_METRIC;
import static org.metricshub.hardware.common.Constants.ROBOTICS_POWER_METRIC;
import static org.metricshub.hardware.common.Constants.TAPE_DRIVE_ENERGY_METRIC;
import static org.metricshub.hardware.common.Constants.TAPE_DRIVE_MOUNT_COUNT_METRIC;
import static org.metricshub.hardware.common.Constants.TAPE_DRIVE_POWER_METRIC;
import static org.metricshub.hardware.common.Constants.TAPE_DRIVE_UNMOUNT_COUNT_METRIC;
import static org.metricshub.hardware.common.Constants.TEMPERATURE_METRIC;
import static org.metricshub.hardware.common.Constants.VM_1_ONLINE;
import static org.metricshub.hardware.common.Constants.VM_OFFLINE_2;
import static org.metricshub.hardware.common.Constants.VM_ONLINE_3;
import static org.metricshub.hardware.common.Constants.VM_ONLINE_BAD_POWER_SHARE_5;
import static org.metricshub.hardware.common.Constants.VM_ONLINE_NO_POWER_SHARE_4;
import static org.metricshub.hardware.constants.CommonConstants.HW_HOST_ESTIMATED_ENERGY;
import static org.metricshub.hardware.constants.CommonConstants.HW_HOST_ESTIMATED_POWER;
import static org.metricshub.hardware.constants.CommonConstants.HW_HOST_MEASURED_POWER;
import static org.metricshub.hardware.constants.CommonConstants.PRESENT_STATUS;
import static org.metricshub.hardware.constants.CpuConstants.HW_CPU_SPEED_LIMIT_LIMIT_TYPE_MAX;
import static org.metricshub.hardware.constants.CpuConstants.HW_ENERGY_CPU_METRIC;
import static org.metricshub.hardware.constants.CpuConstants.HW_HOST_CPU_THERMAL_DISSIPATION_RATE;
import static org.metricshub.hardware.constants.CpuConstants.HW_POWER_CPU_METRIC;
import static org.metricshub.hardware.constants.EnclosureConstants.HW_ENCLOSURE_POWER;
import static org.metricshub.hardware.constants.VmConstants.HW_ENERGY_VM_METRIC;
import static org.metricshub.hardware.constants.VmConstants.HW_POWER_VM_METRIC;
import static org.metricshub.hardware.constants.VmConstants.HW_VM_POWER_SHARE_METRIC;
import static org.metricshub.hardware.constants.VmConstants.HW_VM_POWER_STATE_METRIC;
import static org.metricshub.hardware.constants.VmConstants.POWER_SOURCE_ID_ATTRIBUTE;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.metricshub.engine.common.helpers.KnownMonitorType;
import org.metricshub.engine.configuration.HostConfiguration;
import org.metricshub.engine.connector.model.Connector;
import org.metricshub.engine.connector.model.ConnectorStore;
import org.metricshub.engine.connector.model.PowerMeasurement;
import org.metricshub.engine.connector.model.common.DeviceKind;
import org.metricshub.engine.connector.model.identity.ConnectorIdentity;
import org.metricshub.engine.connector.model.identity.Detection;
import org.metricshub.engine.strategy.utils.CollectHelper;
import org.metricshub.engine.telemetry.ConnectorNamespace;
import org.metricshub.engine.telemetry.HostProperties;
import org.metricshub.engine.telemetry.MetricFactory;
import org.metricshub.engine.telemetry.Monitor;
import org.metricshub.engine.telemetry.TelemetryManager;
import org.metricshub.engine.telemetry.metric.NumberMetric;
import org.metricshub.engine.telemetry.metric.StateSetMetric;
import org.metricshub.hardware.util.HwCollectHelper;

class HardwareEnergyPostExecutionServiceTest {

	private static final Long STRATEGY_TIME = 1696597422644L;
	private static final Long NEXT_STRATEGY_TIME = STRATEGY_TIME + 2 * 60 * 1000;
	private static final String TEST_CONNECTOR = "TestConnector";

	private HardwareEnergyPostExecutionService hardwareEnergyPostExecutionService;

	private TelemetryManager telemetryManager;

	private static final String DISK_CONTROLLER = KnownMonitorType.DISK_CONTROLLER.getKey();
	private static final String FAN = KnownMonitorType.FAN.getKey();
	private static final String MEMORY = KnownMonitorType.MEMORY.getKey();
	private static final String ROBOTICS = KnownMonitorType.ROBOTICS.getKey();
	private static final String TAPE_DRIVE = KnownMonitorType.TAPE_DRIVE.getKey();
	private static final String TEMPERATURE = KnownMonitorType.TEMPERATURE.getKey();
	private static final String HOST = KnownMonitorType.HOST.getKey();
	private static final String PHYSICAL_DISK = KnownMonitorType.PHYSICAL_DISK.getKey();
	private static final String NETWORK = KnownMonitorType.NETWORK.getKey();

	@BeforeEach
	void init() {
		final ConnectorStore connectorStore = new ConnectorStore();
		final Connector connector = new Connector();
		connector.setConnectorIdentity(
			ConnectorIdentity.builder()
				.detection(Detection.builder().appliesTo(Set.of(DeviceKind.OOB)).tags(Set.of("hardware")).build())
				.build()
		);
		connectorStore.setStore(new HashMap<>(Map.of(TEST_CONNECTOR, connector)));
		telemetryManager = TelemetryManager.builder()
			.hostConfiguration(HostConfiguration.builder().hostname(LOCALHOST).build())
			.strategyTime(STRATEGY_TIME)
			.connectorStore(connectorStore)
			.build();

		// Set the status ok in the host properties
		final ConnectorNamespace connectorNamespace = ConnectorNamespace.builder().isStatusOk(true).build();
		final HostProperties hostProperties = HostProperties.builder()
			.connectorNamespaces(new HashMap<>(Map.of("TestConnector", connectorNamespace)))
			.build();
		telemetryManager.setHostProperties(hostProperties);
	}

	@Test
	void testRunWithFanMonitor() {
		// Create a fan monitor
		final Monitor fanMonitor = Monitor.builder()
			.type(FAN)
			.metrics(new HashMap<>(Map.of(FAN_SPEED_METRIC, NumberMetric.builder().value(0.7).build())))
			.attributes(new HashMap<>(Map.of(MONITOR_ATTRIBUTE_CONNECTOR_ID, "TestConnector")))
			.build();

		// Set the previously created fan monitor in telemetryManager
		final Map<String, Monitor> fanMonitors = new HashMap<>(Map.of("monitor1", fanMonitor));
		telemetryManager.setMonitors(new HashMap<>(Map.of(FAN, fanMonitors)));

		// Call run method in HardwareEnergyPostExecutionService
		hardwareEnergyPostExecutionService = new HardwareEnergyPostExecutionService(telemetryManager);
		hardwareEnergyPostExecutionService.run();

		// Check the computed and collected power metric
		final NumberMetric power = fanMonitor.getMetric(FAN_POWER_METRIC, NumberMetric.class);
		assertNotNull(power);

		// Check the computed and collected energy metric
		assertNull(fanMonitor.getMetric(FAN_ENERGY_METRIC, NumberMetric.class));

		// Next collect
		telemetryManager.setStrategyTime(NEXT_STRATEGY_TIME);
		power.save();
		hardwareEnergyPostExecutionService.run();

		// Check the computed and collected energy metric
		assertNotNull(fanMonitor.getMetric(FAN_ENERGY_METRIC, NumberMetric.class));
	}

	@Test
	void testRunWithMissingFanMonitor() {
		// Create a fan monitor
		final Monitor fanMonitor = Monitor.builder()
			.type(FAN)
			.metrics(new HashMap<>(Map.of(FAN_SPEED_METRIC, NumberMetric.builder().value(0.7).build())))
			.attributes(new HashMap<>(Map.of(MONITOR_ATTRIBUTE_CONNECTOR_ID, TEST_CONNECTOR)))
			.build();

		// Set Fan monitor as missing

		final MetricFactory metricFactory = new MetricFactory(
			telemetryManager.getHostname(),
			telemetryManager.getConnectorStore()
		);
		metricFactory.collectNumberMetric(
			fanMonitor,
			String.format(PRESENT_STATUS, fanMonitor.getType()),
			0.0,
			telemetryManager.getStrategyTime()
		);

		// Set the previously created fan monitor in telemetryManager
		final Map<String, Monitor> fanMonitors = new HashMap<>(Map.of("monitor1", fanMonitor));
		telemetryManager.setMonitors(new HashMap<>(Map.of(FAN, fanMonitors)));

		// Call run method in HardwareEnergyPostExecutionService
		hardwareEnergyPostExecutionService = new HardwareEnergyPostExecutionService(telemetryManager);
		hardwareEnergyPostExecutionService.run();

		// Check that the computed and collected power metric is null
		final NumberMetric power = fanMonitor.getMetric(FAN_POWER_METRIC, NumberMetric.class);
		assertNull(power);

		// Check that the computed and collected energy metric is null
		assertNull(fanMonitor.getMetric(FAN_ENERGY_METRIC, NumberMetric.class));
	}

	@Test
	void testRunWithRoboticsMonitor() {
		// Create a robotics monitor
		final Monitor roboticsMonitor = Monitor.builder()
			.type(ROBOTICS)
			.attributes(new HashMap<>(Map.of(MONITOR_ATTRIBUTE_CONNECTOR_ID, "TestConnector")))
			.metrics(new HashMap<>(Map.of(ROBOTICS_MOVE_COUNT_METRIC, NumberMetric.builder().value(0.7).build())))
			.build();

		// Set the previously created robotics monitor in telemetryManager
		final Map<String, Monitor> roboticsMonitors = new HashMap<>(Map.of("monitor2", roboticsMonitor));
		telemetryManager.setMonitors(new HashMap<>(Map.of(ROBOTICS, roboticsMonitors)));

		// Call run method in HardwareEnergyPostExecutionService
		hardwareEnergyPostExecutionService = new HardwareEnergyPostExecutionService(telemetryManager);
		hardwareEnergyPostExecutionService.run();

		// Check the computed and collected power metric
		final NumberMetric power = roboticsMonitor.getMetric(ROBOTICS_POWER_METRIC, NumberMetric.class);
		assertNotNull(power);

		// Check the computed and collected energy metric
		assertNull(roboticsMonitor.getMetric(ROBOTICS_ENERGY_METRIC, NumberMetric.class));

		// Next collect
		telemetryManager.setStrategyTime(NEXT_STRATEGY_TIME);
		power.save();
		hardwareEnergyPostExecutionService.run();

		// Check the computed and collected energy metric
		assertNotNull(roboticsMonitor.getMetric(ROBOTICS_ENERGY_METRIC, NumberMetric.class));
	}

	@Test
	void testRunWithTapeDriveMonitor() {
		// Create a tape drive monitor
		final Monitor tapeDriveMonitor = Monitor.builder()
			.type(TAPE_DRIVE)
			.metrics(
				new HashMap<>(
					Map.of(
						TAPE_DRIVE_MOUNT_COUNT_METRIC,
						NumberMetric.builder().value(0.7).build(),
						TAPE_DRIVE_UNMOUNT_COUNT_METRIC,
						NumberMetric.builder().value(0.1).build()
					)
				)
			)
			.attributes(new HashMap<>(Map.of("name", "lto123", MONITOR_ATTRIBUTE_CONNECTOR_ID, "TestConnector")))
			.build();

		// Set the previously created tape drive monitor in telemetryManager
		final Map<String, Monitor> tapeDriveMonitors = new HashMap<>(Map.of("monitor3", tapeDriveMonitor));
		telemetryManager.setMonitors(new HashMap<>(Map.of(TAPE_DRIVE, tapeDriveMonitors)));

		// Call run method in HardwareEnergyPostExecutionService
		hardwareEnergyPostExecutionService = new HardwareEnergyPostExecutionService(telemetryManager);
		hardwareEnergyPostExecutionService.run();

		// Check the computed and collected power metric
		final NumberMetric power = tapeDriveMonitor.getMetric(TAPE_DRIVE_POWER_METRIC, NumberMetric.class);
		assertNotNull(power);

		// Check the computed and collected energy metric
		assertNull(tapeDriveMonitor.getMetric(TAPE_DRIVE_ENERGY_METRIC, NumberMetric.class));

		// Next collect
		telemetryManager.setStrategyTime(NEXT_STRATEGY_TIME);
		power.save();
		hardwareEnergyPostExecutionService.run();

		// Check the computed and collected energy metric
		assertNotNull(tapeDriveMonitor.getMetric(TAPE_DRIVE_ENERGY_METRIC, NumberMetric.class));
	}

	@Test
	void testRunWithDiskControllerMonitor() {
		// Create a disk controller monitor
		final Monitor diskControllerMonitor = Monitor.builder()
			.attributes(new HashMap<>(Map.of(MONITOR_ATTRIBUTE_CONNECTOR_ID, "TestConnector")))
			.type(DISK_CONTROLLER)
			.build();

		// Set the previously created disk controller monitor in telemetryManager
		final Map<String, Monitor> diskControllerMonitors = new HashMap<>(Map.of("monitor4", diskControllerMonitor));
		telemetryManager.setMonitors(new HashMap<>(Map.of(DISK_CONTROLLER, diskControllerMonitors)));

		// Call run method in HardwareEnergyPostExecutionService
		hardwareEnergyPostExecutionService = new HardwareEnergyPostExecutionService(telemetryManager);
		hardwareEnergyPostExecutionService.run();

		// Check the computed and collected power metric
		final NumberMetric power = diskControllerMonitor.getMetric(DISK_CONTROLLER_POWER_METRIC, NumberMetric.class);
		assertNotNull(power);

		// Check the computed and collected energy metric
		assertNull(diskControllerMonitor.getMetric(DISK_CONTROLLER_ENERGY_METRIC, NumberMetric.class));

		// Next collect
		telemetryManager.setStrategyTime(NEXT_STRATEGY_TIME);
		power.save();
		hardwareEnergyPostExecutionService.run();

		// Check the computed and collected energy metric
		assertNotNull(diskControllerMonitor.getMetric(DISK_CONTROLLER_ENERGY_METRIC, NumberMetric.class));
	}

	@Test
	void testRunWithMemoryMonitor() {
		// Create a memory monitor
		final Monitor memoryMonitor = Monitor.builder()
			.type(MEMORY)
			.attributes(new HashMap<>(Map.of(MONITOR_ATTRIBUTE_CONNECTOR_ID, "TestConnector")))
			.build();

		// Set the previously created monitor in telemetryManager
		final Map<String, Monitor> monitors = new HashMap<>(Map.of("monitor1", memoryMonitor));
		telemetryManager.setMonitors(new HashMap<>(Map.of(MEMORY, monitors)));

		// Call run method in HardwareEnergyPostExecutionService
		hardwareEnergyPostExecutionService = new HardwareEnergyPostExecutionService(telemetryManager);
		hardwareEnergyPostExecutionService.run();

		// Check the computed and collected power metric
		final NumberMetric power = memoryMonitor.getMetric(MEMORY_POWER_METRIC, NumberMetric.class);
		assertNotNull(power);

		// Check the computed and collected energy metric
		assertNull(memoryMonitor.getMetric(MEMORY_ENERGY_METRIC, NumberMetric.class));

		// Next collect
		telemetryManager.setStrategyTime(NEXT_STRATEGY_TIME);
		power.save();
		hardwareEnergyPostExecutionService.run();

		// Check the computed and collected energy metric
		assertNotNull(memoryMonitor.getMetric(MEMORY_ENERGY_METRIC, NumberMetric.class));
	}

	@Test
	void testRunWithPhysicalDiskMonitor() {
		// Create a physical disk monitor
		final Monitor physicalDiskMonitor = Monitor.builder()
			.type(PHYSICAL_DISK)
			.attributes(new HashMap<>(Map.of(MONITOR_ATTRIBUTE_CONNECTOR_ID, "TestConnector")))
			.build();

		// Set the previously created physical disk monitor in telemetryManager
		final Map<String, Monitor> physicalDiskMonitors = new HashMap<>(Map.of("monitor5", physicalDiskMonitor));
		telemetryManager.setMonitors(new HashMap<>(Map.of(PHYSICAL_DISK, physicalDiskMonitors)));

		// Call run method in HardwareEnergyPostExecutionService
		hardwareEnergyPostExecutionService = new HardwareEnergyPostExecutionService(telemetryManager);
		hardwareEnergyPostExecutionService.run();

		// Check the computed and collected power metric
		final NumberMetric power = physicalDiskMonitor.getMetric(PHYSICAL_DISK_POWER_METRIC, NumberMetric.class);
		assertNotNull(power);

		// Check the computed and collected energy metric
		assertNull(physicalDiskMonitor.getMetric(PHYSICAL_DISK_ENERGY_METRIC, NumberMetric.class));

		// Next collect
		telemetryManager.setStrategyTime(NEXT_STRATEGY_TIME);
		power.save();
		hardwareEnergyPostExecutionService.run();

		// Check the computed and collected energy metric
		assertNotNull(physicalDiskMonitor.getMetric(PHYSICAL_DISK_ENERGY_METRIC, NumberMetric.class));
	}

	@Test
	void testRunWithNetworkMonitor() {
		// Create a network monitor
		final Monitor networkMonitor = Monitor.builder()
			.type(NETWORK)
			.attributes(
				new HashMap<>(
					Map.of(
						"name",
						"real_network_card",
						NETWORK_LINK_SPEED_ATTRIBUTE,
						"100.0",
						MONITOR_ATTRIBUTE_CONNECTOR_ID,
						"TestConnector"
					)
				)
			)
			.metrics(
				new HashMap<>(
					Map.of(
						NETWORK_LINK_STATUS_METRIC,
						NumberMetric.builder().value(1.0).build(),
						NETWORK_TRANSMITTED_BANDWIDTH_UTILIZATION_METRIC,
						NumberMetric.builder().value(10.0).build()
					)
				)
			)
			.build();

		// Set the previously created network monitor in telemetryManager
		final Map<String, Monitor> networkMonitors = new HashMap<>(Map.of("monitor2", networkMonitor));
		telemetryManager.setMonitors(new HashMap<>(Map.of(NETWORK, networkMonitors)));
		// Call run method in HardwareEnergyPostExecutionService
		hardwareEnergyPostExecutionService = new HardwareEnergyPostExecutionService(telemetryManager);
		hardwareEnergyPostExecutionService.run();

		// Check the computed and collected power metric
		final NumberMetric power = networkMonitor.getMetric(NETWORK_POWER_METRIC, NumberMetric.class);
		assertNotNull(power);

		// Check the computed and collected energy metric
		assertNull(networkMonitor.getMetric(NETWORK_ENERGY_METRIC, NumberMetric.class));

		// Next collect
		telemetryManager.setStrategyTime(NEXT_STRATEGY_TIME);
		power.save();
		hardwareEnergyPostExecutionService.run();

		// Check the computed and collected energy metric
		assertNotNull(networkMonitor.getMetric(NETWORK_ENERGY_METRIC, NumberMetric.class));
	}

	@Test
	void testComputeHostTemperatureMetrics() {
		// Create a host monitor
		final Monitor hostMonitor = Monitor.builder().type(HOST).build();

		// Set the host as endpoint
		hostMonitor.setAsEndpoint();

		// Create a temperature monitor
		final Monitor temperatureMonitor = Monitor.builder().type(TEMPERATURE).build();

		// Set the previously created monitor in telemetryManager
		telemetryManager.addNewMonitor(hostMonitor, KnownMonitorType.HOST.getKey(), "monitor0");
		telemetryManager.addNewMonitor(temperatureMonitor, KnownMonitorType.TEMPERATURE.getKey(), "monitor1");

		// Check the computed and collected metrics
		final MetricFactory metricFactory = new MetricFactory(
			telemetryManager.getHostname(),
			telemetryManager.getConnectorStore()
		);
		metricFactory.collectNumberMetric(temperatureMonitor, TEMPERATURE_METRIC, 10.0, telemetryManager.getStrategyTime());

		// Call run method in HardwareEnergyPostExecutionService
		hardwareEnergyPostExecutionService = new HardwareEnergyPostExecutionService(telemetryManager);
		hardwareEnergyPostExecutionService.run();

		//  Check the computed and collected temperature metrics (the host is not a cpu sensor)
		assertNotNull(hostMonitor.getMetric(HW_HOST_AMBIENT_TEMPERATURE, NumberMetric.class));
		assertNull(hostMonitor.getMetric(HW_HOST_AVERAGE_CPU_TEMPERATURE, NumberMetric.class));
	}

	@Test
	void testComputeHostPowerAndEnergyMetricsWithMissingMonitors() {
		// Initialize the host and other monitors
		final Monitor host = Monitor.builder()
			.id(KnownMonitorType.HOST.getKey())
			.type(KnownMonitorType.HOST.getKey())
			.attributes(new HashMap<>(Map.of(MONITOR_ATTRIBUTE_CONNECTOR_ID, "TestConnector")))
			.build();
		host.setAsEndpoint();

		// Set a previous collected host power value
		final MetricFactory metricFactory = new MetricFactory(
			telemetryManager.getHostname(),
			telemetryManager.getConnectorStore()
		);
		final NumberMetric previousPowerValue = metricFactory.collectNumberMetric(
			host,
			HW_HOST_ESTIMATED_POWER,
			60.0,
			telemetryManager.getStrategyTime() - 120 * 1000
		);
		previousPowerValue.save();

		final Monitor enclosure = Monitor.builder()
			.id(KnownMonitorType.ENCLOSURE.getKey())
			.type(KnownMonitorType.ENCLOSURE.getKey())
			.attributes(new HashMap<>(Map.of(MONITOR_ATTRIBUTE_CONNECTOR_ID, "TestConnector")))
			.build();

		final Monitor cpu = Monitor.builder()
			.id("cpu1")
			.type(KnownMonitorType.CPU.getKey())
			.attributes(new HashMap<>(Map.of(MONITOR_ATTRIBUTE_CONNECTOR_ID, "TestConnector")))
			.build();
		// Value from a previous cycle (saved): it is not collected by the connector in the current cycle, so it is re-estimated
		metricFactory.collectNumberMetric(cpu, HW_CPU_POWER, 60.0, telemetryManager.getStrategyTime()).save();
		metricFactory.collectNumberMetric(
			cpu,
			String.format(PRESENT_STATUS, cpu.getType()),
			0.0,
			telemetryManager.getStrategyTime()
		);

		final Monitor memory = Monitor.builder()
			.id("memory1")
			.type(KnownMonitorType.MEMORY.getKey())
			.attributes(new HashMap<>(Map.of(MONITOR_ATTRIBUTE_CONNECTOR_ID, "TestConnector")))
			.build();
		metricFactory.collectNumberMetric(
			memory,
			String.format(PRESENT_STATUS, memory.getType()),
			1.0,
			telemetryManager.getStrategyTime()
		);

		// Value from a previous cycle (saved): it is not collected by the connector in the current cycle, so it is re-estimated
		metricFactory.collectNumberMetric(memory, HW_MEMORY_POWER, 4.0, telemetryManager.getStrategyTime()).save();

		final Monitor disk = Monitor.builder()
			.id("disk_nvm_1")
			.type(KnownMonitorType.PHYSICAL_DISK.getKey())
			.attributes(new HashMap<>(Map.of(MONITOR_ATTRIBUTE_CONNECTOR_ID, "TestConnector")))
			.build();

		// Value from a previous cycle (saved): it is not collected by the connector in the current cycle, so it is re-estimated
		metricFactory.collectNumberMetric(disk, HW_PHYSICAL_DISK_POWER, 6.0, telemetryManager.getStrategyTime()).save();

		final Monitor diskNoPower = Monitor.builder()
			.id("disk_noPower")
			.type(KnownMonitorType.PHYSICAL_DISK.getKey())
			.attributes(new HashMap<>(Map.of(MONITOR_ATTRIBUTE_CONNECTOR_ID, "TestConnector")))
			.build();

		final Monitor missingDisk = Monitor.builder()
			.id("disk_nvm_2")
			.type(KnownMonitorType.PHYSICAL_DISK.getKey())
			.attributes(new HashMap<>(Map.of(MONITOR_ATTRIBUTE_CONNECTOR_ID, "TestConnector")))
			.build();

		// Add the previously created monitors to telemetry manager
		telemetryManager.addNewMonitor(host, KnownMonitorType.HOST.getKey(), KnownMonitorType.HOST.getKey());
		telemetryManager.addNewMonitor(cpu, KnownMonitorType.CPU.getKey(), "cpu1");
		telemetryManager.addNewMonitor(disk, KnownMonitorType.PHYSICAL_DISK.getKey(), "disk_nvm_1");
		telemetryManager.addNewMonitor(memory, KnownMonitorType.MEMORY.getKey(), "memory1");
		telemetryManager.addNewMonitor(diskNoPower, KnownMonitorType.PHYSICAL_DISK.getKey(), "disk_noPower");
		telemetryManager.addNewMonitor(missingDisk, KnownMonitorType.PHYSICAL_DISK.getKey(), "disk_nvm_2");
		telemetryManager.addNewMonitor(enclosure, KnownMonitorType.ENCLOSURE.getKey(), KnownMonitorType.ENCLOSURE.getKey());

		// Call run method in HardwareEnergyPostExecutionService
		hardwareEnergyPostExecutionService = new HardwareEnergyPostExecutionService(telemetryManager);
		hardwareEnergyPostExecutionService.run();

		// Check that the missing monitor is not considered when power and energy computation is done
		assertNotNull(host.getMetric(HW_HOST_ESTIMATED_POWER, NumberMetric.class));
		assertEquals(41.11, host.getMetric(HW_HOST_ESTIMATED_POWER, NumberMetric.class).getValue());
		assertNotNull(host.getMetric(HW_HOST_ESTIMATED_ENERGY, NumberMetric.class));
		assertEquals(4933.2, host.getMetric(HW_HOST_ESTIMATED_ENERGY, NumberMetric.class).getValue());

		// Reset the cpu as "not missing"
		metricFactory.collectNumberMetric(
			cpu,
			String.format(PRESENT_STATUS, cpu.getType()),
			1.0,
			telemetryManager.getStrategyTime()
		);

		// Call again run method in HardwareEnergyPostExecutionService
		hardwareEnergyPostExecutionService = new HardwareEnergyPostExecutionService(telemetryManager);
		hardwareEnergyPostExecutionService.run();

		// Check that cpu monitor is now considered when power and energy computation is done
		assertNotNull(host.getMetric(HW_HOST_ESTIMATED_POWER, NumberMetric.class));
		assertEquals(54.31, host.getMetric(HW_HOST_ESTIMATED_POWER, NumberMetric.class).getValue());
		assertNotNull(host.getMetric(HW_HOST_ESTIMATED_ENERGY, NumberMetric.class));
		assertEquals(6517.200000000001, host.getMetric(HW_HOST_ESTIMATED_ENERGY, NumberMetric.class).getValue());
	}

	@Test
	void testComputeHostPowerAndEnergyMetrics() {
		// Initialize the host and other monitors
		final Monitor host = Monitor.builder()
			.id(KnownMonitorType.HOST.getKey())
			.type(KnownMonitorType.HOST.getKey())
			.attributes(new HashMap<>(Map.of(MONITOR_ATTRIBUTE_CONNECTOR_ID, "TestConnector")))
			.build();
		host.setAsEndpoint();

		// Set a previous collected host power value
		final MetricFactory metricFactory = new MetricFactory(
			telemetryManager.getHostname(),
			telemetryManager.getConnectorStore()
		);
		final NumberMetric previousPowerValue = metricFactory.collectNumberMetric(
			host,
			HW_HOST_ESTIMATED_POWER,
			60.0,
			telemetryManager.getStrategyTime() - 120 * 1000
		);
		previousPowerValue.save();

		final Monitor enclosure = Monitor.builder()
			.id(KnownMonitorType.ENCLOSURE.getKey())
			.type(KnownMonitorType.ENCLOSURE.getKey())
			.attributes(new HashMap<>(Map.of(MONITOR_ATTRIBUTE_CONNECTOR_ID, "TestConnector")))
			.build();

		final Monitor cpu = Monitor.builder()
			.id("cpu1")
			.type(KnownMonitorType.CPU.getKey())
			.attributes(new HashMap<>(Map.of(MONITOR_ATTRIBUTE_CONNECTOR_ID, "TestConnector")))
			.build();
		// Value from a previous cycle (saved): it is not collected by the connector in the current cycle, so it is re-estimated
		metricFactory.collectNumberMetric(cpu, HW_CPU_POWER, 60.0, telemetryManager.getStrategyTime()).save();

		final Monitor memory = Monitor.builder()
			.id("memory1")
			.type(KnownMonitorType.MEMORY.getKey())
			.attributes(new HashMap<>(Map.of(MONITOR_ATTRIBUTE_CONNECTOR_ID, "TestConnector")))
			.build();

		// Value from a previous cycle (saved): it is not collected by the connector in the current cycle, so it is re-estimated
		metricFactory.collectNumberMetric(memory, HW_MEMORY_POWER, 4.0, telemetryManager.getStrategyTime()).save();

		final Monitor disk = Monitor.builder()
			.id("disk_nvm_1")
			.type(KnownMonitorType.PHYSICAL_DISK.getKey())
			.attributes(new HashMap<>(Map.of(MONITOR_ATTRIBUTE_CONNECTOR_ID, "TestConnector")))
			.build();

		// Value from a previous cycle (saved): it is not collected by the connector in the current cycle, so it is re-estimated
		metricFactory.collectNumberMetric(disk, HW_PHYSICAL_DISK_POWER, 6.0, telemetryManager.getStrategyTime()).save();

		final Monitor diskNoPower = Monitor.builder()
			.id("disk_noPower")
			.type(KnownMonitorType.PHYSICAL_DISK.getKey())
			.attributes(new HashMap<>(Map.of(MONITOR_ATTRIBUTE_CONNECTOR_ID, "TestConnector")))
			.build();

		final Monitor missingDisk = Monitor.builder()
			.id("disk_nvm_2")
			.type(KnownMonitorType.PHYSICAL_DISK.getKey())
			.attributes(new HashMap<>(Map.of(MONITOR_ATTRIBUTE_CONNECTOR_ID, "TestConnector")))
			.build();

		// Add the previously created monitors to telemetry manager
		telemetryManager.addNewMonitor(host, KnownMonitorType.HOST.getKey(), KnownMonitorType.HOST.getKey());
		telemetryManager.addNewMonitor(cpu, KnownMonitorType.CPU.getKey(), "cpu1");
		telemetryManager.addNewMonitor(disk, KnownMonitorType.PHYSICAL_DISK.getKey(), "disk_nvm_1");
		telemetryManager.addNewMonitor(memory, KnownMonitorType.MEMORY.getKey(), "memory1");
		telemetryManager.addNewMonitor(diskNoPower, KnownMonitorType.PHYSICAL_DISK.getKey(), "disk_noPower");
		telemetryManager.addNewMonitor(missingDisk, KnownMonitorType.PHYSICAL_DISK.getKey(), "disk_nvm_2");
		telemetryManager.addNewMonitor(enclosure, KnownMonitorType.ENCLOSURE.getKey(), KnownMonitorType.ENCLOSURE.getKey());

		// Call run method in HardwareEnergyPostExecutionService
		hardwareEnergyPostExecutionService = new HardwareEnergyPostExecutionService(telemetryManager);
		hardwareEnergyPostExecutionService.run();

		//  Check the computed and collected temperature metrics (the host is not a cpu sensor)
		assertNotNull(host.getMetric(HW_HOST_ESTIMATED_POWER, NumberMetric.class));
		assertEquals(54.31, host.getMetric(HW_HOST_ESTIMATED_POWER, NumberMetric.class).getValue());
		assertNotNull(host.getMetric(HW_HOST_ESTIMATED_ENERGY, NumberMetric.class));
		assertEquals(6517.200000000001, host.getMetric(HW_HOST_ESTIMATED_ENERGY, NumberMetric.class).getValue());
	}

	private static Monitor buildMonitor(final String monitorType, final String id) {
		return Monitor.builder()
			.id(id)
			.attributes(new HashMap<>(Map.of(MONITOR_ATTRIBUTE_CONNECTOR_ID, "TestConnector")))
			.type(monitorType)
			.build();
	}

	@Test
	void testRunWithVmMonitor() {
		// Create the metric factory to collect metrics

		final MetricFactory metricFactory = new MetricFactory(
			telemetryManager.getHostname(),
			telemetryManager.getConnectorStore()
		);

		// Prepare the monitors and their metrics

		final Monitor vmOnline1 = buildMonitor(KnownMonitorType.VM.getKey(), VM_1_ONLINE);
		vmOnline1.addMetric(HW_VM_POWER_STATE_METRIC, StateSetMetric.builder().value(ON).build());
		metricFactory.collectNumberMetric(vmOnline1, HW_VM_POWER_SHARE_METRIC, 5.0, telemetryManager.getStrategyTime());

		final Monitor vmOffline2 = buildMonitor(KnownMonitorType.VM.getKey(), VM_OFFLINE_2);
		vmOffline2.addMetric(HW_VM_POWER_STATE_METRIC, StateSetMetric.builder().value("Off").build());
		metricFactory.collectNumberMetric(vmOffline2, HW_VM_POWER_SHARE_METRIC, 10.0, telemetryManager.getStrategyTime());

		final Monitor vmOnline3 = buildMonitor(KnownMonitorType.VM.getKey(), VM_ONLINE_3);
		vmOnline3.addMetric(HW_VM_POWER_STATE_METRIC, StateSetMetric.builder().value(ON).build());
		metricFactory.collectNumberMetric(vmOnline3, HW_VM_POWER_SHARE_METRIC, 5.0, telemetryManager.getStrategyTime());

		final Monitor vmOnlineNoPowerShare4 = buildMonitor(KnownMonitorType.VM.getKey(), VM_ONLINE_NO_POWER_SHARE_4);
		vmOnlineNoPowerShare4.addMetric(HW_VM_POWER_STATE_METRIC, StateSetMetric.builder().value(ON).build());

		final Monitor vmOnlineBadPowerShare5 = buildMonitor(KnownMonitorType.VM.getKey(), VM_ONLINE_BAD_POWER_SHARE_5);
		vmOnlineBadPowerShare5.addMetric(HW_VM_POWER_STATE_METRIC, StateSetMetric.builder().value(ON).build());
		metricFactory.collectNumberMetric(
			vmOnlineBadPowerShare5,
			HW_VM_POWER_SHARE_METRIC,
			-15.0,
			telemetryManager.getStrategyTime()
		);

		// Create the host monitor
		final Monitor host = buildMonitor(KnownMonitorType.HOST.getKey(), HOST_1);
		host.setAsEndpoint();

		// Set the host monitor estimated power
		metricFactory.collectNumberMetric(host, HW_HOST_ESTIMATED_POWER, 100.0, telemetryManager.getStrategyTime());

		// Add the created monitors to telemetry manager
		telemetryManager.addNewMonitor(host, KnownMonitorType.HOST.getKey(), HOST_1);
		telemetryManager.addNewMonitor(vmOnline1, KnownMonitorType.VM.getKey(), VM_1_ONLINE);
		telemetryManager.addNewMonitor(vmOffline2, KnownMonitorType.VM.getKey(), VM_OFFLINE_2);
		telemetryManager.addNewMonitor(vmOnline3, KnownMonitorType.VM.getKey(), VM_ONLINE_3);
		telemetryManager.addNewMonitor(vmOnlineNoPowerShare4, KnownMonitorType.VM.getKey(), VM_ONLINE_NO_POWER_SHARE_4);
		telemetryManager.addNewMonitor(vmOnlineBadPowerShare5, KnownMonitorType.VM.getKey(), VM_ONLINE_BAD_POWER_SHARE_5);

		// Add power source id attribute to the created VM monitors
		vmOnline1.addAttribute(POWER_SOURCE_ID_ATTRIBUTE, host.getId());

		vmOffline2.addAttribute(POWER_SOURCE_ID_ATTRIBUTE, host.getId());

		vmOnline3.addAttribute(POWER_SOURCE_ID_ATTRIBUTE, host.getId());

		vmOnlineNoPowerShare4.addAttribute(POWER_SOURCE_ID_ATTRIBUTE, host.getId());

		vmOnlineBadPowerShare5.addAttribute(POWER_SOURCE_ID_ATTRIBUTE, host.getId());

		// Set previous power values
		NumberMetric previousPowerValue = metricFactory.collectNumberMetric(
			vmOnline1,
			HW_POWER_VM_METRIC,
			10.0,
			telemetryManager.getStrategyTime() - 120 * 1000
		);
		previousPowerValue.save();

		previousPowerValue = metricFactory.collectNumberMetric(
			vmOffline2,
			HW_POWER_VM_METRIC,
			1.0,
			telemetryManager.getStrategyTime() - 120 * 1000
		);
		previousPowerValue.save();

		previousPowerValue = metricFactory.collectNumberMetric(
			vmOnline3,
			HW_POWER_VM_METRIC,
			2.0,
			telemetryManager.getStrategyTime() - 120 * 1000
		);
		previousPowerValue.save();

		previousPowerValue = metricFactory.collectNumberMetric(
			vmOnlineNoPowerShare4,
			HW_POWER_VM_METRIC,
			12.0,
			telemetryManager.getStrategyTime() - 120 * 1000
		);
		previousPowerValue.save();

		previousPowerValue = metricFactory.collectNumberMetric(
			vmOnlineBadPowerShare5,
			HW_POWER_VM_METRIC,
			5.0,
			telemetryManager.getStrategyTime() - 120 * 1000
		);
		previousPowerValue.save();

		// Call run method in HardwareEnergyPostExecutionService
		hardwareEnergyPostExecutionService = new HardwareEnergyPostExecutionService(telemetryManager);
		hardwareEnergyPostExecutionService.run();

		assertEquals(50.0, CollectHelper.getNumberMetricValue(vmOnline1, HW_POWER_VM_METRIC, false));
		assertEquals(6000, CollectHelper.getNumberMetricValue(vmOnline1, HW_ENERGY_VM_METRIC, false));

		assertEquals(0.0, CollectHelper.getNumberMetricValue(vmOffline2, HW_POWER_VM_METRIC, false));
		assertEquals(0.0, CollectHelper.getNumberMetricValue(vmOffline2, HW_ENERGY_VM_METRIC, false));

		assertEquals(50.0, CollectHelper.getNumberMetricValue(vmOnline3, HW_POWER_VM_METRIC, false));
		assertEquals(6000, CollectHelper.getNumberMetricValue(vmOnline3, HW_ENERGY_VM_METRIC, false));

		assertEquals(0.0, CollectHelper.getNumberMetricValue(vmOnlineNoPowerShare4, HW_POWER_VM_METRIC, false));
		assertEquals(0.0, CollectHelper.getNumberMetricValue(vmOnlineNoPowerShare4, HW_ENERGY_VM_METRIC, false));

		assertEquals(0.0, CollectHelper.getNumberMetricValue(vmOnlineBadPowerShare5, HW_POWER_VM_METRIC, false));
		assertEquals(0.0, CollectHelper.getNumberMetricValue(vmOnlineBadPowerShare5, HW_ENERGY_VM_METRIC, false));
	}

	@Test
	void testRunWithVmWithMissingMonitors() {
		// Create the metric factory to collect metrics

		final MetricFactory metricFactory = new MetricFactory(
			telemetryManager.getHostname(),
			telemetryManager.getConnectorStore()
		);

		// Prepare the monitors and their metrics

		final Monitor vmOnline1 = buildMonitor(KnownMonitorType.VM.getKey(), VM_1_ONLINE);
		vmOnline1.addMetric(HW_VM_POWER_STATE_METRIC, StateSetMetric.builder().value(ON).build());
		metricFactory.collectNumberMetric(vmOnline1, HW_VM_POWER_SHARE_METRIC, 5.0, telemetryManager.getStrategyTime());
		metricFactory.collectNumberMetric(
			vmOnline1,
			String.format(PRESENT_STATUS, vmOnline1.getType()),
			0.0,
			telemetryManager.getStrategyTime()
		);

		final Monitor vmOffline2 = buildMonitor(KnownMonitorType.VM.getKey(), VM_OFFLINE_2);
		vmOffline2.addMetric(HW_VM_POWER_STATE_METRIC, StateSetMetric.builder().value("Off").build());
		metricFactory.collectNumberMetric(vmOffline2, HW_VM_POWER_SHARE_METRIC, 10.0, telemetryManager.getStrategyTime());

		final Monitor vmOnline3 = buildMonitor(KnownMonitorType.VM.getKey(), VM_ONLINE_3);
		vmOnline3.addMetric(HW_VM_POWER_STATE_METRIC, StateSetMetric.builder().value(ON).build());
		metricFactory.collectNumberMetric(vmOnline3, HW_VM_POWER_SHARE_METRIC, 5.0, telemetryManager.getStrategyTime());
		metricFactory.collectNumberMetric(
			vmOnline3,
			String.format(PRESENT_STATUS, vmOnline3.getType()),
			0.0,
			telemetryManager.getStrategyTime()
		);

		final Monitor vmOnlineNoPowerShare4 = buildMonitor(KnownMonitorType.VM.getKey(), VM_ONLINE_NO_POWER_SHARE_4);
		vmOnlineNoPowerShare4.addMetric(HW_VM_POWER_STATE_METRIC, StateSetMetric.builder().value(ON).build());

		final Monitor vmOnlineBadPowerShare5 = buildMonitor(KnownMonitorType.VM.getKey(), VM_ONLINE_BAD_POWER_SHARE_5);
		vmOnlineBadPowerShare5.addMetric(HW_VM_POWER_STATE_METRIC, StateSetMetric.builder().value(ON).build());
		metricFactory.collectNumberMetric(
			vmOnlineBadPowerShare5,
			HW_VM_POWER_SHARE_METRIC,
			-15.0,
			telemetryManager.getStrategyTime()
		);

		// Create the host monitor
		final Monitor host = buildMonitor(KnownMonitorType.HOST.getKey(), HOST_1);
		host.setAsEndpoint();

		// Set the host monitor estimated power
		metricFactory.collectNumberMetric(host, HW_HOST_ESTIMATED_POWER, 100.0, telemetryManager.getStrategyTime());

		// Add the created monitors to telemetry manager
		telemetryManager.addNewMonitor(host, KnownMonitorType.HOST.getKey(), HOST_1);
		telemetryManager.addNewMonitor(vmOnline1, KnownMonitorType.VM.getKey(), VM_1_ONLINE);
		telemetryManager.addNewMonitor(vmOffline2, KnownMonitorType.VM.getKey(), VM_OFFLINE_2);
		telemetryManager.addNewMonitor(vmOnline3, KnownMonitorType.VM.getKey(), VM_ONLINE_3);
		telemetryManager.addNewMonitor(vmOnlineNoPowerShare4, KnownMonitorType.VM.getKey(), VM_ONLINE_NO_POWER_SHARE_4);
		telemetryManager.addNewMonitor(vmOnlineBadPowerShare5, KnownMonitorType.VM.getKey(), VM_ONLINE_BAD_POWER_SHARE_5);

		// Add power source id attribute to the created VM monitors
		vmOnline1.addAttribute(POWER_SOURCE_ID_ATTRIBUTE, host.getId());

		vmOffline2.addAttribute(POWER_SOURCE_ID_ATTRIBUTE, host.getId());

		vmOnline3.addAttribute(POWER_SOURCE_ID_ATTRIBUTE, host.getId());

		vmOnlineNoPowerShare4.addAttribute(POWER_SOURCE_ID_ATTRIBUTE, host.getId());

		vmOnlineBadPowerShare5.addAttribute(POWER_SOURCE_ID_ATTRIBUTE, host.getId());

		// Set previous power values

		NumberMetric previousPowerValue = metricFactory.collectNumberMetric(
			vmOffline2,
			HW_POWER_VM_METRIC,
			1.0,
			telemetryManager.getStrategyTime() - 120 * 1000
		);
		previousPowerValue.save();

		previousPowerValue = metricFactory.collectNumberMetric(
			vmOnlineNoPowerShare4,
			HW_POWER_VM_METRIC,
			12.0,
			telemetryManager.getStrategyTime() - 120 * 1000
		);
		previousPowerValue.save();

		previousPowerValue = metricFactory.collectNumberMetric(
			vmOnlineBadPowerShare5,
			HW_POWER_VM_METRIC,
			5.0,
			telemetryManager.getStrategyTime() - 120 * 1000
		);
		previousPowerValue.save();

		// Call run method in HardwareEnergyPostExecutionService
		hardwareEnergyPostExecutionService = new HardwareEnergyPostExecutionService(telemetryManager);
		hardwareEnergyPostExecutionService.run();

		assertEquals(0.0, CollectHelper.getNumberMetricValue(vmOffline2, HW_POWER_VM_METRIC, false));
		assertEquals(0.0, CollectHelper.getNumberMetricValue(vmOffline2, HW_ENERGY_VM_METRIC, false));

		assertEquals(0.0, CollectHelper.getNumberMetricValue(vmOnlineNoPowerShare4, HW_POWER_VM_METRIC, false));
		assertEquals(0.0, CollectHelper.getNumberMetricValue(vmOnlineNoPowerShare4, HW_ENERGY_VM_METRIC, false));

		assertEquals(0.0, CollectHelper.getNumberMetricValue(vmOnlineBadPowerShare5, HW_POWER_VM_METRIC, false));
		assertEquals(0.0, CollectHelper.getNumberMetricValue(vmOnlineBadPowerShare5, HW_ENERGY_VM_METRIC, false));

		// Check that power and energy metrics are null on missing monitors vmOnline1 and vmOnline3
		assertNull(CollectHelper.getNumberMetricValue(vmOnline1, HW_POWER_VM_METRIC, false));
		assertNull(CollectHelper.getNumberMetricValue(vmOnline3, HW_POWER_VM_METRIC, false));
		assertNull(CollectHelper.getNumberMetricValue(vmOnline1, HW_ENERGY_VM_METRIC, false));
		assertNull(CollectHelper.getNumberMetricValue(vmOnline3, HW_ENERGY_VM_METRIC, false));
	}

	@Test
	void testRunWithCpuMonitor() {
		// Create a CPU monitor
		final Monitor cpuMonitor = buildMonitor(KnownMonitorType.CPU.getKey(), KnownMonitorType.CPU.getKey());

		// Default is 0.25 * (2500000000 / 1000000000) * 19 = 1187.5
		final MetricFactory metricFactory = new MetricFactory(
			telemetryManager.getHostname(),
			telemetryManager.getConnectorStore()
		);
		final NumberMetric collectedPowerMetric = metricFactory.collectNumberMetric(
			cpuMonitor,
			CPU_POWER_METRIC,
			11.875,
			telemetryManager.getStrategyTime()
		);
		collectedPowerMetric.save();

		// CPU speed limit is 2000000000 and CPU thermal dissipation rate is 0.5
		telemetryManager.setStrategyTime(NEXT_STRATEGY_TIME);
		metricFactory.collectNumberMetric(
			cpuMonitor,
			HW_CPU_SPEED_LIMIT_LIMIT_TYPE_MAX,
			2000000000D,
			telemetryManager.getStrategyTime()
		);

		metricFactory.collectNumberMetric(
			cpuMonitor,
			HW_HOST_CPU_THERMAL_DISSIPATION_RATE,
			0.5,
			telemetryManager.getStrategyTime()
		);

		// Add the CPU monitor to telemetry manager
		telemetryManager.addNewMonitor(cpuMonitor, KnownMonitorType.CPU.getKey(), KnownMonitorType.CPU.getKey());

		// Call run method in HardwareEnergyPostExecutionService
		hardwareEnergyPostExecutionService = new HardwareEnergyPostExecutionService(telemetryManager);
		hardwareEnergyPostExecutionService.run();

		// Check the collected CPU power and energy metrics
		assertEquals(19.0, cpuMonitor.getMetric(HW_POWER_CPU_METRIC, NumberMetric.class).getValue());
		assertEquals(2280.0, cpuMonitor.getMetric(HW_ENERGY_CPU_METRIC, NumberMetric.class).getValue());
	}

	@Test
	void testRunWithNonHardwareConnector() {
		final Path yamlTestPath = Paths.get("src", "test", "resources", "Linux");

		final ConnectorStore connectorStore = new ConnectorStore(yamlTestPath);
		telemetryManager.setConnectorStore(connectorStore);

		// Create a physical disk monitor
		final Monitor physicalDiskMonitor = Monitor.builder()
			.type(PHYSICAL_DISK)
			.attributes(new HashMap<>(Map.of(MONITOR_ATTRIBUTE_CONNECTOR_ID, "Linux")))
			.build();

		// Set the previously created physical disk monitor in telemetryManager
		final Map<String, Monitor> physicalDiskMonitors = new HashMap<>(Map.of("monitor5", physicalDiskMonitor));
		telemetryManager.setMonitors(new HashMap<>(Map.of(PHYSICAL_DISK, physicalDiskMonitors)));

		// Call run method in HardwareEnergyPostExecutionService
		hardwareEnergyPostExecutionService = new HardwareEnergyPostExecutionService(telemetryManager);
		hardwareEnergyPostExecutionService.run();

		// Check the computed and collected power metric
		final NumberMetric power = physicalDiskMonitor.getMetric(PHYSICAL_DISK_POWER_METRIC, NumberMetric.class);
		assertNull(power);
	}

	// ---------------------------------------------------------------------------------------------
	// Issue #1357: measured component power must not be overwritten, rescaled, removed or counted twice
	// ---------------------------------------------------------------------------------------------

	private static final String GPU = KnownMonitorType.GPU.getKey();
	private static final String OTHER_DEVICE = KnownMonitorType.OTHER_DEVICE.getKey();
	private static final String ENCLOSURE = KnownMonitorType.ENCLOSURE.getKey();
	private static final String CONNECTOR = KnownMonitorType.CONNECTOR.getKey();
	private static final String GPU_POWER_METRIC = HwCollectHelper.generatePowerMetricNameForMonitorType(GPU);
	private static final String OTHER_DEVICE_POWER_METRIC = HwCollectHelper.generatePowerMetricNameForMonitorType(
		OTHER_DEVICE
	);

	/**
	 * Build a hardware monitor which can be referenced as a parent through its "id" attribute.
	 */
	private static Monitor buildMonitorWithIdAttribute(final String monitorType, final String id) {
		final Monitor monitor = buildMonitor(monitorType, id);
		monitor.addAttribute(MONITOR_ATTRIBUTE_ID, id);
		return monitor;
	}

	/**
	 * Attach the given child to the given parent through the hw.parent.type and hw.parent.id attributes.
	 */
	private static void setParent(final Monitor child, final Monitor parent) {
		child.addAttribute("hw.parent.type", parent.getType());
		child.addAttribute("hw.parent.id", parent.getAttribute(MONITOR_ATTRIBUTE_ID));
	}

	/**
	 * Declare the TestConnector as measuring the server power and add the corresponding connector, host and
	 * enclosure monitors.
	 *
	 * @return the host monitor
	 */
	private Monitor setUpMeasuredServer(final MetricFactory metricFactory, final Double enclosurePower) {
		final Connector connector = telemetryManager.getConnectorStore().getStore().get(TEST_CONNECTOR);
		connector.getConnectorIdentity().setCompiledFilename(TEST_CONNECTOR);
		connector.setPowerMeasurement(PowerMeasurement.MEASURED);

		final Monitor connectorMonitor = buildMonitorWithIdAttribute(CONNECTOR, TEST_CONNECTOR);
		telemetryManager.addNewMonitor(connectorMonitor, CONNECTOR, TEST_CONNECTOR);

		final Monitor host = buildMonitor(HOST, HOST);
		host.setAsEndpoint();
		telemetryManager.addNewMonitor(host, HOST, HOST);

		final Monitor enclosure = buildMonitor(ENCLOSURE, ENCLOSURE);
		if (enclosurePower != null) {
			metricFactory.collectNumberMetric(enclosure, HW_ENCLOSURE_POWER, enclosurePower, STRATEGY_TIME);
		}
		telemetryManager.addNewMonitor(enclosure, ENCLOSURE, ENCLOSURE);
		return host;
	}

	private MetricFactory newMetricFactory() {
		return new MetricFactory(telemetryManager.getHostname(), telemetryManager.getConnectorStore());
	}

	@Test
	void testRunDoesNotOverwriteConnectorMeasuredFanAndCpuPower() {
		final MetricFactory metricFactory = newMetricFactory();

		// Fan whose power is collected by the connector (e.g. PaloAltoFirewall)
		final Monitor fan = buildMonitor(FAN, "fan1");
		metricFactory.collectNumberMetric(fan, FAN_SPEED_METRIC, 5000.0, STRATEGY_TIME);
		metricFactory.collectNumberMetric(fan, FAN_POWER_METRIC, 7.5, STRATEGY_TIME);
		telemetryManager.addNewMonitor(fan, FAN, "fan1");

		// CPU whose power is collected by the connector (e.g. LibreHardwareMonitor)
		final Monitor cpu = buildMonitor(KnownMonitorType.CPU.getKey(), "cpu1");
		metricFactory.collectNumberMetric(cpu, CPU_POWER_METRIC, 42.0, STRATEGY_TIME);
		telemetryManager.addNewMonitor(cpu, KnownMonitorType.CPU.getKey(), "cpu1");

		// Fan without measured power: it must still be estimated (5000 RPM => 5 W)
		final Monitor estimatedFan = buildMonitor(FAN, "fan2");
		metricFactory.collectNumberMetric(estimatedFan, FAN_SPEED_METRIC, 5000.0, STRATEGY_TIME);
		telemetryManager.addNewMonitor(estimatedFan, FAN, "fan2");

		new HardwareEnergyPostExecutionService(telemetryManager).run();

		assertEquals(7.5, CollectHelper.getNumberMetricValue(fan, FAN_POWER_METRIC, false));
		assertEquals(42.0, CollectHelper.getNumberMetricValue(cpu, CPU_POWER_METRIC, false));
		assertEquals(5.0, CollectHelper.getNumberMetricValue(estimatedFan, FAN_POWER_METRIC, false));
	}

	@Test
	void testRunMeasuredServerScalesOnlyEstimatedPower() {
		final MetricFactory metricFactory = newMetricFactory();
		final Monitor host = setUpMeasuredServer(metricFactory, 120.0);

		// GPU measured by the connector (e.g. NvidiaSmi): 100 W
		final Monitor gpu = buildMonitor(GPU, "gpu1");
		metricFactory.collectNumberMetric(gpu, GPU_POWER_METRIC, 100.0, STRATEGY_TIME);
		telemetryManager.addNewMonitor(gpu, GPU, "gpu1");

		// Two estimated fans: 3 W and 1 W
		final Monitor fan1 = buildMonitor(FAN, "fan1");
		metricFactory.collectNumberMetric(fan1, FAN_SPEED_METRIC, 3000.0, STRATEGY_TIME);
		telemetryManager.addNewMonitor(fan1, FAN, "fan1");
		final Monitor fan2 = buildMonitor(FAN, "fan2");
		metricFactory.collectNumberMetric(fan2, FAN_SPEED_METRIC, 1000.0, STRATEGY_TIME);
		telemetryManager.addNewMonitor(fan2, FAN, "fan2");

		new HardwareEnergyPostExecutionService(telemetryManager).run();

		// The measured GPU is not rescaled
		assertEquals(100.0, CollectHelper.getNumberMetricValue(gpu, GPU_POWER_METRIC, false));

		// The fans share the remaining 120 - 100 = 20 W proportionally to their estimates (3/4 and 1/4)
		assertEquals(15.0, CollectHelper.getNumberMetricValue(fan1, FAN_POWER_METRIC, false));
		assertEquals(5.0, CollectHelper.getNumberMetricValue(fan2, FAN_POWER_METRIC, false));

		// Components add up to the measured total
		assertEquals(120.0, CollectHelper.getNumberMetricValue(host, HW_HOST_MEASURED_POWER, false));
	}

	@Test
	void testRunMeasuredServerWithoutTotalRemovesOnlyEstimatedPower() {
		final MetricFactory metricFactory = newMetricFactory();
		// No server total collected in this cycle
		setUpMeasuredServer(metricFactory, null);

		final Monitor gpu = buildMonitor(GPU, "gpu1");
		metricFactory.collectNumberMetric(gpu, GPU_POWER_METRIC, 100.0, STRATEGY_TIME);
		telemetryManager.addNewMonitor(gpu, GPU, "gpu1");

		final Monitor fan = buildMonitor(FAN, "fan1");
		metricFactory.collectNumberMetric(fan, FAN_SPEED_METRIC, 3000.0, STRATEGY_TIME);
		telemetryManager.addNewMonitor(fan, FAN, "fan1");

		new HardwareEnergyPostExecutionService(telemetryManager).run();

		// The measured GPU power is kept
		assertEquals(100.0, CollectHelper.getNumberMetricValue(gpu, GPU_POWER_METRIC, false));

		// The estimated fan power is removed until the server total is available
		assertNull(fan.getMetric(FAN_POWER_METRIC, NumberMetric.class));
		assertNull(fan.getMetric(FAN_ENERGY_METRIC, NumberMetric.class));
	}

	@Test
	void testRunMeasuredComponentsExceedingTotalSetEstimatesToZero() {
		final MetricFactory metricFactory = newMetricFactory();
		setUpMeasuredServer(metricFactory, 80.0);

		final Monitor gpu = buildMonitor(GPU, "gpu1");
		metricFactory.collectNumberMetric(gpu, GPU_POWER_METRIC, 100.0, STRATEGY_TIME);
		telemetryManager.addNewMonitor(gpu, GPU, "gpu1");

		final Monitor fan = buildMonitor(FAN, "fan1");
		metricFactory.collectNumberMetric(fan, FAN_SPEED_METRIC, 3000.0, STRATEGY_TIME);
		telemetryManager.addNewMonitor(fan, FAN, "fan1");

		new HardwareEnergyPostExecutionService(telemetryManager).run();

		assertEquals(100.0, CollectHelper.getNumberMetricValue(gpu, GPU_POWER_METRIC, false));
		assertEquals(0.0, CollectHelper.getNumberMetricValue(fan, FAN_POWER_METRIC, false));
	}

	@Test
	void testRunNoEstimateForPortsOfSwitchReportingItsOwnPower() {
		final MetricFactory metricFactory = newMetricFactory();

		final Monitor host = buildMonitor(HOST, HOST);
		host.setAsEndpoint();
		telemetryManager.addNewMonitor(host, HOST, HOST);

		// NVSwitch reporting its power rails: 50 W
		final Monitor nvSwitch = buildMonitorWithIdAttribute(OTHER_DEVICE, "nvswitch0");
		metricFactory.collectNumberMetric(nvSwitch, OTHER_DEVICE_POWER_METRIC, 50.0, STRATEGY_TIME);
		telemetryManager.addNewMonitor(nvSwitch, OTHER_DEVICE, "nvswitch0");

		// NVSwitch port: must not be estimated
		final Monitor port = buildMonitorWithIdAttribute(NETWORK, "nvswitch0_port0");
		setParent(port, nvSwitch);
		telemetryManager.addNewMonitor(port, NETWORK, "nvswitch0_port0");

		// NVSwitch port holding a stale estimate from a previous cycle: must not be counted in the host total
		final Monitor stalePort = buildMonitorWithIdAttribute(NETWORK, "nvswitch0_port1");
		setParent(stalePort, nvSwitch);
		metricFactory.collectNumberMetric(stalePort, NETWORK_POWER_METRIC, 20.0, STRATEGY_TIME - 120 * 1000).save();
		telemetryManager.addNewMonitor(stalePort, NETWORK, "nvswitch0_port1");

		// Regular network card without parent: still estimated (no link speed => 10 W)
		final Monitor nic = buildMonitorWithIdAttribute(NETWORK, "eth0");
		telemetryManager.addNewMonitor(nic, NETWORK, "eth0");

		new HardwareEnergyPostExecutionService(telemetryManager).run();

		assertNull(port.getMetric(NETWORK_POWER_METRIC, NumberMetric.class));
		assertEquals(10.0, CollectHelper.getNumberMetricValue(nic, NETWORK_POWER_METRIC, false));
		assertEquals(50.0, CollectHelper.getNumberMetricValue(nvSwitch, OTHER_DEVICE_POWER_METRIC, false));

		// The host total counts the switch once: (50 + 10) / 0.9
		assertEquals(66.67, CollectHelper.getNumberMetricValue(host, HW_HOST_ESTIMATED_POWER, false));
	}

	@Test
	void testRunEstimatedParentDoesNotPreventChildEstimation() {
		final MetricFactory metricFactory = newMetricFactory();

		final Monitor host = buildMonitor(HOST, HOST);
		host.setAsEndpoint();
		telemetryManager.addNewMonitor(host, HOST, HOST);

		// Disk controller without measured power (estimated at 15 W)
		final Monitor controller = buildMonitorWithIdAttribute(DISK_CONTROLLER, "ctrl0");
		telemetryManager.addNewMonitor(controller, DISK_CONTROLLER, "ctrl0");

		// Physical disk attached to the controller (estimated at 11 W, default SATA 7200 RPM)
		final Monitor disk = buildMonitorWithIdAttribute(PHYSICAL_DISK, "disk0");
		setParent(disk, controller);
		telemetryManager.addNewMonitor(disk, PHYSICAL_DISK, "disk0");

		new HardwareEnergyPostExecutionService(telemetryManager).run();

		assertEquals(15.0, CollectHelper.getNumberMetricValue(controller, DISK_CONTROLLER_POWER_METRIC, false));
		assertEquals(11.0, CollectHelper.getNumberMetricValue(disk, PHYSICAL_DISK_POWER_METRIC, false));

		// Both are counted: (15 + 11) / 0.9
		assertEquals(28.89, CollectHelper.getNumberMetricValue(host, HW_HOST_ESTIMATED_POWER, false));
	}

	@Test
	void testRunConnectorEnergyOnlyIsNotOverwritten() {
		final MetricFactory metricFactory = newMetricFactory();

		// Fan whose energy counter only is collected by the connector
		final Monitor fan = buildMonitor(FAN, "fan1");
		metricFactory.collectNumberMetric(fan, FAN_SPEED_METRIC, 5000.0, STRATEGY_TIME);
		metricFactory.collectNumberMetric(fan, FAN_ENERGY_METRIC, 123456.0, STRATEGY_TIME);
		telemetryManager.addNewMonitor(fan, FAN, "fan1");

		new HardwareEnergyPostExecutionService(telemetryManager).run();

		assertEquals(123456.0, CollectHelper.getNumberMetricValue(fan, FAN_ENERGY_METRIC, false));
		assertNull(fan.getMetric(FAN_POWER_METRIC, NumberMetric.class));
	}

	@Test
	void testRunGpuWithoutValueThisCycleIsNeitherRescaledNorRemoved() {
		final MetricFactory metricFactory = newMetricFactory();

		// Scenario 1: server total available. The GPU value comes from the previous cycle (saved): it is not estimated
		{
			setUpMeasuredServer(metricFactory, 120.0);
			final Monitor gpu = buildMonitor(GPU, "gpu1");
			metricFactory.collectNumberMetric(gpu, GPU_POWER_METRIC, 100.0, STRATEGY_TIME - 120 * 1000).save();
			telemetryManager.addNewMonitor(gpu, GPU, "gpu1");
			final Monitor fan = buildMonitor(FAN, "fan1");
			metricFactory.collectNumberMetric(fan, FAN_SPEED_METRIC, 3000.0, STRATEGY_TIME);
			telemetryManager.addNewMonitor(fan, FAN, "fan1");

			new HardwareEnergyPostExecutionService(telemetryManager).run();

			assertEquals(100.0, CollectHelper.getNumberMetricValue(gpu, GPU_POWER_METRIC, false));
			assertEquals(20.0, CollectHelper.getNumberMetricValue(fan, FAN_POWER_METRIC, false));
		}

		// Scenario 2: server total missing. Only the fan estimate is removed
		{
			init();
			setUpMeasuredServer(metricFactory, null);
			final Monitor gpu = buildMonitor(GPU, "gpu1");
			metricFactory.collectNumberMetric(gpu, GPU_POWER_METRIC, 100.0, STRATEGY_TIME - 120 * 1000).save();
			telemetryManager.addNewMonitor(gpu, GPU, "gpu1");
			final Monitor fan = buildMonitor(FAN, "fan1");
			metricFactory.collectNumberMetric(fan, FAN_SPEED_METRIC, 3000.0, STRATEGY_TIME);
			telemetryManager.addNewMonitor(fan, FAN, "fan1");

			new HardwareEnergyPostExecutionService(telemetryManager).run();

			assertEquals(100.0, CollectHelper.getNumberMetricValue(gpu, GPU_POWER_METRIC, false));
			assertNull(fan.getMetric(FAN_POWER_METRIC, NumberMetric.class));
		}
	}
}
