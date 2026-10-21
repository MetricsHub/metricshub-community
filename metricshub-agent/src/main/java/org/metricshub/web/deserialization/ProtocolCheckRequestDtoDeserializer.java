package org.metricshub.web.deserialization;

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

import java.io.IOException;
import org.metricshub.engine.connector.parser.EnvironmentProcessor;
import org.metricshub.engine.extension.ExtensionManager;
import org.metricshub.web.AgentContextHolder;
import org.metricshub.web.dto.uiconfig.ProtocolCheckRequestDto;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DatabindException;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ValueDeserializer;

/**
 * Deserializes {@link ProtocolCheckRequestDto} and builds typed {@link org.metricshub.engine.configuration.IConfiguration}
 * instances for the inline protocol configuration block.
 */
public class ProtocolCheckRequestDtoDeserializer extends ValueDeserializer<ProtocolCheckRequestDto> {

	private static final com.fasterxml.jackson.databind.ObjectMapper LEGACY_OBJECT_MAPPER =
		new com.fasterxml.jackson.databind.ObjectMapper();

	@Override
	public ProtocolCheckRequestDto deserialize(final JsonParser parser, final DeserializationContext context) {
		final JsonNode root = parser.readValueAsTree();
		final ProtocolCheckRequestDto request = new ProtocolCheckRequestDto();
		if (root == null || root.isNull()) {
			return request;
		}

		// Resolve ${env::NAME} expressions like AgentContext does at configuration load time, so the
		// inline check targets the same hostname and credentials as the saved configuration.
		new EnvironmentProcessor().process(root);

		final JsonNode hostnameNode = root.get("hostname");
		if (hostnameNode != null && !hostnameNode.isNull()) {
			request.setHostname(hostnameNode.asText());
		}

		final JsonNode protocolNode = root.get("protocol");
		final String protocol = protocolNode != null && !protocolNode.isNull() ? protocolNode.asText() : null;
		request.setProtocol(protocol);

		final ExtensionManager extensionManager = resolveExtensionManager(context);
		request.setProtocolConfig(
			ProtocolConfigurationMaps.fromJsonNode(toLegacyJsonNode(root.get("protocolConfig")), protocol, extensionManager)
		);
		return request;
	}

	private static com.fasterxml.jackson.databind.JsonNode toLegacyJsonNode(final JsonNode node) {
		if (node == null) {
			return null;
		}
		try {
			return LEGACY_OBJECT_MAPPER.readTree(node.toString());
		} catch (IOException ignored) {
			return null;
		}
	}

	private static ExtensionManager resolveExtensionManager(final DeserializationContext context) {
		try {
			Object injectable = context.findInjectableValue(ExtensionManager.class.getName(), null, null, null, null);
			if (injectable == null) {
				injectable = context.findInjectableValue(ExtensionManager.class, null, null, null, null);
			}
			if (injectable instanceof ExtensionManager manager) {
				return manager;
			}
			if (injectable instanceof AgentContextHolder holder) {
				return holder.getAgentContext().getExtensionManager();
			}
		} catch (DatabindException ignored) {
			return null;
		}
		return null;
	}
}
