package org.metricshub.hardware.sustainability;

import static org.metricshub.hardware.util.HwConstants.HW_ENERGY_NETWORK_METRIC;
import static org.metricshub.hardware.util.HwConstants.HW_POWER_NETWORK_METRIC;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.metricshub.hardware.util.HwCollectHelper;
import org.sentrysoftware.metricshub.engine.strategy.utils.CollectHelper;
import org.sentrysoftware.metricshub.engine.strategy.utils.MappingProcessor;
import org.sentrysoftware.metricshub.engine.strategy.utils.MathOperationsHelper;
import org.sentrysoftware.metricshub.engine.telemetry.Monitor;
import org.sentrysoftware.metricshub.engine.telemetry.TelemetryManager;

@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class NetworkPowerAndEnergyEstimator extends HardwarePowerAndEnergyEstimator {

	public NetworkPowerAndEnergyEstimator(final Monitor monitor, final TelemetryManager telemetryManager) {
		super(monitor, telemetryManager);
	}

	/**
	 * Estimates the power consumption of the Network monitor
	 * @return Double
	 */
	@Override
	protected Double doPowerEstimation() {
		final String name = monitor.getAttribute("name");

		// If the network card's display name contains "wan" or "virt" it's a virtual card, its power consumption is 0.
		if (name != null && (name.contains("wan") || name.contains("virt"))) {
			return 0.0;
		}

		// If the Link Status value is '0' (unplugged), the power consumption is 1 Watt.
		final Double linkStatus = CollectHelper.getNumberMetricValue(monitor, "hw.network.up", false);
		if (linkStatus != null && linkStatus == 0.0) {
			return 1.0;
		}

		final Double linkSpeedMegaBit = MathOperationsHelper.divide(
			"hw.network.bandwidth.limit",
			CollectHelper.getNumberMetricValue(monitor, "hw.network.bandwidth.limit", false),
			MappingProcessor.MEGABIT_2_BYTE_FACTOR,
			telemetryManager.getHostname()
		);

		final Double transmittedBandwidthUtilization = CollectHelper.getNumberMetricValue(
			monitor,
			"hw.network.bandwidth.utilization{direction=\"transmit\"}",
			false
		);

		/*
		 * If Bandwidth Utilization is provided:
		 * Link Speed > 10: (0.5 + 0.5 * Bandwidth Utilization) * 5 * log10(Link Speed)
		 * Default: (0.5 + 0.5 * bandwidth Utilization) * 5
		 */
		if (HwCollectHelper.isValidRatio(transmittedBandwidthUtilization)) {
			if (HwCollectHelper.isValidPositive(linkSpeedMegaBit) && linkSpeedMegaBit > 10) {
				return (0.5 + 0.5 * transmittedBandwidthUtilization) * 5 * Math.log10(linkSpeedMegaBit);
			} else {
				return (0.5 + 0.5 * transmittedBandwidthUtilization) * 5;
			}
		}

		/*
		 * If Link Speed is provided:
		 * Link Speed > 10: 0.75 * 5 * log10(linkSpeed)
		 * Default: 2
		 */
		if (linkSpeedMegaBit != null) {
			if (linkSpeedMegaBit > 10) {
				return 0.75 * 5 * Math.log10(linkSpeedMegaBit);
			} else {
				return 2.0;
			}
		}

		// If we don't have the linkSpeed, we can't compute the bandwidthUtilization and assume the power is 10 Watts
		return 10.0;
	}

	/**
	 * Estimates the energy consumption of the Network monitor
	 * @return Double
	 */
	@Override
	public Double estimateEnergy() {
		return HwCollectHelper.estimateEnergyUsingPower(
			monitor,
			telemetryManager,
			estimatedPower,
			HW_POWER_NETWORK_METRIC,
			HW_ENERGY_NETWORK_METRIC,
			telemetryManager.getStrategyTime()
		);
	}
}
