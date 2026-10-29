package org.metricshub.web.mcp.tool;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.modelcontextprotocol.server.McpSyncServer;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.ai.mcp.McpToolUtils;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.definition.ToolDefinition;

class McpToolRegistrySyncTest {

	private static ToolCallback callback(final String name, final String description) {
		return new DynamicToolCallback(
			ToolDefinition.builder().name(name).description(description).inputSchema("{\"type\":\"object\"}").build(),
			arguments -> Map.of()
		);
	}

	@Test
	void testRefreshAddsRemovesAndReplaces() {
		final ToolCallback kept = callback("ListHosts", "Lists the hosts.");
		final ToolCallback changed = callback("Windows_listProcesses", "Lists the processes, now with I/O.");
		final ToolCallback added = callback("ipmi_sel", "Reads the SEL.");
		final ToolCallbackProvider provider = mock(ToolCallbackProvider.class);
		when(provider.getToolCallbacks()).thenReturn(new ToolCallback[] { kept, changed, added });

		final McpSyncServer server = mock(McpSyncServer.class);
		when(server.listTools()).thenReturn(
			List.of(
				McpToolUtils.toSyncToolSpecification(kept).tool(),
				McpToolUtils.toSyncToolSpecification(callback("Windows_listProcesses", "Lists the processes.")).tool(),
				McpToolUtils.toSyncToolSpecification(callback("Gone_tool", "Was removed by the reload.")).tool()
			)
		);

		new McpToolRegistrySync(provider, List.of(server)).refresh();

		verify(server).removeTool("Gone_tool");
		verify(server).removeTool("Windows_listProcesses");
		verify(server, never()).removeTool("ListHosts");
		verify(server).addTool(argThat(specification -> "Windows_listProcesses".equals(specification.tool().name())));
		verify(server).addTool(argThat(specification -> "ipmi_sel".equals(specification.tool().name())));
		verify(server, never()).addTool(argThat(specification -> "ListHosts".equals(specification.tool().name())));
	}

	@Test
	void testAFailingServerDoesNotStopTheOthers() {
		final ToolCallbackProvider provider = mock(ToolCallbackProvider.class);
		when(provider.getToolCallbacks()).thenReturn(new ToolCallback[] { callback("ListHosts", "Lists the hosts.") });

		final McpSyncServer failing = mock(McpSyncServer.class);
		when(failing.listTools()).thenThrow(new IllegalStateException("closed"));
		final McpSyncServer healthy = mock(McpSyncServer.class);
		when(healthy.listTools()).thenReturn(List.of());

		new McpToolRegistrySync(provider, List.of(failing, healthy)).refresh();

		verify(healthy).addTool(any());
	}
}
