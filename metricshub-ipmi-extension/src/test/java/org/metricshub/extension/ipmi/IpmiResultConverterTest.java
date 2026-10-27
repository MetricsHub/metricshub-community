package org.metricshub.extension.ipmi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.metricshub.ipmi.client.ChassisStatus;
import org.metricshub.ipmi.client.Fru;
import org.metricshub.ipmi.client.IpmiClientResults;
import org.metricshub.ipmi.client.Sensor;
import org.metricshub.ipmi.core.coding.commands.chassis.GetChassisStatusResponseData;
import org.metricshub.ipmi.core.coding.commands.fru.record.FruRecord;
import org.metricshub.ipmi.core.coding.commands.fru.record.PiromInfo;
import org.metricshub.ipmi.core.coding.commands.fru.record.ProductInfo;
import org.metricshub.ipmi.core.coding.commands.fru.record.SpdInfo;
import org.metricshub.ipmi.core.coding.commands.sdr.GetSensorReadingResponseData;
import org.metricshub.ipmi.core.coding.commands.sdr.record.AbstractSensorRecord;
import org.metricshub.ipmi.core.coding.commands.sdr.record.CompactSensorRecord;
import org.metricshub.ipmi.core.coding.commands.sdr.record.EntityId;
import org.metricshub.ipmi.core.coding.commands.sdr.record.FruDeviceLocatorRecord;
import org.metricshub.ipmi.core.coding.commands.sdr.record.SensorRecord;
import org.metricshub.ipmi.core.coding.commands.sdr.record.SensorType;

public class IpmiResultConverterTest {

