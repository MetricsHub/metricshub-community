package org.metricshub.extension.jdbc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
}
