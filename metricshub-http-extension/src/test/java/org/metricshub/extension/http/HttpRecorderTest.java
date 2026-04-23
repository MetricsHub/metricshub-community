package org.metricshub.extension.http;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.metricshub.engine.common.helpers.JsonHelper;
import org.metricshub.engine.connector.model.common.ResultContent;

class HttpRecorderTest {

	@TempDir
	Path tempDir;

	@AfterEach
	void tearDown() {
		HttpRecorder.clearInstances();
	}

	private long countTxtFiles(final Path dir) throws IOException {
		try (Stream<Path> stream = Files.list(dir)) {
			return stream.filter(p -> p.toString().endsWith(".txt")).count();
		}
	}

	private Path findSingleTxtFile(final Path dir) throws IOException {
		try (Stream<Path> stream = Files.list(dir)) {
			return stream.filter(p -> p.toString().endsWith(".txt")).findFirst().orElse(null);
		}
	}

	@Test
	void testRecordCreatesImageYamlAndResponseFile() throws IOException {
		final HttpRecorder recorder = new HttpRecorder(tempDir.toString());

		recorder.record("GET", "/api/v1/health", null, null, ResultContent.BODY, "OK");
		recorder.flush();

		final Path httpDir = tempDir.resolve(HttpRecorder.HTTP_SUBDIR);
		assertTrue(Files.isRegularFile(httpDir.resolve(HttpRecorder.IMAGE_YAML)));
		assertEquals(1, countTxtFiles(httpDir));
	}

	@Test
	void testRecordWritesCorrectResponseContent() throws IOException {
		final HttpRecorder recorder = new HttpRecorder(tempDir.toString());

		recorder.record("GET", "/api/data", null, null, ResultContent.BODY, "response body content");
		recorder.flush();

		final Path responseFile = findSingleTxtFile(tempDir.resolve(HttpRecorder.HTTP_SUBDIR));
		assertNotNull(responseFile);
		final String content = Files.readString(responseFile, StandardCharsets.UTF_8);
		assertEquals("response body content", content);
	}

	@Test
	@SuppressWarnings("unchecked")
	void testRecordWritesCorrectImageYaml() throws IOException {
		final HttpRecorder recorder = new HttpRecorder(tempDir.toString());

		recorder.record("POST", "/api/v1/query", "request body", null, ResultContent.BODY, "response");
		recorder.flush();

		final ObjectMapper yamlMapper = JsonHelper.buildYamlMapper();
		final Path indexFile = tempDir.resolve(HttpRecorder.HTTP_SUBDIR).resolve(HttpRecorder.IMAGE_YAML);
		final Map<String, Object> image = yamlMapper.readValue(indexFile.toFile(), Map.class);
		final List<Map<String, Object>> entries = (List<Map<String, Object>>) image.get("image");

		assertEquals(1, entries.size());

		final Map<String, Object> entry = entries.get(0);
		final Map<String, Object> request = (Map<String, Object>) entry.get("request");
		assertEquals("POST", request.get("method"));
		assertEquals("/api/v1/query", request.get("path"));
		assertEquals("request body", request.get("body"));
		assertFalse(request.containsKey("headers"));

		final Map<String, Object> response = (Map<String, Object>) entry.get("response");
		assertTrue(((String) response.get("file")).endsWith(".txt"));
		assertEquals("body", response.get("resultContent"));
	}

	@Test
	@SuppressWarnings("unchecked")
	void testRecordMultipleEntriesAppendsToImage() throws IOException {
		final HttpRecorder recorder = new HttpRecorder(tempDir.toString());

		recorder.record("GET", "/api/v1/health", null, null, ResultContent.BODY, "OK");
		recorder.record("GET", "/api/v1/metrics", null, null, ResultContent.BODY, "metrics data");
		recorder.record("POST", "/api/v1/query", "query body", null, ResultContent.BODY, "query result");
		recorder.flush();

		final ObjectMapper yamlMapper = JsonHelper.buildYamlMapper();
		final Path indexFile = tempDir.resolve(HttpRecorder.HTTP_SUBDIR).resolve(HttpRecorder.IMAGE_YAML);
		final Map<String, Object> image = yamlMapper.readValue(indexFile.toFile(), Map.class);
		final List<Map<String, Object>> entries = (List<Map<String, Object>>) image.get("image");

		assertEquals(3, entries.size());

		// Verify 3 response files were created
		final Path httpDir = tempDir.resolve(HttpRecorder.HTTP_SUBDIR);
		assertEquals(3, countTxtFiles(httpDir));
	}

