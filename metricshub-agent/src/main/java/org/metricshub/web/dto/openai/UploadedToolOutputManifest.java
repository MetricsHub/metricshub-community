package org.metricshub.web.dto.openai;

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
import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Builder.Default;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Manifest pointing to a tool output file uploaded to OpenAI
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UploadedToolOutputManifest {

	@Default
	private String type = "tool_output_manifest";

	@JsonProperty("openai_file_id")
	private String openaiFileId;

	@JsonProperty("file_name")
	private String fileName;

	@Default
	private String description =
		"Tool output was too large to include directly and has been omitted from the payload. " +
		"The full data was uploaded to OpenAI Files API. " +
		"Use Code Interpreter to analyze the file using openai_file_id. Do not request raw JSON chunks.";

	/**
	 * The tool output payload: full JSON tree when under the size limit,
	 * truncated JSON tree for troubleshooting tools that exceed the limit,
	 * or {@code null} for non-troubleshooting tools that exceed the limit.
	 */
	private JsonNode payload;
}
