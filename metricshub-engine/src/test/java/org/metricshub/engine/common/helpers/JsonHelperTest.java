package org.metricshub.engine.common.helpers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

class JsonHelperTest {

	@Test
	void testSerialize() {
		assertEquals("true", JsonHelper.serialize(true));
		assertEquals("[ 0 ]", JsonHelper.serialize(new int[1]));
		assertEquals("null", JsonHelper.serialize(null));
	}

	@Test
	void testIsNotNull() {
		final ObjectNode node = JsonNodeFactory.instance.objectNode();
		assertTrue(JsonHelper.isNotNull(node));
		assertFalse(JsonHelper.isNotNull(null));
		node.set("subnode", null);
		assertFalse(JsonHelper.isNotNull(node.get("subnode")));
	}
}
