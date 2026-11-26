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
import org.metricshub.engine.common.helpers.JsonHelper;
import org.metricshub.engine.configuration.HostConfiguration;
import org.metricshub.engine.connector.model.common.ContentType;
import org.metricshub.engine.connector.model.monitor.task.source.compute.Awk;
import org.metricshub.engine.connector.model.monitor.task.source.compute.Compute;
import org.metricshub.engine.strategy.source.SourceResult;
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

	private static ComputeProcessor processor(final SourceResult sourceResult) {
		return ComputeProcessor.builder().hostname(LOCALHOST).sourceResult(sourceResult).build();
	}

	@Test
	void tableInputReadsCellsAsFields() {
		final SourceResult sourceResult = SourceResult.ofTable(table("a;b|c", "|d"));
		processor(sourceResult).process(Awk.builder().script("{ print $1 \";\" NF \";\" $2 }").build());
		// Text output: the ";" inside the first cell is a field separator for whoever splits the text
		assertEquals(SourceResult.ofText("a;b;2;c\n;2;d\n"), sourceResult);
	}

	@Test
	void tableOutputMakesOneRowPerPrint() {
		final SourceResult sourceResult = SourceResult.ofText("a 21\nb 1\n");
		processor(sourceResult).process(
			Awk.builder().script("$2 > 1 { print $1, $2 * 2, \"x;y\" }").output(ContentType.TABLE).build()
		);
		// The cell keeps its ";"
		assertEquals(SourceResult.ofTable(table("a|42|x;y")), sourceResult);
	}

	@Test
	void tableInputAndOutputTogether() {
		final SourceResult sourceResult = SourceResult.ofTable(table("fan1|3000|ok", "fan2|0|failed"));
		processor(sourceResult).process(
			Awk.builder().script("$2 > 0 { print $1, $2 / 60, toupper($3) }").output(ContentType.TABLE).build()
		);
		assertEquals(table("fan1|50|OK"), sourceResult.getTable());
	}

	@Test
	void tableOutputPrintsNumbersWith15SignificantDigits() {
		final SourceResult sourceResult = SourceResult.ofTable(table("fan1|3000"));
		processor(sourceResult).process(Awk.builder().script("{ print $2 / 9000, 2.5 }").output(ContentType.TABLE).build());
		assertEquals(table("0.333333333333333|2.5"), sourceResult.getTable());

		// A text output is standard AWK
		final SourceResult text = SourceResult.ofTable(table("fan1|3000"));
		processor(text).process(Awk.builder().script("{ print $2 / 9000 }").build());
		assertEquals("0.333333\n", text.getText());
	}

	@Test
	void tableOutputKeepsCrlfInACell() {
		final SourceResult sourceResult = SourceResult.ofText("");
		processor(sourceResult).process(
			Awk.builder().script("BEGIN { print \"a\\r\\nb\", \"c\" }").output(ContentType.TABLE).build()
		);
		assertEquals(SourceResult.ofTable(table("a\r\nb|c")), sourceResult);
		// Its text has one line per row: the line break of the cell becomes a space
		assertEquals("a b;c;", sourceResult.asText());
	}

	@Test
	void emptyTableOutputEmptiesTheTable() {
		final SourceResult sourceResult = SourceResult.ofText("stale");
		processor(sourceResult).process(Awk.builder().script("BEGIN { }").output(ContentType.TABLE).build());
		// An empty table stays a table
		assertEquals(SourceResult.empty(), sourceResult);
	}

	@Test
	void textOutputOfTableInputHasNoCrlf() {
		final SourceResult sourceResult = SourceResult.ofTable(table("x"));
		processor(sourceResult).process(Awk.builder().script("BEGIN { print \"a\\r\\nb\" }").build());
		assertEquals(SourceResult.ofText("a\nb\n"), sourceResult);

		// Neither when the CRLF comes from a cell
		final SourceResult cell = SourceResult.ofTable(table("a\r\nb|c"));
		processor(cell).process(Awk.builder().script("{ print $1 }").build());
		assertEquals(SourceResult.ofText("a\nb\n"), cell);
	}

	/**
	 * The text of a host that answers with CRLF is read with LF line endings: the last field of a record does not
	 * end with a carriage return, and the printed lines do not carry one.
	 */
	@Test
	void textInputOfACrlfHostHasNoCarriageReturn() {
		final SourceResult sourceResult = SourceResult.ofText("a b\r\nc d\r\n");
		processor(sourceResult).process(Awk.builder().script("{ print $1, $NF }").output(ContentType.TABLE).build());
		assertEquals(SourceResult.ofTable(table("a|b", "c|d")), sourceResult);

		final SourceResult text = SourceResult.ofText("a b\r\nc d\r\n");
		processor(text).process(Awk.builder().script("{ print $NF \";\" $1 }").build());
		assertEquals("b;a\nd;c\n", text.getText());
	}

	/**
	 * An empty text result is an empty text, whatever the input: the input of the script is not its result.
	 */
	@Test
	void emptyTextOutputIsAnEmptyText() {
		final SourceResult sourceResult = SourceResult.ofTable(table("a|b"));
		processor(sourceResult).process(Awk.builder().script("BEGIN { }").build());
		assertEquals(SourceResult.ofText(""), sourceResult);

		final SourceResult text = SourceResult.ofText("a;b");
		processor(text).process(Awk.builder().script("BEGIN { }").build());
		assertEquals(SourceResult.ofText(""), text);
	}

	/**
	 * Runs the awk compute the way the strategies do: through the {@link ComputeUpdaterProcessor}.
	 *
	 * @param yaml        The awk compute, as written in a connector
	 * @param sourceResult The source table it changes
	 */
	private static void runThroughUpdater(final String yaml, final SourceResult sourceResult) throws Exception {
		JsonHelper.buildYamlMapper()
			.readValue("type: awk\n" + yaml, Compute.class)
			.accept(
				ComputeUpdaterProcessor.builder()
					.computeProcessor(processor(sourceResult))
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
			final SourceResult fromTable = SourceResult.ofTable(table("fan;1|3000", "fan2|0"));
			runThroughUpdater(script + input, fromTable);
			assertEquals(SourceResult.ofTable(table("fan;1|2|3000", "fan2|2|0")), fromTable, input);

			// The text is split on FS, a space by default: the ";" are plain characters
			final SourceResult fromText = SourceResult.ofText("fan1;3000; x\nfan2 0\n");
			runThroughUpdater(script + input, fromText);
			assertEquals(SourceResult.ofTable(table("fan1;3000;|2|x", "fan2|2|0")), fromText, input);
		}

		// Text output, both ways
		final SourceResult fromTable = SourceResult.ofTable(table("fan;1|3000"));
		runThroughUpdater("script: '{ print $1 \"#\" NF }'\ninput: text", fromTable);
		assertEquals(SourceResult.ofText("fan;1#2\n"), fromTable);

		final SourceResult fromText = SourceResult.ofText("fan1 3000\n");
		runThroughUpdater("script: '{ print $1 \"#\" NF }'\ninput: table", fromText);
		assertEquals(SourceResult.ofText("fan1#2\n"), fromText);
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
