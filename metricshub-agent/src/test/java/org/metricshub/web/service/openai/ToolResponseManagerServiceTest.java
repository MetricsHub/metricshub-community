package org.metricshub.web.service.openai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.metricshub.web.config.OpenAiToolOutputProperties;
import org.metricshub.web.dto.mcp.MonitorTypeItem;
import org.metricshub.web.dto.mcp.MonitorTypeSummaryVo;
import org.metricshub.web.dto.mcp.MonitorVo;
import org.metricshub.web.dto.mcp.MonitorsVo;
import org.metricshub.web.dto.mcp.TelemetryResult;
import org.metricshub.web.dto.openai.PersistedToolOutputFile;
import org.metricshub.web.dto.openai.UploadedToolOutputManifest;
import org.metricshub.web.mcp.HostToolResponse;
import org.metricshub.web.mcp.MultiHostToolResponse;
import org.metricshub.web.mcp.TroubleshootHostService;

class ToolResponseManagerServiceTest {

	private ObjectMapper objectMapper;
	private ToolOutputFilePersistService persistService;
	private ToolOutputFileUploadService uploadService;

	@BeforeEach
	void setUp() {
		objectMapper = new ObjectMapper();
		persistService = mock(ToolOutputFilePersistService.class);
		uploadService = mock(ToolOutputFileUploadService.class);
	}

	/**
	 * Creates a new instance of ToolResponseManagerService with the given properties.
	 */
	private ToolResponseManagerService newService(final OpenAiToolOutputProperties properties) {
		return new ToolResponseManagerService(objectMapper, properties, persistService, uploadService);
	}

	/**
	 * Sets up persist and upload mocks, returning the created temp file path.
	 */
	private Path setupMocks(final String toolName, final String toolResultJson, final String fileId) throws Exception {
		final Path tempFile = Files.createTempFile("mh-test-", ".json");
		Files.writeString(tempFile, "temporary");

		when(persistService.persist(eq(toolName), eq(toolResultJson)))
			.thenReturn(
				PersistedToolOutputFile
					.builder()
					.absolutePath(tempFile.toAbsolutePath().toString())
					.resultId("test-id")
					.sizeBytes(toolResultJson.length())
					.toolName(toolName)
					.build()
			);

		when(uploadService.uploadToOpenAi(any(Path.class)))
			.thenReturn(UploadedToolOutputManifest.builder().openaiFileId(fileId).fileName("test.json").build());

		return tempFile;
	}

	/**
	 * Builds a MultiHostToolResponse<TelemetryResult> with one host and the given type counts.
	 */
	private static MultiHostToolResponse<TelemetryResult> buildTroubleshootResponse(
		final String hostname,
		final Map<String, Integer> typeToCounts
	) {
		final Map<String, List<MonitorTypeItem>> monitors = new LinkedHashMap<>();
		for (final var entry : typeToCounts.entrySet()) {
			final List<MonitorTypeItem> items = new ArrayList<>();
			for (int i = 0; i < entry.getValue(); i++) {
				items.add(
					MonitorVo.builder().attributes(Map.of("id", entry.getKey() + "-" + i)).metrics(Map.of("metric1", 1.0)).build()
				);
			}
			items.add(MonitorTypeSummaryVo.builder().totalMonitors(entry.getValue()).build());
			monitors.put(entry.getKey(), items);
		}
		return MultiHostToolResponse
			.<TelemetryResult>builder()
			.hosts(
				List.of(
					HostToolResponse
						.<TelemetryResult>builder()
						.hostname(hostname)
						.response(TelemetryResult.builder().telemetry(MonitorsVo.builder().monitors(monitors).build()).build())
						.build()
				)
			)
			.build();
	}

	// ---- Null input ----

	@Test
	void testReturnNullWhenToolResultIsNull() {
		final var properties = new OpenAiToolOutputProperties();
		final var service = newService(properties);

		final String adapted = service.adaptToolOutput("RegularTool", null);

		assertNull(adapted, "Null tool result should be returned unchanged");
		verifyNoInteractions(persistService, uploadService);
	}

	// ---- Under limit: manifest with full payload ----

