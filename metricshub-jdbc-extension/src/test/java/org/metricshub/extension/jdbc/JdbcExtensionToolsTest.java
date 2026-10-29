package org.metricshub.extension.jdbc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.metricshub.engine.tool.ToolDefinition;

class JdbcExtensionToolsTest {

	@Test
	void testGetTools() {
		final List<ToolDefinition> tools = new JdbcExtension().getTools();
		assertEquals(List.of("query"), tools.stream().map(ToolDefinition::name).toList());
		assertTrue(tools.get(0).destructive());
		assertTrue(tools.get(0).parameters().get("query").required());
	}

	@Test
	void testExecuteToolNeedsADriver() {
		final JdbcConfiguration configuration = JdbcConfiguration.builder().build();
		configuration.setHostname("db-01");

		final IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
			new JdbcExtension().executeTool(
				"query",
				configuration,
				JsonNodeFactory.instance.objectNode().put("query", "SELECT 1")
			)
		);

		assertTrue(exception.getMessage().contains("db-01"), exception.getMessage());
	}
}
