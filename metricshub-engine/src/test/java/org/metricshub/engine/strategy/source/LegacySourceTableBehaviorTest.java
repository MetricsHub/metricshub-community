package org.metricshub.engine.strategy.source;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.metricshub.engine.common.helpers.MetricsHubConstants.EMPTY;
import static org.metricshub.engine.common.helpers.MetricsHubConstants.TABLE_SEP;
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
import org.metricshub.engine.connector.model.common.CustomConcatMethod;
import org.metricshub.engine.connector.model.common.DeviceKind;
import org.metricshub.engine.connector.model.common.EntryConcatMethod;
import org.metricshub.engine.connector.model.common.ExecuteForEachEntryOf;
import org.metricshub.engine.connector.model.common.IEntryConcatMethod;
import org.metricshub.engine.connector.model.common.TranslationTable;
import org.metricshub.engine.connector.model.monitor.task.source.CopySource;
import org.metricshub.engine.connector.model.monitor.task.source.HttpSource;
import org.metricshub.engine.connector.model.monitor.task.source.TableUnionSource;
import org.metricshub.engine.connector.model.monitor.task.source.compute.Append;
import org.metricshub.engine.connector.model.monitor.task.source.compute.Awk;
import org.metricshub.engine.connector.model.monitor.task.source.compute.DuplicateColumn;
import org.metricshub.engine.connector.model.monitor.task.source.compute.ExcludeMatchingLines;
import org.metricshub.engine.connector.model.monitor.task.source.compute.Head;
import org.metricshub.engine.connector.model.monitor.task.source.compute.KeepColumns;
import org.metricshub.engine.connector.model.monitor.task.source.compute.KeepOnlyMatchingLines;
import org.metricshub.engine.connector.model.monitor.task.source.compute.Prepend;
import org.metricshub.engine.connector.model.monitor.task.source.compute.Replace;
import org.metricshub.engine.connector.model.monitor.task.source.compute.Translate;
import org.metricshub.engine.extension.ExtensionManager;
import org.metricshub.engine.strategy.source.compute.ComputeProcessor;
import org.metricshub.engine.telemetry.HostProperties;
import org.metricshub.engine.telemetry.TelemetryManager;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Pins the legacy dual-state behavior of {@link SourceTable} (a table and a raw text, with a consumer-specific
 * fallback at each read site), as listed in the edge cases of the content-type epic (#1508, issue #1509).
 * <p>
 * These tests describe what the engine does today, not what it should do. They are deleted in stage 4 of the epic
 * together with the behavior they pin. Each test names the legacy rule it pins in its Javadoc.
 * <p>
 * The <code>commandLine</code>, <code>sql</code> and <code>jmx</code> rules live in extension modules and are pinned
 * there: <code>CommandLineSourceProcessorLegacyTest</code>, <code>SqlSourceProcessorTest</code> and
 * <code>JmxSourceProcessorTest</code>. Only the engine primitive they rely on is pinned here.
 */
@ExtendWith(MockitoExtension.class)
class LegacySourceTableBehaviorTest {

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

	/**
	 * Appends a row without any cell (what <code>copy</code> and the LIST concatenation drop as "empty").
	 */
	private static List<List<String>> withZeroCellRow(final List<List<String>> table) {
		table.add(new ArrayList<>());
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
	// commandLine
	// ---------------------------------------------------------------------------------------------------------------

	/**
	 * <code>commandLine</code> builds its table with <code>String.split(";")</code>, which drops the trailing empty
	 * cells that {@link SourceTable#csvToTable(String, String)} keeps, and turns a blank line into a one-cell row that
	 * <code>csvToTable</code> drops. The processor itself is pinned in the OS command extension.
	 */
	@Test
	void commandLineSplitDiffersFromCsvToTable() {
		assertEquals(List.of("a", "b"), List.of("a;b;;".split(TABLE_SEP)));
		assertEquals(List.of(List.of("a", "b", "")), SourceTable.csvToTable("a;b;;", TABLE_SEP));

		assertEquals(List.of(""), List.of("".split(TABLE_SEP)));
		assertEquals(List.of(), SourceTable.csvToTable("", TABLE_SEP));

		assertEquals(List.of("", "x"), List.of(";x".split(TABLE_SEP)));
		assertEquals(List.of(List.of("", "x")), SourceTable.csvToTable(";x", TABLE_SEP));
	}

	// ---------------------------------------------------------------------------------------------------------------
	// awk compute
	// ---------------------------------------------------------------------------------------------------------------

	/**
	 * The <code>awk</code> compute reads the raw text when there is one, else the table serialized with
	 * <code>;</code> where a <code>;</code> inside a cell is replaced by <code>,</code>.
	 */
	@Test
	void awkComputeReadsRawTextElseTableWithCommaForSemicolon() {
		final Awk echo = Awk.builder().script("{ print }").build();

		// Table only: the script sees "a,b;c;" and ";d;"
		final SourceTable tableOnly = SourceTable.builder().table(table("a;b|c", "|d")).build();
		computeProcessor(tableOnly).process(echo);
		assertEquals("a,b;c;\n;d;", tableOnly.getRawData());
		assertEquals(table("a,b|c", "|d"), tableOnly.getTable());

		// Raw text present: the table is ignored
		final SourceTable both = SourceTable.builder().rawData("x;y").table(table("ignored")).build();
		computeProcessor(both).process(echo);
		assertEquals("x;y;", both.getRawData());
		assertEquals(table("x|y"), both.getTable());

		// An empty raw text counts as absent
		final SourceTable emptyRaw = SourceTable.builder().rawData(EMPTY).table(table("p|q")).build();
		computeProcessor(emptyRaw).process(echo);
		assertEquals("p;q;", emptyRaw.getRawData());
	}

	/**
	 * The <code>awk</code> compute appends a <code>;</code> to every output line that lacks one, and a blank output
	 * line becomes the line <code>;</code>, that is a row holding one empty cell.
	 */
	@Test
	void awkComputeOutputGetsTrailingSeparatorAndBlankLineBecomesEmptyCell() {
		final SourceTable sourceTable = SourceTable.builder().rawData("in").build();
		computeProcessor(sourceTable).process(Awk.builder().script("{ print \"a;b\"; print \"\"; print \"c;\" }").build());
		assertEquals("a;b;\n;\nc;", sourceTable.getRawData());
		assertEquals(table("a|b", "", "c"), sourceTable.getTable());
	}

	/**
	 * When the script prints nothing, the table is emptied but the raw text is left as it was: a later consumer
	 * reading the raw text first (another <code>awk</code>, <code>json2Csv</code>) sees the input of this compute.
	 */
	@Test
	void awkComputeEmptyResultKeepsStaleRawText() {
		final SourceTable sourceTable = SourceTable.builder().rawData("stale").table(table("a|b")).build();
		computeProcessor(sourceTable).process(Awk.builder().script("BEGIN { }").build());
		assertEquals(List.of(), sourceTable.getTable());
		assertEquals("stale", sourceTable.getRawData());

		// A second awk compute runs on the stale text
		computeProcessor(sourceTable).process(Awk.builder().script("{ print }").build());
		assertEquals("stale;", sourceTable.getRawData());
		assertEquals(table("stale"), sourceTable.getTable());
	}

	// ---------------------------------------------------------------------------------------------------------------
	// ${source::x} substitution
	// ---------------------------------------------------------------------------------------------------------------

	/**
	 * <code>${source::x}</code> substituted into a text field is the table as CSV when the table is not empty, else
	 * the raw text. The trailing <code>;</code> is stripped on the lines holding a single cell only.
	 */
	@Test
	void sourceReferenceContentPrefersTableAndStripsTrailingSeparatorOnSingleCellLines() {
		final TelemetryManager telemetryManager = telemetryManagerWith(
			SOURCE_REF,
			SourceTable.builder().table(table("a", "b|c", "d;e", "")).rawData("ignored").build()
		);
		// "d;e" is a single cell containing the separator: its line "d;e;" is not stripped
		// "" is a single empty cell: its line ";" becomes ""
		assertEquals("a\nb;c;\nd;e;\n", extract(telemetryManager));

		// Raw text when the table is empty, line by line
		assertEquals(
			"k\nv1;v2\n\nz",
			extract(telemetryManagerWith(SOURCE_REF, SourceTable.builder().rawData("k;\nv1;v2\n\nz;").build()))
		);

		// Nothing at all
		assertEquals(EMPTY, extract(telemetryManagerWith(SOURCE_REF, SourceTable.empty())));
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
	// append / prepend / replace / translate round trips
	// ---------------------------------------------------------------------------------------------------------------

	/**
	 * <code>append</code> and <code>prepend</code> with a literal value containing <code>;</code> re-split the table
	 * so the value adds columns; a value containing a line feed adds rows.
	 */
	@Test
	void appendAndPrependWithSeparatorAddColumnsAndLineFeedAddsRows() {
		SourceTable sourceTable = SourceTable.builder().table(table("a|b")).build();
		computeProcessor(sourceTable).process(Append.builder().column(1).value(";x").build());
		assertEquals(table("a|x|b"), sourceTable.getTable());
		assertEquals("a;x;b;", sourceTable.getRawData());

		sourceTable = SourceTable.builder().table(table("a|b")).build();
		computeProcessor(sourceTable).process(Prepend.builder().column(2).value("MSHW;").build());
		assertEquals(table("a|MSHW|b"), sourceTable.getTable());

		// A line feed in the value becomes a new row, through the same serialization round trip
		sourceTable = SourceTable.builder().table(table("a|b")).build();
		computeProcessor(sourceTable).process(Append.builder().column(1).value(";\nz").build());
		assertEquals(table("a", "z|b"), sourceTable.getTable());
		assertEquals("a;\nz;b;", sourceTable.getRawData());
	}

	/**
	 * The round trip happens only for a literal value containing <code>;</code>. A <code>$n</code> column reference,
	 * a column appended past the last one, or a line feed without <code>;</code> leave the table as is while the raw
	 * text is re-serialized: the table and the raw text then disagree on the number of columns or rows.
	 */
	@Test
	void appendWithoutSeparatorInLiteralDoesNotRoundTrip() {
		// $n reference: the referenced cell contains ";" but no column is added
		SourceTable sourceTable = SourceTable.builder().table(table("a|x;y")).build();
		computeProcessor(sourceTable).process(Append.builder().column(1).value("$2").build());
		assertEquals(table("ax;y|x;y"), sourceTable.getTable());
		assertEquals("ax;y;x;y;", sourceTable.getRawData());

		// New column past the last one: the value is added as one cell
		sourceTable = SourceTable.builder().table(table("a")).build();
		computeProcessor(sourceTable).process(Append.builder().column(2).value("p;q").build());
		assertEquals(table("a|p;q"), sourceTable.getTable());
		assertEquals("a;p;q;", sourceTable.getRawData());

		// Line feed without ";": one row in the table, two lines in the raw text
		sourceTable = SourceTable.builder().table(table("a|b")).build();
		computeProcessor(sourceTable).process(Append.builder().column(1).value("\nz").build());
		assertEquals(table("a\nz|b"), sourceTable.getTable());
		assertEquals("a\nz;b;", sourceTable.getRawData());
	}

	/**
	 * <code>replace</code> always re-splits the table, whatever the values: a cell holding <code>;</code> or a line
	 * feed before the compute is split too, even when the replacement does not touch it.
	 */
	@Test
	void replaceAlwaysRoundTrips() {
		SourceTable sourceTable = SourceTable.builder().table(table("a|b")).build();
		computeProcessor(sourceTable).process(Replace.builder().column(1).existingValue("a").newValue("a;x").build());
		assertEquals(table("a|x|b"), sourceTable.getTable());

		sourceTable = SourceTable.builder().table(table("a\nb|c;d")).build();
		computeProcessor(sourceTable).process(Replace.builder().column(1).existingValue("zzz").newValue(EMPTY).build());
		assertEquals(table("a", "b|c|d"), sourceTable.getTable());
		assertEquals("a;\nb;c;d;", sourceTable.getRawData());
	}

	/**
	 * <code>translate</code> re-splits the table only when a translated value contains <code>;</code>; a translated
	 * value holding a line feed stays one cell in the table while the raw text shows two lines.
	 */
	@Test
	void translateRoundTripsOnlyWhenTranslationContainsSeparator() {
		SourceTable sourceTable = SourceTable.builder().table(table("a|b", "z|b")).build();
		final Map<String, String> translations = new LinkedHashMap<>();
		translations.put("a", "p;q");
		translations.put("default", "line1\nline2");
		final TranslationTable translationTable = TranslationTable.builder().translations(translations).build();
		computeProcessor(sourceTable).process(Translate.builder().column(1).translationTable(translationTable).build());
		assertEquals(table("p|q|b", "line1", "line2|b"), sourceTable.getTable());

		// No ";" in any translation: no round trip
		sourceTable = SourceTable.builder().table(table("z|b")).build();
		computeProcessor(sourceTable).process(Translate.builder().column(1).translationTable(translationTable).build());
		assertEquals(table("line1\nline2|b"), sourceTable.getTable());
		assertEquals("line1\nline2;b;", sourceTable.getRawData());
	}

	// ---------------------------------------------------------------------------------------------------------------
	// table compute on a text-only source
	// ---------------------------------------------------------------------------------------------------------------

	/**
	 * A table compute on a text-only source (<code>http</code>, <code>file</code>, <code>ipmiRaw</code>) leaves the
	 * table empty. Most of them also re-serialize the empty table, which erases the raw text; only the computes that
	 * bail out on an empty table (<code>append</code>, <code>prepend</code>, the matching-lines computes) keep it.
	 */
	@Test
	void tableComputeOnTextOnlySourceLeavesEmptyTableAndMostlyErasesRawText() {
		for (final var compute : List.of(
			KeepColumns.builder().columnNumbers("1").build(),
			DuplicateColumn.builder().column(1).build(),
			Head.builder().lines("1").build(),
			Replace.builder().column(1).existingValue("a").newValue("b").build()
		)) {
			final SourceTable sourceTable = SourceTable.builder().rawData("text").build();
			compute.accept(computeProcessor(sourceTable));
			assertEquals(List.of(), sourceTable.getTable(), compute.toString());
			assertEquals(EMPTY, sourceTable.getRawData(), compute.toString());
		}

		for (final var compute : List.of(
			Append.builder().column(1).value("x").build(),
			Prepend.builder().column(1).value("x").build(),
			KeepOnlyMatchingLines.builder().column(1).regExp("a").build(),
			ExcludeMatchingLines.builder().column(1).regExp("a").build()
		)) {
			final SourceTable sourceTable = SourceTable.builder().rawData("text").build();
			compute.accept(computeProcessor(sourceTable));
			assertEquals(List.of(), sourceTable.getTable(), compute.toString());
			assertEquals("text", sourceTable.getRawData(), compute.toString());
		}
	}

	// ---------------------------------------------------------------------------------------------------------------
	// sql / jmx
	// ---------------------------------------------------------------------------------------------------------------

	/**
	 * <code>sql</code> and <code>jmx</code> serialize their raw text with <code>replaceSeparator=true</code>: the
	 * table keeps a <code>;</code> inside a cell while the raw text shows a <code>,</code>. The processors are pinned
	 * in their extension modules; this is the serialization they call.
	 */
	@Test
	void sqlAndJmxRawTextReplacesSeparatorInsideCells() {
		final List<List<String>> table = table("a;b|c");
		assertEquals("a,b;c;", SourceTable.tableToCsv(table, TABLE_SEP, true));
		assertEquals("a;b;c;", SourceTable.tableToCsv(table, TABLE_SEP, false));
		assertEquals(table("a|b|c"), SourceTable.csvToTable(SourceTable.tableToCsv(table, TABLE_SEP, false), TABLE_SEP));
	}

	// ---------------------------------------------------------------------------------------------------------------
	// tableUnion, executeForEachEntryOf
	// ---------------------------------------------------------------------------------------------------------------

	/**
	 * <code>tableUnion</code> concatenates the tables and joins the non-null raw texts with a line feed, then collapses
	 * each double line feed into one (a triple line feed becomes a double one, since the replacement is not repeated).
	 */
	@Test
	void tableUnionJoinsRawTextAndCollapsesBlankLines() {
		final TelemetryManager telemetryManager = telemetryManagerWith(
			SOURCE_REF,
			SourceTable.builder().table(table("a")).rawData("a;\n\nb;\n\n\nc;").build()
		);
		telemetryManager.getHostProperties()
			.getConnectorNamespace(MY_CONNECTOR_1_NAME)
			.addSourceTable(OTHER_REF, SourceTable.builder().table(table("d")).build());
		telemetryManager.getHostProperties()
			.getConnectorNamespace(MY_CONNECTOR_1_NAME)
			.addSourceTable(ENTRIES_REF, SourceTable.builder().table(table("e|f")).rawData("e;f;").build());

		final SourceTable result = SourceProcessor.builder()
			.telemetryManager(telemetryManager)
			.connectorId(MY_CONNECTOR_1_NAME)
			.build()
			.process(TableUnionSource.builder().tables(List.of(SOURCE_REF, OTHER_REF, ENTRIES_REF)).build());

		assertEquals(table("a", "d", "e|f"), result.getTable());
		assertEquals("a;\nb;\n\nc;\ne;f;", result.getRawData());
	}

	/**
	 * <code>executeForEachEntryOf</code> in LIST mode (the default) joins the non-blank raw texts with a line feed and
	 * appends the non-empty rows of every entry table; a table-only entry contributes rows but no text, so the raw
	 * text can end up empty (never null) while the table is not.
	 */
	@Test
	void executeForEachEntryOfListAppendsRawTextAndTable() {
		final SourceTable result = runForEachEntry(
			null,
			Map.of(
				"a",
				SourceTable.builder().rawData("ra;").table(table("ra")).build(),
				"b",
				SourceTable.builder().table(withZeroCellRow(table("tb", ""))).build(),
				"c",
				SourceTable.builder().rawData("rc;").build()
			)
		);
		assertEquals("ra;\nrc;", result.getRawData());
		// The zero-cell row is dropped, the row holding one empty cell is kept
		assertEquals(table("ra", "tb", ""), result.getTable());

		final SourceTable tableOnly = runForEachEntry(
			null,
			Map.of("a", SourceTable.builder().table(table("ta")).build(), "b", SourceTable.empty(), "c", SourceTable.empty())
		);
		assertEquals(EMPTY, tableOnly.getRawData());
		assertEquals(table("ta"), tableOnly.getTable());
	}

	/**
	 * JSON_ARRAY concatenates the non-blank raw texts with <code>,\n</code> inside <code>[]</code>;
	 * JSON_ARRAY_EXTENDED wraps each entry's raw text with the entry row (serialized with <code>,</code> for
	 * <code>;</code> inside a cell); the custom method surrounds each non-null raw text with the start and end
	 * strings where <code>$n</code> is the entry column. None of them fills the table.
	 */
	@Test
	void executeForEachEntryOfJsonAndCustomConcatenateRawText() {
		final Map<String, SourceTable> results = Map.of(
			"a",
			SourceTable.builder().rawData("{\"v\":1}").table(table("ignored")).build(),
			"b",
			SourceTable.builder().rawData(EMPTY).build(),
			"c",
			SourceTable.builder().rawData("{\"v\":3}").build()
		);

		final SourceTable jsonArray = runForEachEntry(EntryConcatMethod.JSON_ARRAY, results);
		assertEquals("[{\"v\":1},\n{\"v\":3}]", jsonArray.getRawData());
		assertEquals(List.of(), jsonArray.getTable());

		final SourceTable extended = runForEachEntry(EntryConcatMethod.JSON_ARRAY_EXTENDED, results);
		assertEquals(
			"[{\n\"Entry\":{\n\"Full\":\"a;x,y;\",\n\"Column(1)\":\"a\",\n\"Column(2)\":\"x;y\",\n\"Value\":{\"v\":1}\n}\n},\n" +
				"{\n\"Entry\":{\n\"Full\":\"c;x,y;\",\n\"Column(1)\":\"c\",\n\"Column(2)\":\"x;y\",\n\"Value\":{\"v\":3}\n}\n}]",
			extended.getRawData()
		);

		final SourceTable custom = runForEachEntry(
			CustomConcatMethod.builder().concatStart("<$1>").concatEnd("</$1>\n").build(),
			results
		);
		assertEquals("<a>{\"v\":1}</a>\n<b></b>\n<c>{\"v\":3}</c>\n", custom.getRawData());
		assertEquals(List.of(), custom.getTable());
	}

	/**
	 * Runs an HTTP source with <code>path: $1</code> for the entries a, b and c (second column "x;y"), each entry
	 * returning the given source table.
	 */
	private SourceTable runForEachEntry(final IEntryConcatMethod concatMethod, final Map<String, SourceTable> results) {
		final TelemetryManager telemetryManager = telemetryManagerWith(
			ENTRIES_REF,
			SourceTable.builder().table(table("a|x;y", "b|x;y", "c|x;y")).build()
		);
		final HttpSource httpSource = HttpSource.builder()
			.url("http://host")
			.path("$1")
			.executeForEachEntryOf(ExecuteForEachEntryOf.builder().source(ENTRIES_REF).concatMethod(concatMethod).build())
			.build();
		doAnswer(invocation -> {
			final SourceTable entryResult = results.get(invocation.<HttpSource>getArgument(0).getPath());
			// A fresh copy each time, as the real processor builds a new result for each entry
			return SourceTable.builder()
				.rawData(entryResult.getRawData())
				.table(entryResult.getTable().stream().map(ArrayList::new).map(row -> (List<String>) row).toList())
				.build();
		}).when(sourceProcessor).process(any(HttpSource.class));
		return sourceUpdaterProcessor(telemetryManager).process(httpSource);
	}

	// ---------------------------------------------------------------------------------------------------------------
	// AWK variables, HTTP token, copy
	// ---------------------------------------------------------------------------------------------------------------

	/**
	 * An AWK <code>variables</code> entry referencing a source exposes the table when there is one, else the raw text
	 * split with {@link SourceTable#csvToTable(String, String)} (an HTTP body is split on line feeds and
	 * <code>;</code>), else an empty array.
	 */
	@Test
	void awkVariableReferencingTextOnlySourceExposesCsvOfRawText() {
		assertEquals(
			table("a|b", "c"),
			resolveVariable(SourceTable.builder().table(table("a|b", "c")).rawData("ignored;").build())
		);
		assertEquals(table("a|b", "c"), resolveVariable(SourceTable.builder().rawData("a;b\n\nc;").build()));
		assertEquals(List.of(), resolveVariable(SourceTable.empty()));
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
	 * The HTTP authentication token is the first cell of the first row when the table is not empty, else the raw
	 * text up to the first <code>;</code>, line feeds included.
	 */
	@Test
	void httpTokenIsFirstCellElseRawTextUpToFirstSeparator() {
		assertEquals("tok", extractToken(SourceTable.builder().table(table("tok|x")).rawData("ignored").build()));
		assertEquals("tok", extractToken(SourceTable.builder().rawData("tok;en\nline2").build()));
		assertEquals("abc\ndef", extractToken(SourceTable.builder().rawData("abc\ndef").build()));
		assertEquals(EMPTY, extractToken(SourceTable.builder().rawData(EMPTY).build()));
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
	 * <code>copy</code> of a text-only source keeps the raw text and an empty table; it also drops the empty rows of
	 * a table and copies the other rows.
	 */
	@Test
	void copyOfTextOnlySourcePreservesRawText() {
		final TelemetryManager telemetryManager = telemetryManagerWith(
			SOURCE_REF,
			SourceTable.builder().rawData("body\n").build()
		);
		telemetryManager.getHostProperties()
			.getConnectorNamespace(MY_CONNECTOR_1_NAME)
			.addSourceTable(OTHER_REF, SourceTable.builder().table(withZeroCellRow(table("a|b", "c"))).build());
		final SourceProcessor processor = SourceProcessor.builder()
			.telemetryManager(telemetryManager)
			.connectorId(MY_CONNECTOR_1_NAME)
			.build();

		final SourceTable textCopy = processor.process(CopySource.builder().from(SOURCE_REF).build());
		assertEquals("body\n", textCopy.getRawData());
		assertEquals(List.of(), textCopy.getTable());

		final SourceTable tableCopy = processor.process(CopySource.builder().from(OTHER_REF).build());
		assertNull(tableCopy.getRawData());
		assertEquals(table("a|b", "c"), tableCopy.getTable());
	}
}
