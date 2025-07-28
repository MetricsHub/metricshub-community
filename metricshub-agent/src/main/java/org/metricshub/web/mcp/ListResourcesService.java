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
import java.util.Set;
import org.metricshub.agent.config.ResourceConfig;
import org.metricshub.agent.config.ResourceGroupConfig;
import org.metricshub.engine.configuration.IConfiguration;
import org.metricshub.web.AgentContextHolder;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Service that retrieves all configured hosts from both resource groups and top-level resources.
 * <p>
 * It compiles host details including protocols and attributes, and is exposed as a tool
 * for listing resources in the current agent context.
 * </p>
 */
@Service
public class ListResourcesService {

	/**
	 * Holds contextual information about the current agent instance.
	 */
	private AgentContextHolder agentContextHolder;

	/**
	 * Creates a new instance of {@link ListResourcesService}.
	 *
	 * @param agentContextHolder the {@link AgentContextHolder} used to access the current agent context
	 */
	@Autowired
	public ListResourcesService(AgentContextHolder agentContextHolder) {
		this.agentContextHolder = agentContextHolder;
	}

	/**
	 * Retrieves all configured hosts, including both top-level and resource group hosts.
	 *
	 * @return a map where the key is the resource identifier and the value contains its details
	 */
	@Tool(name = "ListHosts", description = "Lists all the configured hosts, including their resource groups, protocols, and attributes.")
	public Map<String, ResourceDetails> listConfiguredHosts() {
		Map<String, ResourceDetails> result = new HashMap<>();

		// List all the resources from the resource groups
		agentContextHolder
			.getAgentContext()
			.getAgentConfig()
			.getResourceGroups()
			.forEach((String resourceGroupKey, ResourceGroupConfig resourceGroupConfig) ->
				result.putAll(listResourceGroupConfiguredResources(resourceGroupKey, resourceGroupConfig.getResources()))
			);

		// Add all the top level resources
		result.putAll(
			listResourceGroupConfiguredResources(
				TOP_LEVEL_VIRTUAL_RESOURCE_GROUP_KEY,
				agentContextHolder.getAgentContext().getAgentConfig().getResources()
			)
		);

		return result;
	}

	/**
	 * Builds a map of resource details for all resources in the specified resource group.
	 *
	 * @param resourceGroupKey the key identifying the resource group
	 * @param resources the resources in the group to process
	 * @return a map where the key is the resource ID and the value contains its details
	 */
	private Map<String, ResourceDetails> listResourceGroupConfiguredResources(
		final String resourceGroupKey,
		Map<String, ResourceConfig> resources
	) {
		final Map<String, ResourceDetails> result = new HashMap<>();

		resources.forEach((String resourceKey, ResourceConfig resourceConfig) -> {
			// Create a resource details object for the host
			final ResourceDetails resourceDetails = ResourceDetails
				.builder()
				.resourceGroupKey(resourceGroupKey)
				.protocols(resolveHostConfigurations(resourceConfig.getProtocols()))
				.attributes(resourceConfig.getAttributes())
				.build();

			result.put(resourceKey, resourceDetails);
		});
		return result;
	}

	/**
	 * Converts protocol configurations into a set of {@link ProtocolHostname} objects.
	 *
	 * @param configurations the map of protocol names to their configurations
	 * @return a set of protocol and hostname pairs extracted from the configurations
	 */
	private Set<ProtocolHostname> resolveHostConfigurations(Map<String, IConfiguration> configurations) {
		// Initialize a set for the protocol hostnames
		final Set<ProtocolHostname> protocolNames = new HashSet<>();

		// Retrieve all the protocols hostnames
		configurations.forEach((String protocolName, IConfiguration configuration) -> 
			protocolNames.add(new ProtocolHostname(protocolName, configuration.getHostname()))
		);

		return protocolNames;
	}
}
