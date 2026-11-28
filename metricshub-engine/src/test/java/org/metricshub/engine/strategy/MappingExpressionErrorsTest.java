package org.metricshub.engine.strategy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.metricshub.engine.common.helpers.JsonHelper;
import org.metricshub.engine.connector.deserializer.ConnectorDeserializer;
import org.metricshub.engine.connector.deserializer.PostDeserializeHelper;
import org.metricshub.engine.connector.model.Connector;
import org.metricshub.engine.connector.model.ConnectorStore;
import org.metricshub.engine.connector.model.IntermediateConnector;
import org.metricshub.engine.connector.model.RawConnector;
import org.metricshub.engine.connector.model.RawConnectorStore;
import org.metricshub.engine.connector.parser.ConnectorParser;
import org.metricshub.engine.connector.parser.ConnectorStoreComposer;
import org.metricshub.engine.connector.update.ContentTypeCheck;

/**
 * A connector whose mapping expressions break the load-time checks (#1684) does not load; a connector whose
 * expressions are valid does.
 */
class MappingExpressionErrorsTest {

	private static final Path STRATEGY_FILES = Paths.get("src", "test", "resources", "test-files", "strategy");
	private static final String BAD_CONNECTOR_ID = "TestConnectorWithBadExpressions";
	private static final String GOOD_CONNECTOR_ID = "TestConnectorWithExpressions";
	private static final String MAPPING = "monitors.disk.discovery.mapping.";

	/**
	 * The connector of the directory, parsed and updated as the agent does, not checked.
	 */
	private static Connector parse(final Path directory, final String connectorId) throws IOException {
		final RawConnector raw = ConnectorParser.withNodeProcessorAndUpdateChain(directory)
			.parseRaw(directory.resolve(connectorId + ".yaml").toFile());
		final ConnectorDeserializer deserializer = new ConnectorDeserializer(JsonHelper.buildYamlMapper());
		PostDeserializeHelper.addPostDeserializeSupport(deserializer.getMapper());
		final Connector connector = deserializer.deserialize(
			new IntermediateConnector(connectorId, raw).getConnectorNode()
		);
		ConnectorParser.createUpdateChain().update(connector);
		return connector;
	}

	@Test
	void badExpressionsAreRefusedAtLoad() throws IOException {
		final Path directory = STRATEGY_FILES.resolve("mappingExpressionErrors");
		assertTrue(new ConnectorStore(directory).getStore().isEmpty());

		final Connector connector = parse(directory, BAD_CONNECTOR_ID);
		final Set<String> findings = ContentTypeCheck.check(connector, BAD_CONNECTOR_ID)
			.stream()
			.map(finding -> finding.rule() + " " + finding.location())
			.collect(Collectors.toSet());
		assertEquals(
			Set.of(
				"REFERENCE_IN_EXPRESSION " + MAPPING + "attributes.size",
				"MONITOR_FUNCTION_OUTSIDE_METRICS " + MAPPING + "attributes.speed",
				"EXPRESSION_UNKNOWN_NAME " + MAPPING + "attributes.model",
				"ENVIRON_IN_EXPRESSION " + MAPPING + "attributes.path",
				"REFERENCE_IN_EXPRESSION " + MAPPING + "attributes.ref",
				"STATE_FUNCTION_NOT_WHOLE_VALUE " + MAPPING + "metrics.disk.energy"
			),
			findings
		);
		assertEquals(6, ContentTypeCheck.check(connector, BAD_CONNECTOR_ID).size());
	}

	@Test
	void validExpressionsLoad() throws IOException {
		final Path directory = STRATEGY_FILES.resolve("mappingExpressions");
		assertEquals(List.of(), ContentTypeCheck.check(parse(directory, GOOD_CONNECTOR_ID), GOOD_CONNECTOR_ID));

		final ConnectorStoreComposer composer = ConnectorStoreComposer.builder()
			.withRawConnectorStore(new RawConnectorStore(directory))
			.withUpdateChain(ConnectorParser.createUpdateChain())
			.withDeserializer(new ConnectorDeserializer(JsonHelper.buildYamlMapper()))
			.build();
		final Map<String, Connector> variants = composer.resolveConnectorStoreVariables(
			composer.generateStaticConnectorStore()
		).getCustomConnectorsMap();

		final Connector connector = variants.get(GOOD_CONNECTOR_ID);
		assertNotNull(connector);
		assertEquals(Map.of("scale", "2"), connector.getVariableValues());
		assertEquals(Map.of("BLOCK_SIZE", "512", "EQ", "=x"), connector.getConstants());
	}
}
