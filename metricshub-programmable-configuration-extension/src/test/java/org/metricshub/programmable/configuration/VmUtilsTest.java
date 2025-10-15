package org.metricshub.programmable.configuration;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link VmUtils}.
 */
public class VmUtilsTest {

	private final VmUtils vmUtils = new VmUtils();

	@Test
	void testWithNonNullString() {
		String input = "MySQL.0";
		char[] expected = { 'M', 'y', 'S', 'Q', 'L', '.', '0' };

		char[] result = vmUtils.toCharArray(input);

		assertNotNull(result, "Result should not be null for a valid input string");
		assertArrayEquals(expected, result, "Char array should match the input string");
	}

	@Test
	void testWithNullString() {
		assertNull(vmUtils.toCharArray(null), "Result should be null when input is null");
	}

	@Test
	void testWithEmptyString() {
		char[] result = vmUtils.toCharArray("");
		assertNotNull(result, "Result should not be null for empty string");
		assertEquals(0, result.length, "Empty string should result in empty char array");
	}
}
