package org.metricshub.engine.common.helpers;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.metricshub.engine.common.helpers.ArrayHelper.getValueAtIndex;
import static org.metricshub.engine.common.helpers.ArrayHelper.hexToByteArray;

import org.junit.jupiter.api.Test;

class ArrayHelperTest {

	@Test
	void getValueAtIndexTest() {
		Integer[] array = { 1, 2, 3, 4 };
		assertEquals(3, getValueAtIndex(array, 2, 6));
		assertEquals(6, getValueAtIndex(array, 5, 6));
	}

	@Test
	void testConvert() {
		assertArrayEquals(new byte[] {}, hexToByteArray(null));
		assertThrows(IllegalArgumentException.class, () -> hexToByteArray("illegal"));
		assertArrayEquals(new byte[] { 0x01, 0x02 }, hexToByteArray("0x0102"));
		assertArrayEquals(new byte[] { (byte) 0xab, (byte) 0xcd, (byte) 0xef }, hexToByteArray(" abCdEF    "));
		assertThrows(IllegalArgumentException.class, () -> hexToByteArray("123"));
		assertArrayEquals(new byte[] {}, hexToByteArray("   "));
	}

	@Test
	void testAnyMatchLowerCase() {
		assertTrue(
			ArrayHelper.anyMatchLowerCase(str -> str.contains("value"), "value", "VALUE1", "val", "Value2", null, "")
		);

		assertFalse(ArrayHelper.anyMatchLowerCase(str -> str.contains("value"), (String) null, (String) null));

		assertFalse(ArrayHelper.anyMatchLowerCase(str -> str.contains("value"), "notMatch"));
	}
}
