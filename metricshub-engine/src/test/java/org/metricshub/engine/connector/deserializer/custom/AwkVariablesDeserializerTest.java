package org.metricshub.engine.connector.deserializer.custom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.metricshub.engine.common.helpers.JsonHelper;
import org.metricshub.engine.connector.model.monitor.task.source.JawkSource;
import org.metricshub.engine.connector.model.monitor.task.source.compute.Awk;

class AwkVariablesDeserializerTest {

	private static final ObjectMapper MAPPER = JsonHelper.buildYamlMapper();

	/**
	 * Scalars of every YAML kind, which must read exactly as a <code>Map&lt;String, String&gt;</code> reads them.
	 */
	private static final String SCALARS = """
		variables:
		  octal: 010
		  decimal: 1.50
		  exponent: 1e3
		  hexadecimal: 0x1F
		  flag: true
		  word: yes
		  date: 2026-11-12
		  quoted: "010"
		  separator: ";"
		  reference: ${source::beforeAll.otherSource}
		  empty:
		  tilde: ~
		""";

	private static final String NESTED = """
		variables:
		  properties:
		  - node
		  - 2
		  - ""
		  -
		  labels:
		    vendor: NetApp
		    1: one
		    nested:
		    - x
		    - k: v
		  rows:
		  - - a
		    - b
		  - - c
		""";

	/**
	 * The variables as a <code>Map&lt;String, String&gt;</code> read them, before lists and maps.
	 *
	 * @param variables The variables
	 */
	private record Before(Map<String, String> variables) {}

	/**
	 * The variables of an awk compute, read from YAML text or from a {@link JsonNode} tree (as the connector parser
	 * reads a connector), match what a <code>Map&lt;String, String&gt;</code> reads on the same path: from a tree, a
	 * YAML number is the number (<code>010</code> is <code>8</code>), from the text it is the text.
	 */
	@Test
	void testScalarsReadAsBefore() throws IOException {
		final String compute = "type: awk\nscript: x\n" + SCALARS;
		final JsonNode tree = MAPPER.readTree(compute);
		final Map<String, String> fromText = MAPPER.readValue(compute, Before.class).variables();
		final Map<String, String> fromTree = MAPPER.treeToValue(tree, Before.class).variables();

		assertEquals(fromText, MAPPER.readValue(compute, Awk.class).getVariables(), "From YAML text");
		assertEquals(fromTree, MAPPER.treeToValue(tree, Awk.class).getVariables(), "From a JsonNode tree");
		assertEquals(fromTree, MAPPER.treeToValue(tree, JawkSource.class).getVariables(), "On an awk source");
		assertEquals("8", fromTree.get("octal"), "The connector parser reads a tree");
		assertEquals("${source::beforeAll.otherSource}", fromTree.get("reference"));
		assertNull(fromTree.get("empty"), "A variable without a value is null");
		assertEquals(12, fromTree.size());
	}

	@Test
	void testListsAndMaps() throws IOException {
		final Map<String, Object> labels = new LinkedHashMap<>();
		labels.put("vendor", "NetApp");
		labels.put("1", "one");
		labels.put("nested", List.of("x", Map.of("k", "v")));

		final Map<String, Object> expected = new LinkedHashMap<>();
		// The null item stays null: AwkVariableHelper exposes it as the empty string
		expected.put("properties", Arrays.asList("node", "2", "", null));
		expected.put("labels", labels);
		expected.put("rows", List.of(List.of("a", "b"), List.of("c")));

		final String compute = "type: awk\nscript: x\n" + NESTED;
		assertEquals(expected, MAPPER.readValue(compute, Awk.class).getVariables(), "From YAML text");
		assertEquals(expected, MAPPER.treeToValue(MAPPER.readTree(compute), Awk.class).getVariables(), "From a tree");
	}

	@Test
	void testCopyKeepsTheNestedValues() throws IOException {
		final Awk awk = MAPPER.readValue("type: awk\nscript: x\n" + NESTED, Awk.class);
		assertEquals(awk.getVariables(), awk.copy().getVariables());
	}
}
