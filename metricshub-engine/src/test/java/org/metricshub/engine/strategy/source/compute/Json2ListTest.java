package org.metricshub.engine.strategy.source.compute;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.metricshub.engine.constants.Constants.LOCALHOST;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.metricshub.engine.common.helpers.JsonHelper;
import org.metricshub.engine.connector.model.common.ContentType;
import org.metricshub.engine.connector.model.monitor.task.source.compute.Awk;
import org.metricshub.engine.connector.model.monitor.task.source.compute.Compute;
import org.metricshub.engine.connector.model.monitor.task.source.compute.Json2List;
import org.metricshub.engine.strategy.source.SourceTable;

/**
 * The <code>json2List</code> compute (#1561): one row per leaf value, its JFlat path and its value.
 */
class Json2ListTest {

	private static List<List<String>> rows(final String... pathsAndValues) {
		final List<List<String>> rows = new ArrayList<>();
		for (int i = 0; i < pathsAndValues.length; i += 2) {
			rows.add(List.of(pathsAndValues[i], pathsAndValues[i + 1]));
		}
		return rows;
	}

	private static SourceTable json2List(final String json) {
		final SourceTable sourceTable = SourceTable.ofText(json);
		ComputeProcessor.builder()
			.hostname(LOCALHOST)
			.sourceTable(sourceTable)
			.build()
			.process(Json2List.builder().type("json2List").build());
		return sourceTable;
	}

	@Test
	void deserializesReadsTextAndWritesATable() throws Exception {
		final Compute compute = JsonHelper.buildYamlMapper().readValue("type: json2List", Compute.class);
		assertEquals(Json2List.builder().type("json2List").build(), compute);
		assertEquals(compute, compute.copy());
		assertEquals("- type=Json2List", compute.toString());
		assertEquals(ContentType.TEXT, compute.getInputContentType());
		assertEquals(ContentType.TABLE, compute.getOutputContentType());
	}

	@Test
	void objectsAndArraysInDocumentOrder() {
		assertEquals(
			SourceTable.ofTable(
				rows("/b", "1", "/a/x", "y", "/items[0]/name", "n0", "/items[1]/name", "n1", "/items[1]/id", "7")
			),
			json2List("{\"b\":1,\"a\":{\"x\":\"y\"},\"items\":[{\"name\":\"n0\"},{\"name\":\"n1\",\"id\":7}]}")
		);
	}

	@Test
	void nestedArraysAndRootArray() {
		assertEquals(rows("/a[0][0]", "1", "/a[0][1]", "2", "/a[1][0]", "3"), json2List("{\"a\":[[1,2],[3]]}").getTable());
		assertEquals(
			rows("[0]", "1", "[1][0]", "2", "[1][1][0]", "3", "[1][1][1]", "4", "[2]/k", "NULL"),
			json2List("[1,[2,[3,4]],{\"k\":null}]").getTable()
		);
		// Document order, not JFlat's sorted map: [2] before [10]
		final StringBuilder json = new StringBuilder("[");
		for (int i = 0; i < 12; i++) {
			json.append(i == 0 ? "" : ",").append(i);
		}
		final List<List<String>> table = json2List(json.append(']').toString()).getTable();
		assertEquals(List.of("[2]", "2"), table.get(2));
		assertEquals(List.of("[10]", "10"), table.get(10));
	}

	@Test
	void valuesAreKeptWhole() {
		assertEquals(
			rows(
				"/lf",
				"l1\nl2",
				"/crlf",
				"a\r\nb",
				"/semi;colon",
				"v;w",
				"/q",
				"say \"hi\"",
				"/u",
				"\u00e9\u4e2d\ud83d\ude00",
				"/a/b",
				"slash",
				"/sp ace",
				"x",
				"/empty",
				""
			),
			json2List(
				"{\"lf\":\"l1\\nl2\",\"crlf\":\"a\\r\\nb\",\"semi;colon\":\"v;w\",\"q\":\"say \\\"hi\\\"\"," +
					"\"u\":\"\\u00e9\u4e2d\\ud83d\\ude00\",\"a/b\":\"slash\",\"sp ace\":\"x\",\"empty\":\"\"}"
			).getTable()
		);
	}

