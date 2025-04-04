package org.metricshub.agent.deserialization;

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

import static org.metricshub.engine.common.helpers.MetricsHubConstants.MULTI_VALUE_SEPARATOR;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;
import org.metricshub.engine.common.helpers.StringHelper;

/**
 * AttributesDeserializer is a custom JSON deserializer for converting JSON attributes into a {@link Map<String, String>} format.
 * It parses a JSON object, deserializes its attributes, and converts them into a map where keys are strings and values are stringified representations.
 */
public class AttributesDeserializer extends JsonDeserializer<Map<String, String>> {

	/**
	 * Deserialize JSON attributes into a {@link Map<String, String>} format.
	 *
	 * @param parser  JSON parser.
	 * @param context Deserialization context.
	 * @return A map representing the deserialized attributes.
	 * @throws IOException If an I/O error occurs during deserialization.
	 */
	@Override
	public Map<String, String> deserialize(JsonParser parser, DeserializationContext context) throws IOException {
		if (parser == null) {
			return new HashMap<>();
		}

		final Map<String, Object> deserializedAttributes = parser.readValueAs(new TypeReference<Map<String, Object>>() {});

		return deserializedAttributes
			.entrySet()
			.stream()
			.collect(
				Collectors.toMap(
					Map.Entry::getKey,
					value -> StringHelper.stringify(value.getValue(), MULTI_VALUE_SEPARATOR),
					(oldValue, newValue) -> oldValue,
					LinkedHashMap::new
				)
			);
	}
}
