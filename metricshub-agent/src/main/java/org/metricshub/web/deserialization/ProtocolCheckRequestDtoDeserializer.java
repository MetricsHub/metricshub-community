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

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import org.metricshub.engine.extension.ExtensionManager;
import org.metricshub.web.AgentContextHolder;
import org.metricshub.web.dto.uiconfig.ProtocolCheckRequestDto;

/**
 * Deserializes {@link ProtocolCheckRequestDto} and builds typed {@link org.metricshub.engine.configuration.IConfiguration}
 * instances for the inline protocol configuration block.
 */
public class ProtocolCheckRequestDtoDeserializer extends JsonDeserializer<ProtocolCheckRequestDto> {

	@Override
	public ProtocolCheckRequestDto deserialize(final JsonParser parser, final DeserializationContext context)
		throws IOException {
		final JsonNode root = parser.readValueAsTree();
		final ProtocolCheckRequestDto request = new ProtocolCheckRequestDto();
		if (root == null || root.isNull()) {
			return request;
		}

		final JsonNode hostnameNode = root.get("hostname");
		if (hostnameNode != null && !hostnameNode.isNull()) {
			request.setHostname(hostnameNode.asText());
		}

		final JsonNode protocolNode = root.get("protocol");
		final String protocol = protocolNode != null && !protocolNode.isNull() ? protocolNode.asText() : null;
		request.setProtocol(protocol);

		final ExtensionManager extensionManager = resolveExtensionManager(context);
		request.setProtocolConfig(
			ProtocolConfigurationMaps.fromJsonNode(root.get("protocolConfig"), protocol, extensionManager)
		);
		return request;
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
		} catch (JsonMappingException ignored) {
			return null;
		}
		return null;
	}
}
