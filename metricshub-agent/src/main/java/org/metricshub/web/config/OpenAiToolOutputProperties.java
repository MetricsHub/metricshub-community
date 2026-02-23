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

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Properties controlling generic tool-output handling for oversized payloads.
 */
@ConfigurationProperties(prefix = "ai.openai.tool-output")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class OpenAiToolOutputProperties {

	/**
	 * 1048576 bytes = 1 MiB
	 * Which is a reasonable default limit for tool outputs before
	 * we consider them for truncation or file storage.
	 */
	private long maxToolOutputBytes = 1048576;

	/**
	 * 262144 bytes = 256 KiB
	 * Additional safety buffer to account for JSON encoding overhead and ensure we stay within OpenAI's limits.
	 */
	private long safetyDeltaBytes = 262144;

	private String baseTempDir = System.getProperty("java.io.tmpdir") + "/metricshub/ai";
}
