package org.metricshub.engine.connector.update;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.metricshub.engine.connector.model.Connector;
import org.metricshub.engine.connector.model.ContentTypeReportEntry;
import org.metricshub.engine.connector.model.ContentTypeReportEntry.Kind;
import org.metricshub.engine.connector.model.common.ContentType;
import org.metricshub.engine.connector.model.common.EmbeddedFile;
import org.metricshub.engine.connector.model.common.ReferenceTranslationTable;
import org.metricshub.engine.connector.model.common.TranslationTable;
import org.metricshub.engine.connector.model.monitor.StandardMonitorJob;
import org.metricshub.engine.connector.model.common.ExecuteForEachEntryOf;
import org.metricshub.engine.connector.model.monitor.task.Discovery;
import org.metricshub.engine.connector.model.monitor.task.Mapping;
import org.metricshub.engine.connector.model.monitor.task.source.CommandLineSource;
import org.metricshub.engine.connector.model.monitor.task.source.CopySource;
import org.metricshub.engine.connector.model.monitor.task.source.HttpSource;
import org.metricshub.engine.connector.model.monitor.task.source.IpmiRawSource;
import org.metricshub.engine.connector.model.monitor.task.source.IpmiSource;
import org.metricshub.engine.connector.model.monitor.task.source.JawkSource;
import org.metricshub.engine.connector.model.monitor.task.source.SnmpTableSource;
import org.metricshub.engine.connector.model.monitor.task.source.Source;
import org.metricshub.engine.connector.model.monitor.task.source.StaticSource;
import org.metricshub.engine.connector.model.monitor.task.source.TableJoinSource;
import org.metricshub.engine.connector.model.monitor.task.source.TableUnionSource;
import org.metricshub.engine.connector.model.monitor.task.source.compute.Append;
import org.metricshub.engine.connector.model.monitor.task.source.compute.Awk;
import org.metricshub.engine.connector.model.monitor.task.source.compute.Compute;
import org.metricshub.engine.connector.model.monitor.task.source.compute.Json2Csv;
import org.metricshub.engine.connector.model.monitor.task.source.compute.KeepColumns;
import org.metricshub.engine.connector.model.monitor.task.source.compute.Replace;
import org.metricshub.engine.connector.model.monitor.task.source.compute.ToTable;
import org.metricshub.engine.connector.model.monitor.task.source.compute.ToText;
import org.metricshub.engine.connector.model.monitor.task.source.compute.Translate;
import org.metricshub.engine.connector.model.tool.ConnectorTool;
import org.metricshub.engine.connector.model.tool.ToolResult;
import org.metricshub.engine.strategy.source.SourceTable;
import org.metricshub.engine.strategy.source.compute.ComputeProcessor;

class ContentTypeDesugaringTest {

	private static final String KEY = "${source::monitors.enclosure.discovery.sources.source(1)}";

	/**
	 * A connector holding one discovery source under the given key.
	 */
	private static Connector connectorWith(final Source source) {
		source.setKey(KEY);
		final Map<String, Source> sources = new LinkedHashMap<>();
		sources.put("source(1)", source);
		final Connector connector = Connector.builder().build();
		connector.getMonitors()
			.put(
				"enclosure",
				StandardMonitorJob.standardBuilder().discovery(Discovery.builder().sources(sources).build()).build()
			);
		return connector;
	}

	/**
	 * A connector holding two discovery sources, the first under {@link #KEY}, the second as source(2), and the
	 * given mapping (null for none).
	 */
	private static Connector connectorWith(final Source first, final Source second, final Mapping mapping) {
		first.setKey(KEY);
		second.setKey("${source::monitors.enclosure.discovery.sources.source(2)}");
		final Map<String, Source> sources = new LinkedHashMap<>();
		sources.put("source(1)", first);
		sources.put("source(2)", second);
		final Connector connector = Connector.builder().build();
		connector.getMonitors()
			.put(
				"enclosure",
				StandardMonitorJob.standardBuilder()
					.discovery(Discovery.builder().sources(sources).mapping(mapping).build())
					.build()
			);
		return connector;
	}

	private static List<String> types(final Source source) {
		return source.getComputes().stream().map(Compute::getType).collect(Collectors.toList());
	}

	private static List<Kind> kinds(final Connector connector) {
		return connector.getContentTypeReport().stream().map(ContentTypeReportEntry::kind).collect(Collectors.toList());
	}

	private static Map<String, String> orderedTranslations() {
		final Map<String, String> translations = new LinkedHashMap<>();
		translations.put("ok", "OK;1");
		translations.put("default", "line1\nline2");
		return translations;
	}

