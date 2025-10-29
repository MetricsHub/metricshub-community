package org.metricshub.engine.connector.deserializer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.metricshub.engine.common.helpers.JsonHelper;
import org.metricshub.engine.common.helpers.MetricsHubConstants;
import org.metricshub.engine.connector.model.Connector;
import org.metricshub.engine.connector.model.identity.Detection;
import org.metricshub.engine.connector.model.identity.criterion.Criterion;
import org.metricshub.engine.connector.model.monitor.task.source.Source;
import org.metricshub.engine.connector.parser.SourceKeyProcessor;

public abstract class DeserializerTest implements IDeserializerTest {

	private File getTestResourceFile(String file) {
		return new File(getResourcePath() + file + RESOURCE_EXT);
	}

	@Override
	public Connector getConnector(String file) throws IOException {
		final JsonNode connectorNode = JsonHelper.buildYamlMapper().readTree(getTestResourceFile(file));
		return deserializer.deserialize(new SourceKeyProcessor().process(connectorNode));
	}

	protected void checkMessage(Exception e, String message) {
		assertNotNull(message, () -> "Message cannot be null.");
		assertNotEquals(MetricsHubConstants.EMPTY, message, () -> "Message cannot be empty.");
		assertTrue(
			e.getMessage().contains(message),
			() -> "Exception expected to contain: " + message + ". But got: " + e.getMessage()
		);
	}

	protected void compareCriterion(final Connector connector, List<Criterion> expected) {
		assertNotNull(connector);

		Detection detection = connector.getConnectorIdentity().getDetection();

		assertNotNull(detection);
		assertEquals(expected, detection.getCriteria());
	}

	protected void compareBeforeAllSource(final Connector connector, Map<String, Source> expected) {
		assertNotNull(connector);
		assertEquals(expected, connector.getBeforeAll());
	}
}
