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

	@Test
	void testQueryWithExplicitDriver() throws Exception {
		final String query = "SELECT 7";

		final List<List<String>> rows = sqlTool.query(query, URL, USER, PWD, 30, "org.h2.Driver", null);

		assertNotNull(rows, "Rows should not be null");
		assertEquals(1, rows.size(), "Should return one row");
		assertEquals(List.of("7"), rows.get(0), "First row should contain '7'");
	}

	@Test
	void testQueryWithBlankClassNameThrows() {
		final SQLException ex = assertThrows(
			SQLException.class,
			() -> sqlTool.query("SELECT 1", URL, USER, PWD, 5, "  ", null),
			"Should throw SQLException when className is blank"
		);
		assertTrue(ex.getMessage().contains("className is required"), "Message should mention required className");
	}

	@Test
	void testQueryUnsupportedUrlThrows() {
		// jdbc:fakedriver:xyz is accepted by no built-in nor external driver descriptor.
		final SQLException ex = assertThrows(
			SQLException.class,
			() -> sqlTool.query("SELECT 1", "jdbc:fakedriver:xyz", USER, PWD, 5),
			"Should throw SQLException when no driver accepts the URL"
		);
		assertTrue(
			ex.getMessage().contains("No JDBC driver registered accepts URL"),
			"Message should explain that no driver accepts the URL"
		);
	}
}
