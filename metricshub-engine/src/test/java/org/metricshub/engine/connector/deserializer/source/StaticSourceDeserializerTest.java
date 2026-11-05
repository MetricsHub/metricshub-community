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

		// The list form is the exact cells, a ';' in a cell included; the string form stays null
		assertEquals(List.of("DDN", "", "Disk;Array"), source.getCells());
		assertNull(source.getValue());
		assertEquals(source, source.copy());
		assertEquals(List.of("a", "b"), StaticSource.builder().value(List.of("a", "b")).build().getCells());

		// Reference substitution applies to each cell
		source.update(value -> value == null ? null : value + "!");
		assertEquals(List.of("DDN!", "!", "Disk;Array!"), source.getCells());
		assertTrue(source.toString().contains("- value=[DDN!, !, Disk;Array!]"));

		// A scalar keeps its text in the string form
		final StaticSource number = (StaticSource) connector.getBeforeAll().get("number");
		assertEquals("0", number.getValue());
		assertNull(number.getCells());
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
	void testDeserializeStaticEmptyList() {
		final String yaml = """
			connector:
			  detection:
			    appliesTo: [ Linux ]
			beforeAll:
			  row:
			    type: static
			    value: []
			""";
		// An empty list is no row at all
		final Exception e = assertThrows(
			MismatchedInputException.class,
			() -> deserializer.deserialize(new SourceKeyProcessor().process(JsonHelper.buildYamlMapper().readTree(yaml)))
		);
		assertTrue(e.getMessage().contains("value"), e.getMessage());
	}
}
