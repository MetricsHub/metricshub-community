package org.metricshub.extension.emulation.http;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.metricshub.engine.configuration.HostConfiguration;
import org.metricshub.engine.connector.model.common.DeviceKind;
import org.metricshub.engine.connector.model.common.ResultContent;
import org.metricshub.engine.telemetry.TelemetryManager;
import org.metricshub.extension.emulation.EmulationRoundRobinManager;
import org.metricshub.extension.http.HttpConfiguration;
import org.metricshub.extension.http.utils.HttpRequest;

/**
 * Tests for {@link EmulationHttpRequestExecutor}.
 */
class EmulationHttpRequestExecutorTest {

	private static final String HOSTNAME = "test-host";

	private final EmulationHttpRequestExecutor executor = new EmulationHttpRequestExecutor(
		new EmulationRoundRobinManager()
	);

	/**
	 * Creates a TelemetryManager with the given emulation input directory.
	 */
	private TelemetryManager buildTelemetryManager(final String emulationInputDir) {
		return TelemetryManager
			.builder()
			.hostConfiguration(
				HostConfiguration
					.builder()
					.hostname(HOSTNAME)
					.hostId(HOSTNAME)
					.hostType(DeviceKind.LINUX)
					.configurations(Map.of(HttpConfiguration.class, HttpConfiguration.builder().build()))
					.build()
			)
			.emulationInputDirectory(emulationInputDir)
			.build();
	}

	/**
	 * Creates a basic HttpRequest with the given method and path.
	 */
	private HttpRequest buildHttpRequest(final String method, final String path) {
		return HttpRequest
			.builder()
			.hostname(HOSTNAME)
			.httpConfiguration(HttpConfiguration.builder().build())
			.method(method)
			.path(path)
			.resultContent(ResultContent.BODY)
			.build();
	}

	/**
	 * Writes a standard image.yaml and response files under the given base directory.
	 */
	private void writeStandardImage(final Path baseDir) throws IOException {
		final Path httpDir = baseDir.resolve("http");
		Files.createDirectories(httpDir);

		Files.writeString(
			httpDir.resolve("image.yaml"),
			"""
			image:
			  - request:
			      method: GET
			      path: /api/v1/status
			    response:
			      file: get-status-response.txt
			      resultContent: body
			  - request:
			      method: POST
			      path: /api/v1/data
			      body: '{"action": "query"}'
			      headers:
			        Content-Type: application/json
			        Accept: text/plain
			    response:
			      file: post-data-response.json
			      resultContent: body
			  - request:
			      method: GET
			      path: /api/v1/status
			    response:
			      file: get-status-httpstatus.txt
			      resultContent: httpStatus
			  - request:
			      method: GET
			      path: /api/v1/headers
			    response:
			      file: get-headers-response.txt
			      resultContent: header
			  - request:
			      method: DELETE
			      path: /api/v1/item/42
			    response:
			      file: delete-response.txt
			      resultContent: body
			""",
			StandardCharsets.UTF_8
		);

		Files.writeString(
			httpDir.resolve("get-status-response.txt"),
			"{\"status\":\"OK\",\"version\":\"1.0\"}",
			StandardCharsets.UTF_8
		);
		Files.writeString(
			httpDir.resolve("post-data-response.json"),
			"{\"result\":\"success\",\"data\":[1,2,3]}",
			StandardCharsets.UTF_8
		);
		Files.writeString(httpDir.resolve("get-status-httpstatus.txt"), "200", StandardCharsets.UTF_8);
		Files.writeString(
			httpDir.resolve("get-headers-response.txt"),
			"X-Custom-Header: custom-value",
			StandardCharsets.UTF_8
		);
		Files.writeString(httpDir.resolve("delete-response.txt"), "deleted", StandardCharsets.UTF_8);
	}

	// ---- Null / blank emulation input directory ----

