package org.metricshub.engine.strategy.source.compute.golden;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.metricshub.engine.connector.model.Connector;
import org.metricshub.engine.connector.model.ConnectorStore;
import org.metricshub.engine.strategy.source.compute.golden.ComputeChainGoldenCorpus.CompareMode;
import org.metricshub.engine.strategy.source.compute.golden.ComputeChainGoldenCorpus.ConnectorGolden;

/**
 * Pins what the engine makes of a connector written the way third-party connectors were written before the content
 * types of #1508 (<code>src/test/resources/compatibility/LegacyIdioms.yaml</code>): the desugaring report and the
 * results of every compute chain on the generated inputs, compared exactly. A change of these goldens changes the
 * behavior of such connectors: it must be reviewed and documented for connector authors. Regenerate them with
 * <code>-Dmetricshub.goldens.update=true</code>.
 */
class ContentTypeCompatibilityTest {

	private static final String CONNECTOR_ID = "LegacyIdioms";
	private static final Path DIRECTORY = Path.of("src", "test", "resources", "compatibility");
	private static final Path GOLDEN = DIRECTORY.resolve("goldens").resolve(CONNECTOR_ID + ".json");
	private static final Path REPORT = DIRECTORY.resolve("goldens").resolve(CONNECTOR_ID + "-report.txt");

	@Test
	void legacyIdiomsKeepTheirResults() throws IOException {
		final ConnectorStore store = ComputeChainGoldenCorpus.loadConnectors(DIRECTORY);
		final Connector connector = store.getStore().get(CONNECTOR_ID);
		assertNotNull(connector, "The compatibility connector must load");

		final String report = connector.getContentTypeReport()
			.stream()
			.map(Object::toString)
			.collect(Collectors.joining("\n", "", "\n"));
		final ConnectorGolden golden = ComputeChainGoldenCorpus.generate(store, CONNECTOR_ID);
		if (ComputeChainGoldenCorpus.isUpdateMode()) {
			Files.writeString(REPORT, report, StandardCharsets.UTF_8);
			ComputeChainGoldenCorpus.write(GOLDEN, golden);
		}

		assertTrue(Files.exists(GOLDEN), "Missing " + GOLDEN + ": run with -Dmetricshub.goldens.update=true");
		assertEquals(Files.readString(REPORT, StandardCharsets.UTF_8).replace("\r\n", "\n"), report);
		assertEquals(List.of(), ComputeChainGoldenCorpus.diff(GOLDEN, golden, CompareMode.EXACT));
	}
}