	/**
	 * Numbers as JFlat writes them ({@link java.math.BigDecimal#toString()}), booleans and null in upper case.
	 */
	@Test
	void scalarsAsJFlatWritesThem() {
		assertEquals(
			rows(
				"/int",
				"42",
				"/neg",
				"-7",
				"/dec",
				"1.50",
				"/exp",
				"1E+3",
				"/small",
				"0.0015",
				"/big",
				"12345678901234567890",
				"/zero",
				"0.0",
				"/t",
				"TRUE",
				"/f",
				"FALSE",
				"/n",
				"NULL"
			),
			json2List(
				"{\"int\":42,\"neg\":-7,\"dec\":1.50,\"exp\":1e3,\"small\":1.5E-3,\"big\":12345678901234567890," +
					"\"zero\":-0.0,\"t\":true,\"f\":false,\"n\":null}"
			).getTable()
		);
	}

	@Test
	void emptyContainersHaveNoRow() {
		assertEquals(rows("/after", "1"), json2List("{\"o\":{},\"a\":[],\"after\":1}").getTable());
		assertEquals(SourceTable.ofTable(new ArrayList<>()), json2List("{}"));
		assertEquals(SourceTable.ofTable(new ArrayList<>()), json2List("[]"));
	}

	/**
	 * A document that does not parse, a scalar, an empty text, a number whose exponent overflows a BigDecimal: an
	 * empty table, as <code>json2Table</code> gives. What follows the root is ignored, as JFlat does.
	 */
	@Test
	void invalidDocumentGivesEmptyTable() {
		for (final String json : List.of(
			"not json",
			"{\"a\":1",
			"{\"a\":1,}",
			"\"scalar\"",
			"42",
			"",
			"  \n",
			"{\"huge\":1e9999999999}",
			"{\"tiny\":1.5e-2147483648}"
		)) {
			assertEquals(SourceTable.ofTable(new ArrayList<>()), json2List(json), json);
		}
		assertEquals(rows("/a", "1"), json2List("{\"a\":1} trailing").getTable());
	}

	@Test
	void tableInputIsUnchanged() {
		final SourceTable sourceTable = SourceTable.ofTable(rows("a", "b"));
		ComputeProcessor.builder()
			.hostname(LOCALHOST)
			.sourceTable(sourceTable)
			.build()
			.process(Json2List.builder().build());
		assertEquals(SourceTable.ofTable(rows("a", "b")), sourceTable);
	}

	@Test
	void largeDocument() {
		final StringBuilder json = new StringBuilder("{\"items\":[");
		for (int i = 0; i < 50_000; i++) {
			json.append(i == 0 ? "" : ",").append("{\"id\":").append(i).append(",\"name\":\"item ").append(i).append("\"}");
		}
		final List<List<String>> table = json2List(json.append("]}").toString()).getTable();
		assertEquals(100_000, table.size());
		assertEquals(List.of("/items[49999]/name", "item 49999"), table.get(99_999));
	}

	/**
	 * An <code>awk</code> reads the result as a table: the value is <code>$2</code>, whole.
	 */
	@Test
	void awkReadsTheTable() {
		final SourceTable sourceTable = json2List(
			"{\"data\":{\"id\":\"x\",\"token\":\"ab;c\\nd\"},\"items\":[{\"name\":\"n0\"}]}"
		);
		ComputeProcessor.builder()
			.hostname(LOCALHOST)
			.sourceTable(sourceTable)
			.build()
			.process(Awk.builder().script("$1 == \"/data/token\" { print $2; exit }").output(ContentType.TABLE).build());
		assertEquals(SourceTable.ofTable(List.of(List.of("ab;c\nd"))), sourceTable);
	}
}