	@Test
	void tableComputeAfterCommandLineGetsImplicitToTable() {
		final Source source = CommandLineSource.builder()
			.commandLine("ls")
			.computes(List.of(KeepColumns.builder().type("keepColumns").columnNumbers("1").build()))
			.build();
		final Connector connector = connectorWith(source);
		new ContentTypeDesugaring().update(connector);

		assertEquals(List.of("toTable", "keepColumns"), types(source));
		assertTrue(((ToTable) source.getComputes().get(0)).isImplicit());
		assertEquals(List.of(Kind.TO_TABLE_INSERTED), kinds(connector));
		assertEquals(KEY, connector.getContentTypeReport().get(0).sourceKey());
	}

	@Test
	void commandLineWithoutComputeGetsImplicitToTableAppended() {
		final Source source = CommandLineSource.builder().commandLine("ls").build();
		final Connector connector = connectorWith(source);
		new ContentTypeDesugaring().update(connector);

		assertEquals(List.of("toTable"), types(source));
		assertEquals(List.of(Kind.TO_TABLE_APPENDED), kinds(connector));
	}

	/**
	 * A text source read only through copies (the copies run their own awk) keeps its text: no toTable is appended
	 * to it and nothing is reported for it; the copy, mapped, gets the conversion.
	 */
	@Test
	void textSourceReadThroughCopiesOnlyKeepsItsText() {
		final Source snapshot = CommandLineSource.builder().commandLine("cat snapshot").build();
		final Source copy = CopySource.builder()
			.from(KEY)
			.computes(List.of(Awk.builder().type("awk").script("/^##CPU##$/ { print }").build()))
			.build();
		final Connector connector = connectorWith(snapshot, copy, null);
		new ContentTypeDesugaring().update(connector);

		assertEquals(List.of(), types(snapshot));
		assertEquals(List.of("awk", "toTable"), types(copy));
		assertEquals(List.of(Kind.TO_TABLE_APPENDED), kinds(connector));
		assertEquals(copy.getKey(), connector.getContentTypeReport().get(0).sourceKey());
	}

	/**
	 * A source copied and iterated by another source's executeForEachEntryOf is read as a table: it gets the
	 * toTable appended.
	 */
	@Test
	void textSourceCopiedAndIteratedGetsItsToTable() {
		final Source snapshot = CommandLineSource.builder().commandLine("cat snapshot").build();
		final Source iterating = CopySource.builder()
			.from("${source::monitors.enclosure.discovery.sources.other}")
			.computes(List.of(Awk.builder().type("awk").script("{ print }").build()))
			.build();
		iterating.setExecuteForEachEntryOf(ExecuteForEachEntryOf.builder().source(KEY).build());
		final Connector connector = connectorWith(snapshot, iterating, null);
		new ContentTypeDesugaring().update(connector);

		assertEquals(List.of("toTable"), types(snapshot));
		assertEquals(
			List.of(Kind.TO_TABLE_APPENDED),
			connector.getContentTypeReport()
				.stream()
				.filter(entry -> KEY.equals(entry.sourceKey()))
				.map(ContentTypeReportEntry::kind)
				.toList()
		);
	}

	/**
	 * A source copied and referenced by an awk source's variables is read as a table (the variable is exposed as
	 * the source's table): it gets the toTable appended.
	 */
	@Test
	void textSourceCopiedAndUsedAsAwkSourceVariableGetsItsToTable() {
		final Source snapshot = CommandLineSource.builder().commandLine("cat snapshot").build();
		final JawkSource awkSource = JawkSource.builder().script("BEGIN { print length(rows) }").build();
		awkSource.getVariables().put("rows", KEY);
		final Source copy = CopySource.builder().from(KEY).build();
		final Connector connector = connectorWith(snapshot, awkSource, null);
		copy.setKey("${source::monitors.enclosure.discovery.sources.copy}");
		((StandardMonitorJob) connector.getMonitors().get("enclosure")).getDiscovery().getSources().put("copy", copy);
		new ContentTypeDesugaring().update(connector);

		assertEquals(List.of("toTable"), types(snapshot));
		assertEquals(
			List.of(Kind.TO_TABLE_APPENDED),
			connector.getContentTypeReport()
				.stream()
				.filter(entry -> KEY.equals(entry.sourceKey()))
				.map(ContentTypeReportEntry::kind)
				.toList()
		);
	}

	/**
	 * A source copied and listed in the tables of a union is read as a table: the generic scan finds the reference
	 * in any field, here one that the source's update() does not visit.
	 */
	@Test
	void textSourceCopiedAndInATableUnionGetsItsToTable() {
		final Source snapshot = CommandLineSource.builder().commandLine("cat snapshot").build();
		final Source union = TableUnionSource.builder().tables(new ArrayList<>(List.of(KEY))).build();
		final Source copy = CopySource.builder().from(KEY).build();
		final Connector connector = connectorWith(snapshot, union, null);
		copy.setKey("${source::monitors.enclosure.discovery.sources.copy}");
		((StandardMonitorJob) connector.getMonitors().get("enclosure")).getDiscovery().getSources().put("copy", copy);
		new ContentTypeDesugaring().update(connector);

		assertEquals(List.of("toTable"), types(snapshot));
	}

