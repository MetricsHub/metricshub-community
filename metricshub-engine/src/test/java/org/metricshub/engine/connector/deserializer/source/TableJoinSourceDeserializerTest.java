package org.metricshub.engine.connector.deserializer.source;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.metricshub.engine.common.helpers.JsonHelper;
import org.metricshub.engine.connector.deserializer.DeserializerTest;
import org.metricshub.engine.connector.model.Connector;
import org.metricshub.engine.connector.model.monitor.task.source.JoinType;
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
					.joinType(JoinType.LEFT)
					.defaultRightLine(List.of("testdefault", "", ""))
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
			    joinType: left
			    defaultRightLine: [ "", "a;b", "" ]
			""";
		final Connector connector = deserializer.deserialize(
			new SourceKeyProcessor().process(JsonHelper.buildYamlMapper().readTree(yaml))
		);
		final TableJoinSource source = (TableJoinSource) connector.getBeforeAll().get("join");

		// The exact cells, a ';' in a cell included
		assertEquals(List.of("", "a;b", ""), source.getDefaultRightLine());
		assertEquals(source, source.copy());

		// Reference substitution applies to each cell of a copy, never to the original
		final TableJoinSource copy = source.copy();
		copy.update(value -> value == null ? null : value + "!");
		assertEquals(List.of("!", "a;b!", "!"), copy.getDefaultRightLine());
		assertEquals(List.of("", "a;b", ""), source.getDefaultRightLine());
		assertTrue(copy.toString().contains("- defaultRightLine=[!, a;b!, !]"));
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
			    joinType: left
			    defaultRightLine: [ ~, "a" ]
			""";
		// A null item would be a null cell
		final Exception e = Assertions.assertThrows(
			MismatchedInputException.class,
			() -> deserializer.deserialize(new SourceKeyProcessor().process(JsonHelper.buildYamlMapper().readTree(yaml)))
		);
		assertTrue(e.getMessage().contains("defaultRightLine"), e.getMessage());
	}

	@ParameterizedTest
	@ValueSource(strings = { ";;", "testdefault;;;", "\"\"", "0", "[]" })
	void testDeserializeTableJoinDefaultRightLineNotCells(final String defaultRightLine) {
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
			    joinType: left
			    defaultRightLine: %s
			""".formatted(defaultRightLine);
		// The default line is never written as a string, and an empty list is no row at all
		final Exception e = Assertions.assertThrows(
			MismatchedInputException.class,
			() -> deserializer.deserialize(new SourceKeyProcessor().process(JsonHelper.buildYamlMapper().readTree(yaml)))
		);
		assertTrue(e.getMessage().contains("Expected a list of cells"), e.getMessage());
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
	@ValueSource(strings = { "[ a, b ]", "[ [ a, ~ ] ]", "[ [] ]", "[]", "\" \"" })
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

	/**
	 * Deserializes a connector whose <code>beforeAll</code> has one <code>tableJoin</code>, <code>join</code>.
	 *
	 * @param properties The properties of the join besides its tables and key columns, one YAML line each
	 * @return the join
	 * @throws IOException when the connector does not load
	 */
	private TableJoinSource deserializeJoin(final String... properties) throws IOException {
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
			""" + Arrays.stream(properties).map(property -> "    " + property + "\n").collect(Collectors.joining());
		return (TableJoinSource) deserializer.deserialize(
			new SourceKeyProcessor().process(JsonHelper.buildYamlMapper().readTree(yaml))
		).getBeforeAll().get("join");
	}

	@Test
	void testJoinTypeDefaultsToInner() throws IOException {
		final TableJoinSource join = deserializeJoin();
		assertEquals(JoinType.INNER, join.getJoinType());
		assertNull(join.getRightColumns());
		assertNull(join.getDefaultRightLine());
	}

	@ParameterizedTest
	@CsvSource({ "inner,INNER", "left,LEFT", "Left,LEFT", "INNER,INNER" })
	void testJoinType(final String written, final JoinType expected) throws IOException {
		assertEquals(expected, deserializeJoin("joinType: " + written).getJoinType());
	}

	@ParameterizedTest
	@ValueSource(strings = { "right", "full", "outer", "\"\"" })
	void testJoinTypeUnknown(final String joinType) {
		// There is no right join, the table kept whole is the left table, and no full join yet
		assertThrows(IOException.class, () -> deserializeJoin("joinType: " + joinType));
	}

	@Test
	void testRightColumns() throws IOException {
		final TableJoinSource join = deserializeJoin("joinType: left", "rightColumns: \"3\"");
		assertEquals(3, join.getRightColumns());

		final TableJoinSource copy = join.copy();
		assertEquals(join, copy);
		copy.update(value -> value == null ? null : value + "!");
		assertEquals(JoinType.LEFT, copy.getJoinType());
		assertEquals(3, copy.getRightColumns());
		assertTrue(copy.toString().contains("- joinType=LEFT"), copy.toString());
		assertTrue(copy.toString().contains("- rightColumns=3"), copy.toString());
	}

	@ParameterizedTest
	@ValueSource(strings = { "0", "-1", "a", "1.5", "[ 14 ]", "{ a: 1 }" })
	void testColumnsNotPositive(final String columns) {
		// A list or a map must not be skipped: it would also drop the properties after it, here keyType
		assertThrows(
			IOException.class,
			() -> deserializeJoin("joinType: left", "rightColumns: " + columns, "keyType: wbem")
		);
	}

	@ParameterizedTest
	@CsvSource(
		delimiter = '|',
		value = {
			// A connector written for an engine without joinType: an inner join, the default line would be lost
			"defaultRightLine: [ \"\", \"\" ]| | defaultRightLine requires joinType: left, the join type is inner.",
			"defaultRightLine: [ a ]|inner|defaultRightLine requires joinType: left, the join type is inner.",
			"rightColumns: 2| | rightColumns requires joinType: left, the join type is inner.",
			"rightColumns: 2|inner|rightColumns requires joinType: left, the join type is inner." }
	)
	void testPropertyRequiresJoinType(final String property, final String joinType, final String message) {
		final Exception e = assertThrows(
			IOException.class,
			() -> deserializeJoin(property, joinType == null ? "" : "joinType: " + joinType)
		);
		assertTrue(e.getMessage().contains(message), e.getMessage());
	}

	@Test
	void testRightColumnsAndDefaultRightLine() {
		final Exception e = assertThrows(
			IOException.class,
			() -> deserializeJoin("joinType: left", "rightColumns: 2", "defaultRightLine: [ a, b ]")
		);
		assertTrue(
			e.getMessage()
				.contains(
					"rightColumns and defaultRightLine both describe the right side of an unmatched row: write one of them."
				),
			e.getMessage()
		);
	}

	@Test
	void testLeftJoinProperties() throws IOException {
		assertEquals(
			List.of("a", ""),
			deserializeJoin("joinType: left", "defaultRightLine: [ a, \"\" ]").getDefaultRightLine()
		);
		assertEquals(4, deserializeJoin("joinType: left", "rightColumns: 4").getRightColumns());
	}
}