	public static final byte[] BASE_BOARD_PRODUCT_INFO = {
		1,
		0,
		1,
		8,
		45,
		0,
		0,
		-55,
		1,
		7,
		1,
		0,
		-34,
		75,
		68,
		57,
		48,
		57,
		56,
		67,
		0,
		0,
		0,
		0,
		0,
		0,
		0,
		0,
		0,
		0,
		0,
		0,
		0,
		0,
		0,
		0,
		0,
		0,
		0,
		0,
		0,
		0,
		0,
		16,
		102,
		82,
		-95,
		-100,
		-109,
		-120,
		57,
		41,
		-72,
		-89,
		-42,
		-49,
		55,
		-119,
		-77,
		-61,
		-63,
		0,
		0,
		-16,
		1,
		37,
		0,
		32,
		-66,
		108,
		-34,
		32,
		73,
		66,
		77,
		0,
		0,
		0,
		0,
		0,
		0,
		0,
		0,
		0,
		0,
		0,
		0,
		0,
		0,
		0,
		0,
		0,
		0,
		0,
		0,
		0,
		0,
		0,
		0,
		0,
		0,
		-32,
		83,
		121,
		115,
		116,
		101,
		109,
		32,
		66,
		111,
		97,
		114,
		100,
		0,
		0,
		0,
		0,
		0,
		0,
		0,
		0,
		0,
		0,
		0,
		0,
		0,
		0,
		0,
		0,
		0,
		0,
		0,
		0,
		-34,
		89,
		75,
		49,
		53,
		57,
		48,
		57,
		55,
		90,
		48,
		57,
		89,
		0,
		0,
		0,
		0,
		0,
		0,
		0,
		0,
		0,
		0,
		0,
		0,
		0,
		0,
		0,
		0,
		0,
		0,
		-34,
		52,
		57,
		89,
		54,
		52,
		57,
		56,
		32,
		32,
		32,
		32,
		32,
		0,
		0,
		0,
		0,
		0,
		0,
		0,
		0,
		0,
		0,
		0,
		0,
		0,
		0,
		0,
		0,
		0,
		0,
		0,
		-51,
		52,
		51,
		86,
		55,
		48,
		55,
		50,
		32,
		32,
		32,
		32,
		32,
		0,
		2,
		0,
		2,
		2,
		0,
		0,
		2,
		0,
		80,
		-34,
		0,
		0,
		0,
		0,
		0,
		0,
		0,
		0,
		0,
		0,
		0,
		0,
		0,
		0,
		0,
		0,
		0,
		0,
		0,
		0,
		0,
		0,
		0,
		0,
		0,
		0,
		0,
		0,
		0,
		0,
		5,
		1,
		6,
		60,
		1,
		0,
		48,
		0,
		33,
		94,
		-37,
		23,
		-112,
		0,
		33,
		94,
		-37,
		23,
		-110,
		0,
		0,
		0,
		0,
		0,
		0,
		0,
		0,
		0,
		0,
		0,
		0,
		0,
		0,
		0,
		0,
		0,
		0,
		0,
		0,
		0,
		0,
		0,
		0,
		0,
		0,
		0,
		0,
		0,
		0,
		0,
		0,
		0,
		0,
		0,
		0,
		16,
		102,
		82,
		-95,
		-100,
		-109,
		-120,
		57,
		41,
		-72,
		-89,
		-42,
		-49,
		55,
		-119,
		-77,
		-61,
		1,
		2,
		16,
		17,
		17,
		0,
		0,
		0,
		0,
		0,
		0,
		0,
		0,
		0,
		0,
		0,
		0,
		0,
		0,
		8,
		0,
		0,
		0,
		0,
		0,
		0,
		0,
		0,
		2,
		1,
		44,
		2,
		0,
		0,
		-63,
		0,
		-71,
		1,
		21,
		0,
		-34,
		32,
		73,
		66,
		77,
		0,
		0,
		0,
		0,
		0,
		0,
		0,
		0,
		0,
		0,
		0,
		0,
		0,
		0,
		0,
		0,
		0,
		0,
		0,
		0,
		0,
		0,
		0,
		0,
		0,
		0,
		-32,
		83,
		121,
		115,
		116,
		101,
		109,
		32,
		120,
		51,
		54,
		53,
		48,
		32,
		77,
		50,
		0,
		0,
		0,
		0,
		0,
		0,
		0,
		0,
		0,
		0,
		0,
		0,
		0,
		0,
		0,
		0,
		0,
		-34,
		55,
		57,
		52,
		55,
		50,
		50,
		71,
		0,
		0,
		0,
		0,
		0,
		0,
		0,
		0,
		0,
		0,
		0,
		0,
		0,
		0,
		0,
		0,
		0,
		0,
		0,
		0,
		0,
		0,
		0,
		0,
		-34,
		75,
		68,
		57,
		48,
		57,
		56,
		67,
		0,
		0,
		0,
		0,
		0,
		0,
		0,
		0,
		0,
		0,
		0,
		0,
		0,
		0,
		0,
		0,
		0,
		0,
		0,
		0,
		0,
		0,
		0,
		-31,
		32,
		32,
		32,
		32,
		32,
		32,
		32,
		32,
		32,
		32,
		32,
		32,
		32,
		32,
		32,
		32,
		32,
		32,
		32,
		32,
		32,
		32,
		32,
		32,
		32,
		32,
		32,
		32,
		32,
		32,
		32,
		32,
		0,
		0,
		-63,
		0,
		26
	};

	public static final byte[] FRONT_PANEL_FULL_RECORD = {
		1,
		0,
		81,
		1,
		55,
		32,
		0,
		50,
		12,
		1,
		127,
		104,
		1,
		1,
		-128,
		10,
		-128,
		122,
		56,
		0,
		0,
		1,
		0,
		0,
		1,
		0,
		124,
		-64,
		0,
		0,
		1,
		-103,
		-78,
		-120,
		-1,
		0,
		-79,
		-83,
		-86,
		0,
		0,
		0,
		4,
		0,
		0,
		0,
		0,
		-52,
		65,
		109,
		98,
		105,
		101,
		110,
		116,
		32,
		84,
		101,
		109,
		112
	};

