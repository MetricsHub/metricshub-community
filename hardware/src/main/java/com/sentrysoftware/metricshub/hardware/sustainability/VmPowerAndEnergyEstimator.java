package com.sentrysoftware.metricshub.hardware.sustainability;

import static com.sentrysoftware.metricshub.hardware.util.HwConstants.HW_ENCLOSURE_POWER;
import static com.sentrysoftware.metricshub.hardware.util.HwConstants.HW_ENERGY_VM_METRIC;
import static com.sentrysoftware.metricshub.hardware.util.HwConstants.HW_HOST_ESTIMATED_POWER;
import static com.sentrysoftware.metricshub.hardware.util.HwConstants.HW_HOST_MEASURED_POWER;
import static com.sentrysoftware.metricshub.hardware.util.HwConstants.HW_POWER_VM_METRIC;
import static com.sentrysoftware.metricshub.hardware.util.HwConstants.HW_VM_POWER_SHARE_METRIC;
import static com.sentrysoftware.metricshub.hardware.util.HwConstants.POWER_SOURCE_ID_ATTRIBUTE;

import com.sentrysoftware.metricshub.engine.common.helpers.KnownMonitorType;
import com.sentrysoftware.metricshub.engine.common.helpers.NumberHelper;
import com.sentrysoftware.metricshub.engine.strategy.utils.CollectHelper;
import com.sentrysoftware.metricshub.engine.telemetry.MetricFactory;
import com.sentrysoftware.metricshub.engine.telemetry.Monitor;
import com.sentrysoftware.metricshub.engine.telemetry.TelemetryManager;
import com.sentrysoftware.metricshub.hardware.util.HwCollectHelper;
import java.math.RoundingMode;
import java.util.Map;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class VmPowerAndEnergyEstimator extends HardwarePowerAndEnergyEstimator {

	private Map<String, Double> totalPowerSharesByPowerSource;

	public VmPowerAndEnergyEstimator(
		final Monitor monitor,
		final TelemetryManager telemetryManager,
		final Map<String, Double> totalPowerSharesByPowerSource
	) {
		super(monitor, telemetryManager);
		this.totalPowerSharesByPowerSource = totalPowerSharesByPowerSource;
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
		final double powerShareRatio = totalPowerShares != null ? vmPowerShare / totalPowerShares : 0.0;

		// Getting the power source's power consumption value
		final Monitor powerSourceMonitor = telemetryManager.findMonitorById(powerSourceId);

		Double powerSourcePowerConsumption = null;

		if (powerSourceMonitor != null) {
			if (KnownMonitorType.HOST.getKey().equals(powerSourceMonitor.getType())) {
				powerSourcePowerConsumption =
					CollectHelper.getNumberMetricValue(powerSourceMonitor, HW_HOST_MEASURED_POWER, false);
				if (powerSourcePowerConsumption == null) {
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
			HW_VM_POWER_SHARE_METRIC,
			powerShareRatio * 100,
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
