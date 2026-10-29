package org.metricshub.extension.winrm;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
class WinRmExtensionToolsTest {

	@Mock
	private WinRmRequestExecutor winRmRequestExecutorMock;

	@InjectMocks
	private WinRmExtension winRmExtension;

	private static WinRmConfiguration configuration() {
		final WinRmConfiguration configuration = WinRmConfiguration.builder().build();
		configuration.setHostname("host");
		configuration.setNamespace("root\\cimv2");
		return configuration;
	}

	@Test
	void testGetTools() {
		final List<ToolDefinition> tools = winRmExtension.getTools();
		assertEquals(
			List.of("wql", "runCommand", "readFile", "listFiles"),
			tools.stream().map(ToolDefinition::name).toList()
		);
		assertEquals(
			List.of("runCommand"),
			tools.stream().filter(ToolDefinition::destructive).map(ToolDefinition::name).toList()
		);
	}

	@Test
	void testExecuteToolWqlUsesTheGivenNamespace() throws Exception {
		final WinRmConfiguration configuration = configuration();
		when(
			winRmRequestExecutorMock.executeWqlQuery("host", configuration, "SELECT Name FROM Win32_Service", "root\\default")
		).thenReturn("Name\nSpooler");

		assertEquals(
			"Name\nSpooler",
			winRmExtension.executeTool(
				"wql",
				configuration,
				JsonNodeFactory.instance
					.objectNode()
					.put("query", "SELECT Name FROM Win32_Service")
					.put("namespace", "root\\default")
			)
		);
		assertEquals("root\\default", configuration.getNamespace());
	}

	@Test
	void testExecuteToolReadFileAndListFiles() throws Exception {
		final WinRmConfiguration configuration = configuration();
		when(winRmRequestExecutorMock.readRemoteFile("host", configuration, "C:\\hosts")).thenReturn("127.0.0.1 localhost");
		when(winRmRequestExecutorMock.listRemoteFiles("host", configuration, "C:\\logs\\*.log")).thenReturn(
			"C:\\logs\\a.log"
		);

		assertEquals(
			"127.0.0.1 localhost",
			winRmExtension.executeTool(
				"readFile",
				configuration,
				JsonNodeFactory.instance.objectNode().put("path", "C:\\hosts")
			)
		);
		assertEquals(
			"C:\\logs\\a.log",
			winRmExtension.executeTool(
				"listFiles",
				configuration,
				JsonNodeFactory.instance.objectNode().put("pattern", "C:\\logs\\*.log")
			)
		);
	}

	@Test
	void testExecuteToolRunCommand() throws Exception {
		final WinRmConfiguration configuration = configuration();
		when(winRmRequestExecutorMock.executeWinRemoteCommand("host", configuration, "hostname", null)).thenReturn("WIN01");

		assertEquals(
			"WIN01",
			winRmExtension.executeTool(
				"runCommand",
				configuration,
				JsonNodeFactory.instance.objectNode().put("commandLine", "hostname")
			)
		);
		assertTrue(winRmExtension.getTools().get(1).destructive());
	}

	@Test
	void testExecuteToolRejectsUnknownTool() {
		assertThrows(IllegalArgumentException.class, () ->
			winRmExtension.executeTool("nope", configuration(), JsonNodeFactory.instance.objectNode())
		);
	}
}
