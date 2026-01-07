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

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.metricshub.web.config.OpenAiTelemetryChunkingProperties;
import org.metricshub.web.dto.ChunkCreationResult;
import org.metricshub.web.service.ChunkCreatorService;
import org.springframework.stereotype.Service;

/**
 * Adapts tool outputs before they are sent to OpenAI, ensuring telemetry troubleshooting tool outputs never exceed
 * OpenAI's maximum tool output size.
 * <p>
 * For enabled telemetry tools, oversized outputs are chunked into temp files and replaced with a small manifest JSON that
 * instructs the assistant to fetch pages via {@code FetchResponseChunk}.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ToolResponseManagerService {

	private final ObjectMapper objectMapper;
	private final OpenAiTelemetryChunkingProperties properties;
	private final ChunkCreatorService chunkCreatorService;

	/**
	 * Return {@code toolResultJson} unchanged unless:
	 * <ul>
	 *   <li>the tool is in {@code enabledToolNames}</li>
	 *   <li>the UTF-8 byte size exceeds {@code maxToolOutputBytes - safetyDeltaBytes}</li>
	 * </ul>
	 * In that case, this method stores chunk pages on disk and returns a manifest JSON.
	 *
	 * @param toolName      tool name
	 * @param toolResultJson JSON tool output
	 * @return tool output JSON or response manifest JSON
	 */
	public String adaptTelemetryToolOutputOrManifest(final String toolName, final String toolResultJson) {
		if (toolName == null || toolResultJson == null) {
			return toolResultJson;
		}

		if (properties.getEnabledToolNames() == null || !properties.getEnabledToolNames().contains(toolName)) {
			return toolResultJson;
		}

		final long max = properties.getMaxToolOutputBytes();
		final long safety = properties.getSafetyDeltaBytes();
		final long authorizedLimitBytes = Math.max(0, max - safety);

		final int sizeBytes = toolResultJson.getBytes(StandardCharsets.UTF_8).length;
		if (sizeBytes <= authorizedLimitBytes) {
			return toolResultJson;
		}

		log.info(
			"Tool output is oversized; chunking telemetry tool output (toolName={}, sizeBytes={}, authorizedLimitBytes={})",
			toolName,
			sizeBytes,
			authorizedLimitBytes
		);

		final ChunkCreationResult result = chunkCreatorService.createTelemetryResultChunks(
			toolResultJson,
			authorizedLimitBytes
		);
		final ResponseManifest manifest = ResponseManifest
			.builder()
			.type("response_manifest")
			.resultId(result.getResultId())
			.tool("FetchResponseChunk")
			.firstResultNumber(0)
			.pageCount(result.getPageCount())
			.description(
				"Tool output was chunked. Call FetchResponseChunk(resultId, resultNumber) starting at 0. " +
				"Continue calling it with next_result while has_more is true. Stop when has_more is false."
			)
			.build();

		try {
			return objectMapper.writeValueAsString(manifest);
		} catch (Exception e) {
			throw new IllegalStateException("Failed to serialize response manifest JSON", e);
		}
	}

	/**
	 * Manifest returned to OpenAI when a telemetry tool output has been chunked.
	 */
	@Builder
	@Data
	private static class ResponseManifest {

		private String type;

		@JsonProperty("result_id")
		private String resultId;

		private String tool;

		@JsonProperty("first_result_number")
		private int firstResultNumber;

		@JsonProperty("page_count")
		private int pageCount;

		private String description;
	}
}
