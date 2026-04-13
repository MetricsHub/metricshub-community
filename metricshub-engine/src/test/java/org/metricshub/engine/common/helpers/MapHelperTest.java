package org.metricshub.engine.common.helpers;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.Test;

class MapHelperTest {

	@Test
	void testAreEqual() {
		final Map<String, String> map = Map.of("key", "value");
		assertTrue(MapHelper.areEqual(map, map));
		assertFalse(MapHelper.areEqual(null, map));
		assertFalse(MapHelper.areEqual(map, null));
		assertTrue(MapHelper.areEqual(null, null));

		assertTrue(MapHelper.areEqual(Map.of("key", "value"), Map.of("key", "value")));

		assertFalse(MapHelper.areEqual(Map.of("key", "value1"), Map.of("key", "value2")));

		assertFalse(MapHelper.areEqual(Map.of("key", "value1"), Map.of("key1", "value1", "key2", "value2")));

		final Map<String, String> hashMap = new HashMap<>();
		hashMap.put("key", "value");
		assertTrue(MapHelper.areEqual(Map.of("key", "value"), hashMap));

		final Map<String, String> concurrentHashMap = new ConcurrentHashMap<>();
		concurrentHashMap.put("key", "value");
		assertTrue(MapHelper.areEqual(Map.of("key", "value"), concurrentHashMap));
	}
}
