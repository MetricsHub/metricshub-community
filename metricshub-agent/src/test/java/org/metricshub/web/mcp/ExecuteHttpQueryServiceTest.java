package org.metricshub.web.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
class ExecuteHttpQueryServiceTest {

	/**
	 * Hostname against which the HTTP request is executed.
	 */
	private static final String HOSTNAME = "hostname";

	/**
	 * HTTP GET method.
	 */
	private static final String HTTP_GET = "GET";

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
	private ExecuteHttpQueryService httpQueryService;

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
		httpQueryService = new ExecuteHttpQueryService(agentContextHolder);
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
		final MultiHostToolResponse<QueryResponse> result = httpQueryService.executeQuery(
			List.of(HTTP_URL),
			HTTP_GET,
			HTTP_HEADER,
			HTTP_BODY,
			TIMEOUT,
			null
		);

		assertEquals(1, result.getHosts().size(), () -> "Expected a single host response");
		final HostToolResponse<QueryResponse> hostResponse = result.getHosts().get(0);
		assertEquals(HOSTNAME, hostResponse.getHostname(), () -> "Hostname should be propagated");
		assertEquals(
			"No valid configuration found for HTTP on %s.".formatted(HOSTNAME),
			hostResponse.getResponse().getError(),
			() -> "Unexpected error message when host has no configurations. "
		);
	}

	@Test
	void testExecuteHttpGetRequestWithoutExtension() {
		setup();
		// Removing the extension to test
		extensionManager.setProtocolExtensions(List.of());

		// Calling execute query
		final MultiHostToolResponse<QueryResponse> result = httpQueryService.executeQuery(
			List.of(HTTP_URL),
			HTTP_GET,
			HTTP_HEADER,
			HTTP_BODY,
			TIMEOUT,
			null
		);

		assertTrue(result.getHosts().isEmpty(), () -> "No host response should be returned when extension is missing");
		assertEquals(
			"The HTTP extension is not available",
			result.getErrorMessage(),
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

		final MultiHostToolResponse<QueryResponse> result = httpQueryService.executeQuery(
			List.of(HTTP_URL),
			null,
			HTTP_HEADER,
			HTTP_BODY,
			TIMEOUT,
			null
		);

		assertEquals(1, result.getHosts().size(), () -> "Expected a single host response");
		final HostToolResponse<QueryResponse> hostResponse = result.getHosts().get(0);
		assertEquals(HOSTNAME, hostResponse.getHostname(), () -> "Hostname should be propagated");
		assertEquals(
			SUCCESS_RESPONSE,
			hostResponse.getResponse().getResponse(),
			() -> "HTTP GET response mismatch for mocked value `Success`"
		);
	}

	@Test
	void testExecuteHttpPostRequest() throws Exception {
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

		MultiHostToolResponse<QueryResponse> result = httpQueryService.executeQuery(
			List.of(HTTP_URL),
			"post",
			HTTP_HEADER,
			HTTP_BODY,
			TIMEOUT,
			null
		);

		assertEquals(0, result.getHosts().size(), () -> "No host is expected to respond as HTTP POST is disabled.");
		assertEquals(
			"The HTTP POST method is disabled.",
			result.getErrorMessage(),
			() -> "Expected message `HTTP POST disabled`"
		);

		System.setProperty("metricshub.tools.http.post.enabled", "true");

		result = httpQueryService.executeQuery(List.of(HTTP_URL), "post", HTTP_HEADER, HTTP_BODY, TIMEOUT, null);
		assertEquals(1, result.getHosts().size(), () -> "Expected a single host response");
		final HostToolResponse<QueryResponse> hostResponse = result.getHosts().get(0);
		assertEquals(HOSTNAME, hostResponse.getHostname(), () -> "Hostname should be propagated");
		assertEquals(
			SUCCESS_RESPONSE,
			hostResponse.getResponse().getResponse(),
			() -> "HTTP GET response mismatch for mocked value `Success`"
		);
	}

	@Test
	void testExecuteHttpGetRequestWithBlankUrl() {
		setup();

		final MultiHostToolResponse<QueryResponse> result = httpQueryService.executeQuery(
			List.of("   "),
			null,
			null,
			null,
			TIMEOUT,
			null
		);

		assertEquals(1, result.getHosts().size(), () -> "Expected a single host response for blank URL input");
		final HostToolResponse<QueryResponse> hostResponse = result.getHosts().get(0);
		assertEquals(
			"URL must not be blank",
			hostResponse.getResponse().getError(),
			() -> "Expected blank URL error to be returned"
		);
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
		final MultiHostToolResponse<QueryResponse> result = httpQueryService.executeQuery(
			List.of(HTTP_URL),
			HTTP_GET,
			HTTP_HEADER,
			HTTP_BODY,
			TIMEOUT,
			null
		);

		assertEquals(1, result.getHosts().size(), () -> "Expected a single host response");
		final HostToolResponse<QueryResponse> hostResponse = result.getHosts().get(0);
		assertTrue(
			hostResponse.getResponse().getError().contains("An error has occurred when executing the HTTP"),
			() -> "Error message should contain 'An error has occurred'"
		);
	}

	@Test
	void testCreateQueryNode() {
		// Minimal input → defaults applied (method defaults to GET, resultContent defaults)
		JsonNode queryNode = ExecuteHttpQueryService.createQueryNode(HTTP_GET, HTTP_URL, null, null);
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
		queryNode = ExecuteHttpQueryService.createQueryNode(HTTP_GET, HTTP_URL, HTTP_HEADER, HTTP_BODY);
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

	@Test
	void testResolveHttpMethod() {
		assertEquals(
			HTTP_GET,
			ExecuteHttpQueryService.resolveHttpMethod(null),
			() -> "Resolved method should be GET when method is null"
		);
		assertEquals(
			HTTP_GET,
			ExecuteHttpQueryService.resolveHttpMethod(HTTP_GET),
			() -> "Resolved method should be GET when method is GET"
		);
		assertEquals("POST", ExecuteHttpQueryService.resolveHttpMethod("post"), () -> "Resolved method should be POST");
	}

	@Test
	void testIsHttpMethodPermitted() {
		System.clearProperty("metricshub.tools.http.post.enabled");
		assertTrue(ExecuteHttpQueryService.isHttpMethodPermitted(HTTP_GET), () -> "HTTP GET is expected to be permitted");
		assertFalse(
			ExecuteHttpQueryService.isHttpMethodPermitted("POST"),
			() -> "HTTP POST is not expected to be permitted when property isn't enabled"
		);

		System.setProperty("metricshub.tools.http.post.enabled", "true");
		assertTrue(
			ExecuteHttpQueryService.isHttpMethodPermitted("POST"),
			() -> "HTTP POST is expected to be permitted when property is enabled"
		);
	}
}
