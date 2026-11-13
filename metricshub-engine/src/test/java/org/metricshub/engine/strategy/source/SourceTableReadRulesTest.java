package org.metricshub.engine.strategy.source;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.metricshub.engine.common.helpers.MetricsHubConstants.EMPTY;
import static org.metricshub.engine.constants.Constants.LOCALHOST;
import static org.metricshub.engine.constants.Constants.MY_CONNECTOR_1_NAME;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.metricshub.engine.awk.AwkVariableHelper;
import org.metricshub.engine.client.ClientsExecutor;
import org.metricshub.engine.configuration.HostConfiguration;
import org.metricshub.engine.connector.model.common.ContentType;
import org.metricshub.engine.connector.model.common.CustomConcatMethod;
import org.metricshub.engine.connector.model.common.DeviceKind;
import org.metricshub.engine.connector.model.common.EntryConcatMethod;
import org.metricshub.engine.connector.model.common.ExecuteForEachEntryOf;
import org.metricshub.engine.connector.model.common.IEntryConcatMethod;
import org.metricshub.engine.connector.model.common.TranslationTable;
import org.metricshub.engine.connector.model.monitor.task.source.CopySource;
import org.metricshub.engine.connector.model.monitor.task.source.HttpSource;
import org.metricshub.engine.connector.model.monitor.task.source.compute.Append;
import org.metricshub.engine.connector.model.monitor.task.source.compute.Awk;
import org.metricshub.engine.connector.model.monitor.task.source.compute.Compute;
import org.metricshub.engine.connector.model.monitor.task.source.compute.DuplicateColumn;
import org.metricshub.engine.connector.model.monitor.task.source.compute.ExcludeMatchingLines;
import org.metricshub.engine.connector.model.monitor.task.source.compute.Head;
import org.metricshub.engine.connector.model.monitor.task.source.compute.KeepColumns;
import org.metricshub.engine.connector.model.monitor.task.source.compute.KeepOnlyMatchingLines;
import org.metricshub.engine.connector.model.monitor.task.source.compute.Prepend;
import org.metricshub.engine.connector.model.monitor.task.source.compute.Replace;
import org.metricshub.engine.connector.model.monitor.task.source.compute.Substring;
import org.metricshub.engine.connector.model.monitor.task.source.compute.Translate;
import org.metricshub.engine.extension.ExtensionManager;
import org.metricshub.engine.strategy.source.compute.ComputeProcessor;
import org.metricshub.engine.strategy.source.compute.ComputeUpdaterProcessor;
import org.metricshub.engine.telemetry.HostProperties;
import org.metricshub.engine.telemetry.TelemetryManager;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * How the readers of a {@link SourceTable} treat its single content type: a text or a table, never both (stage 4 of
 * the content-type epic, #1508). These rules replaced the legacy dual-state rules (a table and a raw text, with a
 * fallback at each read site) that were pinned before.
 */
@ExtendWith(MockitoExtension.class)
class SourceTableReadRulesTest {

	private static final String SOURCE_REF = "${source::monitors.enclosure.discovery.sources.source(1)}";
	private static final String OTHER_REF = "${source::monitors.enclosure.discovery.sources.source(2)}";
	private static final String ENTRIES_REF = "${source::monitors.enclosure.discovery.sources.entries}";

	@Mock
	private ISourceProcessor sourceProcessor;

	@Mock
	private ExtensionManager extensionManager;

	private static List<List<String>> table(final String... rows) {
		final List<List<String>> table = new ArrayList<>();
		for (final String row : rows) {
			table.add(new ArrayList<>(Arrays.asList(row.split("\\|", -1))));
		}
		return table;
	}

	private static TelemetryManager telemetryManager() {
		return TelemetryManager.builder()
			.hostConfiguration(
				HostConfiguration.builder().hostname(LOCALHOST).hostId(LOCALHOST).hostType(DeviceKind.LINUX).build()
			)
			.hostProperties(HostProperties.builder().build())
			.build();
	}

