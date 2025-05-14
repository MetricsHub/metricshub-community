package org.metricshub.engine.connector.parser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.metricshub.engine.connector.model.identity.ConnectionType.LOCAL;
import static org.metricshub.engine.connector.model.identity.ConnectionType.REMOTE;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.metricshub.engine.connector.model.Connector;
import org.metricshub.engine.connector.model.identity.Detection;

class ConnectorVariableProcessorTest {

	@Test
	void testProcess() throws IOException {
		// Define the json test file path
		final Path jsonTestFilePath = Paths.get(
			"src",
			"test",
			"resources",
			"test-files",
			"connector",
			"connectorVariable",
			"connectorVariable.json"
		);

		// Create an ObjectMapper
		final ObjectMapper mapper = new ObjectMapper();

		// Retrieve the json node from the json test file
		final JsonNode connectorNode = mapper.readTree(jsonTestFilePath.toFile());

		final Map<String, String> connectorVariables = new HashMap<>();
		connectorVariables.put("snmp-get-next", "snmpGetNext");
		connectorVariables.put("local-connection-type", "local");

		// Init ConnectorVariableProcessor with the connector variables map and a node processor
		final ConnectorVariableProcessor connectorVariableProcessor = ConnectorVariableProcessor
			.builder()
			.connectorVariables(connectorVariables)
			.next(new ConstantsProcessor(new SourceKeyProcessor()))
			.build();

		assertNotNull(connectorVariableProcessor.getNext());

		// Call the method process
		final JsonNode nodeProcessingResult = connectorVariableProcessor.process(connectorNode);

		// Retrieve the processed connector node
		final Connector customConnector = mapper.treeToValue(nodeProcessingResult, Connector.class);

		final Detection detection = customConnector.getConnectorIdentity().getDetection();

		// Check that the variable value was successfully replaced
		assertEquals("snmpGetNext", detection.getCriteria().get(0).getType());

		assertEquals(Set.of(REMOTE, LOCAL), detection.getConnectionTypes());
	}
}
