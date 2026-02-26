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
	 * Automatically uses the context budget manager from {@link ContextBudgetHolder}
	 * if one is set for the current request.
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

		// Check for a budget manager set by the controller
		final ContextBudgetManager budgetManager = ContextBudgetHolder.get();
		if (budgetManager != null) {
			return adaptToolOutputWithBudget(toolName, toolResultJson, fileId, fileName, budgetManager);
		}

		// No budget manager - use existing per-tool truncation logic
		return adaptToolOutputNoBudget(toolName, toolResultJson, fileId, fileName);
	}

	/**
	 * Adapts tool output using the global context budget manager.
	 * Uses tiered allocation with two-phase commit: tentatively allocates budget for the full size,
	 * builds the manifest, measures actual size, and refunds unused budget.
	 *
	 * @param toolName       the tool name
	 * @param toolResultJson the raw JSON output
	 * @param fileId         the OpenAI file ID
	 * @param fileName       the file name
	 * @param budgetManager  the context budget manager
	 * @return the manifest JSON
	 */
	private String adaptToolOutputWithBudget(
		final String toolName,
		final String toolResultJson,
		final String fileId,
		final String fileName,
		final ContextBudgetManager budgetManager
	) {
		// Phase 1: Request tentative budget allocation based on full input size
		final ContextBudgetManager.AllocationResult allocation = budgetManager.allocate(toolResultJson.length());

		// Build the manifest based on the allocation tier
		final String manifestJson = buildManifestWithBudget(toolName, toolResultJson, fileId, fileName, allocation);

		// Phase 2: Measure actual manifest size and refund unused budget
		refundUnusedBudget(budgetManager, allocation, manifestJson);

		return manifestJson;
	}

	/**
	 * Builds the manifest based on the allocation tier.
	 *
	 * @param toolName       the tool name
	 * @param toolResultJson the raw JSON output
	 * @param fileId         the OpenAI file ID
	 * @param fileName       the file name
	 * @param allocation     the allocation result from the budget manager
	 * @return the manifest JSON
	 */
	private String buildManifestWithBudget(
		final String toolName,
		final String toolResultJson,
		final String fileId,
		final String fileName,
		final ContextBudgetManager.AllocationResult allocation
	) {
		return switch (allocation.tier()) {
			case FULL -> buildManifestWithPayload(fileName, fileId, toolResultJson);
			case TRUNCATED -> {
				if (isTroubleshootingTool(toolName)) {
					yield handleTroubleshootToolOutputWithBudget(
						toolName,
						toolResultJson,
						fileId,
						fileName,
						allocation.availableChars()
					);
				}
				// Non-troubleshooting: can't smart-truncate, fall back to file reference
				yield buildGenericManifest(fileName, fileId);
			}
			case SUMMARY_ONLY -> {
				if (isTroubleshootingTool(toolName)) {
					yield handleTroubleshootSummaryOnly(toolName, toolResultJson, fileId, fileName);
				}
				yield buildGenericManifest(fileName, fileId);
			}
			case FILE_REFERENCE_ONLY -> buildFileReferenceOnlyManifest(fileName, fileId);
		};
	}

	/**
	 * Calculates the actual size of the manifest and refunds any unused budget
	 * that was tentatively allocated but not consumed.
	 *
	 * @param budgetManager the context budget manager
	 * @param allocation    the original allocation result
	 * @param manifestJson  the actual manifest that was built
	 */
	private void refundUnusedBudget(
		final ContextBudgetManager budgetManager,
		final ContextBudgetManager.AllocationResult allocation,
		final String manifestJson
	) {
		// Calculate actual size in tokens (based on character count, not bytes)
		final int actualSizeChars = manifestJson.length();
		final int actualTokens = (int) Math.ceil(actualSizeChars / ContextBudgetManager.CHARS_PER_TOKEN);

		// Refund the difference between what was allocated and what was actually used
		final int tokensToRefund = allocation.availableTokens() - actualTokens;
		if (tokensToRefund > 0) {
			budgetManager.refund(tokensToRefund);
			log.debug(
				"Refunded unused budget: allocated={}t, actual={}t, refunded={}t",
				allocation.availableTokens(),
				actualTokens,
				tokensToRefund
			);
		}
	}

	/**
	 * Original behavior when no budget manager is set.
	 * Uses per-tool truncation based on maxToolOutputBytes.
	 *
	 * @param toolName       the tool name
	 * @param toolResultJson the raw JSON output
	 * @param fileId         the OpenAI file ID
	 * @param fileName       the file name
	 * @return the manifest JSON
	 */
	private String adaptToolOutputNoBudget(
		final String toolName,
		final String toolResultJson,
		final String fileId,
		final String fileName
	) {
		// Get the configured byte limit for tool outputs
		final long authorizedLimitBytes = properties.getMaxToolOutputBytes();
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

				// Validate manifestOverhead is non-negative (manifestSizeBytes should always be >= payloadSizeBytes)
				// In theory, this should never happen as the manifest contains the payload plus additional fields.
				// However, we handle this defensively: if it occurs, we stop truncation and fall back to the
				// generic manifest (instead of throwing an exception) to ensure the tool can still return a result.
				if (manifestOverhead < 0) {
					log.warn(
						"Unexpected negative manifest overhead (manifestSize={}, payloadSize={}); stopping truncation",
						manifestSizeBytes,
						payloadSizeBytes
					);
					break;
				}

				final long newPayloadBudget = maxOutputSize - manifestOverhead;

				// If we cannot reduce the budget further, stop iterating
				// This happens when: 1) newPayloadBudget is non-positive (no room for payload)
				// or 2) newPayloadBudget >= payloadBudget (no progress made, likely due to large overhead)
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
	 * Handles troubleshooting tool output with a specific character budget from the global context budget manager.
	 * Uses the TelemetryResultTruncator with the budget as the max size.
	 *
	 * @param toolName       the tool name
	 * @param toolResultJson the full JSON output
	 * @param fileId         the OpenAI file ID
	 * @param fileName       the file name
	 * @param budgetChars    the allocated character budget
	 * @return the manifest JSON with truncated payload
	 */
	private String handleTroubleshootToolOutputWithBudget(
		final String toolName,
		final String toolResultJson,
		final String fileId,
		final String fileName,
		final int budgetChars
	) {
		try {
			final MultiHostToolResponse<TelemetryResult> fullResponse = objectMapper.readValue(
				toolResultJson,
				new TypeReference<MultiHostToolResponse<TelemetryResult>>() {}
			);

			// Use the allocated budget as the max size for truncation
			final TelemetryResultTruncator.TruncationResult truncationResult = TelemetryResultTruncator.truncate(
				fullResponse,
				budgetChars,
				objectMapper
			);

			final String description = buildTroubleshootDescription(
				truncationResult.summary(),
				truncationResult.truncatedEntries()
			);

			final JsonNode truncatedPayload = objectMapper.valueToTree(truncationResult.truncatedResponse());

			final UploadedToolOutputManifest manifest = UploadedToolOutputManifest
				.builder()
				.openaiFileId(fileId)
				.fileName(fileName)
				.description(description)
				.payload(truncatedPayload)
				.build();

			return objectMapper.writeValueAsString(manifest);
		} catch (Exception e) {
			log.warn(
				"Failed to truncate troubleshoot response for tool '{}' with budget, falling back to generic manifest: {}",
				toolName,
				e.getMessage()
			);
			log.debug("Exception details:", e);
			return buildGenericManifest(fileName, fileId);
		}
	}

	/**
	 * Handles troubleshooting tool output when only summary text is allowed (no payload).
	 * Parses the response, builds the summary string, and returns a manifest with
	 * summary in description but NO payload field.
	 *
	 * @param toolName       the tool name
	 * @param toolResultJson the full JSON output
	 * @param fileId         the OpenAI file ID
	 * @param fileName       the file name
	 * @return the manifest JSON with summary only
	 */
	private String handleTroubleshootSummaryOnly(
		final String toolName,
		final String toolResultJson,
		final String fileId,
		final String fileName
	) {
		try {
			final MultiHostToolResponse<TelemetryResult> fullResponse = objectMapper.readValue(
				toolResultJson,
				new TypeReference<MultiHostToolResponse<TelemetryResult>>() {}
			);

			// Truncate to 0 monitors (summary only) to get stats
			final TelemetryResultTruncator.TruncationResult truncationResult = TelemetryResultTruncator.truncate(
				fullResponse,
				0,
				objectMapper
			);

			final String description =
				truncationResult.summary() +
				"\n\nPayload omitted due to context budget constraints." +
				"\nFor the complete dataset, use Code Interpreter to read " +
				"the uploaded file using the openai_file_id.";

			final UploadedToolOutputManifest manifest = UploadedToolOutputManifest
				.builder()
				.openaiFileId(fileId)
				.fileName(fileName)
				.description(description)
				.build();

			return objectMapper.writeValueAsString(manifest);
		} catch (Exception e) {
			log.warn("Failed to build summary for tool '{}': {}", toolName, e.getMessage());
			log.debug("Exception details:", e);
			return buildFileReferenceOnlyManifest(fileName, fileId);
		}
	}

	/**
	 * Builds a minimal manifest with only the file reference.
	 * Used when the context budget is completely exhausted.
	 *
	 * @param fileName the file name
	 * @param fileId   the OpenAI file ID
	 * @return the manifest JSON with file reference only
	 */
	private String buildFileReferenceOnlyManifest(final String fileName, final String fileId) {
		try {
			final UploadedToolOutputManifest manifest = UploadedToolOutputManifest
				.builder()
				.openaiFileId(fileId)
				.fileName(fileName)
				.description(
					"Tool output uploaded to file. Context budget exhausted — no inline data available. " +
					"Use Code Interpreter to read the file using the openai_file_id."
				)
				.build();
			return objectMapper.writeValueAsString(manifest);
		} catch (Exception e) {
			log.error("Failed to build file reference manifest", e);
			return "{\"openai_file_id\":\"" + fileId + "\",\"description\":\"See uploaded file.\"}";
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
		final int sizeBytes = toolResultJson.getBytes(StandardCharsets.UTF_8).length;
		log.info("Uploading tool output to OpenAI Files API (tool={}, sizeBytes={})", toolName, sizeBytes);
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
