package org.metricshub.engine.connector.deserializer.source;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.metricshub.engine.common.helpers.JsonHelper;
import org.metricshub.engine.connector.deserializer.DeserializerTest;
import org.metricshub.engine.connector.model.Connector;
import org.metricshub.engine.connector.model.common.ContentType;
import org.metricshub.engine.connector.model.monitor.task.source.Source;
import org.metricshub.engine.connector.model.monitor.task.source.TableUnionSource;
import org.metricshub.engine.connector.model.monitor.task.source.TextUnionSource;
import org.metricshub.engine.connector.parser.SourceKeyProcessor;

class TableUnionSourceDeserializerTest extends DeserializerTest {

	@Override
	public String getResourcePath() {
		return "src/test/resources/test-files/source/tableUnion/";
	}

	@Test
	void testDeserializeTableUnion() throws IOException {
		final String testResource = "tableUnion";

		List<Object> tables = new ArrayList<>();
		tables.add("testTable1");
		tables.add("testTable2");
		tables.add("testTable3");

		final Connector connector = getConnector(testResource);
		Map<String, Source> expected = new LinkedHashMap<>();
		expected.put(
			"testTableUnionSource",
			TableUnionSource.builder()
				.key("${source::beforeAll.testTableUnionSource}")
				.type("tableUnion")
				.tables(tables)
				.build()
		);

		assertEquals(expected, connector.getBeforeAll());
	}

	@Test
	void testDeserializeTableUnionLiteralRows() throws IOException {
		final String yaml = """
			connector:
			  detection:
			    appliesTo: [ Linux ]
			beforeAll:
			  union:
			    type: tableUnion
			    tables:
			    - [ [ in, "a;b", "" ], [ out ] ]
			    - ${source::beforeAll.other}
			    - in;ac;
			""";
		final TableUnionSource source = (TableUnionSource) deserialize(yaml).getBeforeAll().get("union");

		// A list of rows is exactly these rows and cells, next to a reference and a legacy string literal
		assertEquals(
			List.of(List.of(List.of("in", "a;b", ""), List.of("out")), "${source::beforeAll.other}", "in;ac;"),
			source.getTables()
		);
		assertEquals(source, source.copy());
		assertTrue(source.toString().contains("- tables=[[[in, a;b, ], [out]], ${source::beforeAll.other}, in;ac;]"));
	}

	@Test
	void testDeserializeTableUnionLegacyScalar() throws IOException {
		final String yaml = """
			connector:
			  detection:
			    appliesTo: [ Linux ]
			beforeAll:
			  union:
			    type: tableUnion
			    tables: "${source::beforeAll.a}, ${source::beforeAll.b}"
			""";
		// The legacy comma-separated form still works
		assertEquals(
			List.of("${source::beforeAll.a}", "${source::beforeAll.b}"),
			((TableUnionSource) deserialize(yaml).getBeforeAll().get("union")).getTables()
		);
	}

	@ParameterizedTest
	@ValueSource(
		strings = {
			"[ [ a, b ], \"${source::beforeAll.x}\" ]", // a row that is no list
			"[ [ [ a, ~ ] ] ]", // a null cell
			"[ [ [ a, [ b ] ] ] ]", // a cell that is a list
			"[ [ [ \"a\\nb\" ] ] ]", // a cell with a line break
			"[ [ [] ] ]", // a row without cell
			"[ [] ]", // a table without row
			"[ \" \" ]", // a blank member
			"[ ~ ]" // a null member
		}
	)
	void testDeserializeTableUnionInvalidLiteral(final String tables) {
		final String yaml = """
			connector:
			  detection:
			    appliesTo: [ Linux ]
			beforeAll:
			  union:
			    type: tableUnion
			    tables: %s
			""".formatted(tables);
		assertThrows(MismatchedInputException.class, () -> deserialize(yaml));
	}

	@Test
	void testDeserializeTextUnion() throws IOException {
		final String yaml = """
			connector:
			  detection:
			    appliesTo: [ Linux ]
			beforeAll:
			  union:
			    type: textUnion
			    texts: [ "${source::beforeAll.a}", "${source::beforeAll.b}" ]
			""";
		final Source source = deserialize(yaml).getBeforeAll().get("union");

		assertEquals(
			TextUnionSource.builder()
				.type("textUnion")
				.key("${source::beforeAll.union}")
				.texts(List.of("${source::beforeAll.a}", "${source::beforeAll.b}"))
				.build(),
			source
		);
		assertEquals(source, source.copy());
		assertEquals(ContentType.TEXT, source.getOutputContentType());
		assertTrue(source.toString().contains("- texts=[${source::beforeAll.a}, ${source::beforeAll.b}]"));
	}

	@Test
	void testDeserializeTextUnionWithoutTexts() {
		final String yaml = """
			connector:
			  detection:
			    appliesTo: [ Linux ]
			beforeAll:
			  union:
			    type: textUnion
			""";
		assertThrows(MismatchedInputException.class, () -> deserialize(yaml));
	}

	@ParameterizedTest
	@ValueSource(strings = { "[ abc ]", "[ \"${source::beforeAll.a}\", \"a;b\" ]", "[ \" \" ]" })
	void testDeserializeTextUnionMemberThatIsNoReference(final String texts) {
		final String yaml = """
			connector:
			  detection:
			    appliesTo: [ Linux ]
			beforeAll:
			  union:
			    type: textUnion
			    texts: %s
			""".formatted(texts);
		// Raised as the blank member of a collection deserializer is
		assertThrows(IllegalArgumentException.class, () -> deserialize(yaml));
	}

	private Connector deserialize(final String yaml) throws IOException {
		return deserializer.deserialize(new SourceKeyProcessor().process(JsonHelper.buildYamlMapper().readTree(yaml)));
	}
}
