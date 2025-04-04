package org.metricshub.agent.helper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.metricshub.engine.connector.model.identity.ConnectionType.LOCAL;
import static org.metricshub.engine.connector.model.identity.ConnectionType.REMOTE;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.metricshub.agent.config.AdditionalConnector;
import org.metricshub.agent.connector.AdditionalConnectorsParsingResult;
import org.metricshub.agent.connector.ConnectorVariablesLibraryParser;
import org.metricshub.engine.connector.model.Connector;
import org.metricshub.engine.connector.model.identity.Detection;
import org.metricshub.engine.connector.model.identity.criterion.SnmpGetNextCriterion;

class ConnectorVariablesLibraryParserTest {

	private static final String CONNECTOR_ID = "connectorVariable";

	@Test
	void testParse() throws IOException {
		// Define the yaml test files path
		final Path yamlTestPath = Paths.get("src", "test", "resources", "connectorVariablesLibraryParser");

		// Call ConnectorVariablesLibraryParser to parse the custom connectors files using the connectorVariables map and the connector id
		final ConnectorVariablesLibraryParser connectorVariablesLibraryParser = new ConnectorVariablesLibraryParser();

		final Map<String, String> connectorVariables = new HashMap<>();
		connectorVariables.put("snmp-get-next", "snmpGetNext");
		connectorVariables.put("local-variable", "local");
		final Map<String, AdditionalConnector> additionalConnectorConfigMap = new HashMap<>();
		final AdditionalConnector additionalConnectorConfig = AdditionalConnector
			.builder()
			.force(true)
			.uses(CONNECTOR_ID)
			.variables(connectorVariables)
			.build();
		additionalConnectorConfigMap.put(CONNECTOR_ID, additionalConnectorConfig);
		final AdditionalConnectorsParsingResult parsingResult = connectorVariablesLibraryParser.parse(
			yamlTestPath,
			additionalConnectorConfigMap
		);

		final Map<String, Connector> customConnectorsMap = parsingResult.getCustomConnectorsMap();

		// Check that only the connector containing variables is returned in the map
		assertEquals(1, customConnectorsMap.size());

		// Check that the connector variable value was successfully replaced.
		final Connector customConnector = customConnectorsMap.get(CONNECTOR_ID);
		final Detection detection = customConnector.getConnectorIdentity().getDetection();
		// Both user's and default values are set, the priority is for the user's variable value.
		assertEquals("snmpGetNext", detection.getCriteria().get(0).getType());
		// User's value set, no default value.
		assertEquals(Set.of(REMOTE, LOCAL), detection.getConnectionTypes());
		// User's value not set, default value is set.
		final SnmpGetNextCriterion criterion = (SnmpGetNextCriterion) detection.getCriteria().get(0);
		assertEquals("1.3.6.1.4.1.795.10.1.1.3.1.1", criterion.getOid());
		// User's value not set, default value not set, variable remains unchanged.
		assertEquals(
			"${var::oid-description}",
			customConnector.getConnectorIdentity().getVariables().get("oid").getDescription().trim()
		);
	}
}
