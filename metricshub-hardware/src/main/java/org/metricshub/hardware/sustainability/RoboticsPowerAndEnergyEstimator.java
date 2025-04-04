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

import static org.metricshub.hardware.util.HwConstants.HW_ENERGY_ROBOTICS_METRIC;
import static org.metricshub.hardware.util.HwConstants.HW_POWER_ROBOTICS_METRIC;

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
