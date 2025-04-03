package org.metricshub.extension.jdbc.client;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import org.junit.jupiter.api.Test;

class JdbcClientTest {

	@Test
	void testExecuteSelectQuery() throws SQLException {
		Connection connection = null;
		try {
			// Setup in-memory H2 database and create table
			String url = "jdbc:h2:mem:testdb1";
			connection = DriverManager.getConnection(url, "sa", "");

			try (Statement statement = connection.createStatement()) {
				// Create a sample table
				String createTableSQL = "CREATE TABLE users (id INT PRIMARY KEY, name VARCHAR(255));";
				statement.execute(createTableSQL);

				// Insert sample data
				String insertDataSQL = "INSERT INTO users (id, name) VALUES (1, 'John Doe'), (2, 'Jane Doe');";
				statement.execute(insertDataSQL);
			}

			// Test JDBCClient with a SELECT query on the in-memory H2 database
			String username = "sa";
			char[] password = "".toCharArray();
			String sqlQuery = "SELECT * FROM users";
			boolean showWarnings = false;
			int timeout = 30;

			SqlResult sqlResult = JdbcClient.execute(url, username, password, sqlQuery, showWarnings, timeout);

			assertEquals("1", sqlResult.getResults().get(0).get(0));
			assertEquals("John Doe", sqlResult.getResults().get(0).get(1));
			assertEquals("2", sqlResult.getResults().get(1).get(0));
			assertEquals("Jane Doe", sqlResult.getResults().get(1).get(1));
		} finally {
			if (connection != null) {
				connection.close();
			}
		}
	}

	@Test
	void testExecuteInsertQuery() throws SQLException {
		Connection connection = null;
		try {
			// Setup in-memory H2 database and create table
			// Use a new in-memory H2 database
			String url = "jdbc:h2:mem:testdb2";
			connection = DriverManager.getConnection(url, "sa", "");

			try (Statement statement = connection.createStatement()) {
				// Create a sample table
				String createTableSQL = "CREATE TABLE users (id INT PRIMARY KEY, name VARCHAR(255));";
				statement.execute(createTableSQL);

				// Insert sample data
				String insertDataSQL = "INSERT INTO users (id, name) VALUES (1, 'John Doe'), (2, 'Jane Doe');";
				statement.execute(insertDataSQL);
			}

			// Test JDBCClient with an INSERT query
			String username = "sa";
			char[] password = "".toCharArray();
			String sqlQuery = "INSERT INTO users (id, name) VALUES (3, 'Alice');";
			boolean showWarnings = false;
			int timeout = 30;

			// Execute the INSERT query
			JdbcClient.execute(url, username, password, sqlQuery, showWarnings, timeout);

			String selectQuery = "SELECT * FROM users WHERE id = 3";
			SqlResult sqlResult = JdbcClient.execute(url, username, password, selectQuery, showWarnings, timeout);

			assertEquals("3", sqlResult.getResults().get(0).get(0));
			assertEquals("Alice", sqlResult.getResults().get(0).get(1));
		} finally {
			if (connection != null) {
				connection.close();
			}
		}
	}
}
