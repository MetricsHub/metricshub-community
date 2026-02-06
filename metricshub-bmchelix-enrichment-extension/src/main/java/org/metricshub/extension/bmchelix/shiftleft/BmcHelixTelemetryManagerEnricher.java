package org.metricshub.extension.bmchelix.shiftleft;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * MetricsHub Helix Enrichment Extension
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

import java.util.List;
import java.util.Map;
import org.metricshub.engine.common.helpers.StringHelper;
import org.metricshub.engine.telemetry.Monitor;
import org.metricshub.engine.telemetry.TelemetryManager;
import org.metricshub.extension.bmchelix.BmcHelixEnrichmentExtension;

/**
 * Enrich telemetry manager state for BMC Helix before metrics are recorded.
 */
public class BmcHelixTelemetryManagerEnricher {

	/**
	 * Ordered list of attribute keys to attempt for resolving instance name, with fallback to id.
	 */
	private static final List<String> INSTANCE_NAME_CANDIDATE_KEYS = List.of(
		"name",
		"hw.network.name",
		"system.device",
		"network.interface.name",
		"process.name",
		"system.service.name",
		"service.name",
		"host.name",
		"id"
	);

	/**
	 * Apply shift-left enrichment using the telemetry manager.
	 *
	 * @param telemetryManager telemetry manager providing host and monitor context
	 */
	public void enrich(final TelemetryManager telemetryManager) {
		if (telemetryManager == null || telemetryManager.getMonitors() == null) {
			return;
		}

		for (Map<String, Monitor> monitorsByType : telemetryManager.getMonitors().values()) {
			if (monitorsByType == null) {
				continue;
			}
			for (Monitor monitor : monitorsByType.values()) {
				if (monitor != null) {
					enrichMonitor(monitor);
				}
			}
		}
	}

	/**
	 * Enrich a single monitor with BMC Helix identity attributes.
	 * If the attribute is not present, it will be added.
	 *
	 * @param monitor monitor to enrich
	 */
	private void enrichMonitor(final Monitor monitor) {
		final String currentEntityName = monitor.getAttribute(BmcHelixEnrichmentExtension.ENTITY_NAME_KEY);
		final String currentEntityTypeId = monitor.getAttribute(BmcHelixEnrichmentExtension.ENTITY_TYPE_ID_KEY);
		final String currentInstanceName = monitor.getAttribute(BmcHelixEnrichmentExtension.INSTANCE_NAME_KEY);

		if (!StringHelper.nonNullNonBlank(currentEntityName)) {
			final String entityName = BmcHelixEnrichmentExtension.normalizeIdentityValue(monitor.getId());
			monitor.addAttribute(BmcHelixEnrichmentExtension.ENTITY_NAME_KEY, entityName);
		}
		if (!StringHelper.nonNullNonBlank(currentEntityTypeId)) {
			final String entityTypeId = BmcHelixEnrichmentExtension.normalizeIdentityValue(monitor.getType());
			monitor.addAttribute(BmcHelixEnrichmentExtension.ENTITY_TYPE_ID_KEY, entityTypeId);
		}
		if (!StringHelper.nonNullNonBlank(currentInstanceName)) {
			final String fallbackEntityName = monitor.getAttribute(BmcHelixEnrichmentExtension.ENTITY_NAME_KEY);
			final String instanceName = resolveInstanceName(monitor, fallbackEntityName);
			monitor.addAttribute(BmcHelixEnrichmentExtension.INSTANCE_NAME_KEY, instanceName);
		}
	}

	/**
	 * Resolve the instance name using candidate attributes, then fallback to name, then entityName.
	 *
	 * @param monitor    monitor holding attributes
	 * @param entityName monitor id fallback value
	 * @return resolved instance name
	 */
	private String resolveInstanceName(final Monitor monitor, final String entityName) {
		final String candidateValue = firstNonBlankAttribute(monitor, INSTANCE_NAME_CANDIDATE_KEYS);
		return StringHelper.nonNullNonBlank(candidateValue) ? candidateValue : entityName;
	}

	/**
	 * Find the first non-blank attribute value for the provided keys.
	 *
	 * @param monitor monitor holding attributes
	 * @param keys    ordered attribute keys to check
	 * @return the first non-blank attribute value, or null if none match
	 */
	private String firstNonBlankAttribute(final Monitor monitor, final List<String> keys) {
		for (String key : keys) {
			final String value = monitor.getAttribute(key);
			if (StringHelper.nonNullNonBlank(value)) {
				return value;
			}
		}
		return null;
	}
}