	@Test
	void testUnderLimitReturnsManifestWithFullPayload() throws Exception {
		final var properties = new OpenAiToolOutputProperties();
		properties.setMaxToolOutputBytes(10_000);
		properties.setSafetyDeltaBytes(1_000);

		final var service = newService(properties);
		final String toolName = "RegularTool";
		final String toolResultJson = "{\"result\":\"ok\"}";

		final Path tempFile = setupMocks(toolName, toolResultJson, "file-under-limit");
		try {
			final String adapted = service.adaptToolOutput(toolName, toolResultJson);

			// Should be a manifest JSON
			final JsonNode manifestNode = objectMapper.readTree(adapted);
			assertEquals("tool_output_manifest", manifestNode.get("type").asText());
			assertEquals("file-under-limit", manifestNode.get("openai_file_id").asText());
			assertEquals(toolName + "_output.json", manifestNode.get("file_name").asText());
			// Should contain "not truncated" in description
			assertTrue(manifestNode.get("description").asText().contains("not truncated"));
			// Payload should contain the full data
			assertNotNull(manifestNode.get("payload"));
			assertEquals("ok", manifestNode.get("payload").get("result").asText());

			// Upload should have been called
			verify(persistService).persist(toolName, toolResultJson);
			verify(uploadService).uploadToOpenAi(any(Path.class));
			assertTrue(Files.notExists(tempFile), "Temp file should be deleted after upload");
		} finally {
			Files.deleteIfExists(tempFile);
		}
	}

	// ---- Under limit for troubleshooting tool: also returns manifest with full payload ----

	@Test
	void testTroubleshootingToolUnderLimitReturnsManifestWithFullPayload() throws Exception {
		final var properties = new OpenAiToolOutputProperties();
		properties.setMaxToolOutputBytes(100_000);
		properties.setSafetyDeltaBytes(10_000);

		final var service = newService(properties);
		final String toolName = TroubleshootHostService.TOOL_NAMES.iterator().next();
		final String toolResultJson = "{\"hosts\":[]}";

		final Path tempFile = setupMocks(toolName, toolResultJson, "file-ts-under");
		try {
			final String adapted = service.adaptToolOutput(toolName, toolResultJson);

			final JsonNode manifestNode = objectMapper.readTree(adapted);
			assertEquals("tool_output_manifest", manifestNode.get("type").asText());
			assertEquals("file-ts-under", manifestNode.get("openai_file_id").asText());
			assertTrue(manifestNode.get("description").asText().contains("not truncated"));
			assertNotNull(manifestNode.get("payload"));

			verify(persistService).persist(toolName, toolResultJson);
			verify(uploadService).uploadToOpenAi(any(Path.class));
		} finally {
			Files.deleteIfExists(tempFile);
		}
	}

	// ---- Non-troubleshooting over limit: generic manifest (no payload) ----

	@Test
	void testNonTroubleshootOverLimitReturnsGenericManifest() throws Exception {
		final var properties = new OpenAiToolOutputProperties();
		properties.setMaxToolOutputBytes(100);
		properties.setSafetyDeltaBytes(10); // authorized = 90 bytes

		final var service = newService(properties);
		final String toolName = "RegularTool";
		final String toolResultJson = "x".repeat(200); // over limit

		final Path tempFile = setupMocks(toolName, toolResultJson, "file-over-generic");
		try {
			final String adapted = service.adaptToolOutput(toolName, toolResultJson);

			final JsonNode manifestNode = objectMapper.readTree(adapted);
			assertEquals("tool_output_manifest", manifestNode.get("type").asText());
			assertEquals("file-over-generic", manifestNode.get("openai_file_id").asText());
			// Description should indicate output was too large
			assertTrue(manifestNode.get("description").asText().contains("too large"));
			// Payload should be null/absent
			assertTrue(
				manifestNode.get("payload") == null || manifestNode.get("payload").isNull(),
				"Generic manifest should have no payload"
			);

			verify(persistService).persist(toolName, toolResultJson);
			verify(uploadService).uploadToOpenAi(any(Path.class));
		} finally {
			Files.deleteIfExists(tempFile);
		}
	}

	// ---- Troubleshooting over limit: truncated manifest ----

