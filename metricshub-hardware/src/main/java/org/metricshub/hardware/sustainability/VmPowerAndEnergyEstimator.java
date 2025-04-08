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

import static org.metricshub.hardware.util.HwConstants.HW_ENCLOSURE_POWER;
import static org.metricshub.hardware.util.HwConstants.HW_ENERGY_VM_METRIC;
import static org.metricshub.hardware.util.HwConstants.HW_HOST_ESTIMATED_POWER;
import static org.metricshub.hardware.util.HwConstants.HW_HOST_MEASURED_POWER;
import static org.metricshub.hardware.util.HwConstants.HW_POWER_VM_METRIC;
import static org.metricshub.hardware.util.HwConstants.POWER_SOURCE_ID_ATTRIBUTE;

import java.math.RoundingMode;
import java.util.Map;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.metricshub.engine.common.helpers.KnownMonitorType;
import org.metricshub.engine.common.helpers.NumberHelper;
import org.metricshub.engine.strategy.utils.CollectHelper;
import org.metricshub.engine.telemetry.MetricFactory;
import org.metricshub.engine.telemetry.Monitor;
import org.metricshub.engine.telemetry.TelemetryManager;
import org.metricshub.hardware.util.HwCollectHelper;

@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class VmPowerAndEnergyEstimator extends HardwarePowerAndEnergyEstimator {

	private Map<String, Double> totalPowerSharesByPowerSource;
	private boolean isPowerMeasured;

	public VmPowerAndEnergyEstimator(
		final Monitor monitor,
		final TelemetryManager telemetryManager,
		final Map<String, Double> totalPowerSharesByPowerSource,
		final boolean isPowerMeasured
	) {
		super(monitor, telemetryManager);
		this.totalPowerSharesByPowerSource = totalPowerSharesByPowerSource;
		this.isPowerMeasured = isPowerMeasured;
	}

	/**
	 * Estimates the power consumption of the VM monitor
	 *
	 * @return Double
	 */
	@Override
	protected Double doPowerEstimation() {
		// Get the vm power share, always >= 0.0 here
		final double vmPowerShare = HwCollectHelper.getVmPowerShare(monitor);

		// Getting the VM's power share ratio
		final String powerSourceId = monitor.getAttribute(POWER_SOURCE_ID_ATTRIBUTE);
		final Double totalPowerShares = totalPowerSharesByPowerSource.get(powerSourceId);

		// totalPowerShares is never null here because the VM always comes with a powerShare value
		final double powerShareRatio = totalPowerShares != null && totalPowerShares > 0.0
			? vmPowerShare / totalPowerShares
			: 0.0;

		// Getting the power source's power consumption value
		final Monitor powerSourceMonitor = telemetryManager.findMonitorById(powerSourceId);

		Double powerSourcePowerConsumption = null;

		if (powerSourceMonitor != null) {
			if (KnownMonitorType.HOST.getKey().equals(powerSourceMonitor.getType())) {
				// If the power of the power source monitor is measured
				if (isPowerMeasured) {
					powerSourcePowerConsumption =
						CollectHelper.getNumberMetricValue(powerSourceMonitor, HW_HOST_MEASURED_POWER, false);
				} else {
					// If the power of the power source monitor is estimated
					powerSourcePowerConsumption =
						CollectHelper.getNumberMetricValue(powerSourceMonitor, HW_HOST_ESTIMATED_POWER, false);
				}
			} else if (KnownMonitorType.ENCLOSURE.getKey().equals(powerSourceMonitor.getType())) {
				powerSourcePowerConsumption = CollectHelper.getNumberMetricValue(powerSourceMonitor, HW_ENCLOSURE_POWER, false);
			} else {
				powerSourcePowerConsumption =
					CollectHelper.getNumberMetricValue(
						powerSourceMonitor,
						HwCollectHelper.generateEnergyMetricNameForMonitorType(powerSourceMonitor.getType()),
						false
					);
			}
		}

		// Setting the VM's power consumption, energy and energy usage values
		if (powerSourcePowerConsumption != null && powerSourcePowerConsumption >= 0.0) {
			estimatedPower = NumberHelper.round(powerSourcePowerConsumption * powerShareRatio, 2, RoundingMode.HALF_UP);
		}

		final MetricFactory metricFactory = new MetricFactory(telemetryManager.getHostname());
		metricFactory.collectNumberMetric(
			monitor,
			"hw.vm.power_ratio",
			powerShareRatio,
			telemetryManager.getStrategyTime()
		);
		return estimatedPower;
	}

	/**
	 * Estimates the energy consumption of the VM monitor
	 *
	 * @return Double
	 */
	@Override
	public Double estimateEnergy() {
		return HwCollectHelper.estimateEnergyUsingPower(
			monitor,
			telemetryManager,
			estimatedPower,
			HW_POWER_VM_METRIC,
			HW_ENERGY_VM_METRIC,
			telemetryManager.getStrategyTime()
		);
	}
}