	/**
	 * The same source mapped as well is read as a table: it gets the toTable appended.
	 */
	@Test
	void textSourceCopiedAndMappedGetsItsToTable() {
		final Source snapshot = CommandLineSource.builder().commandLine("cat snapshot").build();
		final Source copy = CopySource.builder()
			.from(KEY)
			.computes(List.of(Awk.builder().type("awk").script("{ print }").build()))
			.build();
		final Connector connector = connectorWith(snapshot, copy, Mapping.builder().source(KEY).build());
		new ContentTypeDesugaring().update(connector);

		assertEquals(List.of("toTable"), types(snapshot));
		assertEquals(List.of("awk", "toTable"), types(copy));
		assertEquals(List.of(Kind.TO_TABLE_APPENDED, Kind.TO_TABLE_APPENDED), kinds(connector));
	}

	@Test
	void awkAfterCommandLineNeedsNothingButTheFinalToTable() {
		final Source source = CommandLineSource.builder()
			.commandLine("ls")
			.computes(List.of(Awk.builder().type("awk").script("{ print }").build()))
			.build();
		final Connector connector = connectorWith(source);
		new ContentTypeDesugaring().update(connector);

		assertEquals(List.of("awk", "toTable"), types(source));
		assertEquals(List.of(Kind.TO_TABLE_APPENDED), kinds(connector));
	}

	@Test
	void awkAfterTableSourceGetsImplicitToTextAndIsReported() {
		final Source source = SnmpTableSource.builder()
			.oid("1.3")
			.selectColumns("ID,1")
			.computes(
				List.of(
					Awk.builder().type("awk").script("${file::1}").build(),
					KeepColumns.builder().type("keepColumns").columnNumbers("1").build()
				)
			)
			.build();
		final Connector connector = connectorWith(source);
		connector.getEmbeddedFiles()
			.put(1, EmbeddedFile.builder().id(1).content("BEGIN { FS = \";\" }\n{ print $1 }".getBytes()).build());
		new ContentTypeDesugaring().update(connector);

		// toText before the awk (fed a table), toTable after it (text into a table compute)
		assertEquals(List.of("toText", "awk", "toTable", "keepColumns"), types(source));
		assertTrue(((ToText) source.getComputes().get(0)).isImplicit());
		assertEquals(List.of(Kind.TO_TEXT_INSERTED, Kind.AWK_ON_TABLE, Kind.TO_TABLE_INSERTED), kinds(connector));
		assertTrue(connector.getContentTypeReport().get(1).detail().contains("sets FS to \";\""));
	}

	@Test
	void tableComputeAfterPureTextSourceIsReportedNotConverted() {
		final Source source = HttpSource.builder()
			.url("/")
			.computes(
				List.of(
					KeepColumns.builder().type("keepColumns").columnNumbers("1").build(),
					Awk.builder().type("awk").script("{ print }").build()
				)
			)
			.build();
		final Connector connector = connectorWith(source);
		new ContentTypeDesugaring().update(connector);

		// No toTable before keepColumns (it finds an empty table today); toText before the awk fed by its table;
		// nothing appended: the awk compute ends the chain in text but the list ends with... a legacy dual producer
		assertEquals(List.of("keepColumns", "toText", "awk", "toTable"), types(source));
		assertEquals(
			List.of(Kind.TABLE_COMPUTE_ON_TEXT_SOURCE, Kind.TO_TEXT_INSERTED, Kind.AWK_ON_TABLE, Kind.TO_TABLE_APPENDED),
			kinds(connector)
		);
	}

	@Test
	void json2CsvAfterTextSourceNeedsNothingAndAfterTableSourceIsReported() {
		final Source http = HttpSource.builder()
			.url("/")
			.computes(List.of(Json2Csv.builder().type("json2Csv").properties("/a").build()))
			.build();
		Connector connector = connectorWith(http);
		new ContentTypeDesugaring().update(connector);
		assertEquals(List.of("json2Csv"), types(http));
		assertTrue(connector.getContentTypeReport().isEmpty());

		final Source copy = CopySource.builder()
			.from(KEY)
			.computes(List.of(Json2Csv.builder().type("json2Csv").properties("/a").build()))
			.build();
		connector = connectorWith(copy);
		new ContentTypeDesugaring().update(connector);
		assertEquals(List.of("json2Csv"), types(copy));
		assertEquals(List.of(Kind.TEXT_COMPUTE_ON_TABLE_SOURCE), kinds(connector));
	}

