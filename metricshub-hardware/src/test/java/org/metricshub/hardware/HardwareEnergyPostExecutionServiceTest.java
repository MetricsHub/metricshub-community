package org.metricshub.hardware;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.metricshub.engine.common.helpers.MetricsHubConstants.MONITOR_ATTRIBUTE_CONNECTOR_ID;
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
import static org.metricshub.hardware.constants.CommonConstants.PRESENT_STATUS;
import static org.metricshub.hardware.constants.CpuConstants.HW_CPU_SPEED_LIMIT_LIMIT_TYPE_MAX;
import static org.metricshub.hardware.constants.CpuConstants.HW_ENERGY_CPU_METRIC;
import static org.metricshub.hardware.constants.CpuConstants.HW_HOST_CPU_THERMAL_DISSIPATION_RATE;
import static org.metricshub.hardware.constants.CpuConstants.HW_POWER_CPU_METRIC;
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
			ConnectorIdentity
				.builder()
				.detection(Detection.builder().appliesTo(Set.of(DeviceKind.OOB)).tags(Set.of("hardware")).build())
				.build()
		);
		connectorStore.setStore(new HashMap<>(Map.of(TEST_CONNECTOR, connector)));
		telemetryManager =
			TelemetryManager
				.builder()
				.hostConfiguration(HostConfiguration.builder().hostname(LOCALHOST).build())
				.strategyTime(STRATEGY_TIME)
				.connectorStore(connectorStore)
				.build();

		// Set the status ok in the host properties
		final ConnectorNamespace connectorNamespace = ConnectorNamespace.builder().isStatusOk(true).build();
		final HostProperties hostProperties = HostProperties
			.builder()
			.connectorNamespaces(new HashMap<>(Map.of("TestConnector", connectorNamespace)))
			.build();
		telemetryManager.setHostProperties(hostProperties);
	}

	@Test
	void testRunWithFanMonitor() {
		// Create a fan monitor
		final Monitor fanMonitor = Monitor
			.builder()
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
		final Monitor fanMonitor = Monitor
			.builder()
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
		final Monitor roboticsMonitor = Monitor
			.builder()
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
		final Monitor tapeDriveMonitor = Monitor
			.builder()
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
		final Monitor diskControllerMonitor = Monitor
			.builder()
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
		final Monitor memoryMonitor = Monitor
			.builder()
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
		final Monitor physicalDiskMonitor = Monitor
			.builder()
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
		final Monitor networkMonitor = Monitor
			.builder()
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
		final Monitor host = Monitor
			.builder()
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

		final Monitor enclosure = Monitor
			.builder()
			.id(KnownMonitorType.ENCLOSURE.getKey())
			.type(KnownMonitorType.ENCLOSURE.getKey())
			.attributes(new HashMap<>(Map.of(MONITOR_ATTRIBUTE_CONNECTOR_ID, "TestConnector")))
			.build();

		final Monitor cpu = Monitor
			.builder()
			.id("cpu1")
			.type(KnownMonitorType.CPU.getKey())
			.attributes(new HashMap<>(Map.of(MONITOR_ATTRIBUTE_CONNECTOR_ID, "TestConnector")))
			.build();
		metricFactory.collectNumberMetric(cpu, HW_CPU_POWER, 60.0, telemetryManager.getStrategyTime());
		metricFactory.collectNumberMetric(
			cpu,
			String.format(PRESENT_STATUS, cpu.getType()),
			0.0,
			telemetryManager.getStrategyTime()
		);

		final Monitor memory = Monitor
			.builder()
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

		metricFactory.collectNumberMetric(memory, HW_MEMORY_POWER, 4.0, telemetryManager.getStrategyTime());

		final Monitor disk = Monitor
			.builder()
			.id("disk_nvm_1")
			.type(KnownMonitorType.PHYSICAL_DISK.getKey())
			.attributes(new HashMap<>(Map.of(MONITOR_ATTRIBUTE_CONNECTOR_ID, "TestConnector")))
			.build();

		metricFactory.collectNumberMetric(disk, HW_PHYSICAL_DISK_POWER, 6.0, telemetryManager.getStrategyTime());

		final Monitor diskNoPower = Monitor
			.builder()
			.id("disk_noPower")
			.type(KnownMonitorType.PHYSICAL_DISK.getKey())
			.attributes(new HashMap<>(Map.of(MONITOR_ATTRIBUTE_CONNECTOR_ID, "TestConnector")))
			.build();

		final Monitor missingDisk = Monitor
			.builder()
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
		final Monitor host = Monitor
			.builder()
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

		final Monitor enclosure = Monitor
			.builder()
			.id(KnownMonitorType.ENCLOSURE.getKey())
			.type(KnownMonitorType.ENCLOSURE.getKey())
			.attributes(new HashMap<>(Map.of(MONITOR_ATTRIBUTE_CONNECTOR_ID, "TestConnector")))
			.build();

		final Monitor cpu = Monitor
			.builder()
			.id("cpu1")
			.type(KnownMonitorType.CPU.getKey())
			.attributes(new HashMap<>(Map.of(MONITOR_ATTRIBUTE_CONNECTOR_ID, "TestConnector")))
			.build();
		metricFactory.collectNumberMetric(cpu, HW_CPU_POWER, 60.0, telemetryManager.getStrategyTime());

		final Monitor memory = Monitor
			.builder()
			.id("memory1")
			.type(KnownMonitorType.MEMORY.getKey())
			.attributes(new HashMap<>(Map.of(MONITOR_ATTRIBUTE_CONNECTOR_ID, "TestConnector")))
			.build();

		metricFactory.collectNumberMetric(memory, HW_MEMORY_POWER, 4.0, telemetryManager.getStrategyTime());

		final Monitor disk = Monitor
			.builder()
			.id("disk_nvm_1")
			.type(KnownMonitorType.PHYSICAL_DISK.getKey())
			.attributes(new HashMap<>(Map.of(MONITOR_ATTRIBUTE_CONNECTOR_ID, "TestConnector")))
			.build();

		metricFactory.collectNumberMetric(disk, HW_PHYSICAL_DISK_POWER, 6.0, telemetryManager.getStrategyTime());

		final Monitor diskNoPower = Monitor
			.builder()
			.id("disk_noPower")
			.type(KnownMonitorType.PHYSICAL_DISK.getKey())
			.attributes(new HashMap<>(Map.of(MONITOR_ATTRIBUTE_CONNECTOR_ID, "TestConnector")))
			.build();

		final Monitor missingDisk = Monitor
			.builder()
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
		return Monitor
			.builder()
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

		previousPowerValue =
			metricFactory.collectNumberMetric(
				vmOffline2,
				HW_POWER_VM_METRIC,
				1.0,
				telemetryManager.getStrategyTime() - 120 * 1000
			);
		previousPowerValue.save();

		previousPowerValue =
			metricFactory.collectNumberMetric(
				vmOnline3,
				HW_POWER_VM_METRIC,
				2.0,
				telemetryManager.getStrategyTime() - 120 * 1000
			);
		previousPowerValue.save();

		previousPowerValue =
			metricFactory.collectNumberMetric(
				vmOnlineNoPowerShare4,
				HW_POWER_VM_METRIC,
				12.0,
				telemetryManager.getStrategyTime() - 120 * 1000
			);
		previousPowerValue.save();

		previousPowerValue =
			metricFactory.collectNumberMetric(
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

		previousPowerValue =
			metricFactory.collectNumberMetric(
				vmOnlineNoPowerShare4,
				HW_POWER_VM_METRIC,
				12.0,
				telemetryManager.getStrategyTime() - 120 * 1000
			);
		previousPowerValue.save();

		previousPowerValue =
			metricFactory.collectNumberMetric(
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
		final Monitor physicalDiskMonitor = Monitor
			.builder()
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
}
