package org.metricshub.engine.connector.model;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * MetricsHub Engine
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.metricshub.engine.connector.model.common.EmbeddedFile;

class IntermediateConnectorTest {

	private static final String CONNECTOR_NODE = "connector";

	private static final String CONTENT_WITHOUT_VARIABLES = "content";

	private static final String CONTENT_WITH_VARIABLES = "${var::variableName}";

	private static final String CONNECTOR_ID = "connectorId";

	@Test
	void testGetDeepCopy() {
		final JsonNode node = JsonNodeFactory.instance.objectNode().put(CONNECTOR_NODE, CONTENT_WITHOUT_VARIABLES);
		final IntermediateConnector intermediateConnector = new IntermediateConnector(
			CONNECTOR_ID,
			node,
			Map.of(1, EmbeddedFile.builder().content(CONTENT_WITHOUT_VARIABLES.getBytes()).build())
		);

		// Create a deep copy of the intermediate connector
		final IntermediateConnector intermediateConnectorDeepCopy = intermediateConnector.getDeepCopy("connectorId2");
		assertNotEquals(intermediateConnector, intermediateConnectorDeepCopy);
		// As the strings are invariant, no need to perform a reference test.
		// Make sur the jsonNode content is the same
		assertEquals(intermediateConnector.getConnectorNode(), intermediateConnector.getConnectorNode());
		// Make sure the JsonNode references are different.
		assertNotSame(intermediateConnector.getConnectorNode(), intermediateConnectorDeepCopy.getConnectorNode());
		// Retrieve embedded files and their copy to perform comparison
		final Map<Integer, EmbeddedFile> embeddedFiles = intermediateConnector.getEmbeddedFiles();
		final Map<Integer, EmbeddedFile> deepCopiedEmbeddedFiles = intermediateConnectorDeepCopy.getEmbeddedFiles();
		// Make sure both maps do not have the same reference.
		assertNotSame(embeddedFiles, deepCopiedEmbeddedFiles);
		// Make sure the maps size is the same.
		assertEquals(embeddedFiles.size(), deepCopiedEmbeddedFiles.size());

		for (Integer embeddedFileId : embeddedFiles.keySet()) {
			final EmbeddedFile embeddedFile = embeddedFiles.get(embeddedFileId);
			final EmbeddedFile copiedEmbeddedFile = deepCopiedEmbeddedFiles.get(embeddedFileId);
			// Make sure the embedded files who has the same name do not have the same reference.
			assertNotSame(embeddedFile, copiedEmbeddedFile);
			// Make sure they have the same content.
			assertEquals(embeddedFile, copiedEmbeddedFile);
			//Make sure their content byte array do not have the same reference
			assertNotSame(embeddedFile.getContent(), copiedEmbeddedFile.getContent());
		}
	}

	@Test
	void testHasVariables() {
		final ObjectNode node = JsonNodeFactory.instance.objectNode().put(CONNECTOR_NODE, CONTENT_WITH_VARIABLES);
		final EmbeddedFile embeddedFile = new EmbeddedFile(CONTENT_WITHOUT_VARIABLES.getBytes(), "filename", 1);
		// connector with variables, embedded file without variables
		IntermediateConnector intermediateConnector = new IntermediateConnector(
			CONNECTOR_ID,
			node,
			Map.of(1, embeddedFile)
		);
		assertTrue(intermediateConnector.hasVariables());
		// connector without variables, embedded file with variables
		node.put(CONNECTOR_NODE, CONTENT_WITHOUT_VARIABLES);
		embeddedFile.setContent(CONTENT_WITH_VARIABLES.getBytes());
		assertTrue(intermediateConnector.hasVariables());
		// connector without variables, embedded file without variables
		embeddedFile.setContent(CONTENT_WITHOUT_VARIABLES.getBytes());
		assertFalse(intermediateConnector.hasVariables());
		// connector with variables, embedded file with variables
		node.put(CONNECTOR_NODE, CONTENT_WITH_VARIABLES);
		embeddedFile.setContent(CONTENT_WITH_VARIABLES.getBytes());
		assertTrue(intermediateConnector.hasVariables());
		// null connector node, null embedded file
		intermediateConnector.setConnectorNode(null);
		intermediateConnector.setEmbeddedFiles(new HashMap<>());
		assertFalse(intermediateConnector.hasVariables());
	}
}
