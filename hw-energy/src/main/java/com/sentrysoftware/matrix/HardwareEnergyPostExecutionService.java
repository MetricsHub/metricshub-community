package com.sentrysoftware.matrix;

import com.sentrysoftware.matrix.common.helpers.KnownMonitorType;
import com.sentrysoftware.matrix.delegate.IPostExecutionService;
import com.sentrysoftware.matrix.sustainability.FanPowerAndEnergyEstimator;
import com.sentrysoftware.matrix.sustainability.HardwarePowerAndEnergyEstimator;
import com.sentrysoftware.matrix.sustainability.RoboticsPowerAndEnergyEstimator;
import com.sentrysoftware.matrix.telemetry.Monitor;
import com.sentrysoftware.matrix.telemetry.TelemetryManager;
import com.sentrysoftware.matrix.util.PowerAndEnergyCollectHelper;
import java.util.Map;
import java.util.function.BiFunction;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Slf4j
public class HardwareEnergyPostExecutionService implements IPostExecutionService {

	private TelemetryManager telemetryManager;

	/**
	 * Estimates and collects power and energy consumption for a given monitor type e.g: FAN, ROBOTICS, NETWORK, etc ..
	 *
	 * @param monitorType        a given monitor type {@link KnownMonitorType}
	 * @param powerMetricName    the name of the power metric of the given monitor type
	 * @param energyMetricName   the name of the energy metric of the given monitor type
	 * @param estimatorGenerator Function that generates the estimator
	 */
	private void estimateAndCollectPowerAndEnergyForMonitorType(
		final KnownMonitorType monitorType,
		final String powerMetricName,
		final String energyMetricName,
		final BiFunction<Monitor, TelemetryManager, HardwarePowerAndEnergyEstimator> estimatorGenerator
	) {
		// Find monitors having the selected monitor type
		final String monitorTypeKey = monitorType.getKey();
		final Map<String, Monitor> sameTypeMonitors = telemetryManager.findMonitorByType(monitorTypeKey);

		// If no monitors are found, log a message
		if (sameTypeMonitors == null) {
			log.info("Host {} does not contain {} monitors", telemetryManager.getHostname(), monitorTypeKey);
			return;
		}

		// For each monitor, estimate and collect power and energy consumption metrics
		sameTypeMonitors
			.values()
			.forEach(monitor ->
				PowerAndEnergyCollectHelper.collectPowerAndEnergy(
					monitor,
					powerMetricName,
					energyMetricName,
					telemetryManager,
					estimatorGenerator.apply(monitor, telemetryManager)
				)
			);
	}

	/**
	 * Runs the estimation of several metrics like power consumption,
	 * energy consumption, thermal consumption information, etc ...
	 */
	@Override
	public void run() {
		estimateAndCollectPowerAndEnergyForMonitorType(
			KnownMonitorType.FAN,
			"hw.power{hw.type=\"fan\"}",
			"hw.energy{hw.type=\"fan\"}",
			FanPowerAndEnergyEstimator::new
		);

		estimateAndCollectPowerAndEnergyForMonitorType(
			KnownMonitorType.ROBOTICS,
			"hw.power{hw.type=\"robotics\"}",
			"hw.energy{hw.type=\"robotics\"}",
			RoboticsPowerAndEnergyEstimator::new
		);
	}
}
