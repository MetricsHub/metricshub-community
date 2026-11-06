package org.metricshub.engine.strategy.source.compute;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.metricshub.engine.common.helpers.MetricsHubConstants.EMPTY;
import static org.metricshub.engine.constants.Constants.LOCALHOST;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.metricshub.engine.common.helpers.JsonHelper;
import org.metricshub.engine.connector.model.common.ContentType;
import org.metricshub.engine.connector.model.monitor.task.source.compute.Awk;
import org.metricshub.engine.connector.model.monitor.task.source.compute.Compute;
import org.metricshub.engine.connector.model.monitor.task.source.compute.InsertColumn;
import org.metricshub.engine.connector.model.monitor.task.source.compute.Json2Csv;
import org.metricshub.engine.connector.model.monitor.task.source.compute.KeepColumns;
import org.metricshub.engine.connector.model.monitor.task.source.compute.ToTable;
import org.metricshub.engine.connector.model.monitor.task.source.compute.ToText;
import org.metricshub.engine.strategy.source.SourceTable;

/**
 * The <code>toTable</code>, <code>toText</code> and <code>insertColumn</code> computes (#1510).
 */
class ConversionComputesTest {

	private static List<List<String>> table(final String... rows) {
		final List<List<String>> table = new ArrayList<>();
		for (final String row : rows) {
			table.add(new ArrayList<>(Arrays.asList(row.split("\\|", -1))));
		}
		return table;
	}

	private static ComputeProcessor processor(final SourceTable sourceTable) {
		return ComputeProcessor.builder().hostname(LOCALHOST).sourceTable(sourceTable).build();
	}

	// ---------------------------------------------------------------------------------------------------------------
	// toTable
	// ---------------------------------------------------------------------------------------------------------------

	@Test
	void toTableSplitsLinesOnTheSeparator() {
		final SourceTable sourceTable = SourceTable.ofText("a;b;c\nx;;z;\n\n1;2.5;3\n");
		processor(sourceTable).process(ToTable.builder().build());
		// Empty cells kept, the cell after a trailing separator and the blank line dropped
		assertEquals(table("a|b|c", "x||z", "1|2.5|3"), sourceTable.getTable());
		assertEquals("a;b;c;\nx;;z;\n1;2.5;3;", sourceTable.getRawData());
		assertEquals(ContentType.TABLE, sourceTable.getContentType());
	}

	@Test
	void toTableHonorsSeparatorFiltersAndSelectColumns() {
		final SourceTable sourceTable = SourceTable.ofText("h1,h2,h3\na,b;x,c\nskip,me,now\nd,e,f");
		processor(sourceTable).process(
			ToTable.builder().separator(",").exclude("^skip").keep("^[a-z],").selectColumns("1,3").build()
		);
		// The ";" inside a cell is protected (replaced by ",") since the separator is not ";"
		assertEquals(table("a|c", "d|f"), sourceTable.getTable());

		final SourceTable pipe = SourceTable.ofText("a|b|c\nd|e|f");
		processor(pipe).process(ToTable.builder().separator("|").build());
		assertEquals(table("a|b|c", "d|e|f"), pipe.getTable());
	}

	@Test
	void toTableOnEmptyTextGivesEmptyTable() {
		final SourceTable sourceTable = SourceTable.ofText(EMPTY);
		processor(sourceTable).process(ToTable.builder().build());
		assertEquals(List.of(), sourceTable.getTable());
		assertEquals(EMPTY, sourceTable.getRawData());

		final SourceTable noText = SourceTable.empty();
		processor(noText).process(ToTable.builder().build());
		assertEquals(List.of(), noText.getTable());
	}

	/**
	 * The implicit <code>toTable</code> follows a legacy producer that built its table already: it must not touch
	 * the source table, not even when the table is empty and the raw text is stale.
	 */
	@Test
	void implicitToTableIsANoOp() {
		final SourceTable sourceTable = SourceTable.builder().rawData("stale;text").table(table("built")).build();
		processor(sourceTable).process(ToTable.implicit());
		assertEquals(table("built"), sourceTable.getTable());
		assertEquals("stale;text", sourceTable.getRawData());

		final SourceTable emptyTable = SourceTable.builder().rawData("stale;text").build();
		processor(emptyTable).process(ToTable.implicit());
		assertEquals(List.of(), emptyTable.getTable());
		assertEquals("stale;text", emptyTable.getRawData());
	}