	@Test
	void testConvertResultListOfFruListOfSensor() {
		List<Sensor> sensors = buildSensors();

		List<Fru> frus = buildSystemBoardFrus();

		String result = IpmiResultConverter.convertResult(null, frus, sensors);
		String expected =
			"FRU;IBM;System x3650 M2;KD9098C - 794722G\n" +
			"System Board;1;System Board 1;IBM;System x3650 M2;KD9098C - 794722G;Base board=Device Present|Video USB=Connected;;;;\n" +
			"Battery;1;Battery 1;;;;Battery 1=Transition to OK;;;;\n" +
			"Front Panel Board;1;Front Panel Board 1;;;;Front Panel=Device Present;;;;\n" +
			"Temperature;0001;Ambient Temp;Front Panel Board 1;22.0;38;41";

		assertEquals(expected, result);
	}

	@Test
	void testConvertResultMemoryModuleAndProcessorFrus() {
		// DIMM 1 and CPU 1, each with a presence sensor; DIMM 2 has a FRU but no sensor, CPU 2 a sensor but no FRU
		FruDeviceLocatorRecord dimm1 = new FruDeviceLocatorRecord();
		dimm1.setFruEntityId(EntityId.MemoryDevice.getCode());
		dimm1.setFruEntityInstance(1);
		FruDeviceLocatorRecord dimm2 = new FruDeviceLocatorRecord();
		dimm2.setFruEntityId(EntityId.MemoryDevice.getCode());
		dimm2.setFruEntityInstance(2);
		FruDeviceLocatorRecord cpu1 = new FruDeviceLocatorRecord();
		cpu1.setFruEntityId(EntityId.Processor.getCode());
		cpu1.setFruEntityInstance(1);
		List<Fru> frus = List.of(
			buildSystemBoardFrus().get(0),
			IpmiClientResults.fru(dimm1, List.of(new SpdInfo(DDR4_SPD, 0))),
			IpmiClientResults.fru(dimm2, List.of(new SpdInfo(DDR4_SPD, 0))),
			IpmiClientResults.fru(cpu1, List.of(new PiromInfo(PIROM, 0)))
		);

		List<Sensor> sensors = List.of(
			IpmiClientResults.sensor(presence(EntityId.MemoryDevice, 1, "DIMM 1"), states(1)),
			IpmiClientResults.sensor(presence(EntityId.Processor, 1, "CPU 1"), states(1)),
			IpmiClientResults.sensor(presence(EntityId.Processor, 2, "CPU 2"), states(1))
		);

		assertEquals(
			"FRU;IBM;System x3650 M2;KD9098C - 794722G\n" +
				"FRU;Samsung;M393A2K43BB1-CTD;409F3CA2\n" +
				"FRU;Samsung;M393A2K43BB1-CTD;409F3CA2\n" +
				"FRU;Intel;SR3GH;634AFBC3354D3C6D\n" +
				"Memory Device;1;Memory Device 1;Samsung;M393A2K43BB1-CTD;409F3CA2;DIMM 1=Device Present;16384;DDR4 RDIMM ECC;;\n" +
				"Processor;1;Processor 1;Intel;SR3GH;634AFBC3354D3C6D;CPU 1=Device Present;;;3000;" +
				"8 cores with Hyper-Threading, 2100-3000 MHz, L2 1024 KB per core, L3 11264 KB, TDP 85 W, Part number 37608DC00416530\n" +
				"Processor;2;Processor 2;;;;CPU 2=Device Present;;;;",
			IpmiResultConverter.convertResult(null, frus, sensors)
		);
	}