	private static TelemetryManager telemetryManagerWith(final String sourceRef, final SourceTable sourceTable) {
		final TelemetryManager telemetryManager = telemetryManager();
		telemetryManager.getHostProperties()
			.getConnectorNamespace(MY_CONNECTOR_1_NAME)
			.addSourceTable(sourceRef, sourceTable);
		return telemetryManager;
	}

	private static ComputeProcessor computeProcessor(final SourceTable sourceTable) {
		final TelemetryManager telemetryManager = telemetryManager();
		return ComputeProcessor.builder()
			.telemetryManager(telemetryManager)
			.clientsExecutor(new ClientsExecutor(telemetryManager))
			.connectorId(MY_CONNECTOR_1_NAME)
			.hostname(LOCALHOST)
			.sourceKey(SOURCE_REF)
			.index(0)
			.sourceTable(sourceTable)
			.build();
	}

	private SourceUpdaterProcessor sourceUpdaterProcessor(final TelemetryManager telemetryManager) {
		return new SourceUpdaterProcessor(
			sourceProcessor,
			telemetryManager,
			MY_CONNECTOR_1_NAME,
			Map.of(),
			extensionManager
		);
	}

	// ---------------------------------------------------------------------------------------------------------------
	// awk compute
	// ---------------------------------------------------------------------------------------------------------------

	/**
	 * The <code>awk</code> compute writes text: a <code>;</code> appended to every output line that lacks one, a blank
	 * output line becoming the line <code>;</code>, which the canonical conversion reads as a row holding one empty
	 * cell.
	 */
	@Test
	void awkComputeOutputGetsTrailingSeparatorAndBlankLineBecomesEmptyCell() {
		final SourceTable sourceTable = SourceTable.ofText("in");
		computeProcessor(sourceTable).process(Awk.builder().script("{ print \"a;b\"; print \"\"; print \"c;\" }").build());
		assertEquals(ContentType.TEXT, sourceTable.getContentType());
		assertEquals("a;b;\n;\nc;", sourceTable.getText());
		assertEquals(table("a|b", "", "c"), sourceTable.asTable());
	}

	// ---------------------------------------------------------------------------------------------------------------
	// ${source::x} substitution
	// ---------------------------------------------------------------------------------------------------------------

	/**
	 * <code>${source::x}</code> substituted into a text field is a table as CSV, or a text as is. The trailing
	 * <code>;</code> is stripped on the lines holding a single cell only, in both cases.
	 */
	@Test
	void sourceReferenceContentSerializesATableAndStripsTrailingSeparatorOnSingleCellLines() {
		// "d;e" is a single cell containing the separator: its line "d;e;" is not stripped
		// "" is a single empty cell: its line ";" becomes ""
		assertEquals(
			"a\nb;c;\nd;e;\n",
			extract(telemetryManagerWith(SOURCE_REF, SourceTable.ofTable(table("a", "b|c", "d;e", ""))))
		);

		// A text, line by line
		assertEquals("k\nv1;v2\n\nz", extract(telemetryManagerWith(SOURCE_REF, SourceTable.ofText("k;\nv1;v2\n\nz;"))));

		// Nothing at all
		assertEquals(EMPTY, extract(telemetryManagerWith(SOURCE_REF, SourceTable.empty())));
		assertEquals(EMPTY, extract(telemetryManagerWith(SOURCE_REF, SourceTable.ofText(EMPTY))));
	}

	private static String extract(final TelemetryManager telemetryManager) {
		return SourceUpdaterProcessor.extractSourceReferenceContent(
			telemetryManager,
			MY_CONNECTOR_1_NAME,
			"Test",
			"key",
			SOURCE_REF
		);
	}

	// ---------------------------------------------------------------------------------------------------------------
	// append / prepend / replace / translate values
	// ---------------------------------------------------------------------------------------------------------------

