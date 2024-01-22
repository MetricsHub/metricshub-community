package org.sentrysoftware.metricshub.hardware;

import java.util.Map;
import java.util.function.BiFunction;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.sentrysoftware.metricshub.engine.common.helpers.KnownMonitorType;
import org.sentrysoftware.metricshub.engine.delegate.IPostExecutionService;
import org.sentrysoftware.metricshub.engine.strategy.utils.CollectHelper;
import org.sentrysoftware.metricshub.engine.telemetry.MetricFactory;
import org.sentrysoftware.metricshub.engine.telemetry.Monitor;
import org.sentrysoftware.metricshub.engine.telemetry.TelemetryManager;
import org.sentrysoftware.metricshub.engine.telemetry.metric.NumberMetric;
import org.sentrysoftware.metricshub.hardware.sustainability.CpuPowerEstimator;
import org.sentrysoftware.metricshub.hardware.sustainability.DiskControllerPowerAndEnergyEstimator;
import org.sentrysoftware.metricshub.hardware.sustainability.FanPowerAndEnergyEstimator;
import org.sentrysoftware.metricshub.hardware.sustainability.HardwarePowerAndEnergyEstimator;
import org.sentrysoftware.metricshub.hardware.sustainability.HostMonitorPowerAndEnergyEstimator;
import org.sentrysoftware.metricshub.hardware.sustainability.HostMonitorThermalCalculator;
import org.sentrysoftware.metricshub.hardware.sustainability.MemoryPowerAndEnergyEstimator;
import org.sentrysoftware.metricshub.hardware.sustainability.NetworkPowerAndEnergyEstimator;
import org.sentrysoftware.metricshub.hardware.sustainability.PhysicalDiskPowerAndEnergyEstimator;
import org.sentrysoftware.metricshub.hardware.sustainability.RoboticsPowerAndEnergyEstimator;
import org.sentrysoftware.metricshub.hardware.sustainability.TapeDrivePowerAndEnergyEstimator;
import org.sentrysoftware.metricshub.hardware.sustainability.VmPowerAndEnergyEstimator;
import org.sentrysoftware.metricshub.hardware.util.HwCollectHelper;
import org.sentrysoftware.metricshub.hardware.util.HwConstants;
import org.sentrysoftware.metricshub.hardware.util.PowerAndEnergyCollectHelper;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Slf4j
public class HardwareEnergyPostExecutionService implements IPostExecutionService {

	private static final String HOST_DOES_NOT_CONTAIN_MONITORS = "Host {} does not contain {} monitors";
	private TelemetryManager telemetryManager;

	/**
	 * Estimates and collects power and energy consumption for a given monitor type e.g: FAN, ROBOTICS, NETWORK, etc ..
	 *
	 * @param monitorType        a given monitor type {@link KnownMonitorType}
	 * @param powerMetricName    the name of the power metric of the given monitor type
	 * @param energyMetricName   the name of the energy metric of the given monitor type
	 * @param estimatorGenerator Function that generates the estimator
	 */
	private void estimateAndCollectPowerAndEnergyForMonitorType(
		final KnownMonitorType monitorType,
		final String powerMetricName,
		final String energyMetricName,
		final BiFunction<Monitor, TelemetryManager, HardwarePowerAndEnergyEstimator> estimatorGenerator
	) {
		// Find monitors having the selected monitor type
		final String monitorTypeKey = monitorType.getKey();
		final Map<String, Monitor> sameTypeMonitors = telemetryManager.findMonitorsByType(monitorTypeKey);

		// If no monitors are found, log a message
		if (sameTypeMonitors == null) {
			log.info(HOST_DOES_NOT_CONTAIN_MONITORS, telemetryManager.getHostname(), monitorTypeKey);
			return;
		}

		// For each monitor, estimate and collect power and energy consumption metrics
		sameTypeMonitors
			.values()
			.stream()
			.filter(monitor -> !HwCollectHelper.isMissing(monitor))
			.filter(monitor -> telemetryManager.isConnectorStatusOk(monitor))
			.forEach(monitor ->
				PowerAndEnergyCollectHelper.collectPowerAndEnergy(
					monitor,
					powerMetricName,
					energyMetricName,
					telemetryManager,
					estimatorGenerator.apply(monitor, telemetryManager)
				)
			);
	}