	@Test
	@SuppressWarnings("unchecked")
	void testRecordAllowsDuplicate() throws IOException {
		final HttpRecorder recorder = new HttpRecorder(tempDir.toString());

		recorder.record("GET", "/api/v1/health", null, null, ResultContent.BODY, "OK");
		recorder.record("GET", "/api/v1/health", null, null, ResultContent.BODY, "OK again");
		recorder.flush();

		final ObjectMapper yamlMapper = JsonHelper.buildYamlMapper();
		final Path indexFile = tempDir.resolve(HttpRecorder.HTTP_SUBDIR).resolve(HttpRecorder.IMAGE_YAML);
		final Map<String, Object> image = yamlMapper.readValue(indexFile.toFile(), Map.class);
		final List<Map<String, Object>> entries = (List<Map<String, Object>>) image.get("image");

		assertEquals(2, entries.size());
		assertEquals(2, countTxtFiles(tempDir.resolve(HttpRecorder.HTTP_SUBDIR)));
	}

	@Test
	@SuppressWarnings("unchecked")
	void testRecordDifferentResultContentNotDuplicate() throws IOException {
		final HttpRecorder recorder = new HttpRecorder(tempDir.toString());

		recorder.record("GET", "/api/v1/health", null, null, ResultContent.BODY, "OK");
		recorder.record("GET", "/api/v1/health", null, null, ResultContent.HTTP_STATUS, "200");
		recorder.flush();

		final ObjectMapper yamlMapper = JsonHelper.buildYamlMapper();
		final Path indexFile = tempDir.resolve(HttpRecorder.HTTP_SUBDIR).resolve(HttpRecorder.IMAGE_YAML);
		final Map<String, Object> image = yamlMapper.readValue(indexFile.toFile(), Map.class);
		final List<Map<String, Object>> entries = (List<Map<String, Object>>) image.get("image");

		assertEquals(2, entries.size());
	}

	@Test
	@SuppressWarnings("unchecked")
	void testRecordWithHeaders() throws IOException {
		final HttpRecorder recorder = new HttpRecorder(tempDir.toString());
		final Map<String, String> headers = new LinkedHashMap<>();
		headers.put("Content-Type", "application/json");
		headers.put("Accept", "application/json");

		recorder.record("POST", "/api/v1/query", "body", headers, ResultContent.BODY, "response");
		recorder.flush();

		final ObjectMapper yamlMapper = JsonHelper.buildYamlMapper();
		final Path indexFile = tempDir.resolve(HttpRecorder.HTTP_SUBDIR).resolve(HttpRecorder.IMAGE_YAML);
		final Map<String, Object> image = yamlMapper.readValue(indexFile.toFile(), Map.class);
		final List<Map<String, Object>> entries = (List<Map<String, Object>>) image.get("image");
		final Map<String, Object> request = (Map<String, Object>) entries.get(0).get("request");
		final Map<String, String> recordedHeaders = (Map<String, String>) request.get("headers");

		assertEquals("application/json", recordedHeaders.get("Content-Type"));
		assertEquals("application/json", recordedHeaders.get("Accept"));
	}

	@Test
	@SuppressWarnings("unchecked")
	void testRecordEmptyBodyIsOmitted() throws IOException {
		final HttpRecorder recorder = new HttpRecorder(tempDir.toString());

		recorder.record("GET", "/api/v1/health", "", null, ResultContent.BODY, "OK");
		recorder.flush();

		final ObjectMapper yamlMapper = JsonHelper.buildYamlMapper();
		final Path indexFile = tempDir.resolve(HttpRecorder.HTTP_SUBDIR).resolve(HttpRecorder.IMAGE_YAML);
		final Map<String, Object> image = yamlMapper.readValue(indexFile.toFile(), Map.class);
		final List<Map<String, Object>> entries = (List<Map<String, Object>>) image.get("image");
		final Map<String, Object> request = (Map<String, Object>) entries.get(0).get("request");

		assertFalse(request.containsKey("body"));
	}

	@Test
	@SuppressWarnings("unchecked")
	void testRecordEmptyHeadersAreOmitted() throws IOException {
		final HttpRecorder recorder = new HttpRecorder(tempDir.toString());

		recorder.record("GET", "/api/v1/health", null, Map.of(), ResultContent.BODY, "OK");
		recorder.flush();

		final ObjectMapper yamlMapper = JsonHelper.buildYamlMapper();
		final Path indexFile = tempDir.resolve(HttpRecorder.HTTP_SUBDIR).resolve(HttpRecorder.IMAGE_YAML);
		final Map<String, Object> image = yamlMapper.readValue(indexFile.toFile(), Map.class);
		final List<Map<String, Object>> entries = (List<Map<String, Object>>) image.get("image");
		final Map<String, Object> request = (Map<String, Object>) entries.get(0).get("request");

		assertFalse(request.containsKey("headers"));
	}

