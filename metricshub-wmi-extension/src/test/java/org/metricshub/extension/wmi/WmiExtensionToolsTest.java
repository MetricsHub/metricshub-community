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
		when(
			wmiRequestExecutorMock.executeWqlQuery("host", configuration, "SELECT Name FROM Win32_Service", "root\\default")
		).thenReturn("Name\nSpooler");

		final String result = wmiExtension.executeTool(
			"wql",
			configuration,
			JsonNodeFactory.instance
				.objectNode()
				.put("query", "SELECT Name FROM Win32_Service")
				.put("namespace", "root\\default")
		);

		assertEquals("Name\nSpooler", result);
		assertEquals("root\\default", configuration.getNamespace());
	}

	@Test
	void testExecuteToolRunCommand() throws Exception {
		final WmiConfiguration configuration = configuration();
		when(wmiRequestExecutorMock.executeWinRemoteCommand("host", configuration, "hostname", null)).thenReturn("WIN01");

		assertEquals(
			"WIN01",
			wmiExtension.executeTool(
				"runCommand",
				configuration,
				JsonNodeFactory.instance.objectNode().put("commandLine", "hostname")
			)
		);
	}

	@Test
	void testExecuteToolRejectsUnknownTool() {
		assertThrows(IllegalArgumentException.class, () ->
			wmiExtension.executeTool("nope", configuration(), JsonNodeFactory.instance.objectNode())
		);
	}
}
