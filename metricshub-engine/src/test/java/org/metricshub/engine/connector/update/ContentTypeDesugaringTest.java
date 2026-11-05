package org.metricshub.engine.connector.update;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.metricshub.engine.connector.model.Connector;
import org.metricshub.engine.connector.model.ContentTypeReportEntry;
import org.metricshub.engine.connector.model.ContentTypeReportEntry.Kind;
import org.metricshub.engine.connector.model.common.EmbeddedFile;
import org.metricshub.engine.connector.model.common.ReferenceTranslationTable;
import org.metricshub.engine.connector.model.common.TranslationTable;
import org.metricshub.engine.connector.model.monitor.StandardMonitorJob;
import org.metricshub.engine.connector.model.monitor.task.Discovery;
import org.metricshub.engine.connector.model.monitor.task.source.CommandLineSource;
import org.metricshub.engine.connector.model.monitor.task.source.CopySource;
import org.metricshub.engine.connector.model.monitor.task.source.HttpSource;
import org.metricshub.engine.connector.model.monitor.task.source.SnmpTableSource;
import org.metricshub.engine.connector.model.monitor.task.source.Source;
import org.metricshub.engine.connector.model.monitor.task.source.StaticSource;
import org.metricshub.engine.connector.model.monitor.task.source.TableJoinSource;
import org.metricshub.engine.connector.model.monitor.task.source.compute.Append;
import org.metricshub.engine.connector.model.monitor.task.source.compute.Awk;
import org.metricshub.engine.connector.model.monitor.task.source.compute.Compute;
import org.metricshub.engine.connector.model.monitor.task.source.compute.Json2Csv;
import org.metricshub.engine.connector.model.monitor.task.source.compute.KeepColumns;
import org.metricshub.engine.connector.model.monitor.task.source.compute.Replace;
import org.metricshub.engine.connector.model.monitor.task.source.compute.ToTable;
import org.metricshub.engine.connector.model.monitor.task.source.compute.ToText;
import org.metricshub.engine.connector.model.monitor.task.source.compute.Translate;

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

	private static Source desugar(final Source source) {
		final Connector connector = connectorWith(source);
		new ContentTypeDesugaring().update(connector);
		return source;
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
		desugar(source);
		final List<String> once = types(source);
		desugar(source);
		assertEquals(once, types(source));
		assertEquals(List.of("toText", "awk", "toTable", "keepColumns"), once);
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
