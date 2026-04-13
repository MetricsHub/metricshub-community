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

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.metricshub.engine.telemetry.Monitor;
import org.metricshub.engine.telemetry.TelemetryManager;

@Data
@RequiredArgsConstructor
@NoArgsConstructor
public abstract class HardwarePowerAndEnergyEstimator {

	@NonNull
	protected Monitor monitor;

	@NonNull
	protected TelemetryManager telemetryManager;

	protected Double estimatedPower;

	/**
	 * Estimates the power consumption of a hardware monitor then set the estimatedPower field
	 *
	 * @return Double
	 */
	public Double estimatePower() {
		estimatedPower = doPowerEstimation();
		return estimatedPower;
	}

	/**
	 * Estimates the power consumption of a hardware monitor
	 *
	 * @return Double
	 */
	protected abstract Double doPowerEstimation();

	/**
	 * Estimates the energy consumption of a hardware monitor
	 * @return Double
	 */
	public abstract Double estimateEnergy();
}
