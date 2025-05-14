package org.metricshub.hardware.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.metricshub.engine.common.helpers.MetricsHubConstants.WHITE_SPACE;
import static org.metricshub.hardware.constants.BatteryConstants.BATTERY_TYPE;
import static org.metricshub.hardware.constants.BladeConstants.BLADE_NAME;
import static org.metricshub.hardware.constants.CommonConstants.DEVICE_ID;
import static org.metricshub.hardware.constants.CommonConstants.DISPLAY_ID;
import static org.metricshub.hardware.constants.CommonConstants.ID_COUNT;
import static org.metricshub.hardware.constants.CommonConstants.LOCALHOST;
import static org.metricshub.hardware.constants.CommonConstants.LOCATION;
import static org.metricshub.hardware.constants.CommonConstants.MODEL;
import static org.metricshub.hardware.constants.CommonConstants.VENDOR;
import static org.metricshub.hardware.constants.CpuConstants.CPU_MAXIMUM_SPEED;
import static org.metricshub.hardware.constants.EnclosureConstants.COMPUTER;
import static org.metricshub.hardware.constants.EnclosureConstants.ENCLOSURE_TYPE;
import static org.metricshub.hardware.constants.FanConstants.FAN_SENSOR_LOCATION;
import static org.metricshub.hardware.constants.GpuConstants.GPU_MEMORY_LIMIT_METRIC;
import static org.metricshub.hardware.constants.LedConstants.COLOR;
import static org.metricshub.hardware.constants.LedConstants.NAME;
import static org.metricshub.hardware.constants.LogicalDiskConstants.LOGICAL_DISK_SIZE;
import static org.metricshub.hardware.constants.LogicalDiskConstants.RAID_LEVEL;
import static org.metricshub.hardware.constants.LunConstants.LOCAL_DEVICE_NAME;
import static org.metricshub.hardware.constants.LunConstants.REMOTE_DEVICE_NAME;
import static org.metricshub.hardware.constants.MemoryConstants.MEMORY_SIZE_METRIC;
import static org.metricshub.hardware.constants.MemoryConstants.MEMORY_TYPE;
import static org.metricshub.hardware.constants.NetworkConstants.DEVICE_TYPE;
import static org.metricshub.hardware.constants.OtherDeviceConstants.ADDITIONAL_LABEL;
import static org.metricshub.hardware.constants.PhysicalDiskConstants.PHYSICAL_DISK_SIZE_METRIC;
import static org.metricshub.hardware.constants.PowerSupplyConstants.POWER_SUPPLY_LIMIT_METRIC;
import static org.metricshub.hardware.constants.PowerSupplyConstants.POWER_SUPPLY_TYPE;
import static org.metricshub.hardware.constants.RoboticsConstants.ROBOTIC_TYPE;
import static org.metricshub.hardware.constants.TemperatureConstants.TEMPERATURE_SENSOR_LOCATION;
import static org.metricshub.hardware.util.MonitorNameBuilder.buildBatteryName;
import static org.metricshub.hardware.util.MonitorNameBuilder.buildBladeName;
import static org.metricshub.hardware.util.MonitorNameBuilder.buildCpuName;
import static org.metricshub.hardware.util.MonitorNameBuilder.buildDiskControllerName;
import static org.metricshub.hardware.util.MonitorNameBuilder.buildEnclosureName;
import static org.metricshub.hardware.util.MonitorNameBuilder.buildFanName;
import static org.metricshub.hardware.util.MonitorNameBuilder.buildGpuName;
import static org.metricshub.hardware.util.MonitorNameBuilder.buildLedName;
import static org.metricshub.hardware.util.MonitorNameBuilder.buildLogicalDiskName;
import static org.metricshub.hardware.util.MonitorNameBuilder.buildLunName;
import static org.metricshub.hardware.util.MonitorNameBuilder.buildMemoryName;
import static org.metricshub.hardware.util.MonitorNameBuilder.buildName;
import static org.metricshub.hardware.util.MonitorNameBuilder.buildNetworkCardName;
import static org.metricshub.hardware.util.MonitorNameBuilder.buildPowerSupplyName;
import static org.metricshub.hardware.util.MonitorNameBuilder.buildRoboticsName;
import static org.metricshub.hardware.util.MonitorNameBuilder.buildTapeDriveName;
import static org.metricshub.hardware.util.MonitorNameBuilder.buildTemperatureName;
import static org.metricshub.hardware.util.MonitorNameBuilder.buildVmName;
import static org.metricshub.hardware.util.MonitorNameBuilder.buildVoltageName;
import static org.metricshub.hardware.util.MonitorNameBuilder.hasMeaningfulContent;
import static org.metricshub.hardware.util.MonitorNameBuilder.joinWords;
import static org.metricshub.hardware.util.MonitorNameBuilder.trimUnwantedCharacters;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.metricshub.engine.common.helpers.KnownMonitorType;
import org.metricshub.engine.configuration.HostConfiguration;
import org.metricshub.engine.connector.model.common.DeviceKind;
import org.metricshub.engine.telemetry.Monitor;
import org.metricshub.engine.telemetry.TelemetryManager;
import org.metricshub.engine.telemetry.metric.AbstractMetric;
import org.metricshub.engine.telemetry.metric.NumberMetric;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MonitorNameBuilderTest {

	@Mock
	private TelemetryManager telemetryManager;

	@Mock
	private HostConfiguration hostConfiguration;

	@BeforeAll
	static void configureLocale() {
		Locale.setDefault(Locale.US);
	}

	@Test
	void testDetectMeaningfulContent() {
		assertTrue(hasMeaningfulContent("non-empty"));
		assertFalse(hasMeaningfulContent("    "));
		assertFalse(hasMeaningfulContent(null));
	}

	@Test
	void testTrimUnwantedCharacters() {
		final String rawName = " ,Foo(),   Bar   ";
		final String cleanedName = trimUnwantedCharacters(rawName);
		assertEquals("Foo Bar", cleanedName);
	}

	@Test
	void testJoinWordsWithCustomSeparator() {
		final String[] words = { "alpha", "", "beta", null, "gamma" };
		final String joined = joinWords(words, "|");
		assertEquals("alpha|beta|gamma", joined);
	}

	@Test
	void testJoinWordsWithDefaultSeparator() {
		final String[] words = { "one", "two", "three" };
		assertEquals("one - two - three", joinWords(words));
	}

	@Test
	void testIsLocalhost() {
		assertFalse(MonitorNameBuilder.isLocalhost(null));
		assertFalse(MonitorNameBuilder.isLocalhost(Collections.emptyMap()));
		assertFalse(MonitorNameBuilder.isLocalhost(Map.of(LOCATION, "remote")));
		assertTrue(MonitorNameBuilder.isLocalhost(Map.of(LOCATION, LOCALHOST)));
	}

	@Test
	void testHandleComputerDisplayName() {
		{
			final Monitor host = Monitor.builder().attributes(Map.of(LOCATION, "remote")).build();

			assertEquals(
				MonitorNameBuilder.WINDOWS_COMPUTER,
				MonitorNameBuilder.handleComputerDisplayName(host, DeviceKind.WINDOWS)
			);
			assertEquals(
				MonitorNameBuilder.LINUX_COMPUTER,
				MonitorNameBuilder.handleComputerDisplayName(host, DeviceKind.LINUX)
			);
			assertEquals(
				MonitorNameBuilder.HP_TRU64_UNIX_COMPUTER,
				MonitorNameBuilder.handleComputerDisplayName(host, DeviceKind.TRU64)
			);
			assertEquals(
				MonitorNameBuilder.HP_OPEN_VMS_COMPUTER,
				MonitorNameBuilder.handleComputerDisplayName(host, DeviceKind.VMS)
			);
			assertEquals(
				MonitorNameBuilder.STORAGE_ENCLOSURE,
				MonitorNameBuilder.handleComputerDisplayName(host, DeviceKind.STORAGE)
			);
			assertEquals(
				MonitorNameBuilder.NETWORK_SWITCH_ENCLOSURE,
				MonitorNameBuilder.handleComputerDisplayName(host, DeviceKind.NETWORK)
			);
		}

		{
			final Monitor host = Monitor.builder().attributes(Map.of(LOCATION, LOCALHOST)).build();

			assertEquals(
				MonitorNameBuilder.LOCALHOST_ENCLOSURE,
				MonitorNameBuilder.handleComputerDisplayName(host, DeviceKind.WINDOWS)
			);
			assertEquals(
				MonitorNameBuilder.LOCALHOST_ENCLOSURE,
				MonitorNameBuilder.handleComputerDisplayName(host, DeviceKind.LINUX)
			);
			assertEquals(
				MonitorNameBuilder.LOCALHOST_ENCLOSURE,
				MonitorNameBuilder.handleComputerDisplayName(host, DeviceKind.TRU64)
			);
			assertEquals(
				MonitorNameBuilder.LOCALHOST_ENCLOSURE,
				MonitorNameBuilder.handleComputerDisplayName(host, DeviceKind.VMS)
			);
			assertEquals(
				MonitorNameBuilder.LOCALHOST_ENCLOSURE,
				MonitorNameBuilder.handleComputerDisplayName(host, DeviceKind.STORAGE)
			);
			assertEquals(
				MonitorNameBuilder.LOCALHOST_ENCLOSURE,
				MonitorNameBuilder.handleComputerDisplayName(host, DeviceKind.NETWORK)
			);
		}
	}

	@Test
	void testPreferDisplayIdOverDeviceIdOrIdCount() {
		String resultWithDisplayId = buildName(
			"TypeX",
			"Display123",
			"DeviceXYZ",
			"1",
			Pattern.compile("Device", Pattern.CASE_INSENSITIVE),
			"Extra"
		);
		assertEquals("TypeX: Display123 (Extra)", resultWithDisplayId);

		String resultWithoutDisplayId = buildName(
			"TypeY",
			WHITE_SPACE,
			"Device12345",
			"2",
			Pattern.compile("Device", Pattern.CASE_INSENSITIVE)
		);
		assertEquals("TypeY: 12345", resultWithoutDisplayId);

		String resultTooLongDeviceId = buildName(
			"",
			WHITE_SPACE,
			"verylongdeviceid_exceeding_limit",
			"3",
			Pattern.compile("Device", Pattern.CASE_INSENSITIVE),
			"Info"
		);
		assertEquals("verylongid_exceeding_limit (Info)", resultTooLongDeviceId);
	}

	@Test
	void testBuildBatteryName() {
		final Map<String, String> batteryAttributes = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
		batteryAttributes.put(ID_COUNT, "0");
		batteryAttributes.put(DEVICE_ID, " battery,  1,1 ");
		Monitor battery = Monitor.builder().attributes(batteryAttributes).build();
		assertEquals("11", buildBatteryName(battery));

		batteryAttributes.put(ID_COUNT, "0");
		batteryAttributes.put(DEVICE_ID, " battery,  1,1 ");
		batteryAttributes.put(DISPLAY_ID, "1.1");
		batteryAttributes.put(VENDOR, "Intel");
		batteryAttributes.put(MODEL, "CMOS");
		batteryAttributes.put(BATTERY_TYPE, "System Board CMOS Battery");
		battery = Monitor.builder().attributes(batteryAttributes).build();
		assertEquals("1.1 (Intel CMOS - System Board CMOS Battery)", buildBatteryName(battery));
	}

	@Test
	void testBuildBladeName() {
		final Map<String, String> bladeAttributes = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
		bladeAttributes.put(ID_COUNT, "0");
		bladeAttributes.put(DEVICE_ID, " blade,  1,1 ");
		Monitor blade = Monitor.builder().attributes(bladeAttributes).build();
		assertEquals("11", buildBladeName(blade));

		bladeAttributes.put(DISPLAY_ID, "1.1");
		bladeAttributes.put(BLADE_NAME, "Blade 123");
		bladeAttributes.put(MODEL, "model 1");
		blade = Monitor.builder().attributes(bladeAttributes).build();
		assertEquals("1.1 (Blade 123 - model 1)", buildBladeName(blade));
	}

	@Test
	void testBuildCpuName() {
		final Map<String, String> cpuAttributes = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
		cpuAttributes.put(ID_COUNT, "0");
		cpuAttributes.put(DEVICE_ID, "CPU1.1");
		cpuAttributes.put(DISPLAY_ID, "CPU #1");
		cpuAttributes.put(VENDOR, "Intel");
		cpuAttributes.put(MODEL, "Xeon");

		final Map<String, AbstractMetric> cpuMetrics = new HashMap<>();
		NumberMetric cpuMaxSpeedMetric = NumberMetric
			.builder()
			.value(3600.0)
			.name(CPU_MAXIMUM_SPEED)
			.attributes(Map.of("limit_type", "max"))
			.collectTime(System.currentTimeMillis() - 120000)
			.build();
		cpuMetrics.put(CPU_MAXIMUM_SPEED, cpuMaxSpeedMetric);

		Monitor cpu = Monitor
			.builder()
			.attributes(cpuAttributes)
			.metrics(cpuMetrics)
			.type(KnownMonitorType.CPU.getKey())
			.build();
		assertEquals(
			"CPU #1 (Intel - Xeon - 3.60 GHz)",
			new MonitorNameBuilder(LOCALHOST).buildMonitorNameUsingType(cpu, null)
		);

		cpuAttributes.clear();
		cpuAttributes.put(ID_COUNT, "0");
		cpuAttributes.put(DEVICE_ID, "CPU1,1");
		cpuAttributes.put(VENDOR, "Intel");
		cpuMaxSpeedMetric =
			NumberMetric
				.builder()
				.value(999.0)
				.name(CPU_MAXIMUM_SPEED)
				.attributes(Map.of("limit_type", "max"))
				.collectTime(System.currentTimeMillis() - 120000)
				.build();
		cpuMetrics.put(CPU_MAXIMUM_SPEED, cpuMaxSpeedMetric);
		cpu = Monitor.builder().attributes(cpuAttributes).metrics(cpuMetrics).build();
		assertEquals("11 (Intel - 999 MHz)", buildCpuName(cpu));
	}

	@Test
	void testBuildDiskControllerName() {
		final Map<String, String> diskControllerAttributes = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
		diskControllerAttributes.put(ID_COUNT, "0");
		diskControllerAttributes.put(DEVICE_ID, "1,1");
		diskControllerAttributes.put(DISPLAY_ID, "1.1");
		diskControllerAttributes.put(VENDOR, "vendor X");
		diskControllerAttributes.put(MODEL, "model 1");
		final Monitor diskController = Monitor.builder().attributes(diskControllerAttributes).build();
		assertEquals("Disk Controller: 1.1 (vendor X model 1)", buildDiskControllerName(diskController));
	}

	@Test
	void testBuildEnclosureName() {
		// Initialize the mocks
		when(telemetryManager.getHostConfiguration()).thenReturn(hostConfiguration);
		when(hostConfiguration.getHostType()).thenReturn(DeviceKind.LINUX);

		Map<String, String> localhostAttributes = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
		localhostAttributes.put(LOCATION, LOCALHOST);
		Monitor localhostMonitor = Monitor.builder().attributes(localhostAttributes).build();
		when(telemetryManager.getEndpointHostMonitor()).thenReturn(localhostMonitor);

		// Check the generated name
		final Map<String, String> enclosureAttributes = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
		enclosureAttributes.put(DEVICE_ID, "1.1");
		enclosureAttributes.put(VENDOR, "Dell");
		enclosureAttributes.put(MODEL, "2200");
		enclosureAttributes.put(ID_COUNT, "0");
		enclosureAttributes.put(ENCLOSURE_TYPE, COMPUTER);
		Monitor enclosure = Monitor.builder().attributes(enclosureAttributes).build();
		assertEquals("Computer: Dell 2200", buildEnclosureName(telemetryManager, enclosure));

		enclosureAttributes.put(DEVICE_ID, "1.1");
		enclosureAttributes.put(DISPLAY_ID, "PowerEdge 54dsf");
		enclosureAttributes.put(VENDOR, "Dell");
		enclosureAttributes.put(MODEL, "2200 Dell");
		enclosureAttributes.put(ID_COUNT, "0");
		enclosureAttributes.put(ENCLOSURE_TYPE, COMPUTER);
		enclosure = Monitor.builder().attributes(enclosureAttributes).build();
		assertEquals("Computer: PowerEdge 54dsf (2200 Dell)", buildEnclosureName(telemetryManager, enclosure));
	}

	@Test
	void testBuildFanName() {
		final Map<String, String> fanAttributes = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
		fanAttributes.put(ID_COUNT, "0");
		fanAttributes.put(DEVICE_ID, "FAN 1.1");
		fanAttributes.put(DISPLAY_ID, "Fan 1A 1.1 XYZ");
		fanAttributes.put(FAN_SENSOR_LOCATION, "1A");
		Monitor fan = Monitor.builder().attributes(fanAttributes).build();
		assertEquals("Fan 1A 1.1 XYZ (1A)", buildFanName(fan));

		fanAttributes.clear();
		fanAttributes.put(ID_COUNT, "0");
		fanAttributes.put(DEVICE_ID, "FAN 1.1");
		fan = Monitor.builder().attributes(fanAttributes).build();
		assertEquals("1.1", buildFanName(fan));

		fanAttributes.clear();
		fanAttributes.put(ID_COUNT, "0");
		fanAttributes.put(FAN_SENSOR_LOCATION, "1A");
		fan = Monitor.builder().attributes(fanAttributes).build();
		assertEquals("0 (1A)", buildFanName(fan));
	}

	@Test
	void testBuildLedName() {
		final Map<String, String> ledAttributes = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
		ledAttributes.put(ID_COUNT, "0");
		ledAttributes.put(DEVICE_ID, "LED 1.1");
		ledAttributes.put(DISPLAY_ID, "LED #1");
		ledAttributes.put(NAME, "Network");
		ledAttributes.put(COLOR, "RED");
		Monitor led = Monitor.builder().attributes(ledAttributes).build();
		assertEquals("LED #1 (Red - Network)", buildLedName(led));

		ledAttributes.clear();
		ledAttributes.put(ID_COUNT, "0");
		ledAttributes.put(DEVICE_ID, "LED 1,1");
		ledAttributes.put(COLOR, "green");
		led = Monitor.builder().attributes(ledAttributes).build();
		assertEquals("11 (Green)", buildLedName(led));
	}

	@Test
	void testBuildLogicalDiskName() {
		final Map<String, String> logicalDiskAttributes = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
		logicalDiskAttributes.put(ID_COUNT, "0");
		logicalDiskAttributes.put(DEVICE_ID, "disk01");
		logicalDiskAttributes.put(DISPLAY_ID, "disk-01");
		logicalDiskAttributes.put(RAID_LEVEL, "5");

		final Map<String, AbstractMetric> logicalDiskMetrics = new HashMap<>();
		logicalDiskMetrics.put(LOGICAL_DISK_SIZE, NumberMetric.builder().value(1.09E13).build());

		Monitor logicalDisk = Monitor.builder().attributes(logicalDiskAttributes).metrics(logicalDiskMetrics).build();
		assertEquals("disk-01 (RAID 5 - 9.9 TB)", buildLogicalDiskName(logicalDisk));

		logicalDiskAttributes.clear();
		logicalDiskAttributes.put(ID_COUNT, "0");
		logicalDiskAttributes.put(DEVICE_ID, "disk01");
		logicalDiskAttributes.put(RAID_LEVEL, "Raid 2");
		logicalDiskMetrics.put(LOGICAL_DISK_SIZE, NumberMetric.builder().value(1073741824.0).build());
		logicalDisk = Monitor.builder().attributes(logicalDiskAttributes).metrics(logicalDiskMetrics).build();
		assertEquals("01 (Raid 2 - 1.0 GB)", buildLogicalDiskName(logicalDisk));
	}

	@Test
	void testBuildLunName() {
		Map<String, String> lunAttributes = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
		lunAttributes.put(ID_COUNT, "0");
		lunAttributes.put(DEVICE_ID, "LUN,1,1");
		Monitor lun = Monitor.builder().attributes(lunAttributes).build();
		assertEquals("11", buildLunName(lun));

		lunAttributes.clear();
		lunAttributes.put(ID_COUNT, "0");
		lunAttributes.put(DEVICE_ID, " LUN,  1 ");
		lunAttributes.put(DISPLAY_ID, "LUN 1");
		lunAttributes.put(LOCAL_DEVICE_NAME, "local 123");
		lunAttributes.put(REMOTE_DEVICE_NAME, "remote 123");
		lun = Monitor.builder().attributes(lunAttributes).build();
		assertEquals("LUN 1 (local 123 - remote 123)", buildLunName(lun));
	}

	@Test
	void testBuildMemoryName() {
		final Map<String, String> memoryAttributes = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
		memoryAttributes.put(ID_COUNT, "0");
		memoryAttributes.put(DEVICE_ID, " memory module 11 ");
		Monitor memory = Monitor.builder().attributes(memoryAttributes).build();
		assertEquals("11", buildMemoryName(memory));

		memoryAttributes.clear();
		memoryAttributes.put(ID_COUNT, "0");
		memoryAttributes.put(DEVICE_ID, "1.1");
		memoryAttributes.put(VENDOR, "Hynix Semiconductor (00AD00B300AD)");
		memoryAttributes.put(MEMORY_TYPE, "DDR4");
		final Map<String, AbstractMetric> metrics = new HashMap<>();
		metrics.put(MEMORY_SIZE_METRIC, NumberMetric.builder().value(16384.0).build());
		memory = Monitor.builder().attributes(memoryAttributes).metrics(metrics).build();
		assertEquals("1.1 (Hynix Semiconductor (00AD00B300AD) - DDR4 - 16384 MB)", buildMemoryName(memory));
	}

	@Test
	void testBuildNetworkCardName() {
		final Map<String, String> networkCardAttributes = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
		networkCardAttributes.put(ID_COUNT, "0");
		networkCardAttributes.put(DEVICE_ID, "network 11");
		Monitor network = Monitor.builder().attributes(networkCardAttributes).build();
		assertEquals("11", buildNetworkCardName(network));

		networkCardAttributes.clear();
		networkCardAttributes.put(ID_COUNT, "0");
		networkCardAttributes.put(DEVICE_ID, "0");
		networkCardAttributes.put(VENDOR, "HP Ethernet Controller Interface 10/100 base-t");
		networkCardAttributes.put(DEVICE_TYPE, "NIC");
		networkCardAttributes.put(MODEL, "1234");
		network = Monitor.builder().attributes(networkCardAttributes).build();
		assertEquals("0 (NIC - HP - 1234)", buildNetworkCardName(network));
	}

	@Test
	void testBuildOtherDeviceName() {
		{
			final Map<String, String> attributes = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
			attributes.put(ID_COUNT, "0");
			attributes.put(DEVICE_ID, "other device 11");
			attributes.put(ADDITIONAL_LABEL, "additional details");

			final Monitor monitor = Monitor.builder().attributes(attributes).build();

			assertEquals("other device 11 (additional details)", MonitorNameBuilder.buildOtherDeviceName(monitor));
		}

		{
			final Map<String, String> attributes = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
			attributes.put(ID_COUNT, "0");
			attributes.put(DEVICE_ID, "01");
			attributes.put(DEVICE_TYPE, "type C");

			final Monitor monitor = Monitor.builder().attributes(attributes).build();

			assertEquals("type C: 01", MonitorNameBuilder.buildOtherDeviceName(monitor));
		}
	}

	@Test
	void testBuildPhysicalDiskName() {
		{
			final Map<String, String> attributes = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
			attributes.put(ID_COUNT, "0");
			attributes.put(DEVICE_ID, "disk01");
			attributes.put(DISPLAY_ID, "disk-01");
			attributes.put(VENDOR, "HP");

			final Map<String, AbstractMetric> metrics = new HashMap<>();

			metrics.put(PHYSICAL_DISK_SIZE_METRIC, NumberMetric.builder().value(1E12).build());

			final Monitor monitor = Monitor.builder().attributes(attributes).metrics(metrics).build();

			assertEquals("disk-01 (HP - 1.0 TB)", MonitorNameBuilder.buildPhysicalDiskName(monitor));
		}

		{
			final Map<String, String> attributes = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
			attributes.put(ID_COUNT, "0");
			attributes.put(DEVICE_ID, "disk01");
			attributes.put(VENDOR, "HP");

			final Map<String, AbstractMetric> metrics = new HashMap<>();

			metrics.put(PHYSICAL_DISK_SIZE_METRIC, NumberMetric.builder().value(1000000000.0).build());

			final Monitor monitor = Monitor.builder().attributes(attributes).metrics(metrics).build();

			assertEquals("01 (HP - 1.0 GB)", MonitorNameBuilder.buildPhysicalDiskName(monitor));
		}
	}

	@Test
	void testBuildPowerSupplyName() {
		Map<String, String> powerSupplyAttributes = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
		powerSupplyAttributes.put(ID_COUNT, "0");
		powerSupplyAttributes.put(DEVICE_ID, "power01");
		powerSupplyAttributes.put(POWER_SUPPLY_TYPE, "Dell");

		final NumberMetric limitMetric = NumberMetric.builder().value(1000D).build();
		final Map<String, AbstractMetric> powerSupplyMetrics = Map.of(POWER_SUPPLY_LIMIT_METRIC, limitMetric);

		final Monitor powerSupply = Monitor.builder().attributes(powerSupplyAttributes).metrics(powerSupplyMetrics).build();

		// expected format: "<deviceId> ( <type> - 1000 W )"
		assertEquals("01 (Dell - 1000 W)", buildPowerSupplyName(powerSupply));
	}

	@Test
	void testBuildRoboticsName() {
		final Map<String, String> roboticsAttributes = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
		roboticsAttributes.put(ID_COUNT, "0");
		roboticsAttributes.put(DEVICE_ID, " robotics,  1,1,1 ");
		Monitor robotics = Monitor.builder().attributes(roboticsAttributes).build();
		assertEquals("111", buildRoboticsName(robotics));

		roboticsAttributes.put(DISPLAY_ID, "1.1.1");
		roboticsAttributes.put(VENDOR, "Quantum");
		roboticsAttributes.put(MODEL, "Quantum 123");
		roboticsAttributes.put(ROBOTIC_TYPE, "Tape Library");
		robotics = Monitor.builder().attributes(roboticsAttributes).build();
		assertEquals("1.1.1 (Quantum 123 - Tape Library)", buildRoboticsName(robotics));
	}

	@Test
	void testBuildTapeDriveName() {
		final Map<String, String> tapeDriveAttributes = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
		tapeDriveAttributes.put(ID_COUNT, "0");
		tapeDriveAttributes.put(DEVICE_ID, "tape drive, 01");
		Monitor tapeDrive = Monitor.builder().attributes(tapeDriveAttributes).build();
		assertEquals("01", buildTapeDriveName(tapeDrive));

		tapeDriveAttributes.clear();
		tapeDriveAttributes.put(ID_COUNT, "0");
		tapeDriveAttributes.put(DEVICE_ID, "drive 12");
		tapeDriveAttributes.put(DISPLAY_ID, "drive 12");
		tapeDriveAttributes.put(VENDOR, "Quantum");
		tapeDriveAttributes.put(MODEL, "Quantum 123");
		tapeDrive = Monitor.builder().attributes(tapeDriveAttributes).build();
		assertEquals("drive 12 (Quantum 123)", buildTapeDriveName(tapeDrive));
	}

	@Test
	void testBuildTemperatureName() {
		final Map<String, String> temperatureAttributes = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
		temperatureAttributes.put(ID_COUNT, "0");
		temperatureAttributes.put(DEVICE_ID, "temperature sensor 101");
		temperatureAttributes.put(TEMPERATURE_SENSOR_LOCATION, "fan temperature");
		final Monitor temperature = Monitor.builder().attributes(temperatureAttributes).build();
		assertEquals("101 (fan temperature)", buildTemperatureName(temperature));
	}

	@Test
	void testBuildVoltageName() {
		final Map<String, String> voltageAttributes = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
		voltageAttributes.put(ID_COUNT, "0");
		voltageAttributes.put(DEVICE_ID, "voltage 101");
		voltageAttributes.put(TEMPERATURE_SENSOR_LOCATION, "fan voltage");
		final Monitor voltage = Monitor.builder().attributes(voltageAttributes).build();
		assertEquals("101 (fan voltage)", buildVoltageName(voltage));
	}

	@Test
	void testBuildVmName() {
		final Map<String, String> vmAttributes = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
		vmAttributes.put(ID_COUNT, "0");
		vmAttributes.put(DEVICE_ID, "vm 101");
		final Monitor vmWithAttributes = Monitor.builder().attributes(vmAttributes).build();

		assertEquals("101", buildVmName(vmWithAttributes));
		vmAttributes.put(DISPLAY_ID, "         ");
		assertEquals("101", buildVmName(vmWithAttributes));
		vmAttributes.put(DISPLAY_ID, "displayId");
		assertEquals("displayId", buildVmName(vmWithAttributes));
	}

	@Test
	void testBuildGpuName() {
		final Map<String, String> gpuAttributes = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
		gpuAttributes.put(ID_COUNT, "0");
		gpuAttributes.put(DEVICE_ID, "gpu NVIDIA 1");
		gpuAttributes.put(VENDOR, "          ");
		gpuAttributes.put(MODEL, "          ");
		Monitor gpu = Monitor.builder().attributes(gpuAttributes).build();
		final String gpuName = buildGpuName(gpu);
		assertEquals("NVIDIA 1", gpuName);

		// vendor or model (or both) are part of the built name
		gpuAttributes.put(VENDOR, "NVIDIA");
		gpuAttributes.put(MODEL, "N");
		assertEquals("NVIDIA 1", MonitorNameBuilder.buildGpuName(gpu));

		// vendor is null, model is null, size is 0.0
		gpuAttributes.remove(VENDOR);
		gpuAttributes.remove(MODEL);
		assertEquals("NVIDIA 1", MonitorNameBuilder.buildGpuName(gpu));

		// vendor is null, model is null, size > 0.0
		final NumberMetric gpuMemoryLimitMetric = NumberMetric.builder().value(1024.0).build();
		final Map<String, AbstractMetric> gpuMetrics = new HashMap<>();
		gpuMetrics.put(GPU_MEMORY_LIMIT_METRIC, gpuMemoryLimitMetric);
		gpu = Monitor.builder().attributes(gpuAttributes).metrics(gpuMetrics).build();
		assertEquals("NVIDIA 1 - 1.00 GB", MonitorNameBuilder.buildGpuName(gpu));

		// size > 0.0, vendor is not null, model is not null, vendor is part of model
		gpuAttributes.put(VENDOR, "NVIDIA_VENDOR");
		gpuAttributes.put(MODEL, "NVIDIA_VENDOR_MODEL");
		assertEquals("NVIDIA 1 (NVIDIA_VENDOR_MODEL - 1.00 GB)", MonitorNameBuilder.buildGpuName(gpu));

		// size > 0.0, vendor is not null, model is not null, vendor is not part of model
		gpuAttributes.put(VENDOR, "VENDOR");
		gpuAttributes.put(MODEL, "MODEL");
		assertEquals("NVIDIA 1 (VENDOR - MODEL - 1.00 GB)", MonitorNameBuilder.buildGpuName(gpu));

		// size > 0.0, vendor is not null, model is null
		gpuAttributes.remove(MODEL);
		assertEquals("NVIDIA 1 (VENDOR - 1.00 GB)", MonitorNameBuilder.buildGpuName(gpu));

		// size > 0.0, vendor is null, model is not null
		gpuAttributes.remove(VENDOR);
		gpuAttributes.put(MODEL, "MODEL");
		assertEquals("NVIDIA 1 (MODEL - 1.00 GB)", MonitorNameBuilder.buildGpuName(gpu));
	}

	@Test
	void testBuildName() {
		assertEquals(
			"type A: display 12345",
			MonitorNameBuilder.buildName(
				"type A",
				"display 12345",
				"dev 12345",
				"0",
				Pattern.compile("dev(ice)*", Pattern.CASE_INSENSITIVE),
				""
			)
		);

		assertEquals(
			"12345",
			MonitorNameBuilder.buildName(
				"",
				WHITE_SPACE,
				"Dev DEVICE 12345",
				"0",
				Pattern.compile("dev(ice)*", Pattern.CASE_INSENSITIVE),
				""
			)
		);

		assertEquals(
			"12345678901234567890 (info)",
			MonitorNameBuilder.buildName(
				"",
				WHITE_SPACE,
				"device 12345678901234567890",
				"0",
				Pattern.compile("dev(ice)*", Pattern.CASE_INSENSITIVE),
				"info"
			)
		);

		assertEquals(
			"type Z: 0 (info)",
			MonitorNameBuilder.buildName(
				"type Z",
				WHITE_SPACE,
				WHITE_SPACE,
				"0",
				Pattern.compile("dev(ice)*", Pattern.CASE_INSENSITIVE),
				"info"
			)
		);
	}
}
