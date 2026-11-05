package org.metricshub.engine.strategy.source.compute;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.metricshub.engine.constants.Constants.LOCALHOST;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.metricshub.engine.common.helpers.JsonHelper;
import org.metricshub.engine.connector.model.common.ContentType;
import org.metricshub.engine.connector.model.monitor.task.source.compute.Awk;
import org.metricshub.engine.connector.model.monitor.task.source.compute.Compute;
import org.metricshub.engine.strategy.source.SourceTable;

/**
 * The <code>input</code> and <code>output</code> properties of the <code>awk</code> compute (#1511).
 */
class AwkComputeStructuredIoTest {

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

	@Test
	void tableInputReadsCellsAsFields() {
		final SourceTable sourceTable = SourceTable.ofTable(table("a;b|c", "|d"));
		processor(sourceTable).process(
			Awk.builder().script("{ print $1 \";\" NF \";\" $2 }").input(ContentType.TABLE).build()
		);
		// Text output: the printed lines are re-parsed on ";", so the ";" inside the first cell adds a column here
		assertEquals(table("a|b|2|c", "|2|d"), sourceTable.getTable());
		assertEquals("a;b;2;c;\n;2;d;", sourceTable.getRawData());
	}

	@Test
	void tableOutputMakesOneRowPerPrint() {
		final SourceTable sourceTable = SourceTable.ofText("a 21\nb 1\n");
		processor(sourceTable).process(
			Awk.builder().script("$2 > 1 { print $1, $2 * 2, \"x;y\" }").output(ContentType.TABLE).build()
		);
		assertEquals(table("a|42|x;y"), sourceTable.getTable());
		// The raw text is serialized as toText does: the cell keeps its ";" in the table, "," in the text
		assertEquals("a;42;x,y;", sourceTable.getRawData());
		assertEquals(ContentType.TABLE, sourceTable.getContentType());
	}

	@Test
	void tableInputAndOutputTogether() {
		final SourceTable sourceTable = SourceTable.ofTable(table("fan1|3000|ok", "fan2|0|failed"));
		processor(sourceTable).process(
			Awk.builder()
				.script("$2 > 0 { print $1, $2 / 60, toupper($3) }")
				.input(ContentType.TABLE)
				.output(ContentType.TABLE)
				.build()
		);
		assertEquals(table("fan1|50|OK"), sourceTable.getTable());
	}

	@Test
	void emptyTableOutputEmptiesTheTable() {
		final SourceTable sourceTable = SourceTable.builder().rawData("stale").table(table("a")).build();
		processor(sourceTable).process(Awk.builder().script("BEGIN { }").output(ContentType.TABLE).build());
		assertEquals(List.of(), sourceTable.getTable());
	}

	@Test
	void propertiesDriveTheContentTypesAndDeserialize() throws Exception {
		final Awk legacy = Awk.builder().script("{}").build();
		assertEquals(ContentType.TEXT, legacy.getInputContentType());
		assertEquals(ContentType.TEXT, legacy.getOutputContentType());

		final Compute parsed = JsonHelper.buildYamlMapper()
			.readValue("type: awk\nscript: '{ print }'\ninput: table\noutput: Table", Compute.class);
		assertEquals(ContentType.TABLE, parsed.getInputContentType());
		assertEquals(ContentType.TABLE, parsed.getOutputContentType());
		assertTrue(
			parsed.copy() instanceof Awk copy && copy.getInput() == ContentType.TABLE && copy.getOutput() == ContentType.TABLE
		);
	}
}