	/**
	 * A copy source produces what its origin produces: a copy of an http source is text (nothing before json2Csv,
	 * a table compute reported), a copy of a chain ending with an awk is a legacy dual text (toTable inserted).
	 */
	@Test
	void copySourceTakesTheTypeOfItsOrigin() {
		final Source http = HttpSource.builder().url("/").build();
		http.setKey(KEY);
		final Source copyToJson = CopySource.builder()
			.from(KEY)
			.computes(List.of(Json2Csv.builder().type("json2Csv").properties("/a").build()))
			.build();
		copyToJson.setKey("${source::monitors.enclosure.discovery.sources.json}");
		final Source copyToTable = CopySource.builder()
			.from(KEY)
			.computes(List.of(KeepColumns.builder().type("keepColumns").columnNumbers("1").build()))
			.build();
		copyToTable.setKey("${source::monitors.enclosure.discovery.sources.table}");
		final Source awkChain = SnmpTableSource.builder()
			.oid("1.3")
			.selectColumns("ID,1")
			.computes(List.of(Awk.builder().type("awk").script("{ print }").build()))
			.build();
		awkChain.setKey("${source::monitors.enclosure.discovery.sources.awk}");
		final Source copyOfAwk = CopySource.builder()
			.from(awkChain.getKey())
			.computes(List.of(KeepColumns.builder().type("keepColumns").columnNumbers("1").build()))
			.build();
		copyOfAwk.setKey("${source::monitors.enclosure.discovery.sources.copyOfAwk}");

		final Map<String, Source> sources = new LinkedHashMap<>();
		sources.put("source(1)", http);
		sources.put("json", copyToJson);
		sources.put("table", copyToTable);
		sources.put("awk", awkChain);
		sources.put("copyOfAwk", copyOfAwk);
		final Connector connector = Connector.builder().build();
		connector.getMonitors()
			.put(
				"enclosure",
				StandardMonitorJob.standardBuilder().discovery(Discovery.builder().sources(sources).build()).build()
			);
		new ContentTypeDesugaring().update(connector);

		assertEquals(List.of("json2Csv"), types(copyToJson));
		assertEquals(List.of("keepColumns"), types(copyToTable));
		assertEquals(List.of("toTable", "keepColumns"), types(copyOfAwk));
		assertEquals(
			// The awk chain is read through its copy only: it keeps its text, no toTable is appended to it
			List.of(Kind.TABLE_COMPUTE_ON_TEXT_SOURCE, Kind.TO_TEXT_INSERTED, Kind.AWK_ON_TABLE, Kind.TO_TABLE_INSERTED),
			kinds(connector)
		);
	}

	/**
	 * A chain of copies of any depth resolves to its origin; a cycle of copies leaves each copy to its declared
	 * table.
	 */
	@Test
	void copyChainsResolveWithoutDepthLimitAndCyclesEnd() {
		final Map<String, Source> sources = new LinkedHashMap<>();
		final Source http = HttpSource.builder().url("/").build();
		http.setKey(KEY);
		sources.put("source(1)", http);
		String previous = KEY;
		Source last = null;
		for (int i = 0; i < 10; i++) {
			last = CopySource.builder().from(previous).build();
			last.setKey("${source::monitors.enclosure.discovery.sources.copy" + i + "}");
			sources.put("copy" + i, last);
			previous = last.getKey();
		}
		last.setComputes(List.of(KeepColumns.builder().type("keepColumns").columnNumbers("1").build()));
		final Source loopA = CopySource.builder()
			.from("${source::monitors.enclosure.discovery.sources.loopB}")
			.computes(List.of(Awk.builder().type("awk").script("{ print }").build()))
			.build();
		loopA.setKey("${source::monitors.enclosure.discovery.sources.loopA}");
		final Source loopB = CopySource.builder()
			.from("${source::monitors.enclosure.discovery.sources.loopA}")
			.computes(List.of(Awk.builder().type("awk").script("{ print }").build()))
			.build();
		loopB.setKey("${source::monitors.enclosure.discovery.sources.loopB}");
		sources.put("loopA", loopA);
		sources.put("loopB", loopB);
		final Connector connector = Connector.builder().build();
		connector.getMonitors()
			.put(
				"enclosure",
				StandardMonitorJob.standardBuilder().discovery(Discovery.builder().sources(sources).build()).build()
			);
		new ContentTypeDesugaring().update(connector);

		// The tenth copy is still the http text: the table compute is reported, nothing inserted
		assertEquals(List.of("keepColumns"), types(last));
		// The cycle ends where it closes: each copy sees the text its peer's awk produces, and each one is read
		// through its peer's copy only, so neither gets a toTable appended
		assertEquals(List.of("awk"), types(loopA));
		assertEquals(List.of("awk"), types(loopB));
		assertEquals(1, kinds(connector).stream().filter(Kind.TABLE_COMPUTE_ON_TEXT_SOURCE::equals).count());
	}

