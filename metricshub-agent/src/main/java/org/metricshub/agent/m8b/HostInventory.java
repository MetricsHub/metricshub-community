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

import static org.metricshub.agent.helper.ConfigHelper.TOP_LEVEL_VIRTUAL_RESOURCE_GROUP_KEY;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.metricshub.agent.config.AgentConfig;
import org.metricshub.agent.config.ResourceConfig;
import org.metricshub.agent.config.ResourceGroupConfig;
import org.metricshub.agent.context.AgentContext;
import org.metricshub.agent.m8b.protocol.HostDescriptor;
import org.metricshub.engine.configuration.IConfiguration;
import org.metricshub.engine.telemetry.TelemetryManager;

/**
 * Builds the host inventory reported to the M8B Governor: the hosts the agent is <em>actually</em>
 * monitoring, i.e. the resources that have an active {@link TelemetryManager}. A configured resource
 * that failed validation has no telemetry manager and is therefore not advertised, so the Governor
 * never routes a tool call to a host the agent cannot reach.
 * <p>
 * A resource key is unique only within its resource group, so a host is identified by the pair
 * (resource group, resource key). The result is sorted by group then key so two identical
 * configurations always produce the same payload.
 * </p>
 */
public final class HostInventory {

	private HostInventory() {}

	/**
	 * @param agentContext the running agent context
	 * @return the monitored hosts, sorted by resource group then resource key
	 */
	public static List<HostDescriptor> from(final AgentContext agentContext) {
		final AgentConfig agentConfig = agentContext.getAgentConfig();
		final Map<String, Map<String, TelemetryManager>> active = agentContext.getTelemetryManagers();
		if (agentConfig == null || active == null) {
			return List.of();
		}
		final List<HostDescriptor> hosts = new ArrayList<>();
		active.forEach((groupKey, resources) -> {
			if (resources != null) {
				resources
					.keySet()
					.forEach(resourceKey ->
						hosts.add(describe(groupKey, resourceKey, resourceConfig(agentConfig, groupKey, resourceKey)))
					);
			}
		});
		hosts.sort(Comparator.comparing(HostDescriptor::resourceGroup).thenComparing(HostDescriptor::resourceKey));
		return List.copyOf(hosts);
	}

	private static ResourceConfig resourceConfig(
		final AgentConfig agentConfig,
		final String groupKey,
		final String resourceKey
	) {
		if (TOP_LEVEL_VIRTUAL_RESOURCE_GROUP_KEY.equals(groupKey)) {
			return agentConfig.getResources() == null ? null : agentConfig.getResources().get(resourceKey);
		}
		final ResourceGroupConfig group =
			agentConfig.getResourceGroups() == null ? null : agentConfig.getResourceGroups().get(groupKey);
		return group == null || group.getResources() == null ? null : group.getResources().get(resourceKey);
	}

	private static HostDescriptor describe(
		final String groupKey,
		final String resourceKey,
		final ResourceConfig resourceConfig
	) {
		final Map<String, String> hostnames = new TreeMap<>();
		final Map<String, String> attributes = new TreeMap<>();
		if (resourceConfig != null) {
			final Map<String, IConfiguration> protocols = resourceConfig.getProtocols();
			if (protocols != null) {
				protocols.forEach((protocol, configuration) -> {
					if (protocol != null && configuration != null && configuration.getHostname() != null) {
						hostnames.put(protocol, configuration.getHostname());
					}
				});
			}
			if (resourceConfig.getAttributes() != null) {
				resourceConfig
					.getAttributes()
					.forEach((key, value) -> {
						if (key != null && value != null) {
							attributes.put(key, value);
						}
					});
			}
		}
		return new HostDescriptor(resourceKey, groupKey, hostnames, attributes);
	}
}
