package org.metricshub.extension.ipmi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

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
import org.metricshub.ipmi.core.coding.commands.fru.record.ProductInfo;
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
			"System Board;1;System Board 1;IBM;System x3650 M2;KD9098C - 794722G;Base board=Device Present|Video USB=Connected\n" +
			"Battery;1;Battery 1;;;;Battery 1=Transition to OK\n" +
			"Front Panel Board;1;Front Panel Board 1;;;;Front Panel=Device Present\n" +
			"Temperature;0001;Ambient Temp;Front Panel Board 1;22.0;38;41";

		assertEquals(expected, result);
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