	/**
	 * Estimates and collects power and energy consumption for a VM
	 * @param isPowerEstimated whether the power consumption is estimated
	 */
	private void estimateAndCollectPowerAndEnergyForVm(final boolean isPowerEstimated) {
		final Map<String, Monitor> vmMonitors = telemetryManager.findMonitorsByType(KnownMonitorType.VM.getKey());

		// If no vm monitors are found, log a message
		if (vmMonitors == null) {
			log.info(HOST_DOES_NOT_CONTAIN_MONITORS, telemetryManager.getHostname(), KnownMonitorType.VM.getKey());
			return;
		}

		final Map<String, Double> totalPowerSharesByPowerSource = vmMonitors
			.values()
			.stream()
			.collect(Collectors.toMap(this::getVmPowerSourceMonitorId, HwCollectHelper::getVmPowerShare, Double::sum));

		// For each vm monitor, estimate and collect power and energy consumption metrics
		vmMonitors
			.values()
			.stream()
			.filter(monitor -> !HwCollectHelper.isMissing(monitor))
			.filter(monitor -> telemetryManager.isConnectorStatusOk(monitor))
			.forEach(monitor ->
				PowerAndEnergyCollectHelper.collectPowerAndEnergy(
					monitor,
					HwConstants.HW_POWER_VM_METRIC,
					HwConstants.HW_ENERGY_VM_METRIC,
					telemetryManager,
					new VmPowerAndEnergyEstimator(monitor, telemetryManager, totalPowerSharesByPowerSource, isPowerEstimated)
				)
			);
	}

	/**
	 * @return The ID of the parent {@link Monitor} whose power source is consumed by the given VM.
	 */
	public String getVmPowerSourceMonitorId(final Monitor monitor) {
		// If the parent has a power consumption, then we have the power source
		final Monitor parent = telemetryManager.findParentMonitor(monitor);

		if (parent != null && parent.getMetric(HwConstants.HW_POWER_VM_METRIC, NumberMetric.class) != null) {
			monitor.addAttribute(HwConstants.POWER_SOURCE_ID_ATTRIBUTE, parent.getId());
			return parent.getId();
		}

		// If the parent does not have a power consumption, the power source is the host
		final Monitor hostMonitor = telemetryManager.getEndpointHostMonitor();
		monitor.addAttribute(HwConstants.POWER_SOURCE_ID_ATTRIBUTE, hostMonitor.getId());
		return hostMonitor.getId();
	}

	/**
	 * Estimates and collects power and energy consumption for the hostMonitor.
	 * @param estimatorGenerator Function that generates the estimator
	 */
	private boolean estimateAndCollectPowerAndEnergyForHost(
		final BiFunction<Monitor, TelemetryManager, HostMonitorPowerAndEnergyEstimator> estimatorGenerator
	) {
		// Find hostMonitor
		final Monitor hostMonitor = telemetryManager.getEndpointHostMonitor();

		// If host is not found, log a message
		if (hostMonitor == null) {
			log.info("Host {} does not exist", telemetryManager.getHostname());
			return false;
		}

		// Compute and collect power and energy for host monitor

		return PowerAndEnergyCollectHelper.collectHostPowerAndEnergy(
			hostMonitor,
			telemetryManager,
			estimatorGenerator.apply(hostMonitor, telemetryManager)
		);
	}

	/**
	 * Runs the estimation of several metrics like power consumption,
	 * energy consumption, thermal consumption information, etc ...
	 */
	@Override
	public void run() {
		estimateAndCollectPowerAndEnergyForMonitorType(
			KnownMonitorType.FAN,
			HwConstants.HW_POWER_FAN_METRIC,
			HwConstants.HW_ENERGY_FAN_METRIC,
			FanPowerAndEnergyEstimator::new
		);

		estimateAndCollectPowerAndEnergyForMonitorType(
			KnownMonitorType.ROBOTICS,
			HwConstants.HW_POWER_ROBOTICS_METRIC,
			HwConstants.HW_ENERGY_ROBOTICS_METRIC,
			RoboticsPowerAndEnergyEstimator::new
		);

		estimateAndCollectPowerAndEnergyForMonitorType(
			KnownMonitorType.TAPE_DRIVE,
			HwConstants.HW_POWER_TAPE_DRIVE_METRIC,
			HwConstants.HW_ENERGY_TAPE_DRIVE_METRIC,
			TapeDrivePowerAndEnergyEstimator::new
		);

		estimateAndCollectPowerAndEnergyForMonitorType(
			KnownMonitorType.DISK_CONTROLLER,
			HwConstants.HW_POWER_DISK_CONTROLLER_METRIC,
			HwConstants.HW_ENERGY_DISK_CONTROLLER_METRIC,
			DiskControllerPowerAndEnergyEstimator::new
		);

		estimateAndCollectPowerAndEnergyForMonitorType(
			KnownMonitorType.PHYSICAL_DISK,
			HwConstants.HW_POWER_PHYSICAL_DISK_METRIC,
			HwConstants.HW_ENERGY_PHYSICAL_DISK_METRIC,
			PhysicalDiskPowerAndEnergyEstimator::new
		);

		estimateAndCollectPowerAndEnergyForMonitorType(
			KnownMonitorType.MEMORY,
			HwConstants.HW_POWER_MEMORY_METRIC,
			HwConstants.HW_ENERGY_MEMORY_METRIC,
			MemoryPowerAndEnergyEstimator::new
		);

		collectNetworkMetrics();

		// Compute host temperature metrics (ambientTemperature, cpuTemperature, cpuThermalDissipationRate)
		new HostMonitorThermalCalculator(telemetryManager).computeHostTemperatureMetrics();

		estimateAndCollectPowerAndEnergyForMonitorType(
			KnownMonitorType.CPU,
			HwConstants.HW_POWER_CPU_METRIC,
			HwConstants.HW_ENERGY_CPU_METRIC,
			CpuPowerEstimator::new
		);

		final boolean isPowerMeasured = estimateAndCollectPowerAndEnergyForHost(HostMonitorPowerAndEnergyEstimator::new);
		estimateAndCollectPowerAndEnergyForVm(isPowerMeasured);
	}

