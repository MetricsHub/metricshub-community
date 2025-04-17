package org.metricshub.agent.service;

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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Iterator;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.metricshub.engine.extension.ExtensionManager;
import org.metricshub.engine.extension.IConfigurationProvider;

/**
 * Service for managing the agent configuration.<br>
 * It loads the configuration from the specified directory and merges it into a single configuration object.<br>
 * This service uses the ExtensionManager to load configuration providers and merge their configurations.<br>
 */
@Data
@Builder(setterPrefix = "with")
@Slf4j
public class ConfigurationService {

	private Path configDirectory;

	/**
	 * Loads the configuration from the specified directory.
	 * @param extensionManager The extension manager to use for loading configuration providers.
	 * @return The merged configuration as a JsonNode.
	 */
	public JsonNode loadConfiguration(final ExtensionManager extensionManager) {
		final JsonNode mergedConfiguration = JsonNodeFactory.instance.objectNode();

		extensionManager
			.getConfigurationProviderExtensions()
			.forEach((IConfigurationProvider configurationProvider) -> {
				var fragments = configurationProvider.load(configDirectory);
				if (fragments != null) {
					deepMergeFragments(mergedConfiguration, fragments);
				}
			});

		return mergedConfiguration;
	}

	/**
	 * Merge the fragments into the mergedConfiguration.
	 * This method iterates over the fragments and merges each one into the mergedConfiguration.
	 * @param mergedConfiguration The merged configuration node.
	 * @param fragments The collection of fragments to merge.
	 */
	private static void deepMergeFragments(final JsonNode mergedConfiguration, Collection<JsonNode> fragments) {
		for (var fragment : fragments) {
			if (fragment != null) {
				// Merge the fragment into the mergedConfiguration
				deepMerge(mergedConfiguration, fragment);
			}
		}
	}

	/**
	 * Recursively merges the contents of {@code updateNode} into {@code mainNode} following a well-defined strategy.<br>
	 * Bad configurations may lead to unexpected behavior, so the product must be configured correctly.
	 * <p>
	 * Merge strategy:
	 * <ol>
	 *   <li>If both values are arrays of <b>objects</b>: the contents of {@code updateNode} are <b>appended</b> to those of {@code mainNode}.</li>
	 *   <li>If both values are arrays of <b>scalars</b> (non-object values): {@code updateNode} <b>replaces</b> {@code mainNode} values.</li>
	 *   <li>If both values are JSON <b>objects</b>: the merge is performed <b>recursively</b> (deep merge).</li>
	 *   <li>If types differ or cannot be merged: the value from {@code updateNode} <b>overwrites</b> the one in {@code mainNode}.</li>
	 *   <li>If a field exists only in {@code updateNode}, it is <b>added</b> to {@code mainNode}.</li>
	 *   <li>If the update value is explicitly {@code null}, it is represented using {@link com.fasterxml.jackson.databind.node.NullNode}.</li>
	 * </ol>
	 * <p>
	 * This method modifies {@code mainNode} in-place and returns it for convenience.
	 * Defensive checks are applied to avoid type mismatches and null pointer exceptions.
	 *
	 * @param mainNode   The base JSON node to merge into (must be mutable, typically an {@link com.fasterxml.jackson.databind.node.ObjectNode}).
	 * @param updateNode The JSON node containing new values to merge into {@code mainNode}.
	 * @return The merged {@code mainNode} with all changes from {@code updateNode} applied.
	 *
	 * @return The merged JSON node.
	 */
	public static JsonNode deepMerge(JsonNode mainNode, JsonNode updateNode) {
		final Iterator<String> fieldNames = updateNode.fieldNames();
		while (fieldNames.hasNext()) {
			final String fieldName = fieldNames.next();
			final JsonNode mainField = mainNode.get(fieldName);
			final JsonNode updateField = updateNode.get(fieldName);

			if (mainField != null && updateField != null) {
				if (mainField.isArray() && updateField.isArray()) {
					mergeJsonArray(fieldName, mainField, updateField);
				} else if (mainField.isObject() && updateField.isObject()) {
					deepMerge(mainField, updateField);
				} else {
					// Incompatible types or scalars: replace value
					setNonNullUpdateField(mainNode, fieldName, updateField);
				}
			} else {
				setUpdateField(mainNode, fieldName, updateField);
			}
		}
		return mainNode;
	}

	/**
	 * Set the update field in the main node. The update field is set to the main node if it is not null.
	 * @param mainNode    The main JSON node to update.
	 * @param fieldName   The name of the field to set.
	 * @param updateField The update field to set.
	 */
	private static void setUpdateField(final JsonNode mainNode, final String fieldName, final JsonNode updateField) {
		// Field doesn't exist in mainNode: add it
		if (mainNode instanceof ObjectNode objectNode) {
			if (updateField != null && !updateField.isNull()) {
				objectNode.set(fieldName, updateField.deepCopy());
			} else {
				objectNode.set(fieldName, JsonNodeFactory.instance.nullNode());
			}
		}
	}

	/**
	 * Set the update field in the main node. The update field is expected to be non-null.
	 * @param mainNode    The main JSON node to update.
	 * @param fieldName   The name of the field to set.
	 * @param updateField The update field to set.
	 */
	private static void setNonNullUpdateField(
		final JsonNode mainNode,
		final String fieldName,
		final JsonNode updateField
	) {
		if (mainNode instanceof ObjectNode objectNode) {
			objectNode.set(fieldName, updateField.deepCopy());
		}
	}

	/**
	 * Merges JSON arrays based on specific conditions.
	 * <p>
	 * Merge strategy:
	 * <ol>
	 *   <li>If both arrays have a set of <b>objects</b>: the contents of {@code updateArrayNode} are <b>appended</b> to those of {@code mainArrayNode}.</li>
	 *   <li>If both arrays have a set of <b>scalars</b> (non-object values): {@code updateArrayNode} <b>replaces</b> {@code mainArrayNode} values.</li>
	 * </ol>
	 * <p>
	 * @param fieldName       The name of the field representing the array, used for logging.
	 * @param mainArrayNode   The main JSON array node containing the array to merge into.
	 * @param updateArrayNode The update JSON node containing the array to merge.
	 */
	private static void mergeJsonArray(
		final String fieldName,
		final JsonNode mainArrayNode,
		final JsonNode updateArrayNode
	) {
		final ArrayNode mainArray = (ArrayNode) mainArrayNode;
		final ArrayNode updateArray = (ArrayNode) updateArrayNode;

		if (mainArray.isEmpty()) {
			mainArray.addAll(updateArray);
			return;
		}

		// Handle edge case: empty update array
		if (updateArray.isEmpty()) {
			// Keep strategy: scalar array overwrite
			mainArray.removeAll();
			return;
		}

		if (mainArray.get(0).isObject() && updateArray.get(0).isObject()) {
			// Append objects
			updateArray.forEach(mainArray::add);
		} else if (!mainArray.get(0).isObject() && !updateArray.get(0).isObject()) {
			// Overwrite scalars
			mainArray.removeAll();
			mainArray.addAll(updateArray);
		} else {
			// Mixed content types (object vs scalar), fallback to overwrite
			log.warn("Inconsistent array types in field '{}'. Overwriting main array.", fieldName);
			mainArray.removeAll();
			mainArray.addAll(updateArray);
		}
	}
}
