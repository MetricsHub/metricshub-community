package org.metricshub.engine.strategy.source.compute.golden;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.metricshub.engine.client.ClientsExecutor;
import org.metricshub.engine.connector.model.ConnectorStore;
import org.metricshub.engine.connector.model.monitor.task.source.compute.Json2Csv;
import org.metricshub.engine.connector.model.monitor.task.source.compute.Xml2Csv;
import org.metricshub.engine.strategy.source.SourceTable;
import org.metricshub.engine.strategy.source.compute.golden.ComputeChainGoldenCorpus.ConnectorGolden;
import org.metricshub.engine.strategy.source.compute.golden.ComputeChainGoldenCorpus.Inputs;
import org.metricshub.engine.strategy.source.compute.golden.ComputeChainGoldenCorpus.Repository;

/**
 * Verifies (or, with <code>-Dmetricshub.goldens.update=true</code>, rewrites) the compute-chain goldens of every
 * connector of the configured connector repositories. See {@link ComputeChainGoldenCorpus} for the configuration.
 * The connector tests are skipped when no repository is present.
 */
class ComputeChainGoldenCorpusTest {

	/**
	 * One dynamic test per connector, plus one per repository checking that the stored goldens match the connectors.
	 */
	@TestFactory
	Stream<DynamicTest> goldens() {
		final List<Repository> repositories = Repository.existing();
		if (repositories.isEmpty()) {
			return Stream.of(
				DynamicTest.dynamicTest(
					"connector repositories",
					() -> assumeTrue(
						false,
						"No connector repository found among " +
							ComputeChainGoldenCorpus.configuredRoots() +
							": set -D" +
							ComputeChainGoldenCorpus.ROOTS_PROPERTY
					)
				)
			);
		}
		return repositories.stream().flatMap(this::goldens);
	}

	private Stream<DynamicTest> goldens(final Repository repository) {
		final ConnectorStore store = ComputeChainGoldenCorpus.loadConnectors(repository.getConnectorDirectory());
		final Set<String> connectorIds = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
		connectorIds.addAll(store.getStore().keySet());
		final Stream<DynamicTest> connectors = connectorIds.stream()
			.map(
				connectorId -> DynamicTest.dynamicTest(
					repository.getRoot().getFileName() + " / " + connectorId,
					() -> checkConnector(repository, store, connectorId)
				)
			);
		final DynamicTest inventory = DynamicTest.dynamicTest(
			repository.getRoot().getFileName() + " / stored goldens match the connectors",
			() -> checkInventory(repository, connectorIds)
		);
		return Stream.concat(connectors, Stream.of(inventory));
	}

	private void checkConnector(final Repository repository, final ConnectorStore store, final String connectorId)
		throws IOException {
		final ConnectorGolden golden = ComputeChainGoldenCorpus.generate(store, connectorId);
		final Path file = repository.getGoldenDirectory().resolve(connectorId + ".json");
		if (ComputeChainGoldenCorpus.isUpdateMode()) {
			if (golden.getSources().isEmpty()) {
				Files.deleteIfExists(file);
			} else {
				ComputeChainGoldenCorpus.write(file, golden);
			}
			return;
		}
		if (golden.getSources().isEmpty()) {
			assertTrue(!Files.exists(file), connectorId + " has no compute chain but a stored golden " + file);
			return;
		}
		assertTrue(
			Files.exists(file),
			connectorId +
				" has no stored golden " +
				file +
				": generate it with -D" +
				ComputeChainGoldenCorpus.UPDATE_PROPERTY +
				"=true"
		);
		final List<String> differences = ComputeChainGoldenCorpus.diff(file, golden);
		assertTrue(
			differences.isEmpty(),
			() -> connectorId +
				": " +
				differences.size() +
				" difference(s) with " +
				file +
				"\n" +
				String.join("\n", differences)
		);
	}

	/**
	 * Every stored golden must belong to a connector that still exists (a deleted connector leaves no golden behind).
	 */
	private void checkInventory(final Repository repository, final Set<String> connectorIds) throws IOException {
		if (!Files.isDirectory(repository.getGoldenDirectory())) {
			return;
		}
		final List<String> orphans = new ArrayList<>();
		try (Stream<Path> files = Files.list(repository.getGoldenDirectory())) {
			files.map(file -> file.getFileName().toString())
				.filter(name -> name.endsWith(".json"))
				.map(name -> name.substring(0, name.length() - ".json".length()))
				.filter(connectorId -> !connectorIds.contains(connectorId))
				.forEach(connectorId -> {
					if (ComputeChainGoldenCorpus.isUpdateMode()) {
						try {
							Files.delete(repository.getGoldenDirectory().resolve(connectorId + ".json"));
						} catch (IOException e) {
							throw new IllegalStateException(e);
						}
					} else {
						orphans.add(connectorId);
					}
				});
		}
		assertTrue(orphans.isEmpty(), "Stored goldens without a connector: " + orphans);
	}

	// ---------------------------------------------------------------------------------------------------------------
	// The generators themselves, checked without any repository
	// ---------------------------------------------------------------------------------------------------------------

