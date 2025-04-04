package org.metricshub.agent.deserialization;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * MetricsHub Agent
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
import com.fasterxml.jackson.core.JsonStreamContext;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import org.metricshub.agent.config.ResourceConfig;
import org.metricshub.agent.helper.ConfigHelper;
import org.metricshub.engine.common.helpers.JsonHelper;
import org.metricshub.engine.connector.deserializer.PostDeserializeHelper;
import org.metricshub.engine.connector.model.Connector;
import org.metricshub.engine.connector.model.monitor.MonitorJob;
import org.metricshub.engine.connector.parser.ConnectorParser;
import org.metricshub.engine.connector.parser.ReferenceResolverProcessor;
import org.metricshub.engine.connector.parser.SourceKeyProcessor;
import org.metricshub.engine.connector.update.ConnectorUpdateChain;

/**
 * Custom JSON deserializer for mapping monitor jobs from JSON to a {@code Map<String, MonitorJob>} using Jackson.
 */
public class MonitorJobsDeserializer extends JsonDeserializer<Map<String, MonitorJob>> {

	/**
	 * ObjectMapper for JSON deserialization.
	 */
	public static final ObjectMapper OBJECT_MAPPER = newObjectMapper();

	@Override
	public Map<String, MonitorJob> deserialize(JsonParser parser, DeserializationContext context) throws IOException {
		if (parser == null) {
			return new HashMap<>();
		}

		final JsonNode jsonNode = parser.readValueAs(JsonNode.class);

		if (jsonNode == null) {
			return new HashMap<>();
		}

		// Resolve relative source references through the ReferenceResolverProcessor
		final JsonNode monitorsNode = new ReferenceResolverProcessor(new SourceKeyProcessor())
			.process(JsonNodeFactory.instance.objectNode().set("monitors", jsonNode));

		// Parse the connector like it is done by the engine for regular connectors
		final Connector connector = JsonHelper.deserialize(OBJECT_MAPPER, monitorsNode, Connector.class);

		// Create the update chain like it is actually done by the engine
		final ConnectorUpdateChain connectorUpdateChain = ConnectorParser.createUpdateChain();

		// Run the update chain
		if (connectorUpdateChain != null) {
			connectorUpdateChain.update(connector);
		}

		// Here we are going to get the parsing context to get the parent object (ResourceConfig)
		// Once the parent is retrieved, the connector is set in this parent instance.
		// This connector will be used later by the engine to run the monitor jobs
		final JsonStreamContext parsingContext = parser.getParsingContext();
		final Object parent = parsingContext.getCurrentValue();

		((ResourceConfig) parent).setConnector(connector);

		return connector.getMonitors();
	}

	/**
	 * Create a new ObjectMapper for {@link Connector} deserialization
	 *
	 * @return {@link ObjectMapper}
	 */
	private static ObjectMapper newObjectMapper() {
		final ObjectMapper objectMapper = ConfigHelper.newObjectMapper();

		// Post deserialize support creates a task after the initial connector deserialization
		// to update the connector information such as the references that are used later by the update chain
		// in order to create the source dependency tree that the engine uses to process the sources
		// a certain order.
		PostDeserializeHelper.addPostDeserializeSupport(objectMapper);

		return objectMapper;
	}
}
