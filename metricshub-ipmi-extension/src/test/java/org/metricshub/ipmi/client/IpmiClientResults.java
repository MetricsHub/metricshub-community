package org.metricshub.ipmi.client;

import java.util.Collections;
import java.util.List;
import org.metricshub.ipmi.core.coding.commands.chassis.GetChassisStatusResponseData;
import org.metricshub.ipmi.core.coding.commands.fru.record.FruRecord;
import org.metricshub.ipmi.core.coding.commands.sdr.GetSensorReadingResponseData;
import org.metricshub.ipmi.core.coding.commands.sdr.record.AbstractSensorRecord;
import org.metricshub.ipmi.core.coding.commands.sdr.record.FruDeviceLocatorRecord;

/**
 * Builds the ipmi-java results from raw records, for the tests of the IPMI extension: their constructors are
 * package-private, as only the {@link IpmiClient} creates them, hence this class in the package of the library.
 */
public final class IpmiClientResults {

	private IpmiClientResults() {}

	/**
	 * @param record the SDR record of the sensor
	 * @param reading its Get Sensor Reading response
	 * @return a {@link Sensor} without problem
	 */
	public static Sensor sensor(final AbstractSensorRecord record, final GetSensorReadingResponseData reading) {
		return Sensor.of(record, reading, null, null);
	}

	/**
	 * @param locator the FRU Device Locator record
	 * @param records the decoded FRU areas
	 * @return a complete {@link Fru}
	 */
	public static Fru fru(final FruDeviceLocatorRecord locator, final List<FruRecord> records) {
		return new Fru(locator, records, Collections.emptyList());
	}

	/**
	 * @param frus the FRUs
	 * @return a {@link FruList} without skipped record
	 */
	public static FruList fruList(final List<Fru> frus) {
		return new FruList(frus, Collections.emptyList());
	}

	/**
	 * @param sensors the sensors
	 * @param skippedReason the reason of one skipped SDR record (ID 0042h, type C0h)
	 * @return a {@link SensorList}
	 */
	public static SensorList sensorList(final List<Sensor> sensors, final String skippedReason) {
		return new SensorList(sensors, Collections.singletonList(new SkippedRecord(0x42, 0xC0, skippedReason)));
	}

	/**
	 * @param data the Get Chassis Status response
	 * @return the {@link ChassisStatus}
	 */
	public static ChassisStatus chassisStatus(final GetChassisStatusResponseData data) {
		return new ChassisStatus(data);
	}
}