	/**
	 * The generated JSON document is consumed by json2Csv the way the harness expects: one row per entry, the
	 * properties in order, the enclosing-object reference resolved.
	 */
	@Test
	void jsonInputMatchesJson2CsvExpectations() throws Exception {
		final Json2Csv json2Csv = Json2Csv.builder()
			.entryKey("/Entry/Value/Members")
			.properties("/@odata.id;/Status/Health;/Links/Systems[0]/@odata.id;../Count;")
			.build();
		final String json = Inputs.json(json2Csv);
		assertEquals(json, Inputs.json(json2Csv), "deterministic");

		final String csv = ClientsExecutor.executeJson2Csv(
			json,
			json2Csv.getEntryKey(),
			SourceTable.lineToList(json2Csv.getProperties(), ";"),
			";",
			"host"
		);
		// json2Csv does not escape the separator: a value containing ";" shifts the columns (legacy behavior)
		assertEquals(
			"/Entry/Value/Members[0];@odata.id-0;Health-0;@odata.id-0;Count-up;\n" +
				"/Entry/Value/Members[1];@odata.id;1;Health;1;@odata.id;1;Count-up;\n" +
				"/Entry/Value/Members[2];;;;Count-up;\n" +
				"/Entry/Value/Members[3];42;42;42;Count-up;\n",
			csv
		);

		// Root entry key: one entry, the document itself
		final Json2Csv rootKey = Json2Csv.builder().entryKey("/").properties("/Name;/Id").build();
		assertEquals(
			List.of(List.of("/", "Name-0", "Id-0")),
			SourceTable.csvToTable(
				ClientsExecutor.executeJson2Csv(Inputs.json(rootKey), "/", List.of("/Name", "/Id"), ";", "host"),
				";"
			)
		);

		// A "*" in the middle of the entry key iterates an array
		final Json2Csv wildcard = Json2Csv.builder().entryKey("/members/*/disks").properties("/id;../name").build();
		assertEquals(
			8,
			SourceTable.csvToTable(
				ClientsExecutor.executeJson2Csv(
					Inputs.json(wildcard),
					"/members/*/disks",
					List.of("/id", "../name"),
					";",
					"host"
				),
				";"
			).size()
		);
	}

	/**
	 * The generated XML document is consumed by xml2Csv the way the harness expects: one row per record element.
	 */
	@Test
	void xmlInputMatchesXml2CsvExpectations() throws Exception {
		final Xml2Csv xml2Csv = Xml2Csv.builder()
			.recordTag("/configResolveClass/outConfigs")
			.properties("equipmentChassis>dn;equipmentChassis>model;equipmentChassis/stats>power")
			.build();
		final String xml = Inputs.xml(xml2Csv);
		assertEquals(xml, Inputs.xml(xml2Csv), "deterministic");
		final List<List<String>> rows = new ClientsExecutor().executeXmlParsing(
			xml,
			xml2Csv.getProperties(),
			xml2Csv.getRecordTag()
		);
		assertEquals(
			List.of(List.of("dn-0", "model-0", "power-0"), List.of("dn;1", "model;1", "power;1"), List.of("", "", "")),
			rows
		);
	}

	/**
	 * The input shapes carry the edge cases the corpus is about: a cell containing the separator, empty and trailing
	 * empty cells, a blank line, a text with and without a trailing line feed.
	 */
	@Test
	void inputShapesCarryTheEdgeCases() {
		assertTrue(Inputs.TEXT.endsWith("\n"));
		assertTrue(Inputs.TEXT.contains("\n\n"), "blank line");
		assertTrue(Inputs.TEXT.contains(";;"), "empty cell");
		assertTrue(Inputs.TEXT.contains(";\n"), "trailing separator");
		final List<List<String>> table = Inputs.table();
		assertTrue(table.stream().flatMap(List::stream).anyMatch(cell -> cell.contains(";")), "separator inside a cell");
		assertTrue(table.stream().anyMatch(row -> row.get(row.size() - 1).isEmpty()), "trailing empty cell");

		// commandLine: no trailing separator in the raw text, split(";") in the table
		final SourceTable commandLine = Inputs.commandLine(Inputs.TEXT);
		assertEquals("a;b;c\nx;;z;\n\n1;2.5;3\nval;with,comma\nonly", commandLine.getRawData());
		assertEquals(List.of("x", "", "z"), commandLine.getTable().get(1));
		assertEquals(List.of(""), commandLine.getTable().get(2));

		// awk source: csvToTable keeps the trailing empty cell and drops the blank line
		final SourceTable awkSource = Inputs.awkSource(Inputs.TEXT);
		assertEquals(List.of("x", "", "z"), awkSource.getTable().get(1));
		assertEquals(List.of("1", "2.5", "3"), awkSource.getTable().get(2));

		// Each shape is a fresh mutable table
		final Map<String, ?> shapes = Inputs.inputsFor(
			org.metricshub.engine.connector.model.monitor.task.source.TableUnionSource.builder()
				.tables(List.of())
				.computes(List.of(Json2Csv.builder().properties("/a").build()))
				.build(),
			Map.of()
		);
		assertEquals(
			Set.of("text", "table", "commandLine", "awkSource", "csv", "csvReplaceSeparator", "empty", "json"),
			shapes.keySet().stream().collect(Collectors.toSet())
		);
	}
}
