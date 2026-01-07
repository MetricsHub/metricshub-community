package org.metricshub.web.mcp;

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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.metricshub.web.config.OpenAiTelemetryChunkingProperties;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

/**
 * Tool used by the assistant to retrieve paginated telemetry chunks produced when a troubleshooting tool response is oversized.
 * <p>
 * Pages are stored on disk under {@code <baseTempDir>/<resultId>/<resultNumber>.json}. When the last page (has_more=false) is
 * served, the directory is scheduled for deletion after {@code cleanupDelaySeconds}.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ChunkToolService implements IMCPToolService {

	private final OpenAiTelemetryChunkingProperties properties;
	private final ObjectMapper objectMapper;

	private final ScheduledExecutorService cleanupExecutor = Executors.newSingleThreadScheduledExecutor(
		new DaemonThreadFactory()
	);
	private final Map<String, ScheduledFuture<?>> cleanupTasks = new ConcurrentHashMap<>();

	@Tool(
		name = "FetchResponseChunk",
		description = "Fetch a paginated chunk of a previous oversized telemetry tool output. " +
		"Use resultId and resultNumber starting at 0. Continue calling it with next_result while has_more is true. " +
		"Stop when has_more is false."
	)
	public String fetchResponseChunk(
		@ToolParam(description = "The UUID of the chunked result") final String resultId,
		@ToolParam(description = "The page number, starting at 0") final Integer resultNumber
	) {
		final Path chunkPath = Paths.get(
			properties.getBaseTempDir(),
			resultId == null ? "" : resultId,
			pageName(resultNumber)
		);

		if (!Files.exists(chunkPath)) {
			return notFoundJson(resultId, resultNumber);
		}

		try {
			final String json = Files.readString(chunkPath, StandardCharsets.UTF_8);
			scheduleCleanupIfLastPage(resultId, json);
			return json;
		} catch (IOException e) {
			log.warn("Failed to read chunk file {}: {}", chunkPath, e.getMessage());
			return notFoundJson(resultId, resultNumber);
		}
	}

	/**
	 * Build a minimal error JSON when the requested chunk is absent.
	 *
	 * @param resultId     chunk identifier
	 * @param resultNumber requested page index
	 * @return serialized error payload
	 */
	private String notFoundJson(final String resultId, final Integer resultNumber) {
		final ObjectNode root = JsonNodeFactory.instance.objectNode();
		root.put("result_id", resultId == null ? "" : resultId);
		root.putArray("hosts");
		root.set("next_result", JsonNodeFactory.instance.nullNode());
		root.put("has_more", false);
		root.put(
			"errorMessage",
			"Chunk not found for resultId=" + (resultId == null ? "null" : resultId) + " and resultNumber=" + resultNumber
		);
		try {
			return objectMapper.writeValueAsString(root);
		} catch (Exception e) {
			final String fallback =
				"{\"result_id\":\"" +
				(resultId == null ? "" : resultId) +
				"\",\"hosts\":[],\"next_result\":null,\"has_more\":false}";
			return fallback;
		}
	}

	/**
	 * When the returned page is the last one, schedule cleanup of the chunk directory.
	 *
	 * @param resultId chunk identifier
	 * @param json     page content returned to the caller
	 */
	private void scheduleCleanupIfLastPage(final String resultId, final String json) {
		try {
			final JsonNode node = objectMapper.readTree(json);
			if (node == null || !node.has("has_more") || node.get("has_more").asBoolean(true)) {
				return;
			}
			if (cleanupTasks.containsKey(resultId)) {
				return;
			}

			final long delaySec = Math.max(0, properties.getCleanupDelaySeconds());
			final ScheduledFuture<?> future = cleanupExecutor.schedule(
				() -> deleteResultDir(resultId),
				delaySec,
				TimeUnit.SECONDS
			);
			cleanupTasks.put(resultId, future);
		} catch (Exception e) {
			log.debug("Could not parse chunk JSON to schedule cleanup: {}", e.getMessage());
		}
	}

	/**
	 * Delete all files for a given chunk result id, ignoring missing paths.
	 *
	 * @param resultId chunk identifier
	 */
	private void deleteResultDir(final String resultId) {
		final Path dir = Paths.get(properties.getBaseTempDir(), resultId);
		try {
			if (!Files.exists(dir)) {
				return;
			}
			Files
				.walk(dir)
				.sorted((p1, p2) -> p2.getNameCount() - p1.getNameCount())
				.forEach(path -> {
					try {
						Files.deleteIfExists(path);
					} catch (IOException e) {
						log.debug("Failed to delete path {} during cleanup: {}", path, e.getMessage());
					}
				});
			log.info("Cleaned up chunk directory for resultId={}", resultId);
		} catch (IOException e) {
			log.warn("Failed to cleanup chunk directory {}: {}", dir, e.getMessage());
		} finally {
			cleanupTasks.remove(resultId);
		}
	}

	/**
	 * Build the filename used to locate a specific page.
	 *
	 * @param resultNumber requested page index
	 * @return filename with ".json" extension
	 */
	private static String pageName(final Integer resultNumber) {
		if (resultNumber == null || resultNumber < 0) {
			return "0.json";
		}
		return resultNumber + ".json";
	}

	/**
	 * Thread factory used for background cleanup tasks.
	 */
	private static class DaemonThreadFactory implements ThreadFactory {

		/**
		 * Create a daemon thread for cleanup tasks with a stable name and exception handler.
		 *
		 * @param r runnable to execute
		 * @return configured daemon thread
		 */
		@Override
		public Thread newThread(Runnable r) {
			final Thread t = Executors.defaultThreadFactory().newThread(r);
			t.setDaemon(true);
			t.setName("chunk-cleanup-" + t.getId());
			t.setUncaughtExceptionHandler((thr, ex) -> log.warn("Cleanup thread error: {}", ex.toString()));
			return t;
		}
	}
}
