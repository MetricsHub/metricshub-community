package org.metricshub.extension.internaldb;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * MetricsHub Internal DB Extension
 * ჻჻჻჻჻჻
 * Copyright 2023 - 2025 MetricsHub
 * ჻჻჻჻჻჻
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 * ╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱
 */

import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.metricshub.engine.connector.model.common.SqlColumn;
import org.metricshub.engine.connector.model.common.SqlTable;
import org.metricshub.engine.strategy.source.SourceTable;
import org.metricshub.engine.telemetry.TelemetryManager;

/**
 * The SqlClientExecutor class provides a method for executing
 * SQL queries on a list of tables.
 */
@Slf4j
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class SqlClientExecutor {

	/**
	 * The bundled H2 driver, obtained from this extension's own class loader and connected to
	 * directly (see {@link #executeQuery(List, String)}). It is deliberately kept out of the
	 * JVM-global {@link DriverManager}: connecting through the instance avoids relying on
	 * DriverManager's one-shot, thread-context-classloader-dependent driver discovery (which, under
	 * per-extension isolation, cannot be trusted to ever see this extension's shaded H2), and it
	 * keeps the driver from leaking into the global registry or pinning this isolated class loader.
	 */
	private static final Driver H2_DRIVER = loadIsolatedH2Driver();

	private TelemetryManager telemetryManager;
	private String connectorId;

	/**
	 * Loads the bundled H2 driver with this extension's class loader and removes the instance H2
	 * self-registers into the JVM-global {@link DriverManager} during its static initialization, so
	 * the driver stays private to this extension. The returned instance is used to open connections
	 * directly, mirroring the JDBC extension's driver handling.
	 *
	 * @return the isolated H2 {@link Driver}, or {@code null} if it could not be loaded.
	 */
	private static Driver loadIsolatedH2Driver() {
		final ClassLoader classLoader = SqlClientExecutor.class.getClassLoader();
		try {
			// Triggers H2's static initializer, which self-registers a singleton in DriverManager.
			Class.forName("org.h2.Driver", true, classLoader);
		} catch (ClassNotFoundException e) {
			log.error("The bundled H2 driver could not be loaded for the Internal DB extension: {}", e.getMessage());
			return null;
		}

		// Find the driver H2 registered from our own class loader and detach it from the global
		// registry; we connect through the instance directly instead.
		final Enumeration<Driver> drivers = DriverManager.getDrivers();
		while (drivers.hasMoreElements()) {
			final Driver driver = drivers.nextElement();
			if (driver.getClass().getClassLoader() == classLoader) {
				try {
					DriverManager.deregisterDriver(driver);
				} catch (SQLException e) {
					log.debug("Could not deregister the bundled H2 driver from DriverManager: {}", e.getMessage());
				}
				return driver;
			}
		}

		log.error("The bundled H2 driver did not register itself; Internal DB queries will fail.");
		return null;
	}

	/**
	 * Creates a new H2 in-memory database and create a connection to it.
	 * Creates and fill the SQL tables corresponding to the {@link SqlTable} in the sqlTables {@link List}.
	 * Execute the query and return the result in a form of a list of lists of strings.
	 * @param sqlTables The tables to execute the query on.
	 * @param query     The query to execute.
	 * @return          The result of the query.
	 */
	public List<List<String>> executeQuery(final List<SqlTable> sqlTables, final String query) {
		if (sqlTables == null) {
			log.error("Malformed Internal DB Query Source, no SQL Table is provided.");
			return new ArrayList<>();
		}

		if (query == null) {
			log.error("Malformed Internal DB Query Source, no SQL Query is provided.");
			return new ArrayList<>();
		}

		if (H2_DRIVER == null) {
			log.error("The bundled H2 driver is not available; cannot run the Internal DB Query.");
			return new ArrayList<>();
		}

		final String hostId = telemetryManager.getHostConfiguration().getHostId();
		final String connectionName = "jdbc:h2:mem:" + hostId + UUID.randomUUID().toString();
		// Creation of the connection to the H2 database in memory, through the isolated driver
		// instance (never DriverManager) so the driver stays private to this extension.
		try (Connection connection = H2_DRIVER.connect(connectionName, new Properties())) {
			connection.setAutoCommit(false);

			// Prepare the SQL tables
			for (SqlTable sqlTable : sqlTables) {
				createAndInsert(sqlTable, connection);
			}

			return executeQuery(query, connection);
		} catch (Exception exception) {
			log.error("Error when creating the database for the Internal DB Query: {}", exception.getMessage());
			log.debug("Exception: ", exception);
			return new ArrayList<>();
		}
	}

	/**
	 * Execute a SQL query on the connection object and return the result in a form
	 * of a list of lists of strings.
	 *
	 * @param query      The query to execute.
	 * @param connection The connection to the database.
	 * @return The result of the query.
	 */
	private List<List<String>> executeQuery(final String query, final Connection connection) {
		final List<List<String>> result = new ArrayList<>();

		try (Statement statement = connection.createStatement(); ResultSet resultSet = statement.executeQuery(query)) {
			if (resultSet == null) {
				log.error("The Internal DB Query {} returned a null result.", query);
				return result;
			}

			final ResultSetMetaData rsmd = resultSet.getMetaData();

			final int columnsNumber = rsmd.getColumnCount();

			while (resultSet.next()) {
				final List<String> row = new ArrayList<>();
				for (int i = 1; i <= columnsNumber; i++) {
					final String resultValue = resultSet.getString(i);
					row.add(resultValue != null ? resultValue : "");
				}
				result.add(row);
			}
		} catch (SQLException exception) {
			log.error("Error when executing Internal DB Query {}: {}", query, exception.getMessage());
			log.debug("SQL Exception: ", exception);
		}

		return result;
	}

	/**
	 * Create the SQL tables corresponding to the sqlTables in the database linked to the connection object.
	 * @param sqlTable   The tables to use to create SQL tables.
	 * @param connection The connection to the database.
	 */
	private void createAndInsert(final SqlTable sqlTable, final Connection connection) {
		final String createTableQuery = createTableQuery(sqlTable);
		if (createTableQuery == null) {
			log.debug("Error when creating Internal DB Query CREATE TABLE query for source {}", sqlTable.getSource());
			return;
		}

		try {
			final Statement statement = connection.createStatement();

			statement.execute(createTableQuery);
			connection.commit();
			log.debug("Executing CREATE TABLE query: {}", createTableQuery);
		} catch (SQLException exception) {
			log.error("Error when executing CREATE TABLE query {}: {}", createTableQuery, exception.getMessage());
			log.debug("CREATE TABLE SQL Exception: ", exception);
			return;
		}

		insertTableDataBatch(sqlTable, connection);
	}

	/**
	 * Insert the data from the {@link SourceTable} corresponding to the {@link SqlTable} object into the SQL table
	 *
	 * @param sqlTable   The table to insert the data from.
	 * @param connection The connection to the database.
	 */
	private void insertTableDataBatch(final SqlTable sqlTable, final Connection connection) {
		final SourceTable sourceTable = SourceTable.lookupSourceTable(
			sqlTable.getSource(),
			connectorId,
			telemetryManager
		).orElse(null);
		if (sourceTable == null) {
			log.error(
				"The source table {} is not found during the Internal DB Query job. Skip processing.",
				sqlTable.getSource()
			);
			return;
		}

		List<List<String>> table = sourceTable.getTable();
		if (table == null || table.isEmpty()) {
			log.error("The source table {} is empty. Skip Internal DB Query job processing.", sqlTable.getSource());
			return;
		}

		// Build the INSERT statement with placeholders
		final String alias = sqlTable.getAlias().strip();
		final List<SqlColumn> columns = sqlTable.getColumns();
		final List<String> columnNames = columns.stream().map(SqlColumn::getName).toList();

		final String joinedColumnNames = String.join(",", columnNames);
		final String placeholders = String.join(
			",",
			columns
				.stream()
				.map(_ -> "?")
				.toArray(String[]::new)
		);

		final String insertSQL = "INSERT INTO " + alias + " (" + joinedColumnNames + ") VALUES (" + placeholders + ")";

		try (PreparedStatement preparedStatement = connection.prepareStatement(insertSQL)) {
			// Prepare column metadata map
			final Map<String, ColumnMetadata> columnMetadataMap = DatabaseHelper.prepareColumnMetadata(
				preparedStatement,
				columnNames
			);

			int batchSize = 1000; // this can be configured later if needed
			int count = 0;

			for (final List<String> row : table) {
				for (final SqlColumn sqlColumn : columns) {
					final ColumnMetadata metadata = columnMetadataMap.get(sqlColumn.getName());
					final String value = row.get(sqlColumn.getNumber() - 1);
					final boolean persisted = DatabaseHelper.set(value, metadata, preparedStatement);
					if (!persisted) {
						log.error(
							"Error when setting value {} for column {} in lookup source table {}",
							value,
							sqlColumn.getName(),
							sqlTable.getSource()
						);
					}
				}

				preparedStatement.addBatch();
				count++;

				// Execute batch and clear cache periodically
				if (count % batchSize == 0) {
					preparedStatement.executeBatch();
					preparedStatement.clearBatch(); // Clears PreparedStatement cache
					connection.commit();
					log.debug("Batch INSERT executed: {} rows committed.", batchSize);
				}
			}

			// Execute remaining batch
			preparedStatement.executeBatch();
			preparedStatement.clearBatch(); // Clears final cache
			connection.commit();
			log.debug("Final batch INSERT completed for table: {}. Total rows committed: {}", alias, count);
		} catch (Exception exception) {
			log.error("Error when batch inserting for table {}: {}", alias, exception.getMessage());
			log.debug("Batch Insert SQL Exception: ", exception);
		}
	}

	/**
	 * Create the SQL 'CREATE TABLE' query to create the SQL table corresponding to the {@link SqlTable} object.
	 * @param sqlTable the {@link SqlTable} to use to create the SQL 'CREATE TABLE' query.
	 * @return The SQL 'CREATE TABLE' query to create the table.
	 */
	private String createTableQuery(final SqlTable sqlTable) {
		StringBuilder queryBuilder = new StringBuilder();
		queryBuilder.append("CREATE TABLE ");

		final String alias = sqlTable.getAlias().strip();

		if (alias.isBlank() || alias.contains(";") || alias.contains(" ") || alias.contains("\"")) {
			log.error("Invalid alias {} for the source {}", sqlTable.getAlias(), sqlTable.getSource());
			return null;
		}

		queryBuilder.append(alias);
		queryBuilder.append(" (");

		final List<String> columnNameType = new ArrayList<>();

		for (final SqlColumn sqlColumn : sqlTable.getColumns()) {
			final String name = sqlColumn.getName();
			if (name == null || name.isBlank()) {
				log.error("Invalid name in lookup source table {}", sqlTable.getSource());
				return null;
			}

			final String type = sqlColumn.getType();
			if (type == null || type.isBlank()) {
				log.error("Invalid type for column {} in lookup source table {}", name, sqlTable.getSource());
				return null;
			}

			final int number = sqlColumn.getNumber();

			// Check if number is valid
			if (number < 1) {
				log.error("Wrong number {} for column {} in lookup source table {}", number, name, sqlTable.getSource());
				return null;
			}
			columnNameType.add(String.join(" ", name, type));
		}

		queryBuilder.append(String.join(",", columnNameType));
		queryBuilder.append(");");

		return queryBuilder.toString();
	}
}
