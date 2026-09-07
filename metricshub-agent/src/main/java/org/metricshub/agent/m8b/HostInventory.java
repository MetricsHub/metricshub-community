package org.metricshub.agent.m8b;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * MetricsHub Agent
 * ჻჻჻჻჻჻
 * Copyright 2023 - 2026 MetricsHub
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
import java.util.TreeMap;
import org.metricshub.agent.config.AgentConfig;
import org.metricshub.agent.m8b.protocol.HostDescriptor;
import org.metricshub.web.mcp.ListResourcesService;
import org.metricshub.web.mcp.ResourceDetails;

/**
 * Builds the host inventory reported to the M8B Governor from the agent configuration. The result is
 * sorted by resource key so two identical configurations always produce the same payload.
 */
public final class HostInventory {

	private HostInventory() {}

	/**
	 * @param agentConfig the agent configuration
	 * @return the monitored hosts, sorted by resource key
	 */
	public static List<HostDescriptor> from(final AgentConfig agentConfig) {
		return ListResourcesService.listConfiguredHosts(agentConfig)
			.entrySet()
			.stream()
			.sorted(Map.Entry.comparingByKey())
			.map(entry -> describe(entry.getKey(), entry.getValue()))
			.toList();
	}

	private static HostDescriptor describe(final String resourceKey, final ResourceDetails details) {
		final Map<String, String> hostnames = new TreeMap<>();
		if (details.protocols() != null) {
			details.protocols().forEach(protocol -> hostnames.put(protocol.protocol(), protocol.hostname()));
		}
		final Map<String, String> attributes = new TreeMap<>();
		if (details.attributes() != null) {
			details
				.attributes()
				.forEach((key, value) -> {
					if (key != null && value != null) {
						attributes.put(key, value);
					}
				});
		}
		return new HostDescriptor(resourceKey, details.resourceGroupKey(), hostnames, attributes);
	}
}
