package org.metricshub.engine.connector.parser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.metricshub.engine.common.helpers.JsonHelper;
import org.metricshub.engine.configuration.AdditionalConnector;
import org.metricshub.engine.connector.deserializer.ConnectorDeserializer;
import org.metricshub.engine.connector.model.Connector;
import org.metricshub.engine.connector.model.ConnectorStore;
import org.metricshub.engine.connector.model.RawConnectorStore;

class ConnectorVariableValuesTest {

	@Test
	void variableValuesPerVariant() {
		final ConnectorStoreComposer composer = ConnectorStoreComposer.builder()
			.withRawConnectorStore(new RawConnectorStore(Paths.get("src", "test", "resources", "connectorVariableValues")))
			.withUpdateChain(ConnectorParser.createUpdateChain())
			.withDeserializer(new ConnectorDeserializer(JsonHelper.buildYamlMapper()))
			.withAdditionalConnectors(
				Map.of(
					"Configured",
					AdditionalConnector.builder().uses("Declared").variables(Map.of("b", "20", "undeclared", "u")).build(),
					"NameOnlyConfigured",
					AdditionalConnector.builder().uses("NameOnly").variables(Map.of("scale", "3")).build()
				)
			)
			.build();

		final ConnectorStore store = composer.generateStaticConnectorStore();

		// A connector that declares variables is composed per resource, even without ${var::}
		assertEquals(List.of("Plain"), List.copyOf(store.getStore().keySet()));
		assertTrue(store.getConnectorsWithVariables().containsAll(List.of("Declared", "NameOnly")));
		assertEquals(Map.of(), store.getStore().get("Plain").getVariableValues());

		final Map<String, Connector> variants = composer.resolveConnectorStoreVariables(store).getCustomConnectorsMap();

		// Default variant: the declared defaults; c has no default and is absent
		assertEquals(Map.of("a", "1", "b", "2"), variants.get("Declared").getVariableValues());
		// Configured variant: the configured value overrides the default; an undeclared name is absent
		assertEquals(Map.of("a", "1", "b", "20"), variants.get("Configured").getVariableValues());

		assertEquals(Map.of("scale", "2"), variants.get("NameOnly").getVariableValues());
		assertEquals(Map.of("scale", "3"), variants.get("NameOnlyConfigured").getVariableValues());
	}
}