	// ---------------------------------------------------------------------------------------------------------------
	// toText
	// ---------------------------------------------------------------------------------------------------------------

	@Test
	void toTextSerializesWithTrailingSeparatorAndCommaForSeparatorInCells() {
		final SourceTable sourceTable = SourceTable.ofTable(table("a;b|c", "|d"));
		processor(sourceTable).process(ToText.builder().build());
		assertEquals("a,b;c;\n;d;", sourceTable.getRawData());
		assertEquals(List.of(), sourceTable.getTable());
		assertEquals(ContentType.TEXT, sourceTable.getContentType());
	}

	/**
	 * The implicit <code>toText</code> precedes an <code>awk</code> compute that serializes the table itself: it
	 * must not touch the source table (an empty awk result leaves the raw text as it was, null included).
	 */
	@Test
	void implicitToTextIsANoOp() {
		final SourceTable tableOnly = SourceTable.ofTable(table("a;b|c"));
		processor(tableOnly).process(ToText.implicit());
		assertNull(tableOnly.getRawData());
		assertEquals(table("a;b|c"), tableOnly.getTable());

		final SourceTable dual = SourceTable.builder().rawData("raw;text").table(table("a;b|c")).build();
		processor(dual).process(ToText.implicit());
		assertEquals("raw;text", dual.getRawData());
	}

	@Test
	void implicitToTextThenAwkMatchesAwkOnTheTable() {
		for (final String script : List.of("{ print }", "BEGIN { }")) {
			final SourceTable viaToText = SourceTable.ofTable(table("a;b|c", "|d"));
			processor(viaToText).process(ToText.implicit());
			processor(viaToText).process(Awk.builder().script(script).build());

			final SourceTable direct = SourceTable.ofTable(table("a;b|c", "|d"));
			processor(direct).process(Awk.builder().script(script).build());

			assertEquals(direct, viaToText, script);
		}
	}

	// ---------------------------------------------------------------------------------------------------------------
	// insertColumn
	// ---------------------------------------------------------------------------------------------------------------

	@Test
	void insertColumnInsertsLiteralOrReferencedValue() {
		final SourceTable sourceTable = SourceTable.ofTable(table("a|b", "c|d"));
		processor(sourceTable).process(InsertColumn.builder().column(2).value("MSHW").build());
		assertEquals(table("a|MSHW|b", "c|MSHW|d"), sourceTable.getTable());
		assertEquals("a;MSHW;b;\nc;MSHW;d;", sourceTable.getRawData());

		processor(sourceTable).process(InsertColumn.builder().column(1).value("$3-$1").build());
		assertEquals(table("b-a|a|MSHW|b", "d-c|c|MSHW|d"), sourceTable.getTable());

		// Past the last column, and a reference to an absent column
		processor(sourceTable).process(InsertColumn.builder().column(5).value("$9;x").build());
		assertEquals(table("b-a|a|MSHW|b|;x", "d-c|c|MSHW|d|;x"), sourceTable.getTable());
		// The value is one cell whatever it holds: no round trip, and the raw text shows "," for the ";" inside it
		assertEquals("b-a;a;MSHW;b;,x;\nd-c;c;MSHW;d;,x;", sourceTable.getRawData());

		// A reference too large for an int is an absent column
		processor(sourceTable).process(InsertColumn.builder().column(1).value("$9999999999").build());
		assertEquals("", sourceTable.getTable().get(0).get(0));
	}

	@Test
	void toTableUsesALiteralSeparatorAndKeepsSparseRows() {
		final SourceTable sourceTable = SourceTable.ofText("a::b::c\nd\n\ne::f");
		processor(sourceTable).process(ToTable.builder().separator("::").selectColumns("2,3-").build());
		// "d" has no column 2: an empty cell keeps the row; the blank line gives no row
		assertEquals(table("b|c", "", "f"), sourceTable.getTable());
		assertEquals(ContentType.TABLE, sourceTable.getContentType());
	}

