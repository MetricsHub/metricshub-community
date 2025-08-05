package org.metricshub.web.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.metricshub.agent.context.AgentContext;
import org.metricshub.engine.connector.model.Connector;
import org.metricshub.engine.connector.model.ConnectorStore;
import org.metricshub.engine.connector.model.identity.ConnectorIdentity;
import org.metricshub.web.AgentContextHolder;
import org.metricshub.web.dto.ConnectorInfo;

class ListConnectorsServiceTest {

	@Test
	void testListConnectors() {
		final AgentContextHolder agentContextHolder = mock(AgentContextHolder.class);
		final AgentContext agentContext = mock(AgentContext.class);

		final ConnectorStore connectorStore = mock(ConnectorStore.class);

		when(agentContextHolder.getAgentContext()).thenReturn(agentContext);
		when(agentContext.getConnectorStore()).thenReturn(connectorStore);
		final ConnectorIdentity connectorIdentity1 = ConnectorIdentity
			.builder()
			.compiledFilename("Connector 1")
			.information("Info 1")
			.build();
		final Connector connector1 = Connector.builder().connectorIdentity(connectorIdentity1).build();
		final ConnectorIdentity connectorIdentity2 = ConnectorIdentity
			.builder()
			.compiledFilename("Connector 2")
			.information("Info 2")
			.build();
		final Connector connector2 = Connector.builder().connectorIdentity(connectorIdentity2).build();
		when(connectorStore.getStore()).thenReturn(Map.of("connector1", connector1, "connector2", connector2));

		final Map<String, ConnectorInfo> connectors = new ListConnectorsService(agentContextHolder).listConnectors();
		assertEquals(2, connectors.size(), "Should return two connectors");
		assertEquals(
			new ConnectorInfo("connector1", connectorIdentity1),
			connectors.get("connector1"),
			"Connector 1 should match"
		);
		assertEquals(
			new ConnectorInfo("connector2", connectorIdentity2),
			connectors.get("connector2"),
			"Connector 2 should match"
		);
	}
}