	@Test
	void legacyIdiomsAreReported() {
		final TranslationTable table = TranslationTable.builder().translations(orderedTranslations()).build();
		final Source source = SnmpTableSource.builder()
			.oid("1.3")
			.selectColumns("ID,1")
			.computes(
				List.of(
					Append.builder().type("append").column(1).value("MSHW;").build(),
					Append.builder().type("append").column(1).value("$2").build(),
					Replace.builder().type("replace").column(1).existingValue("a").newValue("b\nc").build(),
					Translate.builder().type("translate").column(1).translationTable(table).build(),
					Translate.builder()
						.type("translate")
						.column(1)
						.translationTable(new ReferenceTranslationTable("${translation::statuses}"))
						.build()
				)
			)
			.build();
		final Connector connector = connectorWith(source);
		connector.getTranslations().put("statuses", table);
		new ContentTypeDesugaring().update(connector);

		assertEquals(
			List.of(
				Kind.SEPARATOR_IN_VALUE,
				Kind.LINE_FEED_IN_VALUE,
				Kind.SEPARATOR_IN_VALUE,
				Kind.LINE_FEED_IN_VALUE,
				Kind.SEPARATOR_IN_VALUE,
				Kind.LINE_FEED_IN_VALUE
			),
			kinds(connector)
		);
		assertEquals("append #0 value='MSHW;'", connector.getContentTypeReport().get(0).detail());
		// Nothing inserted: every compute is table to table
		assertEquals(5, source.getComputes().size());
	}

	@Test
	void sourceIdiomsAreReported() {
		Connector connector = connectorWith(
			TableJoinSource.builder()
				.leftTable("a")
				.rightTable("b")
				.leftKeyColumn(1)
				.rightKeyColumn(1)
				.defaultRightLine(";;")
				.build()
		);
		new ContentTypeDesugaring().update(connector);
		assertEquals(List.of(Kind.DEFAULT_RIGHT_LINE), kinds(connector));

		connector = connectorWith(StaticSource.builder().value("DDN;Data Direct Networks;Disk Array").build());
		new ContentTypeDesugaring().update(connector);
		assertEquals(List.of(Kind.STATIC_CSV), kinds(connector));

		connector = connectorWith(StaticSource.builder().value("single").build());
		new ContentTypeDesugaring().update(connector);
		assertTrue(connector.getContentTypeReport().isEmpty());

		// The list forms are not reported, even with a ';' in a cell
		connector = connectorWith(
			TableJoinSource.builder()
				.leftTable("a")
				.rightTable("b")
				.leftKeyColumn(1)
				.rightKeyColumn(1)
				.defaultRightLine(List.of("", "a;b"))
				.build()
		);
		new ContentTypeDesugaring().update(connector);
		assertTrue(connector.getContentTypeReport().isEmpty());

		connector = connectorWith(
			StaticSource.builder().value(List.of("DDN", "Data Direct Networks", "Disk;Array")).build()
		);
		new ContentTypeDesugaring().update(connector);
		assertTrue(connector.getContentTypeReport().isEmpty());
	}

	@Test
	void desugaringIsIdempotent() {
		final Source source = SnmpTableSource.builder()
			.oid("1.3")
			.selectColumns("ID,1")
			.computes(
				List.of(
					Awk.builder().type("awk").script("{ print }").build(),
					KeepColumns.builder().type("keepColumns").columnNumbers("1").build()
				)
			)
			.build();
		final Connector connector = connectorWith(source);
		new ContentTypeDesugaring().update(connector);
		final List<String> once = types(source);
		final List<ContentTypeReportEntry> report = List.copyOf(connector.getContentTypeReport());
		new ContentTypeDesugaring().update(connector);
		assertEquals(once, types(source));
		assertEquals(List.of("toText", "awk", "toTable", "keepColumns"), once);
		// The report is rebuilt from the chain as written, so a second pass loses no finding
		assertEquals(report, connector.getContentTypeReport());
		assertEquals(List.of(Kind.TO_TEXT_INSERTED, Kind.AWK_ON_TABLE, Kind.TO_TABLE_INSERTED), kinds(connector));
	}

