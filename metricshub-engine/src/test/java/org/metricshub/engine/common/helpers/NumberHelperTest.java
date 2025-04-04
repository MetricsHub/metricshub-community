package org.metricshub.engine.common.helpers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

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
}
