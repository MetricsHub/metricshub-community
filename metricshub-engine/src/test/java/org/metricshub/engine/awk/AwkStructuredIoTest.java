package org.metricshub.engine.awk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Structured AWK input and output (#1511): {@link TableInputSource}, {@link TableSink} and the
 * {@link AwkExecutor} overloads built on them.
 */
class AwkStructuredIoTest {

	private static List<List<String>> table(final String... rows) {
		final List<List<String>> table = new ArrayList<>();
		for (final String row : rows) {
			table.add(new ArrayList<>(Arrays.asList(row.split("\\|", -1))));
		}
		return table;
	}

	@Test
	void tableInputSourceExposesCellsAsFields() throws Exception {
		final TableInputSource source = new TableInputSource(table("a;b|c", "|d"));
		assertTrue(source.nextRecord());
		assertEquals(List.of("a;b", "c"), source.getFields());
		assertNull(source.getRecordText());
		assertFalse(source.isFromFilenameList());
		assertTrue(source.nextRecord());
		assertEquals(List.of("", "d"), source.getFields());
		assertFalse(source.nextRecord());
		assertNull(source.getFields());

		assertFalse(new TableInputSource(null).nextRecord());

		final TableInputSource withText = TableInputSource.withRecordText(table("a;b|c"), ";");
		assertTrue(withText.nextRecord());
		assertEquals("a;b;c", withText.getRecordText());
		assertEquals(List.of("a;b", "c"), withText.getFields());
	}

	@Test
	void scriptReadsTableWithoutSerialization() throws Exception {
		// A ";" inside a cell stays in its field, and NF is the number of cells
		final String result = AwkExecutor.executeAwk(
			"{ print NF \":\" $1 \":\" $2 }",
			new TableInputSource(table("a;b|c", "|d")),
			AwkExecutor.getUtilityEngine(),
			null
		);
		assertEquals("2:a;b:c\n2::d\n", result);

		// $0 is synthesized from the cells with FS, as it is when read, then with OFS once a field is assigned
		assertEquals(
			"a;b c\n",
			AwkExecutor.executeAwk("{ print $0 }", new TableInputSource(table("a;b|c")), AwkExecutor.getUtilityEngine(), null)
		);
		assertEquals(
			"b@c d\nb-x\n",
			AwkExecutor.executeAwk(
				"BEGIN { FS = \"@\"; OFS = \"-\" } { print; $2 = \"x\"; print }",
				new TableInputSource(table("b|c d")),
				AwkExecutor.getUtilityEngine(),
				null
			)
		);
	}

	@Test
	void barePrintWritesTheFieldsAsCells() throws Exception {
		// The cells as read: a ";" stays in its cell, empty cells are kept, print $0 is still one cell
		assertEquals(
			List.of(List.of("a;b", "", "c", ""), List.of("a;b  c "), List.of("a;b", "", "c", "")),
			AwkExecutor.executeAwkToTable(
				"{ print; print $0; print $1, $2, $3, $4 }",
				new TableInputSource(table("a;b||c|")),
				AwkExecutor.getUtilityEngine(),
				null
			)
		);

		// The fields as the script left them: a field assigned beyond NF (the gap filled with empty cells), a smaller
		// NF, a number rendered with OFMT
		assertEquals(
			List.of(List.of("a", "b", "", "", "x"), List.of("a", "b"), List.of("a", "0.33")),
			AwkExecutor.executeAwkToTable(
				"BEGIN { OFMT = \"%.2f\" } { $5 = \"x\"; print; NF = 2; print; $2 = 1 / 3; print }",
				new TableInputSource(table("a|b")),
				AwkExecutor.getUtilityEngine(),
				null
			)
		);

		// A filter
		assertEquals(
			List.of(List.of("1", "x"), List.of("3", "z")),
			AwkExecutor.executeAwkToTable(
				"$2 != \"\" { print }",
				new TableInputSource(table("1|x", "2|", "3|z")),
				AwkExecutor.getUtilityEngine(),
				null
			)
		);

		// Text pending from a printf completes the last cell, the fields follow as cells of their own
		assertEquals(
			List.of(List.of("p", "q1", "2")),
			AwkExecutor.executeAwkToTable(
				"BEGIN { OFS = \";\" } { printf \"p;q\"; print }",
				new TableInputSource(table("1|2")),
				AwkExecutor.getUtilityEngine(),
				null
			)
		);

		// A text input: the fields as Jawk split the record with FS
		assertEquals(
			List.of(List.of("a", "b c"), List.of("d", "")),
			AwkExecutor.executeAwkToTable(
				"BEGIN { FS = \";\" } { print }",
				"a;b c\nd;\n",
				AwkExecutor.getUtilityEngine(),
				null
			)
		);

		// No fields (NF == 0): one empty cell as before, never a row without cells
		assertEquals(
			List.of(List.of(""), List.of(""), List.of(""), List.of("p")),
			AwkExecutor.executeAwkToTable(
				"BEGIN { print } { print } END { NF = 0; print; printf \"p\"; print }",
				"\n",
				AwkExecutor.getUtilityEngine(),
				null
			)
		);
		assertEquals(
			List.of(List.of("")),
			AwkExecutor.executeAwkToTable(
				"{ print }",
				new TableInputSource(List.of(List.of())),
				AwkExecutor.getUtilityEngine(),
				null
			)
		);

		// Text output is unchanged: a bare print writes $0
		assertEquals("a;b  c\n", AwkExecutor.executeAwk("{ print }", "a;b  c\n", AwkExecutor.getUtilityEngine(), null));
	}

	@Test
	void tableSinkMakesOneRowPerPrint() throws Exception {
		final List<List<String>> rows = AwkExecutor.executeAwkToTable(
			"{ print $1, $2 * 2, 1.5, 2.0, \"x;y\"; print }",
			"a 21\n",
			AwkExecutor.getUtilityEngine(),
			null
		);
		// A bare print writes the record's fields (#1566)
		assertEquals(List.of(List.of("a", "42", "1.5", "2", "x;y"), List.of("a", "21")), rows);

		// Empty output: no row
		assertEquals(List.of(), AwkExecutor.executeAwkToTable("BEGIN { }", "in", AwkExecutor.getUtilityEngine(), null));

		// Structured input and output together
		assertEquals(
			List.of(List.of("a;b", "C")),
			AwkExecutor.executeAwkToTable(
				"{ print $1, toupper($2) }",
				new TableInputSource(table("a;b|c")),
				AwkExecutor.getUtilityEngine(),
				null
			)
		);
	}

	@Test
	void tableSinkSplitsPrintfOnOrsAndOfs() throws Exception {
		// printf output is split as the script serialized it: ORS ends a row, OFS separates the cells
		assertEquals(
			List.of(List.of("a", "1", ""), List.of("b", "2"), List.of("c")),
			AwkExecutor.executeAwkToTable(
				"BEGIN { OFS = \";\" } { printf \"%s;%d;\\n\", $1, 1; printf \"%s%s%d%s%s\", \"b\", OFS, 2, ORS, \"c\" }",
				"a\n",
				AwkExecutor.getUtilityEngine(),
				null
			)
		);

		// A record started by printf is completed by the next print, as in a text stream
		assertEquals(
			List.of(List.of("x", "y", "z"), List.of("")),
			AwkExecutor.executeAwkToTable(
				"BEGIN { printf \"x\" OFS; print \"y\", \"z\"; printf \"\\n\" }",
				"",
				AwkExecutor.getUtilityEngine(),
				null
			)
		);

		// The print values stay cells of their own: the OFS or ORS they contain is not split
		assertEquals(
			List.of(List.of("x", "y;z", "1\n2")),
			AwkExecutor.executeAwkToTable(
				"BEGIN { OFS = \";\"; printf \"x;\"; print \"y;z\", \"1\\n2\" }",
				"",
				AwkExecutor.getUtilityEngine(),
				null
			)
		);

		// The OFS in force when the pending text began is the one it is split on
		assertEquals(
			List.of(List.of("a", "b"), List.of("c", "d"), List.of("e", "f")),
			AwkExecutor.executeAwkToTable(
				"BEGIN { OFS = \",\"; printf \"a,b\"; OFS = \";\"; printf \"\"; printf \"\\nc;d\\ne\"; printf \";f\" }",
				"",
				AwkExecutor.getUtilityEngine(),
				null
			)
		);

		// A multi-character ORS straddling two printf calls, and a long record built piecewise
		assertEquals(
			List.of(List.of("1"), List.of("2"), List.of("x".repeat(5000))),
			AwkExecutor.executeAwkToTable(
				"BEGIN { ORS = \"\\r\\n\"; printf \"1\\r\"; printf \"\\n2\"; printf \"\\r\\n\"; for (i = 0; i < 5000; i++) printf \"x\" }",
				"",
				AwkExecutor.getUtilityEngine(),
				null
			)
		);

		// Empty OFS or ORS: nothing to split on
		assertEquals(
			List.of(List.of("a b\nc")),
			AwkExecutor.executeAwkToTable(
				"BEGIN { OFS = \"\"; ORS = \"\"; printf \"a b\\nc\" }",
				"",
				AwkExecutor.getUtilityEngine(),
				null
			)
		);

		// Multi-character separators
		assertEquals(
			List.of(List.of("1", "2"), List.of("3")),
			AwkExecutor.executeAwkToTable(
				"BEGIN { OFS = \"<>\"; ORS = \"\\r\\n\"; printf \"1<>2\\r\\n3\\r\\n\" }",
				"",
				AwkExecutor.getUtilityEngine(),
				null
			)
		);
	}

	@Test
	void evalOnRowKeepsLegacyRecordAndProtectsCells() throws Exception {
		final List<String> row = List.of("a", "x;y", "3");
		// $0 is the row joined with ";", as before
		assertEquals("a;x;y;3", AwkExecutor.evalAwk("$0", row));
		// The fields are the cells: a ";" inside a cell no longer shifts the following columns
		assertEquals("x;y", AwkExecutor.evalAwk("$2", row));
		assertEquals("3", AwkExecutor.evalAwk("$3", row));
		assertEquals("3", AwkExecutor.evalAwk("NF", row));
		assertEquals("a-x;y", AwkExecutor.evalAwk("join(\"-\", $1, $2)", row));
		assertEquals("6", AwkExecutor.evalAwk("$3 * 2", row));
		assertEquals("", AwkExecutor.evalAwk("$9", row));
	}
}
