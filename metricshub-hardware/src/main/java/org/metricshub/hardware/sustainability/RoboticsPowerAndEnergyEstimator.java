package org.metricshub.hardware.sustainability;

import static org.metricshub.hardware.util.HwConstants.HW_ENERGY_ROBOTICS_METRIC;
import static org.metricshub.hardware.util.HwConstants.HW_POWER_ROBOTICS_METRIC;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.metricshub.hardware.util.HwCollectHelper;
import org.sentrysoftware.metricshub.engine.strategy.utils.CollectHelper;
import org.sentrysoftware.metricshub.engine.telemetry.Monitor;
import org.sentrysoftware.metricshub.engine.telemetry.TelemetryManager;

@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class RoboticsPowerAndEnergyEstimator extends HardwarePowerAndEnergyEstimator {

	public RoboticsPowerAndEnergyEstimator(final Monitor monitor, final TelemetryManager telemetryManager) {
		super(monitor, telemetryManager);
	}

	/**
	 * Calculate the approximate power consumption of the media changer.<br>
	 * If it moved, 154W, if not, 48W Source:
	 * https://docs.oracle.com/en/storage/tape-storage/sl4000/slklg/calculate-total-power-consumption.html
	 *
	 * @return Double value.
	 */
	@Override
	protected Double doPowerEstimation() {
		final Double moveCount = CollectHelper.getNumberMetricValue(monitor, "hw.robotics.moves", false);
		if (moveCount != null && moveCount > 0.0) {
			return 154.0;
		}

		return 48.0;
	}

	/**
	 * Estimates the energy consumption of Robotics monitor
	 *
	 * @return Double value.
	 */
	@Override
	public Double estimateEnergy() {
		return HwCollectHelper.estimateEnergyUsingPower(
			monitor,
			telemetryManager,
			estimatedPower,
			HW_POWER_ROBOTICS_METRIC,
			HW_ENERGY_ROBOTICS_METRIC,
			telemetryManager.getStrategyTime()
		);
	}
}