	@Test
	void testConvertResultUnprogrammedSpdAndPirom() {
		// SPD data without manufacturer, date, serial number and part number
		byte[] blankSpd = DDR4_SPD.clone();
		Arrays.fill(blankSpd, 320, 329, (byte) 0);
		Arrays.fill(blankSpd, 329, 349, (byte) ' ');
		// PIROM without base frequency: zero the P1 frequency of the core section, then fix the checksum
		byte[] noBase = PIROM.clone();
		int core = noBase[0x04];
		noBase[core + 4] = 0;
		noBase[core + 5] = 0;
		int sum = 0;
		for (int i = 0; i < 0x76; i++) {
			sum += noBase[i];
		}
		noBase[0x76] = (byte) -sum;

		// DIMM 1: blank SPD data only; DIMM 2: blank SPD data with a product area; CPU 1: PIROM without base frequency
		FruDeviceLocatorRecord dimm1 = new FruDeviceLocatorRecord();
		dimm1.setFruEntityId(EntityId.MemoryDevice.getCode());
		dimm1.setFruEntityInstance(1);
		FruDeviceLocatorRecord dimm2 = new FruDeviceLocatorRecord();
		dimm2.setFruEntityId(EntityId.MemoryDevice.getCode());
		dimm2.setFruEntityInstance(2);
		FruDeviceLocatorRecord cpu1 = new FruDeviceLocatorRecord();
		cpu1.setFruEntityId(EntityId.Processor.getCode());
		cpu1.setFruEntityInstance(1);
		List<Fru> frus = List.of(
			IpmiClientResults.fru(dimm1, List.of(new SpdInfo(blankSpd, 0))),
			IpmiClientResults.fru(dimm2, List.of(new ProductInfo(BASE_BOARD_PRODUCT_INFO, 360), new SpdInfo(blankSpd, 0))),
			IpmiClientResults.fru(cpu1, List.of(new PiromInfo(noBase, 0)))
		);

		List<Sensor> sensors = List.of(
			IpmiClientResults.sensor(presence(EntityId.MemoryDevice, 1, "DIMM 1"), states(1)),
			IpmiClientResults.sensor(presence(EntityId.MemoryDevice, 2, "DIMM 2"), states(1)),
			IpmiClientResults.sensor(presence(EntityId.Processor, 1, "CPU 1"), states(1))
		);

		// An anonymous FRU is not listed; the product area names the second one
		assertEquals(
			"FRU;IBM;System x3650 M2;KD9098C\n" +
				"FRU;Intel;SR3GH;634AFBC3354D3C6D\n" +
				"Memory Device;1;Memory Device 1;;;;DIMM 1=Device Present;;;;\n" +
				"Memory Device;2;Memory Device 2;IBM;System x3650 M2;KD9098C;DIMM 2=Device Present;16384;DDR4 RDIMM ECC;;\n" +
				"Processor;1;Processor 1;Intel;SR3GH;634AFBC3354D3C6D;CPU 1=Device Present;;;3000;" +
				"8 cores with Hyper-Threading, 3000 MHz, L2 1024 KB per core, L3 11264 KB, TDP 85 W, Part number 37608DC00416530",
			IpmiResultConverter.convertResult(null, frus, sensors)
		);
	}

	/**
	 * @return a presence sensor (generic event/reading type 08h) of the given entity
	 */
	private static CompactSensorRecord presence(EntityId entity, int instance, String name) {
		CompactSensorRecord record = new CompactSensorRecord();
		record.setId(1);
		record.setEntityId(entity);
		record.setEntityInstanceNumber((byte) instance);
		record.setName(name);
		record.setSensorType(SensorType.EntityPresence);
		record.setEventReadingType(0x08);
		return record;
	}

	/**
	 * The processor information ROM of an Intel Xeon Silver 4110 (S-spec SR3GH, 8 cores with Hyper-Threading, 2.1 to
	 * 3.0 GHz, 11 MB of L3, 85 W), as the FRU device of a processor of a Lenovo XCC returns it.
	 */
	private static final byte[] PIROM = bytes(
		"07 80 00 0d 19 21 2b 31 34 56 65 6c 77 20 53 52 33 47 48 00 08 01 00 01 00 54 06 00 00 00 21 00" +
			" 30 02 60 09 00 80 00 24 00 00 01 00 04 00 2c 0c 00 00 00 00 50 12 00 06 00 12 00 06 50 13 00 10" +
			" 00 10 00 10 50 13 50 13 50 13 50 13 50 13 50 13 00 00 00 00 00 00 33 37 36 30 38 44 43 30 30 34" +
			" 31 36 35 33 30 77 85 00 90 00 59 00 bf eb fb ff 0e 3f 01 09 00 00 15 6d 3c 4d 35 c3 fb 4a 63 00"
	);

