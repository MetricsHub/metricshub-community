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
		assertEquals(SourceTable.ofTable(table("a|b|c", "x||z", "1|2.5|3")), sourceTable);
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
		assertEquals(SourceTable.empty(), sourceTable);

		final SourceTable noText = SourceTable.empty();
		processor(noText).process(ToTable.builder().build());
		assertEquals(SourceTable.empty(), noText);
	}

	/**
	 * The <code>toTable</code> the content-type desugaring inserts after a legacy text producer
	 * (<code>commandLine</code>, the <code>awk</code> source and compute) converts like a written one: the canonical
	 * conversion, {@link SourceTable#asTable()}. A table is left as it is.
	 */
	@Test
	void generatedToTableConverts() {
		final SourceTable sourceTable = SourceTable.ofText("a;b;;\r\n\r\n;\nc");
		processor(sourceTable).process(ToTable.generated());
		// One trailing empty cell dropped, the blank line dropped, ";" a row of one empty cell, CRLF stripped
		assertEquals(SourceTable.ofTable(table("a|b|", "", "c")), sourceTable);

		final SourceTable alreadyATable = SourceTable.ofTable(table("built"));
		processor(alreadyATable).process(ToTable.generated());
		assertEquals(SourceTable.ofTable(table("built")), alreadyATable);
	}

	/**
	 * A <code>toTable</code> with line filters or a column selection on a table applies them to its text, as the
	 * legacy toTable did with the raw text of a table (an <code>sql</code> result, a table after a table compute).
	 */
	@Test
	void toTableWithOptionsOnATableReadsItsText() {
		final SourceTable sourceTable = SourceTable.ofTable(table("MSHW|a;x|b", "other|c|d", "MSHW|e|f"));
		processor(sourceTable).process(ToTable.builder().keep("^MSHW;").selectColumns("2,3").build());
		assertEquals(SourceTable.ofTable(table("a,x|b", "e|f")), sourceTable);
	}

	// ---------------------------------------------------------------------------------------------------------------
	// toText
	// ---------------------------------------------------------------------------------------------------------------

	@Test
	void toTextSerializesWithTrailingSeparatorAndCommaForSeparatorInCells() {
		final SourceTable sourceTable = SourceTable.ofTable(table("a;b|c", "|d"));
		processor(sourceTable).process(ToText.builder().build());
		assertEquals(SourceTable.ofText("a,b;c;\n;d;"), sourceTable);
	}

	/**
	 * The <code>toText</code> the content-type desugaring inserts before an <code>awk</code> compute fed a table
	 * converts like a written one: the canonical conversion, {@link SourceTable#asText()}. A text is left as it is.
	 */
	@Test
	void generatedToTextConverts() {
		final SourceTable sourceTable = SourceTable.ofTable(table("a;b|c"));
		processor(sourceTable).process(ToText.generated());
		assertEquals(SourceTable.ofText("a,b;c;"), sourceTable);

		final SourceTable alreadyAText = SourceTable.ofText("raw;text");
		processor(alreadyAText).process(ToText.generated());
		assertEquals(SourceTable.ofText("raw;text"), alreadyAText);
	}

	@Test
	void generatedToTextThenAwkMatchesExplicitToTextThenAwk() {
		for (final String script : List.of("{ print }", "BEGIN { }")) {
			final SourceTable viaGenerated = SourceTable.ofTable(table("a;b|c", "|d"));
			processor(viaGenerated).process(ToText.generated());
			processor(viaGenerated).process(Awk.builder().script(script).build());

			final SourceTable viaExplicit = SourceTable.ofTable(table("a;b|c", "|d"));
			processor(viaExplicit).process(ToText.builder().type("toText").build());
			processor(viaExplicit).process(Awk.builder().script(script).build());

			assertEquals(viaExplicit, viaGenerated, script);
		}
		final SourceTable echoed = SourceTable.ofTable(table("a;b|c", "|d"));
		processor(echoed).process(ToText.generated());
		processor(echoed).process(Awk.builder().script("{ print }").build());
		assertEquals(SourceTable.ofText("a,b;c;\n;d;"), echoed);
	}

	// ---------------------------------------------------------------------------------------------------------------
	// insertColumn
	// ---------------------------------------------------------------------------------------------------------------

	@Test
	void insertColumnInsertsLiteralOrReferencedValue() {
		final SourceTable sourceTable = SourceTable.ofTable(table("a|b", "c|d"));
		processor(sourceTable).process(InsertColumn.builder().column(2).value("MSHW").build());
		assertEquals(SourceTable.ofTable(table("a|MSHW|b", "c|MSHW|d")), sourceTable);

		processor(sourceTable).process(InsertColumn.builder().column(1).value("$3-$1").build());
		assertEquals(table("b-a|a|MSHW|b", "d-c|c|MSHW|d"), sourceTable.getTable());

		// Past the last column, and a reference to an absent column
		processor(sourceTable).process(InsertColumn.builder().column(5).value("$9;x").build());
		assertEquals(table("b-a|a|MSHW|b|;x", "d-c|c|MSHW|d|;x"), sourceTable.getTable());

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
		assertEquals(SourceTable.ofText(""), fromEmptyTable);

		// The awk compute writes text
		processor(fromEmptyText).process(Awk.builder().script("{ print }").build());
		assertEquals(ContentType.TEXT, fromEmptyText.getContentType());
	}

	@Test
	void toTextNormalizesCrlfInsideCells() {
		final SourceTable sourceTable = SourceTable.ofTable(table("a\r\nb|c"));
		processor(sourceTable).process(ToText.builder().build());
		assertEquals("a\nb;c;", sourceTable.getText());
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
	}

	@Test
	void insertColumnPadsShortRowsAndLeavesInvalidArgumentsAlone() {
		// A short row (an awk whose last field is empty, split by toTable) is padded: the cell lands at its column
		final SourceTable sourceTable = SourceTable.ofTable(table("a|b", "c"));
		processor(sourceTable).process(InsertColumn.builder().column(3).value("x").build());
		assertEquals(table("a|b|x", "c||x"), sourceTable.getTable());

		processor(sourceTable).process(InsertColumn.builder().column(0).value("x").build());
		assertEquals(table("a|b|x", "c||x"), sourceTable.getTable());

		// A column far past the rows is a mistake: the rows are left unchanged rather than padded up to it
		processor(sourceTable).process(InsertColumn.builder().column(1_000_000_000).value("x").build());
		assertEquals(table("a|b|x", "c||x"), sourceTable.getTable());

		// A wide row is no mistake: column 1001 appends to a 1000-cell row
		final List<String> wide = new ArrayList<>(java.util.Collections.nCopies(1000, "v"));
		final SourceTable wideTable = SourceTable.ofTable(new ArrayList<>(List.of(wide)));
		processor(wideTable).process(InsertColumn.builder().column(1001).value("x").build());
		assertEquals(1001, wideTable.getTable().get(0).size());
		assertEquals("x", wideTable.getTable().get(0).get(1000));

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
		assertEquals(EMPTY, SourceTable.ofText(null).getText());
		assertEquals(ContentType.TABLE, SourceTable.ofTable(table("a")).getContentType());
		assertEquals(ContentType.TABLE, SourceTable.ofTable(null).getContentType());
		assertEquals(List.of(), SourceTable.ofTable(null).getTable());
		assertEquals(ContentType.TABLE, SourceTable.empty().getContentType());
		// A new instance each time: the strategies compare the retry sentinel by identity
		assertNotSame(SourceTable.empty(), SourceTable.empty());

		// A reader of the other type sees an empty value
		assertEquals(List.of(), SourceTable.ofText("a;b").getTable());
		assertEquals(EMPTY, SourceTable.ofTable(table("a|b")).getText());

		// The setters switch the type and drop the other side
		final SourceTable sourceTable = SourceTable.ofText("a;b");
		sourceTable.setTable(table("c"));
		assertEquals(SourceTable.ofTable(table("c")), sourceTable);
		sourceTable.setText("d");
		assertEquals(SourceTable.ofText("d"), sourceTable);
		assertEquals(List.of(), sourceTable.getTable());
	}

	/**
	 * The conversions return new values and never change the source table: a stored source is read in place.
	 */
	@Test
	void conversionsReturnNewValues() {
		final SourceTable text = SourceTable.ofText("a;b\r\nc;");
		assertEquals(table("a|b", "c"), text.asTable());
		assertEquals("a;b\r\nc;", text.asText());
		assertEquals(SourceTable.ofText("a;b\r\nc;"), text);

		final SourceTable tableSource = SourceTable.ofTable(table("a;x|b"));
		assertEquals("a,x;b;", tableSource.asText());
		final List<List<String>> copy = tableSource.asTable();
		assertEquals(table("a;x|b"), copy);
		copy.get(0).set(0, "changed");
		assertEquals(SourceTable.ofTable(table("a;x|b")), tableSource);
	}
}
