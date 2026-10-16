package org.metricshub.hardware;

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

import static org.metricshub.hardware.constants.CpuConstants.HW_ENERGY_CPU_METRIC;
import static org.metricshub.hardware.constants.CpuConstants.HW_POWER_CPU_METRIC;
import static org.metricshub.hardware.constants.DiskControllerConstants.HW_ENERGY_DISK_CONTROLLER_METRIC;
import static org.metricshub.hardware.constants.DiskControllerConstants.HW_POWER_DISK_CONTROLLER_METRIC;
import static org.metricshub.hardware.constants.FanConstants.HW_ENERGY_FAN_METRIC;
import static org.metricshub.hardware.constants.FanConstants.HW_POWER_FAN_METRIC;
import static org.metricshub.hardware.constants.MemoryConstants.HW_ENERGY_MEMORY_METRIC;
import static org.metricshub.hardware.constants.MemoryConstants.HW_POWER_MEMORY_METRIC;
import static org.metricshub.hardware.constants.NetworkConstants.HW_ENERGY_NETWORK_METRIC;
import static org.metricshub.hardware.constants.NetworkConstants.HW_POWER_NETWORK_METRIC;
import static org.metricshub.hardware.constants.PhysicalDiskConstants.HW_ENERGY_PHYSICAL_DISK_METRIC;
import static org.metricshub.hardware.constants.PhysicalDiskConstants.HW_POWER_PHYSICAL_DISK_METRIC;
import static org.metricshub.hardware.constants.RoboticsConstants.HW_ENERGY_ROBOTICS_METRIC;
import static org.metricshub.hardware.constants.RoboticsConstants.HW_POWER_ROBOTICS_METRIC;
import static org.metricshub.hardware.constants.TapeDriveConstants.HW_ENERGY_TAPE_DRIVE_METRIC;
import static org.metricshub.hardware.constants.TapeDriveConstants.HW_POWER_TAPE_DRIVE_METRIC;
import static org.metricshub.hardware.constants.VmConstants.HW_ENERGY_VM_METRIC;
import static org.metricshub.hardware.constants.VmConstants.HW_POWER_VM_METRIC;
import static org.metricshub.hardware.constants.VmConstants.POWER_SOURCE_ID_ATTRIBUTE;
import static org.metricshub.hardware.util.HwCollectHelper.connectorHasHardwareTag;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.metricshub.engine.common.helpers.KnownMonitorType;
import org.metricshub.engine.delegate.IPostExecutionService;
import org.metricshub.engine.strategy.utils.CollectHelper;
import org.metricshub.engine.telemetry.MetricFactory;
import org.metricshub.engine.telemetry.Monitor;
import org.metricshub.engine.telemetry.TelemetryManager;
import org.metricshub.engine.telemetry.metric.NumberMetric;
import org.metricshub.hardware.sustainability.CpuPowerEstimator;
import org.metricshub.hardware.sustainability.DiskControllerPowerAndEnergyEstimator;
import org.metricshub.hardware.sustainability.FanPowerAndEnergyEstimator;
import org.metricshub.hardware.sustainability.HardwarePowerAndEnergyEstimator;
import org.metricshub.hardware.sustainability.HostMonitorPowerAndEnergyEstimator;
import org.metricshub.hardware.sustainability.HostMonitorThermalCalculator;
import org.metricshub.hardware.sustainability.MemoryPowerAndEnergyEstimator;
import org.metricshub.hardware.sustainability.NetworkPowerAndEnergyEstimator;
import org.metricshub.hardware.sustainability.PhysicalDiskPowerAndEnergyEstimator;
import org.metricshub.hardware.sustainability.RoboticsPowerAndEnergyEstimator;
import org.metricshub.hardware.sustainability.TapeDrivePowerAndEnergyEstimator;
import org.metricshub.hardware.sustainability.VmPowerAndEnergyEstimator;
import org.metricshub.hardware.util.HwCollectHelper;
import org.metricshub.hardware.util.PowerAndEnergyCollectHelper;

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
	 * @param measuredMonitorIds The ids of the monitors whose power or energy is collected by the connector
	 * @param measuredPowerParentIds The ids of the monitors whose measured power includes their children's power
	 */
	private void estimateAndCollectPowerAndEnergyForMonitorType(
		final KnownMonitorType monitorType,
		final String powerMetricName,
		final String energyMetricName,
		final BiFunction<Monitor, TelemetryManager, HardwarePowerAndEnergyEstimator> estimatorGenerator,
		final Set<String> measuredMonitorIds,
		final Set<String> measuredPowerParentIds
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
			.filter(monitor -> connectorHasHardwareTag(monitor, telemetryManager))
			.forEach(monitor -> {
				if (shouldEstimatePower(monitor, measuredMonitorIds, measuredPowerParentIds)) {
					PowerAndEnergyCollectHelper.collectPowerAndEnergy(
						monitor,
						powerMetricName,
						energyMetricName,
						telemetryManager,
						estimatorGenerator.apply(monitor, telemetryManager)
					);
				}
			});
	}

	/**
	 * Whether the power of the given monitor must be estimated.
	 *
	 * @param monitor            The monitor to estimate
	 * @param measuredMonitorIds The ids of the monitors whose power or energy is collected by the connector
	 * @param measuredPowerParentIds The ids of the monitors whose measured power includes their children's power
	 * @return <code>false</code> if the connector already collected the monitor's power or energy,
	 *         or if its parent device reports its own power. <code>true</code> otherwise.
	 */
	private boolean shouldEstimatePower(
		final Monitor monitor,
		final Set<String> measuredMonitorIds,
		final Set<String> measuredPowerParentIds
	) {
		// Estimation: never overwrite the power or energy collected by the connector
		if (measuredMonitorIds.contains(monitor.getId())) {
			return false;
		} else if (HwCollectHelper.hasMeasuredParent(monitor, telemetryManager, measuredPowerParentIds)) {
			// Double counting: the parent's measured power already includes this monitor
			return false;
		}
		return true;
	}

	/**
	 * For the monitors measured by the connector, derive the metric the connector does not collect:
	 * <ul>
	 *   <li>Power without an energy counter: derive the energy from the measured power (power x time since the
	 *   previous collect, added to the previous energy), so the energy counter keeps being exported.</li>
	 *   <li>Energy counter without power: derive the power from the energy counter (rate per second), so the
	 *   monitor is counted in the host power.</li>
	 * </ul>
	 *
	 * @param measuredMonitorIds The ids of the monitors whose power or energy is collected by the connector
	 */
	private void collectMissingPowerOrEnergy(final Set<String> measuredMonitorIds) {
		if (measuredMonitorIds.isEmpty()) {
			return;
		}
		final MetricFactory metricFactory = new MetricFactory(
			telemetryManager.getHostname(),
			telemetryManager.getConnectorStore()
		);
		telemetryManager
			.getMonitors()
			.values()
			.stream()
			.map(Map::values)
			.flatMap(Collection::stream)
			.filter(monitor -> measuredMonitorIds.contains(monitor.getId()))
			.forEach(monitor -> {
				final String powerMetricName = HwCollectHelper.generatePowerMetricNameForMonitorType(monitor.getType());
				final String energyMetricName = HwCollectHelper.generateEnergyMetricNameForMonitorType(monitor.getType());

				// The connector collects the energy: keep it, and derive the power when it is missing
				if (CollectHelper.getUpdatedNumberMetricValue(monitor, energyMetricName) != null) {
					if (CollectHelper.getUpdatedNumberMetricValue(monitor, powerMetricName) == null) {
						final Double power = HwCollectHelper.calculateMetricRatePerSecond(
							monitor,
							energyMetricName,
							powerMetricName,
							telemetryManager.getHostname()
						);
						// No previous counter (first collect) or a counter reset (negative rate): no power this cycle
						if (power != null && power >= 0) {
							metricFactory.collectNumberMetric(monitor, powerMetricName, power, telemetryManager.getStrategyTime());
						} else if (CollectHelper.getNumberMetricValue(monitor, energyMetricName, true) != null) {
							// Counter reset: the power derived in a previous cycle must not be counted in this cycle's totals
							monitor.getMetrics().remove(powerMetricName);
						}
					}
					return;
				}

				// No measured power in this cycle (e.g. energy only): nothing to derive
				final Double measuredPower = CollectHelper.getUpdatedNumberMetricValue(monitor, powerMetricName);
				if (measuredPower == null) {
					return;
				}

				final Double energy = HwCollectHelper.estimateEnergyUsingPower(
					monitor,
					telemetryManager,
					measuredPower,
					powerMetricName,
					energyMetricName,
					telemetryManager.getStrategyTime()
				);
				if (energy != null) {
					metricFactory.collectNumberMetric(monitor, energyMetricName, energy, telemetryManager.getStrategyTime());
				}
			});
	}

	/**
	 * Find the monitors whose power or energy has been collected during the current cycle.
	 * Called before any estimation, it returns the monitors measured by the connector.
	 * Called after the estimations, it also returns the monitors estimated by the engine.
	 *
	 * @return the ids of the monitors whose power or energy is collected
	 */
	private Set<String> findMonitorIdsWithCollectedPowerOrEnergy() {
		return telemetryManager
			.getMonitors()
			.values()
			.stream()
			.map(Map::values)
			.flatMap(Collection::stream)
			.filter(HwCollectHelper::isPowerOrEnergyCollected)
			.map(Monitor::getId)
			.filter(Objects::nonNull)
			.collect(Collectors.toSet());
	}

	/**
	 * Find the monitors whose power has been measured by the connector during the current cycle and therefore
	 * already includes the power of their children (e.g. a switch reporting its own power, which includes its ports).
	 * <ul>
	 *   <li>An updated power value is required: a monitor reporting only its energy counter, or whose power is stale,
	 *   provides no power that could replace its children's power.</li>
	 *   <li>The host and the enclosures are excluded: their power is the total the components are rescaled against,
	 *   so their children must still be estimated.</li>
	 * </ul>
	 * Must be called before any estimation, otherwise the estimates are reported as measured too.
	 *
	 * @return the ids of the monitors whose measured power includes their children's power
	 */
	private Set<String> findMeasuredPowerParentIds() {
		return telemetryManager
			.getMonitors()
			.values()
			.stream()
			.map(Map::values)
			.flatMap(Collection::stream)
			.filter(monitor -> !KnownMonitorType.HOST.getKey().equals(monitor.getType()))
			.filter(monitor -> !KnownMonitorType.ENCLOSURE.getKey().equals(monitor.getType()))
			.filter(HardwareEnergyPostExecutionService::isPowerUpdated)
			.map(Monitor::getId)
			.filter(Objects::nonNull)
			.collect(Collectors.toSet());
	}

	/**
	 * Whether the power of the given monitor has been collected during the current cycle.
	 *
	 * @param monitor The monitor to check
	 * @return <code>true</code> if the monitor's power metric has been updated
	 */
	private static boolean isPowerUpdated(final Monitor monitor) {
		final String powerMetricName = HwCollectHelper.generatePowerMetricNameForMonitorType(monitor.getType());
		return CollectHelper.getUpdatedNumberMetricValue(monitor, powerMetricName) != null;
	}

	/**
	 * Estimates and collects power and energy consumption for a VM
	 * @param isPowerEstimated   whether the power consumption is estimated
	 * @param measuredMonitorIds The ids of the monitors whose power or energy is collected by the connector
	 */
	private void estimateAndCollectPowerAndEnergyForVm(
		final boolean isPowerEstimated,
		final Set<String> measuredMonitorIds
	) {
		final Map<String, Monitor> vmMonitors = telemetryManager.findMonitorsByType(KnownMonitorType.VM.getKey());

		// If no vm monitors are found, log a message
		if (vmMonitors == null) {
			log.info(HOST_DOES_NOT_CONTAIN_MONITORS, telemetryManager.getHostname(), KnownMonitorType.VM.getKey());
			return;
		}

		// Only the VMs to estimate share the power source: the measured VMs keep their power
		final Map<String, Double> totalPowerSharesByPowerSource = vmMonitors
			.values()
			.stream()
			.collect(
				Collectors.toMap(
					this::getVmPowerSourceMonitorId,
					monitor -> measuredMonitorIds.contains(monitor.getId()) ? 0.0 : HwCollectHelper.getVmPowerShare(monitor),
					Double::sum
				)
			);

		// The power of the measured VMs is deducted from their power source before it is shared
		final Map<String, Double> measuredVmPowerByPowerSource = vmMonitors
			.values()
			.stream()
			.filter(monitor -> measuredMonitorIds.contains(monitor.getId()))
			.filter(monitor -> CollectHelper.getNumberMetricValue(monitor, HW_POWER_VM_METRIC, false) != null)
			.collect(
				Collectors.toMap(
					this::getVmPowerSourceMonitorId,
					monitor -> CollectHelper.getNumberMetricValue(monitor, HW_POWER_VM_METRIC, false),
					Double::sum
				)
			);

		// For each vm monitor, estimate and collect power and energy consumption metrics
		vmMonitors
			.values()
			.stream()
			.filter(monitor -> !HwCollectHelper.isMissing(monitor))
			.filter(monitor -> telemetryManager.isConnectorStatusOk(monitor))
			.filter(monitor -> connectorHasHardwareTag(monitor, telemetryManager))
			.forEach(monitor -> {
				// Estimation: never overwrite the VM power or energy collected by the connector
				if (measuredMonitorIds.contains(monitor.getId())) {
					return;
				}
				PowerAndEnergyCollectHelper.collectPowerAndEnergy(
					monitor,
					HW_POWER_VM_METRIC,
					HW_ENERGY_VM_METRIC,
					telemetryManager,
					new VmPowerAndEnergyEstimator(
						monitor,
						telemetryManager,
						totalPowerSharesByPowerSource,
						isPowerEstimated,
						measuredVmPowerByPowerSource
					)
				);
			});
	}

	/**
	 * @return The ID of the parent {@link Monitor} whose power source is consumed by the given VM.
	 */
	public String getVmPowerSourceMonitorId(final Monitor monitor) {
		// If the parent has a power consumption, then we have the power source
		final Monitor parent = telemetryManager.findParentMonitor(monitor);

		if (parent != null && parent.getMetric(HW_POWER_VM_METRIC, NumberMetric.class) != null) {
			monitor.addAttribute(POWER_SOURCE_ID_ATTRIBUTE, parent.getId());
			return parent.getId();
		}

		// If the parent does not have a power consumption, the power source is the host
		final Monitor hostMonitor = telemetryManager.getEndpointHostMonitor();
		monitor.addAttribute(POWER_SOURCE_ID_ATTRIBUTE, hostMonitor.getId());
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
		// Before any estimation: the monitors whose power or energy has been collected by the connector
		final Set<String> measuredMonitorIds = findMonitorIdsWithCollectedPowerOrEnergy();

		// Before any estimation: the monitors whose measured power already includes their children's power
		final Set<String> measuredPowerParentIds = findMeasuredPowerParentIds();

		estimateAndCollectPowerAndEnergyForMonitorType(
			KnownMonitorType.FAN,
			HW_POWER_FAN_METRIC,
			HW_ENERGY_FAN_METRIC,
			FanPowerAndEnergyEstimator::new,
			measuredMonitorIds,
			measuredPowerParentIds
		);

		estimateAndCollectPowerAndEnergyForMonitorType(
			KnownMonitorType.ROBOTICS,
			HW_POWER_ROBOTICS_METRIC,
			HW_ENERGY_ROBOTICS_METRIC,
			RoboticsPowerAndEnergyEstimator::new,
			measuredMonitorIds,
			measuredPowerParentIds
		);

		estimateAndCollectPowerAndEnergyForMonitorType(
			KnownMonitorType.TAPE_DRIVE,
			HW_POWER_TAPE_DRIVE_METRIC,
			HW_ENERGY_TAPE_DRIVE_METRIC,
			TapeDrivePowerAndEnergyEstimator::new,
			measuredMonitorIds,
			measuredPowerParentIds
		);

		estimateAndCollectPowerAndEnergyForMonitorType(
			KnownMonitorType.DISK_CONTROLLER,
			HW_POWER_DISK_CONTROLLER_METRIC,
			HW_ENERGY_DISK_CONTROLLER_METRIC,
			DiskControllerPowerAndEnergyEstimator::new,
			measuredMonitorIds,
			measuredPowerParentIds
		);

		estimateAndCollectPowerAndEnergyForMonitorType(
			KnownMonitorType.PHYSICAL_DISK,
			HW_POWER_PHYSICAL_DISK_METRIC,
			HW_ENERGY_PHYSICAL_DISK_METRIC,
			PhysicalDiskPowerAndEnergyEstimator::new,
			measuredMonitorIds,
			measuredPowerParentIds
		);

		estimateAndCollectPowerAndEnergyForMonitorType(
			KnownMonitorType.MEMORY,
			HW_POWER_MEMORY_METRIC,
			HW_ENERGY_MEMORY_METRIC,
			MemoryPowerAndEnergyEstimator::new,
			measuredMonitorIds,
			measuredPowerParentIds
		);

		collectNetworkMetrics(measuredMonitorIds, measuredPowerParentIds);

		// Compute host temperature metrics (ambientTemperature, cpuTemperature, cpuThermalDissipationRate)
		new HostMonitorThermalCalculator(telemetryManager).computeHostTemperatureMetrics();

		estimateAndCollectPowerAndEnergyForMonitorType(
			KnownMonitorType.CPU,
			HW_POWER_CPU_METRIC,
			HW_ENERGY_CPU_METRIC,
			CpuPowerEstimator::new,
			measuredMonitorIds,
			measuredPowerParentIds
		);

		// After the estimations: the monitors estimated by the engine are the newly collected ones
		final Set<String> estimatedMonitorIds = findMonitorIdsWithCollectedPowerOrEnergy();
		estimatedMonitorIds.removeAll(measuredMonitorIds);

		// The measured monitors are not estimated: derive their missing energy or power from what the connector collects
		collectMissingPowerOrEnergy(measuredMonitorIds);

		final boolean isPowerMeasured = estimateAndCollectPowerAndEnergyForHost((hostMonitor, manager) ->
			new HostMonitorPowerAndEnergyEstimator(hostMonitor, manager, measuredPowerParentIds, estimatedMonitorIds)
		);
		estimateAndCollectPowerAndEnergyForVm(isPowerMeasured, measuredMonitorIds);
	}

	/**
	 * Estimates and collects power and energy consumption for a given Network monitor
	 *
	 * @param measuredMonitorIds The ids of the monitors whose power or energy is collected by the connector
	 * @param measuredPowerParentIds The ids of the monitors whose measured power includes their children's power
	 */
	private void collectNetworkMetrics(final Set<String> measuredMonitorIds, final Set<String> measuredPowerParentIds) {
		// Find monitors having the selected monitor type
		final String monitorTypeKey = KnownMonitorType.NETWORK.getKey();
		final Map<String, Monitor> sameTypeMonitors = telemetryManager.findMonitorsByType(monitorTypeKey);

		// If no monitors are found, log a message
		if (sameTypeMonitors == null) {
			log.info(HOST_DOES_NOT_CONTAIN_MONITORS, telemetryManager.getHostname(), monitorTypeKey);
			return;
		}

		// For each monitor, estimate and collect power and energy consumption metrics
		sameTypeMonitors
			.values()
			.forEach(monitor -> collectNetworkMonitorMetrics(monitor, measuredMonitorIds, measuredPowerParentIds));
	}

	/**
	 * Collect a Network Monitor bandwidthUtilization metric and estimate its power
	 * and energy consumption
	 *
	 * @param monitor            network {@link Monitor} instance
	 * @param measuredMonitorIds The ids of the monitors whose power or energy is collected by the connector
	 * @param measuredPowerParentIds The ids of the monitors whose measured power includes their children's power
	 */
	private void collectNetworkMonitorMetrics(
		final Monitor monitor,
		final Set<String> measuredMonitorIds,
		final Set<String> measuredPowerParentIds
	) {
		final String hostname = telemetryManager.getHostname();
		final Long strategyTime = telemetryManager.getStrategyTime();

		final Double linkSpeed = CollectHelper.getNumberMetricValue(monitor, "hw.network.bandwidth.limit", false);

		// If we don't have the linkSpeed, we can't compute the bandwidth Utilization
		if (linkSpeed != null && linkSpeed != 0) {
			final Double transmittedByteRate = HwCollectHelper.calculateMetricRatePerSecond(
				monitor,
				"hw.network.io{direction=\"transmit\"}",
				"__hw.network.io.rate{direction=\"transmit\"}",
				hostname
			);

			final Double receivedByteRate = HwCollectHelper.calculateMetricRatePerSecond(
				monitor,
				"hw.network.io{direction=\"receive\"}",
				"__hw.network.io.rate{direction=\"receive\"}",
				hostname
			);

			// The bandwidths are 'byteRate / linkSpeed (in Byte/s)'
			final Double bandwidthUtilizationTransmitted = HwCollectHelper.isValidPositive(transmittedByteRate)
				? transmittedByteRate / linkSpeed
				: null;
			final Double bandwidthUtilizationReceived = HwCollectHelper.isValidPositive(receivedByteRate)
				? receivedByteRate / linkSpeed
				: null;

			final MetricFactory metricFactory = new MetricFactory(hostname, telemetryManager.getConnectorStore());

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

		if (
			connectorHasHardwareTag(monitor, telemetryManager) &&
			shouldEstimatePower(monitor, measuredMonitorIds, measuredPowerParentIds)
		) {
			PowerAndEnergyCollectHelper.collectPowerAndEnergy(
				monitor,
				HW_POWER_NETWORK_METRIC,
				HW_ENERGY_NETWORK_METRIC,
				telemetryManager,
				new NetworkPowerAndEnergyEstimator(monitor, telemetryManager)
			);
		}
	}
}
