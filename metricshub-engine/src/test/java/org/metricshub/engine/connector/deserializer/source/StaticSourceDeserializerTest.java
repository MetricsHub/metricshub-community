package org.metricshub.engine.connector.deserializer.source;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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
				StaticSource.builder()
					.key("${source::beforeAll.testStaticSource}")
					.type("static")
					.value(List.of("testValue"))
					.build()
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
			    value: [ 0 ]
			  none:
			    type: static
			    value: []
			""";
		final Connector connector = deserializer.deserialize(
			new SourceKeyProcessor().process(JsonHelper.buildYamlMapper().readTree(yaml))
		);
		final StaticSource source = (StaticSource) connector.getBeforeAll().get("row");

		// The exact cells, a ';' in a cell included
		assertEquals(List.of("DDN", "", "Disk;Array"), source.getValue());
		assertEquals(source, source.copy());

		// Reference substitution applies to each cell of a copy, never to the original
		final StaticSource copy = source.copy();
		copy.update(value -> value == null ? null : value + "!");
		assertEquals(List.of("DDN!", "!", "Disk;Array!"), copy.getValue());
		assertEquals(List.of("DDN", "", "Disk;Array"), source.getValue());
		assertTrue(copy.toString().contains("- value=[DDN!, !, Disk;Array!]"));

		// A cell that is not a string is its text, an empty list is no cell: an empty table
		assertEquals(List.of("0"), ((StaticSource) connector.getBeforeAll().get("number")).getValue());
		assertEquals(List.of(), ((StaticSource) connector.getBeforeAll().get("none")).getValue());
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
		// A null item would be a null cell
		final Exception e = assertThrows(
			MismatchedInputException.class,
			() -> deserializer.deserialize(new SourceKeyProcessor().process(JsonHelper.buildYamlMapper().readTree(yaml)))
		);
		assertTrue(e.getMessage().contains("value"), e.getMessage());
	}

	@ParameterizedTest
	@ValueSource(strings = { "a;b;c", "0", "\"\"", "${source::beforeAll.other}", "{ a: b }" })
	void testDeserializeStaticNotAList(final String value) {
		final String yaml = """
			connector:
			  detection:
			    appliesTo: [ Linux ]
			beforeAll:
			  row:
			    type: static
			    value: %s
			""".formatted(value);
		// A hard-coded row is never written as a string, and a source is copied by a copy source
		final Exception e = assertThrows(
			MismatchedInputException.class,
			() -> deserializer.deserialize(new SourceKeyProcessor().process(JsonHelper.buildYamlMapper().readTree(yaml)))
		);
		assertTrue(e.getMessage().contains("Expected a list of cells"), e.getMessage());
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
