package org.metricshub.web.mcp.tool;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.metricshub.engine.tool.ToolDefinition;
import org.metricshub.engine.tool.ToolParameter;
import org.springframework.ai.tool.ToolCallback;

class HostToolsTest {

	private static final ObjectMapper MAPPER = new ObjectMapper();

	private static ToolDefinition definition(final boolean destructive) {
		final Map<String, ToolParameter> parameters = new LinkedHashMap<>();
		parameters.put("pattern", new ToolParameter("string", "Regex on the name", false, ".*"));
		parameters.put("owner", new ToolParameter(null, "The owner", true, null));
		return new ToolDefinition("listThings", "Lists the things.", parameters, destructive);
	}

	@Test
	void testPerHostRejectsReservedParameterNames() {
		final ToolDefinition definition = new ToolDefinition(
			"bad",
			"Declares the host selection itself.",
			Map.of(HostTools.HOSTNAME, new ToolParameter("string", "The host", true, null)),
			false
		);

		final IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
			HostTools.perHost("Test_bad", definition, (hostname, arguments) -> Map.of())
		);

		assertTrue(exception.getMessage().contains(HostTools.HOSTNAME), exception.getMessage());
	}

	@Test
	void testPerHostRejectsANameLongerThanAFunctionNameMayBe() {
		final String name = HostTools.toolName("a".repeat(50), "listTheThingsOfTheHost");

		final IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
			HostTools.perHost(name, definition(false), (hostname, arguments) -> Map.of())
		);

		assertTrue(exception.getMessage().contains("64"), exception.getMessage());
	}

	@Test
	void testToolNameKeepsToMcpCharacters() {
		assertEquals("ipmi_sel", HostTools.toolName("ipmi", "sel"));
		assertEquals("My_Connector_v2_listThings", HostTools.toolName("My Connector.v2", "listThings"));
	}

	@Test
	void testInputSchemaAddsHostnameAndPoolSize() {
		final ObjectNode schema = HostTools.inputSchema(definition(false));

		assertEquals("object", schema.get("type").asText());
		final JsonNode properties = schema.get("properties");
		assertEquals("array", properties.get(HostTools.HOSTNAME).get("type").asText());
		assertEquals("string", properties.get(HostTools.HOSTNAME).get("items").get("type").asText());
		assertEquals("string", properties.get("pattern").get("type").asText());
		assertEquals("Regex on the name Default: .*", properties.get("pattern").get("description").asText());
		assertEquals("string", properties.get("owner").get("type").asText());
		assertEquals("integer", properties.get(HostTools.POOL_SIZE).get("type").asText());

		final List<String> required = new ArrayList<>();
		schema.get("required").forEach(node -> required.add(node.asText()));
		assertEquals(List.of(HostTools.HOSTNAME, "owner"), required);
	}

	@Test
	void testInputSchemaAdvertisesPatternAndEnum() {
		final ToolDefinition definition = new ToolDefinition(
			"kill",
			"Kills a process.",
			Map.of(
				"pid",
				new ToolParameter("string", "The process", true, null, "\\d+", null),
				"signal",
				new ToolParameter("string", "The signal", false, "TERM", null, List.of("TERM", "KILL"))
			),
			true
		);

		final JsonNode properties = HostTools.inputSchema(definition).get("properties");

		assertEquals("^(?:\\d+)$", properties.get("pid").get("pattern").asText());
		assertEquals("[\"TERM\",\"KILL\"]", properties.get("signal").get("enum").toString());
		assertTrue(properties.get("signal").get("pattern") == null);
	}

	@Test
	void testPerHostRejectsAnInvalidArgumentBeforeRunning() throws Exception {
		final ToolDefinition definition = new ToolDefinition(
			"ping",
			"Pings a target.",
			Map.of("count", new ToolParameter("integer", "How many pings", true, null)),
			false
		);
		final ToolCallback callback = HostTools.perHost("Test_ping", definition, (hostname, arguments) -> {
			throw new IllegalStateException("Must not run");
		});

		final JsonNode result = MAPPER.readTree(callback.call("{\"hostname\":[\"a\"],\"count\":\"4; reboot\"}"));

		assertEquals("The argument count must be of type integer", result.get("errorMessage").asText());
	}

	@Test
	void testDescriptionFlagsDestructiveTools() {
		assertEquals("Lists the things.", HostTools.description(definition(false)));
		assertEquals("DESTRUCTIVE: Lists the things.", HostTools.description(definition(true)));
	}

	@Test
	void testPerHostRunsOncePerHostWithResolvedArguments() throws Exception {
		final ToolCallback callback = HostTools.perHost("Test_listThings", definition(false), (hostname, arguments) ->
			Map.of("value", hostname + ":" + arguments.get("owner") + ":" + arguments.get("pattern"))
		);

		assertEquals("Test_listThings", callback.getToolDefinition().name());

		final JsonNode result = MAPPER.readTree(callback.call("{\"hostname\":[\"a\",\"b\"],\"owner\":\"me\"}"));

		assertTrue(result.get("errorMessage").isNull());
		final JsonNode hosts = result.get("hosts");
		assertEquals(2, hosts.size());
		assertEquals("a", hosts.get(0).get("hostname").asText());
		// The default of pattern was applied
		assertEquals("a:me:.*", hosts.get(0).get("response").get("value").asText());
		assertEquals("b:me:.*", hosts.get(1).get("response").get("value").asText());
	}

	@Test
	void testPerHostAcceptsASingleHostname() throws Exception {
		final ToolCallback callback = HostTools.perHost("Test_listThings", definition(false), (hostname, arguments) ->
			Map.of("value", hostname)
		);

		final JsonNode result = MAPPER.readTree(callback.call("{\"hostname\":\"a\",\"owner\":\"me\",\"poolSize\":2}"));

		assertEquals(1, result.get("hosts").size());
		assertEquals("a", result.get("hosts").get(0).get("response").get("value").asText());
	}

	@Test
	void testPerHostReportsMissingHostnameAndArguments() throws Exception {
		final ToolCallback callback = HostTools.perHost("Test_listThings", definition(false), (hostname, arguments) ->
			Map.of()
		);

		final JsonNode noHost = MAPPER.readTree(callback.call("{\"owner\":\"me\"}"));
		assertEquals("The hostname parameter is required.", noHost.get("errorMessage").asText());

		final JsonNode noOwner = MAPPER.readTree(callback.call("{\"hostname\":[\"a\"]}"));
		assertTrue(noOwner.get("errorMessage").asText().contains("owner"), noOwner.toString());
	}

	@Test
	void testPerHostTurnsAFailureIntoAnError() throws Exception {
		final ToolCallback callback = HostTools.perHost("Test_listThings", definition(false), (hostname, arguments) -> {
			throw new IllegalStateException("BMC unreachable");
		});

		final JsonNode result = MAPPER.readTree(callback.call("{\"hostname\":[\"a\"],\"owner\":\"me\"}"));

		assertEquals(
			"An error has occurred when running the tool: BMC unreachable",
			result.get("hosts").get(0).get("response").get("error").asText()
		);
	}
}
