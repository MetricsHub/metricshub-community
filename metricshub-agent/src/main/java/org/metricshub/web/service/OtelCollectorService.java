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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.metricshub.agent.config.otel.OtelCollectorConfig;
import org.metricshub.agent.context.AgentContext;
import org.metricshub.agent.service.OtelCollectorProcessService;
import org.metricshub.web.AgentContextHolder;
import org.metricshub.web.exception.OtelCollectorException;
import org.springframework.stereotype.Service;

/**
 * Service for controlling the OpenTelemetry Collector process and reading its logs.
 */
@Slf4j
@Service
public class OtelCollectorService {

	/**
	 * Maximum bytes to read from the end of the log file when tailing by lines.
	 */
	private static final long MAX_TAIL_BYTES = 512 * 1024;

	/**
	 * Default number of lines to return when tailing logs.
	 */
	public static final int DEFAULT_TAIL_LINES = 200;

	private final AgentContextHolder agentContextHolder;

	/**
	 * Constructor for OtelCollectorService.
	 *
	 * @param agentContextHolder the holder for the current agent context
	 */
	public OtelCollectorService(final AgentContextHolder agentContextHolder) {
		this.agentContextHolder = agentContextHolder;
	}

	/**
	 * Restarts the OpenTelemetry Collector process (stop then launch).
	 *
	 * @throws OtelCollectorException if the agent context is unavailable or restart fails
	 */
	public void restartCollector() throws OtelCollectorException {
		final AgentContext context = agentContextHolder.getAgentContext();
		if (context == null) {
			throw new OtelCollectorException(
				OtelCollectorException.Code.CONTEXT_UNAVAILABLE,
				"Agent context is not available."
			);
		}

		final OtelCollectorProcessService processService = context.getOtelCollectorProcessService();
		if (processService == null) {
			throw new OtelCollectorException(
				OtelCollectorException.Code.RESTART_FAILED,
				"OpenTelemetry Collector process service is not available."
			);
		}

		try {
			log.info("Restarting OpenTelemetry Collector.");
			processService.stop();
			processService.launch();
			log.info("OpenTelemetry Collector restarted successfully.");
		} catch (Exception e) {
			log.error("Failed to restart OpenTelemetry Collector: {}", e.getMessage());
			log.debug("Failed to restart OpenTelemetry Collector.", e);
			throw new OtelCollectorException(
				OtelCollectorException.Code.RESTART_FAILED,
				"Failed to restart OpenTelemetry Collector: " + e.getMessage(),
				e
			);
		}
	}

	/**
	 * Returns the last N lines of the OpenTelemetry Collector log file.
	 *
	 * @param tailLines maximum number of lines to return (default used if &lt;= 0)
	 * @return the log tail as a single string (lines separated by newline)
	 * @throws OtelCollectorException if the output directory is not configured, no log file is
	 *                                found, or an IO error occurs
	 */
	public String getCollectorLogTail(int tailLines) throws OtelCollectorException {
		if (tailLines <= 0) {
			tailLines = DEFAULT_TAIL_LINES;
		}

		final AgentContext context = agentContextHolder.getAgentContext();
		if (context == null) {
			throw new OtelCollectorException(
				OtelCollectorException.Code.CONTEXT_UNAVAILABLE,
				"Agent context is not available."
			);
		}

		final String outputDirStr = context.getAgentConfig() != null ? context.getAgentConfig().getOutputDirectory() : null;
		if (outputDirStr == null || outputDirStr.isBlank()) {
			throw new OtelCollectorException(
				OtelCollectorException.Code.OUTPUT_DIR_NOT_CONFIGURED,
				"Output directory is not configured. Cannot read OTEL collector logs."
			);
		}

		final Path outputDir = Paths.get(outputDirStr);
		if (!Files.isDirectory(outputDir)) {
			throw new OtelCollectorException(
				OtelCollectorException.Code.LOG_FILE_NOT_FOUND,
				"Output directory does not exist or is not a directory: " + outputDir
			);
		}

		final String logId = OtelCollectorConfig.EXECUTABLE_OUTPUT_ID;
		final String prefix = logId + "-";

		try (Stream<Path> stream = Files.list(outputDir)) {
			final Path latestLog = stream
				.filter(Files::isRegularFile)
				.filter(p -> p.getFileName().toString().startsWith(prefix) && p.getFileName().toString().endsWith(".log"))
				.max(
					Comparator.comparingLong(p -> {
						try {
							return Files.getLastModifiedTime(p).toMillis();
						} catch (IOException e) {
							return 0L;
						}
					})
				)
				.orElse(null);

			if (latestLog == null) {
				throw new OtelCollectorException(
					OtelCollectorException.Code.LOG_FILE_NOT_FOUND,
					"No OpenTelemetry Collector log file found in " + outputDir + " (expected " + prefix + "*.log)."
				);
			}

			final long fileSize = Files.size(latestLog);
			final long bytesToRead = Math.min(MAX_TAIL_BYTES, fileSize);
			final long startPosition = fileSize - bytesToRead;

			try (var raf = new java.io.RandomAccessFile(latestLog.toFile(), "r")) {
				raf.seek(startPosition);
				final byte[] buffer = new byte[(int) bytesToRead];
				final int read = raf.read(buffer);
				if (read <= 0) {
					return "";
				}
				String tail = new String(buffer, 0, read, StandardCharsets.UTF_8);
				List<String> lines = java.util.Arrays.asList(tail.split("\n"));
				if (lines.size() > tailLines) {
					lines = lines.subList(lines.size() - tailLines, lines.size());
					tail = String.join("\n", lines);
				}
				return tail;
			}
		} catch (IOException e) {
			log.error("Failed to read OTEL collector log: {}", e.getMessage());
			log.debug("Failed to read OTEL collector log.", e);
			throw new OtelCollectorException(
				OtelCollectorException.Code.IO_FAILURE,
				"Failed to read OpenTelemetry Collector log: " + e.getMessage(),
				e
			);
		}
	}
}
