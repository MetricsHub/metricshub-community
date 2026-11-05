package org.metricshub.engine.strategy.source.compute.golden;

import static org.junit.jupiter.api.Assumptions.assumeFalse;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;
import org.metricshub.engine.connector.model.Connector;
import org.metricshub.engine.connector.model.ConnectorStore;
import org.metricshub.engine.connector.model.ContentTypeReportEntry;
import org.metricshub.engine.connector.model.ContentTypeReportEntry.Kind;
import org.metricshub.engine.strategy.source.compute.golden.ComputeChainGoldenCorpus.Repository;

/**
 * Prints the content-type desugaring report of every connector of the configured connector repositories (the
 * worklist of stage 2 of #1508 and the exit criterion of stage 4), and writes it to
 * <code>target/content-type-report.txt</code>. Same configuration as {@link ComputeChainGoldenCorpus}; skipped when
 * no repository is present.
 */
class ContentTypeReportTest {

	@Test
	void printReport() throws IOException {
		final List<Repository> repositories = Repository.existing();
		assumeFalse(
			repositories.isEmpty(),
			"No connector repository found among " + ComputeChainGoldenCorpus.configuredRoots()
		);

		final StringBuilder report = new StringBuilder();
		final Map<Kind, Integer> totals = new EnumMap<>(Kind.class);
		int connectorsWithFindings = 0;
		for (final Repository repository : repositories) {
			final ConnectorStore store = ComputeChainGoldenCorpus.loadConnectors(repository.getConnectorDirectory());
			final Map<Kind, Integer> counts = new EnumMap<>(Kind.class);
			report.append("# ").append(repository.getRoot().toAbsolutePath().normalize()).append('\n');
			for (final Map.Entry<String, Connector> entry : new TreeMap<>(store.getStore()).entrySet()) {
				final List<ContentTypeReportEntry> findings = entry.getValue().getContentTypeReport();
				if (findings.isEmpty()) {
					continue;
				}
				connectorsWithFindings++;
				report.append('\n').append(entry.getKey()).append(" (").append(findings.size()).append(")\n");
				for (final ContentTypeReportEntry finding : findings) {
					report.append("  ").append(finding).append('\n');
					counts.merge(finding.kind(), 1, Integer::sum);
				}
			}
			report.append("\nTotals for ").append(repository.getRoot().getFileName()).append(":\n");
			counts.forEach((kind, count) -> report.append("  ").append(kind).append(": ").append(count).append('\n'));
			counts.forEach((kind, count) -> totals.merge(kind, count, Integer::sum));
			report.append('\n');
		}
		report.append("Connectors with findings: ").append(connectorsWithFindings).append('\n');
		totals.forEach((kind, count) -> report.append("  ").append(kind).append(": ").append(count).append('\n'));

		final Path output = Path.of("target", "content-type-report.txt");
		Files.createDirectories(output.getParent());
		Files.writeString(output, report.toString(), StandardCharsets.UTF_8);
		System.out.println(report);
		System.out.println("Report written to " + output.toAbsolutePath());
	}
}
