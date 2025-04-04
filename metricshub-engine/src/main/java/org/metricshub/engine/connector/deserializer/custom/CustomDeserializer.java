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
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.deser.std.DelegatingDeserializer;
import java.io.IOException;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import org.metricshub.engine.connector.model.monitor.task.source.Source;

/**
 * Class implementing the functionality of the Post deserialization
 */
public class CustomDeserializer extends DelegatingDeserializer {

	private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

	/**
	 * Source reference pattern
	 */
	private static final Pattern REFERENCE_PATTERN = Pattern.compile(
		"\\s*(\\$\\{source::((monitors)\\.(.*)\\.(.*)\\.sources\\.(.*))|((beforeAll|afterAll)\\.(.*))\\})\\s*",
		Pattern.MULTILINE | Pattern.CASE_INSENSITIVE
	);

	private static final long serialVersionUID = 1L;

	/**
	 * Constructs a new instance of CustomDeserializer.
	 *
	 * @param delegate The delegate {@link JsonDeserializer}.
	 */
	public CustomDeserializer(JsonDeserializer<?> delegate) {
		super(delegate);
	}

	@Override
	protected JsonDeserializer<?> newDelegatingInstance(JsonDeserializer<?> newDelegatee) {
		return new CustomDeserializer(newDelegatee);
	}

	@Override
	public Object deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
		final Object deserializedObject = super.deserialize(p, ctxt);

		callPostDeserialize(deserializedObject);

		return deserializedObject;
	}

	/**
	 * Post deserialization of the given object
	 *
	 * @param deserializedObject
	 */
	private void callPostDeserialize(final Object deserializedObject) {
		if (deserializedObject instanceof Source source) {
			// Temporary remove the source key so that
			// the 'references' method will not detect the key as a reference
			// thus, a source will not reference itself incorrectly
			final String sourceKey = source.getKey();
			source.setKey(null);

			final Set<String> refs = new HashSet<>();

			references(
				OBJECT_MAPPER.convertValue(deserializedObject, JsonNode.class),
				refs,
				val -> REFERENCE_PATTERN.matcher(val).find()
			);

			// Set the source key
			source.setKey(sourceKey);

			source.setReferences(refs);
		}
	}

	/**
	 * Traverse the given {@link JsonNode} and update the given reference collection
	 *
	 * @param jsonNode  node we wish to traverse
	 * @param refs      references collection to update
	 * @param predicate predicate function used to check the reference value
	 */
	private void references(final JsonNode jsonNode, final Collection<String> refs, final Predicate<String> predicate) {
		if (jsonNode == null) {
			return;
		}

		// If object? continue the traversal
		if (jsonNode.isObject()) {
			jsonNode.fields().forEachRemaining(entry -> references(entry.getValue(), refs, predicate));
		} else if (jsonNode.isArray()) {
			// if array? traverse each value in the array
			for (int i = 0; i < jsonNode.size(); i++) {
				references(jsonNode.get(i), refs, predicate);
			}
		} else {
			// If the value is not null and predicted, add it to the refs collection
			if (!jsonNode.isNull()) {
				final String value = jsonNode.asText();

				if (predicate.test(value)) {
					refs.add(value);
				}
			}
		}
	}
}
