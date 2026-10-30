package org.metricshub.extension.oscommand;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.metricshub.engine.tool.ToolDefinition;

class OsCommandExtensionToolsTest {

	@Test
	void testGetTools() {
		final List<ToolDefinition> tools = new OsCommandExtension(new OsCommandService()).getTools();
		assertEquals(List.of("runCommand"), tools.stream().map(ToolDefinition::name).toList());
		assertTrue(tools.get(0).destructive());
		assertTrue(tools.get(0).parameters().get("commandLine").required());
	}

	@Test
	void testExecuteToolRunsTheCommandOverSsh() throws Exception {
		final OsCommandService osCommandService = mock(OsCommandService.class);
		final SshConfiguration configuration = SshConfiguration.sshConfigurationBuilder().timeout(30L).build();
		configuration.setHostname("host");
		when(osCommandService.runSshCommand("df -h", "host", configuration, 30L, null, "df -h", null)).thenReturn(
			"Filesystem Size"
		);

		assertEquals(
			"Filesystem Size",
			new OsCommandExtension(osCommandService).executeTool(
				"runCommand",
				configuration,
				JsonNodeFactory.instance.objectNode().put("commandLine", "df -h")
			)
		);
	}

	@Test
	void testToolsRunOverSshOnly() {
		final OsCommandExtension extension = new OsCommandExtension(new OsCommandService());
		assertTrue(extension.isToolConfiguration(SshConfiguration.sshConfigurationBuilder().build()));
		assertFalse(extension.isToolConfiguration(OsCommandConfiguration.builder().build()));
	}
}
