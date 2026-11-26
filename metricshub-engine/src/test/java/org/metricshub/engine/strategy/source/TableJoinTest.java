package org.metricshub.engine.strategy.source;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.metricshub.engine.connector.model.monitor.task.source.JoinType;

class TableJoinTest {

	private static final List<List<String>> LEFT = List.of(
		List.of("a", "left-a"),
		List.of("b", "left-b"),
		List.of("x", "left-x")
	);

	private static final List<List<String>> RIGHT = List.of(
		List.of("A", "right-a", "1"),
		List.of("b", "right-b", "2"),
		List.of("z", "right-z", "3")
	);

	private static List<List<String>> join(
		final List<List<String>> left,
		final List<List<String>> right,
		final JoinType joinType
	) {
		return TableJoin.join(left, right, 1, 1, joinType, null, null, false);
	}

	@Test
	void testInner() {
		assertEquals(
			List.of(List.of("a", "left-a", "A", "right-a", "1"), List.of("b", "left-b", "b", "right-b", "2")),
			join(LEFT, RIGHT, JoinType.INNER)
		);
	}

	@Test
	void testLeft() {
		assertEquals(
			List.of(
				List.of("a", "left-a", "A", "right-a", "1"),
				List.of("b", "left-b", "b", "right-b", "2"),
				List.of("x", "left-x", "", "", "")
			),
			join(LEFT, RIGHT, JoinType.LEFT)
		);
	}

	@Test
	void testLeftPadsToTheWidestRightRow() {
		final List<List<String>> right = List.of(List.of("a", "1"), List.of("b", "2", "3", "4"));
		assertEquals(
			List.of(
				List.of("a", "left-a", "a", "1"),
				List.of("b", "left-b", "b", "2", "3", "4"),
				List.of("x", "left-x", "", "", "", "")
			),
			join(LEFT, right, JoinType.LEFT)
		);
	}

	@Test
	void testLeftEmptyRightTable() {
		// Without rightColumns, an empty right table has no width
		assertEquals(LEFT, join(LEFT, List.of(), JoinType.LEFT));
		assertEquals(
			List.of(List.of("a", "left-a", "", ""), List.of("b", "left-b", "", ""), List.of("x", "left-x", "", "")),
			TableJoin.join(LEFT, List.of(), 1, 1, JoinType.LEFT, 2, null, false)
		);
	}

	@Test
	void testLeftRightColumns() {
		// rightColumns wins over the width of the right rows: the result does not depend on the rows
		assertEquals(List.of("x", "left-x", ""), TableJoin.join(LEFT, RIGHT, 1, 1, JoinType.LEFT, 1, null, false).get(2));
		assertEquals(
			List.of("x", "left-x", "", "", "", "", ""),
			TableJoin.join(LEFT, RIGHT, 1, 1, JoinType.LEFT, 5, null, false).get(2)
		);
		// A matched row keeps its right row as it is
		assertEquals(
			List.of("a", "left-a", "A", "right-a", "1"),
			TableJoin.join(LEFT, RIGHT, 1, 1, JoinType.LEFT, 1, null, false).get(0)
		);
	}

	@Test
	void testLeftDefaultRightLine() {
		final List<String> defaultRightLine = List.of("", "0", "Chassis", "extra");
		final List<List<String>> result = TableJoin.join(LEFT, RIGHT, 1, 1, JoinType.LEFT, null, defaultRightLine, false);
		assertEquals(List.of("a", "left-a", "A", "right-a", "1"), result.get(0));
		// Exactly these cells, whatever the width of the right rows
		assertEquals(List.of("x", "left-x", "", "0", "Chassis", "extra"), result.get(2));
		// Also with an empty right table
		assertEquals(
			List.of("a", "left-a", "", "0", "Chassis", "extra"),
			TableJoin.join(LEFT, List.of(), 1, 1, JoinType.LEFT, null, defaultRightLine, false).get(0)
		);
	}

	@Test
	void testInnerIgnoresTheMissingSide() {
		assertEquals(
			join(LEFT, RIGHT, JoinType.INNER),
			TableJoin.join(LEFT, RIGHT, 1, 1, JoinType.INNER, 2, List.of("d"), false)
		);
	}

