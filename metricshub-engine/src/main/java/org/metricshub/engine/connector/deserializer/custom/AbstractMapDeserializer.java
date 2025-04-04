package org.metricshub.engine.connector.deserializer.custom;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * MetricsHub Engine
 * ჻჻჻჻჻჻
 * Copyright 2023 - 2024 Sentry Software
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
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import java.io.IOException;
import java.util.Map;

/**
 * An abstract class providing deserialization support for custom types using Jackson's ObjectMapper
 * and representing the deserialization of a {@link Map} instance.
 *
 * @param <T> The type of values stored in the map.
 */
public abstract class AbstractMapDeserializer<T> extends JsonDeserializer<Map<String, T>> {

	@Override
	public Map<String, T> deserialize(JsonParser parser, DeserializationContext ctxt) throws IOException {
		if (parser == null) {
			return emptyMap();
		}

		final Map<String, T> map = parser.readValueAs(getTypeReference());

		if (map == null) {
			return emptyMap();
		}

		if (!isValidMap(map)) {
			throw new InvalidFormatException(parser, messageOnInvalidMap(parser.currentName()), map, Map.class);
		}

		if (isExpectedInstance(map)) {
			return map;
		}

		return fromMap(map);
	}

	/**
	 * Get the error message to display when the map is invalid
	 *
	 * @param nodeKey the current node key. E.g. sources
	 * @return String value
	 */
	protected abstract String messageOnInvalidMap(String nodeKey);

	/**
	 * Builds a map from the map passed as argument
	 *
	 * @param map
	 * @return new {@link Map}
	 */
	protected abstract Map<String, T> fromMap(Map<String, T> map);

	/**
	 * Whether the map is expected or not
	 *
	 * @param map
	 * @return <code>true</code> if the map is in an expected type otherwise
	 * false.
	 */
	protected abstract boolean isExpectedInstance(Map<String, T> map);

	/**
	 * Create a new empty map
	 *
	 * @return new {@link Map}
	 */
	protected abstract Map<String, T> emptyMap();

	/**
	 * Whether the given map is valid or not
	 *
	 * @param map
	 * @return <code>true</code> if the map is valid otherwise false
	 */
	protected abstract boolean isValidMap(Map<String, T> map);

	/**
	 * Create the Java type to read content as (passed to ObjectCodec that
	 * deserializes content)
	 *
	 * @return a new {@link TypeReference}
	 */
	protected abstract TypeReference<Map<String, T>> getTypeReference();
}
