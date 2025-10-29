package org.metricshub.extension.internaldb;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Time;
import java.sql.Timestamp;
import java.sql.Types;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DatabaseHelperTest {

	private PreparedStatement preparedStatement;

	@BeforeEach
	void setUp() {
		preparedStatement = mock(PreparedStatement.class);
	}

	@Test
	void testSetInteger() throws SQLException {
		final ColumnMetadata metadata = new ColumnMetadata(1, Integer.class, Types.INTEGER);
		boolean result = DatabaseHelper.set("123", metadata, preparedStatement);

		verify(preparedStatement).setInt(1, 123);
		assertTrue(result);

		result = DatabaseHelper.set(" 123 ", metadata, preparedStatement);

		verify(preparedStatement, times(2)).setInt(1, 123);
		assertTrue(result);
	}

	@Test
	void testSetLong() throws SQLException {
		final ColumnMetadata metadata = new ColumnMetadata(2, Long.class, Types.BIGINT);
		boolean result = DatabaseHelper.set("456789", metadata, preparedStatement);

		verify(preparedStatement).setLong(2, 456789L);
		assertTrue(result);

		result = DatabaseHelper.set(" 456789 ", metadata, preparedStatement);

		verify(preparedStatement, times(2)).setLong(2, 456789L);
		assertTrue(result);
	}

	@Test
	void testSetBigDecimal() throws SQLException {
		final ColumnMetadata metadata = new ColumnMetadata(3, BigDecimal.class, Types.DECIMAL);
		boolean result = DatabaseHelper.set("123.45", metadata, preparedStatement);

		verify(preparedStatement).setBigDecimal(3, new BigDecimal("123.45"));
		assertTrue(result);

		result = DatabaseHelper.set(" 123.45 ", metadata, preparedStatement);

		verify(preparedStatement, times(2)).setBigDecimal(3, new BigDecimal("123.45"));
		assertTrue(result);
	}

	@Test
	void testSetBoolean() throws SQLException {
		final ColumnMetadata metadata = new ColumnMetadata(4, Boolean.class, Types.BOOLEAN);
		boolean result = DatabaseHelper.set("true", metadata, preparedStatement);

		verify(preparedStatement).setBoolean(4, true);
		assertTrue(result);

		result = DatabaseHelper.set(" TRUE ", metadata, preparedStatement);

		verify(preparedStatement, times(2)).setBoolean(4, true);
		assertTrue(result);
	}

	@Test
	void testSetDouble() throws SQLException {
		final ColumnMetadata metadata = new ColumnMetadata(5, Double.class, Types.DOUBLE);
		boolean result = DatabaseHelper.set("12.34", metadata, preparedStatement);

		verify(preparedStatement).setDouble(5, 12.34);
		assertTrue(result);

		result = DatabaseHelper.set(" 12.34 ", metadata, preparedStatement);

		verify(preparedStatement, times(2)).setDouble(5, 12.34);
		assertTrue(result);
	}

	@Test
	void testSetFloat() throws SQLException {
		final ColumnMetadata metadata = new ColumnMetadata(6, Float.class, Types.FLOAT);
		boolean result = DatabaseHelper.set("5.67", metadata, preparedStatement);

		verify(preparedStatement).setFloat(6, 5.67f);
		assertTrue(result);

		result = DatabaseHelper.set(" 5.67 ", metadata, preparedStatement);

		verify(preparedStatement, times(2)).setFloat(6, 5.67f);
		assertTrue(result);
	}

	@Test
	void testSetString() throws SQLException {
		final ColumnMetadata metadata = new ColumnMetadata(7, String.class, Types.VARCHAR);
		boolean result = DatabaseHelper.set("Hello", metadata, preparedStatement);

		verify(preparedStatement).setString(7, "Hello");
		assertTrue(result);

		result = DatabaseHelper.set(" Hello ", metadata, preparedStatement);

		verify(preparedStatement).setString(7, " Hello ");
		assertTrue(result);
	}

	@Test
	void testSetDate() throws SQLException {
		final ColumnMetadata metadata = new ColumnMetadata(8, Date.class, Types.DATE);
		boolean result = DatabaseHelper.set("2024-01-30", metadata, preparedStatement);

		verify(preparedStatement).setDate(8, Date.valueOf("2024-01-30"));
		assertTrue(result);

		result = DatabaseHelper.set("  2024-01-30  ", metadata, preparedStatement);

		verify(preparedStatement, times(2)).setDate(8, Date.valueOf("2024-01-30"));
		assertTrue(result);
	}

	@Test
	void testSetTime() throws SQLException {
		final ColumnMetadata metadata = new ColumnMetadata(9, Time.class, Types.TIME);
		boolean result = DatabaseHelper.set("12:34:56", metadata, preparedStatement);

		verify(preparedStatement).setTime(9, Time.valueOf("12:34:56"));
		assertTrue(result);

		result = DatabaseHelper.set(" 12:34:56 ", metadata, preparedStatement);

		verify(preparedStatement, times(2)).setTime(9, Time.valueOf("12:34:56"));
		assertTrue(result);
	}

	@Test
	void testSetTimestamp() throws SQLException {
		final ColumnMetadata metadata = new ColumnMetadata(10, Timestamp.class, Types.TIMESTAMP);
		boolean result = DatabaseHelper.set("2024-01-30 15:45:00", metadata, preparedStatement);

		verify(preparedStatement).setTimestamp(10, Timestamp.valueOf("2024-01-30 15:45:00"));
		assertTrue(result);

		result = DatabaseHelper.set(" 2024-01-30 15:45:00 ", metadata, preparedStatement);

		verify(preparedStatement, times(2)).setTimestamp(10, Timestamp.valueOf("2024-01-30 15:45:00"));
		assertTrue(result);
	}

	@Test
	void testSetByte() throws SQLException {
		final ColumnMetadata metadata = new ColumnMetadata(11, Byte.class, Types.TINYINT);
		boolean result = DatabaseHelper.set("127", metadata, preparedStatement);

		verify(preparedStatement).setByte(11, (byte) 127);
		assertTrue(result);

		result = DatabaseHelper.set("  127  ", metadata, preparedStatement);
		assertTrue(result);
		verify(preparedStatement, times(2)).setByte(11, (byte) 127);
	}

	@Test
	void testSetShort() throws SQLException {
		final ColumnMetadata metadata = new ColumnMetadata(12, Short.class, Types.SMALLINT);
		boolean result = DatabaseHelper.set("32767", metadata, preparedStatement);

		verify(preparedStatement).setShort(12, (short) 32767);
		assertTrue(result);

		result = DatabaseHelper.set("  32767   ", metadata, preparedStatement);

		verify(preparedStatement, times(2)).setShort(12, (short) 32767);
		assertTrue(result);
	}

	@Test
	void testSetNullValue() throws SQLException {
		final ColumnMetadata metadata = new ColumnMetadata(13, Integer.class, Types.INTEGER);
		boolean result = DatabaseHelper.set(null, metadata, preparedStatement);

		verify(preparedStatement).setNull(13, Types.INTEGER);
		assertTrue(result);
	}

	@Test
	void testSetEmptyValue() throws SQLException {
		final ColumnMetadata metadata = new ColumnMetadata(14, String.class, Types.VARCHAR);
		boolean result = DatabaseHelper.set("", metadata, preparedStatement);

		verify(preparedStatement).setNull(14, Types.VARCHAR);
		assertTrue(result);
	}

	@Test
	void testSetInvalidInteger() {
		final ColumnMetadata metadata = new ColumnMetadata(15, Integer.class, Types.INTEGER);
		boolean result = DatabaseHelper.set("invalid", metadata, preparedStatement);

		// Exception should be caught and return false
		assertFalse(result);
	}

	@Test
	void testSetInvalidBoolean() throws SQLException {
		final ColumnMetadata metadata = new ColumnMetadata(16, Boolean.class, Types.BOOLEAN);
		boolean result = DatabaseHelper.set("notBoolean", metadata, preparedStatement);

		// Invalid boolean should not throw an exception but will store "false"
		verify(preparedStatement).setBoolean(16, false);
		assertTrue(result);
	}
}