	/**
	 * A <code>;</code> or a line feed in an <code>append</code> or <code>prepend</code> value is a character of the
	 * cell: the table is never re-split.
	 */
	@Test
	void appendAndPrependValuesStayInTheirCell() {
		SourceTable sourceTable = SourceTable.ofTable(table("a|b"));
		computeProcessor(sourceTable).process(Append.builder().column(1).value(";x").build());
		assertEquals(table("a;x|b"), sourceTable.getTable());

		sourceTable = SourceTable.ofTable(table("a|b"));
		computeProcessor(sourceTable).process(Prepend.builder().column(2).value("MSHW;").build());
		assertEquals(table("a|MSHW;b"), sourceTable.getTable());

		sourceTable = SourceTable.ofTable(table("a|b"));
		computeProcessor(sourceTable).process(Append.builder().column(1).value(";\nz").build());
		assertEquals(table("a;\nz|b"), sourceTable.getTable());

		// $n reference: the referenced cell contains ";", no column is added
		sourceTable = SourceTable.ofTable(table("a|x;y"));
		computeProcessor(sourceTable).process(Append.builder().column(1).value("$2").build());
		assertEquals(table("ax;y|x;y"), sourceTable.getTable());

		// New column past the last one: the value is added as one cell
		sourceTable = SourceTable.ofTable(table("a"));
		computeProcessor(sourceTable).process(Append.builder().column(2).value("p;q").build());
		assertEquals(table("a|p;q"), sourceTable.getTable());

		// Line feed: one row
		sourceTable = SourceTable.ofTable(table("a|b"));
		computeProcessor(sourceTable).process(Append.builder().column(1).value("\nz").build());
		assertEquals(table("a\nz|b"), sourceTable.getTable());
	}

	/**
	 * <code>replace</code> and <code>translate</code> never re-split the table: a new value holding <code>;</code> or a
	 * line feed stays one cell, and the cells the compute does not touch stay as they are.
	 */
	@Test
	void replaceAndTranslateValuesStayInTheirCell() {
		SourceTable sourceTable = SourceTable.ofTable(table("a|b"));
		computeProcessor(sourceTable).process(Replace.builder().column(1).existingValue("a").newValue("a;x").build());
		assertEquals(table("a;x|b"), sourceTable.getTable());

		sourceTable = SourceTable.ofTable(table("a\nb|c;d"));
		computeProcessor(sourceTable).process(Replace.builder().column(1).existingValue("zzz").newValue(EMPTY).build());
		assertEquals(table("a\nb|c;d"), sourceTable.getTable());

		sourceTable = SourceTable.ofTable(table("a|b", "z|b"));
		final Map<String, String> translations = new LinkedHashMap<>();
		translations.put("a", "p;q");
		translations.put("default", "line1\nline2");
		final TranslationTable translationTable = TranslationTable.builder().translations(translations).build();
		computeProcessor(sourceTable).process(Translate.builder().column(1).translationTable(translationTable).build());
		assertEquals(table("p;q|b", "line1\nline2|b"), sourceTable.getTable());
	}

	// ---------------------------------------------------------------------------------------------------------------
	// table compute on a text
	// ---------------------------------------------------------------------------------------------------------------

	/**
	 * A table compute on a text (an <code>http</code>, <code>file</code> or <code>ipmi</code> source) reads no rows
	 * and gives an empty table, whether it changes the table in place or replaces it.
	 */
	@Test
	void tableComputeOnTextGivesAnEmptyTable() {
		for (final var compute : List.of(
			KeepColumns.builder().columnNumbers("1").build(),
			DuplicateColumn.builder().column(1).build(),
			Head.builder().lines("1").build(),
			Replace.builder().column(1).existingValue("a").newValue("b").build(),
			Append.builder().column(1).value("x").build(),
			Prepend.builder().column(1).value("x").build(),
			KeepOnlyMatchingLines.builder().column(1).regExp("a").build(),
			ExcludeMatchingLines.builder().column(1).regExp("a").build()
		)) {
			final SourceTable sourceTable = SourceTable.ofText("text");
			compute.accept(
				ComputeUpdaterProcessor.builder()
					.computeProcessor(computeProcessor(sourceTable))
					.telemetryManager(telemetryManager())
					.connectorId(MY_CONNECTOR_1_NAME)
					.attributes(Map.of())
					.build()
			);
			assertEquals(SourceTable.empty(), sourceTable, compute.toString());
		}
	}

