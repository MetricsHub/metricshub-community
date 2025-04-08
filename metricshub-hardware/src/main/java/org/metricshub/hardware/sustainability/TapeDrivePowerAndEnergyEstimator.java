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

import static org.metricshub.engine.common.helpers.MetricsHubConstants.EMPTY;
import static org.metricshub.engine.common.helpers.MetricsHubConstants.MONITOR_ATTRIBUTE_NAME;
import static org.metricshub.hardware.util.HwConstants.HW_ENERGY_TAPE_DRIVE_METRIC;
import static org.metricshub.hardware.util.HwConstants.HW_POWER_TAPE_DRIVE_METRIC;

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
public class TapeDrivePowerAndEnergyEstimator extends HardwarePowerAndEnergyEstimator {

	public TapeDrivePowerAndEnergyEstimator(final Monitor monitor, final TelemetryManager telemetryManager) {
		super(monitor, telemetryManager);
	}

	/**
	 * Estimates the power consumption of TapeDrive monitor
	 * @return Double
	 */
	@Override
	protected Double doPowerEstimation() {
		Double mountCount = CollectHelper.getNumberMetricValue(monitor, "hw.tape_drive.operations{type=\"mount\"}", false);

		mountCount = mountCount != null ? mountCount : 0.0;

		Double unmountCount = CollectHelper.getNumberMetricValue(
			monitor,
			"hw.tape_drive.operations{type=\"unmount\"}",
			false
		);
		unmountCount = unmountCount != null ? unmountCount : 0.0;

		final boolean isActive = mountCount + unmountCount > 0;
		final String monitorName = monitor.getAttribute(MONITOR_ATTRIBUTE_NAME);
		final String lowerCaseName = monitorName == null ? EMPTY : monitorName.toLowerCase();
		return estimatePowerHelper(isActive, lowerCaseName);
	}

	/**
	 * Estimates the tape drive power consumption based on its name and its activity
	 * @param isActive      Whether the tape drive is active or not
	 * @param lowerCaseName The name of the tape drive in lower case
	 * @return double value
	 */
	double estimatePowerHelper(final boolean isActive, final String lowerCaseName) {
		if (lowerCaseName.contains("lto")) {
			return isActive ? 46 : 30;
		} else if (lowerCaseName.contains("t10000d")) {
			return isActive ? 127 : 64;
		} else if (lowerCaseName.contains("t10000")) {
			return isActive ? 93 : 61;
		} else if (lowerCaseName.contains("ts")) {
			return isActive ? 53 : 35;
		}

		return isActive ? 80 : 55;
	}

	/**
	 * Estimates the energy consumption of TapeDrive monitor
	 * @return Double
	 */
	@Override
	public Double estimateEnergy() {
		return HwCollectHelper.estimateEnergyUsingPower(
			monitor,
			telemetryManager,
			estimatedPower,
			HW_POWER_TAPE_DRIVE_METRIC,
			HW_ENERGY_TAPE_DRIVE_METRIC,
			telemetryManager.getStrategyTime()
		);
	}
}
