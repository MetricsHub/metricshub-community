package org.metricshub.engine.strategy.source.compute.golden;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
 * Prints the content-type desugaring report of every connector of the configured connector repositories, writes it to
 * <code>target/content-type-report.txt</code> and requires it to be empty: every connector states its conversions
 * explicitly (#1508), the desugaring inserts nothing. Same configuration as {@link ComputeChainGoldenCorpus}, including
 * the connector filter; skipped when no repository is present.
 */
class ContentTypeReportTest {

	@Test
	void reportIsEmpty() throws IOException {
		final List<Repository> repositories = Repository.existing();
		assumeFalse(
			repositories.isEmpty(),
			"No connector repository found among " + ComputeChainGoldenCorpus.configuredRoots()
		);

		final Map<Repository, ConnectorStore> stores = new LinkedHashMap<>();
		for (final Repository repository : repositories) {
			// Only the repository's own connectors, its dependencies' ones resolved under them
			stores.put(repository, ComputeChainGoldenCorpus.load(repository).store());
		}
		final String report = ComputeChainGoldenCorpus.contentTypeReport(stores);

		final Path output = Path.of("target", "content-type-report.txt");
		Files.createDirectories(output.getParent());
		Files.writeString(output, report, StandardCharsets.UTF_8);
		System.out.println(report);
		System.out.println("Report written to " + output.toAbsolutePath());

		final List<String> withEntries = stores.values()
			.stream()
			.flatMap(store -> store.getStore().entrySet().stream())
			.filter(entry -> ComputeChainGoldenCorpus.isSelected(entry.getKey()))
			.filter(entry -> !entry.getValue().getContentTypeReport().isEmpty())
			.map(Map.Entry::getKey)
			.sorted()
			.toList();
		assertEquals(List.of(), withEntries, "Connectors with content-type report entries, see " + output.toAbsolutePath());
	}
}
