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

import java.util.Map;
import java.util.stream.Collectors;
import org.metricshub.web.AgentContextHolder;
import org.metricshub.web.dto.ConnectorInfo;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class ListConnectorsService {

	/**
	 * Holds contextual information about the current agent instance.
	 */
	private AgentContextHolder agentContextHolder;

	/**
	 * Creates a new instance of {@link ListConnectorsService}.
	 *
	 * @param agentContextHolder the {@link AgentContextHolder} used to access the current agent context
	 */
	@Autowired
	public ListConnectorsService(AgentContextHolder agentContextHolder) {
		this.agentContextHolder = agentContextHolder;
	}

	/**
	 * Lists all the connectors information available in the current agent context.
	 *
	 * @return a list of {@link ConnectorInfo} containing connector IDs and their identities.
	 */
	@Tool(
		name = "ListConnectors",
		description = """
		Lists all connectors supported by MetricsHub.
		Returns a list of connector IDs along with their details such as displayName, information, platforms, tags and detection criteria.
		"""
	)
	public Map<String, ConnectorInfo> listConnectors() {
		return agentContextHolder
			.getAgentContext()
			.getConnectorStore()
			.getStore()
			.entrySet()
			.stream()
			.map(entry -> new ConnectorInfo(entry.getKey(), entry.getValue().getConnectorIdentity()))
			.collect(Collectors.toMap(ConnectorInfo::identifier, identity -> identity));
	}
}