	@Test
	void testExecuteHttpNullEmulationInputDirectory() {
		final TelemetryManager tm = buildTelemetryManager(null);
		final HttpRequest request = buildHttpRequest("GET", "/api/v1/status");

		assertNull(executor.executeHttp(request, false, tm));
	}

	@Test
	void testExecuteHttpBlankEmulationInputDirectory() {
		final TelemetryManager tm = buildTelemetryManager("   ");
		final HttpRequest request = buildHttpRequest("GET", "/api/v1/status");

		assertNull(executor.executeHttp(request, false, tm));
	}

	// ---- Missing index file ----

	@Test
	void testExecuteHttpMissingIndexFile(@TempDir Path tempDir) {
		// Empty temp dir, no http/image.yaml
		final TelemetryManager tm = buildTelemetryManager(tempDir.toString());
		final HttpRequest request = buildHttpRequest("GET", "/api/v1/status");

		assertNull(executor.executeHttp(request, false, tm));
	}

	// ---- Malformed YAML ----

	@Test
	void testExecuteHttpMalformedYaml(@TempDir Path tempDir) throws IOException {
		final Path httpDir = tempDir.resolve("http");
		Files.createDirectories(httpDir);
		Files.writeString(httpDir.resolve("image.yaml"), "this: is: [not: valid: yaml: {{{{", StandardCharsets.UTF_8);

		final TelemetryManager tm = buildTelemetryManager(tempDir.toString());
		final HttpRequest request = buildHttpRequest("GET", "/something");

		assertNull(executor.executeHttp(request, false, tm));
	}

	// ---- Empty image ----

	@Test
	void testExecuteHttpEmptyImage(@TempDir Path tempDir) throws IOException {
		final Path httpDir = tempDir.resolve("http");
		Files.createDirectories(httpDir);
		Files.writeString(httpDir.resolve("image.yaml"), "image: []\n", StandardCharsets.UTF_8);

		final TelemetryManager tm = buildTelemetryManager(tempDir.toString());
		final HttpRequest request = buildHttpRequest("GET", "/something");

		assertNull(executor.executeHttp(request, false, tm));
	}

	// ---- Successful GET match ----

	@Test
	void testExecuteHttpGetMatch(@TempDir Path tempDir) throws IOException {
		writeStandardImage(tempDir);
		final TelemetryManager tm = buildTelemetryManager(tempDir.toString());
		final HttpRequest request = buildHttpRequest("GET", "/api/v1/status");

		final String result = executor.executeHttp(request, false, tm);
		assertEquals("{\"status\":\"OK\",\"version\":\"1.0\"}", result);
	}

	// ---- Method defaults to GET when null ----

	@Test
	void testExecuteHttpNullMethodDefaultsToGet(@TempDir Path tempDir) throws IOException {
		writeStandardImage(tempDir);
		final TelemetryManager tm = buildTelemetryManager(tempDir.toString());
		final HttpRequest request = HttpRequest
			.builder()
			.hostname(HOSTNAME)
			.httpConfiguration(HttpConfiguration.builder().build())
			.method(null)
			.path("/api/v1/status")
			.resultContent(ResultContent.BODY)
			.build();

		final String result = executor.executeHttp(request, false, tm);
		assertEquals("{\"status\":\"OK\",\"version\":\"1.0\"}", result);
	}

	// ---- POST with body and headers ----

	@Test
	void testExecuteHttpPostWithBodyAndHeaders(@TempDir Path tempDir) throws IOException {
		writeStandardImage(tempDir);
		final TelemetryManager tm = buildTelemetryManager(tempDir.toString());
		final HttpRequest request = HttpRequest
			.builder()
			.hostname(HOSTNAME)
			.httpConfiguration(HttpConfiguration.builder().build())
			.method("POST")
			.path("/api/v1/data")
			.body("{\"action\": \"query\"}", Map.of(), "connector", HOSTNAME)
			.header("Content-Type: application/json\nAccept: text/plain", Map.of(), "connector", HOSTNAME)
			.resultContent(ResultContent.BODY)
			.build();

		final String result = executor.executeHttp(request, false, tm);
		assertEquals("{\"result\":\"success\",\"data\":[1,2,3]}", result);
	}

