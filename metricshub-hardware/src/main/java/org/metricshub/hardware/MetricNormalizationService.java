package org.metricshub.hardware;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * MetricsHub Hardware Energy and Sustainability Module
 * ჻჻჻჻჻჻
 * Copyright 2023 - 2024 Sentry Software
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

import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.metricshub.hardware.threshold.CpuMetricNormalizer;
import org.metricshub.hardware.threshold.FanMetricNormalizer;
import org.metricshub.hardware.threshold.GpuMetricNormalizer;
import org.metricshub.hardware.threshold.LogicalDiskMetricNormalizer;
import org.metricshub.hardware.threshold.LunMetricNormalizer;
import org.metricshub.hardware.threshold.MemoryMetricNormalizer;
import org.metricshub.hardware.threshold.NetworkMetricNormalizer;
import org.metricshub.hardware.threshold.OtherDeviceMetricNormalizer;
import org.metricshub.hardware.threshold.PhysicalDiskMetricNormalizer;
import org.metricshub.hardware.threshold.RoboticsMetricNormalizer;
import org.metricshub.hardware.threshold.TapeDriveMetricNormalizer;
import org.metricshub.hardware.threshold.TemperatureMetricNormalizer;
import org.metricshub.hardware.threshold.VoltageMetricNormalizer;
import org.sentrysoftware.metricshub.engine.common.helpers.KnownMonitorType;
import org.sentrysoftware.metricshub.engine.delegate.IPostExecutionService;
import org.sentrysoftware.metricshub.engine.telemetry.Monitor;
import org.sentrysoftware.metricshub.engine.telemetry.TelemetryManager;

/**
 * Service class for normalizing hardware monitor metrics.
 * This service retrieves all monitors from the {@link TelemetryManager}, filters the hardware monitors,
 * and normalizes their metrics based on their type.
 * Implements the {@link IPostExecutionService} interface to define the post-execution metric normalization logic.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class MetricNormalizationService implements IPostExecutionService {

	private TelemetryManager telemetryManager;

	/**
	 * The connector and hosts are generic kinds, so they are excluded from the
	 * physical types
	 */
	private static final Set<KnownMonitorType> EXCLUDED_MONITOR_TYPES = Set.of(
		KnownMonitorType.CONNECTOR,
		KnownMonitorType.HOST
	);

	// Hardware monitor types
	private static final Set<String> HARDWARE_MONITOR_TYPES = Stream
		.of(KnownMonitorType.values())
		.filter(type -> !EXCLUDED_MONITOR_TYPES.contains(type))
		.map(KnownMonitorType::getKey)
		.collect(Collectors.toCollection(() -> new TreeSet<>(String.CASE_INSENSITIVE_ORDER)));

	/**
	 * Checks if the given monitor is a hardware monitor.
	 *
	 * @param monitor the monitor to check
	 * @return {@code true} if the monitor is a hardware monitor, {@code false}
	 *         otherwise
	 */
	private boolean isHardwareMonitor(final Monitor monitor) {
		return HARDWARE_MONITOR_TYPES.contains(monitor.getType());
	}

	/**
	 * Normalizes hardware monitors metrics.
	 * Retrieves all monitors from the {@code telemetryManager}, filters for hardware monitors,
	 * and normalizes the metrics of each monitor based on its type.
	 */
	@Override
	public void run() {
		// Retrieve hardware monitors from the TelemetryManager
		telemetryManager
			.getMonitors()
			.values()
			.stream()
			.flatMap(monitors -> monitors.values().stream())
			.filter(this::isHardwareMonitor)
			.flatMap(monitor ->
				KnownMonitorType.fromString(monitor.getType()).map(type -> new KnownMonitor(monitor, type)).stream()
			)
			.forEach(knownMonitor -> {
				final Monitor monitor = knownMonitor.getMonitor();
				final String hostname = telemetryManager.getHostname();
				final Long strategyTime = telemetryManager.getStrategyTime();
				switch (knownMonitor.getType()) {
					case CPU:
						new CpuMetricNormalizer(strategyTime, hostname).normalize(monitor);
						break;
					case FAN:
						new FanMetricNormalizer(strategyTime, hostname).normalize(monitor);
						break;
					case GPU:
						new GpuMetricNormalizer(strategyTime, hostname).normalize(monitor);
						break;
					case LOGICAL_DISK:
						new LogicalDiskMetricNormalizer(strategyTime, hostname).normalize(monitor);
						break;
					case LUN:
						new LunMetricNormalizer(strategyTime, hostname).normalize(monitor);
						break;
					case MEMORY:
						new MemoryMetricNormalizer(strategyTime, hostname).normalize(monitor);
						break;
					case NETWORK:
						new NetworkMetricNormalizer(strategyTime, hostname).normalize(monitor);
						break;
					case OTHER_DEVICE:
						new OtherDeviceMetricNormalizer(strategyTime, hostname).normalize(monitor);
						break;
					case PHYSICAL_DISK:
						new PhysicalDiskMetricNormalizer(strategyTime, hostname).normalize(monitor);
						break;
					case ROBOTICS:
						new RoboticsMetricNormalizer(strategyTime, hostname).normalize(monitor);
						break;
					case TAPE_DRIVE:
						new TapeDriveMetricNormalizer(strategyTime, hostname).normalize(monitor);
						break;
					case TEMPERATURE:
						new TemperatureMetricNormalizer(strategyTime, hostname).normalize(monitor);
						break;
					case VOLTAGE:
						new VoltageMetricNormalizer(strategyTime, hostname).normalize(monitor);
						break;
					default:
						break;
				}
			});
	}

	/**
	 * Stores a monitor and its type.
	 */
	@Data
	@AllArgsConstructor
	class KnownMonitor {

		private Monitor monitor;
		private KnownMonitorType type;
	}
}
