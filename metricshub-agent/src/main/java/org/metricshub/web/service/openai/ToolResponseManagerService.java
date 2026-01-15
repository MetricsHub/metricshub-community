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

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.metricshub.web.config.OpenAiToolOutputProperties;
import org.metricshub.web.dto.openai.PersistedToolOutputFile;
import org.metricshub.web.dto.openai.UploadedToolOutputManifest;
import org.metricshub.web.mcp.TroubleshootHostService;
import org.springframework.stereotype.Service;

/**
 * Adapts tool outputs before they are sent to OpenAI: if oversized or troubleshooting tool output,
 * persists and uploads them, returning a manifest.
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
	 * Returns the original tool output unless it's a troubleshooting tool output or oversized
	 *
	 * @param toolName       the tool name
	 * @param toolResultJson JSON string returned by the tool
	 * @return original JSON or manifest JSON
	 */
	public String adaptToolOutputOrManifest(final String toolName, final String toolResultJson) {
		if (toolResultJson == null) {
			return toolResultJson;
		}
		final var isTroubleshootingTool = isTroubleshootingTool(toolName);

		final long authorizedLimitBytes = Math.max(
			0,
			properties.getMaxToolOutputBytes() - properties.getSafetyDeltaBytes()
		);
		final int sizeBytes = toolResultJson.getBytes(StandardCharsets.UTF_8).length;

		if (!isTroubleshootingTool && sizeBytes <= authorizedLimitBytes) {
			return toolResultJson;
		}

		log.info(
			"{} tool output detected; persisting and uploading (tool={}, sizeBytes={}, authorizedLimitBytes={})",
			isTroubleshootingTool ? "Troubleshooting" : "Oversized",
			toolName,
			sizeBytes,
			authorizedLimitBytes
		);

		final PersistedToolOutputFile persisted = persistService.persist(toolName, toolResultJson);

		// Upload to OpenAI Files API
		final UploadedToolOutputManifest manifest = uploadService.uploadToOpenAi(Path.of(persisted.getAbsolutePath()));

		deleteLocalFileSilently(persisted.getAbsolutePath());

		try {
			final String manifestJson = objectMapper.writeValueAsString(manifest);
			final int manifestSize = manifestJson.getBytes(StandardCharsets.UTF_8).length;
			if (manifestSize > authorizedLimitBytes) {
				throw new IllegalStateException(
					"Manifest JSON exceeds authorized limit: " + manifestSize + " > " + authorizedLimitBytes
				);
			}
			return manifestJson;
		} catch (Exception e) {
			throw new IllegalStateException("Failed to serialize tool output manifest JSON", e);
		}
	}

	/**
	 * Checks if the tool is a troubleshooting tool.
	 *
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
