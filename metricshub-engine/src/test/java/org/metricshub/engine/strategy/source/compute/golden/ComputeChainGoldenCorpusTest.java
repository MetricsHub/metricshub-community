package org.metricshub.engine.strategy.source.compute.golden;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
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
import org.metricshub.engine.connector.model.monitor.task.source.CopySource;
import org.metricshub.engine.connector.model.monitor.task.source.HttpSource;
import org.metricshub.engine.connector.model.monitor.task.source.SnmpTableSource;
import org.metricshub.engine.connector.model.monitor.task.source.SqlSource;
import org.metricshub.engine.connector.model.monitor.task.source.StaticSource;
import org.metricshub.engine.connector.model.monitor.task.source.TextUnionSource;
import org.metricshub.engine.connector.model.monitor.task.source.compute.Append;
import org.metricshub.engine.connector.model.monitor.task.source.compute.Compute;
import org.metricshub.engine.connector.model.monitor.task.source.compute.Json2Csv;
import org.metricshub.engine.connector.model.monitor.task.source.compute.Xml2Csv;
import org.metricshub.engine.strategy.source.SourceTable;
import org.metricshub.engine.strategy.source.compute.golden.ComputeChainGoldenCorpus.ConnectorGolden;
import org.metricshub.engine.strategy.source.compute.golden.ComputeChainGoldenCorpus.Inputs;
import org.metricshub.engine.strategy.source.compute.golden.ComputeChainGoldenCorpus.LoadedRepository;
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
		final LoadedRepository loaded;
		try {
			loaded = ComputeChainGoldenCorpus.load(repository);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		final ConnectorStore store = loaded.store();
		final Set<String> connectorIds = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
		connectorIds.addAll(store.getStore().keySet());
		loadedConnectorIds.addAll(connectorIds);
		loadedConnectorIds.addAll(loaded.unloaded().keySet());
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
		// A connector file that did not load has no golden, no check and no report: a failure, golden or not
		final Stream<DynamicTest> unloaded = loaded.unloaded()
			.entrySet()
			.stream()
			.filter(entry -> ComputeChainGoldenCorpus.isSelected(entry.getKey()))
			.map(
				entry -> DynamicTest.dynamicTest(
					repository.getRoot().getFileName() + " / " + entry.getKey(),
					() -> fail(entry.getKey() + ": connector file did not load " + entry.getValue())
				)
			);
		return Stream.of(connectors, unloaded, Stream.of(inventory)).flatMap(stream -> stream);
	}

	private void checkConnector(final Repository repository, final ConnectorStore store, final String connectorId)
		throws IOException {
		final List<String> differences = ComputeChainGoldenCorpus.check(repository, store, connectorId);
		assertTrue(
			differences.isEmpty(),
			() -> connectorId +
				": " +
				differences.size() +
				" difference(s) with the stored golden\n" +
				String.join("\n", differences)
		);
	}

	/**
	 * Every stored golden must belong to a connector that still exists (a deleted connector leaves no golden behind).
	 */
	private void checkInventory(final Repository repository, final Set<String> connectorIds) throws IOException {
		// A connector whose file exists but did not load keeps its golden, in both modes (its own test fails)
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
		// This text (the AWK json2csv() function) does not escape the separator: a value containing ";" shifts the
		// columns once the text is cut on ";". The json2Csv compute keeps each value in its cell (executeJson2Table).
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
		final Inputs.Shape commandLine = Inputs.commandLine(Inputs.TEXT);
		assertEquals("a;b;c\nx;;z;\n\n1;2.5;3\nval;with,comma\nonly", commandLine.text());
		assertEquals(List.of("x", "", "z"), commandLine.table().get(1));
		assertEquals(List.of(""), commandLine.table().get(2));

		// awk source: csvToTable keeps the trailing empty cell and drops the blank line
		final Inputs.Shape awkSource = Inputs.awkSource(Inputs.TEXT);
		assertEquals(List.of("x", "", "z"), awkSource.table().get(1));
		assertEquals(List.of("1", "2.5", "3"), awkSource.table().get(2));

		// A shape is read on the side of the type: the raw text, else the table serialized; or the table
		assertEquals(SourceTable.ofText(commandLine.text()), commandLine.as(ContentType.TEXT));
		assertEquals(SourceTable.ofTable(commandLine.table()), commandLine.as(ContentType.TABLE));
		assertEquals(SourceTable.ofText("a,b;c;"), Inputs.tableOnly(Inputs.rows("a;b|c")).as(ContentType.TEXT));

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
	void copyWithShortReferenceIsTypedByItsOrigin() {
		final HttpSource http = HttpSource.builder().url("/x").build();
		http.setKey("${source::monitors.m.discovery.sources.raw}");
		final CopySource copy = CopySource.builder()
			.from("${source::raw}")
			.computes(List.of(Append.builder().column(1).value("v").build()))
			.build();
		copy.setKey("${source::monitors.m.discovery.sources.copy}");
		final Map<String, org.metricshub.engine.connector.model.monitor.task.source.Source> sources = Map.of(
			http.getKey(),
			http,
			copy.getKey(),
			copy
		);

		// The short sibling reference resolves to the http origin: the copy is fed texts, as at run time
		assertEquals(ContentType.TEXT, Inputs.resultType(copy, sources, 0));
		final Map<String, Supplier<SourceTable>> inputs = Inputs.inputsFor(copy, sources);
		assertTrue(inputs.containsKey("text"), inputs.keySet().toString());
		assertEquals(SourceTable.ofText(""), inputs.get("empty").get());
	}

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
		assertEquals(SourceTable.ofText(Inputs.WIDE_TEXT), http.get("textWide").get());
		assertEquals(
			Inputs.WIDE_TEXT.substring(0, Inputs.WIDE_TEXT.length() - 1),
			http.get("textNoTrailingNewlineWide").get().getText()
		);
		assertEquals(SourceTable.ofText(""), http.get("empty").get());

		final Map<String, Supplier<SourceTable>> commandLine = Inputs.inputsFor(
			CommandLineSource.builder().commandLine("x").computes(append).build(),
			Map.of()
		);
		assertEquals(List.of("commandLine", "commandLineWide", "empty"), new ArrayList<>(commandLine.keySet()));
		// commandLine produces text
		assertEquals(ContentType.TEXT, commandLine.get("commandLineWide").get().getContentType());
		assertEquals(wide, commandLine.get("commandLineWide").get().asTable());
		assertTrue(!commandLine.get("commandLineWide").get().getText().endsWith("\n"), "commandLine: no trailing newline");

		final Map<String, Supplier<SourceTable>> sql = Inputs.inputsFor(
			SqlSource.builder().query("q").computes(append).build(),
			Map.of()
		);
		assertEquals(
			List.of("csvReplaceSeparator", "csvReplaceSeparatorSeparatorInCell", "csvReplaceSeparatorWide", "empty"),
			new ArrayList<>(sql.keySet())
		);
		// sql produces a table: the cell keeps its ";"
		assertEquals(
			SourceTable.ofTable(Inputs.separatorInCellTable()),
			sql.get("csvReplaceSeparatorSeparatorInCell").get()
		);

		final Map<String, Supplier<SourceTable>> staticSource = Inputs.inputsFor(
			StaticSource.builder().value("a;b").computes(append).build(),
			Map.of()
		);
		assertEquals(
			List.of("static", "staticSeparatorInCell", "staticWide", "empty"),
			new ArrayList<>(staticSource.keySet())
		);
		assertEquals(List.of(List.of("a", "b")), staticSource.get("static").get().getTable());
		assertEquals(SourceTable.ofTable(wide), staticSource.get("staticWide").get());
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
		assertEquals(SourceTable.ofTable(wide), snmp.get("tableWide").get());
		assertEquals(SourceTable.empty(), snmp.get("empty").get());

		// A textUnion gets the synthetic texts of a text source, read as text
		final Map<String, Supplier<SourceTable>> textUnion = Inputs.inputsFor(
			TextUnionSource.builder().texts(List.of("${source::a}")).computes(append).build(),
			Map.of()
		);
		assertEquals(new ArrayList<>(http.keySet()), new ArrayList<>(textUnion.keySet()));
		assertEquals(SourceTable.ofText(Inputs.TEXT), textUnion.get("text").get());
		assertEquals(SourceTable.ofText(""), textUnion.get("empty").get());
	}

	/**
	 * The comparison is exact: the source type, the compute list and every output, a missing stored output and a
	 * stored output no longer generated included.
	 */
	@Test
	void exactComparison(@TempDir final Path directory) throws IOException {
		final Path file = directory.resolve("X.json");
		Files.writeString(file, """
			{"connector":"X","sources":{"s":{"source":"snmpTable","computes":["append"],"outputs":{
			"table":{"table":[["a","b",""]]},
			"text":{"text":"a\\n"},
			"same":{"text":"a\\n"},
			"gone":{"text":""}}}}}
			""");
		final ConnectorGolden generated = golden(
			"commandLine",
			List.of("toTable", "insertColumn"),
			Map.of(
				"table",
				new Output(List.of(List.of("a", "b", "", "")), null, null, null),
				"text",
				new Output(List.of(List.of("a")), null, null, null),
				"same",
				new Output(null, "a\n", null, null),
				"new",
				new Output(null, null, true, null)
			)
		);
		final List<String> differences = ComputeChainGoldenCorpus.diff(file, generated);
		// Source type, chain, table (a trailing empty cell gained), text (now a table), new (not stored), gone
		assertEquals(6, differences.size(), differences.toString());
		for (final String prefix : List.of(
			"s: source type changed \"snmpTable\" -> \"commandLine\"",
			"s: compute chain changed [\"append\"] -> [\"toTable\",\"insertColumn\"]",
			"s [table]: expected {\"table\":[[\"a\",\"b\",\"\"]]} but was {\"table\":[[\"a\",\"b\",\"\",\"\"]]}",
			"s [text]: expected {\"text\":\"a\\n\"} but was {\"table\":[[\"a\"]]}",
			"s [new]: expected null but was ",
			"s [gone]: stored output no longer generated"
		)) {
			assertTrue(differences.stream().anyMatch(d -> d.startsWith(prefix)), prefix + " not in " + differences);
		}
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
			ComputeChainGoldenCorpus.UPDATE_PROPERTY
		)) {
			previous.put(property, System.getProperty(property));
		}
		try {
			// Start from check mode, whatever the Maven command line set
			System.clearProperty(ComputeChainGoldenCorpus.UPDATE_PROPERTY);
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
			// One side per output: commandLine produces a text, the generated toTable makes it a table
			assertTrue(!written.contains("rawData"), written);

			System.clearProperty(ComputeChainGoldenCorpus.UPDATE_PROPERTY);
			assertEquals(0, GoldenCheck.run(List.of(root), true), "the stored golden matches");

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

			// A connector whose file exists but does not parse keeps its golden, even in update mode, and fails. Here the
			// broken file empties the whole store: no golden is deleted, each one is reported
			Files.writeString(root.resolve("Broken.yaml"), "connector: [ not: a valid: connector");
			Files.copy(goldens.resolve("GoldenA.json"), goldens.resolve("Broken.json"));
			System.setProperty(ComputeChainGoldenCorpus.UPDATE_PROPERTY, "true");
			assertEquals(3, GoldenCheck.run(List.of(root), false), "Broken, GoldenA and GoldenB did not load");
			System.clearProperty(ComputeChainGoldenCorpus.UPDATE_PROPERTY);
			assertTrue(Files.exists(goldens.resolve("Broken.json")), "kept by the update mode");
			assertTrue(Files.exists(goldens.resolve("GoldenA.json")), "kept by the update mode");
			Files.delete(root.resolve("Broken.yaml"));
			Files.delete(goldens.resolve("Broken.json"));
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

	/**
	 * A repository is loaded merged with the connector directories of the roots before it (theirs win on a shared
	 * path): its connectors resolve the files they extend there, only its own connectors are kept, and a connector
	 * file of it that does not load, or shares its id with a dependency's connector, is reported (a header file is not
	 * a connector file, and a golden of one is an orphan).
	 */
	@Test
	void dependencyRootsAndUnloadedConnectors() throws IOException {
		final Path base = Path.of("src", "test", "resources", "golden-roots", "base");
		final Path extending = Path.of("src", "test", "resources", "golden-roots", "extending");

		// Alone, the extending repository uses its stale copy of the base header; Broken fails, the header does not
		final LoadedRepository alone = ComputeChainGoldenCorpus.load(Repository.of(extending));
		assertEquals(List.of("BaseConnector", "Child"), List.copyOf(alone.store().getStore().keySet()));
		assertEquals(List.of("Broken"), List.copyOf(alone.unloaded().keySet()));
		assertEquals(
			Set.of("${source::monitors.thing.simple.sources.stale}"),
			ComputeChainGoldenCorpus.generate(alone.store(), "Child").getSources().keySet()
		);

		final List<Repository> repositories = Repository.of(List.of(base, extending));
		assertEquals(List.of(), repositories.get(0).getDependencyDirectories());
		assertEquals(
			List.of(ComputeChainGoldenCorpus.connectorDirectory(base)),
			repositories.get(1).getDependencyDirectories()
		);
		final LoadedRepository baseLoaded = ComputeChainGoldenCorpus.load(repositories.get(0));
		assertEquals(List.of("BaseConnector"), List.copyOf(baseLoaded.store().getStore().keySet()));
		assertEquals(Map.of(), baseLoaded.unloaded());

		// With the base repository: Child loads with the chain of the base header (not the stale copy), and the
		// extending BaseConnector does not load, since the merged tree holds two connectors with that id
		final LoadedRepository loaded = ComputeChainGoldenCorpus.load(repositories.get(1));
		assertEquals(List.of("Child"), List.copyOf(loaded.store().getStore().keySet()));
		assertEquals(
			Set.of("${source::monitors.thing.simple.sources.s}"),
			ComputeChainGoldenCorpus.generate(loaded.store(), "Child").getSources().keySet()
		);
		assertEquals(List.of("BaseConnector", "Broken"), List.copyOf(loaded.unloaded().keySet()));
		assertTrue(loaded.unloaded().get("BaseConnector").contains("BaseConnector.yaml"), loaded.unloaded().toString());
		assertTrue(loaded.unloaded().get("Broken").contains("../../semconv/Missing"), loaded.unloaded().toString());

		// A golden of a header is an orphan; the golden of a connector file that did not load is kept
		assertEquals(List.of("Thing"), ComputeChainGoldenCorpus.orphanGoldens(repositories.get(0), List.of()));
		assertEquals(List.of(), ComputeChainGoldenCorpus.orphanGoldens(repositories.get(1), List.of()));

		// The runner, base: BaseConnector has no stored golden, Thing.json is an orphan. Extending: Child has no stored
		// golden, BaseConnector and Broken did not load (Broken.json kept). Nothing is written or deleted
		final Map<String, String> previous = new HashMap<>();
		for (final String property : List.of(
			ComputeChainGoldenCorpus.CONNECTORS_PROPERTY,
			ComputeChainGoldenCorpus.UPDATE_PROPERTY
		)) {
			previous.put(property, System.getProperty(property));
			System.clearProperty(property);
		}
		try {
			assertEquals(5, GoldenCheck.run(List.of(base, extending), false));
			assertEquals(List.of("Thing.json"), fileNames(ComputeChainGoldenCorpus.goldenDirectory(base)));
			assertEquals(List.of("Broken.json"), fileNames(ComputeChainGoldenCorpus.goldenDirectory(extending)));
		} finally {
			previous.forEach((property, value) -> {
				if (value != null) {
					System.setProperty(property, value);
				}
			});
		}
	}

	private static List<String> fileNames(final Path directory) throws IOException {
		try (Stream<Path> files = Files.list(directory)) {
			return files.map(file -> file.getFileName().toString()).sorted().toList();
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
