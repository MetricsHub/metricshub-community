package org.metricshub.extension.jmx;

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
class JmxExtensionToolsTest {

	@Mock
	private JmxRequestExecutor jmxRequestExecutorMock;

	@InjectMocks
	private JmxExtension jmxExtension;

	@Test
	void testGetTools() {
		final List<ToolDefinition> tools = jmxExtension.getTools();
		assertEquals(List.of("fetchMBean"), tools.stream().map(ToolDefinition::name).toList());
		assertTrue(tools.get(0).parameters().get("objectName").required());
	}

	@Test
	void testExecuteToolSplitsTheLists() throws Exception {
		final JmxConfiguration configuration = JmxConfiguration.builder().hostname("localhost").port(1099).build();
		when(
			jmxRequestExecutorMock.fetchMBean(
				configuration,
				"java.lang:type=MemoryPool,name=*",
				List.of("Usage", "PeakUsage"),
				List.of("name"),
				null,
				null
			)
		).thenReturn(List.of(List.of("Metaspace", "10", "12")));

		final String result = jmxExtension.executeTool(
			"fetchMBean",
			configuration,
			JsonNodeFactory.instance
				.objectNode()
				.put("objectName", "java.lang:type=MemoryPool,name=*")
				.put("attributes", "Usage, PeakUsage")
				.put("keyProperties", "name")
		);

		assertTrue(result.contains("Metaspace"), result);
	}

	@Test
	void testExecuteToolRejectsUnknownTool() {
		assertThrows(IllegalArgumentException.class, () ->
			jmxExtension.executeTool(
				"nope",
				JmxConfiguration.builder().hostname("localhost").port(1099).build(),
				JsonNodeFactory.instance.objectNode()
			)
		);
	}
}
