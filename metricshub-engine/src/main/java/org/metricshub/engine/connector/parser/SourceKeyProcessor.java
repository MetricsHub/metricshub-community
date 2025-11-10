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
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.Map.Entry;
import java.util.Set;

/**
 * Processes source keys in a given JSON node structure. This processor modifies the input JSON node by
 * adding a "key" field to the source nodes. It is designed to work within a chain of node processors,
 * handling specific node transformations related to source keys.
 *
 * <br>The processor handles two specific parts of the node structure:
 * <ul>
 *   <li>
 *    Pre-nodes: Adds a "key" to each source node under "beforeAll" or "afterAll" based on the source name.
 *    E.g. <strong>${source::beforeAll.source_1}</strong> or <strong>${source::afterAll.source_1}</strong>
 *   </li>
 *   <li>
 *    Monitor nodes: For specified monitor job types ("discovery", "collect", "simple"), adds a "key" to
 *    each source node under "monitors" based on the monitor name, job type, and source name.
 *    E.g. <strong>${source::monitors.system.collect.sources.source_1}</strong>.
 *   </li>
 * </ul>
 *
 * <p>This class extends {@link AbstractNodeProcessor}, allowing for chain-of-responsibility pattern implementation.</p>
 */
public class SourceKeyProcessor extends AbstractNodeProcessor {

	/**
	 * The source's key property
	 */
	private static final String SOURCE_KEY_PROPERTY = "key";

	/**
	 * Known monitor job type: discovery, collect and simple
	 */
	private static final Set<String> MONITOR_JOB_TYPES;

	static {
		final Set<String> monitorJobTypes = new LinkedHashSet<>();
		monitorJobTypes.addAll(Set.of("discovery", "collect", "simple"));
		MONITOR_JOB_TYPES = monitorJobTypes;
	}

	/**
	 * Constructs a SourceKeyProcessor with a next processor.
	 */
	public SourceKeyProcessor(AbstractNodeProcessor next) {
		super(next);
	}

	/**
	 * Constructs a SourceKeyProcessor without a next processor.
	 */
	public SourceKeyProcessor() {
		this(null);
	}

	@Override
	protected JsonNode processNode(JsonNode node) throws IOException {
		processSurroundingNode("beforeAll", node);
		processSurroundingNode("afterAll", node);

		// Attempt to get the "monitors" node
		final JsonNode monitorsNode = node.get("monitors");
		// Make sure the node is available
		if (monitorsNode != null && !monitorsNode.isNull()) {
			// Traverse the monitors until the source nodes and set the key of each source node
			monitorsNode
				.properties()
				.forEach((Entry<String, JsonNode> monitorEntry) -> {
					final String monitorName = monitorEntry.getKey();
					final JsonNode monitorJobsNode = monitorEntry.getValue();
					monitorJobsNode
						.properties()
						.forEach((Entry<String, JsonNode> monitorJobEntry) -> {
							final String monitorJobType = monitorJobEntry.getKey();
							// Make sure the node is a monitor job node (discovery, collect, simple)
							if (MONITOR_JOB_TYPES.contains(monitorJobType)) {
								final JsonNode monitorJobNode = monitorJobEntry.getValue();
								final JsonNode sourcesNode = monitorJobNode.get("sources");
								if (sourcesNode != null && !sourcesNode.isNull()) {
									sourcesNode
										.properties()
										.forEach((Entry<String, JsonNode> sourceEntry) -> {
											final String sourceName = sourceEntry.getKey();
											final JsonNode sourceNode = sourceEntry.getValue();
											final ObjectNode sourceObjectNode = (ObjectNode) sourceNode;
											sourceObjectNode.set(
												SOURCE_KEY_PROPERTY,
												new TextNode(
													String.format("${source::monitors.%s.%s.sources.%s}", monitorName, monitorJobType, sourceName)
												)
											);
										});
								}
							}
						});
				});
		}
		return node;
	}

	/**
	 * Processes the surrounding node of the given JSON node e.g. beforeAll and afterAll node, by adding a "key" property.
	 *
	 * @param nodeKey The key of the surrounding node
	 * @param node    The JSON node
	 */
	private void processSurroundingNode(final String nodeKey, final JsonNode node) {
		// Get the surrounding  JSON node e.g. beforeAll or afterAll
		final JsonNode surroundingNode = node.get(nodeKey);
		if (surroundingNode != null && !surroundingNode.isNull()) {
			// Loop over the source nodes and set the key property on each source node
			surroundingNode
				.properties()
				.forEach((Entry<String, JsonNode> sourceNodeEntry) -> {
					final String sourceName = sourceNodeEntry.getKey();
					final JsonNode sourceNode = sourceNodeEntry.getValue();
					final ObjectNode sourceObjectNode = (ObjectNode) sourceNode;
					sourceObjectNode.set(
						SOURCE_KEY_PROPERTY,
						new TextNode(String.format("${source::%s.%s}", nodeKey, sourceName))
					);
				});
		}
	}
}
