package org.metricshub.engine.connector.parser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.TextNode;
import java.io.IOException;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EnvironmentProcessorTest {

	@Test
	void testProcessNode() throws IOException {
		final String keyPropertyName = "key";

		// Test Case: Verifies that a JsonNode with an environment variable placeholder remains unchanged after the environment processing.
		// This checks the processor's ability to ignore not found environment variables, maintaining their state post-processing.
		// This test dynamically generates a unique, non-existing environment variable placeholder using UUID, ensuring that
		// the environment variable is unlikely to exist.
		{
			final TextNode textNode = new TextNode(String.format("${env::%s}", UUID.randomUUID().toString()));
			final JsonNode jsonNode = JsonNodeFactory.instance.objectNode().set(keyPropertyName, textNode);
			assertEquals(
				textNode,
				jsonNode.get(keyPropertyName),
				"Initial state should contain the environment variable placeholder."
			);
			new EnvironmentProcessor().process(jsonNode);
			assertEquals(
				textNode,
				jsonNode.get(keyPropertyName),
				"Post-processing state should contain the environment variable placeholder."
			);
		}

		// Test Case: Verifies that a NullNode remains unchanged after the environment processing.
		// This checks the processor's ability to ignore null values, maintaining their state post-processing.
		{
			final JsonNode jsonNode = JsonNodeFactory.instance
				.objectNode()
				.set(keyPropertyName, JsonNodeFactory.instance.nullNode());
			assertTrue(jsonNode.get(keyPropertyName).isNull(), "Initial state should be a NullNode.");
			new EnvironmentProcessor().process(jsonNode);
			assertTrue(jsonNode.get(keyPropertyName).isNull(), "Post-processing state should remain a NullNode.");
		}

		// Test Case: Asserts that an empty TextNode is not modified by the environment processor.
		// This evaluates the processor's handling of empty string values, ensuring they're preserved.
		{
			final TextNode emptyTextNode = new TextNode("");
			final JsonNode jsonNode = JsonNodeFactory.instance.objectNode().set(keyPropertyName, emptyTextNode);
			assertEquals(emptyTextNode, jsonNode.get(keyPropertyName), "Initial state should be an empty TextNode.");
			new EnvironmentProcessor().process(jsonNode);
			assertEquals(
				emptyTextNode,
				jsonNode.get(keyPropertyName),
				"Post-processing state should remain an empty TextNode."
			);
		}

		// Test Case: Ensures that a TextNode with a null value is not altered by the environment processor.
		// This case tests the processor's handling of null values within TextNodes specifically.
		{
			final TextNode nullValueTextNode = new TextNode(null);
			final JsonNode jsonNode = JsonNodeFactory.instance.objectNode().set(keyPropertyName, nullValueTextNode);
			assertEquals(
				nullValueTextNode,
				jsonNode.get(keyPropertyName),
				"Initial state should be a TextNode with a null value."
			);
			new EnvironmentProcessor().process(jsonNode);
			assertEquals(
				nullValueTextNode,
				jsonNode.get(keyPropertyName),
				"Post-processing state should remain a TextNode with a null value."
			);
		}

		final String javaHomeEnvironmentVariable = "${env::JAVA_HOME}";

		// Test Case: Verifies that a simple ObjectNode containing an environment variable placeholder
		// is correctly processed, replacing the placeholder with the actual environment value.
		{
			final JsonNode jsonNode = JsonNodeFactory.instance
				.objectNode()
				.set(keyPropertyName, new TextNode(javaHomeEnvironmentVariable));
			assertEquals(
				javaHomeEnvironmentVariable,
				jsonNode.get(keyPropertyName).asText(),
				"Initial state should contain the environment variable placeholder."
			);
			new EnvironmentProcessor().process(jsonNode);
			assertNotEquals(
				javaHomeEnvironmentVariable,
				jsonNode.get(keyPropertyName).asText(),
				"Post-processing should replace the placeholder with the actual environment value."
			);
		}

		// Test Case: Checks the processor's ability to replace multiple concatenated environment variable placeholders
		// within a single TextNode, ensuring each instance is correctly processed.
		{
			final JsonNode jsonNode = JsonNodeFactory.instance
				.objectNode()
				.set(keyPropertyName, new TextNode(javaHomeEnvironmentVariable + " " + javaHomeEnvironmentVariable));
			assertTrue(
				jsonNode.get(keyPropertyName).asText().contains(javaHomeEnvironmentVariable),
				"Initial state should contain concatenated environment variable placeholders."
			);
			new EnvironmentProcessor().process(jsonNode);
			assertFalse(
				jsonNode.get(keyPropertyName).asText().contains(javaHomeEnvironmentVariable),
				"Post-processing should replace all placeholders with the actual environment values."
			);
		}

		// Test Case: Validates that all occurrences of the environment variable placeholders within an ArrayNode
		// are individually processed and replaced, demonstrating the processor's effectiveness in array structures.
		{
			final JsonNode jsonNode = JsonNodeFactory.instance
				.objectNode()
				.set(
					keyPropertyName,
					JsonNodeFactory.instance.arrayNode().add(javaHomeEnvironmentVariable).add(javaHomeEnvironmentVariable)
				);
			final ArrayNode arrayNode = (ArrayNode) jsonNode.get(keyPropertyName);
			for (JsonNode node : arrayNode) {
				assertEquals(
					javaHomeEnvironmentVariable,
					node.asText(),
					"Initial array elements should be environment variable placeholders."
				);
			}
			new EnvironmentProcessor().process(jsonNode);
			for (JsonNode node : arrayNode) {
				assertNotEquals(
					javaHomeEnvironmentVariable,
					node.asText(),
					"Post-processing should replace placeholders in all array elements with actual environment values."
				);
			}
		}
	}
}
