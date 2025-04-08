package org.metricshub.engine.common.helpers;

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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.TreeNode;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator.Feature;
import java.io.IOException;
import java.io.InputStream;

/**
 * Helper class for JSON and YAML serialization and deserialization using Jackson.
 */
public class JsonHelper {

	private JsonHelper() {}

	/**
	 * Serialize the Java value to a JSON String
	 *
	 * @param <T>    The element type
	 * @param value the value object to serialize.
	 * @return a string containing the object serialized in JSON format.
	 */
	public static <T> String serialize(final T value) {
		try {
			return buildObjectMapper().writeValueAsString(value);
		} catch (JsonProcessingException e) {
			return "{}";
		}
	}

	/**
	 * Deserialize the JSON string to a Java value.
	 *
	 * @param <T>    The element type
	 * @param json  The JSON value we wish to deserialize.
	 * @param clazz The target Java type.
	 * @return new Object.
	 * @throws IOException if an error occurs during deserialization.
	 */
	public static <T> T deserialize(final String json, final Class<T> clazz) throws IOException {
		return buildObjectMapper().readValue(json, clazz);
	}

	/**
	 * Deserialize the given input stream.
	 *
	 * @param <T>    The element type
	 * @param mapper {@link ObjectMapper} instance used to deserialize input stream.
	 * @param input  {@link InputStream} instance.
	 * @param type   Java type.
	 * @return new instance of T.
	 * @throws IOException if an error occurs during deserialization.
	 */
	public static <T> T deserialize(final ObjectMapper mapper, final InputStream input, final Class<T> type)
		throws IOException {
		return mapper.readValue(input, type);
	}

	/**
	 * Deserialize the given input stream.
	 *
	 * @param <T>    The element type
	 * @param mapper {@link ObjectMapper} instance used to deserialize input stream.
	 * @param node   {@link TreeNode} instance.
	 * @param type   Java type.
	 * @return new instance of T.
	 * @throws IOException if an error occurs during deserialization.
	 */
	public static <T> T deserialize(final ObjectMapper mapper, final TreeNode node, final Class<T> type)
		throws IOException {
		return mapper.treeToValue(node, type);
	}

	/**
	 * Deserialize and return the requested type using the InputStream.
	 *
	 * @param is    {@link InputStream} connection to the JSON.
	 * @param clazz The target Java type.
	 * @param <T>   The element type
	 * @return new Object.
	 * @throws IOException if an error occurs during deserialization.
	 */
	public static <T> T deserialize(final InputStream is, final Class<T> clazz) throws IOException {
		return buildObjectMapper().readValue(is, clazz);
	}

	/**
	 * Build and return a new {@link ObjectMapper} instance enabling indentation.
	 *
	 * @return new {@link ObjectMapper} instance.
	 */
	public static ObjectMapper buildObjectMapper() {
		// Since 2.13 use JsonMapper.builder().enable(...)
		return JsonMapper
			.builder()
			.enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS)
			.enable(SerializationFeature.INDENT_OUTPUT)
			.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false)
			.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
			.configure(DeserializationFeature.FAIL_ON_INVALID_SUBTYPE, false)
			.build();
	}

	/**
	 * Build a new {@link ObjectMapper} using {@link YAMLFactory}.
	 *
	 * @return new {@link ObjectMapper} instance.
	 */
	public static ObjectMapper buildYamlMapper() {
		return JsonMapper
			.builder(new YAMLFactory().disable(Feature.SPLIT_LINES).enable(Feature.MINIMIZE_QUOTES))
			.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
			.enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS)
			.enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES)
			.enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_VALUES)
			.build();
	}

	/**
	 * Checks if a JsonNode is not null and does not represent a JSON null value.
	 *
	 * @param jsonNode the JsonNode to check
	 * @return {@code true} if the JsonNode is not null and not a JSON null; {@code false} otherwise
	 */
	public static boolean isNotNull(final JsonNode jsonNode) {
		return jsonNode != null && !jsonNode.isNull();
	}
}
