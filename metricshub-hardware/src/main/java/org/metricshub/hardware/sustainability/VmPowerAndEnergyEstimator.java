package org.metricshub.hardware.sustainability;

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
import org.metricshub.hardware.util.HwCollectHelper;
import org.sentrysoftware.metricshub.engine.common.helpers.KnownMonitorType;
import org.sentrysoftware.metricshub.engine.common.helpers.NumberHelper;
import org.sentrysoftware.metricshub.engine.strategy.utils.CollectHelper;
import org.sentrysoftware.metricshub.engine.telemetry.MetricFactory;
import org.sentrysoftware.metricshub.engine.telemetry.Monitor;
import org.sentrysoftware.metricshub.engine.telemetry.TelemetryManager;

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
