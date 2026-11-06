package org.metricshub.engine.strategy.source.compute.golden;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.io.TempDir;
import org.metricshub.engine.client.ClientsExecutor;
import org.metricshub.engine.connector.model.ConnectorStore;
import org.metricshub.engine.connector.model.common.ContentType;
import org.metricshub.engine.connector.model.monitor.task.source.CommandLineSource;
import org.metricshub.engine.connector.model.monitor.task.source.HttpSource;
import org.metricshub.engine.connector.model.monitor.task.source.SnmpTableSource;
import org.metricshub.engine.connector.model.monitor.task.source.SqlSource;
import org.metricshub.engine.connector.model.monitor.task.source.StaticSource;
import org.metricshub.engine.connector.model.monitor.task.source.compute.Append;
import org.metricshub.engine.connector.model.monitor.task.source.compute.Compute;
import org.metricshub.engine.connector.model.monitor.task.source.compute.Json2Csv;
import org.metricshub.engine.connector.model.monitor.task.source.compute.Xml2Csv;
import org.metricshub.engine.strategy.source.SourceTable;
import org.metricshub.engine.strategy.source.compute.golden.ComputeChainGoldenCorpus.CompareMode;
import org.metricshub.engine.strategy.source.compute.golden.ComputeChainGoldenCorpus.ConnectorGolden;
import org.metricshub.engine.strategy.source.compute.golden.ComputeChainGoldenCorpus.Inputs;
import org.metricshub.engine.strategy.source.compute.golden.ComputeChainGoldenCorpus.Output;
import org.metricshub.engine.strategy.source.compute.golden.ComputeChainGoldenCorpus.Repository;
import org.metricshub.engine.strategy.source.compute.golden.ComputeChainGoldenCorpus.SourceGolden;

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
		// Runs last, once every repository has been loaded: a misspelt -Dmetricshub.goldens.connectors id must not pass
		final DynamicTest selection = DynamicTest.dynamicTest(
			"selected connectors exist",
			() -> assertEquals(
				List.of(),
				ComputeChainGoldenCorpus.unknownSelection(loadedConnectorIds),
				"unknown connector ids"
			)
		);
		return Stream.concat(repositories.stream().flatMap(this::goldens), Stream.of(selection));
	}

	/** The ids of the connectors loaded by {@link #goldens(Repository)}, across repositories. */
	private final Set<String> loadedConnectorIds = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);

	private Stream<DynamicTest> goldens(final Repository repository) {
		final ConnectorStore store = ComputeChainGoldenCorpus.loadConnectors(repository.getConnectorDirectory());
		final Set<String> connectorIds = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
		connectorIds.addAll(store.getStore().keySet());
		loadedConnectorIds.addAll(connectorIds);
		final Stream<DynamicTest> connectors = connectorIds.stream()
			.filter(ComputeChainGoldenCorpus::isSelected)
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
		final List<String> differences = ComputeChainGoldenCorpus.check(
			repository,
			store,
			connectorId,
			ComputeChainGoldenCorpus.compareMode()
		);
		differences.stream().filter(ComputeChainGoldenCorpus::isIntended).forEach(System.out::println);
		final List<String> failing = differences.stream().filter(d -> !ComputeChainGoldenCorpus.isIntended(d)).toList();
		assertTrue(
			failing.isEmpty(),
			() -> connectorId + ": " + failing.size() + " difference(s) with the stored golden\n" + String.join("\n", failing)
		);
	}

	/**
	 * Every stored golden must belong to a connector that still exists (a deleted connector leaves no golden behind).
	 */
	private void checkInventory(final Repository repository, final Set<String> connectorIds) throws IOException {
		final List<String> orphans = ComputeChainGoldenCorpus.orphanGoldens(repository, connectorIds);
		if (ComputeChainGoldenCorpus.isUpdateMode()) {
			for (final String connectorId : orphans) {
				Files.delete(repository.getGoldenDirectory().resolve(connectorId + ".json"));
			}
			return;
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
		// The fourth entry is a JSON number, not a text
		assertTrue(json.contains("\"Health\":42"), json);
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
		assertTrue(table.stream().flatMap(List::stream).noneMatch(cell -> cell.contains(";")), "no separator in a cell");
		assertTrue(table.stream().anyMatch(row -> row.get(row.size() - 1).isEmpty()), "trailing empty cell");
		final List<List<String>> separatorInCell = Inputs.separatorInCellTable();
		assertEquals(2, separatorInCell.size());
		assertTrue(separatorInCell.get(1).get(0).contains(";"), "separator inside a cell");

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
			List.of(
				"text",
				"textWide",
				"table",
				"tableSeparatorInCell",
				"tableWide",
				"commandLine",
				"commandLineWide",
				"awkSource",
				"awkSourceWide",
				"csv",
				"csvSeparatorInCell",
				"csvWide",
				"csvReplaceSeparator",
				"csvReplaceSeparatorSeparatorInCell",
				"csvReplaceSeparatorWide",
				"empty",
				"json"
			),
			new ArrayList<>(shapes.keySet())
		);
	}

	/**
	 * Every shape has a wide twin built from the 3 × 16 table, every table-derived shape a separator-in-cell twin,
	 * and the text shapes keep their trailing-newline convention.
	 */
	@Test
	void wideAndSeparatorInCellTwins() {
		final List<List<String>> wide = Inputs.wideTable();
		assertEquals(3, wide.size());
		wide.forEach(row -> assertEquals(16, row.size()));
		assertEquals("r1c1", wide.get(0).get(0));
		assertEquals("r3c16", wide.get(2).get(15));
		assertTrue(Inputs.WIDE_TEXT.startsWith("r1c1;r1c2;"), Inputs.WIDE_TEXT);
		assertTrue(Inputs.WIDE_TEXT.endsWith(";r3c16\n"), Inputs.WIDE_TEXT);
		assertEquals(3, Inputs.WIDE_TEXT.chars().filter(c -> c == '\n').count());

		final List<Compute> append = List.of(Append.builder().column(1).value("v").build());
		final Map<String, Supplier<SourceTable>> http = Inputs.inputsFor(
			HttpSource.builder().url("/x").computes(append).build(),
			Map.of()
		);
		assertEquals(
			List.of("text", "textWide", "textNoTrailingNewline", "textNoTrailingNewlineWide", "empty"),
			new ArrayList<>(http.keySet())
		);
		assertEquals(Inputs.WIDE_TEXT, http.get("textWide").get().getRawData());
		assertEquals(
			Inputs.WIDE_TEXT.substring(0, Inputs.WIDE_TEXT.length() - 1),
			http.get("textNoTrailingNewlineWide").get().getRawData()
		);

		final Map<String, Supplier<SourceTable>> commandLine = Inputs.inputsFor(
			CommandLineSource.builder().commandLine("x").computes(append).build(),
			Map.of()
		);
		assertEquals(List.of("commandLine", "commandLineWide", "empty"), new ArrayList<>(commandLine.keySet()));
		assertEquals(wide, commandLine.get("commandLineWide").get().getTable());
		assertTrue(
			!commandLine.get("commandLineWide").get().getRawData().endsWith("\n"),
			"commandLine: no trailing newline"
		);

		final Map<String, Supplier<SourceTable>> sql = Inputs.inputsFor(
			SqlSource.builder().query("q").computes(append).build(),
			Map.of()
		);
		assertEquals(
			List.of("csvReplaceSeparator", "csvReplaceSeparatorSeparatorInCell", "csvReplaceSeparatorWide", "empty"),
			new ArrayList<>(sql.keySet())
		);
		assertEquals(Inputs.separatorInCellTable(), sql.get("csvReplaceSeparatorSeparatorInCell").get().getTable());
		assertTrue(sql.get("csvReplaceSeparatorSeparatorInCell").get().getRawData().contains("cell,with,sep"));

		final Map<String, Supplier<SourceTable>> staticSource = Inputs.inputsFor(
			StaticSource.builder().value("a;b").computes(append).build(),
			Map.of()
		);
		assertEquals(
			List.of("static", "staticSeparatorInCell", "staticWide", "empty"),
			new ArrayList<>(staticSource.keySet())
		);
		assertEquals(List.of(List.of("a", "b")), staticSource.get("static").get().getTable());
		assertEquals(wide, staticSource.get("staticWide").get().getTable());
		assertTrue(staticSource.get("staticWide").get().getRawData().startsWith("r1c1;r1c2;"));
		// The list form is the single row of exactly these cells, a ';' in a cell included
		final Map<String, Supplier<SourceTable>> staticCells = Inputs.inputsFor(
			StaticSource.builder().value(List.of("a", "b;c")).computes(append).build(),
			Map.of()
		);
		assertEquals(List.of(List.of("a", "b;c")), staticCells.get("static").get().getTable());

		final Map<String, Supplier<SourceTable>> snmp = Inputs.inputsFor(
			SnmpTableSource.builder().oid("1").selectColumns("1").computes(append).build(),
			Map.of()
		);
		assertEquals(List.of("table", "tableSeparatorInCell", "tableWide", "empty"), new ArrayList<>(snmp.keySet()));
		assertEquals(wide, snmp.get("tableWide").get().getTable());
		assertNull(snmp.get("tableWide").get().getRawData());
	}

	/**
	 * The typed comparison checks what the consumers of the chain see: the stripped table when the generated chain
	 * ends in a table, the raw text when it ends in text; the chain itself is not compared, and a difference on a
	 * separator-in-cell input is intended.
	 */
	@Test
	void typedComparison(@TempDir final Path directory) throws IOException {
		final Path file = directory.resolve("X.json");
		ComputeChainGoldenCorpus.write(
			file,
			golden(
				"snmpTable",
				List.of("append"),
				Map.of(
					"table",
					new Output(List.of(List.of("a", "b", ""), List.of("")), "a;b;\n;\n", null, null),
					"tableSeparatorInCell",
					new Output(List.of(List.of("cell", "with", "sep")), null, null, null),
					"text",
					new Output(List.of(List.of("a")), "a\n", null, null)
				)
			)
		);
		// Same for the consumers of a table: trailing empty cells, raw text, source type and chain all differ
		final ConnectorGolden generated = golden(
			"commandLine",
			List.of("toTable", "insertColumn"),
			Map.of(
				"table",
				new Output(List.of(List.of("a", "b"), List.of()), "a;b;", null, null),
				"tableSeparatorInCell",
				new Output(List.of(List.of("cell;with;sep")), null, null, null),
				"text",
				new Output(List.of(List.of("a")), "a\n", null, null)
			)
		);
		generated.getSources().get("s").setEnding(ContentType.TABLE);
		final List<String> typed = ComputeChainGoldenCorpus.diff(file, generated, CompareMode.TYPED);
		assertEquals(1, typed.size(), typed.toString());
		assertTrue(ComputeChainGoldenCorpus.isIntended(typed.get(0)), typed.get(0));
		assertTrue(typed.get(0).contains("s [tableSeparatorInCell]"), typed.get(0));
		// The exact mode sees every one of them: source type, chain, table, tableSeparatorInCell
		assertEquals(4, ComputeChainGoldenCorpus.diff(file, generated, CompareMode.EXACT).size());
		assertEquals(4, ComputeChainGoldenCorpus.diff(file, generated).size());

		// A chain ending in text: the raw text only
		generated.getSources().get("s").setEnding(ContentType.TEXT);
		final List<String> text = ComputeChainGoldenCorpus.diff(file, generated, CompareMode.TYPED);
		assertEquals(1, text.size(), text.toString());
		assertTrue(!ComputeChainGoldenCorpus.isIntended(text.get(0)) && text.get(0).startsWith("s [table]"), text.get(0));

		// An error, a time-out and a missing stored output are differences
		generated.getSources().get("s").setEnding(ContentType.TABLE);
		generated.getSources().get("s").getOutputs().put("text", new Output(null, null, null, "Boom: x"));
		generated.getSources().get("s").getOutputs().put("table", new Output(null, null, true, null));
		generated.getSources().get("s").getOutputs().put("tableWide", new Output(List.of(), null, null, null));
		final List<String> failing = ComputeChainGoldenCorpus.diff(file, generated, CompareMode.TYPED)
			.stream()
			.filter(d -> !ComputeChainGoldenCorpus.isIntended(d))
			.toList();
		assertEquals(3, failing.size(), failing.toString());
		// A chain that now throws on the separator-in-cell input is a regression, not an intended difference
		generated.getSources().get("s").getOutputs().put("tableSeparatorInCell", new Output(null, null, null, "Boom: y"));
		final List<String> withError = ComputeChainGoldenCorpus.diff(file, generated, CompareMode.TYPED);
		assertEquals(
			4,
			withError.stream().filter(d -> !ComputeChainGoldenCorpus.isIntended(d)).count(),
			withError.toString()
		);
		assertEquals(0, withError.stream().filter(ComputeChainGoldenCorpus::isIntended).count(), withError.toString());
	}

	private static ConnectorGolden golden(
		final String source,
		final List<String> computes,
		final Map<String, Output> outputs
	) {
		final ConnectorGolden golden = new ConnectorGolden();
		golden.setConnector("X");
		final SourceGolden sourceGolden = new SourceGolden();
		sourceGolden.setSource(source);
		sourceGolden.setComputes(computes);
		sourceGolden.getOutputs().putAll(outputs);
		golden.getSources().put("s", sourceGolden);
		return golden;
	}

	/**
	 * The connector filter selects connector ids case-insensitively, and the runner honours it in update and check
	 * modes, on a repository holding two connectors: the written golden carries the wide inputs.
	 */
	@Test
	void connectorFilterAndRunner(@TempDir final Path root) throws IOException {
		Files.writeString(root.resolve("GoldenA.yaml"), connectorYaml("Golden A", "commandLine", "commandLine: echo"));
		Files.writeString(
			root.resolve("GoldenB.yaml"),
			connectorYaml("Golden B", "snmpTable", "oid: 1.3\n          selectColumns: \"1,2\"")
		);
		final Path goldens = ComputeChainGoldenCorpus.goldenDirectory(root);
		// The dynamic tests of this class read the same properties in the same JVM: restore them afterwards
		final Map<String, String> previous = new HashMap<>();
		for (final String property : List.of(
			ComputeChainGoldenCorpus.CONNECTORS_PROPERTY,
			ComputeChainGoldenCorpus.UPDATE_PROPERTY,
			ComputeChainGoldenCorpus.COMPARE_PROPERTY
		)) {
			previous.put(property, System.getProperty(property));
		}
		try {
			// Start from check mode, exact comparison, whatever the Maven command line set
			System.clearProperty(ComputeChainGoldenCorpus.UPDATE_PROPERTY);
			System.clearProperty(ComputeChainGoldenCorpus.COMPARE_PROPERTY);
			System.setProperty(ComputeChainGoldenCorpus.CONNECTORS_PROPERTY, "goldena, Other");
			assertTrue(ComputeChainGoldenCorpus.isSelected("GoldenA"));
			assertTrue(ComputeChainGoldenCorpus.isSelected("OTHER"));
			assertTrue(!ComputeChainGoldenCorpus.isSelected("GoldenB"));

			// A listed id no connector carries is a failure, not a silent no-op (a misspelt id would pass otherwise)
			assertEquals(List.of("Other"), ComputeChainGoldenCorpus.unknownSelection(List.of("GoldenA", "GoldenB")));
			assertEquals(2, GoldenCheck.run(List.of(root), false), "GoldenA has no stored golden, Other is unknown");
			System.setProperty(ComputeChainGoldenCorpus.CONNECTORS_PROPERTY, "goldena");
			assertEquals(List.of(), ComputeChainGoldenCorpus.unknownSelection(List.of("GoldenA", "GoldenB")));

			// Nothing stored yet: one failing difference for the selected connector only
			assertEquals(1, GoldenCheck.run(List.of(root), false));

			System.setProperty(ComputeChainGoldenCorpus.UPDATE_PROPERTY, "true");
			assertEquals(0, GoldenCheck.run(List.of(root), false));
			assertTrue(Files.exists(goldens.resolve("GoldenA.json")));
			assertTrue(!Files.exists(goldens.resolve("GoldenB.json")), "not selected: not written");
			final String written = Files.readString(goldens.resolve("GoldenA.json"));
			assertTrue(written.contains("\"commandLineWide\""), written);
			assertTrue(written.contains("\"r1c16\""), written);
			assertTrue(!written.contains("ending"), "the ending content type is not stored");

			System.clearProperty(ComputeChainGoldenCorpus.UPDATE_PROPERTY);
			System.setProperty(ComputeChainGoldenCorpus.COMPARE_PROPERTY, "typed");
			assertEquals(0, GoldenCheck.run(List.of(root), true), "the stored golden matches in typed mode");
			System.clearProperty(ComputeChainGoldenCorpus.COMPARE_PROPERTY);
			assertEquals(0, GoldenCheck.run(List.of(root), false), "and in exact mode");

			System.clearProperty(ComputeChainGoldenCorpus.CONNECTORS_PROPERTY);
			assertTrue(ComputeChainGoldenCorpus.isSelected("GoldenB"));
			assertEquals(1, GoldenCheck.run(List.of(root), false), "GoldenB has no stored golden");
			// A golden of a connector that no longer exists: reported, then deleted in update mode
			Files.copy(goldens.resolve("GoldenA.json"), goldens.resolve("Zombie.json"));
			assertEquals(
				List.of("Zombie"),
				ComputeChainGoldenCorpus.orphanGoldens(Repository.of(root), List.of("GoldenA", "GoldenB"))
			);
			assertEquals(2, GoldenCheck.run(List.of(root), false), "GoldenB has no stored golden, Zombie no connector");
			System.setProperty(ComputeChainGoldenCorpus.UPDATE_PROPERTY, "true");
			assertEquals(0, GoldenCheck.run(List.of(root), false));
			System.clearProperty(ComputeChainGoldenCorpus.UPDATE_PROPERTY);
			assertTrue(!Files.exists(goldens.resolve("Zombie.json")), "deleted by the update mode");
			assertEquals(0, GoldenCheck.run(List.of(root), false));
		} finally {
			previous.forEach((property, value) -> {
				if (value == null) {
					System.clearProperty(property);
				} else {
					System.setProperty(property, value);
				}
			});
		}
	}

	private static String connectorYaml(final String displayName, final String type, final String fields) {
		return """
			connector:
			  displayName: %s
			  detection:
			    appliesTo: [ linux ]
			    criteria:
			    - type: deviceType
			      keep: [ linux ]
			monitors:
			  thing:
			    simple:
			      sources:
			        s:
			          type: %s
			          %s
			          computes:
			          - type: append
			            column: 1
			            value: v
			      mapping:
			        source: ${source::s}
			        attributes:
			          id: $1
			""".formatted(displayName, type, fields);
	}
}