	// ---------------------------------------------------------------------------------------------------------------
	// append, prepend, replace and substring without column, on a text (#1577)
	// ---------------------------------------------------------------------------------------------------------------

	/**
	 * Runs the compute as a source does, through the updater, on the given result.
	 */
	private static SourceTable run(final Compute compute, final SourceTable sourceTable) {
		compute.accept(
			ComputeUpdaterProcessor.builder()
				.computeProcessor(computeProcessor(sourceTable))
				.telemetryManager(telemetryManager())
				.connectorId(MY_CONNECTOR_1_NAME)
				.attributes(Map.of())
				.build()
		);
		return sourceTable;
	}

	private static SourceTable onText(final Compute compute, final String text) {
		return run(compute, SourceTable.ofText(text));
	}

	@Test
	void computeWithoutColumnAppliesOnceToTheWholeText() {
		// The value goes before or after the whole text, after its final line ending, with no column reference
		assertEquals(SourceTable.ofText("Bearer abc"), onText(Prepend.builder().value("Bearer ").build(), "abc"));
		assertEquals(SourceTable.ofText("$1;a;b\nc;d\n"), onText(Prepend.builder().value("$1;").build(), "a;b\nc;d\n"));
		assertEquals(SourceTable.ofText("a;b\nc;d\n$2"), onText(Append.builder().value("$2").build(), "a;b\nc;d\n"));
		assertEquals(SourceTable.ofText("x"), onText(Append.builder().value("x").build(), EMPTY));

		// Every occurrence, on every line, literally: no regular expression, no column reference
		assertEquals(
			SourceTable.ofText("a-b\nc-d\n"),
			onText(Replace.builder().existingValue(";").newValue("-").build(), "a;b\nc;d\n")
		);
		assertEquals(
			SourceTable.ofText("a$1b"),
			onText(Replace.builder().existingValue(".").newValue("$1").build(), "a.b")
		);
		assertEquals(
			SourceTable.ofText("ab"),
			onText(Replace.builder().existingValue("$1").newValue(EMPTY).build(), "a$1b")
		);
		assertEquals(
			SourceTable.ofText("a\nb"),
			onText(Replace.builder().existingValue("\r\n").newValue("\n").build(), "a\r\nb")
		);
		// A missing value leaves the text unchanged
		assertEquals(SourceTable.ofText("a"), onText(Replace.builder().existingValue("a").build(), "a"));

		// As on a cell: start is 1-based, length is the 1-based position of the last character kept
		assertEquals(SourceTable.ofText("bcd"), onText(Substring.builder().start("2").length("4").build(), "abcdef"));
		assertEquals(SourceTable.ofText("a\nb"), onText(Substring.builder().start("1").length("3").build(), "a\nb\nc"));
		assertEquals(SourceTable.ofText(EMPTY), onText(Substring.builder().start("1").length("0").build(), "abc"));
		// Out of the text, not a number, a column reference: the text is unchanged
		for (final Substring invalid : List.of(
			Substring.builder().start("0").length("2").build(),
			Substring.builder().start("2").length("7").build(),
			Substring.builder().start("x").length("2").build(),
			Substring.builder().start("$1").length("2").build(),
			Substring.builder().start("1").length("$2").build()
		)) {
			assertEquals(SourceTable.ofText("abcdef"), onText(invalid, "abcdef"), invalid.toString());
		}
	}

	@Test
	void computeWithoutColumnLeavesATableUnchanged() {
		for (final Compute compute : List.of(
			Prepend.builder().value("x").build(),
			Append.builder().value("x").build(),
			Replace.builder().existingValue("a").newValue("b").build(),
			Substring.builder().start("1").length("1").build()
		)) {
			assertEquals(SourceTable.ofTable(table("ab|cd")), run(compute, SourceTable.ofTable(table("ab|cd"))));
		}
	}

