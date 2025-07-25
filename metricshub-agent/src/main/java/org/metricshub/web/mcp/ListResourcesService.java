package org.metricshub.web.mcp;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * MetricsHub Agent
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

import static org.metricshub.agent.helper.ConfigHelper.TOP_LEVEL_VIRTUAL_RESOURCE_GROUP_KEY;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import org.metricshub.agent.config.ResourceConfig;
import org.metricshub.agent.config.ResourceGroupConfig;
import org.metricshub.engine.configuration.IConfiguration;
import org.metricshub.web.AgentContextHolder;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class ListResourcesService {

	/**
	 * Holds contextual information about the current agent instance.
	 */
	private AgentContextHolder agentContextHolder;

	/**
	 * Constructor for PingToolService.
	 *
	 * @param agentContextHolder the {@link AgentContextHolder} instance to access
	 *                           the agent context
	 */
	@Autowired
	public ListResourcesService(AgentContextHolder agentContextHolder) {
		this.agentContextHolder = agentContextHolder;
	}

	@Tool(name = "ListHosts", description = "Lists all the configured hosts.")
	public Map<String, ResourceDetails> listConfiguredHosts() {
		Map<String, ResourceDetails> result = new HashMap<>();

		// List all the resources from the resource groups
		for (Map.Entry<String, ResourceGroupConfig> groupEntry : agentContextHolder
			.getAgentContext()
			.getAgentConfig()
			.getResourceGroups()
			.entrySet()) {
			result.putAll(listResourceGroupConfiguredResources(groupEntry.getKey(), groupEntry.getValue().getResources()));
		}

		// Add all the top level resources
		result.putAll(
			listResourceGroupConfiguredResources(
				TOP_LEVEL_VIRTUAL_RESOURCE_GROUP_KEY,
				agentContextHolder.getAgentContext().getAgentConfig().getResources()
			)
		);

		return result;
	}

	private Map<String, ResourceDetails> listResourceGroupConfiguredResources(
		final String resourceGroupKey,
		Map<String, ResourceConfig> resources
	) {
		final Map<String, ResourceDetails> result = new HashMap<>();
		resources.forEach((String resourceKey, ResourceConfig resourceConfig) -> {
			final ResourceDetails resourceInfos = ResourceDetails
				.builder()
				.resourceGroupKey(resourceGroupKey)
				.protocols(resolveHostConfigurations(resourceConfig.getProtocols()))
				.attributes(resourceConfig.getAttributes())
				.build();

			result.put(resourceKey, resourceInfos);
		});
		return result;
	}

	private Set<ProtocolHostname> resolveHostConfigurations(Map<String, IConfiguration> configurations) {
		final Set<ProtocolHostname> protocolNames = new HashSet<>();

		for (Entry<String, IConfiguration> configuration : configurations.entrySet()) {
			protocolNames.add(new ProtocolHostname(configuration.getKey(), configuration.getValue().getHostname()));
		}

		return protocolNames;
	}
}
