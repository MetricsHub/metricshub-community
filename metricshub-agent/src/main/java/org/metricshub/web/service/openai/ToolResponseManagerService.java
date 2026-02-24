package org.metricshub.web.service.openai;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * MetricsHub Agent
 * ჻჻჻჻჻჻
 * Copyright 2023 - 2025 MetricsHub
 * ჻჻჻჻჻჻
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 * ╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱
 */

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.metricshub.web.config.OpenAiToolOutputProperties;
import org.metricshub.web.dto.mcp.TelemetryResult;
import org.metricshub.web.dto.openai.PersistedToolOutputFile;
import org.metricshub.web.dto.openai.UploadedToolOutputManifest;
import org.metricshub.web.mcp.MultiHostToolResponse;
import org.metricshub.web.mcp.TroubleshootHostService;
import org.springframework.stereotype.Service;

/**
 * Adapts tool outputs before they are sent to OpenAI.
 *
 * <p><strong>Always Upload strategy</strong>: the full payload is always uploaded to
 * OpenAI Files API regardless of size or tool type. The size check only determines
 * what is <em>returned</em> to the assistant.</p>
 *
 * <ul>
 *   <li><strong>Any tool, under size limit</strong> &rarr; upload full payload, return manifest with
 *       original JSON in payload + file reference (openai_file_id, file_name)</li>
 *   <li><strong>Non-troubleshooting tool, over size limit</strong> &rarr; upload full payload, return
 *       manifest with upload info only</li>
 *   <li><strong>Troubleshooting tool, over size limit</strong> &rarr; upload full payload, progressively
 *       halve largest (host, type) entries, return manifest with truncated data in payload +
 *       per-(host, type) truncation details in description + upload info</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ToolResponseManagerService {

	private final ObjectMapper objectMapper;
	private final OpenAiToolOutputProperties properties;
	private final ToolOutputFilePersistService persistService;
	private final ToolOutputFileUploadService uploadService;

	/**
	 * Maximum number of attempts to iteratively truncate and rebuild the manifest
	 * to fit within the authorized size limit. Each attempt adjusts the payload budget
	 * to account for the overhead of the manifest wrapper (description, file fields, JSON structure).
	 */
	private static final int MAX_TRUNCATION_ATTEMPTS = 3;

	/**
	 * Adapts tool output for OpenAI consumption.
	 *
	 * @param toolName       the name of the tool that produced the output
	 * @param toolResultJson the raw JSON output from the tool
	 * @return a manifest JSON (always), containing the file reference and either the full or truncated payload
	 */
	public String adaptToolOutput(final String toolName, final String toolResultJson) {
		if (toolResultJson == null) {
			return toolResultJson;
		}

		// Always upload the full payload to OpenAI Files API
		final String fileId = uploadToOpenAiFiles(toolName, toolResultJson);
		final String fileName = toolName + "_output.json";

		// Compute authorized byte limit (safetyDelta provides margin for manifest wrapper)
		final long authorizedLimitBytes = Math.max(
			0,
			properties.getMaxToolOutputBytes() - properties.getSafetyDeltaBytes()
		);
		final int sizeBytes = toolResultJson.getBytes(StandardCharsets.UTF_8).length;

		// If under the size limit → return manifest with full JSON in payload field + file reference
		if (sizeBytes <= authorizedLimitBytes) {
			return buildManifestWithPayload(fileName, fileId, toolResultJson);
		}

		// Over the size limit: behavior depends on tool type
		if (isTroubleshootingTool(toolName)) {
			log.info(
				"Troubleshooting tool output exceeds limit (tool={}, sizeBytes={}, limit={}); truncating",
				toolName,
				sizeBytes,
				authorizedLimitBytes
			);
			return handleTroubleshootToolOutput(toolName, toolResultJson, fileId, fileName, authorizedLimitBytes);
		}

		log.info(
			"Non-troubleshooting tool output exceeds limit (tool={}, sizeBytes={}, limit={}); returning generic manifest",
			toolName,
			sizeBytes,
			authorizedLimitBytes
		);

		// Non-troubleshooting tool → return manifest with upload info only
		return buildGenericManifest(fileName, fileId);
	}

	/**
	 * Handles troubleshooting tool output that exceeds the size limit.
	 * The full payload has already been uploaded. This method:
	 * <ol>
	 *   <li>Parses as MultiHostToolResponse&lt;TelemetryResult&gt;</li>
	 *   <li>Iteratively truncates monitors (progressive halving, largest entry first),
	 *       builds the full manifest, and verifies the serialized manifest fits within
	 *       {@code maxOutputSize}. After each oversize attempt the payload budget is
	 *       reduced by the measured manifest overhead (description, file fields, JSON
	 *       structure) so the next pass targets a tighter limit.</li>
	 *   <li>If the manifest fits within the limit after truncation, returns it.</li>
	 *   <li>If truncation cannot bring the manifest within the limit (e.g., no truncatable
	 *       entries, or the overhead alone exceeds the budget), falls back to a generic
	 *       manifest (upload info only, no payload).</li>
	 * </ol>
	 *
	 * @param toolName       the tool name
	 * @param toolResultJson the full JSON output
	 * @param fileId         the OpenAI file ID from the already-uploaded full payload
	 * @param fileName       the file name for the manifest
	 * @param maxOutputSize  the maximum allowed serialized size in UTF-8 bytes
	 * @return the manifest JSON with truncated summary, or a generic manifest as fallback
	 */
	private String handleTroubleshootToolOutput(
		final String toolName,
		final String toolResultJson,
		final String fileId,
		final String fileName,
		final long maxOutputSize
	) {
		try {
			// Parse the full response
			final MultiHostToolResponse<TelemetryResult> fullResponse = objectMapper.readValue(
				toolResultJson,
				new TypeReference<MultiHostToolResponse<TelemetryResult>>() {}
			);

			// Iteratively truncate and verify the full manifest fits within maxOutputSize.
			// The first pass targets the payload at maxOutputSize; subsequent passes reduce the
			// budget to account for the manifest wrapper (description, file fields, JSON structure).
			long payloadBudget = maxOutputSize;

			for (int attempt = 0; attempt < MAX_TRUNCATION_ATTEMPTS; attempt++) {
				// Truncate monitors (progressive halving, largest entry first) until payload fits
				final TelemetryResultTruncator.TruncationResult truncationResult = TelemetryResultTruncator.truncate(
					fullResponse,
					payloadBudget,
					objectMapper
				);

				// Build description with summary
				final String description = buildTroubleshootDescription(
					truncationResult.summary(),
					truncationResult.truncatedEntries()
				);

				// Convert truncated response to JsonNode for the manifest payload
				final JsonNode truncatedPayload = objectMapper.valueToTree(truncationResult.truncatedResponse());

				// Build the full manifest
				final UploadedToolOutputManifest manifest = UploadedToolOutputManifest
					.builder()
					.openaiFileId(fileId)
					.fileName(fileName)
					.description(description)
					.payload(truncatedPayload)
					.build();

				final String manifestJson = objectMapper.writeValueAsString(manifest);
				final long manifestSizeBytes = manifestJson.getBytes(StandardCharsets.UTF_8).length;

				// If the full manifest fits within the limit, return it
				if (manifestSizeBytes <= maxOutputSize) {
					return manifestJson;
				}

				// Compute the overhead added by the manifest wrapper (everything except the payload value)
				final long payloadSizeBytes = objectMapper
					.writeValueAsString(truncatedPayload)
					.getBytes(StandardCharsets.UTF_8)
					.length;
				final long manifestOverhead = manifestSizeBytes - payloadSizeBytes;

				// Validate manifestOverhead is positive (manifestSizeBytes should always be larger than payloadSizeBytes)
				if (manifestOverhead <= 0) {
					log.warn(
						"Unexpected manifest overhead calculation (manifestSize={}, payloadSize={}); stopping truncation",
						manifestSizeBytes,
						payloadSizeBytes
					);
					break;
				}

				final long newPayloadBudget = maxOutputSize - manifestOverhead;

				// If we cannot reduce the budget further, stop iterating
				if (newPayloadBudget <= 0 || newPayloadBudget >= payloadBudget) {
					log.debug(
						"Cannot reduce payload budget further (attempt={}, payloadBudget={}, newPayloadBudget={})",
						attempt + 1,
						payloadBudget,
						newPayloadBudget
					);
					break;
				}

				payloadBudget = newPayloadBudget;
				log.debug(
					"Manifest exceeded limit (attempt={}, manifestSize={}, limit={}); retrying with payloadBudget={}",
					attempt + 1,
					manifestSizeBytes,
					maxOutputSize,
					payloadBudget
				);
			}

			// Truncation could not bring the full manifest within the limit — fall back to generic manifest
			log.warn(
				"Troubleshooting manifest for tool '{}' still exceeds limit after truncation retries; " +
				"falling back to generic manifest",
				toolName
			);
			return buildGenericManifest(fileName, fileId);
		} catch (Exception e) {
			log.warn(
				"Failed to truncate troubleshoot response for tool '{}', falling back to generic manifest: {}",
				toolName,
				e.getMessage()
			);
			log.debug("Exception details:", e);
			return buildGenericManifest(fileName, fileId);
		}
	}

	/**
	 * Builds a human-readable description for the troubleshoot manifest containing:
	 * <ul>
	 *   <li>A summary with per-(host, type) monitor counts and truncation indicators</li>
	 *   <li>Which specific (host, type) entries were truncated and to what level</li>
	 *   <li>Instructions to use Code Interpreter for full data</li>
	 * </ul>
	 *
	 * @param summary           the truncation summary with stats
	 * @param truncatedEntries  list of truncated (host, type) entries with original/final counts
	 * @return the formatted description
	 */
	static String buildTroubleshootDescription(
		final String summary,
		final List<TelemetryResultTruncator.MonitorEntry> truncatedEntries
	) {
		final StringBuilder sb = new StringBuilder();
		sb.append(summary);
		if (!truncatedEntries.isEmpty()) {
			sb.append("\n\nTruncated entries:");
			for (final TelemetryResultTruncator.MonitorEntry entry : truncatedEntries) {
				sb.append("\n  - ").append(entry.hostname()).append(" / ").append(entry.monitorType());
				sb.append(": ").append(entry.originalCount()).append(" \u2192 ").append(entry.currentCount());
			}
		}
		sb.append("\n\nThe truncated data is available in the payload field.");
		sb.append("\nFor the complete dataset with all monitors, use Code Interpreter to read ");
		sb.append("the uploaded file using the openai_file_id. Do not request raw JSON chunks.");
		return sb.toString();
	}

	/**
	 * Builds a manifest for under-limit responses.
	 * Sets the description to indicate the result is NOT truncated,
	 * and includes the full JSON as a JsonNode in the payload field.
	 *
	 * @param fileName       the file name
	 * @param fileId         the OpenAI file ID
	 * @param jsonPayload    the full JSON string to parse into a JsonNode for the payload field
	 * @return the manifest JSON string
	 */
	private String buildManifestWithPayload(final String fileName, final String fileId, final String jsonPayload) {
		try {
			final JsonNode payloadNode = objectMapper.readTree(jsonPayload);
			final UploadedToolOutputManifest manifest = UploadedToolOutputManifest
				.builder()
				.openaiFileId(fileId)
				.fileName(fileName)
				.description("Complete tool output (not truncated). The payload field contains the full data.")
				.payload(payloadNode)
				.build();
			return objectMapper.writeValueAsString(manifest);
		} catch (Exception e) {
			throw new IllegalStateException("Failed to build manifest with payload", e);
		}
	}

	/**
	 * Builds a generic manifest for non-troubleshooting tools that exceed the size limit.
	 * Sets the description to indicate the result IS truncated (payload is omitted),
	 * and directs the assistant to use Code Interpreter for the full data.
	 *
	 * @param fileName the file name
	 * @param fileId   the OpenAI file ID of the uploaded full payload
	 * @return the manifest JSON string
	 */
	String buildGenericManifest(final String fileName, final String fileId) {
		try {
			final UploadedToolOutputManifest manifest = UploadedToolOutputManifest
				.builder()
				.openaiFileId(fileId)
				.fileName(fileName)
				.build();
			return objectMapper.writeValueAsString(manifest);
		} catch (Exception e) {
			throw new IllegalStateException("Failed to build generic manifest", e);
		}
	}

	/**
	 * Persists the full tool output to disk, uploads it to OpenAI Files API,
	 * and deletes the local file. Returns the OpenAI file ID.
	 *
	 * @param toolName       the tool name
	 * @param toolResultJson the full JSON output
	 * @return the OpenAI file ID
	 */
	private String uploadToOpenAiFiles(final String toolName, final String toolResultJson) {
		log.info("Uploading tool output to OpenAI Files API (tool={}, sizeChars={})", toolName, toolResultJson.length());
		final PersistedToolOutputFile persisted = persistService.persist(toolName, toolResultJson);
		try {
			final UploadedToolOutputManifest uploaded = uploadService.uploadToOpenAi(Path.of(persisted.getAbsolutePath()));
			return uploaded.getOpenaiFileId();
		} finally {
			deleteLocalFileSilently(persisted.getAbsolutePath());
		}
	}

	/**
	 * Checks if the tool is a troubleshooting tool.
	 *
	 * @param toolName the tool name to check
	 * @return true if it is a troubleshooting tool, false otherwise
	 */
	private static boolean isTroubleshootingTool(final String toolName) {
		return TroubleshootHostService.TOOL_NAMES.stream().anyMatch(t -> t.equalsIgnoreCase(toolName));
	}

	/**
	 * Best-effort deletion of the persisted file after upload.
	 *
	 * @param absolutePath file to delete
	 */
	private void deleteLocalFileSilently(final String absolutePath) {
		try {
			java.nio.file.Files.deleteIfExists(java.nio.file.Path.of(absolutePath));
		} catch (Exception e) {
			log.error("Failed to delete persisted tool output file {}: {}", absolutePath, e.getMessage());
			log.debug("Exception details:", e);
		}
	}
}
