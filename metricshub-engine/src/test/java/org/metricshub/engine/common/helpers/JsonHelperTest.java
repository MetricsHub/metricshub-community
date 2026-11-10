package org.metricshub.engine.common.helpers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

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

	@ParameterizedTest
	@ValueSource(
		strings = {
			"+0",
			"-1_",
			".0",
			"0.000000001",
			"1.0",
			"1.",
			"0x1F",
			"0b101",
			"1e3",
			"0.5e-3",
			"1_000",
			"+.5",
			"+12",
			".inf",
			"-.inf",
			".NaN",
			"007",
			"true",
			"null",
			"",
			"plain" }
	)
	void testBuildYamlMapperKeepsStrings(final String value) throws Exception {
		final ObjectMapper mapper = JsonHelper.buildYamlMapper();
		final JsonNode read = mapper.readTree(
			mapper.writeValueAsBytes(JsonNodeFactory.instance.objectNode().put("value", value))
		).get("value");
		assertTrue(read.isTextual(), () -> value + " read back as " + read.getNodeType());
		assertEquals(value, read.asText());
	}

	@Test
	void testBuildYamlMapperKeepsNumbersAndBooleans() throws Exception {
		final ObjectMapper mapper = JsonHelper.buildYamlMapper();
		final ObjectNode node = JsonNodeFactory.instance.objectNode().put("int", 1).put("double", 1.5).put("bool", true);
		final String yaml = mapper.writeValueAsString(node);
		assertEquals("---\nint: 1\ndouble: 1.5\nbool: true\n", yaml);
		assertEquals(node, mapper.readTree(yaml));
	}
}
