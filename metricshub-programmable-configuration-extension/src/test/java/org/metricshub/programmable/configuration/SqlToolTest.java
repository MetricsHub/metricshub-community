package org.metricshub.programmable.configuration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.sql.SQLException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.metricshub.extension.jdbc.client.JdbcClient;
import org.metricshub.extension.jdbc.client.SqlResult;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

class SqlToolTest {

	private final SqlTool sqlTool = new SqlTool();

	@Test
	void queryUseDefaultTimeout() throws Exception {
		final String url = "jdbc:h2:mem:test;DB_CLOSE_DELAY=-1";
		final String user = "sa";
		final char[] pwd = "pw".toCharArray();
		final String query = "SELECT 1";

		// Mock SqlResult
		final SqlResult result = Mockito.mock(SqlResult.class);
		when(result.getResults()).thenReturn(List.of(List.of("1")));

		try (MockedStatic<JdbcClient> mocked = mockStatic(JdbcClient.class)) {
			// Answer: fail if timeout == 0 (we expect default > 0)
			mocked
				.when(() -> JdbcClient.execute(eq(url), eq(user), same(pwd), eq(query), eq(false), anyInt()))
				.thenAnswer(invocation -> {
					int timeout = invocation.getArgument(5, Integer.class);
					assertTrue(timeout > 0, "Default timeout should be > 0 and not 0");
					return result;
				});

			List<List<String>> rows = sqlTool.query(query, url, user, pwd);

			assertNotNull(rows);
			assertEquals(1, rows.size());
			assertEquals(List.of("1"), rows.get(0));

			// password should be wiped
			for (char c : pwd) {
				assertEquals('\0', c, "Password should be wiped after use");
			}
		}
	}

	@Test
	void queryWithExplicitTimeout() throws Exception {
		final String url = "jdbc:h2:mem:test;DB_CLOSE_DELAY=-1";
		final String user = "sa";
		final char[] pwd = "pw".toCharArray();
		final String query = "SELECT 42";
		final int explicitTimeout = 45;

		final SqlResult result = Mockito.mock(SqlResult.class);
		when(result.getResults()).thenReturn(List.of(List.of("42")));

		try (MockedStatic<JdbcClient> mocked = mockStatic(JdbcClient.class)) {
			mocked
				.when(() -> JdbcClient.execute(eq(url), eq(user), same(pwd), eq(query), eq(false), anyInt()))
				.thenAnswer(invocation -> {
					int timeout = invocation.getArgument(5, Integer.class);
					assertEquals(explicitTimeout, timeout, "Explicit timeout should be forwarded as-is");
					return result;
				});

			List<List<String>> rows = sqlTool.query(query, url, user, pwd, explicitTimeout);

			assertNotNull(rows);
			assertEquals(1, rows.size());
			assertEquals(List.of("42"), rows.get(0));

			for (char c : pwd) {
				assertEquals('\0', c, "Password should be wiped after use");
			}
		}
	}

	@Test
	void TesttestThrowsSQLException() throws Exception {
		final String url = "jdbc:h2:mem:test;DB_CLOSE_DELAY=-1";
		final String user = "sa";
		final char[] pwd = "pw".toCharArray();
		final String query = "BAD SQL";

		try (MockedStatic<JdbcClient> mocked = mockStatic(JdbcClient.class)) {
			mocked
				.when(() -> JdbcClient.execute(eq(url), eq(user), same(pwd), eq(query), eq(false), anyInt()))
				.thenThrow(new SQLException("boom"));

			RuntimeException ex = assertThrows(RuntimeException.class, () -> sqlTool.query(query, url, user, pwd, 5));

			assertTrue(ex.getMessage().contains("SQL query failed"));
			assertTrue(ex.getCause() instanceof SQLException);

			for (char c : pwd) {
				assertEquals('\0', c, "Password should be wiped after exception");
			}
		}
	}
}