	/**
	 * An explicit toText leaves text only, so the table compute that follows gets a real toTable, not the no-op
	 * implicit one: the chain keeps its rows end to end.
	 */
	@Test
	void tableComputeAfterExplicitToTextGetsAnExecutableToTable() {
		final Source source = SnmpTableSource.builder()
			.oid("1.3")
			.selectColumns("ID,1")
			.computes(
				List.of(
					ToText.builder().type("toText").build(),
					KeepColumns.builder().type("keepColumns").columnNumbers("2").build()
				)
			)
			.build();
		final Connector connector = connectorWith(source);
		new ContentTypeDesugaring().update(connector);

		assertEquals(List.of("toText", "toTable", "keepColumns"), types(source));
		assertFalse(((ToTable) source.getComputes().get(1)).isImplicit());
		assertTrue(((ToTable) source.getComputes().get(1)).isGenerated());
		assertEquals(List.of(Kind.TO_TABLE_INSERTED), kinds(connector));

		// A second pass starts again from what is written: the same chain, the same finding
		new ContentTypeDesugaring().update(connector);
		assertEquals(List.of("toText", "toTable", "keepColumns"), types(source));
		assertEquals(List.of(Kind.TO_TABLE_INSERTED), kinds(connector));

		// Run the desugared chain: the rows survive, with "," for ";" inside a cell as toText serializes
		final SourceTable sourceTable = SourceTable.ofTable(
			new ArrayList<>(List.of(new ArrayList<>(List.of("a;b", "c")), new ArrayList<>(List.of("d", "e"))))
		);
		final ComputeProcessor processor = ComputeProcessor.builder().hostname("host").sourceTable(sourceTable).build();
		source.getComputes().forEach(compute -> compute.accept(processor));
		assertEquals(List.of(List.of("c"), List.of("e")), sourceTable.getTable());
	}

	@Test
	void toolSourcesAreDesugaredToo() {
		final Source source = CommandLineSource.builder()
			.commandLine("ls")
			.computes(List.of(KeepColumns.builder().type("keepColumns").columnNumbers("1").build()))
			.build();
		source.setKey("${source::tools.list.sources.raw}");
		final Map<String, Source> sources = new LinkedHashMap<>();
		sources.put("raw", source);
		final Connector connector = Connector.builder().build();
		connector.getTools().put("list", ConnectorTool.builder().sources(sources).build());
		new ContentTypeDesugaring().update(connector);

		assertEquals(List.of("toTable", "keepColumns"), types(source));
		assertEquals(List.of(Kind.TO_TABLE_INSERTED), kinds(connector));
		assertEquals("${source::tools.list.sources.raw}", connector.getContentTypeReport().get(0).sourceKey());
	}

	/**
	 * A tool without columns returns the text of a result source that ends in text (a command output): no toTable is
	 * appended, nothing is reported. With columns, the tool returns a table: the toTable is appended as before.
	 */
	@Test
	void toolWithoutColumnsReturnsText() {
		final Source ping = CommandLineSource.builder().commandLine("ping -c 4 host").build();
		ping.setKey("${source::tools.ping.sources.ping}");
		final Map<String, Source> pingSources = new LinkedHashMap<>();
		pingSources.put("ping", ping);
		final ConnectorTool pingTool = ConnectorTool.builder()
			.sources(pingSources)
			.result(new ToolResult("${source::ping}", null))
			.build();

		final Source processes = CommandLineSource.builder().commandLine("ps").build();
		processes.setKey("${source::tools.processes.sources.ps}");
		final Map<String, Source> processSources = new LinkedHashMap<>();
		processSources.put("ps", processes);
		final ConnectorTool processTool = ConnectorTool.builder()
			.sources(processSources)
			.result(new ToolResult("${source::ps}", List.of("pid", "name")))
			.build();

		final Connector connector = Connector.builder().build();
		connector.getTools().put("ping", pingTool);
		connector.getTools().put("processes", processTool);
		new ContentTypeDesugaring().update(connector);

		assertEquals(List.of(), types(ping));
		assertTrue(pingTool.isTextResult());
		assertEquals(List.of("toTable"), types(processes));
		assertTrue(!processTool.isTextResult());
		assertEquals(List.of(Kind.TO_TABLE_APPENDED), kinds(connector));
		assertEquals("${source::tools.processes.sources.ps}", connector.getContentTypeReport().get(0).sourceKey());
	}

	/**
	 * A tool without columns returning a union of two command outputs returns their joined text: the commands keep
	 * their text, nothing is reported.
	 */
	@Test
	void toolWithoutColumnsReturnsTheTextOfAUnionOfCommands() {
		final Source link = CommandLineSource.builder().commandLine("ip link").build();
		link.setKey("${source::tools.details.sources.link}");
		final Source addr = CommandLineSource.builder().commandLine("ip addr").build();
		addr.setKey("${source::tools.details.sources.addr}");
		final Source union = TableUnionSource.builder()
			.tables(new ArrayList<>(List.of("${source::link}", "${source::addr}")))
			.build();
		union.setKey("${source::tools.details.sources.all}");
		final Map<String, Source> sources = new LinkedHashMap<>();
		sources.put("link", link);
		sources.put("addr", addr);
		sources.put("all", union);
		final ConnectorTool tool = ConnectorTool.builder()
			.sources(sources)
			.result(new ToolResult("${source::all}", null))
			.build();
		final Connector connector = Connector.builder().build();
		connector.getTools().put("details", tool);
		new ContentTypeDesugaring().update(connector);

		assertEquals(List.of(), types(link));
		assertEquals(List.of(), types(addr));
		assertTrue(tool.isTextResult());
		assertEquals(List.of(), kinds(connector));
	}

