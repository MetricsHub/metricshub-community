package org.metricshub.engine.common.helpers;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

class TextTableHelperTest {

	@Test
	void testGenerateTextTable() {
		List<List<String>> table = List.of(
			List.of("value1", "value2", "1"),
			List.of("value4", "value5", "1"),
			List.of("value7", "value8", "1")
		);

		String expected =
			"""
			+--------+--------+----+
			| h1     | h2     | h3 |
			+--------+--------+----+
			| value1 | value2 | 1  |
			| value4 | value5 | 1  |
			| value7 | value8 | 1  |
			+--------+--------+----+""";

		assertEquals(expected, TextTableHelper.generateTextTable(List.of("h1", "h2", "h3"), table));

		expected =
			"""
			+----------+----------+----------+
			| Column 1 | Column 2 | Column 3 |
			+----------+----------+----------+
			| value1   | value2   | 1        |
			| value4   | value5   | 1        |
			| value7   | value8   | 1        |
			+----------+----------+----------+""";
		assertEquals(expected, TextTableHelper.generateTextTable(table));

		table = List.of(Arrays.asList(null, null, null));
		expected =
			"""
			+----------+----------+----------+
			| Column 1 | Column 2 | Column 3 |
			+----------+----------+----------+
			| N/A      | N/A      | N/A      |
			+----------+----------+----------+""";

		assertEquals(expected, TextTableHelper.generateTextTable(table));

		assertEquals("<empty>", TextTableHelper.generateTextTable(Collections.emptyList()));

		assertEquals("<empty>", TextTableHelper.generateTextTable((Collection<String>) null, Collections.emptyList()));

		Collection<String> columns = Collections.emptyList();
		assertEquals("<empty>", TextTableHelper.generateTextTable(columns, Collections.emptyList()));

		assertEquals("<empty>", TextTableHelper.generateTextTable(null));
	}

	@Test
	void testGenerateTextTableWithColumnsSeparator() {
		List<List<String>> table = List.of(
			List.of("value1", "value2", "1"),
			List.of("value4", "value5", "1"),
			List.of("value7", "value8", "1")
		);

		String expected =
			"""
			+--------+--------+----+
			| h1     | h2     | h3 |
			+--------+--------+----+
			| value1 | value2 | 1  |
			| value4 | value5 | 1  |
			| value7 | value8 | 1  |
			+--------+--------+----+""";

		assertEquals(expected, TextTableHelper.generateTextTable("h1;h2;h3", table));

		expected =
			"""
			+----------+----------+----------+
			| Column 1 | Column 2 | Column 3 |
			+----------+----------+----------+
			| value1   | value2   | 1        |
			| value4   | value5   | 1        |
			| value7   | value8   | 1        |
			+----------+----------+----------+""";

		assertEquals(expected, TextTableHelper.generateTextTable((String) null, table));

		assertEquals(expected, TextTableHelper.generateTextTable("", table));
	}

	@Test
	void testGenerateTextTableColumsArray() {
		List<List<String>> table = List.of(
			List.of("value1", "value2", "1"),
			List.of("value4", "value5", "1"),
			List.of("value7", "value8", "1")
		);

		String expected =
			"""
			+--------+--------+----+
			| h1     | h2     | h3 |
			+--------+--------+----+
			| value1 | value2 | 1  |
			| value4 | value5 | 1  |
			| value7 | value8 | 1  |
			+--------+--------+----+""";

		assertEquals(expected, TextTableHelper.generateTextTable(new String[] { "h1", "h2", "h3" }, table));

		expected =
			"""
			+----------+----------+----------+
			| Column 1 | Column 2 | Column 3 |
			+----------+----------+----------+
			| value1   | value2   | 1        |
			| value4   | value5   | 1        |
			| value7   | value8   | 1        |
			+----------+----------+----------+""";

		assertEquals(expected, TextTableHelper.generateTextTable((String[]) null, table));

		assertEquals(expected, TextTableHelper.generateTextTable(new String[] {}, table));
	}
}
