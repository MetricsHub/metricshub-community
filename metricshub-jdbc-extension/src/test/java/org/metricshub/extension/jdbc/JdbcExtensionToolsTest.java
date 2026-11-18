package org.metricshub.extension.jdbc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.metricshub.engine.tool.ToolDefinition;
import org.metricshub.engine.tool.ToolTable;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class JdbcExtensionToolsTest {

	@Mock
	private SqlRequestExecutor sqlRequestExecutorMock;

	@InjectMocks
	private JdbcExtension jdbcExtension;

	@Test
	void testGetTools() {
		final List<ToolDefinition> tools = jdbcExtension.getTools();
		assertEquals(List.of("query"), tools.stream().map(ToolDefinition::name).toList());
		assertTrue(tools.get(0).destructive());
		assertTrue(tools.get(0).parameters().get("query").required());
	}

	@Test
	void testExecuteToolQueryIsATableOfTheSelectedColumns() throws Exception {
		// The driver comes from the URL: nothing is connected, the executor is a mock
		final JdbcConfiguration configuration = JdbcConfiguration.builder().url("jdbc:h2:mem:tools".toCharArray()).build();
		configuration.setHostname("db-01");
		final String query = "SELECT name, state FROM services";
		when(sqlRequestExecutorMock.executeSql(eq("db-01"), eq(configuration), eq(query), eq(false), isNull(), any()))
			.thenReturn(List.of(List.of("spooler", "running")));

		assertEquals(
			new ToolTable(List.of("name", "state"), List.of(List.of("spooler", "running"))),
			jdbcExtension.executeTool("query", configuration, JsonNodeFactory.instance.objectNode().put("query", query))
		);
	}

	@Test
	void testExecuteToolNeedsADriver() {
		final JdbcConfiguration configuration = JdbcConfiguration.builder().build();
		configuration.setHostname("db-01");

		final IllegalArgumentException exception = assertThrows(
			IllegalArgumentException.class,
			() -> jdbcExtension.executeTool(
				"query",
				configuration,
				JsonNodeFactory.instance.objectNode().put("query", "SELECT 1")
			)
		);

		assertTrue(exception.getMessage().contains("db-01"), exception.getMessage());
	}
}
