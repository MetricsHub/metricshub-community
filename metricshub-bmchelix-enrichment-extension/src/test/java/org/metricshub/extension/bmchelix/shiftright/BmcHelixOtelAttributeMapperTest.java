package org.metricshub.extension.bmchelix.shiftright;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.opentelemetry.proto.common.v1.AnyValue;
import io.opentelemetry.proto.common.v1.KeyValue;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class BmcHelixOtelAttributeMapperTest {

	@Test
	void toMap_shouldConvertAttributesToStringValues() {
		final List<KeyValue> attributes = List.of(
			KeyValue.newBuilder().setKey("stringKey").setValue(AnyValue.newBuilder().setStringValue("value").build()).build(),
			KeyValue.newBuilder().setKey("intKey").setValue(AnyValue.newBuilder().setIntValue(42L).build()).build(),
			KeyValue.newBuilder().setKey("boolKey").setValue(AnyValue.newBuilder().setBoolValue(true).build()).build()
		);

		final Map<String, String> result = new BmcHelixOtelAttributeMapper().toMap(attributes);

		assertEquals("value", result.get("stringKey"));
		assertEquals("42", result.get("intKey"));
		assertEquals("true", result.get("boolKey"));
	}

	@Test
	void toKeyValues_shouldConvertMapToKeyValues() {
		final List<KeyValue> result = new BmcHelixOtelAttributeMapper()
			.toKeyValues(Map.of("key1", "value1", "key2", "value2"));

		assertEquals(2, result.size(), "Should create two key-values");
		assertTrue(result.stream().anyMatch(keyValue -> "key1".equals(keyValue.getKey())));
		assertTrue(result.stream().anyMatch(keyValue -> "key2".equals(keyValue.getKey())));
	}
}
