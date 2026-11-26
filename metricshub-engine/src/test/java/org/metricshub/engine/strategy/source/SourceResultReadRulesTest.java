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
import org.metricshub.engine.connector.model.monitor.task.source.StaticSource;
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
 * How the readers of a {@link SourceResult} treat its single content type: a text or a table, never both (stage 4 of
 * the content-type epic, #1508). These rules replaced the legacy dual-state rules (a table and a raw text, with a
 * fallback at each read site) that were pinned before.
 */
@ExtendWith(MockitoExtension.class)
class SourceResultReadRulesTest {

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

	private static TelemetryManager telemetryManagerWith(final String sourceRef, final SourceResult sourceResult) {
		final TelemetryManager telemetryManager = telemetryManager();
		telemetryManager.getHostProperties()
			.getConnectorNamespace(MY_CONNECTOR_1_NAME)
			.addSourceResult(sourceRef, sourceResult);
		return telemetryManager;
	}

	private static ComputeProcessor computeProcessor(final SourceResult sourceResult) {
		final TelemetryManager telemetryManager = telemetryManager();
		return ComputeProcessor.builder()
			.telemetryManager(telemetryManager)
			.clientsExecutor(new ClientsExecutor(telemetryManager))
			.connectorId(MY_CONNECTOR_1_NAME)
			.hostname(LOCALHOST)
			.sourceKey(SOURCE_REF)
			.index(0)
			.sourceResult(sourceResult)
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
	 * The <code>awk</code> compute writes the text the script printed: a blank line stays blank, and
	 * <code>toTable</code> drops it.
	 */
	@Test
	void awkComputeOutputIsThePrintedText() {
		final SourceResult sourceResult = SourceResult.ofText("in");
		computeProcessor(sourceResult).process(Awk.builder().script("{ print \"a;b\"; print \"\"; print \"c;\" }").build());
		assertEquals(ContentType.TEXT, sourceResult.getContentType());
		assertEquals("a;b\n\nc;\n", sourceResult.getText());
		assertEquals(table("a|b", "c"), sourceResult.asTable());
	}

	/**
	 * A token that an <code>awk</code> compute prints is the text <code>abc</code>, and a header or an
	 * <code>authenticationToken</code> that references it receives <code>abc</code>.
	 */
	@Test
	void awkComputeTokenReachesTheHeaderAsPrinted() {
		final SourceResult printf = SourceResult.ofText("in");
		computeProcessor(printf).process(Awk.builder().script("{ printf \"abc\" }").build());
		assertEquals("abc", printf.getText());

		final SourceResult print = SourceResult.ofText("in");
		computeProcessor(print).process(Awk.builder().script("{ print \"abc\" }").build());
		assertEquals("abc\n", print.getText());

		for (final SourceResult token : List.of(printf, print)) {
			assertEquals(
				"X-Auth-Token: abc",
				SourceUpdaterProcessor.replaceSourceReferenceContent(
					"X-Auth-Token: " + SOURCE_REF,
					telemetryManagerWith(SOURCE_REF, token),
					MY_CONNECTOR_1_NAME,
					"HttpSource",
					"key"
				)
			);
			assertEquals("abc", extractToken(token));
		}
	}

	// ---------------------------------------------------------------------------------------------------------------
	// ${source::x} substitution
	// ---------------------------------------------------------------------------------------------------------------

	/**
	 * <code>${source::x}</code> substituted into a text field is the text as is, without its final line feeds. A table
	 * is not interpolated: it gives an empty text.
	 */
	@Test
	void sourceReferenceContentIsTheText() {
		// A text
		assertEquals("k;\nv1;v2\n\nz;", extract(telemetryManagerWith(SOURCE_REF, SourceResult.ofText("k;\nv1;v2\n\nz;"))));
		assertEquals("abc", extract(telemetryManagerWith(SOURCE_REF, SourceResult.ofText("abc\n\n"))));

		// A table
		assertEquals(EMPTY, extract(telemetryManagerWith(SOURCE_REF, SourceResult.ofTable(table("a", "b|c")))));

		// Nothing at all
		assertEquals(EMPTY, extract(telemetryManagerWith(SOURCE_REF, SourceResult.empty())));
		assertEquals(EMPTY, extract(telemetryManagerWith(SOURCE_REF, SourceResult.ofText(EMPTY))));
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
		SourceResult sourceResult = SourceResult.ofTable(table("a|b"));
		computeProcessor(sourceResult).process(Append.builder().column(1).value(";x").build());
		assertEquals(table("a;x|b"), sourceResult.getTable());

		sourceResult = SourceResult.ofTable(table("a|b"));
		computeProcessor(sourceResult).process(Prepend.builder().column(2).value("MSHW;").build());
		assertEquals(table("a|MSHW;b"), sourceResult.getTable());

		sourceResult = SourceResult.ofTable(table("a|b"));
		computeProcessor(sourceResult).process(Append.builder().column(1).value(";\nz").build());
		assertEquals(table("a;\nz|b"), sourceResult.getTable());

		// $n reference: the referenced cell contains ";", no column is added
		sourceResult = SourceResult.ofTable(table("a|x;y"));
		computeProcessor(sourceResult).process(Append.builder().column(1).value("$2").build());
		assertEquals(table("ax;y|x;y"), sourceResult.getTable());

		// New column past the last one: the value is added as one cell
		sourceResult = SourceResult.ofTable(table("a"));
		computeProcessor(sourceResult).process(Append.builder().column(2).value("p;q").build());
		assertEquals(table("a|p;q"), sourceResult.getTable());

		// Line feed: one row
		sourceResult = SourceResult.ofTable(table("a|b"));
		computeProcessor(sourceResult).process(Append.builder().column(1).value("\nz").build());
		assertEquals(table("a\nz|b"), sourceResult.getTable());
	}

	/**
	 * <code>replace</code> and <code>translate</code> never re-split the table: a new value holding <code>;</code> or a
	 * line feed stays one cell, and the cells the compute does not touch stay as they are.
	 */
	@Test
	void replaceAndTranslateValuesStayInTheirCell() {
		SourceResult sourceResult = SourceResult.ofTable(table("a|b"));
		computeProcessor(sourceResult).process(Replace.builder().column(1).existingValue("a").newValue("a;x").build());
		assertEquals(table("a;x|b"), sourceResult.getTable());

		sourceResult = SourceResult.ofTable(table("a\nb|c;d"));
		computeProcessor(sourceResult).process(Replace.builder().column(1).existingValue("zzz").newValue(EMPTY).build());
		assertEquals(table("a\nb|c;d"), sourceResult.getTable());

		sourceResult = SourceResult.ofTable(table("a|b", "z|b"));
		final Map<String, String> translations = new LinkedHashMap<>();
		translations.put("a", "p;q");
		translations.put("default", "line1\nline2");
		final TranslationTable translationTable = TranslationTable.builder().translations(translations).build();
		computeProcessor(sourceResult).process(Translate.builder().column(1).translationTable(translationTable).build());
		assertEquals(table("p;q|b", "line1\nline2|b"), sourceResult.getTable());
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
			KeepOnlyMatchingLines.builder().column(1).regExp("a").build(),
			ExcludeMatchingLines.builder().column(1).regExp("a").build()
		)) {
			final SourceResult sourceResult = SourceResult.ofText("text");
			compute.accept(
				ComputeUpdaterProcessor.builder()
					.computeProcessor(computeProcessor(sourceResult))
					.telemetryManager(telemetryManager())
					.connectorId(MY_CONNECTOR_1_NAME)
					.attributes(Map.of())
					.build()
			);
			assertEquals(SourceResult.empty(), sourceResult, compute.toString());
		}
	}

	// ---------------------------------------------------------------------------------------------------------------
	// append, prepend, replace and substring without column, on a text (#1577)
	// ---------------------------------------------------------------------------------------------------------------

	/**
	 * Runs the compute as a source does, through the updater, on the given result.
	 */
	private static SourceResult run(final Compute compute, final SourceResult sourceResult) {
		compute.accept(
			ComputeUpdaterProcessor.builder()
				.computeProcessor(computeProcessor(sourceResult))
				.telemetryManager(telemetryManager())
				.connectorId(MY_CONNECTOR_1_NAME)
				.attributes(Map.of())
				.build()
		);
		return sourceResult;
	}

	private static SourceResult onText(final Compute compute, final String text) {
		return run(compute, SourceResult.ofText(text));
	}

	/**
	 * A text compute writes LF line endings only, whatever the text and the value hold.
	 */
	@Test
	void computeWithoutColumnWritesLfOnly() {
		assertEquals(
			SourceResult.ofText("x\ny\na\nb\n"),
			onText(Prepend.builder().value("x\r\ny\r\n").build(), "a\r\nb\r\n")
		);
		assertEquals(SourceResult.ofText("a\nb\nx\n"), onText(Append.builder().value("x\r\n").build(), "a\r\nb\r\n"));
		assertEquals(
			SourceResult.ofText("a\nb-\n"),
			onText(Replace.builder().existingValue(";").newValue("-\r\n").build(), "a\r\nb;")
		);
		// A line ending to replace is LF, whatever its form in the text that was read: a text holds no CRLF
		assertEquals(
			SourceResult.ofText("a-b-"),
			onText(Replace.builder().existingValue("\n").newValue("-").build(), "a\r\nb\n")
		);
		assertEquals(
			SourceResult.ofText("a\nb\n"),
			onText(Replace.builder().existingValue("\r\n").newValue("-").build(), "a\r\nb\n")
		);
		// The positions count the LF text: a, LF, b
		assertEquals(SourceResult.ofText("a\nb"), onText(Substring.builder().start("1").length("3").build(), "a\r\nb\r\n"));
	}

	@Test
	void computeWithoutColumnAppliesOnceToTheWholeText() {
		// The value goes before or after the whole text, after its final line ending, with no column reference
		assertEquals(SourceResult.ofText("Bearer abc"), onText(Prepend.builder().value("Bearer ").build(), "abc"));
		assertEquals(SourceResult.ofText("$1;a;b\nc;d\n"), onText(Prepend.builder().value("$1;").build(), "a;b\nc;d\n"));
		assertEquals(SourceResult.ofText("a;b\nc;d\n$2"), onText(Append.builder().value("$2").build(), "a;b\nc;d\n"));
		assertEquals(SourceResult.ofText("x"), onText(Append.builder().value("x").build(), EMPTY));

		// Every occurrence, on every line, literally: no regular expression, no column reference
		assertEquals(
			SourceResult.ofText("a-b\nc-d\n"),
			onText(Replace.builder().existingValue(";").newValue("-").build(), "a;b\nc;d\n")
		);
		assertEquals(
			SourceResult.ofText("a$1b"),
			onText(Replace.builder().existingValue(".").newValue("$1").build(), "a.b")
		);
		assertEquals(
			SourceResult.ofText("ab"),
			onText(Replace.builder().existingValue("$1").newValue(EMPTY).build(), "a$1b")
		);
		// A missing value leaves the text unchanged
		assertEquals(SourceResult.ofText("a"), onText(Replace.builder().existingValue("a").build(), "a"));

		// As on a cell: start is 1-based, length is the 1-based position of the last character kept
		assertEquals(SourceResult.ofText("bcd"), onText(Substring.builder().start("2").length("4").build(), "abcdef"));
		assertEquals(SourceResult.ofText("a\nb"), onText(Substring.builder().start("1").length("3").build(), "a\nb\nc"));
		assertEquals(SourceResult.ofText(EMPTY), onText(Substring.builder().start("1").length("0").build(), "abc"));
		// Out of the text, not a number, a column reference: the text is unchanged
		for (final Substring invalid : List.of(
			Substring.builder().start("0").length("2").build(),
			Substring.builder().start("2").length("7").build(),
			Substring.builder().start("x").length("2").build(),
			Substring.builder().start("$1").length("2").build(),
			Substring.builder().start("1").length("$2").build()
		)) {
			assertEquals(SourceResult.ofText("abcdef"), onText(invalid, "abcdef"), invalid.toString());
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
			assertEquals(SourceResult.ofTable(table("ab|cd")), run(compute, SourceResult.ofTable(table("ab|cd"))));
		}
	}

	/**
	 * A text has no column: a compute with a column leaves a text unchanged, run as a source runs it or called
	 * directly. Nothing converts the text for it, unlike the other table computes (see
	 * {@link #tableComputeOnTextGivesAnEmptyTable()}).
	 */
	@Test
	void computeWithColumnLeavesATextUnchanged() {
		for (final Compute compute : List.of(
			Prepend.builder().column(1).value("x").build(),
			Append.builder().column(1).value("x").build(),
			Replace.builder().column(1).existingValue("a").newValue("b").build(),
			Substring.builder().column(1).start("1").length("1").build()
		)) {
			assertEquals(SourceResult.ofText("ab"), onText(compute, "ab"), compute.toString());

			final SourceResult sourceResult = SourceResult.ofText("ab");
			compute.accept(computeProcessor(sourceResult));
			assertEquals(SourceResult.ofText("ab"), sourceResult, compute.toString());
		}
	}

	// ---------------------------------------------------------------------------------------------------------------
	// executeForEachEntryOf
	// ---------------------------------------------------------------------------------------------------------------

	/**
	 * JSON_ARRAY concatenates the non-blank entry texts with <code>,\n</code> inside <code>[]</code>;
	 * JSON_ARRAY_EXTENDED wraps each entry's text with the cells of the entry row (<code>Column(n)</code>, each cell
	 * as it is); the custom method surrounds each entry's text with the start and end strings where <code>$n</code>
	 * is the entry column. The result is a text.
	 */
	@Test
	void executeForEachEntryOfJsonAndCustomConcatenateText() {
		final Map<String, SourceResult> results = Map.of(
			"a",
			SourceResult.ofText("{\"v\":1}"),
			"b",
			SourceResult.ofText(EMPTY),
			"c",
			SourceResult.ofText("{\"v\":3}")
		);

		final SourceResult jsonArray = runForEachEntry(EntryConcatMethod.JSON_ARRAY, results);
		assertEquals(SourceResult.ofText("[{\"v\":1},\n{\"v\":3}]"), jsonArray);

		final SourceResult extended = runForEachEntry(EntryConcatMethod.JSON_ARRAY_EXTENDED, results);
		assertEquals(
			SourceResult.ofText(
				"[{\n\"Entry\":{\n\"Column(1)\":\"a\",\n\"Column(2)\":\"x;y\",\n\"Value\":{\"v\":1}\n}\n},\n" +
					"{\n\"Entry\":{\n\"Column(1)\":\"c\",\n\"Column(2)\":\"x;y\",\n\"Value\":{\"v\":3}\n}\n}]"
			),
			extended
		);

		final SourceResult custom = runForEachEntry(
			CustomConcatMethod.builder().concatStart("<$1>").concatEnd("</$1>\n").build(),
			results
		);
		assertEquals(SourceResult.ofText("<a>{\"v\":1}</a>\n<b></b>\n<c>{\"v\":3}</c>\n"), custom);

		// An entry that is a table is read as its text
		final SourceResult tableEntries = runForEachEntry(
			EntryConcatMethod.JSON_ARRAY,
			Map.of("a", SourceResult.ofTable(table("p;q|r")), "b", SourceResult.empty(), "c", SourceResult.empty())
		);
		assertEquals(SourceResult.ofText("[p,q;r;]"), tableEntries);
	}

	/**
	 * Runs an HTTP source with <code>path: $1</code> for the entries a, b and c (second column "x;y"), each entry
	 * returning the given source table.
	 */
	private SourceResult runForEachEntry(final IEntryConcatMethod concatMethod, final Map<String, SourceResult> results) {
		final TelemetryManager telemetryManager = telemetryManagerWith(
			ENTRIES_REF,
			SourceResult.ofTable(table("a|x;y", "b|x;y", "c|x;y"))
		);
		final HttpSource httpSource = HttpSource.builder()
			.url("http://host")
			.path("$1")
			.executeForEachEntryOf(ExecuteForEachEntryOf.builder().source(ENTRIES_REF).concatMethod(concatMethod).build())
			.build();
		doAnswer(invocation -> {
			final SourceResult entryResult = results.get(invocation.<HttpSource>getArgument(0).getPath());
			// A fresh copy each time, as the real processor builds a new result for each entry
			return entryResult.getContentType() == ContentType.TEXT
				? SourceResult.ofText(entryResult.getText())
				: SourceResult.ofTable(entryResult.asTable());
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
		assertEquals(table("a|b", "c"), resolveVariable(SourceResult.ofTable(table("a|b", "c"))));
		assertEquals(table("a|b", "c"), resolveVariable(SourceResult.ofText("a;b\r\n\r\nc;")));
		assertEquals(List.of(), resolveVariable(SourceResult.empty()));
		assertEquals(List.of(), resolveVariable(SourceResult.ofText(EMPTY)));
	}

	private static Object resolveVariable(final SourceResult sourceResult) {
		return AwkVariableHelper.resolveVariables(
			Map.of("v", SOURCE_REF),
			telemetryManagerWith(SOURCE_REF, sourceResult),
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
		assertEquals("tok", extractToken(SourceResult.ofTable(table("tok|x"))));
		assertEquals("tok;en", extractToken(SourceResult.ofText("tok;en\nline2")));
		assertEquals("tok;en;", extractToken(SourceResult.ofText("tok;en;")));
		assertEquals("abc", extractToken(SourceResult.ofText("abc\ndef")));
		assertEquals("abc", extractToken(SourceResult.ofText("abc\r\ndef")));
		assertEquals("abc", extractToken(SourceResult.ofText("abc\n")));
		// A lone CR ends no line
		assertEquals("abc\rdef", extractToken(SourceResult.ofText("abc\rdef\nghi")));
		assertEquals("a b=c", extractToken(SourceResult.ofText("a b=c")));
		assertEquals(EMPTY, extractToken(SourceResult.ofText("\nabc")));
		assertEquals(EMPTY, extractToken(SourceResult.ofText(EMPTY)));
		assertNull(extractToken(SourceResult.empty()));
	}

	/**
	 * An HTTP authentication token that is not a source reference is the token itself, whole: it is never parsed as
	 * a <code>;</code> table.
	 */
	@Test
	void httpTokenThatIsNotAReferenceIsTheTokenItself() {
		final SourceUpdaterProcessor processor = sourceUpdaterProcessor(
			telemetryManagerWith(SOURCE_REF, SourceResult.empty())
		);
		assertEquals("to;ken;", processor.extractHttpTokenFromSource("original", "to;ken;", "authenticationToken"));
		assertEquals(EMPTY, processor.extractHttpTokenFromSource("original", EMPTY, "authenticationToken"));
		assertNull(processor.extractHttpTokenFromSource("original", null, "authenticationToken"));
	}

	private String extractToken(final SourceResult sourceResult) {
		return sourceUpdaterProcessor(telemetryManagerWith(SOURCE_REF, sourceResult)).extractHttpTokenFromSource(
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
		final TelemetryManager telemetryManager = telemetryManagerWith(SOURCE_REF, SourceResult.ofText("body\n"));
		final List<List<String>> rows = table("a|b", "c");
		rows.add(new ArrayList<>());
		final SourceResult origin = SourceResult.ofTable(rows);
		telemetryManager.getHostProperties().getConnectorNamespace(MY_CONNECTOR_1_NAME).addSourceResult(OTHER_REF, origin);
		final SourceProcessor processor = SourceProcessor.builder()
			.telemetryManager(telemetryManager)
			.connectorId(MY_CONNECTOR_1_NAME)
			.build();

		assertEquals(SourceResult.ofText("body\n"), processor.process(CopySource.builder().from(SOURCE_REF).build()));

		final SourceResult tableCopy = processor.process(CopySource.builder().from(OTHER_REF).build());
		assertEquals(SourceResult.ofTable(table("a|b", "c")), tableCopy);
		tableCopy.getTable().get(0).set(0, "changed");
		assertEquals("a", origin.getTable().get(0).get(0));
	}

	/**
	 * A source reference in a cell of a <code>static</code> source is interpolated as in any other field: the cell
	 * holds the text of the source. A <code>static</code> source never reads a reference as a source,
	 * <code>copy</code> does.
	 */
	@Test
	void staticCellInterpolatesTheTextOfASource() {
		final TelemetryManager telemetryManager = telemetryManagerWith(SOURCE_REF, SourceResult.ofText("body"));
		final SourceUpdaterProcessor processor = new SourceUpdaterProcessor(
			SourceProcessor.builder().telemetryManager(telemetryManager).connectorId(MY_CONNECTOR_1_NAME).build(),
			telemetryManager,
			MY_CONNECTOR_1_NAME,
			Map.of(),
			extensionManager
		);

		assertEquals(
			SourceResult.ofTable(table("id|a-body")),
			processor.process(StaticSource.builder().value(List.of("id", "a-" + SOURCE_REF)).build())
		);
	}
}
