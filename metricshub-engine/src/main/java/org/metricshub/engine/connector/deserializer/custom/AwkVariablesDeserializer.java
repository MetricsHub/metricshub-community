package org.metricshub.engine.connector.deserializer.custom;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * MetricsHub Engine
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

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Deserializes the <code>variables</code> of an Awk source or compute: a map of variable name to value, where a value
 * is a scalar, read as a {@link String} exactly as a <code>Map&lt;String, String&gt;</code> would read it, a YAML list,
 * read as a {@link List}, or a YAML map, read as a {@link LinkedHashMap} keyed by {@link String}. Lists and maps nest,
 * and a <code>null</code> value (<code>myVariable:</code>) stays <code>null</code>.
 */
public class AwkVariablesDeserializer extends JsonDeserializer<Map<String, Object>> {

	@Override
	public Map<String, Object> deserialize(final JsonParser parser, final DeserializationContext context)
		throws IOException {
		if (!parser.isExpectedStartObjectToken()) {
			@SuppressWarnings("unchecked")
			final Map<String, Object> unexpected = (Map<String, Object>) context.handleUnexpectedToken(Map.class, parser);
			return unexpected;
		}
		return readMap(parser, context);
	}

	/**
	 * Copies the variables of an Awk source or compute deeply: every list and map, at any depth, is a new one, so that
	 * the copy shares no container with the original.
	 *
	 * @param variables The variables, each value a scalar, a {@link List} or a {@link Map}
	 * @return the copy, in the same order
	 */
	public static Map<String, Object> copy(final Map<String, ?> variables) {
		final Map<String, Object> copy = new LinkedHashMap<>();
		variables.forEach((name, value) -> copy.put(name, copyValue(value)));
		return copy;
	}

	/**
	 * Copies one value deeply.
	 *
	 * @param value A scalar, a {@link List}, a {@link Map}, or <code>null</code>
	 * @return a new list or map, or the scalar itself
	 */
	private static Object copyValue(final Object value) {
		if (value instanceof List<?> list) {
			final List<Object> copy = new ArrayList<>(list.size());
			list.forEach(item -> copy.add(copyValue(item)));
			return copy;
		}
		if (value instanceof Map<?, ?> map) {
			final Map<String, Object> copy = new LinkedHashMap<>();
			map.forEach((key, item) -> copy.put(String.valueOf(key), copyValue(item)));
			return copy;
		}
		return value;
	}

	/**
	 * Reads one value, the parser being on its first token.
	 *
	 * @param parser  The parser
	 * @param context The deserialization context
	 * @return the value: a {@link String}, a {@link List}, a {@link Map}, or <code>null</code>
	 * @throws IOException when the value cannot be read
	 */
	private static Object readValue(final JsonParser parser, final DeserializationContext context) throws IOException {
		final JsonToken token = parser.currentToken();
		if (token == JsonToken.START_ARRAY) {
			final List<Object> list = new ArrayList<>();
			while (parser.nextToken() != JsonToken.END_ARRAY) {
				list.add(readValue(parser, context));
			}
			return list;
		}
		if (token == JsonToken.START_OBJECT) {
			return readMap(parser, context);
		}
		if (token == JsonToken.VALUE_NULL) {
			return null;
		}
		if (token.isScalarValue()) {
			return parser.getText();
		}
		return context.handleUnexpectedToken(Object.class, parser);
	}

	/**
	 * Reads a map, the parser being on its start.
	 *
	 * @param parser  The parser, on the start of the map
	 * @param context The deserialization context
	 * @return the map, in the declared order
	 * @throws IOException when a value cannot be read
	 */
	private static Map<String, Object> readMap(final JsonParser parser, final DeserializationContext context)
		throws IOException {
		final Map<String, Object> map = new LinkedHashMap<>();
		for (String name = parser.nextFieldName(); name != null; name = parser.nextFieldName()) {
			parser.nextToken();
			map.put(name, readValue(parser, context));
		}
		return map;
	}
}