	/**
	 * The SPD data of a Samsung 16 GB 2Rx8 DDR4-2666 ECC RDIMM (M393A2K43BB1-CTD), made in week 23 of 2019.
	 */
	private static final byte[] DDR4_SPD = ddr4Spd();

	private static byte[] ddr4Spd() {
		byte[] spd = new byte[512];
		spd[0] = 0x23; // 384 bytes used of 512
		spd[1] = 0x11; // SPD revision 1.1
		spd[2] = 0x0C; // DDR4
		spd[3] = 0x01; // RDIMM
		spd[4] = (byte) 0x85; // 8 Gb, 4 banks, 2 bank groups
		spd[12] = 0x09; // 2 ranks, x8
		spd[13] = 0x0B; // 64 bits + 8 bits of ECC
		// Module manufacturer: JEDEC bank 1, code CEh (Samsung); made in week 23 of 2019; serial number
		spd[320] = (byte) 0x80;
		spd[321] = (byte) 0xCE;
		spd[322] = 0x01;
		spd[323] = 0x19;
		spd[324] = 0x23;
		System.arraycopy(bytes("40 9F 3C A2"), 0, spd, 325, 4);
		// Part number, 20 characters padded with spaces, then the revision and the DRAM manufacturer
		System.arraycopy(String.format("%-20s", "M393A2K43BB1-CTD").getBytes(StandardCharsets.US_ASCII), 0, spd, 329, 20);
		spd[350] = (byte) 0x80;
		spd[351] = (byte) 0xCE;
		return spd;
	}

	private static byte[] bytes(String hex) {
		String[] parts = hex.trim().split("\\s+");
		byte[] result = new byte[parts.length];
		for (int i = 0; i < parts.length; i++) {
			result[i] = (byte) Integer.parseInt(parts[i], 16);
		}
		return result;
	}

	@Test
	void testConvertResultChassisLine() {
		GetChassisStatusResponseData data = new GetChassisStatusResponseData();
		// Powered on, power fault, restore policy 01b = PowerRestored
		data.setCurrentPowerState((byte) 0x29);
		// AC failed, IPMI command
		data.setLastPowerEvent((byte) 0x11);
		// Identify supported and off, cooling fault, intrusion
		data.setMiscChassisState((byte) 0x49);
		assertEquals(
			"Chassis;1;1;0;0;0;1;0;1;0;PowerRestored;Off;AC_FAILED|IPMI_COMMAND\n" +
				"FRU;IBM;System x3650 M2;KD9098C - 794722G",
			IpmiResultConverter.convertResult(
				IpmiClientResults.chassisStatus(data),
				buildSystemBoardFrus(),
				Collections.emptyList()
			)
		);

		// No identify support, no last power event
		data.setLastPowerEvent((byte) 0);
		data.setMiscChassisState((byte) 0);
		assertEquals(
			"Chassis;1;1;0;0;0;0;0;0;0;PowerRestored;;",
			IpmiResultConverter.convertResult(
				IpmiClientResults.chassisStatus(data),
				Collections.emptyList(),
				Collections.emptyList()
			)
		);
	}

	/**
	 * @return System Board FRU in a singleton list
	 */
	public static List<Fru> buildSystemBoardFrus() {
		FruDeviceLocatorRecord fruLocator = new FruDeviceLocatorRecord();
		fruLocator.setFruEntityId(EntityId.Baseboard.getCode());
		fruLocator.setFruEntityInstance(1);
		List<FruRecord> fruRecords = Collections.singletonList(new ProductInfo(BASE_BOARD_PRODUCT_INFO, 360));
		return Collections.singletonList(IpmiClientResults.fru(fruLocator, fruRecords));
	}

