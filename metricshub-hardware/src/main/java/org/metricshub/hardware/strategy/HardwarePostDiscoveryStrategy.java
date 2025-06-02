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

import static org.metricshub.hardware.constants.CommonConstants.ID_COUNT;
import static org.metricshub.hardware.constants.CommonConstants.PRESENT_STATUS;
import static org.metricshub.hardware.util.HwCollectHelper.connectorHasHardwareTag;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.metricshub.engine.client.ClientsExecutor;
import org.metricshub.engine.common.helpers.KnownMonitorType;
import org.metricshub.engine.common.helpers.MetricsHubConstants;
import org.metricshub.engine.extension.ExtensionManager;
import org.metricshub.engine.strategy.AbstractStrategy;
import org.metricshub.engine.telemetry.MetricFactory;
import org.metricshub.engine.telemetry.Monitor;
import org.metricshub.engine.telemetry.TelemetryManager;
import org.metricshub.hardware.util.MonitorNameBuilder;

/**
 * Strategy responsible for executing post-discovery actions for hardware monitors.<br>
 * This strategy is responsible for checking the presence of hardware monitors in the {@link TelemetryManager} and should be executed after the discovery phase.
 */
@Slf4j
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class HardwarePostDiscoveryStrategy extends AbstractStrategy {

	/**
	 * Set of monitor types that should be excluded from hardware missing device detection.
	 */
	private static final Set<String> EXCLUDED_MONITOR_TYPES = Stream
		.of(
			KnownMonitorType.HOST.getKey(),
			KnownMonitorType.CONNECTOR.getKey(),
			KnownMonitorType.LUN.getKey(),
			KnownMonitorType.LOGICAL_DISK.getKey(),
			KnownMonitorType.VOLTAGE.getKey(),
			KnownMonitorType.TEMPERATURE.getKey(),
			KnownMonitorType.VM.getKey(),
			KnownMonitorType.LED.getKey()
		)
		.collect(Collectors.toSet());

	/**
	 * Set of monitor types that are candidates for hardware missing device.
	 */
	private static final Set<String> MONITOR_TYPE_CANDIDATES = KnownMonitorType.KEYS
		.stream()
		.filter(type -> !EXCLUDED_MONITOR_TYPES.contains(type))
		.collect(Collectors.toSet());

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
		new MetricFactory(hostname).collectNumberMetric(monitor, metricName, 0.0, strategyTime);
	}

	/**
	 * Sets the current monitor as present
	 * @param monitor A given monitor
	 * @param hostname The host's name
	 * @param metricName The collected metric name
	 */
	public void setAsPresent(final Monitor monitor, final String hostname, final String metricName) {
		new MetricFactory(hostname).collectNumberMetric(monitor, metricName, 1.0, strategyTime);
	}

	/**
	 * Checks whether a monitor is a candidate for hardware missing device detection.
	 *
	 * @param monitorType A given monitor's type
	 * @return boolean Whether the monitor is a candidate for hardware missing device detection.
	 */
	private boolean isCandidateMonitorType(final String monitorType) {
		return MONITOR_TYPE_CANDIDATES.contains(monitorType);
	}

	@Override
	public final void run() {
		// Set the name of each monitor
		setMonitorsNames();

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

	/**
	 * Iterates all monitors from the telemetry manager, groups them by connector ID and type,
	 * computes a hash-based sequence number (idCount) within each group, and assigns it as an attribute.
	 * If a monitor has no name, generates one via buildMonitorNameUsingType method.
	 */
	private void setMonitorsNames() {
		// Prepare hash storage per connector and type
		final Map<String, Map<String, Map<String, Monitor>>> hashesByConnector = new HashMap<>();

		telemetryManager
			.getMonitors()
			.values()
			.stream()
			.map(Map::values)
			.flatMap(Collection::stream)
			.filter(telemetryManager::isConnectorStatusOk)
			.filter(monitor -> connectorHasHardwareTag(monitor, telemetryManager))
			.forEach((Monitor monitor) -> {
				// Look up the true connectorId on the monitor
				final String connectorId = monitor.getAttribute(MetricsHubConstants.MONITOR_ATTRIBUTE_CONNECTOR_ID);

				// Get or create the per‑connector map<type, map<hash,monitor>>
				if (connectorId != null) {
					final Map<String, Map<String, Monitor>> hashesPerType = hashesByConnector.computeIfAbsent(
						connectorId,
						k -> new HashMap<>()
					);

					// Get or create—the per‑type map<hash,monitor>
					final Map<String, Monitor> hashMap = hashesPerType.computeIfAbsent(monitor.getType(), t -> new HashMap<>());

					// Compute & store
					final String hash = computeIdCount(monitor);
					hashMap.put(hash, monitor);
				}
			});

		for (Map.Entry<String, Map<String, Map<String, Monitor>>> connectorEntry : hashesByConnector.entrySet()) {
			final Map<String, Map<String, Monitor>> perTypeMap = connectorEntry.getValue();

			for (Map.Entry<String, Map<String, Monitor>> typeEntry : perTypeMap.entrySet()) {
				final Map<String, Monitor> hashMap = typeEntry.getValue();

				final List<String> sortedHashes = new ArrayList<>(hashMap.keySet());
				Collections.sort(sortedHashes);

				for (int i = 0; i < sortedHashes.size(); i++) {
					final String hash = sortedHashes.get(i);
					final Monitor monitor = hashMap.get(hash);
					monitor.addAttribute(ID_COUNT, String.valueOf(i + 1));

					if (
						monitor.getAttribute(MetricsHubConstants.MONITOR_ATTRIBUTE_NAME) == null ||
						monitor.getAttribute(MetricsHubConstants.MONITOR_ATTRIBUTE_NAME).isBlank()
					) {
						final String generatedName = new MonitorNameBuilder(telemetryManager.getHostname())
							.buildMonitorNameUsingType(monitor, telemetryManager);
						monitor.addAttribute(MetricsHubConstants.MONITOR_ATTRIBUTE_NAME, generatedName);
					}
				}
			}
		}
	}

	/**
	 * Computes an MD5-based identifier from a monitor’s sorted attributes.
	 *
	 * @param monitor the monitor whose attributes will be hashed
	 * @return lowercase hex MD5 digest of the monitor’s attributes
	 */
	private String computeIdCount(final Monitor monitor) {
		final SortedMap<String, String> sortedAttrs = new TreeMap<>(monitor.getAttributes());
		final StringBuilder sb = new StringBuilder();
		sortedAttrs.forEach((k, v) -> sb.append(k).append("=").append(v).append(";"));
		try {
			final MessageDigest md = MessageDigest.getInstance("MD5");
			final byte[] digest = md.digest(sb.toString().getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(digest);
		} catch (NoSuchAlgorithmException e) {
			throw new RuntimeException("MD5 not supported", e);
		}
	}
}
