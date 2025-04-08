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

import static org.metricshub.engine.common.helpers.MetricsHubConstants.MONITOR_ATTRIBUTE_NAME;
import static org.metricshub.hardware.util.HwConstants.HW_ENERGY_PHYSICAL_DISK_METRIC;
import static org.metricshub.hardware.util.HwConstants.HW_POWER_PHYSICAL_DISK_METRIC;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.metricshub.engine.common.helpers.ArrayHelper;
import org.metricshub.engine.telemetry.Monitor;
import org.metricshub.engine.telemetry.TelemetryManager;
import org.metricshub.hardware.util.HwCollectHelper;

@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class PhysicalDiskPowerAndEnergyEstimator extends HardwarePowerAndEnergyEstimator {

	public PhysicalDiskPowerAndEnergyEstimator(final Monitor monitor, final TelemetryManager telemetryManager) {
		super(monitor, telemetryManager);
	}

	/**
	 * Estimates the power consumption of the Physical disk
	 * @return Double
	 */
	@Override
	protected Double doPowerEstimation() {
		final double powerConsumption;

		final List<String> monitorDataList = new ArrayList<>();
		monitorDataList.add(monitor.getAttribute(MONITOR_ATTRIBUTE_NAME));
		monitorDataList.add(monitor.getAttribute("model"));
		monitorDataList.add(monitor.getAttribute("info"));

		final Monitor parentMonitor = telemetryManager.findParentMonitor(monitor);
		if (parentMonitor != null) {
			monitorDataList.add(parentMonitor.getAttribute(MONITOR_ATTRIBUTE_NAME));
		}

		final String[] monitorData = monitorDataList.toArray(new String[0]);

		// SSD
		if (ArrayHelper.anyMatchLowerCase(str -> str.contains("ssd") || str.contains("solid"), monitorData)) {
			powerConsumption = estimateSsdPowerConsumption(monitorData);
		} else if (ArrayHelper.anyMatchLowerCase(str -> str.contains("sas"), monitorData)) {
			// HDD (non-SSD), depending on the interface
			// SAS
			powerConsumption = estimateSasPowerConsumption(monitorData);
		} else if (ArrayHelper.anyMatchLowerCase(str -> str.contains("scsi") || str.contains("ide"), monitorData)) {
			// SCSI and IDE
			powerConsumption = estimateScsiAndIde(monitorData);
		} else {
			// SATA (and unknown, we'll assume it's the most common case)
			powerConsumption = estimateSataOrDefault(monitorData);
		}
		return powerConsumption;
	}

	/**
	 * Estimates SATA physical disk power consumption. Default is 11W.
	 * @param data the physical disk information
	 * @return double value
	 */
	double estimateSataOrDefault(final String[] data) {
		// Factor in the rotational speed
		if (ArrayHelper.anyMatchLowerCase(str -> str.contains("10k"), data)) {
			return 27.0;
		} else if (ArrayHelper.anyMatchLowerCase(str -> str.contains("15k"), data)) {
			return 32.0;
		} else if (ArrayHelper.anyMatchLowerCase(str -> str.contains("5400") || str.contains("5.4"), data)) {
			return 7.0;
		}

		// Default for 7200-RPM disks
		return 11.0;
	}

	/**
	 * Estimates SCSI and IDE physical disk power consumption
	 * @param data the physical disk information
	 * @return double value
	 */
	double estimateScsiAndIde(final String[] data) {
		// SCSI and IDE
		// Factor in the rotational speed
		if (ArrayHelper.anyMatchLowerCase(str -> str.contains("10k"), data)) {
			// Only SCSI supports 10k
			return 32.0;
		} else if (ArrayHelper.anyMatchLowerCase(str -> str.contains("15k"), data)) {
			// Only SCSI supports 15k
			return 35.0;
		} else if (ArrayHelper.anyMatchLowerCase(str -> str.contains("5400") || str.contains("5.4"), data)) {
			// Likely to be cheap IDE
			return 19;
		}

		// Default for 7200-rpm disks, SCSI or IDE, who knows?
		// SCSI is 31 watts, IDE is 21...
		return 30.0;
	}

	/**
	 * Estimates SAS physical disk power consumption
	 * @param data the physical disk information
	 * @return double value
	 */
	double estimateSasPowerConsumption(final String[] data) {
		// Factor in the rotational speed
		if (ArrayHelper.anyMatchLowerCase(str -> str.contains("15k"), data)) {
			return 17.0;
		}
		// Default for 10k-rpm disks (rarely lower than that anyway)
		return 12.0;
	}

	/**
	 * Estimates SSD physical disk power consumption
	 * @param data the physical disk information
	 * @return double value
	 */
	double estimateSsdPowerConsumption(final String[] data) {
		if (ArrayHelper.anyMatchLowerCase(str -> str.contains("pcie"), data)) {
			return 18.0;
		} else if (ArrayHelper.anyMatchLowerCase(str -> str.contains("nvm"), data)) {
			return 6.0;
		}
		return 3.0;
	}

	/**
	 * Estimates the energy consumption of the Physical disk
	 * @return Double
	 */
	@Override
	public Double estimateEnergy() {
		return HwCollectHelper.estimateEnergyUsingPower(
			monitor,
			telemetryManager,
			estimatedPower,
			HW_POWER_PHYSICAL_DISK_METRIC,
			HW_ENERGY_PHYSICAL_DISK_METRIC,
			telemetryManager.getStrategyTime()
		);
	}
}
