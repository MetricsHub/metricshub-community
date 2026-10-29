package org.metricshub.extension.http;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.metricshub.engine.connector.model.common.ResultContent;
import org.metricshub.engine.telemetry.TelemetryManager;
import org.metricshub.engine.tool.ToolDefinition;
import org.metricshub.extension.http.utils.HttpRequest;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class HttpExtensionToolsTest {

	@Mock
	private HttpRequestExecutor httpRequestExecutorMock;

	@InjectMocks
	private HttpExtension httpExtension;

	@Test
	void testGetTools() {
		final List<ToolDefinition> tools = httpExtension.getTools();
		assertEquals(List.of("get", "request"), tools.stream().map(ToolDefinition::name).toList());
		assertEquals(
			List.of("request"),
			tools.stream().filter(ToolDefinition::destructive).map(ToolDefinition::name).toList()
		);
		assertEquals("/", tools.get(0).parameters().get("path").defaultValue());
		assertFalse(tools.get(0).parameters().containsKey("url"));
	}

	@Test
	void testExecuteToolGetBuildsTheRequestFromThePath() throws Exception {
		final HttpConfiguration configuration = HttpConfiguration.builder().hostname("host").build();
		final ArgumentCaptor<HttpRequest> request = ArgumentCaptor.forClass(HttpRequest.class);
		when(httpRequestExecutorMock.executeHttp(request.capture(), eq(false), any(TelemetryManager.class))).thenReturn(
			"{\"Members\":[]}"
		);

		final String result = httpExtension.executeTool(
			"get",
			configuration,
			JsonNodeFactory.instance.objectNode().put("path", "/redfish/v1/Systems")
		);

		assertEquals("{\"Members\":[]}", result);
		assertEquals("GET", request.getValue().getMethod());
		assertEquals("/redfish/v1/Systems", request.getValue().getPath());
		assertEquals(ResultContent.BODY, request.getValue().getResultContent());
	}

	@Test
	void testExecuteToolRequestUsesTheGivenMethod() throws Exception {
		final HttpConfiguration configuration = HttpConfiguration.builder().hostname("host").build();
		final ArgumentCaptor<HttpRequest> request = ArgumentCaptor.forClass(HttpRequest.class);
		when(httpRequestExecutorMock.executeHttp(request.capture(), eq(false), any(TelemetryManager.class))).thenReturn(
			"200"
		);

		httpExtension.executeTool(
			"request",
			configuration,
			JsonNodeFactory.instance
				.objectNode()
				.put("method", "POST")
				.put("path", "/redfish/v1/Systems/1/Actions/ComputerSystem.Reset")
				.put("body", "{\"ResetType\":\"GracefulRestart\"}")
				.put("resultContent", "http_status")
		);

		assertEquals("POST", request.getValue().getMethod());
		assertEquals(ResultContent.HTTP_STATUS, request.getValue().getResultContent());
	}

	@Test
	void testExecuteToolRequestRejectsAMethodOutsideTheAllowlist() {
		final HttpConfiguration configuration = HttpConfiguration.builder().hostname("host").build();

		final IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
			httpExtension.executeTool(
				"request",
				configuration,
				JsonNodeFactory.instance.objectNode().put("method", "TRACE").put("path", "/")
			)
		);

		assertTrue(exception.getMessage().contains("TRACE"), exception.getMessage());
	}

	@Test
	void testExecuteToolRejectsUnknownTool() {
		assertThrows(IllegalArgumentException.class, () ->
			httpExtension.executeTool(
				"nope",
				HttpConfiguration.builder().hostname("host").build(),
				JsonNodeFactory.instance.objectNode()
			)
		);
	}
}
