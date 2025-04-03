package org.metricshub.hardware.sustainability;

import static org.metricshub.hardware.util.HwConstants.HW_ENERGY_FAN_METRIC;
import static org.metricshub.hardware.util.HwConstants.HW_POWER_FAN_METRIC;

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
public class FanPowerAndEnergyEstimator extends HardwarePowerAndEnergyEstimator {

	public FanPowerAndEnergyEstimator(final Monitor monitor, final TelemetryManager telemetryManager) {
		super(monitor, telemetryManager);
	}

	/**
	 * Estimates the power consumption of Fan monitor
	 *
	 * @return Double value
	 */
	@Override
	protected Double doPowerEstimation() {
		// Get the metrics hw.fan.speed and hw.fan.speed_ratio
		final Double fanSpeed = CollectHelper.getNumberMetricValue(monitor, "hw.fan.speed", false);
		final Double fanSpeedRatio = CollectHelper.getNumberMetricValue(monitor, "hw.fan.speed_ratio", false);
		// Compute the power consumption based on fanSpeed value
		if (HwCollectHelper.isValidPositive(fanSpeed)) {
			// 1000 RPM = 1 Watt
			return fanSpeed / 1000.0;
		} else {
			if (HwCollectHelper.isValidRatio(fanSpeedRatio)) {
				// Approximately 5 Watt for a ratio of 1 (I.e. 5 Watt for 100%)
				return fanSpeedRatio * 5;
			}
		}

		// Approximately 5 Watt for standard fan
		return 5.0;
	}

	/**
	 * Estimates the energy consumption of Fan monitor
	 *
	 * @return Double values
	 */
	@Override
	public Double estimateEnergy() {
		return HwCollectHelper.estimateEnergyUsingPower(
			monitor,
			telemetryManager,
			estimatedPower,
			HW_POWER_FAN_METRIC,
			HW_ENERGY_FAN_METRIC,
			telemetryManager.getStrategyTime()
		);
	}
}