	@Test
	void toTableBoundsAClosedRange() {
		final SourceTable sourceTable = SourceTable.ofText("a;b");
		processor(sourceTable).process(ToTable.builder().selectColumns("1-2147483647").build());
		assertEquals(ComputeProcessor.MAX_RANGE_PADDING, sourceTable.getTable().get(0).size());
		assertEquals("b", sourceTable.getTable().get(0).get(1));

		// A column past the padding limit is still selected when present
		final SourceTable wide = SourceTable.ofText(";".repeat(1000) + "w;x");
		processor(wide).process(ToTable.builder().selectColumns("1001,1002-2147483647").build());
		assertEquals(List.of("w", "x"), firstRow(wide).subList(0, 2));
		assertEquals(1 + ComputeProcessor.MAX_RANGE_PADDING, firstRow(wide).size());

		// A selection ending at the largest column number terminates
		final SourceTable last = SourceTable.ofText("a;b");
		processor(last).process(ToTable.builder().selectColumns("2147483647,2147483640-2147483647").build());
		assertEquals(List.of("", "", "", "", "", "", "", "", ""), firstRow(last));

		// Columns are 1-based: 0 and a range ending at 0 select nothing, unlike an open range
		final SourceTable zero = SourceTable.ofText("a;b");
		processor(zero).process(ToTable.builder().selectColumns("0,1-0,2-").build());
		assertEquals(List.of("b"), firstRow(zero));
	}

	@Test
	void toTableCollapsesABlankSeparator() {
		final SourceTable sourceTable = SourceTable.ofText("  a   b  \nc\td e");
		processor(sourceTable).process(ToTable.builder().separator(" ").selectColumns("2,1").build());
		// Runs of the separator are one separator and leading ones give no cell, as the commandLine selection
		assertEquals(table("b|a", "e|c\td"), sourceTable.getTable());

		// A line of separators only gives no row, as a blank line
		final SourceTable blank = SourceTable.ofText("   \nx");
		processor(blank).process(ToTable.builder().separator(" ").build());
		assertEquals(table("x"), blank.getTable());
	}

	private static List<String> firstRow(final SourceTable sourceTable) {
		return sourceTable.getTable().get(0);
	}

	@Test
	void explicitConversionsTypeAnEmptyResult() {
		final SourceTable fromEmptyText = SourceTable.ofText("");
		processor(fromEmptyText).process(ToTable.builder().build());
		assertEquals(ContentType.TABLE, fromEmptyText.getContentType());

		final SourceTable fromEmptyTable = SourceTable.ofTable(new ArrayList<>());
		processor(fromEmptyTable).process(ToText.builder().build());
		assertEquals(ContentType.TEXT, fromEmptyTable.getContentType());
		assertEquals("", fromEmptyTable.getRawData());

		// A legacy compute types its result by its content again
		processor(fromEmptyText).process(Awk.builder().script("{ print }").build());
		assertEquals(ContentType.TEXT, fromEmptyText.getContentType());
	}

	@Test
	void toTextNormalizesCrlfInsideCells() {
		final SourceTable sourceTable = SourceTable.ofTable(table("a\r\nb|c"));
		processor(sourceTable).process(ToText.builder().build());
		assertEquals("a\nb;c;", sourceTable.getRawData());
	}

	@Test
	void toTableKeepsTheDefaultSeparatorWhenAReferenceResolvesToNothing() {
		final ToTable toTable = ToTable.builder().separator("${attribute::sep}").build();
		toTable.update(value -> value == null ? null : value.replace("${attribute::sep}", ""));
		assertEquals(";", toTable.getSeparator());
	}

	@Test
	void toTableNormalizesCrlf() {
		final SourceTable sourceTable = SourceTable.ofText("a;b\r\nc;d\r\n");
		processor(sourceTable).process(ToTable.builder().build());
		assertEquals(table("a|b", "c|d"), sourceTable.getTable());
		assertEquals("a;b;\nc;d;", sourceTable.getRawData());
	}

