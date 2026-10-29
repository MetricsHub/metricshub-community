package org.metricshub.web.mcp.tool;

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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.function.Function;
import lombok.Getter;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;

/**
 * A tool built at runtime rather than from an annotated method: its definition and a function from the JSON
 * arguments to the JSON-serializable result. The connector and protocol tools, which depend on the agent
 * context, are built this way.
 */
public final class DynamicToolCallback implements ToolCallback {

	private static final ObjectMapper MAPPER = new ObjectMapper();

	@Getter
	private final ToolDefinition toolDefinition;

	private final Function<JsonNode, Object> function;

	/**
	 * @param toolDefinition the name, description and input schema advertised to the LLM
	 * @param function       the function run on the parsed arguments, whose result is serialized to JSON
	 */
	public DynamicToolCallback(final ToolDefinition toolDefinition, final Function<JsonNode, Object> function) {
		this.toolDefinition = toolDefinition;
		this.function = function;
	}

	@Override
	public String call(final String toolInput) {
		final JsonNode arguments;
		try {
			arguments = toolInput == null || toolInput.isBlank() ? MAPPER.createObjectNode() : MAPPER.readTree(toolInput);
		} catch (JsonProcessingException e) {
			throw new IllegalArgumentException(
				"Tool %s: the arguments are not valid JSON: %s".formatted(toolDefinition.name(), e.getOriginalMessage())
			);
		}
		try {
			return MAPPER.writeValueAsString(function.apply(arguments));
		} catch (JsonProcessingException e) {
			throw new IllegalStateException("Tool %s: the result cannot be serialized".formatted(toolDefinition.name()), e);
		}
	}
}