	/**
	 * ipmiRaw produces a table (one row holding the request and the response), not text: a table compute after it
	 * needs no conversion and is not reported.
	 */
	/**
	 * The ipmi source gives the ipmi tool output as text: an awk after it reads that text, nothing is inserted.
	 */
	@Test
	void ipmiIsATextProducer() {
		final Source source = IpmiSource.builder()
			.computes(List.of(Awk.builder().type("awk").script("{ print }").build()))
			.build();
		assertEquals(ContentType.TEXT, source.getOutputContentType());
		final Connector connector = connectorWith(source);
		new ContentTypeDesugaring().update(connector);
		assertEquals(List.of("awk", "toTable"), types(source));
		assertEquals(List.of(Kind.TO_TABLE_APPENDED), kinds(connector));
	}

	@Test
	void ipmiRawIsATableProducer() {
		final Source source = IpmiRawSource.builder()
			.netFn("0x06")
			.command("0x01")
			.computes(List.of(KeepColumns.builder().type("keepColumns").columnNumbers("2").build()))
			.build();
		assertEquals(ContentType.TABLE, source.getOutputContentType());
		final Connector connector = connectorWith(source);
		new ContentTypeDesugaring().update(connector);
		assertEquals(List.of("keepColumns"), types(source));
		assertTrue(connector.getContentTypeReport().isEmpty());
	}

	/**
	 * An awk compute declaring <code>input: table</code> reads the table itself: no toText is inserted, and its
	 * <code>output: table</code> ends the chain in a table, so nothing is appended either. A printf in a script
	 * producing a table is reported.
	 */
	/**
	 * An awk reading a table after a pure text source gets a real toTable: it opted into a table, which the source
	 * does not produce.
	 */
	@Test
	void awkWithTableInputAfterPureTextSourceGetsARealToTable() {
		final Source source = HttpSource.builder()
			.url("/")
			.computes(List.of(Awk.builder().type("awk").script("{ print $1 }").input(ContentType.TABLE).build()))
			.build();
		final Connector connector = connectorWith(source);
		new ContentTypeDesugaring().update(connector);

		// The awk still writes text: the chain ends with the usual implicit toTable
		assertEquals(List.of("toTable", "awk", "toTable"), types(source));
		assertFalse(((ToTable) source.getComputes().get(0)).isImplicit());
		assertEquals(List.of(Kind.TO_TABLE_INSERTED, Kind.TO_TABLE_APPENDED), kinds(connector));
	}

