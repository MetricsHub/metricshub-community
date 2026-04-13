package org.metricshub.engine.common.helpers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.metricshub.engine.common.helpers.NumberHelper.getPositiveOrDefault;

import java.math.RoundingMode;
import org.junit.jupiter.api.Test;

class NumberHelperTest {

	@Test
	void testParseDouble() {
		assertEquals(5.0, NumberHelper.parseDouble("not a number", 5.0));
		assertEquals(7.0, NumberHelper.parseDouble("7", 5.0));
		assertEquals(7.0, NumberHelper.parseDouble("7.0", 5.0));
	}

	@Test
	void testParseInt() {
		assertEquals(5, NumberHelper.parseInt("not a number", 5));
		assertEquals(7, NumberHelper.parseInt("7", 5));
		assertEquals(5, NumberHelper.parseInt("7.0", 5));
	}

	@Test
	void testRound() {
		assertEquals(20D, NumberHelper.round(20.000001, 2, RoundingMode.HALF_UP));
		assertEquals(20, NumberHelper.round(20.000001, 0, RoundingMode.HALF_UP));
		assertEquals(20D, NumberHelper.round(20.00, 1, RoundingMode.HALF_UP));
		assertEquals(20.1113, NumberHelper.round(20.11125, 4, RoundingMode.HALF_UP));
		assertEquals(5, NumberHelper.round(4.5, 0, RoundingMode.HALF_UP));
		assertEquals(4.5, NumberHelper.round(4.5, 1, RoundingMode.HALF_UP));
	}

	@Test
	void testCleanUpEnumInput() {
		assertNull(NumberHelper.cleanUpEnumInput(null));
		assertEquals("3", NumberHelper.cleanUpEnumInput("3.00000"));
		assertEquals("-3", NumberHelper.cleanUpEnumInput("-3.0000000"));
		assertEquals("3.01", NumberHelper.cleanUpEnumInput("3.01"));
		assertEquals("-3", NumberHelper.cleanUpEnumInput("-3"));
		assertEquals("0", NumberHelper.cleanUpEnumInput("0"));
		assertEquals("0", NumberHelper.cleanUpEnumInput("0.0"));
		assertEquals("1", NumberHelper.cleanUpEnumInput("1.0"));
		assertEquals("2", NumberHelper.cleanUpEnumInput("2.0"));
		assertEquals("ok", NumberHelper.cleanUpEnumInput(" OK "));
	}

	@Test
	void testFormatNumber() {
		assertEquals("10.05", NumberHelper.formatNumber(10.05));
		assertEquals("        10.05", NumberHelper.formatNumber(10.05, "%10s%s"));
	}

	@Test
	void testGetPositiveOrDefault() {
		final Number defaultValue = 42;

		assertEquals(defaultValue, getPositiveOrDefault(null, defaultValue), () -> "Null input: should return default");
		assertEquals(5, getPositiveOrDefault(5, defaultValue), () -> "Positive integer: should return input");
		assertEquals(defaultValue, getPositiveOrDefault(0, defaultValue), () -> "Zero: should return default");
		assertEquals(defaultValue, getPositiveOrDefault(-3, defaultValue), () -> "Negative: should return default");
		assertEquals(0.1, getPositiveOrDefault(0.1, defaultValue), () -> "Double 0.1 should return input");
		assertEquals(1.9, getPositiveOrDefault(1.9, defaultValue), () -> "Double 1.9 truncates to 1: should return input");
	}

	@Test
	void testIsNumeric() {
		assertEquals(true, NumberHelper.isNumeric("  123  "), "Should recognize numeric string with spaces");
		assertEquals(true, NumberHelper.isNumeric("  123.45  "), "Should recognize numeric string with decimal and spaces");
		assertEquals(true, NumberHelper.isNumeric("-123.45"), "Should recognize negative numeric string with decimal");
		assertEquals(false, NumberHelper.isNumeric("abc"), "Should not recognize non-numeric string");
		assertEquals(false, NumberHelper.isNumeric(null), "Should not recognize null as numeric");
		assertEquals(false, NumberHelper.isNumeric("123abc"), "Should not recognize alphanumeric string");
	}
}