	// ---- ResultContent matching: same path, different resultContent ----

	@Test
	void testExecuteHttpResultContentMatchBody(@TempDir Path tempDir) throws IOException {
		writeStandardImage(tempDir);
		final TelemetryManager tm = buildTelemetryManager(tempDir.toString());
		final HttpRequest request = buildHttpRequest("GET", "/api/v1/status");

		final String result = executor.executeHttp(request, false, tm);
		assertEquals("{\"status\":\"OK\",\"version\":\"1.0\"}", result);
	}

	@Test
	void testExecuteHttpResultContentMatchHttpStatus(@TempDir Path tempDir) throws IOException {
		writeStandardImage(tempDir);
		final TelemetryManager tm = buildTelemetryManager(tempDir.toString());
		final HttpRequest request = HttpRequest
			.builder()
			.hostname(HOSTNAME)
			.httpConfiguration(HttpConfiguration.builder().build())
			.method("GET")
			.path("/api/v1/status")
			.resultContent(ResultContent.HTTP_STATUS)
			.build();

		final String result = executor.executeHttp(request, false, tm);
		assertEquals("200", result);
	}

	@Test
	void testExecuteHttpResultContentMatchHeader(@TempDir Path tempDir) throws IOException {
		writeStandardImage(tempDir);
		final TelemetryManager tm = buildTelemetryManager(tempDir.toString());
		final HttpRequest request = HttpRequest
			.builder()
			.hostname(HOSTNAME)
			.httpConfiguration(HttpConfiguration.builder().build())
			.method("GET")
			.path("/api/v1/headers")
			.resultContent(ResultContent.HEADER)
			.build();

		final String result = executor.executeHttp(request, false, tm);
		assertEquals("X-Custom-Header: custom-value", result);
	}

	// ---- DELETE method match ----

	@Test
	void testExecuteHttpDeleteMatch(@TempDir Path tempDir) throws IOException {
		writeStandardImage(tempDir);
		final TelemetryManager tm = buildTelemetryManager(tempDir.toString());
		final HttpRequest request = HttpRequest
			.builder()
			.hostname(HOSTNAME)
			.httpConfiguration(HttpConfiguration.builder().build())
			.method("DELETE")
			.path("/api/v1/item/42")
			.resultContent(ResultContent.BODY)
			.build();

		final String result = executor.executeHttp(request, false, tm);
		assertEquals("deleted", result);
	}

	// ---- No matching entry ----

	@Test
	void testExecuteHttpNoMatchingEntry(@TempDir Path tempDir) throws IOException {
		writeStandardImage(tempDir);
		final TelemetryManager tm = buildTelemetryManager(tempDir.toString());
		final HttpRequest request = buildHttpRequest("GET", "/nonexistent/path");

		assertNull(executor.executeHttp(request, false, tm));
	}

	// ---- Method mismatch ----

	@Test
	void testExecuteHttpMethodMismatch(@TempDir Path tempDir) throws IOException {
		writeStandardImage(tempDir);
		final TelemetryManager tm = buildTelemetryManager(tempDir.toString());
		final HttpRequest request = buildHttpRequest("PUT", "/api/v1/status");

		assertNull(executor.executeHttp(request, false, tm));
	}

	// ---- Body mismatch ----

	@Test
	void testExecuteHttpBodyMismatch(@TempDir Path tempDir) throws IOException {
		writeStandardImage(tempDir);
		final TelemetryManager tm = buildTelemetryManager(tempDir.toString());
		final HttpRequest request = HttpRequest
			.builder()
			.hostname(HOSTNAME)
			.httpConfiguration(HttpConfiguration.builder().build())
			.method("POST")
			.path("/api/v1/data")
			.body("{\"action\": \"different\"}", Map.of(), "connector", HOSTNAME)
			.header("Content-Type: application/json\nAccept: text/plain", Map.of(), "connector", HOSTNAME)
			.resultContent(ResultContent.BODY)
			.build();

		assertNull(executor.executeHttp(request, false, tm));
	}

