package org.metricshub.engine.strategy.source.compute;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
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
import org.metricshub.engine.strategy.source.SourceResult;

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

	private static ComputeProcessor processor(final SourceResult sourceResult) {
		return ComputeProcessor.builder().hostname(LOCALHOST).sourceResult(sourceResult).build();
	}

	// ---------------------------------------------------------------------------------------------------------------
	// toTable
	// ---------------------------------------------------------------------------------------------------------------

	@Test
	void toTableSplitsLinesOnTheSeparator() {
		final SourceResult sourceResult = SourceResult.ofText("a;b;c\nx;;z;\n\n1;2.5;3\n");
		processor(sourceResult).process(ToTable.builder().build());
		// Empty cells kept, the cell after a trailing separator and the blank line dropped
		assertEquals(SourceResult.ofTable(table("a|b|c", "x||z", "1|2.5|3")), sourceResult);
	}

	@Test
	void toTableHonorsSeparatorFiltersAndSelectColumns() {
		final SourceResult sourceResult = SourceResult.ofText("h1,h2,h3\na,b;x,c\nskip,me,now\nd,e,f");
		processor(sourceResult).process(
			ToTable.builder().separator(",").exclude("^skip").keep("^[a-z],").selectColumns("1,3").build()
		);
		// The ";" inside a cell is protected (replaced by ",") since the separator is not ";"
		assertEquals(table("a|c", "d|f"), sourceResult.getTable());

		final SourceResult pipe = SourceResult.ofText("a|b|c\nd|e|f");
		processor(pipe).process(ToTable.builder().separator("|").build());
		assertEquals(table("a|b|c", "d|e|f"), pipe.getTable());
	}

	@Test
	void toTableOnEmptyTextGivesEmptyTable() {
		final SourceResult sourceResult = SourceResult.ofText(EMPTY);
		processor(sourceResult).process(ToTable.builder().build());
		assertEquals(SourceResult.empty(), sourceResult);

		final SourceResult noText = SourceResult.empty();
		processor(noText).process(ToTable.builder().build());
		assertEquals(SourceResult.empty(), noText);
	}

	/**
	 * A <code>toTable</code> with line filters or a column selection on a table applies them to its text, as the
	 * legacy toTable did with the raw text of a table (an <code>sql</code> result, a table after a table compute).
	 */
	@Test
	void toTableWithOptionsOnATableReadsItsText() {
		final SourceResult sourceResult = SourceResult.ofTable(table("MSHW|a;x|b", "other|c|d", "MSHW|e|f"));
		processor(sourceResult).process(ToTable.builder().keep("^MSHW;").selectColumns("2,3").build());
		assertEquals(SourceResult.ofTable(table("a,x|b", "e|f")), sourceResult);
	}

	// ---------------------------------------------------------------------------------------------------------------
	// toText
	// ---------------------------------------------------------------------------------------------------------------

	@Test
	void toTextSerializesWithTrailingSeparatorAndCommaForSeparatorInCells() {
		final SourceResult sourceResult = SourceResult.ofTable(table("a;b|c", "|d"));
		processor(sourceResult).process(ToText.builder().build());
		assertEquals(SourceResult.ofText("a,b;c;\n;d;"), sourceResult);
	}

	// ---------------------------------------------------------------------------------------------------------------
	// insertColumn
	// ---------------------------------------------------------------------------------------------------------------

	@Test
	void insertColumnInsertsLiteralOrReferencedValue() {
		final SourceResult sourceResult = SourceResult.ofTable(table("a|b", "c|d"));
		processor(sourceResult).process(InsertColumn.builder().column(2).value("MSHW").build());
		assertEquals(SourceResult.ofTable(table("a|MSHW|b", "c|MSHW|d")), sourceResult);

		processor(sourceResult).process(InsertColumn.builder().column(1).value("$3-$1").build());
		assertEquals(table("b-a|a|MSHW|b", "d-c|c|MSHW|d"), sourceResult.getTable());

		// Past the last column, and a reference to an absent column
		processor(sourceResult).process(InsertColumn.builder().column(5).value("$9;x").build());
		assertEquals(table("b-a|a|MSHW|b|;x", "d-c|c|MSHW|d|;x"), sourceResult.getTable());

		// A reference too large for an int is an absent column
		processor(sourceResult).process(InsertColumn.builder().column(1).value("$9999999999").build());
		assertEquals("", sourceResult.getTable().get(0).get(0));
	}

	@Test
	void toTableUsesALiteralSeparatorAndKeepsSparseRows() {
		final SourceResult sourceResult = SourceResult.ofText("a::b::c\nd\n\ne::f");
		processor(sourceResult).process(ToTable.builder().separator("::").selectColumns("2,3-").build());
		// "d" has no column 2: an empty cell keeps the row; the blank line gives no row
		assertEquals(table("b|c", "", "f"), sourceResult.getTable());
		assertEquals(ContentType.TABLE, sourceResult.getContentType());
	}

	@Test
	void toTableBoundsAClosedRange() {
		final SourceResult sourceResult = SourceResult.ofText("a;b");
		processor(sourceResult).process(ToTable.builder().selectColumns("1-2147483647").build());
		assertEquals(ComputeProcessor.MAX_RANGE_PADDING, sourceResult.getTable().get(0).size());
		assertEquals("b", sourceResult.getTable().get(0).get(1));

		// A column past the padding limit is still selected when present
		final SourceResult wide = SourceResult.ofText(";".repeat(1000) + "w;x");
		processor(wide).process(ToTable.builder().selectColumns("1001,1002-2147483647").build());
		assertEquals(List.of("w", "x"), firstRow(wide).subList(0, 2));
		assertEquals(1 + ComputeProcessor.MAX_RANGE_PADDING, firstRow(wide).size());

		// A selection ending at the largest column number terminates
		final SourceResult last = SourceResult.ofText("a;b");
		processor(last).process(ToTable.builder().selectColumns("2147483647,2147483640-2147483647").build());
		assertEquals(List.of("", "", "", "", "", "", "", "", ""), firstRow(last));

		// Columns are 1-based: 0 and a range ending at 0 select nothing, unlike an open range
		final SourceResult zero = SourceResult.ofText("a;b");
		processor(zero).process(ToTable.builder().selectColumns("0,1-0,2-").build());
		assertEquals(List.of("b"), firstRow(zero));
	}

	@Test
	void toTableCollapsesABlankSeparator() {
		final SourceResult sourceResult = SourceResult.ofText("  a   b  \nc\td e");
		processor(sourceResult).process(ToTable.builder().separator(" ").selectColumns("2,1").build());
		// Runs of the separator are one separator and leading ones give no cell, as the commandLine selection
		assertEquals(table("b|a", "e|c\td"), sourceResult.getTable());

		// A line of separators only gives no row, as a blank line
		final SourceResult blank = SourceResult.ofText("   \nx");
		processor(blank).process(ToTable.builder().separator(" ").build());
		assertEquals(table("x"), blank.getTable());
	}

	private static List<String> firstRow(final SourceResult sourceResult) {
		return sourceResult.getTable().get(0);
	}

	@Test
	void explicitConversionsTypeAnEmptyResult() {
		final SourceResult fromEmptyText = SourceResult.ofText("");
		processor(fromEmptyText).process(ToTable.builder().build());
		assertEquals(ContentType.TABLE, fromEmptyText.getContentType());

		final SourceResult fromEmptyTable = SourceResult.ofTable(new ArrayList<>());
		processor(fromEmptyTable).process(ToText.builder().build());
		assertEquals(SourceResult.ofText(""), fromEmptyTable);

		// The awk compute writes text
		processor(fromEmptyText).process(Awk.builder().script("{ print }").build());
		assertEquals(ContentType.TEXT, fromEmptyText.getContentType());
	}

	@Test
	void toTextWritesACellWithLineBreaksOnOneLine() {
		final SourceResult sourceResult = SourceResult.ofTable(table("a\r\nb|c", "d\n\ne|f\rg"));
		processor(sourceResult).process(ToText.builder().build());
		assertEquals("a b;c;\nd e;f g;", sourceResult.getText());
	}

	@Test
	void toTableKeepsTheDefaultSeparatorWhenAReferenceResolvesToNothing() {
		final ToTable toTable = ToTable.builder().separator("${attribute::sep}").build();
		toTable.update(value -> value == null ? null : value.replace("${attribute::sep}", ""));
		assertEquals(";", toTable.getSeparator());
	}

	@Test
	void toTableNormalizesCrlf() {
		final SourceResult sourceResult = SourceResult.ofText("a;b\r\nc;d\r\n");
		processor(sourceResult).process(ToTable.builder().build());
		assertEquals(table("a|b", "c|d"), sourceResult.getTable());
	}

	/**
	 * A source may hand out fixed-size rows (ipmiRaw builds its row with Arrays.asList) or null cells: the compute
	 * builds fresh rows and treats a null cell as empty.
	 */
	@Test
	void insertColumnCopesWithFixedSizeRowsAndNullCells() {
		final SourceResult sourceResult = SourceResult.ofTable(
			new ArrayList<>(List.of(Arrays.asList("a", null), Arrays.asList("c", "d")))
		);
		processor(sourceResult).process(InsertColumn.builder().column(3).value("[$2]").build());
		assertEquals(List.of(Arrays.asList("a", null, "[]"), List.of("c", "d", "[d]")), sourceResult.getTable());
	}

	@Test
	void insertColumnPadsShortRowsAndLeavesInvalidArgumentsAlone() {
		// A short row (an awk whose last field is empty, split by toTable) is padded: the cell lands at its column
		final SourceResult sourceResult = SourceResult.ofTable(table("a|b", "c"));
		processor(sourceResult).process(InsertColumn.builder().column(3).value("x").build());
		assertEquals(table("a|b|x", "c||x"), sourceResult.getTable());

		processor(sourceResult).process(InsertColumn.builder().column(0).value("x").build());
		assertEquals(table("a|b|x", "c||x"), sourceResult.getTable());

		// A column far past the rows is a mistake: the rows are left unchanged rather than padded up to it
		processor(sourceResult).process(InsertColumn.builder().column(1_000_000_000).value("x").build());
		assertEquals(table("a|b|x", "c||x"), sourceResult.getTable());

		// A wide row is no mistake: column 1001 appends to a 1000-cell row
		final List<String> wide = new ArrayList<>(java.util.Collections.nCopies(1000, "v"));
		final SourceResult wideTable = SourceResult.ofTable(new ArrayList<>(List.of(wide)));
		processor(wideTable).process(InsertColumn.builder().column(1001).value("x").build());
		assertEquals(1001, wideTable.getTable().get(0).size());
		assertEquals("x", wideTable.getTable().get(0).get(1000));

		processor(sourceResult).process((InsertColumn) null);
		assertEquals(table("a|b|x", "c||x"), sourceResult.getTable());
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
		// An awk reads either (#1559)
		assertNull(Awk.builder().script("{}").build().getInputContentType());
		assertEquals(ContentType.TEXT, Awk.builder().script("{}").build().getOutputContentType());
		assertEquals(ContentType.TEXT, Json2Csv.builder().build().getInputContentType());
		assertEquals(ContentType.TABLE, Json2Csv.builder().build().getOutputContentType());
		assertEquals(ContentType.TABLE, KeepColumns.builder().columnNumbers("1").build().getInputContentType());
		assertEquals(ContentType.TABLE, KeepColumns.builder().columnNumbers("1").build().getOutputContentType());
	}

	@Test
	void sourceResultFactoriesAndContentType() {
		assertEquals(ContentType.TEXT, SourceResult.ofText("x").getContentType());
		assertEquals(ContentType.TEXT, SourceResult.ofText(null).getContentType());
		assertEquals(EMPTY, SourceResult.ofText(null).getText());
		assertEquals(ContentType.TABLE, SourceResult.ofTable(table("a")).getContentType());
		assertEquals(ContentType.TABLE, SourceResult.ofTable(null).getContentType());
		assertEquals(List.of(), SourceResult.ofTable(null).getTable());
		assertEquals(ContentType.TABLE, SourceResult.empty().getContentType());
		// A new instance each time: the strategies compare the retry sentinel by identity
		assertNotSame(SourceResult.empty(), SourceResult.empty());

		// A reader of the other type sees an empty value
		assertEquals(List.of(), SourceResult.ofText("a;b").getTable());
		assertEquals(EMPTY, SourceResult.ofTable(table("a|b")).getText());

		// The setters switch the type and drop the other side
		final SourceResult sourceResult = SourceResult.ofText("a;b");
		sourceResult.setTable(table("c"));
		assertEquals(SourceResult.ofTable(table("c")), sourceResult);
		sourceResult.setText("d");
		assertEquals(SourceResult.ofText("d"), sourceResult);
		assertEquals(List.of(), sourceResult.getTable());
	}

	/**
	 * The conversions return new values and never change the source table: a stored source is read in place.
	 */
	@Test
	void conversionsReturnNewValues() {
		final SourceResult text = SourceResult.ofText("a;b\r\nc;");
		assertEquals(table("a|b", "c"), text.asTable());
		assertEquals("a;b\nc;", text.asText());
		assertEquals(SourceResult.ofText("a;b\nc;"), text);

		final SourceResult tableSource = SourceResult.ofTable(table("a;x|b"));
		assertEquals("a,x;b;", tableSource.asText());
		final List<List<String>> copy = tableSource.asTable();
		assertEquals(table("a;x|b"), copy);
		copy.get(0).set(0, "changed");
		assertEquals(SourceResult.ofTable(table("a;x|b")), tableSource);
	}
}
