package org.metricshub.engine.connector.deserializer.source;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import java.io.IOException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.metricshub.engine.common.helpers.JsonHelper;
import org.metricshub.engine.connector.deserializer.DeserializerTest;
import org.metricshub.engine.connector.model.Connector;
import org.metricshub.engine.connector.model.monitor.task.source.Source;
import org.metricshub.engine.connector.model.monitor.task.source.TableJoinSource;
import org.metricshub.engine.connector.parser.SourceKeyProcessor;

class TableJoinSourceDeserializerTest extends DeserializerTest {

	@Override
	public String getResourcePath() {
		return "src/test/resources/test-files/source/tableJoin/";
	}

	@Test
	void testDeserializeTableJoin() throws IOException {
		final String testResource = "tableJoin";

		try {
			final Connector connector = getConnector(testResource);
			Map<String, Source> expected = new LinkedHashMap<>();
			expected.put(
				"testTableJoinSource",
				TableJoinSource.builder()
					.type("tableJoin")
					.forceSerialization(false)
					.leftTable("testLeft")
					.rightTable("testRight")
					.leftKeyColumn(2)
					.rightKeyColumn(3)
					.defaultRightLine("testdefault;;;")
					.key("${source::beforeAll.testTableJoinSource}")
					.computes(Collections.emptyList())
					.build()
			);

			compareBeforeAllSource(connector, expected);
		} catch (Exception e) {
			Assertions.fail(e.getMessage());
		}
	}

	@Test
	void testDeserializeTableJoinCells() throws IOException {
		final String yaml = """
			connector:
			  detection:
			    appliesTo: [ Linux ]
			beforeAll:
			  join:
			    type: tableJoin
			    leftTable: testLeft
			    rightTable: testRight
			    leftKeyColumn: 1
			    rightKeyColumn: 1
			    defaultRightLine: [ "", "a;b", "" ]
			""";
		final Connector connector = deserializer.deserialize(
			new SourceKeyProcessor().process(JsonHelper.buildYamlMapper().readTree(yaml))
		);
		final TableJoinSource source = (TableJoinSource) connector.getBeforeAll().get("join");

		// The list form is the exact cells, a ';' in a cell included; the string form stays null
		assertEquals(List.of("", "a;b", ""), source.getDefaultRightLineCells());
		assertNull(source.getDefaultRightLine());
		assertEquals(source, source.copy());
		assertEquals(
			List.of("", "a;b", ""),
			TableJoinSource.builder().defaultRightLine(List.of("", "a;b", "")).build().getDefaultRightLineCells()
		);

		// Reference substitution applies to each cell
		source.update(value -> value == null ? null : value + "!");
		assertEquals(List.of("!", "a;b!", "!"), source.getDefaultRightLineCells());
		assertTrue(source.toString().contains("- defaultRightLine=[!, a;b!, !]"));
	}

	@Test
	void testDeserializeTableJoinNullCell() {
		final String yaml = """
			connector:
			  detection:
			    appliesTo: [ Linux ]
			beforeAll:
			  join:
			    type: tableJoin
			    leftTable: testLeft
			    rightTable: testRight
			    leftKeyColumn: 1
			    rightKeyColumn: 1
			    defaultRightLine: [ ~, "a" ]
			""";
		// A null item would be a null cell, which the string form can never produce
		final Exception e = Assertions.assertThrows(
			MismatchedInputException.class,
			() -> deserializer.deserialize(new SourceKeyProcessor().process(JsonHelper.buildYamlMapper().readTree(yaml)))
		);
		assertTrue(e.getMessage().contains("defaultRightLine"), e.getMessage());
	}

	@Test
	void testDeserializeTableJoinEmptyList() {
		final String yaml = """
			connector:
			  detection:
			    appliesTo: [ Linux ]
			beforeAll:
			  join:
			    type: tableJoin
			    leftTable: testLeft
			    rightTable: testRight
			    leftKeyColumn: 1
			    rightKeyColumn: 1
			    defaultRightLine: []
			""";
		// An empty list is no row at all
		final Exception e = Assertions.assertThrows(
			MismatchedInputException.class,
			() -> deserializer.deserialize(new SourceKeyProcessor().process(JsonHelper.buildYamlMapper().readTree(yaml)))
		);
		assertTrue(e.getMessage().contains("defaultRightLine"), e.getMessage());
	}

	@Test
	void testDeserializeTableJoinLiteralRows() throws IOException {
		final String yaml = """
			connector:
			  detection:
			    appliesTo: [ Linux ]
			beforeAll:
			  join:
			    type: tableJoin
			    leftTable: [ [ in, "a;b" ], [ out, "" ] ]
			    rightTable: ${source::beforeAll.right}
			    leftKeyColumn: 1
			    rightKeyColumn: 1
			""";
		final TableJoinSource source = (TableJoinSource) deserializer.deserialize(
			new SourceKeyProcessor().process(JsonHelper.buildYamlMapper().readTree(yaml))
		).getBeforeAll().get("join");

		// The list of rows is exactly these rows and cells; the string form of the left table stays null
		assertEquals(List.of(List.of("in", "a;b"), List.of("out", "")), source.getLeftTableRows());
		assertNull(source.getLeftTable());
		assertEquals("${source::beforeAll.right}", source.getRightTable());
		assertNull(source.getRightTableRows());
		assertEquals(source, source.copy());

		// The updates apply to each cell of a copy, never to the original
		final TableJoinSource copy = source.copy();
		copy.update(value -> value == null ? null : value + "!");
		assertEquals(List.of(List.of("in!", "a;b!"), List.of("out!", "!")), copy.getLeftTableRows());
		assertEquals(List.of(List.of("in", "a;b"), List.of("out", "")), source.getLeftTableRows());
		assertTrue(copy.toString().contains("- leftTable=[[in!, a;b!], [out!, !]]"));
	}

	@Test
	void testDeserializeTableJoinIgnoresWrittenRows() throws IOException {
		// The rows come from leftTable only: written as such, they would skip the checks of the literal
		final String yaml = """
			connector:
			  detection:
			    appliesTo: [ Linux ]
			beforeAll:
			  join:
			    type: tableJoin
			    leftTable: testLeft
			    leftTableRows: [ [ a, ~ ] ]
			    rightTable: testRight
			    leftKeyColumn: 1
			    rightKeyColumn: 1
			""";
		final TableJoinSource join = (TableJoinSource) deserializer.deserialize(
			new SourceKeyProcessor().process(JsonHelper.buildYamlMapper().readTree(yaml))
		).getBeforeAll().get("join");
		assertEquals("testLeft", join.getLeftTable());
		assertNull(join.getLeftTableRows());
	}

	@ParameterizedTest
	@ValueSource(strings = { "[ a, b ]", "[ [ a, ~ ] ]", "[ [] ]", "[]", "\" \"", "[ [ \"a\\rb\" ] ]" })
	void testDeserializeTableJoinInvalidLeftTable(final String leftTable) {
		final String yaml = """
			connector:
			  detection:
			    appliesTo: [ Linux ]
			beforeAll:
			  join:
			    type: tableJoin
			    leftTable: %s
			    rightTable: testRight
			    leftKeyColumn: 1
			    rightKeyColumn: 1
			""".formatted(leftTable);
		Assertions.assertThrows(
			MismatchedInputException.class,
			() -> deserializer.deserialize(new SourceKeyProcessor().process(JsonHelper.buildYamlMapper().readTree(yaml)))
		);
	}
}