	/**
	 * Called without the updater, which gives a table compute on a text an empty table (see
	 * {@link #tableComputeOnTextGivesAnEmptyTable()}), a compute with a column leaves a text unchanged.
	 */
	@Test
	void computeWithColumnLeavesATextUnchanged() {
		for (final Compute compute : List.of(
			Prepend.builder().column(1).value("x").build(),
			Append.builder().column(1).value("x").build(),
			Replace.builder().column(1).existingValue("a").newValue("b").build(),
			Substring.builder().column(1).start("1").length("1").build()
		)) {
			final SourceTable sourceTable = SourceTable.ofText("ab");
			compute.accept(computeProcessor(sourceTable));
			assertEquals(SourceTable.ofText("ab"), sourceTable, compute.toString());
		}
	}

	// ---------------------------------------------------------------------------------------------------------------
	// executeForEachEntryOf
	// ---------------------------------------------------------------------------------------------------------------

	/**
	 * JSON_ARRAY concatenates the non-blank entry texts with <code>,\n</code> inside <code>[]</code>;
	 * JSON_ARRAY_EXTENDED wraps each entry's text with the entry row (serialized with <code>,</code> for
	 * <code>;</code> inside a cell); the custom method surrounds each entry's text with the start and end strings
	 * where <code>$n</code> is the entry column. The result is a text.
	 */
	@Test
	void executeForEachEntryOfJsonAndCustomConcatenateText() {
		final Map<String, SourceTable> results = Map.of(
			"a",
			SourceTable.ofText("{\"v\":1}"),
			"b",
			SourceTable.ofText(EMPTY),
			"c",
			SourceTable.ofText("{\"v\":3}")
		);

		final SourceTable jsonArray = runForEachEntry(EntryConcatMethod.JSON_ARRAY, results);
		assertEquals(SourceTable.ofText("[{\"v\":1},\n{\"v\":3}]"), jsonArray);

		final SourceTable extended = runForEachEntry(EntryConcatMethod.JSON_ARRAY_EXTENDED, results);
		assertEquals(
			SourceTable.ofText(
				"[{\n\"Entry\":{\n\"Full\":\"a;x,y;\",\n\"Column(1)\":\"a\",\n\"Column(2)\":\"x;y\",\n\"Value\":{\"v\":1}\n}\n},\n" +
					"{\n\"Entry\":{\n\"Full\":\"c;x,y;\",\n\"Column(1)\":\"c\",\n\"Column(2)\":\"x;y\",\n\"Value\":{\"v\":3}\n}\n}]"
			),
			extended
		);

		final SourceTable custom = runForEachEntry(
			CustomConcatMethod.builder().concatStart("<$1>").concatEnd("</$1>\n").build(),
			results
		);
		assertEquals(SourceTable.ofText("<a>{\"v\":1}</a>\n<b></b>\n<c>{\"v\":3}</c>\n"), custom);

		// An entry that is a table is read as its text
		final SourceTable tableEntries = runForEachEntry(
			EntryConcatMethod.JSON_ARRAY,
			Map.of("a", SourceTable.ofTable(table("p;q|r")), "b", SourceTable.empty(), "c", SourceTable.empty())
		);
		assertEquals(SourceTable.ofText("[p,q;r;]"), tableEntries);
	}

	/**
	 * Runs an HTTP source with <code>path: $1</code> for the entries a, b and c (second column "x;y"), each entry
	 * returning the given source table.
	 */
	private SourceTable runForEachEntry(final IEntryConcatMethod concatMethod, final Map<String, SourceTable> results) {
		final TelemetryManager telemetryManager = telemetryManagerWith(
			ENTRIES_REF,
			SourceTable.ofTable(table("a|x;y", "b|x;y", "c|x;y"))
		);
		final HttpSource httpSource = HttpSource.builder()
			.url("http://host")
			.path("$1")
			.executeForEachEntryOf(ExecuteForEachEntryOf.builder().source(ENTRIES_REF).concatMethod(concatMethod).build())
			.build();
		doAnswer(invocation -> {
			final SourceTable entryResult = results.get(invocation.<HttpSource>getArgument(0).getPath());
			// A fresh copy each time, as the real processor builds a new result for each entry
			return entryResult.getContentType() == ContentType.TEXT
				? SourceTable.ofText(entryResult.getText())
				: SourceTable.ofTable(entryResult.asTable());
		}).when(sourceProcessor).process(any(HttpSource.class));
		return sourceUpdaterProcessor(telemetryManager).process(httpSource);
	}

