package org.metricshub.engine.strategy.source.compute.golden;

import static org.junit.jupiter.api.Assumptions.assumeFalse;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.metricshub.engine.connector.model.ConnectorStore;
import org.metricshub.engine.strategy.source.compute.golden.ComputeChainGoldenCorpus.Repository;

/**
 * Prints the content-type desugaring report of every connector of the configured connector repositories (the
 * worklist of stage 2 of #1508 and the exit criterion of stage 4), and writes it to
 * <code>target/content-type-report.txt</code>. Same configuration as {@link ComputeChainGoldenCorpus}, including
 * the connector filter; skipped when no repository is present.
 */
class ContentTypeReportTest {

	@Test
	void printReport() throws IOException {
		final List<Repository> repositories = Repository.existing();
		assumeFalse(
			repositories.isEmpty(),
			"No connector repository found among " + ComputeChainGoldenCorpus.configuredRoots()
		);

		final Map<Repository, ConnectorStore> stores = new LinkedHashMap<>();
		for (final Repository repository : repositories) {
			stores.put(repository, ComputeChainGoldenCorpus.loadConnectors(repository.getConnectorDirectory()));
		}
		final String report = ComputeChainGoldenCorpus.contentTypeReport(stores);

		final Path output = Path.of("target", "content-type-report.txt");
		Files.createDirectories(output.getParent());
		Files.writeString(output, report, StandardCharsets.UTF_8);
		System.out.println(report);
		System.out.println("Report written to " + output.toAbsolutePath());
	}
}