	@Test
	void testTroubleshootOverLimitReturnsTruncatedManifest() throws Exception {
		final var properties = new OpenAiToolOutputProperties();
		properties.setMaxToolOutputBytes(600);
		properties.setSafetyDeltaBytes(100); // authorized = 500 chars

		final var service = newService(properties);
		final String toolName = TroubleshootHostService.TOOL_NAMES.iterator().next();

		// Build a response with sufficient monitors to guarantee exceeding 500 chars
		// Each monitor adds ~50 chars, so 15 monitors ensures we exceed 500 chars
		final var response = buildTroubleshootResponse("server1", Map.of("disk", 15));
		final String toolResultJson = objectMapper.writeValueAsString(response);

		// Assert that we've constructed input that exceeds the limit (deterministic)
		assertTrue(
			toolResultJson.length() > 500,
			"Test setup error: JSON length (" + toolResultJson.length() + ") should exceed 500 chars"
		);

		final Path tempFile = setupMocks(toolName, toolResultJson, "file-ts-truncated");
		try {
			final String adapted = service.adaptToolOutput(toolName, toolResultJson);

			final JsonNode manifestNode = objectMapper.readTree(adapted);
			assertEquals("tool_output_manifest", manifestNode.get("type").asText());
			assertEquals("file-ts-truncated", manifestNode.get("openai_file_id").asText());
			// Description should contain truncation information
			assertTrue(
				manifestNode.get("description").asText().contains("TRUNCATED SUMMARY") ||
				manifestNode.get("description").asText().contains("payload"),
				"Description should contain truncation info"
			);
			// Payload should contain truncated data
			assertNotNull(manifestNode.get("payload"));
			// The payload should be a valid response structure
			assertTrue(manifestNode.get("payload").has("hosts"));

			verify(persistService).persist(toolName, toolResultJson);
			verify(uploadService).uploadToOpenAi(any(Path.class));
		} finally {
			Files.deleteIfExists(tempFile);
		}
	}

	// ---- Troubleshooting over limit: fallback to generic manifest on parse error ----

	@Test
	void testTroubleshootFallbackOnParseError() throws Exception {
		final var properties = new OpenAiToolOutputProperties();
		properties.setMaxToolOutputBytes(100);
		properties.setSafetyDeltaBytes(10); // authorized = 90 bytes

		final var service = newService(properties);
		final String toolName = TroubleshootHostService.TOOL_NAMES.iterator().next();
		// Invalid JSON that can't be parsed as MultiHostToolResponse
		final String toolResultJson = "x".repeat(200);

		final Path tempFile = setupMocks(toolName, toolResultJson, "file-ts-fallback");
		try {
			final String adapted = service.adaptToolOutput(toolName, toolResultJson);

			// Should fall back to generic manifest
			final JsonNode manifestNode = objectMapper.readTree(adapted);
			assertEquals("tool_output_manifest", manifestNode.get("type").asText());
			assertEquals("file-ts-fallback", manifestNode.get("openai_file_id").asText());
			// Generic manifest: payload should be null
			assertTrue(
				manifestNode.get("payload") == null || manifestNode.get("payload").isNull(),
				"Fallback should produce generic manifest without payload"
			);

			verify(persistService).persist(toolName, toolResultJson);
			verify(uploadService).uploadToOpenAi(any(Path.class));
		} finally {
			Files.deleteIfExists(tempFile);
		}
	}

	// ---- Upload always called ----

	@Test
	void testUploadAlwaysCalledRegardlessOfSize() throws Exception {
		final var properties = new OpenAiToolOutputProperties();
		properties.setMaxToolOutputBytes(100_000);
		properties.setSafetyDeltaBytes(1_000);

		final var service = newService(properties);
		final String toolName = "SomeTool";
		final String toolResultJson = "{\"tiny\":true}";

		final Path tempFile = setupMocks(toolName, toolResultJson, "file-always");
		try {
			service.adaptToolOutput(toolName, toolResultJson);

			// Even for tiny results, persist+upload should be called
			verify(persistService).persist(toolName, toolResultJson);
			verify(uploadService).uploadToOpenAi(any(Path.class));
		} finally {
			Files.deleteIfExists(tempFile);
		}
	}

	// ---- buildTroubleshootDescription ----

	@Test
	void testBuildTroubleshootDescriptionWithTruncatedEntries() {
		final var truncatedEntries = List.of(
			new TelemetryResultTruncator.MonitorEntry("host1", "disk", 200, 25),
			new TelemetryResultTruncator.MonitorEntry("host1", "cpu", 64, 32)
		);

		final String description = ToolResponseManagerService.buildTroubleshootDescription(
			"TRUNCATED SUMMARY (full data in uploaded file):",
			truncatedEntries
		);

		assertTrue(description.contains("Truncated entries:"));
		assertTrue(description.contains("host1 / disk: 200"));
		assertTrue(description.contains("host1 / cpu: 64"));
		assertTrue(description.contains("payload field"));
		assertTrue(description.contains("Code Interpreter"));
	}

	@Test
	void testBuildTroubleshootDescriptionWithoutTruncatedEntries() {
		final String description = ToolResponseManagerService.buildTroubleshootDescription("Some summary", List.of());

		assertTrue(description.contains("Some summary"));
		assertTrue(description.contains("payload field"));
		// Should NOT contain "Truncated entries:" when list is empty
		assertFalse(description.contains("Truncated entries:"));
	}
}