	/**
	 * Estimates and collects power and energy consumption for a given Network monitor
	 */
	private void collectNetworkMetrics() {
		// Find monitors having the selected monitor type
		final String monitorTypeKey = KnownMonitorType.NETWORK.getKey();
		final Map<String, Monitor> sameTypeMonitors = telemetryManager.findMonitorsByType(monitorTypeKey);

		// If no monitors are found, log a message
		if (sameTypeMonitors == null) {
			log.info(HOST_DOES_NOT_CONTAIN_MONITORS, telemetryManager.getHostname(), monitorTypeKey);
			return;
		}

		// For each monitor, estimate and collect power and energy consumption metrics
		sameTypeMonitors.values().forEach(this::collectNetworkMonitorMetrics);
	}

	/**
	 * Collect a Network Monitor bandwidthUtilization metric and estimate its power
	 * and energy consumption
	 *
	 * @param monitor network {@link Monitor} instance
	 */
	private void collectNetworkMonitorMetrics(final Monitor monitor) {
		final String hostname = telemetryManager.getHostname();
		final Long strategyTime = telemetryManager.getStrategyTime();

		final Double linkSpeed = CollectHelper.getNumberMetricValue(monitor, "hw.network.bandwidth.limit", false);

		// If we don't have the linkSpeed, we can't compute the bandwidth Utilization
		if (linkSpeed != null && linkSpeed != 0) {
			final Double transmittedByteRate = HwCollectHelper.calculateMetricRate(
				monitor,
				"hw.network.io{direction=\"transmit\"}",
				"__hw.network.io.rate{direction=\"transmit\"}",
				hostname
			);

			final Double receivedByteRate = HwCollectHelper.calculateMetricRate(
				monitor,
				"hw.network.io{direction=\"receive\"}",
				"__hw.network.io.rate{direction=\"receive\"}",
				hostname
			);

			// The bandwidths are 'byteRate * 8 / linkSpeed (in Bit/s)'
			final Double bandwidthUtilizationTransmitted = HwCollectHelper.isValidPositive(transmittedByteRate)
				? (transmittedByteRate * 8) / linkSpeed
				: null;
			final Double bandwidthUtilizationReceived = HwCollectHelper.isValidPositive(receivedByteRate)
				? (receivedByteRate * 8) / linkSpeed
				: null;

			final MetricFactory metricFactory = new MetricFactory(hostname);

			if (bandwidthUtilizationTransmitted != null) {
				metricFactory.collectNumberMetric(
					monitor,
					"hw.network.bandwidth.utilization{direction=\"transmit\"}",
					bandwidthUtilizationTransmitted,
					strategyTime
				);
			}

			if (bandwidthUtilizationReceived != null) {
				metricFactory.collectNumberMetric(
					monitor,
					"hw.network.bandwidth.utilization{direction=\"receive\"}",
					bandwidthUtilizationReceived,
					strategyTime
				);
			}
		}

		PowerAndEnergyCollectHelper.collectPowerAndEnergy(
			monitor,
			HwConstants.HW_POWER_NETWORK_METRIC,
			HwConstants.HW_ENERGY_NETWORK_METRIC,
			telemetryManager,
			new NetworkPowerAndEnergyEstimator(monitor, telemetryManager)
		);
	}
}
