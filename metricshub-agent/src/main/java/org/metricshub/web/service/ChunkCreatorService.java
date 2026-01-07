package org.metricshub.web.service;

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
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.metricshub.engine.telemetry.Monitor;
import org.metricshub.engine.telemetry.MonitorsVo;
import org.metricshub.web.config.OpenAiTelemetryChunkingProperties;
import org.metricshub.web.dto.ChunkCreationResult;
import org.metricshub.web.dto.TelemetryResult;
import org.metricshub.web.mcp.HostToolResponse;
import org.metricshub.web.mcp.MultiHostToolResponse;
import org.springframework.stereotype.Service;

/**
 * Splits oversized telemetry tool outputs into paginated JSON files stored in a temporary directory.
 * <p>
 * Pages are created per host, and for a given host the {@code telemetry.monitors} list is sliced across consecutive pages.
 * Each page is guaranteed to be at most {@code authorizedLimitBytes} when serialized to UTF-8 JSON.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ChunkCreatorService {

	private static final String HAS_MORE_KEY = "has_more";

	private static final String NEXT_RESULT_KEY = "next_result";

	private static final TypeReference<MultiHostToolResponse<TelemetryResult>> TELEMETRY_TOOL_RESPONSE =
		new TypeReference<>() {};

	private final ObjectMapper objectMapper;
	private final OpenAiTelemetryChunkingProperties properties;

	/**
	 * Create paginated JSON files in {@code <baseTempDir>/<resultId>/} for the provided tool result JSON.
	 *
	 * @param toolResultJson        the original tool output JSON (must deserialize to {@code MultiHostToolResponse<TelemetryResult>})
	 * @param authorizedLimitBytes  maximum UTF-8 byte size per page
	 * @return chunk creation result containing the generated result id and page count
	 */
	public ChunkCreationResult createTelemetryResultChunks(final String toolResultJson, final long authorizedLimitBytes) {
		// Generate unique storage namespace per tool call.
		final String resultId = UUID.randomUUID().toString();
		final Path resultDir = Paths.get(properties.getBaseTempDir(), resultId);

		try {
			Files.createDirectories(resultDir);
		} catch (IOException e) {
			throw new IllegalStateException("Failed to create chunk directory: " + resultDir, e);
		}

		// Parse the original tool payload to reuse existing DTOs and avoid re-shaping.
		final MultiHostToolResponse<TelemetryResult> response;
		try {
			response = objectMapper.readValue(toolResultJson, TELEMETRY_TOOL_RESPONSE);
		} catch (Exception e) {
			throw new IllegalArgumentException("Tool result JSON is not a valid MultiHostToolResponse<TelemetryResult>", e);
		}

		// Track where we are writing and which page will be patched as the last one.
		int pageIndex = 0;
		int lastPageIndex = -1;

		final List<HostToolResponse<TelemetryResult>> hosts = response.getHosts() == null ? List.of() : response.getHosts();
		for (HostToolResponse<TelemetryResult> hostResponse : hosts) {
			final String hostname = hostResponse == null ? null : hostResponse.getHostname();
			final TelemetryResult telemetryResult = hostResponse == null ? null : hostResponse.getResponse();

			final MonitorsVo telemetry = telemetryResult == null ? null : telemetryResult.telemetry();
			final List<Monitor> monitors = telemetry == null || telemetry.getMonitors() == null
				? List.of()
				: telemetry.getMonitors();
			final int total = telemetry == null ? 0 : telemetry.getTotal();
			final String errorMessage = telemetryResult == null ? null : telemetryResult.errorMessage();

			if (monitors.isEmpty()) {
				// Preserve host entry even when it has no monitors to stream.
				final ObjectNode page = buildPageNode(resultId, hostname, total, List.of(), errorMessage, pageIndex + 1, true);
				final byte[] bytes = serialize(page);
				ensureWithinLimit(bytes, authorizedLimitBytes, "empty monitors page");
				atomicWrite(resultDir, pageIndex, bytes);
				lastPageIndex = pageIndex;
				pageIndex++;
				continue;
			}

			int start = 0;
			while (start < monitors.size()) {
				final ObjectNode singleMonitorPage = buildPageNode(
					resultId,
					hostname,
					total,
					List.of(monitors.get(start)),
					errorMessage,
					pageIndex + 1,
					true
				);
				final byte[] singleBytes = serialize(singleMonitorPage);
				if (singleBytes.length > authorizedLimitBytes) {
					log.warn(
						"Cannot chunk telemetry output: one monitor entry exceeds page limit (limit={} bytes, monitorBytes={})",
						authorizedLimitBytes,
						singleBytes.length
					);

					// Return a single page with an explicit error when even one monitor cannot fit.
					final String tooLargeMessage = "One monitor entry exceeds the allowed OpenAI tool output page size";
					final ObjectNode errorPage = buildPageNode(
						resultId,
						hostname,
						total,
						List.of(),
						tooLargeMessage,
						null,
						false
					);
					final byte[] errorBytes = serialize(errorPage);
					ensureWithinLimit(errorBytes, authorizedLimitBytes, "oversized monitor error page");
					atomicWrite(resultDir, pageIndex, errorBytes);
					return ChunkCreationResult.builder().resultId(resultId).pageCount(pageIndex + 1).build();
				}

				int endExclusive = start + 1;
				int bestEndExclusive = endExclusive;
				byte[] bestBytes = singleBytes;

				while (endExclusive < monitors.size()) {
					// Greedily pack monitors until adding the next would breach the byte budget.
					final List<Monitor> candidateSlice = monitors.subList(start, endExclusive + 1);
					final ObjectNode candidatePage = buildPageNode(
						resultId,
						hostname,
						total,
						candidateSlice,
						errorMessage,
						pageIndex + 1,
						true
					);
					final byte[] candidateBytes = serialize(candidatePage);
					if (candidateBytes.length > authorizedLimitBytes) {
						break;
					}
					bestEndExclusive = endExclusive + 1;
					bestBytes = candidateBytes;
					endExclusive++;
				}

				// Persist the best-fitting slice for this page.
				atomicWrite(resultDir, pageIndex, bestBytes);
				lastPageIndex = pageIndex;
				pageIndex++;
				start = bestEndExclusive;
			}
		}

		if (pageIndex == 0) {
			// Handle the degenerate case: no hosts produced any output.
			final ObjectNode emptyPage = buildEmptyPage(resultId);
			final byte[] bytes = serialize(emptyPage);
			ensureWithinLimit(bytes, authorizedLimitBytes, "empty result page");
			atomicWrite(resultDir, 0, bytes);
			lastPageIndex = 0;
			pageIndex = 1;
		}

		if (lastPageIndex >= 0) {
			// Rewrite the last page to signal completion (has_more=false).
			final Path lastPagePath = resultDir.resolve(lastPageIndex + ".json");
			final ObjectNode last = readAsObjectNode(lastPagePath);
			last.set(NEXT_RESULT_KEY, JsonNodeFactory.instance.nullNode());
			last.put(HAS_MORE_KEY, false);
			final byte[] bytes = serialize(last);
			ensureWithinLimit(bytes, authorizedLimitBytes, "final page after rewrite");
			atomicWriteRaw(lastPagePath, bytes);
		}

		return ChunkCreationResult.builder().resultId(resultId).pageCount(pageIndex).build();
	}

	/**
	 * Build an empty page when no hosts are present.
	 *
	 * @param resultId chunked response identifier
	 * @return page node with no hosts and has_more=false
	 */
	private ObjectNode buildEmptyPage(final String resultId) {
		final ObjectNode root = JsonNodeFactory.instance.objectNode();
		root.put("result_id", resultId);
		root.putArray("hosts");
		root.set(NEXT_RESULT_KEY, JsonNodeFactory.instance.nullNode());
		root.put(HAS_MORE_KEY, false);
		return root;
	}

	/**
	 * Build a page payload for a single host with a specific monitor slice.
	 *
	 * @param resultId      chunked response identifier
	 * @param hostname      host name for the page
	 * @param total         total monitors count reported by the source telemetry
	 * @param monitorsSlice monitors included in this page
	 * @param errorMessage  optional error message to propagate
	 * @param nextResult    next page index or null for last page
	 * @param hasMore       whether more pages follow
	 * @return page node ready to serialize
	 */
	private ObjectNode buildPageNode(
		final String resultId,
		final String hostname,
		final int total,
		final List<Monitor> monitorsSlice,
		final String errorMessage,
		final Integer nextResult,
		final boolean hasMore
	) {
		final ObjectNode root = JsonNodeFactory.instance.objectNode();
		root.put("result_id", resultId);

		final ArrayNode hostsNode = root.putArray("hosts");
		final ObjectNode hostNode = hostsNode.addObject();
		hostNode.put("hostname", hostname == null ? "" : hostname);

		final ObjectNode telemetryNode = hostNode.putObject("telemetry");
		telemetryNode.put("total", total);
		final JsonNode monitorsNode = objectMapper.valueToTree(monitorsSlice == null ? List.of() : monitorsSlice);
		telemetryNode.set("monitors", monitorsNode);

		if (errorMessage == null) {
			hostNode.set("errorMessage", JsonNodeFactory.instance.nullNode());
		} else {
			hostNode.put("errorMessage", errorMessage);
		}

		if (nextResult == null) {
			root.set(NEXT_RESULT_KEY, JsonNodeFactory.instance.nullNode());
		} else {
			root.put(NEXT_RESULT_KEY, nextResult);
		}
		root.put(HAS_MORE_KEY, hasMore);
		return root;
	}

	/**
	 * Ensure the serialized page size respects the configured byte limit.
	 *
	 * @param bytes   serialized page bytes
	 * @param limit   maximum allowed size
	 * @param context diagnostic context used in error messages
	 */
	private static void ensureWithinLimit(final byte[] bytes, final long limit, final String context) {
		if (bytes.length > limit) {
			throw new IllegalStateException("Chunk page exceeds limit (" + context + "): " + bytes.length + " > " + limit);
		}
	}

	/**
	 * Serialize a JSON node using the shared {@link ObjectMapper}.
	 *
	 * @param node JSON to serialize
	 * @return UTF-8 encoded bytes
	 */
	private byte[] serialize(final JsonNode node) {
		try {
			return objectMapper.writeValueAsBytes(node);
		} catch (Exception e) {
			throw new IllegalStateException("Failed to serialize chunk page", e);
		}
	}

	/**
	 * Read and parse a page file into an {@link ObjectNode}.
	 *
	 * @param path path to the JSON file
	 * @return parsed object node
	 */
	private ObjectNode readAsObjectNode(final Path path) {
		try {
			return (ObjectNode) objectMapper.readTree(Files.readString(path, StandardCharsets.UTF_8));
		} catch (Exception e) {
			throw new IllegalStateException("Failed to read/parse chunk page: " + path, e);
		}
	}

	/**
	 * Write a page file by delegating to {@link #atomicWriteRaw(Path, byte[])} with a numbered filename.
	 *
	 * @param dir       target directory
	 * @param pageIndex page index used as filename
	 * @param bytes     serialized page bytes
	 */
	private void atomicWrite(final Path dir, final int pageIndex, final byte[] bytes) {
		final Path dest = dir.resolve(pageIndex + ".json");
		atomicWriteRaw(dest, bytes);
		log.debug("Wrote chunk page {} ({} bytes)", dest, bytes.length);
	}

	/**
	 * Atomically write bytes to a destination path via a temporary file to avoid partial writes.
	 *
	 * @param dest  final destination path
	 * @param bytes content to write
	 */
	private static void atomicWriteRaw(final Path dest, final byte[] bytes) {
		final Path tmp = Paths.get(dest.toString() + ".tmp");
		try {
			Files.write(tmp, bytes, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
			moveFile(dest, tmp);
		} catch (IOException e) {
			throw new IllegalStateException("Failed to write chunk page: " + dest, e);
		} finally {
			try {
				Files.deleteIfExists(tmp);
			} catch (IOException ignored) {
				// Ignore
			}
		}
	}

	/**
	 * Atomically move a temporary file to its final destination.
	 *
	 * @param dest final destination path
	 * @param tmp  temporary source path
	 * @throws IOException if an I/O error occurs
	 */
	private static void moveFile(final Path dest, final Path tmp) throws IOException {
		try {
			Files.move(tmp, dest, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
		} catch (AtomicMoveNotSupportedException ex) {
			// Best-effort: fall back to a regular move while still ensuring the temporary file is complete before replacing.
			Files.move(tmp, dest, StandardCopyOption.REPLACE_EXISTING);
		}
	}
}