	// ---- Headers mismatch ----

	@Test
	void testExecuteHttpHeadersMismatch(@TempDir Path tempDir) throws IOException {
		writeStandardImage(tempDir);
		final TelemetryManager tm = buildTelemetryManager(tempDir.toString());
		final HttpRequest request = HttpRequest
			.builder()
			.hostname(HOSTNAME)
			.httpConfiguration(HttpConfiguration.builder().build())
			.method("POST")
			.path("/api/v1/data")
			.body("{\"action\": \"query\"}", Map.of(), "connector", HOSTNAME)
			.header("Content-Type: text/xml", Map.of(), "connector", HOSTNAME)
			.resultContent(ResultContent.BODY)
			.build();

		assertNull(executor.executeHttp(request, false, tm));
	}

	// ---- Null request entry is skipped ----

	@Test
	void testExecuteHttpNullRequestEntrySkipped(@TempDir Path tempDir) throws IOException {
		final Path httpDir = tempDir.resolve("http");
		Files.createDirectories(httpDir);
		Files.writeString(
			httpDir.resolve("image.yaml"),
			"""
			image:
			  - request:
			    response:
			      file: something.txt
			  - request:
			      method: GET
			      path: /valid
			    response:
			      file: valid-response.txt
			      resultContent: body
			""",
			StandardCharsets.UTF_8
		);
		Files.writeString(httpDir.resolve("valid-response.txt"), "valid answer", StandardCharsets.UTF_8);

		final TelemetryManager tm = buildTelemetryManager(tempDir.toString());
		final HttpRequest request = buildHttpRequest("GET", "/valid");

		final String result = executor.executeHttp(request, false, tm);
		assertEquals("valid answer", result);
	}

	// ---- Missing response file ----

	@Test
	void testExecuteHttpMissingResponseFile(@TempDir Path tempDir) throws IOException {
		final Path httpDir = tempDir.resolve("http");
		Files.createDirectories(httpDir);
		Files.writeString(
			httpDir.resolve("image.yaml"),
			"""
			image:
			  - request:
			      method: GET
			      path: /missing-file
			    response:
			      file: this-file-does-not-exist.txt
			      resultContent: body
			""",
			StandardCharsets.UTF_8
		);

		final TelemetryManager tm = buildTelemetryManager(tempDir.toString());
		final HttpRequest request = buildHttpRequest("GET", "/missing-file");

		assertNull(executor.executeHttp(request, false, tm));
	}

	// ---- Case insensitive method matching ----

	@Test
	void testExecuteHttpMethodCaseInsensitive(@TempDir Path tempDir) throws IOException {
		writeStandardImage(tempDir);
		final TelemetryManager tm = buildTelemetryManager(tempDir.toString());
		final HttpRequest request = buildHttpRequest("get", "/api/v1/status");

		final String result = executor.executeHttp(request, false, tm);
		assertEquals("{\"status\":\"OK\",\"version\":\"1.0\"}", result);
	}

	// ---- Request with no body, entry with no body (strict null==null) ----

	@Test
	void testExecuteHttpNoBodyMatchesNullBody(@TempDir Path tempDir) throws IOException {
		writeStandardImage(tempDir);
		final TelemetryManager tm = buildTelemetryManager(tempDir.toString());
		// GET /api/v1/status has no body in the YAML, request has no body set
		final HttpRequest request = HttpRequest
			.builder()
			.hostname(HOSTNAME)
			.httpConfiguration(HttpConfiguration.builder().build())
			.method("GET")
			.path("/api/v1/status")
			.resultContent(ResultContent.BODY)
			.build();

		final String result = executor.executeHttp(request, false, tm);
		assertEquals("{\"status\":\"OK\",\"version\":\"1.0\"}", result);
	}

