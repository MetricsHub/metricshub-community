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

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.openai.core.JsonValue;
import com.openai.models.responses.FunctionTool;
import com.openai.models.responses.FunctionTool.Parameters;
import com.openai.models.responses.Tool;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.NoArgsConstructor;
import org.springframework.ai.model.ModelOptionsUtils;
import org.springframework.ai.tool.ToolCallback;

/**
 * Converts Spring AI ToolCallbacks into OpenAI Responses function tools.
 *
 * This class is intentionally "dumb": no caching, no Spring annotations.
 * Build once at startup (bean creation) and reuse the resulting List&lt;Tool&gt; as needed.
 */
@NoArgsConstructor(access = lombok.AccessLevel.PRIVATE)
public final class SpringToolsToOpenAiTools {

	private static final String DEFAULT_SCHEMA = "{\"type\":\"object\",\"properties\":{}}";
	private static final TypeReference<Map<String, JsonNode>> MAP = new TypeReference<>() {};

	/**
	 * Builds a list of OpenAI Tools from the given array of ToolCallbacks.
	 * @param callbacks the array of ToolCallbacks
	 * @return the list of OpenAI Tools
	 */
	public static List<Tool> buildOpenAiTools(final ToolCallback[] callbacks) {
		if (callbacks == null || callbacks.length == 0) {
			return List.of();
		}

		List<Tool> tools = new ArrayList<>(callbacks.length);

		for (ToolCallback cb : callbacks) {
			var def = cb.getToolDefinition();

			String schemaJson = def.inputSchema();
			if (schemaJson.isBlank()) {
				schemaJson = DEFAULT_SCHEMA;
			}

			Map<String, JsonNode> schema;
			try {
				schema = ModelOptionsUtils.OBJECT_MAPPER.readValue(schemaJson, MAP);
			} catch (Exception e) {
				throw new IllegalStateException("Invalid JSON schema for tool: " + def.name(), e);
			}

			var description = def.description().isBlank() ? def.name() : def.description();

			var functionTool = FunctionTool
				.builder()
				.name(def.name())
				.description(description)
				.strict(false)
				.parameters(buildParametersFromSchema(schema))
				.build();
			tools.add(Tool.ofFunction(functionTool));
		}

		return List.copyOf(tools);
	}

	/**
	 * Builds OpenAI Parameters from a JSON schema represented as a Map.
	 *
	 * @param schema the JSON schema as a Map
	 * @return the Parameters object
	 */
	private static Parameters buildParametersFromSchema(final Map<String, JsonNode> schema) {
		Map<String, ? extends JsonValue> additionalProperties = schema
			.entrySet()
			.stream()
			.collect(
				HashMap::new,
				(map, entry) -> map.put(entry.getKey(), JsonValue.fromJsonNode(entry.getValue())),
				HashMap::putAll
			);
		return Parameters.builder().additionalProperties(additionalProperties).build();
	}
}
