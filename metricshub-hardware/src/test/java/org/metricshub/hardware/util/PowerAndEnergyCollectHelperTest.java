package org.metricshub.hardware.util;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.metricshub.engine.common.helpers.KnownMonitorType;
import org.metricshub.engine.connector.model.Connector;
import org.metricshub.engine.connector.model.PowerMeasurement;
import org.metricshub.engine.telemetry.Monitor;
import org.metricshub.engine.telemetry.TelemetryManager;
import org.metricshub.hardware.constants.EnclosureConstants;

class PowerAndEnergyCollectHelperTest {

	@Test
	void testMeasuredConnectorTakesPrecedence() {
		var connector1 = new Connector();
		connector1.setPowerMeasurement(PowerMeasurement.MEASURED);
		var connector2 = new Connector();
		connector2.setPowerMeasurement(PowerMeasurement.ESTIMATED);

		var result = PowerAndEnergyCollectHelper.isPowerMeasured(List.of(connector1, connector2), new TelemetryManager());
		assertTrue(result, "MEASURED should take precedence over ESTIMATED as consequence Power is measured");
	}

	@Test
	void testConditionalWithActivePowerMetric() {
		var connector1 = new Connector();
		connector1.setPowerMeasurement(PowerMeasurement.ESTIMATED);
		var connector2 = new Connector();
		connector2.setPowerMeasurement(PowerMeasurement.CONDITIONAL);

		var monitor = new Monitor();
		monitor.getConditionalCollection().put(EnclosureConstants.HW_ENCLOSURE_POWER, "120");

		var telemetryManager = new TelemetryManager();
		telemetryManager.setMonitors(Map.of(KnownMonitorType.ENCLOSURE.getKey(), Map.of("e1", monitor)));

		var result = PowerAndEnergyCollectHelper.isPowerMeasured(List.of(connector1, connector2), telemetryManager);
		assertTrue(result, "CONDITIONAL should be considered if power metric is present as consequence Power is measured");
	}

	@Test
	void testConditionalButPowerMetricDeactivated() {
		var connector = new Connector();
		connector.setPowerMeasurement(PowerMeasurement.CONDITIONAL);

		var monitor = new Monitor();
		monitor.getConditionalCollection().put(EnclosureConstants.HW_ENCLOSURE_POWER, "");
		// "" implies deactivation in Monitor.isMetricDeactivated logic

		var telemetryManager = new TelemetryManager();
		telemetryManager.setMonitors(Map.of(KnownMonitorType.ENCLOSURE.getKey(), Map.of("e1", monitor)));

		var result = PowerAndEnergyCollectHelper.isPowerMeasured(List.of(connector), telemetryManager);
		assertFalse(
			result,
			"CONDITIONAL should not be considered if power metric is deactivated as consequence Power is not measured"
		);
	}

	@Test
	void testOnlyEstimatedNoMonitors() {
		var connector = new Connector();
		connector.setPowerMeasurement(PowerMeasurement.ESTIMATED);

		var result = PowerAndEnergyCollectHelper.isPowerMeasured(List.of(connector), new TelemetryManager());
		assertFalse(result, "Only ESTIMATED and no monitors should result in false as consequence Power is not measured");
	}

	@Test
	void testNullConnectorList() {
		var result = PowerAndEnergyCollectHelper.isPowerMeasured(null, new TelemetryManager());
		assertFalse(result, "Null connector list should result in false as consequence Power is not measured");
	}

	@Test
	void testConditionalWithActiveEnergyMetric() {
		var connector = new Connector();
		connector.setPowerMeasurement(PowerMeasurement.CONDITIONAL);

		var monitor = new Monitor();
		monitor.getConditionalCollection().put(EnclosureConstants.HW_ENCLOSURE_ENERGY, "15319239489");

		var telemetryManager = new TelemetryManager();
		telemetryManager.setMonitors(Map.of(KnownMonitorType.ENCLOSURE.getKey(), Map.of("e1", monitor)));

		var result = PowerAndEnergyCollectHelper.isPowerMeasured(List.of(connector), telemetryManager);
		assertTrue(result, "CONDITIONAL should be considered if energy metric is present as consequence Power is measured");
	}

	@Test
	void testConditionalWithNonActiveEnergyMetric() {
		var connector = new Connector();
		connector.setPowerMeasurement(PowerMeasurement.CONDITIONAL);

		var monitor = new Monitor();
		monitor.getConditionalCollection().put("some.metric", "15319239489");

		var telemetryManager = new TelemetryManager();
		telemetryManager.setMonitors(Map.of(KnownMonitorType.ENCLOSURE.getKey(), Map.of("e1", monitor)));

		var result = PowerAndEnergyCollectHelper.isPowerMeasured(List.of(connector), telemetryManager);
		assertFalse(
			result,
			"CONDITIONAL should not be considered if energy metric is not present as consequence Power is not measured"
		);
	}
}
