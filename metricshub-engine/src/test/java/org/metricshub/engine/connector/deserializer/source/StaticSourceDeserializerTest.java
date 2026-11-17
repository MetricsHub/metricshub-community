package org.metricshub.engine.connector.deserializer.source;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.metricshub.engine.common.helpers.JsonHelper;
import org.metricshub.engine.connector.deserializer.DeserializerTest;
import org.metricshub.engine.connector.model.Connector;
import org.metricshub.engine.connector.model.monitor.task.source.Source;
import org.metricshub.engine.connector.model.monitor.task.source.StaticSource;
import org.metricshub.engine.connector.parser.SourceKeyProcessor;

class StaticSourceDeserializerTest extends DeserializerTest {

	@Override
	public String getResourcePath() {
		return "src/test/resources/test-files/source/static/";
	}

	@Test
	void testDeserializeStatic() throws IOException {
		final String testResource = "static";
		final Connector connector = getConnector(testResource);

		final Map<String, Source> expected = new LinkedHashMap<>(
			Map.of(
				"testStaticSource",
				StaticSource.builder().key("${source::beforeAll.testStaticSource}").type("static").value("testValue").build()
			)
		);

		assertEquals(expected, connector.getBeforeAll());
	}

	@Test
	void testDeserializeStaticCells() throws IOException {
		final String yaml = """
			connector:
			  detection:
			    appliesTo: [ Linux ]
			beforeAll:
			  row:
			    type: static
			    value: [ "DDN", "", "Disk;Array" ]
			  number:
			    type: static
			    value: 0
			""";
		final Connector connector = deserializer.deserialize(
			new SourceKeyProcessor().process(JsonHelper.buildYamlMapper().readTree(yaml))
		);
		final StaticSource source = (StaticSource) connector.getBeforeAll().get("row");

		// A list of cells is one row of the exact cells, a ';' in a cell included; the string form stays null
		assertEquals(List.of(List.of("DDN", "", "Disk;Array")), source.getRows());
		assertNull(source.getValue());
		assertEquals(source, source.copy());
		assertEquals(List.of(List.of("a", "b")), StaticSource.builder().value(List.of("a", "b")).build().getRows());

		// Reference substitution applies to each cell
		source.update(value -> value == null ? null : value + "!");
		assertEquals(List.of(List.of("DDN!", "!", "Disk;Array!")), source.getRows());
		assertTrue(source.toString().contains("- value=[[DDN!, !, Disk;Array!]]"));

		// A scalar keeps its text in the string form
		final StaticSource number = (StaticSource) connector.getBeforeAll().get("number");
		assertEquals("0", number.getValue());
		assertNull(number.getRows());
	}

	private StaticSource staticSource(final String value) throws IOException {
		final String yaml = """
			connector:
			  detection:
			    appliesTo: [ Linux ]
			beforeAll:
			  table:
			    type: static
			    value: %s
			""".formatted(value);
		return (StaticSource) deserializer.deserialize(
			new SourceKeyProcessor().process(JsonHelper.buildYamlMapper().readTree(yaml))
		).getBeforeAll().get("table");
	}

	@Test
	void testDeserializeStaticRows() throws IOException {
		// A list of lists is one row per list, an empty list is an empty table
		final StaticSource rows = staticSource("[ [ a, 1 ], [ b ] ]");
		assertEquals(List.of(List.of("a", "1"), List.of("b")), rows.getRows());
		assertEquals(List.of(List.of("a")), staticSource("[ [ a ] ]").getRows());
		assertEquals(List.of(), staticSource("[]").getRows());
		assertEquals(rows, rows.copy());
		assertEquals(rows.getRows(), StaticSource.builder().value(rows.getRows()).build().getRows());

		// Reference substitution applies to each cell of each row
		rows.update(value -> value == null ? null : value + "!");
		assertEquals(List.of(List.of("a!", "1!"), List.of("b!")), rows.getRows());

		// Cells and rows do not mix, and a row needs a cell
		for (final String value : List.of("[ a, [ b ] ]", "[ [ a ], b ]", "[ [] ]", "[ [ a, ~ ] ]", "[ [ [ a ] ] ]")) {
			final Exception e = assertThrows(MismatchedInputException.class, () -> staticSource(value), value);
			assertTrue(e.getMessage().contains("value"), e.getMessage());
		}
	}

	@Test
	void testDeserializeStaticNullCell() {
		final String yaml = """
			connector:
			  detection:
			    appliesTo: [ Linux ]
			beforeAll:
			  row:
			    type: static
			    value: [ ~, "a" ]
			""";
		// A null item would be a null cell, which the string form can never produce
		final Exception e = assertThrows(
			MismatchedInputException.class,
			() -> deserializer.deserialize(new SourceKeyProcessor().process(JsonHelper.buildYamlMapper().readTree(yaml)))
		);
		assertTrue(e.getMessage().contains("value"), e.getMessage());
	}

	@Test
	void testDeserializeStaticLineBreakInCell() {
		final String yaml = """
			connector:
			  detection:
			    appliesTo: [ Linux ]
			beforeAll:
			  row:
			    type: static
			    value: [ "a\\nb", "c" ]
			""";
		// A line break in a cell would split the single row into several records of the raw text
		final Exception e = assertThrows(
			MismatchedInputException.class,
			() -> deserializer.deserialize(new SourceKeyProcessor().process(JsonHelper.buildYamlMapper().readTree(yaml)))
		);
		assertTrue(e.getMessage().contains("value"), e.getMessage());
	}
}
