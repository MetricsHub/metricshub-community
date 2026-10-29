package org.metricshub.extension.wbem;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
import org.metricshub.engine.telemetry.TelemetryManager;
import org.metricshub.engine.tool.ToolDefinition;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WbemExtensionToolsTest {

	@Mock
	private WbemRequestExecutor wbemRequestExecutorMock;

	@InjectMocks
	private WbemExtension wbemExtension;

	@Test
	void testGetTools() {
		final List<ToolDefinition> tools = wbemExtension.getTools();
		assertEquals(List.of("wql"), tools.stream().map(ToolDefinition::name).toList());
		assertFalse(tools.get(0).destructive());
	}

	@Test
	void testExecuteToolWqlUsesTheGivenNamespace() throws Exception {
		final WbemConfiguration configuration = WbemConfiguration.builder().build();
		configuration.setHostname("host");
		configuration.setNamespace("root/cimv2");
		when(
			wbemRequestExecutorMock.executeWbem(
				eq("host"),
				eq(configuration),
				eq("SELECT Name FROM EMC_StorageSystem"),
				eq("root/emc"),
				any(TelemetryManager.class),
				isNull()
			)
		).thenReturn(List.of(List.of("array1")));

		final String result = wbemExtension.executeTool(
			"wql",
			configuration,
			JsonNodeFactory.instance
				.objectNode()
				.put("query", "SELECT Name FROM EMC_StorageSystem")
				.put("namespace", "root/emc")
		);

		assertTrue(result.contains("array1"), result);
		assertEquals("root/emc", configuration.getNamespace());
	}

	@Test
	void testExecuteToolRejectsUnknownTool() {
		assertThrows(IllegalArgumentException.class, () ->
			wbemExtension.executeTool("nope", WbemConfiguration.builder().build(), JsonNodeFactory.instance.objectNode())
		);
	}
}
