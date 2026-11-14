package org.metricshub.engine.strategy.source.compute;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.metricshub.engine.constants.Constants.LOCALHOST;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.metricshub.engine.awk.AwkExecutor;
import org.metricshub.engine.common.helpers.JsonHelper;
import org.metricshub.engine.configuration.HostConfiguration;
import org.metricshub.engine.connector.model.common.ContentType;
import org.metricshub.engine.connector.model.monitor.task.source.compute.Awk;
import org.metricshub.engine.connector.model.monitor.task.source.compute.Compute;
import org.metricshub.engine.strategy.source.SourceTable;
import org.metricshub.engine.telemetry.TelemetryManager;

/**
 * The structured input and output of the <code>awk</code> compute (#1511, #1559).
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
		processor(sourceTable).process(Awk.builder().script("{ print $1 \";\" NF \";\" $2 }").build());
		// Text output: the ";" inside the first cell is a field separator for whoever splits the text
		assertEquals(SourceTable.ofText("a;b;2;c;\n;2;d;"), sourceTable);
	}

	/**
	 * The compute runs the program it holds, the one a previous run compiled (#1576): here a program that
	 * deliberately differs from the script.
	 */
	@Test
	void runsTheProgramTheComputeHolds() throws Exception {
		final Awk awk = Awk.builder().script("{ print \"from the script\" }").build();
		awk.setProgram(AwkExecutor.compile("{ print \"from the program\" }", AwkExecutor.getUtilityEngine()));
		final SourceTable sourceTable = SourceTable.ofText("x\n");

		processor(sourceTable).process(awk);

		assertEquals(SourceTable.ofText("from the program;"), sourceTable);
	}

	@Test
	void tableOutputMakesOneRowPerPrint() {
		final SourceTable sourceTable = SourceTable.ofText("a 21\nb 1\n");
		processor(sourceTable).process(
			Awk.builder().script("$2 > 1 { print $1, $2 * 2, \"x;y\" }").output(ContentType.TABLE).build()
		);
		// The cell keeps its ";"
		assertEquals(SourceTable.ofTable(table("a|42|x;y")), sourceTable);
	}

	@Test
	void tableInputAndOutputTogether() {
		final SourceTable sourceTable = SourceTable.ofTable(table("fan1|3000|ok", "fan2|0|failed"));
		processor(sourceTable).process(
			Awk.builder().script("$2 > 0 { print $1, $2 / 60, toupper($3) }").output(ContentType.TABLE).build()
		);
		assertEquals(table("fan1|50|OK"), sourceTable.getTable());
	}

	@Test
	void tableOutputKeepsCrlfInACell() {
		final SourceTable sourceTable = SourceTable.ofText("");
		processor(sourceTable).process(
			Awk.builder().script("BEGIN { print \"a\\r\\nb\", \"c\" }").output(ContentType.TABLE).build()
		);
		assertEquals(SourceTable.ofTable(table("a\r\nb|c")), sourceTable);
		// Its text never has a CRLF
		assertEquals("a\nb;c;", sourceTable.asText());
	}

	@Test
	void emptyTableOutputEmptiesTheTable() {
		final SourceTable sourceTable = SourceTable.ofText("stale");
		processor(sourceTable).process(Awk.builder().script("BEGIN { }").output(ContentType.TABLE).build());
		// An empty table stays a table
		assertEquals(SourceTable.empty(), sourceTable);
	}

	@Test
	void textOutputOfTableInputHasNoCrlf() {
		final SourceTable sourceTable = SourceTable.ofTable(table("x"));
		processor(sourceTable).process(Awk.builder().script("BEGIN { print \"a\\r\\nb\" }").build());
		assertEquals(SourceTable.ofText("a;\nb;"), sourceTable);
	}

	/**
	 * An empty text result is an empty text, whatever the input: the input of the script is not its result.
	 */
	@Test
	void emptyTextOutputIsAnEmptyText() {
		final SourceTable sourceTable = SourceTable.ofTable(table("a|b"));
		processor(sourceTable).process(Awk.builder().script("BEGIN { }").build());
		assertEquals(SourceTable.ofText(""), sourceTable);

		final SourceTable text = SourceTable.ofText("a;b");
		processor(text).process(Awk.builder().script("BEGIN { }").build());
		assertEquals(SourceTable.ofText(""), text);
	}

	/**
	 * Runs the awk compute the way the strategies do: through the {@link ComputeUpdaterProcessor}.
	 *
	 * @param yaml        The awk compute, as written in a connector
	 * @param sourceTable The source table it changes
	 */
	private static void runThroughUpdater(final String yaml, final SourceTable sourceTable) throws Exception {
		JsonHelper.buildYamlMapper()
			.readValue("type: awk\n" + yaml, Compute.class)
			.accept(
				ComputeUpdaterProcessor.builder()
					.computeProcessor(processor(sourceTable))
					.telemetryManager(
						TelemetryManager.builder()
							.hostConfiguration(HostConfiguration.builder().hostname(LOCALHOST).build())
							.build()
					)
					.connectorId("MyConnector")
					.attributes(Map.of())
					.build()
			);
	}

	/**
	 * The input follows the source result as it is at run time, whatever the deprecated <code>input</code> says: a
	 * table is read structured (a ";" inside a cell stays in its field), a text the traditional way (#1559).
	 */
	@Test
	void inputFollowsTheSourceResult() throws Exception {
		final String script = "script: '{ print $1, NF, $2 }'\noutput: table\n";
		for (final String input : List.of("", "input: table\n", "input: text\n")) {
			final SourceTable fromTable = SourceTable.ofTable(table("fan;1|3000", "fan2|0"));
			runThroughUpdater(script + input, fromTable);
			assertEquals(SourceTable.ofTable(table("fan;1|2|3000", "fan2|2|0")), fromTable, input);

			// The text is split on FS, a space by default: the ";" are plain characters
			final SourceTable fromText = SourceTable.ofText("fan1;3000; x\nfan2 0\n");
			runThroughUpdater(script + input, fromText);
			assertEquals(SourceTable.ofTable(table("fan1;3000;|2|x", "fan2|2|0")), fromText, input);
		}

		// Text output, both ways
		final SourceTable fromTable = SourceTable.ofTable(table("fan;1|3000"));
		runThroughUpdater("script: '{ print $1 \"#\" NF }'\ninput: text", fromTable);
		assertEquals(SourceTable.ofText("fan;1#2;"), fromTable);

		final SourceTable fromText = SourceTable.ofText("fan1 3000\n");
		runThroughUpdater("script: '{ print $1 \"#\" NF }'\ninput: table", fromText);
		assertEquals(SourceTable.ofText("fan1#2;"), fromText);
	}

	@Test
	void inputIsDeprecatedAndIgnored() throws Exception {
		final Awk legacy = Awk.builder().script("{}").build();
		assertNull(legacy.getInputContentType());
		assertEquals(ContentType.TEXT, legacy.getOutputContentType());

		// Still accepted, dropped
		final Compute parsed = JsonHelper.buildYamlMapper()
			.readValue("type: awk\nscript: '{ print }'\ninput: table\noutput: Table", Compute.class);
		assertEquals(Awk.builder().type("awk").script("{ print }").output(ContentType.TABLE).build(), parsed);
		assertNull(parsed.getInputContentType());
		assertEquals(ContentType.TABLE, parsed.getOutputContentType());
		assertTrue(parsed.copy() instanceof Awk copy && copy.getOutput() == ContentType.TABLE);
	}
}
