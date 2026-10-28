package org.metricshub.extension.ipmi;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Collections;
import org.junit.jupiter.api.Test;
import org.metricshub.ipmi.client.ChassisStatus;
import org.metricshub.ipmi.client.IpmiClientResults;
import org.metricshub.ipmi.core.coding.commands.sdr.GetSensorReadingResponseData;
import org.metricshub.ipmi.core.coding.commands.sdr.record.EntityId;
import org.metricshub.ipmi.core.coding.commands.sdr.record.FullSensorRecord;
import org.metricshub.ipmi.core.coding.commands.sdr.record.SensorRecord;
import org.metricshub.ipmi.core.coding.commands.sdr.record.SensorUnit;

/**
 * The reading rows of {@link IpmiResultConverter#convertResult(ChassisStatus, java.util.List, java.util.List)}: when
 * a reading is
 * reported and which thresholds go with it.
 */
class IpmiResultConverterReadingTest {

	private static FullSensorRecord fan(double lowerCritical, double lowerNonCritical) {
		// A real threshold record, so that it has reading factors: the front panel temperature with B = 0 (M = 1)
		byte[] data = IpmiResultConverterTest.FRONT_PANEL_FULL_RECORD.clone();
		data[26] = 0;
		data[27] = 0;
		FullSensorRecord record = (FullSensorRecord) SensorRecord.populateSensorRecord(data);
		record.setId(1);
		record.setEntityId(EntityId.SystemBoard);
		record.setEntityInstanceNumber((byte) 1);
		record.setName("Fan 1");
		record.setSensorBaseUnit(SensorUnit.Rpm);
		record.setLowerCriticalThreshold(lowerCritical);
		record.setLowerNonCriticalThreshold(lowerNonCritical);
		return record;
	}

	private static GetSensorReadingResponseData reading(boolean available, boolean scanned) {
		GetSensorReadingResponseData data = new GetSensorReadingResponseData();
		data.setSensorReading((byte) 0);
		data.setSensorStateValid(available);
		data.setScanningEnabled(scanned);
		return data;
	}

	private static String convert(FullSensorRecord record, GetSensorReadingResponseData data) {
		return IpmiResultConverter.convertResult(
			null,
			Collections.emptyList(),
			Collections.singletonList(IpmiClientResults.sensor(record, data))
		);
	}

	@Test
	void zeroThresholdIsAThreshold() {
		assertEquals("Fan;0001;Fan 1;System Board 1;0.0;0;", convert(fan(0.0, Double.NaN), reading(true, true)));
	}

	@Test
	void undefinedThresholdIsLeftEmpty() {
		assertEquals("Fan;0001;Fan 1;System Board 1;0.0;;", convert(fan(Double.NaN, Double.NaN), reading(true, true)));
	}

	@Test
	void unavailableOrUnscannedReadingIsNotReported() {
		assertEquals("", convert(fan(0.0, 0.0), reading(false, true)));
		assertEquals("", convert(fan(0.0, 0.0), reading(true, false)));
	}
}
