package org.metricshub.engine.strategy.source.compute;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.metricshub.engine.constants.Constants.LOCALHOST;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.slf4j.simple.SimpleLogger;
import org.slf4j.spi.LocationAwareLogger;
import org.metricshub.engine.common.helpers.JsonHelper;
import org.metricshub.engine.configuration.HostConfiguration;
import org.metricshub.engine.connector.model.common.ContentType;
import org.metricshub.engine.connector.model.monitor.task.source.compute.Awk;
import org.metricshub.engine.connector.model.monitor.task.source.compute.Compute;
import org.metricshub.engine.strategy.source.SourceTable;
import org.metricshub.engine.telemetry.TelemetryManager;

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
		// Text output: the ";" inside the first cell is a field separator for whoever splits the text
		assertEquals(SourceTable.ofText("a;b;2;c;\n;2;d;"), sourceTable);
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
			Awk.builder()
				.script("$2 > 0 { print $1, $2 / 60, toupper($3) }")
				.input(ContentType.TABLE)
				.output(ContentType.TABLE)
				.build()
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
		processor(sourceTable).process(
			Awk.builder().script("BEGIN { print \"a\\r\\nb\" }").input(ContentType.TABLE).build()
		);
		assertEquals(SourceTable.ofText("a;\nb;"), sourceTable);
	}

	/**
	 * An empty text result is an empty text, whatever the input: the input of the script is not its result.
	 */
	@Test
	void emptyTextOutputIsAnEmptyText() {
		final SourceTable sourceTable = SourceTable.ofTable(table("a|b"));
		processor(sourceTable).process(Awk.builder().script("BEGIN { }").input(ContentType.TABLE).build());
		assertEquals(SourceTable.ofText(""), sourceTable);

		final SourceTable text = SourceTable.ofText("a;b");
		processor(text).process(Awk.builder().script("BEGIN { }").build());
		assertEquals(SourceTable.ofText(""), text);
	}

	private static final String TEXT_FOR_TABLE_WARNING = "reads a table but received a text";

	/**
	 * Runs the action with the WARN level of the {@link ComputeProcessor} logger enabled (the tests log nothing).
	 *
	 * @param action The action
	 * @return what the action logged
	 */
	private static String warningsOf(final Runnable action) throws Exception {
		final Field logField = ComputeProcessor.class.getDeclaredField("log");
		logField.setAccessible(true);
		final SimpleLogger logger = (SimpleLogger) logField.get(null);
		final Field levelField = SimpleLogger.class.getDeclaredField("currentLogLevel");
		levelField.setAccessible(true);
		final int previousLevel = levelField.getInt(logger);
		final PrintStream previousErr = System.err;
		final ByteArrayOutputStream err = new ByteArrayOutputStream();
		try (PrintStream capture = new PrintStream(err, true, StandardCharsets.UTF_8)) {
			levelField.setInt(logger, LocationAwareLogger.WARN_INT);
			System.setErr(capture);
			action.run();
		} finally {
			System.setErr(previousErr);
			levelField.setInt(logger, previousLevel);
		}
		return err.toString(StandardCharsets.UTF_8);
	}

	private static int count(final String text, final String part) {
		return text.split(part, -1).length - 1;
	}

	private static ComputeProcessor processor(final SourceTable sourceTable, final String connectorId) {
		return ComputeProcessor.builder()
			.hostname(LOCALHOST)
			.connectorId(connectorId)
			.sourceKey("${source::monitors.fan.discovery.sources.source(1)}")
			.index(0)
			.sourceTable(sourceTable)
			.build();
	}

	/**
	 * Runs the awk compute the way the strategies do: through the {@link ComputeUpdaterProcessor}.
	 *
	 * @param awk         The awk compute
	 * @param sourceTable The source table it changes
	 */
	private static void runThroughUpdater(final Awk awk, final SourceTable sourceTable) {
		awk.accept(
			ComputeUpdaterProcessor.builder()
				.computeProcessor(processor(sourceTable, "MyConnector"))
				.telemetryManager(
					TelemetryManager.builder().hostConfiguration(HostConfiguration.builder().hostname(LOCALHOST).build()).build()
				)
				.connectorId("MyConnector")
				.attributes(Map.of())
				.build()
		);
	}

	/**
	 * A table input meeting a text at run time: the text is converted into a table, the script gets its rows, and
	 * the WARN names the connector and the source, never the values.
	 */
	@Test
	void textMeetingTableInputIsConvertedWithAWarning() throws Exception {
		// CRLF lines, as a source read on Windows may give
		final SourceTable tableOutput = SourceTable.ofText("fan1;3000;\r\nfan2;0;\r\n");
		final String tableOutputWarnings = warningsOf(
			() -> runThroughUpdater(
				Awk.builder().script("{ print $1, NF, $2 }").input(ContentType.TABLE).output(ContentType.TABLE).build(),
				tableOutput
			)
		);
		assertEquals(SourceTable.ofTable(table("fan1|2|3000", "fan2|2|0")), tableOutput);
		assertEquals(1, count(tableOutputWarnings, TEXT_FOR_TABLE_WARNING), tableOutputWarnings);
		assertTrue(tableOutputWarnings.contains("MyConnector"), tableOutputWarnings);
		assertTrue(
			tableOutputWarnings.contains("${source::monitors.fan.discovery.sources.source(1)} -> computes[0]"),
			tableOutputWarnings
		);
		assertFalse(tableOutputWarnings.contains("fan1") || tableOutputWarnings.contains("3000"), tableOutputWarnings);

		final SourceTable textOutput = SourceTable.ofText("fan1;3000;\n");
		final String textOutputWarnings = warningsOf(
			() -> runThroughUpdater(
				Awk.builder().script("{ print $1 \";\" NF }").input(ContentType.TABLE).build(),
				textOutput
			)
		);
		assertEquals(SourceTable.ofText("fan1;2;"), textOutput);
		assertEquals(1, count(textOutputWarnings, TEXT_FOR_TABLE_WARNING), textOutputWarnings);
	}

	@Test
	void tableInputMeetingATableHasNoWarning() throws Exception {
		final SourceTable sourceTable = SourceTable.ofTable(table("fan1|3000", "fan2|0"));
		final String warnings = warningsOf(
			() -> runThroughUpdater(
				Awk.builder().script("{ print $1, NF, $2 }").input(ContentType.TABLE).output(ContentType.TABLE).build(),
				sourceTable
			)
		);
		assertEquals(SourceTable.ofTable(table("fan1|2|3000", "fan2|2|0")), sourceTable);
		assertFalse(warnings.contains(TEXT_FOR_TABLE_WARNING), warnings);
	}

	/**
	 * An empty text (the result of a source that failed) has no row to lose: no WARN.
	 */
	@Test
	void emptyTextMeetingTableInputHasNoWarning() throws Exception {
		final SourceTable sourceTable = SourceTable.ofText("");
		final String warnings = warningsOf(
			() -> runThroughUpdater(
				Awk.builder().script("{ print $1 }").input(ContentType.TABLE).output(ContentType.TABLE).build(),
				sourceTable
			)
		);
		assertEquals(SourceTable.empty(), sourceTable);
		assertFalse(warnings.contains(TEXT_FOR_TABLE_WARNING), warnings);
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
