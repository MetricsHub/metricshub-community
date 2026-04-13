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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.metricshub.web.config.OpenAiToolOutputProperties;
import org.metricshub.web.dto.openai.PersistedToolOutputFile;
import org.springframework.stereotype.Service;

/**
 * Persists oversized tool outputs to disk with atomic write.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ToolOutputFilePersistService {

	private static final String JSON_EXTENSION = ".json";

	private final OpenAiToolOutputProperties properties;

	/**
	 * Persist the oversized tool output under the base temp directory using the result id as filename.
	 *
	 * @param toolName       tool name (for logging/traceability)
	 * @param toolResultJson tool output JSON
	 * @return metadata about the persisted file
	 */
	public PersistedToolOutputFile persist(final String toolName, final String toolResultJson) {
		final String resultId = UUID.randomUUID().toString();
		final Path dir = Paths.get(properties.getBaseTempDir());
		final Path tmp = dir.resolve(resultId + JSON_EXTENSION + ".tmp");
		final Path dest = dir.resolve(resultId + JSON_EXTENSION);
		try {
			Files.createDirectories(dir);
			final byte[] bytes = toolResultJson.getBytes(StandardCharsets.UTF_8);
			writeAtomic(tmp, dest, bytes);
			log.debug(
				"Persisted oversized tool output for tool '{}' with result ID '{}' to '{}'. File size: {} bytes",
				toolName,
				resultId,
				dest.toAbsolutePath(),
				bytes.length
			);
			return PersistedToolOutputFile
				.builder()
				.resultId(resultId)
				.absolutePath(dest.toAbsolutePath().toString())
				.sizeBytes(bytes.length)
				.toolName(toolName)
				.build();
		} catch (IOException e) {
			throw new IllegalStateException("Failed to persist oversized tool output", e);
		} finally {
			try {
				Files.deleteIfExists(tmp);
			} catch (IOException e) {
				log.warn("Failed to delete temporary tool output file: {}", tmp.toAbsolutePath());
				log.debug("Exception details:", e);
			}
		}
	}

	/**
	 * Atomic write via temp file then move, falling back to non-atomic move if not supported.
	 *
	 * @param tmp   temporary file path
	 * @param dest  destination file path
	 * @param bytes content to write
	 */
	private static void writeAtomic(final Path tmp, final Path dest, final byte[] bytes) {
		try {
			Files.write(tmp, bytes, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
			move(tmp, dest);
		} catch (IOException e) {
			throw new IllegalStateException("Failed to persist tool output", e);
		}
	}

	/**
	 * Move file atomically if supported, else non-atomically.
	 *
	 * @param tmp  temporary file path
	 * @param dest destination file path
	 * @throws IOException if move fails
	 */
	private static void move(final Path tmp, final Path dest) throws IOException {
		try {
			Files.move(tmp, dest, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
		} catch (Exception ex) {
			Files.move(tmp, dest, StandardCopyOption.REPLACE_EXISTING);
		}
	}
}
