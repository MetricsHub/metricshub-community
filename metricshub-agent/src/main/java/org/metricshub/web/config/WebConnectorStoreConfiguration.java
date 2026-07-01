package org.metricshub.web.config;

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

import org.metricshub.agent.helper.ConfigHelper;
import org.metricshub.engine.connector.model.ConnectorStore;
import org.metricshub.engine.connector.parser.AdditionalConnectorsParsingResult;
import org.metricshub.web.AgentContextHolder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * Spring configuration that exposes a {@link ConnectorStore} including connectors with variables
 * (resolved with default variable values for catalog listing in the UI).
 */
@Configuration
public class WebConnectorStoreConfiguration {

	/**
	 * Builds a connector store copy that includes variable connector templates resolved with defaults.
	 *
	 * @param agentContextHolder holder for the active agent context
	 * @return connector store for UI listing
	 */
	@Bean
	public ConnectorStore webConnectorStore(final AgentContextHolder agentContextHolder) {
		final var agentContext = agentContextHolder.getAgentContext();
		if (agentContext == null) {
			throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Agent context is not available.");
		}
		final ConnectorStore connectorStore = agentContext.getConnectorStore().newConnectorStore();

		final AdditionalConnectorsParsingResult additionalConnectors = ConfigHelper.buildAdditionalConnectors(
			connectorStore,
			null,
			connectorStore.getRawConnectorStore()
		);
		connectorStore.addMany(additionalConnectors.getCustomConnectorsMap());
		return connectorStore;
	}
}