	// ---------------------------------------------------------------------------------------------------------------
	// AWK variables, HTTP token, copy
	// ---------------------------------------------------------------------------------------------------------------

	/**
	 * An AWK <code>variables</code> entry referencing a source exposes its table, or the table its text converts to
	 * (an HTTP body is split on line feeds and <code>;</code>), else an empty array.
	 */
	@Test
	void awkVariableExposesTheTableOrTheConvertedText() {
		assertEquals(table("a|b", "c"), resolveVariable(SourceTable.ofTable(table("a|b", "c"))));
		assertEquals(table("a|b", "c"), resolveVariable(SourceTable.ofText("a;b\r\n\r\nc;")));
		assertEquals(List.of(), resolveVariable(SourceTable.empty()));
		assertEquals(List.of(), resolveVariable(SourceTable.ofText(EMPTY)));
	}

	private static Object resolveVariable(final SourceTable sourceTable) {
		return AwkVariableHelper.resolveVariables(
			Map.of("v", SOURCE_REF),
			telemetryManagerWith(SOURCE_REF, sourceTable),
			MY_CONNECTOR_1_NAME,
			Map.of(),
			"key"
		).get("v");
	}

	/**
	 * The HTTP authentication token is the first cell of the first row of a table, or the whole first line of a text
	 * without its line ending (#1560): a <code>;</code> in the line is part of the token.
	 */
	@Test
	void httpTokenIsFirstCellOfATableOrFirstLineOfAText() {
		assertEquals("tok", extractToken(SourceTable.ofTable(table("tok|x"))));
		assertEquals("tok;en", extractToken(SourceTable.ofText("tok;en\nline2")));
		assertEquals("abc", extractToken(SourceTable.ofText("abc\ndef")));
		assertEquals("abc", extractToken(SourceTable.ofText("abc\r\ndef")));
		assertEquals("abc", extractToken(SourceTable.ofText("abc\n")));
		// A lone CR ends no line
		assertEquals("abc\rdef", extractToken(SourceTable.ofText("abc\rdef\nghi")));
		assertEquals("a b=c", extractToken(SourceTable.ofText("a b=c")));
		assertEquals(EMPTY, extractToken(SourceTable.ofText("\nabc")));
		assertEquals(EMPTY, extractToken(SourceTable.ofText(EMPTY)));
		assertNull(extractToken(SourceTable.empty()));
	}

	private String extractToken(final SourceTable sourceTable) {
		return sourceUpdaterProcessor(telemetryManagerWith(SOURCE_REF, sourceTable)).extractHttpTokenFromSource(
			"original",
			SOURCE_REF,
			"authenticationToken"
		);
	}

	/**
	 * <code>copy</code> of a text is the same text; <code>copy</code> of a table copies its rows, but the rows of no
	 * cell, so that a compute on the copy leaves the origin unchanged.
	 */
	@Test
	void copyKeepsTheTextOrCopiesTheRows() {
		final TelemetryManager telemetryManager = telemetryManagerWith(SOURCE_REF, SourceTable.ofText("body\n"));
		final List<List<String>> rows = table("a|b", "c");
		rows.add(new ArrayList<>());
		final SourceTable origin = SourceTable.ofTable(rows);
		telemetryManager.getHostProperties().getConnectorNamespace(MY_CONNECTOR_1_NAME).addSourceTable(OTHER_REF, origin);
		final SourceProcessor processor = SourceProcessor.builder()
			.telemetryManager(telemetryManager)
			.connectorId(MY_CONNECTOR_1_NAME)
			.build();

		assertEquals(SourceTable.ofText("body\n"), processor.process(CopySource.builder().from(SOURCE_REF).build()));

		final SourceTable tableCopy = processor.process(CopySource.builder().from(OTHER_REF).build());
		assertEquals(SourceTable.ofTable(table("a|b", "c")), tableCopy);
		tableCopy.getTable().get(0).set(0, "changed");
		assertEquals("a", origin.getTable().get(0).get(0));
	}
}
