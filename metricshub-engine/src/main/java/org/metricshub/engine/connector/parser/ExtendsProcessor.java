package org.metricshub.engine.connector.parser;

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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NonNull;
import org.metricshub.engine.common.helpers.FileHelper;

/**
 * Represents a processor that merges extended connectors specified under the 'extends' section of the given JSON node.
 * This processor recursively merges extended connectors, applying the merging logic for arrays and objects.
 * The merged result is then passed to the next processor in the chain.
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ExtendsProcessor extends AbstractNodeProcessor {

	@NonNull
	private Path connectorDirectory;

	@NonNull
	private ObjectMapper mapper;

	@Builder
	public ExtendsProcessor(@NonNull Path connectorDirectory, @NonNull ObjectMapper mapper, AbstractNodeProcessor next) {
		super(next);
		this.connectorDirectory = connectorDirectory;
		this.mapper = mapper;
	}

	@Override
	public JsonNode processNode(JsonNode node) throws IOException {
		return doMerge(node);
	}

	/**
	 * Merge logic:<br>
	 * <ol>
	 *   <li>Merged extended connectors located under the extends section of the given node.</li>
	 *   <li>Once all the extended connectors are merged, merge the given JsonNode (node) with the extended connectors that have been merged.</li>
	 * </ol>
	 * <br>
	 * A recursive merge is applied for each extended connector because it can extend another connector too. That's why doMerge
	 * is called for each extended connector.
	 * @param node The JSON node to merge.
	 * @return The merged JSON node.
	 * @throws IOException If an I/O error occurs during merging.
	 */
	private JsonNode doMerge(JsonNode node) throws IOException {
		JsonNode extNode = node.get("extends");

		JsonNode result = node;
		if (extNode != null && extNode.isArray()) {
			final ArrayNode extNodeArray = (ArrayNode) extNode;
			final Iterator<JsonNode> iter = extNodeArray.iterator();

			JsonNode extended = null;
			if (iter.hasNext()) {
				extended = doMerge(getJsonNode(iter));
				while (iter.hasNext()) {
					final JsonNode extendedNext = doMerge(getJsonNode(iter));
					merge(extended, extendedNext);
				}
			}

			extNodeArray.removeAll();

			if (extended != null) {
				result = merge(extended, node);
			}
		}
		return result;
	}

	/**
	 * Gets the next JSON node from the iterator based on the connector directory.
	 *
	 * @param iter The iterator over a collection of JSON nodes.
	 * @return The next JSON node.
	 * @throws IOException If an I/O error occurs during node retrieval.
	 */
	private JsonNode getJsonNode(Iterator<JsonNode> iter) throws IOException {
		final String connectorRelativePath = iter.next().asText() + ".yaml";

		// If the path is absolute, it should refer to a path within the "connectors" directory
		if (!connectorRelativePath.startsWith(".")) {
			final Path connectorsDirectoryPath = FileHelper.findConnectorsDirectory(connectorDirectory.toUri());
			if (connectorsDirectoryPath != null) {
				final File connectorPathFile = connectorsDirectoryPath.resolve(connectorRelativePath).normalize().toFile();
				if (connectorPathFile != null && connectorPathFile.exists()) {
					return mapper.readTree(connectorPathFile);
				}
			}
		}

		Path connectorPath = connectorDirectory.resolve(connectorRelativePath).normalize();

		return mapper.readTree(Files.newInputStream(connectorPath));
	}

	/**
	 * Merge the given mainNode and updateNode.
	 * Merge strategy:<br>
	 * <ol>
	 *   <li>Arrays of objects are appended from <code>updateNode</code> to <code>mainNode</code>.</li>
	 *   <li>Arrays of simple values from <code>updateNode</code> erase the ones in <code>mainNode</code>.</li>
	 *   <li><code>updateNode</code> object values overwrite <code>mainNode</code> object values.<li>
	 * </ol>
	 *
	 * @param mainNode   The main JSON node to merge into.
	 * @param updateNode The update JSON node to merge.
	 * @return The merged JSON node.
	 */
	public static JsonNode merge(JsonNode mainNode, JsonNode updateNode) {
		final Iterator<String> fieldNames = updateNode.fieldNames();
		while (fieldNames.hasNext()) {
			String fieldName = fieldNames.next();
			JsonNode jsonNode = mainNode.get(fieldName);
			if (jsonNode != null && jsonNode.isArray() && updateNode.get(fieldName).isArray()) {
				// both JSON nodes are arrays
				mergeJsonArray(updateNode, fieldName, jsonNode);
			} else if (jsonNode != null && jsonNode.isObject()) {
				// both JSON nodes are objects, merge them
				merge(jsonNode, updateNode.get(fieldName));
			} else {
				if (mainNode instanceof ObjectNode objectNode) {
					// overwrite field
					JsonNode value = updateNode.get(fieldName);
					objectNode.set(fieldName, value);
				}
			}
		}
		return mainNode;
	}

	/**
	 * Merges JSON arrays based on specific conditions.
	 *
	 * @param updateNode The update JSON node containing the array to merge.
	 * @param fieldName  The name of the field representing the array.
	 * @param jsonNode   The main JSON node containing the array to merge into.
	 */
	private static void mergeJsonArray(JsonNode updateNode, String fieldName, JsonNode jsonNode) {
		ArrayNode mainArray = (ArrayNode) jsonNode;
		ArrayNode extendedArray = (ArrayNode) updateNode.get(fieldName);

		if (mainArray.size() != 0 && mainArray.get(0).isObject()) {
			// Array of objects gets merged (appended)
			for (int i = 0; i < extendedArray.size(); i++) {
				mainArray.add(extendedArray.get(i));
			}
		} else {
			// Simple array gets overwritten
			mainArray.removeAll();
			mainArray.addAll(extendedArray);
		}
	}
}
