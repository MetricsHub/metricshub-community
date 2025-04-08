package org.metricshub.engine.common.helpers;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class ListHelperTest {

	@Test
	void getValueAtIndexTest() {
		List<Integer> array = Arrays.asList(1, 2, 3, 4);
		assertEquals(3, ListHelper.getValueAtIndex(array, 2, 6));
		assertEquals(6, ListHelper.getValueAtIndex(array, 5, 6));
	}
}
