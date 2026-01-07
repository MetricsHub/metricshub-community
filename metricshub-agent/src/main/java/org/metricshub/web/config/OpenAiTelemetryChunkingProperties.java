package org.metricshub.web.config;

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

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties controlling how oversized OpenAI tool outputs are handled for telemetry troubleshooting tools.
 * <p>
 * When an enabled tool output exceeds {@code maxToolOutputBytes - safetyDeltaBytes}, the output is chunked and replaced by a
 * manifest instructing the assistant to fetch pages through the {@code FetchResponseChunk} tool.
 */
@ConfigurationProperties(prefix = "ai.openai.telemetry-chunking")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class OpenAiTelemetryChunkingProperties {

	/**
	 * Maximum number of UTF-8 bytes allowed for an OpenAI tool output.
	 */
	private long maxToolOutputBytes = 10_485_760;

	/**
	 * Safety margin applied before chunking to avoid hitting hard OpenAI limits.
	 */
	private long safetyDeltaBytes = 2_097_152;

	/**
	 * Delay after the last chunk is fetched before cleaning up temporary chunk files.
	 */
	private long cleanupDelaySeconds = 15;

	/**
	 * Base directory where chunk pages are written.
	 */
	private String baseTempDir = System.getProperty("java.io.tmpdir") + "/metricshub/ai";

	/**
	 * Tool names for which telemetry output chunking is enabled.
	 */
	private List<String> enabledToolNames = List.of(
		"CollectMetricsForHost",
		"GetMetricsFromCacheForHost",
		"TestAvailableConnectorsForHost"
	);
}
