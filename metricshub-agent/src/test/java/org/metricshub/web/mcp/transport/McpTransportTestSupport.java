package org.metricshub.web.mcp.transport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpClientTransport;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.IntSupplier;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * Shared fixtures for the MCP transport integration tests: a minimal Spring Boot web application exposing one tool
 * through the Spring AI MCP auto-configuration and {@link LegacySseMcpServerConfiguration}, plus client-side
 * assertions.
 */
final class McpTransportTestSupport {

	static final String ECHO_TOOL = "echo";

	private McpTransportTestSupport() {}

	/**
	 * Minimal web application: Spring Boot auto-configuration without security, the MCP server auto-configuration and
	 * the legacy SSE configuration under test.
	 */
	@Configuration
	@EnableAutoConfiguration(
		exclude = {
			SecurityAutoConfiguration.class, SecurityFilterAutoConfiguration.class, UserDetailsServiceAutoConfiguration.class
		}
	)
	@Import(LegacySseMcpServerConfiguration.class)
	static class TestApplication {

		@Bean
		ToolCallbackProvider testTools() {
			return MethodToolCallbackProvider.builder().toolObjects(new EchoTool()).build();
		}
	}

	static class EchoTool {

		@Tool(name = ECHO_TOOL, description = "Echoes the given text")
		public String echo(@ToolParam(description = "The text to echo") final String text) {
			return "echo:" + text;
		}
	}

	/**
	 * Initializes an MCP client over the given transport, lists the tools and calls the echo tool.
	 *
	 * @param transport the client transport under test
	 */
	static void assertToolsAreServed(final McpClientTransport transport) {
		try (McpSyncClient client = McpClient.sync(transport).requestTimeout(Duration.ofSeconds(30)).build()) {
			client.initialize();

			// McpSchema.Tool collides with Spring AI's @Tool annotation, hence the qualified name
			final McpSchema.Tool echoTool = client
				.listTools()
				.tools()
				.stream()
				.filter(tool -> ECHO_TOOL.equals(tool.name()))
				.findFirst()
				.orElseThrow(() -> new AssertionError("The echo tool must be listed"));

			// The argument name comes from the advertised schema: it depends on whether the test classes were
			// compiled with parameter names
			final String argumentName = echoTool.inputSchema().properties().keySet().iterator().next();
			final CallToolResult result = client.callTool(new CallToolRequest(ECHO_TOOL, Map.of(argumentName, "hello")));
			assertFalse(Boolean.TRUE.equals(result.isError()), "The tool call must succeed: " + result);
			assertTrue(
				result
					.content()
					.stream()
					.filter(TextContent.class::isInstance)
					.map(TextContent.class::cast)
					.anyMatch(content -> content.text().contains("echo:hello")),
				"Unexpected tool result: " + result
			);
		}
	}

	/**
	 * Initializes an MCP client, lets several keep-alive pings go through the real client, and checks that the session
	 * is still there and usable afterwards (the client answers the pings, so the server must keep the session).
	 *
	 * @param transport          the SSE client transport
	 * @param activeSessionCount supplier of the server-side session count
	 * @throws InterruptedException when interrupted while waiting for the pings
	 */
	static void assertSessionSurvivesKeepAlivePings(
		final McpClientTransport transport,
		final IntSupplier activeSessionCount
	) throws InterruptedException {
		try (McpSyncClient client = McpClient.sync(transport).requestTimeout(Duration.ofSeconds(30)).build()) {
			client.initialize();
			final int before = activeSessionCount.getAsInt();
			assertTrue(before >= 1, "The session must be tracked");

			// Several 200 ms keep-alive intervals
			Thread.sleep(1_500);

			assertEquals(before, activeSessionCount.getAsInt(), "A client answering the pings must keep its session");
			assertTrue(
				client
					.listTools()
					.tools()
					.stream()
					.anyMatch(tool -> ECHO_TOOL.equals(tool.name()))
			);
		}
	}

	/**
	 * Opens an SSE stream, reads the endpoint event and posts a request without initializing the session, as an SSE
	 * client that reconnected its event stream does. The SDK transport would block forever; the hardened transport must
	 * answer 400 immediately.
	 *
	 * @param baseUrl the base URL of the server
	 * @throws Exception when the HTTP exchanges fail
	 */
	static void assertUninitializedSessionIsRejected(final String baseUrl) throws Exception {
		final HttpClient httpClient = HttpClient.newHttpClient();

		final HttpRequest sseRequest = HttpRequest.newBuilder(URI.create(baseUrl + "/sse"))
			.header("Accept", "text/event-stream")
			.timeout(Duration.ofSeconds(10))
			.GET()
			.build();
		final HttpResponse<InputStream> sseResponse = httpClient.send(
			sseRequest,
			HttpResponse.BodyHandlers.ofInputStream()
		);
		assertEquals(200, sseResponse.statusCode());

		try (
			BufferedReader reader = new BufferedReader(new InputStreamReader(sseResponse.body(), StandardCharsets.UTF_8))
		) {
			String messageEndpoint = null;
			String line;
			while ((line = reader.readLine()) != null) {
				if (line.startsWith("data:")) {
					messageEndpoint = line.substring("data:".length()).trim();
					break;
				}
			}
			assertNotNull(messageEndpoint, "The endpoint event must be received");

			final HttpRequest toolsListRequest = HttpRequest.newBuilder(URI.create(baseUrl + messageEndpoint))
				.header("Content-Type", "application/json")
				.timeout(Duration.ofSeconds(10))
				.POST(HttpRequest.BodyPublishers.ofString("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/list\"}"))
				.build();

			final long start = System.nanoTime();
			final HttpResponse<String> response = httpClient.send(toolsListRequest, HttpResponse.BodyHandlers.ofString());
			final long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);

			assertEquals(400, response.statusCode(), response.body());
			assertTrue(response.body().contains("not initialized"), response.body());
			assertTrue(elapsedMs < 5_000, "The rejection must be immediate, took " + elapsedMs + " ms");
		}
	}

	/**
	 * Posts a JSON-RPC message to a path and returns the HTTP status.
	 *
	 * @param url the URL to post to
	 * @return the HTTP status code
	 * @throws Exception when the HTTP exchange fails
	 */
	static int postJsonRpc(final String url) throws Exception {
		final HttpRequest request = HttpRequest.newBuilder(URI.create(url))
			.header("Content-Type", "application/json")
			.header("Accept", "application/json, text/event-stream")
			.timeout(Duration.ofSeconds(10))
			.POST(HttpRequest.BodyPublishers.ofString("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"ping\"}"))
			.build();
		return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString()).statusCode();
	}
}
