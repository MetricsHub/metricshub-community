package org.metricshub.hardware.sustainability;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * MetricsHub Hardware Energy and Sustainability Module
 * ჻჻჻჻჻჻
 * Copyright 2023 - 2025 MetricsHub
 * ჻჻჻჻჻჻
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 * ╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱
 */

import static org.metricshub.hardware.util.HwConstants.HW_CPU_SPEED_LIMIT_LIMIT_TYPE_MAX;
import static org.metricshub.hardware.util.HwConstants.HW_ENERGY_CPU_METRIC;
import static org.metricshub.hardware.util.HwConstants.HW_HOST_CPU_THERMAL_DISSIPATION_RATE;
import static org.metricshub.hardware.util.HwConstants.HW_POWER_CPU_METRIC;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.metricshub.engine.strategy.utils.CollectHelper;
import org.metricshub.engine.telemetry.Monitor;
import org.metricshub.engine.telemetry.TelemetryManager;
import org.metricshub.hardware.util.HwCollectHelper;

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