	/**
	 * A source may hand out fixed-size rows (ipmiRaw builds its row with Arrays.asList) or null cells: the compute
	 * builds fresh rows and treats a null cell as empty.
	 */
	@Test
	void insertColumnCopesWithFixedSizeRowsAndNullCells() {
		final SourceTable sourceTable = SourceTable.ofTable(
			new ArrayList<>(List.of(Arrays.asList("a", null), Arrays.asList("c", "d")))
		);
		processor(sourceTable).process(InsertColumn.builder().column(3).value("[$2]").build());
		assertEquals(List.of(Arrays.asList("a", null, "[]"), List.of("c", "d", "[d]")), sourceTable.getTable());
		assertEquals("a;;[];\nc;d;[d];", sourceTable.getRawData());
	}

	@Test
	void insertColumnPadsShortRowsAndLeavesInvalidArgumentsAlone() {
		// A short row (an awk whose last field is empty, split by toTable) is padded: the cell lands at its column
		final SourceTable sourceTable = SourceTable.ofTable(table("a|b", "c"));
		processor(sourceTable).process(InsertColumn.builder().column(3).value("x").build());
		assertEquals(table("a|b|x", "c||x"), sourceTable.getTable());

		processor(sourceTable).process(InsertColumn.builder().column(0).value("x").build());
		assertEquals(table("a|b|x", "c||x"), sourceTable.getTable());

		processor(sourceTable).process((InsertColumn) null);
		assertEquals(table("a|b|x", "c||x"), sourceTable.getTable());
	}

	// ---------------------------------------------------------------------------------------------------------------
	// Model
	// ---------------------------------------------------------------------------------------------------------------

	@Test
	void computesDeserializeFromYaml() throws Exception {
		final Compute toTable = JsonHelper.buildYamlMapper()
			.readValue("type: toTable\nseparator: ','\nkeep: ^a\nexclude: ^b\nselectColumns: 1,2", Compute.class);
		assertEquals(
			ToTable.builder().type("toTable").separator(",").keep("^a").exclude("^b").selectColumns("1,2").build(),
			toTable
		);
		assertEquals(ContentType.TEXT, toTable.getInputContentType());
		assertEquals(ContentType.TABLE, toTable.getOutputContentType());

		final Compute toText = JsonHelper.buildYamlMapper().readValue("type: toText", Compute.class);
		assertInstanceOf(ToText.class, toText);
		assertEquals(ContentType.TABLE, toText.getInputContentType());
		assertEquals(ContentType.TEXT, toText.getOutputContentType());

		final Compute insertColumn = JsonHelper.buildYamlMapper()
			.readValue("type: insertColumn\ncolumn: 2\nvalue: $1", Compute.class);
		assertEquals(InsertColumn.builder().type("insertColumn").column(2).value("$1").build(), insertColumn);
		assertEquals(ContentType.TABLE, insertColumn.getInputContentType());
		assertEquals(ContentType.TABLE, insertColumn.getOutputContentType());

		// The content types are not YAML fields
		assertNull(JsonHelper.buildYamlMapper().valueToTree(toText).get("outputContentType"));
	}

	@Test
	void contentTypesOfTheOtherComputes() {
		assertEquals(ContentType.TEXT, Awk.builder().script("{}").build().getInputContentType());
		assertEquals(ContentType.TEXT, Awk.builder().script("{}").build().getOutputContentType());
		assertEquals(ContentType.TEXT, Json2Csv.builder().build().getInputContentType());
		assertEquals(ContentType.TABLE, Json2Csv.builder().build().getOutputContentType());
		assertEquals(ContentType.TABLE, KeepColumns.builder().columnNumbers("1").build().getInputContentType());
		assertEquals(ContentType.TABLE, KeepColumns.builder().columnNumbers("1").build().getOutputContentType());
	}

	@Test
	void sourceTableFactoriesAndContentType() {
		assertEquals(ContentType.TEXT, SourceTable.ofText("x").getContentType());
		assertEquals(ContentType.TEXT, SourceTable.ofText(null).getContentType());
		assertEquals(EMPTY, SourceTable.ofText(null).getRawData());
		assertEquals(ContentType.TABLE, SourceTable.ofTable(table("a")).getContentType());
		assertEquals(ContentType.TABLE, SourceTable.ofTable(null).getContentType());
		assertEquals(ContentType.TABLE, SourceTable.empty().getContentType());
		// Dual: the table wins
		assertEquals(ContentType.TABLE, SourceTable.builder().rawData("x").table(table("a")).build().getContentType());
	}
}
