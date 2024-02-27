package org.sentrysoftware.metricshub.hardware.strategy;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * Hardware Energy and Sustainability Module
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
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.Data;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.sentrysoftware.metricshub.engine.common.helpers.KnownMonitorType;
import org.sentrysoftware.metricshub.engine.strategy.IStrategy;
import org.sentrysoftware.metricshub.engine.telemetry.TelemetryManager;
import org.sentrysoftware.metricshub.hardware.HardwareEnergyPostExecutionService;

@RequiredArgsConstructor
@Data
public class HardwareStrategy implements IStrategy {

	@NonNull
	private TelemetryManager telemetryManager;

	@NonNull
	private Long strategyTime;

	/**
	 * The connector and hosts are generic kinds so they are excluded from the physical types
	 */
	private static final Set<KnownMonitorType> EXCLUDED_MONITOR_TYPES = Set.of(
		KnownMonitorType.CONNECTOR,
		KnownMonitorType.HOST
	);

	/**
	 * Set of all the hardware monitor types as strings
	 */
	private static final Set<String> HARDWARE_MONITOR_TYPES = Stream
		.of(KnownMonitorType.values())
		.filter(monitorType -> !EXCLUDED_MONITOR_TYPES.contains(monitorType))
		.map(KnownMonitorType::getKey)
		.collect(Collectors.toSet());

	@Override
	public void run() {
		if (hasHardwareMonitors(telemetryManager)) {
			new HardwareEnergyPostExecutionService(telemetryManager).run();
		}
	}

	/**
	 * Whether the telemetry manager defines hardware monitors or not.
	 *
	 * @param telemetryManager Wraps all the monitors.
	 * @return <code>true</code> if the telemetry manager has hardware monitors otherwise <code>false</code>
	 */
	boolean hasHardwareMonitors(final TelemetryManager telemetryManager) {
		return telemetryManager.getMonitors().keySet().stream().anyMatch(HARDWARE_MONITOR_TYPES::contains);
	}

	@Override
	public long getStrategyTimeout() {
		return telemetryManager.getHostConfiguration().getStrategyTimeout();
	}
}