	@Test
	void testRowsWithoutKeyAreDropped() {
		final List<List<String>> left = List.of(List.of("", "no key"), List.of("a"), List.of(), List.of("b", "left-b"));
		final List<List<String>> right = List.of(List.of("", "no key"), List.of("b", "right-b"), List.of("c"));
		// The empty row has no key cell, the "" cells are no key: even a left join drops these left rows
		assertEquals(List.of(List.of("b", "left-b", "b", "right-b")), join(left, right, JoinType.INNER));
		assertEquals(
			List.of(List.of("a", "", ""), List.of("b", "left-b", "b", "right-b")),
			join(left, right, JoinType.LEFT)
		);
		// A key column beyond the row, or an empty key cell: no key
		final List<List<String>> rows = List.of(List.of("k"), List.of("k", ""), List.of("k", "v"));
		assertEquals(
			List.of(List.of("k", "v", "k", "v")),
			TableJoin.join(rows, rows, 2, 2, JoinType.LEFT, null, null, false)
		);
	}

	@Test
	void testDuplicateKeys() {
		final List<List<String>> left = List.of(List.of("k", "left-1"), List.of("k", "left-2"));
		final List<List<String>> right = List.of(List.of("k", "right-1"), List.of("k", "right-2"));
		// Every left row is kept; it matches the first right row of its key
		assertEquals(
			List.of(List.of("k", "left-1", "k", "right-1"), List.of("k", "left-2", "k", "right-1")),
			join(left, right, JoinType.INNER)
		);
	}

	@Test
	void testKeyColumns() {
		final List<List<String>> left = List.of(List.of("left", "k"));
		final List<List<String>> right = List.of(List.of("right", "r", "K"));
		assertEquals(
			List.of(List.of("left", "k", "right", "r", "K")),
			TableJoin.join(left, right, 2, 3, JoinType.INNER, null, null, false)
		);
	}

	@Test
	void testKeysAreCaseInsensitiveInEveryLocale() {
		final Locale locale = Locale.getDefault();
		try {
			// In Turkish, "I".toLowerCase() is a dotless i
			Locale.setDefault(Locale.forLanguageTag("tr"));
			assertEquals(
				List.of(List.of("TITLE", "title")),
				join(List.of(List.of("TITLE")), List.of(List.of("title")), JoinType.INNER)
			);
		} finally {
			Locale.setDefault(locale);
		}
	}

	@Test
	void testWbemKeys() {
		final List<List<String>> left = List.of(List.of("Disk.b=\"2\",a=\"1\""), List.of("NoClass"));
		final List<List<String>> right = List.of(List.of("disk.a=\"1\",b=\"2\""), List.of("noclass"));
		assertEquals(
			List.of(List.of("Disk.b=\"2\",a=\"1\"", "disk.a=\"1\",b=\"2\""), List.of("NoClass", "noclass")),
			TableJoin.join(left, right, 1, 1, JoinType.INNER, null, null, true)
		);
		// Without keyType: wbem, the order of the key properties matters
		assertEquals(List.of(List.of("NoClass", "noclass")), join(left, right, JoinType.INNER));
	}

	@Test
	void testNullTables() {
		assertEquals(List.of(), join(null, RIGHT, JoinType.LEFT));
		assertEquals(List.of(), join(LEFT, null, JoinType.INNER));
		assertEquals(LEFT, join(LEFT, null, JoinType.LEFT));
	}

	@Test
	void testResultRowsAreNew() {
		final List<List<String>> left = List.of(new ArrayList<>(List.of("a")));
		final List<List<String>> right = List.of(new ArrayList<>(List.of("a", "r")));
		final List<List<String>> result = join(left, right, JoinType.INNER);
		// A compute may change the rows of the result, never the tables joined
		result.get(0).set(0, "changed");
		assertEquals(List.of("a"), left.get(0));
		assertEquals(List.of("a", "r"), right.get(0));
		// The padding cells of two rows are distinct cells
		final List<List<String>> padded = TableJoin.join(
			List.of(List.of("x"), List.of("y")),
			List.of(),
			1,
			1,
			JoinType.LEFT,
			1,
			null,
			false
		);
		padded.get(0).set(1, "changed");
		assertEquals(List.of("y", ""), padded.get(1));
	}
}
