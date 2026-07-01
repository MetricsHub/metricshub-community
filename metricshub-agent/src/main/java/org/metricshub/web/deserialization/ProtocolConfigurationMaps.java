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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.metricshub.agent.helper.ConfigHelper;
import org.metricshub.engine.configuration.IConfiguration;
import org.metricshub.engine.extension.ExtensionManager;

/**
 * Builds typed protocol configuration maps for the guided configuration UI.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ProtocolConfigurationMaps {

	/**
	 * Deserializes a protocol configuration JSON node into a map of protocol id to {@link IConfiguration}.
	 * <p>
	 * Supports both shapes:
	 * <ul>
	 *   <li>protocol-keyed: {@code { "ssh": { "username": "admin" } }}</li>
	 *   <li>flat inline fields: {@code { "username": "admin", "port": 22 }} (wrapped under {@code protocolHint})</li>
	 * </ul>
	 *
	 * @param configNode       JSON node for protocolConfig
	 * @param protocolHint     protocol id from the parent request (e.g. ssh)
	 * @param extensionManager extension manager used to build typed configurations
	 * @return protocol id to configuration map
	 */
	public static Map<String, IConfiguration> fromJsonNode(
		final JsonNode configNode,
		final String protocolHint,
		final ExtensionManager extensionManager
	) {
		final Map<String, IConfiguration> protocolConfig = new HashMap<>();
		if (configNode == null || configNode.isNull() || configNode.isMissingNode() || !configNode.isObject()) {
			return protocolConfig;
		}
		if (extensionManager == null) {
			return protocolConfig;
		}

		if (isProtocolKeyedNode(configNode, protocolHint, extensionManager)) {
			configNode
				.properties()
				.forEach(entry ->
					buildConfiguration(extensionManager, entry.getKey(), entry.getValue())
						.ifPresent(configuration -> protocolConfig.put(entry.getKey(), configuration))
				);
			return protocolConfig;
		}

		if (protocolHint != null && !protocolHint.isBlank()) {
			buildConfiguration(extensionManager, protocolHint, configNode)
				.ifPresent(configuration -> protocolConfig.put(protocolHint, configuration));
		}

		return protocolConfig;
	}

	/**
	 * Resolves the configuration for the requested protocol from a typed protocol map.
	 *
	 * @param protocol       protocol id (e.g. ssh)
	 * @param protocolConfig protocol configuration map
	 * @return matching configuration, or {@code null} when none applies
	 */
	public static IConfiguration resolveForProtocol(
		final String protocol,
		final Map<String, IConfiguration> protocolConfig
	) {
		if (protocolConfig == null || protocolConfig.isEmpty()) {
			return null;
		}
		if (protocol != null && !protocol.isBlank()) {
			for (final Map.Entry<String, IConfiguration> entry : protocolConfig.entrySet()) {
				if (protocol.equalsIgnoreCase(entry.getKey())) {
					return entry.getValue();
				}
			}
		}
		if (protocolConfig.size() == 1) {
			return protocolConfig.values().iterator().next();
		}
		return null;
	}

	private static boolean isProtocolKeyedNode(
		final JsonNode configNode,
		final String protocolHint,
		final ExtensionManager extensionManager
	) {
		if (protocolHint != null && !protocolHint.isBlank() && configNode.has(protocolHint)) {
			return true;
		}
		final Iterator<String> fieldNames = configNode.fieldNames();
		while (fieldNames.hasNext()) {
			final String fieldName = fieldNames.next();
			if (extensionManager.findExtensionByType(fieldName).isPresent()) {
				return true;
			}
		}
		return false;
	}

	private static Optional<IConfiguration> buildConfiguration(
		final ExtensionManager extensionManager,
		final String protocolName,
		JsonNode configNode
	) {
		if (protocolName == null || protocolName.isBlank()) {
			return Optional.empty();
		}
		if (configNode == null || configNode.isNull()) {
			configNode = JsonNodeFactory.instance.objectNode();
		}
		try {
			return extensionManager.buildConfigurationFromJsonNode(protocolName, configNode, ConfigHelper::decrypt);
		} catch (Exception ignored) {
			return Optional.empty();
		}
	}
}
