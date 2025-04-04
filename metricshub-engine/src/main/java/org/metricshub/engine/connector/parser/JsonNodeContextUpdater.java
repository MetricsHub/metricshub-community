package org.metricshub.engine.connector.parser;

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

import static org.metricshub.engine.common.helpers.MetricsHubConstants.EMPTY;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BinaryOperator;
import java.util.function.Predicate;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NonNull;

/**
 * This utility class traverses a JsonNode using a path context, applying updates according to an
 * updater function and a predicate that determines whether the value should be updated.
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class JsonNodeContextUpdater extends AbstractJsonNodeUpdater {

	/**
	 * Constructs a new instance of the {@link JsonNodeContextUpdater}.
	 * @param jsonNode  {@link JsonNode} object to update.
	 * @param predicate Update condition function.
	 * @param updater   Function performing an update of a string value using a current context.
	 */
	@Builder(setterPrefix = "with", builderMethodName = "jsonNodeContextUpdaterBuilder")
	public JsonNodeContextUpdater(
		@NonNull JsonNode jsonNode,
		@NonNull Predicate<String> predicate,
		@NonNull BinaryOperator<String> updater
	) {
		super(jsonNode, predicate);
		this.updater = updater;
	}

	@NonNull
	private final BinaryOperator<String> updater;

	/**
	 * Traverse the current JsonNode, applying the updater to each JsonNode child
	 * when the predicate evaluates to true, indicating that the value should be updated.
	 */
	@Override
	public void update() {
		update(jsonNode, EMPTY);
	}

	/**
	 * Traverse the current JsonNode, applying the updater to each JsonNode child
	 * when the predicate evaluates to true, indicating that the value should be updated.
	 *
	 * @param node the {@link JsonNode} to update
	 */
	private void update(final JsonNode node, final String currentPath) {
		if (node == null) {
			return;
		}

		if (node.isObject()) {
			// Get JsonNode fields
			final List<String> fieldNames = new ArrayList<>(node.size());
			node.fieldNames().forEachRemaining(fieldNames::add);

			// Get the corresponding JsonNode for each field
			for (final String fieldName : fieldNames) {
				final JsonNode child = node.get(fieldName);

				// Build a new path or append field name to the existing path context
				final String newPath = currentPath.isEmpty() ? fieldName : currentPath + "." + fieldName;

				// Means it wrap sub JsonNode(s)
				if (child.isContainerNode()) {
					update(child, newPath);
				} else if (!child.isNull()) {
					// Perform the replacement
					final String oldValue = child.asText();
					// Transformation of the value is unnecessary if it lacks the placeholder
					runUpdate(() -> ((ObjectNode) node).set(fieldName, new TextNode(updater.apply(oldValue, newPath))), oldValue);
				}
			}
		} else if (node.isArray()) {
			// Loop over the array and get each JsonNode element
			for (int i = 0; i < node.size(); i++) {
				final JsonNode child = node.get(i);

				// Create a new path for the array entry
				final String newPath = currentPath + "[" + i + "]";

				// Means this node is a JsonNode element
				if (child.isContainerNode()) {
					update(child, newPath);
				} else if (!child.isNull()) {
					// Means this is a simple array node
					final String oldValue = child.asText();
					// Transformation of the value is unnecessary if it lacks the placeholder
					final int index = i;
					runUpdate(() -> ((ArrayNode) node).set(index, new TextNode(updater.apply(oldValue, newPath))), oldValue);
				}
			}
		}
	}
}
