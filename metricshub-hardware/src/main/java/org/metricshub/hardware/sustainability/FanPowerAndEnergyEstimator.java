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

import static org.metricshub.hardware.util.HwConstants.HW_ENERGY_FAN_METRIC;
import static org.metricshub.hardware.util.HwConstants.HW_POWER_FAN_METRIC;

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
