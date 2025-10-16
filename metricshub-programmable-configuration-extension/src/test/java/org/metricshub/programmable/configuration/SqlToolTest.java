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

		List<List<String>> rows = sqlTool.query(query, URL, USER, PWD);
		assertNotNull(rows);
		assertEquals(1, rows.size());
		assertEquals(List.of("1"), rows.get(0));
	}

	@Test
	void testQueryWithExplicitTimeout() throws Exception {
		final String query = "SELECT 42";
		final int explicitTimeout = 45;

		List<List<String>> rows = sqlTool.query(query, URL, USER, PWD, explicitTimeout);

		assertNotNull(rows);
		assertEquals(1, rows.size());
		assertEquals(List.of("42"), rows.get(0));
	}

	@Test
	void TestThrowsSQLException() throws Exception {
		final String query = "BAD SQL";

		SQLException ex = assertThrows(SQLException.class, () -> sqlTool.query(query, URL, USER, PWD, 5));
		assertTrue(ex.getCause() instanceof SQLException);
	}
}
