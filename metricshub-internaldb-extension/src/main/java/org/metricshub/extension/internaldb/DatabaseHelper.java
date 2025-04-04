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

import java.math.BigDecimal;
import java.sql.ParameterMetaData;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Helper class for database operations.
 */
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Slf4j
public class DatabaseHelper {

	/**
	 * Extracts column metadata dynamically from the given PreparedStatement.
	 *
	 * @param preparedStatement The prepared statement already initialized with the INSERT query.
	 * @param columnNames       The column names from the SQL table.
	 * @return A map of column names to their metadata.
	 * @throws SQLException If there is an error retrieving the metadata.
	 */
	public static Map<String, ColumnMetadata> prepareColumnMetadata(
		final PreparedStatement preparedStatement,
		final List<String> columnNames
	) throws SQLException {
		final Map<String, ColumnMetadata> columnMetadataMap = new HashMap<>();

		try {
			final ParameterMetaData parameterMetadata = preparedStatement.getParameterMetaData();

			for (int i = 1; i <= parameterMetadata.getParameterCount(); i++) {
				// Fully qualified class name
				final String fullyQualifiedClassName = parameterMetadata.getParameterClassName(i);
				// SQL type
				final int sqlType = parameterMetadata.getParameterType(i);

				// Resolve Java type from the class name
				final Class<?> javaType = resolveClass(fullyQualifiedClassName);

				// Use column names from the SQL table
				columnMetadataMap.put(columnNames.get(i - 1), new ColumnMetadata(i, javaType, sqlType));
			}
		} catch (SQLException exception) {
			log.error("Error retrieving ParameterMetaData from PreparedStatement: {}", exception.getMessage());
			log.debug("Exception:", exception);
			throw exception;
		}

		return columnMetadataMap;
	}

	/**
	 * Resolves Java types using `Class.forName()`.
	 *
	 * @param className Fully qualified class name from `ParameterMetaData`.
	 * @return The corresponding Java class.
	 */
	private static Class<?> resolveClass(final String className) {
		try {
			return Class.forName(className); // Dynamically load the class
		} catch (ClassNotFoundException e) {
			log.error("Class not found for JDBC type mapping: {}", className);
			return Object.class; // Fallback type
		}
	}

	/**
	 * Set values in a PreparedStatement dynamically based on column metadata.
	 *
	 * @param value             The value to set.
	 * @param columnMetadata    The column metadata (position, Java type, SQL type).
	 * @param preparedStatement The prepared statement.
	 * @return true if setting was successful, false otherwise.
	 */
	public static boolean set(
		final String value,
		final ColumnMetadata columnMetadata,
		final PreparedStatement preparedStatement
	) {
		final int position = columnMetadata.getPosition();
		final Class<?> javaType = columnMetadata.getJavaType();
		final int sqlType = columnMetadata.getSqlType();

		try {
			if (value == null || value.isEmpty()) {
				preparedStatement.setNull(position, sqlType);
				return true;
			}

			// Trim only for non-String types to avoid parsing errors
			final String trimmedValue = trimValue(value, javaType);

			// Map Java types to PreparedStatement setters
			if (Integer.class.equals(javaType)) {
				preparedStatement.setInt(position, Integer.parseInt(trimmedValue));
			} else if (Long.class.equals(javaType)) {
				preparedStatement.setLong(position, Long.parseLong(trimmedValue));
			} else if (BigDecimal.class.equals(javaType)) {
				preparedStatement.setBigDecimal(position, new BigDecimal(trimmedValue));
			} else if (Boolean.class.equals(javaType)) {
				preparedStatement.setBoolean(position, Boolean.parseBoolean(trimmedValue));
			} else if (Double.class.equals(javaType)) {
				preparedStatement.setDouble(position, Double.parseDouble(trimmedValue));
			} else if (Float.class.equals(javaType)) {
				preparedStatement.setFloat(position, Float.parseFloat(trimmedValue));
			} else if (java.sql.Date.class.equals(javaType)) {
				preparedStatement.setDate(position, java.sql.Date.valueOf(trimmedValue));
			} else if (java.sql.Time.class.equals(javaType)) {
				preparedStatement.setTime(position, java.sql.Time.valueOf(trimmedValue));
			} else if (java.sql.Timestamp.class.equals(javaType)) {
				preparedStatement.setTimestamp(position, java.sql.Timestamp.valueOf(trimmedValue));
			} else if (Byte.class.equals(javaType)) {
				preparedStatement.setByte(position, Byte.parseByte(trimmedValue));
			} else if (Short.class.equals(javaType)) {
				preparedStatement.setShort(position, Short.parseShort(trimmedValue));
			} else {
				// Default to String
				preparedStatement.setString(position, value);
			}

			return true;
		} catch (Exception ex) {
			log.error("Error setting value '{}' at position {}: {}", value, position, ex.getMessage());
			return false;
		}
	}

	/**
	 * Trim the value if it is not a String.
	 *
	 * @param value    The value to trim.
	 * @param javaType The Java type of the value.
	 * @return The trimmed value.
	 */
	private static String trimValue(final String value, final Class<?> javaType) {
		return String.class.equals(javaType) ? value : value.trim();
	}
}
