package org.metricshub.hardware.strategy;

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

import static org.metricshub.hardware.constants.CommonConstants.PRESENT_STATUS;
import static org.metricshub.hardware.util.HwCollectHelper.connectorHasHardwareTag;

import java.util.Collection;
import java.util.Map;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.metricshub.engine.client.ClientsExecutor;
import org.metricshub.engine.common.helpers.KnownMonitorType;
import org.metricshub.engine.extension.ExtensionManager;
import org.metricshub.engine.strategy.AbstractStrategy;
import org.metricshub.engine.telemetry.MetricFactory;
import org.metricshub.engine.telemetry.Monitor;
import org.metricshub.engine.telemetry.TelemetryManager;

/**
 * Strategy responsible for executing post-discovery actions for hardware monitors.<br>
 * This strategy is responsible for checking the presence of hardware monitors in the {@link TelemetryManager} and should be executed after the discovery phase.
 */
@Slf4j
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class HardwarePostDiscoveryStrategy extends AbstractStrategy {

	/**
	 * Create a new instance of {@link HardwarePostDiscoveryStrategy}.<br>
	 * This strategy is responsible for checking the presence of hardware monitors in the {@link TelemetryManager} and should be executed after the discovery phase.
	 *
	 * @param telemetryManager The {@link TelemetryManager} instance wrapping the connector monitors.
	 * @param strategyTime     The strategy time (Discovery time).
	 * @param clientsExecutor  The {@link ClientsExecutor} instance.
	 * @param extensionManager The {@link ExtensionManager} instance.
	 */
	public HardwarePostDiscoveryStrategy(
		@NonNull final TelemetryManager telemetryManager,
		@NonNull final Long strategyTime,
		@NonNull final ClientsExecutor clientsExecutor,
		@NonNull final ExtensionManager extensionManager
	) {
		super(telemetryManager, strategyTime, clientsExecutor, extensionManager);
	}

	/**
	 * Sets the current monitor as missing.
	 *
	 * @param monitor A given monitor
	 * @param hostname The host's name
	 * @param metricName The collected metric name
	 */
	public void setAsMissing(final Monitor monitor, final String hostname, final String metricName) {
		new MetricFactory(hostname, telemetryManager.getConnectorStore()).collectNumberMetric(
			monitor,
			metricName,
			0.0,
			strategyTime
		);
	}

	/**
	 * Sets the current monitor as present
	 * @param monitor A given monitor
	 * @param hostname The host's name
	 * @param metricName The collected metric name
	 */
	public void setAsPresent(final Monitor monitor, final String hostname, final String metricName) {
		new MetricFactory(hostname, telemetryManager.getConnectorStore()).collectNumberMetric(
			monitor,
			metricName,
			1.0,
			strategyTime
		);
	}

	/**
	 * Checks whether a monitor is a candidate for hardware missing device detection.
	 *
	 * @param monitorType A given monitor's type
	 * @return boolean Whether the monitor is a candidate for hardware missing device detection.
	 */
	private boolean isCandidateMonitorType(final String monitorType) {
		return KnownMonitorType.MISSING_DEVICE_DETECTION_TYPES.contains(monitorType);
	}

	@Override
	public final void run() {
		// Filter monitors, set present/missing metrics
		telemetryManager
			.getMonitors()
			.values()
			.stream()
			.map(Map::values)
			.flatMap(Collection::stream)
			.filter(monitor -> isCandidateMonitorType(monitor.getType()))
			.filter(telemetryManager::isConnectorStatusOk)
			.filter(monitor -> connectorHasHardwareTag(monitor, telemetryManager))
			.forEach(monitor -> {
				if (!strategyTime.equals(monitor.getDiscoveryTime())) {
					setAsMissing(monitor, telemetryManager.getHostname(), String.format(PRESENT_STATUS, monitor.getType()));
				} else {
					setAsPresent(monitor, telemetryManager.getHostname(), String.format(PRESENT_STATUS, monitor.getType()));
				}
			});
	}
}