	@Test
	@SuppressWarnings("unchecked")
	void testRecordAllResultContent() throws IOException {
		final HttpRecorder recorder = new HttpRecorder(tempDir.toString());

		recorder.record("GET", "/path", null, null, ResultContent.ALL, "all content");
		recorder.flush();

		final ObjectMapper yamlMapper = JsonHelper.buildYamlMapper();
		final Path indexFile = tempDir.resolve(HttpRecorder.HTTP_SUBDIR).resolve(HttpRecorder.IMAGE_YAML);
		final Map<String, Object> image = yamlMapper.readValue(indexFile.toFile(), Map.class);
		final List<Map<String, Object>> entries = (List<Map<String, Object>>) image.get("image");
		final Map<String, Object> response = (Map<String, Object>) entries.get(0).get("response");

		assertEquals("all", response.get("resultContent"));
	}

	@Test
	@SuppressWarnings("unchecked")
	void testRecordHttpStatusResultContent() throws IOException {
		final HttpRecorder recorder = new HttpRecorder(tempDir.toString());

		recorder.record("GET", "/path", null, null, ResultContent.HTTP_STATUS, "200");
		recorder.flush();

		final ObjectMapper yamlMapper = JsonHelper.buildYamlMapper();
		final Path indexFile = tempDir.resolve(HttpRecorder.HTTP_SUBDIR).resolve(HttpRecorder.IMAGE_YAML);
		final Map<String, Object> image = yamlMapper.readValue(indexFile.toFile(), Map.class);
		final List<Map<String, Object>> entries = (List<Map<String, Object>>) image.get("image");
		final Map<String, Object> response = (Map<String, Object>) entries.get(0).get("response");

		assertEquals("httpStatus", response.get("resultContent"));
	}

	@Test
	void testGetInstanceReturnsSameInstance() {
		final String dir = tempDir.resolve("output1").toString();
		final HttpRecorder recorder1 = HttpRecorder.getInstance(dir);
		final HttpRecorder recorder2 = HttpRecorder.getInstance(dir);

		assertSame(recorder1, recorder2);
	}

	@Test
	void testGetInstanceReturnsDifferentInstancesForDifferentDirs() {
		final HttpRecorder recorder1 = HttpRecorder.getInstance(tempDir.resolve("output1").toString());
		final HttpRecorder recorder2 = HttpRecorder.getInstance(tempDir.resolve("output2").toString());

		assertFalse(recorder1 == recorder2);
	}

	@Test
	void testClearInstances() {
		final String dir = tempDir.resolve("output1").toString();
		final HttpRecorder recorder1 = HttpRecorder.getInstance(dir);
		HttpRecorder.clearInstances();
		final HttpRecorder recorder2 = HttpRecorder.getInstance(dir);

		assertFalse(recorder1 == recorder2);
	}

	@Test
	void testLoadExistingEntriesNoFile() throws IOException {
		final HttpRecorder recorder = new HttpRecorder(tempDir.toString());
		final Path nonexistent = tempDir.resolve("nonexistent.yaml");

		final List<Map<String, Object>> entries = recorder.loadExistingEntries(nonexistent);

		assertNotNull(entries);
		assertTrue(entries.isEmpty());
	}

	@Test
	void testLoadExistingEntriesWithValidFile() throws IOException {
		final HttpRecorder recorder = new HttpRecorder(tempDir.toString());

		final String yaml =
			"""
			image:
			  - request:
			      method: GET
			      path: /api/test
			    response:
			      file: response_001.txt
			      resultContent: body
			""";
		final Path indexFile = tempDir.resolve("test_image.yaml");
		Files.writeString(indexFile, yaml, StandardCharsets.UTF_8);

		final List<Map<String, Object>> entries = recorder.loadExistingEntries(indexFile);

		assertEquals(1, entries.size());
	}