	// ---- Request with body but entry has no body → mismatch (strict) ----

	@Test
	void testExecuteHttpBodyPresentButEntryHasNone(@TempDir Path tempDir) throws IOException {
		writeStandardImage(tempDir);
		final TelemetryManager tm = buildTelemetryManager(tempDir.toString());
		final HttpRequest request = HttpRequest
			.builder()
			.hostname(HOSTNAME)
			.httpConfiguration(HttpConfiguration.builder().build())
			.method("GET")
			.path("/api/v1/status")
			.body("unexpected-body", Map.of(), "connector", HOSTNAME)
			.resultContent(ResultContent.BODY)
			.build();

		assertNull(executor.executeHttp(request, false, tm));
	}

	// ---- Request with headers but entry has no headers → mismatch ----

	@Test
	void testExecuteHttpHeadersPresentButEntryHasNone(@TempDir Path tempDir) throws IOException {
		writeStandardImage(tempDir);
		final TelemetryManager tm = buildTelemetryManager(tempDir.toString());
		final HttpRequest request = HttpRequest
			.builder()
			.hostname(HOSTNAME)
			.httpConfiguration(HttpConfiguration.builder().build())
			.method("GET")
			.path("/api/v1/status")
			.header("X-Extra: value", Map.of(), "connector", HOSTNAME)
			.resultContent(ResultContent.BODY)
			.build();

		assertNull(executor.executeHttp(request, false, tm));
	}

	// ---- ResultContent mismatch ----

	@Test
	void testExecuteHttpResultContentMismatch(@TempDir Path tempDir) throws IOException {
		writeStandardImage(tempDir);
		final TelemetryManager tm = buildTelemetryManager(tempDir.toString());
		// The first GET /api/v1/status entry has resultContent=body, requesting ALL should not match it
		// The second has httpStatus. So requesting ALL should find no match.
		final HttpRequest request = HttpRequest
			.builder()
			.hostname(HOSTNAME)
			.httpConfiguration(HttpConfiguration.builder().build())
			.method("GET")
			.path("/api/v1/status")
			.resultContent(ResultContent.ALL)
			.build();

		assertNull(executor.executeHttp(request, false, tm));
	}

	// ---- logMode=true still works ----

	@Test
	void testExecuteHttpLogModeTrue(@TempDir Path tempDir) throws IOException {
		writeStandardImage(tempDir);
		final TelemetryManager tm = buildTelemetryManager(tempDir.toString());
		final HttpRequest request = buildHttpRequest("GET", "/api/v1/status");

		final String result = executor.executeHttp(request, true, tm);
		assertEquals("{\"status\":\"OK\",\"version\":\"1.0\"}", result);
	}

	// ---- Authentication token resolver (with macros) ----

	@Test
	void testExecuteHttpWithAuthenticationToken(@TempDir Path tempDir) throws IOException {
		writeStandardImage(tempDir);
		final TelemetryManager tm = buildTelemetryManager(tempDir.toString());
		final HttpRequest request = HttpRequest
			.builder()
			.hostname(HOSTNAME)
			.httpConfiguration(HttpConfiguration.builder().build())
			.method("GET")
			.path("/api/v1/status")
			.authenticationToken("some-token")
			.resultContent(ResultContent.BODY)
			.build();

		// Should still match GET /api/v1/status with BODY
		final String result = executor.executeHttp(request, false, tm);
		assertEquals("{\"status\":\"OK\",\"version\":\"1.0\"}", result);
	}

	// ---- No http subdirectory at all ----

	@Test
	void testExecuteHttpNoHttpSubdirectory(@TempDir Path tempDir) {
		// Empty temp dir, no http/ subdirectory at all
		final TelemetryManager tm = buildTelemetryManager(tempDir.toString());
		final HttpRequest request = buildHttpRequest("GET", "/something");

		assertNull(executor.executeHttp(request, false, tm));
	}
}
