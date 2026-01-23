package org.metricshub.web.service.openai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.metricshub.web.config.OpenAiToolOutputProperties;
import org.metricshub.web.dto.openai.PersistedToolOutputFile;
import org.metricshub.web.dto.openai.UploadedToolOutputManifest;
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
	 *
	 * @param properties the OpenAiToolOutputProperties to pass to the service constructor
	 * @return a new ToolResponseManagerService instance
	 */
	private ToolResponseManagerService newService(final OpenAiToolOutputProperties properties) {
		return new ToolResponseManagerService(objectMapper, properties, persistService, uploadService);
	}

	@Test
	void testReturnNullWhenToolResultIsNull() {
		final var properties = new OpenAiToolOutputProperties();
		final var service = newService(properties);

		final String adapted = service.adaptToolOutputOrManifest("RegularTool", null);

		assertNull(adapted, "Null tool result should be returned unchanged");
		verifyNoInteractions(persistService, uploadService);
	}

	@Test
	void testReturnOriginalWhenWithinLimitForNonTroubleshootingTool() {
		final var properties = new OpenAiToolOutputProperties();
		properties.setMaxToolOutputBytes(1_000);
		properties.setSafetyDeltaBytes(100);

		final var service = newService(properties);
		final String toolName = "RegularTool";
		final String toolResultJson = "{\"result\":\"ok\"}";

		final String adapted = service.adaptToolOutputOrManifest(toolName, toolResultJson);

		assertEquals(
			toolResultJson,
			adapted,
			"Should return original output when size is within authorized limit for standard tools"
		);
		verifyNoInteractions(persistService, uploadService);
	}

	@Test
	void testPersistUploadAndReturnManifestForOversizedOutput() throws Exception {
		final var properties = new OpenAiToolOutputProperties();
		properties.setMaxToolOutputBytes(1_100);
		properties.setSafetyDeltaBytes(100); // Authorized limit = 1_000 bytes

		final var service = newService(properties);
		final String toolName = "RegularTool";
		final String toolResultJson = "x".repeat(1_050); // Larger than authorized limit

		final Path tempFile = Files.createTempFile("mh-oversize-", ".json");
		Files.writeString(tempFile, "temporary");

		final var persisted = PersistedToolOutputFile
			.builder()
			.absolutePath(tempFile.toAbsolutePath().toString())
			.resultId("oversize-result-id")
			.sizeBytes(toolResultJson.length())
			.toolName(toolName)
			.build();
		final var manifest = UploadedToolOutputManifest.builder().openaiFileId("file-id").fileName("file.json").build();

		try {
			when(persistService.persist(toolName, toolResultJson)).thenReturn(persisted);
			when(uploadService.uploadToOpenAi(tempFile)).thenReturn(manifest);

			final String adapted = service.adaptToolOutputOrManifest(toolName, toolResultJson);

			assertEquals(
				objectMapper.writeValueAsString(manifest),
				adapted,
				"Oversized outputs should return the uploaded manifest JSON"
			);
			verify(persistService).persist(toolName, toolResultJson);
			verify(uploadService).uploadToOpenAi(tempFile);
			assertTrue(Files.notExists(tempFile), "Persisted file should be deleted after upload");
		} finally {
			Files.deleteIfExists(tempFile);
		}
	}

	@Test
	void testTroubleshootingToolAlwaysUploadsEvenWhenSmall() throws Exception {
		final var properties = new OpenAiToolOutputProperties();
		properties.setMaxToolOutputBytes(2_000);
		properties.setSafetyDeltaBytes(500); // Authorized limit = 1_500 bytes

		final var service = newService(properties);
		final String toolName = TroubleshootHostService.TOOL_NAMES.iterator().next();
		final String toolResultJson = "{\"status\":\"short\"}";

		final Path tempFile = Files.createTempFile("mh-troubleshoot-", ".json");
		Files.writeString(tempFile, "temporary");

		final var persisted = PersistedToolOutputFile
			.builder()
			.absolutePath(tempFile.toAbsolutePath().toString())
			.resultId("troubleshoot-result-id")
			.sizeBytes(toolResultJson.length())
			.toolName(toolName)
			.build();
		final var manifest = UploadedToolOutputManifest
			.builder()
			.openaiFileId("troubleshoot-file-id")
			.fileName("troubleshoot.json")
			.build();

		try {
			when(persistService.persist(toolName, toolResultJson)).thenReturn(persisted);
			when(uploadService.uploadToOpenAi(tempFile)).thenReturn(manifest);

			final String adapted = service.adaptToolOutputOrManifest(toolName, toolResultJson);

			assertEquals(
				objectMapper.writeValueAsString(manifest),
				adapted,
				"Troubleshooting tools should always return manifest JSON even when small"
			);
			verify(persistService).persist(toolName, toolResultJson);
			verify(uploadService).uploadToOpenAi(tempFile);
			assertTrue(Files.notExists(tempFile), "Persisted troubleshooting file should be deleted after upload");
		} finally {
			Files.deleteIfExists(tempFile);
		}
	}

	@Test
	void testThrowsWhenManifestExceedsAuthorizedLimit() throws Exception {
		final var properties = new OpenAiToolOutputProperties();
		properties.setMaxToolOutputBytes(60);
		properties.setSafetyDeltaBytes(50); // Authorized limit = 10 bytes

		final var service = newService(properties);
		final String toolName = "RegularTool";
		final String toolResultJson = "x".repeat(20); // Force oversized path

		final Path tempFile = Files.createTempFile("mh-manifest-limit-", ".json");
		Files.writeString(tempFile, "temporary");

		final var persisted = PersistedToolOutputFile
			.builder()
			.absolutePath(tempFile.toAbsolutePath().toString())
			.resultId("manifest-limit-id")
			.sizeBytes(toolResultJson.length())
			.toolName(toolName)
			.build();
		final var manifest = UploadedToolOutputManifest
			.builder()
			.openaiFileId("file-id-too-long")
			.fileName("manifest.json")
			.build();

		try {
			when(persistService.persist(toolName, toolResultJson)).thenReturn(persisted);
			when(uploadService.uploadToOpenAi(tempFile)).thenReturn(manifest);

			assertThrows(
				IllegalStateException.class,
				() -> service.adaptToolOutputOrManifest(toolName, toolResultJson),
				"Manifest larger than authorized limit should trigger an IllegalStateException"
			);
			verify(persistService).persist(toolName, toolResultJson);
			verify(uploadService).uploadToOpenAi(tempFile);
			assertTrue(
				Files.notExists(tempFile),
				"Persisted file should be deleted even when manifest serialization fails the size check"
			);
		} finally {
			Files.deleteIfExists(tempFile);
		}
	}
}
