package org.metricshub.agent.m8b.protocol;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * MetricsHub Agent
 * ჻჻჻჻჻჻
 * Copyright 2023 - 2026 MetricsHub
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

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.UncheckedIOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.TreeSet;

/**
 * JSON (de)serialization of the tunnel protocol.
 * <p>
 * Unknown properties are ignored so a newer server can extend a message without breaking this agent,
 * and {@code null} values are omitted so the wire stays compact and stable.
 * </p>
 */
public final class M8bJson {

	/**
	 * Mapper used on the wire.
	 */
	public static final JsonMapper MAPPER = JsonMapper.builder()
		.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
		.serializationInclusion(Include.NON_NULL)
		.build();

	private M8bJson() {}

	/**
	 * Serializes a message to a single JSON text frame.
	 *
	 * @param message the message
	 * @return the JSON text
	 */
	public static String write(final M8bMessage message) {
		try {
			return MAPPER.writeValueAsString(message);
		} catch (JsonProcessingException e) {
			throw new UncheckedIOException(e);
		}
	}

	/**
	 * Parses a JSON text frame.
	 *
	 * @param json the frame content
	 * @return the message, {@link M8bMessage.Unknown} when the type is not known
	 * @throws JsonProcessingException when the frame is not a valid message
	 */
	public static M8bMessage read(final String json) throws JsonProcessingException {
		return MAPPER.readValue(json, M8bMessage.class);
	}

	/**
	 * Computes a fingerprint of a value: {@code sha256:} followed by the lowercase hex SHA-256 of its
	 * canonical JSON form (object keys sorted at every level, no nulls, no whitespace). Equal values
	 * yield equal fingerprints regardless of key order.
	 *
	 * @param value the value to fingerprint
	 * @return the fingerprint
	 */
	public static String fingerprint(final Object value) {
		try {
			final byte[] canonical = MAPPER.writeValueAsBytes(canonicalize(MAPPER.valueToTree(value)));
			final byte[] digest = MessageDigest.getInstance("SHA-256").digest(canonical);
			return "sha256:" + HexFormat.of().formatHex(digest);
		} catch (JsonProcessingException e) {
			throw new UncheckedIOException(e);
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("SHA-256 is not available", e);
		}
	}

	/**
	 * Rebuilds a tree with the object keys sorted at every level; arrays keep their order.
	 */
	static JsonNode canonicalize(final JsonNode node) {
		if (node.isObject()) {
			final ObjectNode sorted = MAPPER.createObjectNode();
			final TreeSet<String> names = new TreeSet<>();
			node.fieldNames().forEachRemaining(names::add);
			names.forEach(name -> sorted.set(name, canonicalize(node.get(name))));
			return sorted;
		}
		if (node.isArray()) {
			final ArrayNode array = MAPPER.createArrayNode();
			node.forEach(element -> array.add(canonicalize(element)));
			return array;
		}
		return node;
	}
}