	@Test
	void testBuildEntryMethodAndPath() {
		final HttpRecorder recorder = new HttpRecorder(tempDir.toString());
		final Map<String, Object> entry = recorder.buildEntry(
			"GET",
			"/api/v1/health",
			null,
			null,
			ResultContent.BODY,
			"response_001.txt"
		);

		@SuppressWarnings("unchecked")
		final Map<String, Object> request = (Map<String, Object>) entry.get("request");
		assertEquals("GET", request.get("method"));
		assertEquals("/api/v1/health", request.get("path"));
		assertFalse(request.containsKey("body"));
		assertFalse(request.containsKey("headers"));

		@SuppressWarnings("unchecked")
		final Map<String, Object> response = (Map<String, Object>) entry.get("response");
		assertEquals("response_001.txt", response.get("file"));
		assertEquals("body", response.get("resultContent"));
	}

	@Test
	void testBuildEntryWithBodyAndHeaders() {
		final HttpRecorder recorder = new HttpRecorder(tempDir.toString());
		final Map<String, String> headers = new LinkedHashMap<>();
		headers.put("Accept", "text/xml");

		final Map<String, Object> entry = recorder.buildEntry(
			"POST",
			"/api/query",
			"request body",
			headers,
			ResultContent.ALL,
			"response_002.txt"
		);

		@SuppressWarnings("unchecked")
		final Map<String, Object> request = (Map<String, Object>) entry.get("request");
		assertEquals("POST", request.get("method"));
		assertEquals("/api/query", request.get("path"));
		assertEquals("request body", request.get("body"));

		@SuppressWarnings("unchecked")
		final Map<String, String> recordedHeaders = (Map<String, String>) request.get("headers");
		assertEquals("text/xml", recordedHeaders.get("Accept"));

		@SuppressWarnings("unchecked")
		final Map<String, Object> response = (Map<String, Object>) entry.get("response");
		assertEquals("response_002.txt", response.get("file"));
		assertEquals("all", response.get("resultContent"));
	}

	@Test
	void testBuildEntryNullPath() {
		final HttpRecorder recorder = new HttpRecorder(tempDir.toString());
		final Map<String, Object> entry = recorder.buildEntry(
			"GET",
			null,
			null,
			null,
			ResultContent.BODY,
			"response_001.txt"
		);

		@SuppressWarnings("unchecked")
		final Map<String, Object> request = (Map<String, Object>) entry.get("request");
		assertFalse(request.containsKey("path"));
	}

	@Test
	void testBuildEntryNullResultContent() {
		final HttpRecorder recorder = new HttpRecorder(tempDir.toString());
		final Map<String, Object> entry = recorder.buildEntry("GET", "/path", null, null, null, "response_001.txt");

		@SuppressWarnings("unchecked")
		final Map<String, Object> response = (Map<String, Object>) entry.get("response");
		assertFalse(response.containsKey("resultContent"));
	}

	@Test
	@SuppressWarnings("unchecked")
	void testRecordWithDuplicateHeadersAllowsDuplicate() throws IOException {
		final HttpRecorder recorder = new HttpRecorder(tempDir.toString());
		final Map<String, String> headers = new LinkedHashMap<>();
		headers.put("Content-Type", "application/json");

		recorder.record("POST", "/api/v1/query", "body", headers, ResultContent.BODY, "response 1");
		recorder.record("POST", "/api/v1/query", "body", headers, ResultContent.BODY, "response 2");
		recorder.flush();

		final ObjectMapper yamlMapper = JsonHelper.buildYamlMapper();
		final Path indexFile = tempDir.resolve(HttpRecorder.HTTP_SUBDIR).resolve(HttpRecorder.IMAGE_YAML);
		final Map<String, Object> image = yamlMapper.readValue(indexFile.toFile(), Map.class);
		final List<Map<String, Object>> entries = (List<Map<String, Object>>) image.get("image");

		assertEquals(2, entries.size());
	}

	@Test
	@SuppressWarnings("unchecked")
	void testRecordSamePathDifferentBodyNotDuplicate() throws IOException {
		final HttpRecorder recorder = new HttpRecorder(tempDir.toString());

		recorder.record("POST", "/api/v1/query", "body1", null, ResultContent.BODY, "response 1");
		recorder.record("POST", "/api/v1/query", "body2", null, ResultContent.BODY, "response 2");
		recorder.flush();

		final ObjectMapper yamlMapper = JsonHelper.buildYamlMapper();
		final Path indexFile = tempDir.resolve(HttpRecorder.HTTP_SUBDIR).resolve(HttpRecorder.IMAGE_YAML);
		final Map<String, Object> image = yamlMapper.readValue(indexFile.toFile(), Map.class);
		final List<Map<String, Object>> entries = (List<Map<String, Object>>) image.get("image");

		assertEquals(2, entries.size());
	}
}