	@Test
	void awkWithTableInputAndOutputNeedsNoConversion() {
		final Source source = SnmpTableSource.builder()
			.oid("1.3")
			.selectColumns("ID,1")
			.computes(
				List.of(
					Awk.builder()
						.type("awk")
						.script("{ printf \"%s\", $1 }")
						.input(ContentType.TABLE)
						.output(ContentType.TABLE)
						.build(),
					KeepColumns.builder().type("keepColumns").columnNumbers("1").build()
				)
			)
			.build();
		final Connector connector = connectorWith(source);
		new ContentTypeDesugaring().update(connector);

		assertEquals(List.of("awk", "keepColumns"), types(source));
		assertEquals(List.of(Kind.PRINTF_IN_TABLE_OUTPUT), kinds(connector));

		// The word in a comment or a string literal is not a printf statement
		final Source noPrintf = SnmpTableSource.builder()
			.oid("1.3")
			.selectColumns("ID,1")
			.computes(
				List.of(
					Awk.builder()
						.type("awk")
						.script("# avoid printf here\n{ print \"printf\", \"a \\\"quoted\\\" printf\" } # printf")
						.input(ContentType.TABLE)
						.output(ContentType.TABLE)
						.build()
				)
			)
			.build();
		final Connector noPrintfConnector = connectorWith(noPrintf);
		new ContentTypeDesugaring().update(noPrintfConnector);
		assertTrue(noPrintfConnector.getContentTypeReport().isEmpty());

		// A "#" inside a regex literal does not start a comment: the printf after it is seen
		final Source regexPrintf = SnmpTableSource.builder()
			.oid("1.3")
			.selectColumns("ID,1")
			.computes(
				List.of(
					Awk.builder()
						.type("awk")
						.script("/foo#bar/ { printf \"%s\", $1 }\n{ x = a / b / 2 }")
						.input(ContentType.TABLE)
						.output(ContentType.TABLE)
						.build()
				)
			)
			.build();
		final Connector regexPrintfConnector = connectorWith(regexPrintf);
		new ContentTypeDesugaring().update(regexPrintfConnector);
		assertEquals(List.of(Kind.PRINTF_IN_TABLE_OUTPUT), kinds(regexPrintfConnector));
		final String code = ContentTypeDesugaring.code("/foo#bar/ { printf \"%s\", $1 }\n{ x = a / b / 2 }");
		// The pattern of a rule after the action of the previous one is a regex too
		final String nextRule = ContentTypeDesugaring.code("/foo/ { print }\n/printf/ { print }");
		assertFalse(nextRule.contains("printf"), nextRule);
		// ... and after a pattern-only rule
		final String patternOnly = ContentTypeDesugaring.code("/foo/\n/printf/\n$1 == \"x\"\n/printf/");
		assertFalse(patternOnly.contains("printf"), patternOnly);
		// A typed regexp (@/re/) is a literal: its "#" does not start a comment
		final String typed = ContentTypeDesugaring.code("BEGIN { r = @/foo#bar/; printf \"%s\", $1 }");
		assertFalse(typed.contains("foo#bar"), typed);
		assertTrue(typed.contains("printf"), typed);
		// ... and after an arithmetic or comparison operator
		final String operand = ContentTypeDesugaring.code(
			"BEGIN { x = 1 + /printf/; y = a * /printf/ - /printf/ % /printf/; z = b < /printf/ }"
		);
		assertFalse(operand.contains("printf"), operand);

		// The same lint applies to an awk source writing a table, inline or from an embedded file
		final Source inlineSource = JawkSource.builder().output(ContentType.TABLE).script("{ printf \"%s\", $1 }").build();
		final Connector inlineConnector = connectorWith(inlineSource);
		new ContentTypeDesugaring().update(inlineConnector);
		assertEquals(List.of(Kind.PRINTF_IN_TABLE_OUTPUT), kinds(inlineConnector));
		final Source fileSource = JawkSource.builder().output(ContentType.TABLE).script("${file::1}").build();
		final Connector fileConnector = connectorWith(fileSource);
		fileConnector.getEmbeddedFiles().put(1, EmbeddedFile.builder().id(1).content("{ printf $1 }".getBytes()).build());
		new ContentTypeDesugaring().update(fileConnector);
		assertEquals(List.of(Kind.PRINTF_IN_TABLE_OUTPUT), kinds(fileConnector));
		// Not for a text output (a legacy dual producer: only the usual toTable appended)
		final Source textSource = JawkSource.builder().script("{ printf \"%s\", $1 }").build();
		final Connector textConnector = connectorWith(textSource);
		new ContentTypeDesugaring().update(textConnector);
		assertEquals(List.of(Kind.TO_TABLE_APPENDED), kinds(textConnector));
		// A regex operand after a keyword is a literal too
		final String afterKeyword = ContentTypeDesugaring.code("{ print /printf/; return /foo#bar/; printf \"%s\", $1 }");
		assertFalse(afterKeyword.contains("/printf/"), afterKeyword);
		assertFalse(afterKeyword.contains("foo#bar"), afterKeyword);
		assertTrue(afterKeyword.endsWith("printf  , $1 }"), afterKeyword);
		assertTrue(code.contains("printf"), code);
		assertFalse(code.contains("foo#bar"), code);
		assertTrue(code.contains("a / b / 2"), code);

		// Text output after a table input: the following table compute needs a toTable, and the chain ends typed
		final Source textOut = CommandLineSource.builder()
			.commandLine("ls")
			.computes(List.of(Awk.builder().type("awk").script("{ print $1 }").input(ContentType.TABLE).build()))
			.build();
		final Connector textOutConnector = connectorWith(textOut);
		new ContentTypeDesugaring().update(textOutConnector);
		assertEquals(List.of("toTable", "awk", "toTable"), types(textOut));
		assertEquals(List.of(Kind.TO_TABLE_INSERTED, Kind.TO_TABLE_APPENDED), kinds(textOutConnector));
	}

	@Test
	void implicitFlagIsNotPartOfEqualityNorYaml() {
		assertEquals(ToTable.implicit(), ToTable.builder().type("toTable").build());
		assertEquals(ToText.implicit(), ToText.builder().type("toText").build());
		assertFalse(ToTable.builder().type("toTable").build().isImplicit());
		assertTrue(ToTable.implicit().copy().isImplicit());
		assertTrue(ToText.implicit().copy().isImplicit());
	}
}
