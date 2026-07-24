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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import java.util.Collections;
import org.junit.jupiter.api.Test;
import org.metricshub.agent.context.AgentContext;
import org.metricshub.engine.connector.model.ConnectorStore;
import org.metricshub.engine.connector.model.RawConnectorStore;
import org.metricshub.web.AgentContextHolder;
import org.mockito.Mockito;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class WebConnectorStoreConfigurationTest {

	@Test
	void testWebConnectorStoreThrowsWhenAgentContextIsMissing() {
		final AgentContextHolder agentContextHolder = Mockito.mock(AgentContextHolder.class);
		when(agentContextHolder.getAgentContext()).thenReturn(null);

		final WebConnectorStoreConfiguration configuration = new WebConnectorStoreConfiguration();

		final ResponseStatusException exception = assertThrows(ResponseStatusException.class, () ->
			configuration.webConnectorStore(agentContextHolder)
		);
		assertEquals(HttpStatus.SERVICE_UNAVAILABLE, exception.getStatusCode());
	}

	@Test
	void testWebConnectorStoreBuildsConnectorStoreCopy() {
		final ConnectorStore connectorStore = new ConnectorStore();
		connectorStore.setStore(Collections.emptyMap());
		final RawConnectorStore rawConnectorStore = new RawConnectorStore();
		rawConnectorStore.setStore(Collections.emptyMap());
		connectorStore.setRawConnectorStore(rawConnectorStore);

		final AgentContext agentContext = Mockito.mock(AgentContext.class);
		when(agentContext.getConnectorStore()).thenReturn(connectorStore);

		final AgentContextHolder agentContextHolder = Mockito.mock(AgentContextHolder.class);
		when(agentContextHolder.getAgentContext()).thenReturn(agentContext);

		final WebConnectorStoreConfiguration configuration = new WebConnectorStoreConfiguration();
		final ConnectorStore webStore = configuration.webConnectorStore(agentContextHolder);

		assertNotNull(webStore);
		assertNotNull(webStore.getStore());
	}
}
