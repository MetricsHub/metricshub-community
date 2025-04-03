package org.metricshub.hardware.sustainability;

import static org.metricshub.hardware.util.HwConstants.HW_CPU_SPEED_LIMIT_LIMIT_TYPE_MAX;
import static org.metricshub.hardware.util.HwConstants.HW_ENERGY_CPU_METRIC;
import static org.metricshub.hardware.util.HwConstants.HW_HOST_CPU_THERMAL_DISSIPATION_RATE;
import static org.metricshub.hardware.util.HwConstants.HW_POWER_CPU_METRIC;

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
public class CpuPowerEstimator extends HardwarePowerAndEnergyEstimator {

	public CpuPowerEstimator(final Monitor monitor, final TelemetryManager telemetryManager) {
		super(monitor, telemetryManager);
	}

	/**
	 * Estimates the power consumption of the CPU
	 * @return Double
	 */
	@Override
	protected Double doPowerEstimation() {
		Double cpuSpeedLimit = CollectHelper.getNumberMetricValue(monitor, HW_CPU_SPEED_LIMIT_LIMIT_TYPE_MAX, false);
		cpuSpeedLimit = cpuSpeedLimit != null && cpuSpeedLimit > 0 ? cpuSpeedLimit : 2500000000.0;

		Double thermalDissipationRate = CollectHelper.getNumberMetricValue(
			monitor,
			HW_HOST_CPU_THERMAL_DISSIPATION_RATE,
			false
		);
		// If we didn't have a thermal dissipation rate value, then assume it's at 0.25 (25%)
		thermalDissipationRate = thermalDissipationRate != null ? thermalDissipationRate : 0.25;

		return thermalDissipationRate * (cpuSpeedLimit / 1000000000) * 19;
	}

	/**
	 * Estimates the energy consumption of the CPU
	 * @return Double
	 */
	@Override
	public Double estimateEnergy() {
		return HwCollectHelper.estimateEnergyUsingPower(
			monitor,
			telemetryManager,
			estimatedPower,
			HW_POWER_CPU_METRIC,
			HW_ENERGY_CPU_METRIC,
			telemetryManager.getStrategyTime()
		);
	}
}
