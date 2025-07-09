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
import java.util.SortedMap;
import java.util.TreeMap;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.metricshub.engine.client.ClientsExecutor;
import org.metricshub.engine.common.helpers.MetricsHubConstants;
import org.metricshub.engine.extension.ExtensionManager;
import org.metricshub.engine.strategy.AbstractStrategy;
import org.metricshub.engine.telemetry.Monitor;
import org.metricshub.engine.telemetry.TelemetryManager;
import org.metricshub.hardware.util.MonitorNameBuilder;

/**
 * Strategy responsible for executing monitor name generation for hardware monitors.
 */
@Slf4j
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class HardwareMonitorNameGenerationStrategy extends AbstractStrategy {

	/**
	 * Create a new instance of {@link HardwareMonitorNameGenerationStrategy}.<br>
	 * This strategy is responsible for monitor name attribute generation.
	 *
	 * @param telemetryManager The {@link TelemetryManager} instance wrapping the connector monitors.
	 * @param strategyTime     The strategy time (Discovery time).
	 * @param clientsExecutor  The {@link ClientsExecutor} instance.
	 * @param extensionManager The {@link ExtensionManager} instance.
	 */
	public HardwareMonitorNameGenerationStrategy(
		@NonNull final TelemetryManager telemetryManager,
		@NonNull final Long strategyTime,
		@NonNull final ClientsExecutor clientsExecutor,
		@NonNull final ExtensionManager extensionManager
	) {
		super(telemetryManager, strategyTime, clientsExecutor, extensionManager);
	}

	@Override
	public void run() {
		setMonitorsNames();
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
			.filter((Monitor monitor) -> !monitor.isEndpointHost())
			.filter((Monitor monitor) -> !monitor.isConnector())
			.filter(telemetryManager::isConnectorStatusOk)
			.filter(monitor -> connectorHasHardwareTag(monitor, telemetryManager))
			.forEach((Monitor monitor) -> {
				// Look up the true connectorId on the monitor
				final String connectorId = monitor.getAttribute(MetricsHubConstants.MONITOR_ATTRIBUTE_CONNECTOR_ID);

				// Get or create the per-connector map<type, map<hash,monitor>>
				if (connectorId != null) {
					final Map<String, Map<String, Monitor>> hashesPerType = hashesByConnector.computeIfAbsent(
						connectorId,
						k -> new HashMap<>()
					);

					// Get or create the per-type map<hash, monitor>
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
	private static String computeIdCount(final Monitor monitor) {
		final SortedMap<String, String> sortedAttrs = new TreeMap<>(monitor.getAttributes());
		final var sb = new StringBuilder();
		sortedAttrs.forEach((k, v) -> sb.append(k).append("=").append(v).append(";"));
		try {
			final var md = MessageDigest.getInstance("MD5");
			final byte[] digest = md.digest(sb.toString().getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(digest);
		} catch (NoSuchAlgorithmException e) {
			throw new RuntimeException("MD5 not supported", e);
		}
	}
}
