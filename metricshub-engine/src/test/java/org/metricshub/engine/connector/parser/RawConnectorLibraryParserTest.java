package org.metricshub.engine.connector.parser;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.metricshub.engine.connector.model.RawConnector;

class RawConnectorLibraryParserTest {

	@Test
	void testLibraryParseMatchesSingleConnectorParse() throws Exception {
		// ConnectorVariables and EmbeddedConnectorVariables both extend ./System/System, so the second one reads the
		// cached parent: the merge of the first must not have changed it
		final Path directory = Paths.get("src", "test", "resources", "connectorStoreComposer");
		final Map<String, RawConnector> library = new RawConnectorLibraryParser().parseConnectorsFromAllYamlFiles(
			directory
		);

		assertEquals(3, library.size());
		for (final Map.Entry<String, RawConnector> entry : library.entrySet()) {
			final RawConnector single = ConnectorParser.withNodeProcessorAndUpdateChain(directory)
				.parseRaw(directory.resolve(entry.getKey() + ".yaml").toFile());
			assertArrayEquals(single.getByteConnector(), entry.getValue().getByteConnector(), entry.getKey());
			assertEquals(single.getEmbeddedFiles(), entry.getValue().getEmbeddedFiles(), entry.getKey());
		}
	}
}
