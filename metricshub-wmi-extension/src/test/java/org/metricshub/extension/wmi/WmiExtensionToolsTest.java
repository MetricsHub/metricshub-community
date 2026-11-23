package org.metricshub.extension.wmi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.metricshub.engine.tool.ToolDefinition;
import org.metricshub.engine.tool.ToolTable;
import org.metricshub.engine.tool.ToolText;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WmiExtensionToolsTest {

	@Mock
	private WmiRequestExecutor wmiRequestExecutorMock;

	@InjectMocks
	private WmiExtension wmiExtension;

	private static WmiConfiguration configuration() {
		final WmiConfiguration configuration = WmiConfiguration.builder().build();
		configuration.setHostname("host");
		configuration.setNamespace("root\\cimv2");
		return configuration;
	}

	@Test
	void testGetTools() {
		final List<ToolDefinition> tools = wmiExtension.getTools();
		assertEquals(List.of("wql", "runCommand"), tools.stream().map(ToolDefinition::name).toList());
		assertFalse(tools.get(0).destructive());
		assertTrue(tools.get(0).parameters().get("query").required());
		assertTrue(tools.get(1).destructive());
	}

	@Test
	void testExecuteToolWqlUsesTheGivenNamespace() throws Exception {
		final WmiConfiguration configuration = configuration();
		final String query = "SELECT Name, State FROM Win32_Service";
		// The names of the columns come from the interface's default method: run it, the mock only serves the rows
		when(wmiRequestExecutorMock.executeWqlQuery("host", configuration, query, "root\\default")).thenCallRealMethod();
		when(wmiRequestExecutorMock.executeWmi("host", configuration, query, "root\\default", null, null)).thenReturn(
			List.of(List.of("Spooler", "Running"))
		);

		assertEquals(
			new ToolTable(List.of("Name", "State"), List.of(List.of("Spooler", "Running"))),
			wmiExtension.executeTool(
				"wql",
				configuration,
				JsonNodeFactory.instance.objectNode().put("query", query).put("namespace", "root\\default")
			)
		);
		assertEquals("root\\default", configuration.getNamespace());
	}

	@Test
	void testExecuteToolRunCommand() throws Exception {
		final WmiConfiguration configuration = configuration();
		when(wmiRequestExecutorMock.executeWinRemoteCommand("host", configuration, "hostname", null, null)).thenReturn(
			"WIN01"
		);

		assertEquals(
			new ToolText("WIN01"),
			wmiExtension.executeTool(
				"runCommand",
				configuration,
				JsonNodeFactory.instance.objectNode().put("commandLine", "hostname")
			)
		);
	}

	@Test
	void testExecuteToolRejectsUnknownTool() {
		assertThrows(
			IllegalArgumentException.class,
			() -> wmiExtension.executeTool("nope", configuration(), JsonNodeFactory.instance.objectNode())
		);
	}
}
