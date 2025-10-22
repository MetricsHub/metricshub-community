package org.metricshub.web.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.metricshub.agent.context.AgentContext;
import org.metricshub.engine.configuration.HostConfiguration;
import org.metricshub.engine.extension.ExtensionManager;
import org.metricshub.engine.telemetry.TelemetryManager;
import org.metricshub.extension.http.HttpConfiguration;
import org.metricshub.extension.http.HttpExtension;
import org.metricshub.extension.http.HttpRequestExecutor;
import org.metricshub.extension.http.utils.HttpRequest;
import org.metricshub.web.AgentContextHolder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ExecuteHttpGetQueryServiceTest {

	/**
	 * Hostname against which the HTTP request is executed.
	 */
	private static final String HOSTNAME = "hostname";

	/**
	 * Target HTTP URL used in tests.
	 */
	private static final String HTTP_URL = "https://hostname:443/redfish/v1";

	/**
	 *  HTTP headers used for the request (newline-separated "key: value" pairs).
	 */
	private static final String HTTP_HEADER =
		"""
		Accept: Application/json
		Authentication: Bearer q4sdf4sdf92qsd4wxc5
		User-Agent: MetricsHub/1.0
		""";

	/**
	 * HTTP request body payload used in tests.
	 */
	private static final String HTTP_BODY = "{\"message\" \"hello\"}";

	/**
	 * A Timeout for the query execution.
	 */
	private static final Long TIMEOUT = 10L;

	/**
	 * Mocked successful provider response.
	 */
	private static final String SUCCESS_RESPONSE = "Success";

	private AgentContextHolder agentContextHolder;
	private AgentContext agentContext;
	private ExtensionManager extensionManager;
	private ExecuteHttpGetQueryService httpQueryService;

	@InjectMocks
	private HttpExtension httpExtension;

	@Mock
	private HttpRequestExecutor httpRequestExecutorMock;

	void setup() {
		// Mocking the agent context holder
		agentContextHolder = mock(AgentContextHolder.class);

		// Mocking the agent context
		agentContext = mock(AgentContext.class);

		// Creating an extension manager with the HTTP Extension
		extensionManager = ExtensionManager.builder().withProtocolExtensions(List.of(httpExtension)).build();

		// Mocking agent context and agent context holder
		when(agentContextHolder.getAgentContext()).thenReturn(agentContext);
		when(agentContext.getExtensionManager()).thenReturn(extensionManager);

		// Injecting the agent context holder in the HTTP query service.
		httpQueryService = new ExecuteHttpGetQueryService(agentContextHolder);
	}

	@Test
	void testExecuteHttpGetRequestWithoutConfiguration() {
		setup();
		// creating a host configuration without configuration
		HostConfiguration hostConfiguration = HostConfiguration
			.builder()
			.hostname(HOSTNAME)
			.configurations(Map.of())
			.build();

		// Creating a telemetry manager with a host configuration
		TelemetryManager telemetryManager = TelemetryManager.builder().hostConfiguration(hostConfiguration).build();

		// Mocking agent context
		when(agentContext.getTelemetryManagers()).thenReturn(Map.of("Paris", Map.of(HOSTNAME, telemetryManager)));

		// Calling execute query
		final QueryResponse result = httpQueryService.executeQuery(HOSTNAME, HTTP_URL, HTTP_HEADER, HTTP_BODY, TIMEOUT);

		assertNotNull(result, "Result should not be null when executing a query");

		assertEquals(
			"No valid configuration found for HTTP on %s.".formatted(HOSTNAME),
			result.getIsError(),
			() -> "Unexpected error message when host has no configurations. "
		);
	}

	@Test
	void testExecuteHttpGetRequestWithoutExtension() {
		setup();
		// Removing the extension to test
		extensionManager.setProtocolExtensions(List.of());

		// Calling execute query
		final QueryResponse result = httpQueryService.executeQuery(HOSTNAME, HTTP_URL, HTTP_HEADER, HTTP_BODY, TIMEOUT);

		assertNull(result.getResponse(), () -> "Response shouldn't be null.");
		assertEquals(
			"No Extension found for HTTP.",
			result.getIsError(),
			() -> "Unexpected error message when the HTTP extension isn't found"
		);
	}

	@Test
	void testExecuteHttpGetRequest() throws Exception {
		setup();
		// Creating a HTTP Configuration for the host
		HttpConfiguration httpConfiguration = HttpConfiguration
			.builder()
			.hostname(HOSTNAME)
			.username("username")
			.password("password".toCharArray())
			.build();

		// Creating a host configuration with the HTTP configuration
		HostConfiguration hostConfiguration = HostConfiguration
			.builder()
			.hostname(HOSTNAME)
			.configurations(Map.of(HttpConfiguration.class, httpConfiguration))
			.build();

		// Creating a telemetry manager with a host configuration
		TelemetryManager telemetryManager = TelemetryManager.builder().hostConfiguration(hostConfiguration).build();

		// Mocking agent context
		when(agentContext.getTelemetryManagers()).thenReturn(Map.of("Paris", Map.of(HOSTNAME, telemetryManager)));

		// Mocking executeHttp query on HTTP request executor
		when(httpRequestExecutorMock.executeHttp(any(HttpRequest.class), anyBoolean(), any(TelemetryManager.class)))
			.thenReturn(SUCCESS_RESPONSE);

		final QueryResponse result = httpQueryService.executeQuery(HOSTNAME, HTTP_URL, HTTP_HEADER, HTTP_BODY, TIMEOUT);

		assertEquals(SUCCESS_RESPONSE, result.getResponse(), () -> "HTTP GET response mismatch for mocked value `Success`");
	}

	@Test
	void testExecuteHttpGetResponseExtensionThrows() throws Exception {
		// Setup the mocks
		setup();

		// Creating a HTTP Configuration for the host
		HttpConfiguration httpConfiguration = HttpConfiguration
			.builder()
			.hostname(HOSTNAME)
			.username("username")
			.password("password".toCharArray())
			.build();

		// Creating a host configuration with the HTTP configuration
		HostConfiguration hostConfiguration = HostConfiguration
			.builder()
			.hostname(HOSTNAME)
			.configurations(Map.of(HttpConfiguration.class, httpConfiguration))
			.build();

		// Creating a telemetry manager with a host configuration
		TelemetryManager telemetryManager = TelemetryManager.builder().hostConfiguration(hostConfiguration).build();

		// Mocking agent context
		when(agentContext.getTelemetryManagers()).thenReturn(Map.of("Paris", Map.of(HOSTNAME, telemetryManager)));

		// Make the executor throw when the extension tries to execute
		when(httpRequestExecutorMock.executeHttp(any(HttpRequest.class), anyBoolean(), any(TelemetryManager.class)))
			.thenThrow(new IllegalArgumentException("An error has occurred"));

		// Call the execute query method
		final QueryResponse result = httpQueryService.executeQuery(HOSTNAME, HTTP_URL, HTTP_HEADER, HTTP_BODY, TIMEOUT);

		// Assertions
		assertNotNull(result.getIsError(), () -> "Error message should be returned when an exception is throws");
		assertTrue(
			result.getIsError().contains("An error has occurred when executing the HTTP"),
			() -> "Error message should contain 'An error has occurred'"
		);
	}

	@Test
	void testCreateQueryNode() {
		// Minimal input → defaults applied (method defaults to GET, resultContent defaults)
		JsonNode queryNode = ExecuteHttpGetQueryService.createQueryNode(null, HTTP_URL, null, null);
		assertEquals("GET", queryNode.get("method").asText(), () -> "method should default to GET when null");
		assertEquals(HTTP_URL, queryNode.get("url").asText(), () -> "url must match");
		assertNull(queryNode.get("header"), () -> "header field should be absent when null");
		assertNull(queryNode.get("body"), () -> "body field should be absent when null");
		assertEquals(
			"all_with_status",
			queryNode.get("resultContent").asText(),
			() -> "resultContent should default to all_with_status"
		);

		// Full input → all fields present and respected (method uppercased)
		queryNode = ExecuteHttpGetQueryService.createQueryNode("get", HTTP_URL, HTTP_HEADER, HTTP_BODY);
		assertEquals("GET", queryNode.get("method").asText(), () -> "method should be uppercased");
		assertEquals(HTTP_URL, queryNode.get("url").asText(), () -> "url must match");
		assertEquals(HTTP_HEADER, queryNode.get("header").asText(), () -> "header should match input string");
		assertEquals(HTTP_BODY, queryNode.get("body").asText(), () -> "body should match input string");
		assertEquals(
			"all_with_status",
			queryNode.get("resultContent").asText(),
			() -> "resultContent should default to all_with_status"
		);
	}
}