	/**
	 * @return List of Sensor records
	 */
	public static List<Sensor> buildSensors() {
		// Build the base board compact record: a presence sensor (generic event/reading type 08h)
		CompactSensorRecord compact1 = new CompactSensorRecord();
		compact1.setId(1);
		compact1.setEntityId(EntityId.SystemBoard);
		compact1.setEntityInstanceNumber((byte) 1);
		compact1.setName("Base board");
		compact1.setSensorType(SensorType.EntityPresence);
		compact1.setEventReadingType(0x08);

		// Battery: a severity sensor (generic event/reading type 07h)
		CompactSensorRecord compact2 = new CompactSensorRecord();
		compact2.setId(1);
		compact2.setEntityId(EntityId.Battery);
		compact2.setEntityInstanceNumber((byte) 1);
		compact2.setName("Battery 1");
		compact2.setSensorType(SensorType.Battery);
		compact2.setEventReadingType(0x07);

		// Video USB on system board: a cable sensor (sensor-specific event/reading type)
		CompactSensorRecord compact3 = new CompactSensorRecord();
		compact3.setId(1);
		compact3.setEntityId(EntityId.SystemBoard);
		compact3.setEntityInstanceNumber((byte) 1);
		compact3.setName("Video USB");
		compact3.setSensorType(SensorType.CableInterconnect);
		compact3.setEventReadingType(0x6f);

		// Front panel presence
		CompactSensorRecord compact4 = new CompactSensorRecord();
		compact4.setId(1);
		compact4.setEntityId(EntityId.FrontPanelBoard);
		compact4.setEntityInstanceNumber((byte) 1);
		compact4.setName("Front Panel");
		compact4.setSensorType(SensorType.EntityPresence);
		compact4.setEventReadingType(0x08);

		// Sorry...
		SensorRecord full = SensorRecord.populateSensorRecord(FRONT_PANEL_FULL_RECORD);

		GetSensorReadingResponseData data = new GetSensorReadingResponseData();
		// And sorry...
		data.setSensorReading((byte) -102);
		data.setSensorStateValid(true);
		data.setScanningEnabled(true);

		return Arrays.asList(
			IpmiClientResults.sensor(compact1, states(1)),
			IpmiClientResults.sensor(compact2, states(0)),
			IpmiClientResults.sensor(compact3, states(0)),
			IpmiClientResults.sensor(compact4, states(1)),
			IpmiClientResults.sensor((AbstractSensorRecord) full, data)
		);
	}

	/**
	 * @return a valid reading with the given state offsets asserted
	 */
	private static GetSensorReadingResponseData states(int... offsets) {
		GetSensorReadingResponseData data = new GetSensorReadingResponseData();
		data.setSensorStateValid(true);
		data.setScanningEnabled(true);
		boolean[] asserted = new boolean[16];
		for (int offset : offsets) {
			asserted[offset] = true;
		}
		data.setStatesAsserted(asserted);
		return data;
	}

	@Test
	void testConvertResultGetChassisStatusResponseData() {
		GetChassisStatusResponseData chassisStatus = buildChassisStatusResponseData(1);
		assertEquals(
			"System power state is up",
			IpmiResultConverter.convertResult(IpmiClientResults.chassisStatus(chassisStatus))
		);
		chassisStatus.setCurrentPowerState((byte) 0);
		assertEquals(
			"System power state is down",
			IpmiResultConverter.convertResult(IpmiClientResults.chassisStatus(chassisStatus))
		);
	}

	@Test
	void testConvertResultGetChassisStatusNullResponseData() {
		assertNull(IpmiResultConverter.convertResult((ChassisStatus) null));
	}

	/**
	 * @return {@link GetChassisStatusResponseData} instance wrapping the Chassis power state
	 */
	public static GetChassisStatusResponseData buildChassisStatusResponseData(int powerState) {
		GetChassisStatusResponseData chassisStatus = new GetChassisStatusResponseData();
		chassisStatus.setCurrentPowerState((byte) powerState);
		return chassisStatus;
	}
}
