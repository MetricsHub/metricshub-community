package org.metricshub.programmable.configuration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.SQLException;
import java.util.List;
import org.junit.jupiter.api.Test;

class SqlToolTest {

	private final SqlTool sqlTool = new SqlTool();

	private static final String URL = "jdbc:h2:mem:SqlToolTest";
	private static final String USER = "sa";
	private static final String PWD = "pw";

	@Test
	void testQueryUseDefaultTimeout() throws Exception {
		final String query = "SELECT 1";

		final List<List<String>> rows = sqlTool.query(query, URL, USER, PWD);
		assertNotNull(rows, "Rows should not be null");
		assertEquals(1, rows.size(), "Should return one row");
		assertEquals(List.of("1"), rows.get(0), "First row should contain '1'");
	}

	@Test
	void testQueryWithExplicitTimeout() throws Exception {
		final String query = "SELECT 42";
		final int explicitTimeout = 45;

		final List<List<String>> rows = sqlTool.query(query, URL, USER, PWD, explicitTimeout);

		assertNotNull(rows, "Rows should not be null");
		assertEquals(1, rows.size(), "Should return one row");
		assertEquals(List.of("42"), rows.get(0), "First row should contain '42'");
	}

	@Test
	void testThrowsSQLException() {
		final String query = "BAD SQL";

		final SQLException ex = assertThrows(
			SQLException.class,
			() -> sqlTool.query(query, URL, USER, PWD, 5),
			"Should throw SQLException"
		);
		assertTrue(ex.getCause() instanceof SQLException, "Cause should be SQLException");
	}
}
