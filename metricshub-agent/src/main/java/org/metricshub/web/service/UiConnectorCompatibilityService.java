package org.metricshub.web.service;

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
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.metricshub.engine.common.exception.InvalidConfigurationException;
import org.metricshub.engine.common.helpers.JsonHelper;
import org.metricshub.engine.configuration.HostConfiguration;
import org.metricshub.engine.configuration.IConfiguration;
import org.metricshub.engine.connector.model.Connector;
import org.metricshub.engine.connector.model.ConnectorStore;
import org.metricshub.engine.connector.model.RawConnector;
import org.metricshub.engine.connector.model.RawConnectorStore;
import org.metricshub.engine.connector.model.common.DeviceKind;
import org.metricshub.engine.connector.model.identity.ConnectionType;
import org.metricshub.engine.connector.model.identity.ConnectorIdentity;
import org.metricshub.engine.connector.model.identity.Detection;
import org.metricshub.engine.connector.model.monitor.task.source.Source;
import org.metricshub.engine.extension.ExtensionManager;
import org.metricshub.web.dto.uiconfig.UiConnectorSummaryDto;
import org.metricshub.web.dto.uiconfig.UiConnectorVariableDto;
import org.springframework.stereotype.Service;

/**
 * Evaluates connector compatibility for the UI wizard using the same rules as
 * {@link org.metricshub.engine.strategy.detection.AutomaticDetection} and
 * {@link HostConfiguration#determineAcceptedSources}.
 */
@Service
@RequiredArgsConstructor
public class UiConnectorCompatibilityService {

	private static final List<String> KNOWN_PROTOCOL_KEYS = List.of(
		"ssh",
		"wmi",
		"winrm",
		"wbem",
		"http",
		"snmp",
		"snmpv3",
		"ipmi",
		"jdbc",
		"jmx",
		"oscommand",
		"ping"
	);

	private static final JsonNode EMPTY_PROTOCOL_JSON = JsonNodeFactory.instance.objectNode();

	private static final UnaryOperator<char[]> NO_DECRYPT = value -> value;

	private static final ObjectMapper YAML_MAPPER = JsonHelper.buildYamlMapper();

	private volatile List<UiConnectorSummaryDto> cachedConnectorCatalog = List.of();
	private volatile int cachedConnectorCatalogSize = -1;

	/**
	 * Returns the static connector catalog for the UI (metadata only, no host context).
	 * Built once and cached until the connector store size changes.
	 *
	 * @param connectorStore connector store from the agent context
	 * @param extensionManager extension manager from the agent context
	 * @return sorted connector summaries without compatibility flags
	 */
	public List<UiConnectorSummaryDto> getConnectorCatalog(
		final ConnectorStore connectorStore,
		final ExtensionManager extensionManager
	) {
		final int storeSize = connectorStore.getStore().size();
		if (!cachedConnectorCatalog.isEmpty() && cachedConnectorCatalogSize == storeSize) {
			return cachedConnectorCatalog;
		}
		synchronized (this) {
			if (!cachedConnectorCatalog.isEmpty() && cachedConnectorCatalogSize == storeSize) {
				return cachedConnectorCatalog;
			}
			final RawConnectorStore rawConnectorStore = connectorStore.getRawConnectorStore();
			final Set<String> variableTemplateIds = new LinkedHashSet<>(connectorStore.getConnectorsWithVariables());
			final List<UiConnectorSummaryDto> built = connectorStore
				.getStore()
				.entrySet()
				.stream()
				.map(entry ->
					buildStaticSummary(
						entry.getKey(),
						entry.getValue(),
						extensionManager,
						variableTemplateIds.contains(entry.getKey()),
						rawConnectorStore
					)
				)
				.sorted(Comparator.comparing(UiConnectorSummaryDto::getDisplayName, String.CASE_INSENSITIVE_ORDER))
				.collect(Collectors.toCollection(ArrayList::new));
			cachedConnectorCatalog = List.copyOf(built);
			cachedConnectorCatalogSize = storeSize;
			return cachedConnectorCatalog;
		}
	}

	/**
	 * Lists connectors for the UI, optionally filtered to those compatible with the resource context.
	 *
	 * @param hostType    host.type attribute value (e.g. linux, storage)
	 * @param protocolKeys configured protocol keys on the resource (e.g. ssh, http)
	 * @param includeAll  when true, returns every connector with compatibility metadata; when false, only compatible ones
	 * @param connectorStore connector store from the agent context
	 * @param extensionManager extension manager from the agent context
	 * @return sorted connector summaries
	 */
	public List<UiConnectorSummaryDto> listConnectors(
		final String hostType,
		final List<String> protocolKeys,
		final boolean includeAll,
		final ConnectorStore connectorStore,
		final ExtensionManager extensionManager
	) {
		final HostProbe probe = buildHostProbe(hostType, protocolKeys, extensionManager);
		return getConnectorCatalog(connectorStore, extensionManager)
			.stream()
			.map(summary -> annotateCompatibility(summary, probe))
			.filter(summary -> includeAll || summary.isCompatible())
			.collect(Collectors.toCollection(ArrayList::new));
	}

	/**
	 * @param summary static connector summary
	 * @param probe     host context probe
	 * @return summary with {@code compatible} and {@code incompatibilityReasons} populated
	 */
	private UiConnectorSummaryDto annotateCompatibility(final UiConnectorSummaryDto summary, final HostProbe probe) {
		final List<String> reasons = new ArrayList<>();
		final boolean compatible = evaluateSummaryCompatibility(summary, probe, reasons);
		return summary.toBuilder().compatible(compatible).incompatibilityReasons(reasons).build();
	}

	/**
	 * Evaluates compatibility from catalog metadata and a host probe (no connector re-parse).
	 *
	 * @param summary static connector summary
	 * @param probe   host context probe
	 * @param reasons collector for human-readable incompatibility reasons
	 * @return true when compatible with the probe context
	 */
	private boolean evaluateSummaryCompatibility(
		final UiConnectorSummaryDto summary,
		final HostProbe probe,
		final List<String> reasons
	) {
		if (probe.invalidHostType) {
			reasons.add("Invalid or missing host.type: " + probe.hostTypeInput);
			return false;
		}
		if (probe.configuredProtocols.isEmpty()) {
			reasons.add("Configure at least one protocol on the resource before selecting connectors.");
			return false;
		}
		if (probe.acceptedSources.isEmpty()) {
			reasons.add(
				"No monitoring source available for the configured protocols (" +
					String.join(", ", probe.configuredProtocols) +
					")."
			);
			return false;
		}
		if (summary.getAppliesToHostTypes() == null || summary.getAppliesToHostTypes().isEmpty()) {
			reasons.add("Connector has no detection definition.");
			return false;
		}
		if (probe.deviceKind == null || !summary.getAppliesToHostTypes().contains(deviceKindKey(probe.deviceKind))) {
			reasons.add(
				"Requires host.type " +
					formatDisplayHostTypes(summary) +
					" (current: " +
					(probe.deviceKind != null ? probe.deviceKind.getDisplayName() : probe.hostTypeInput) +
					" / " +
					(probe.deviceKind != null ? deviceKindKey(probe.deviceKind) : probe.hostTypeInput) +
					")."
			);
			return false;
		}
		final ConnectionType expected = probe.isLocalhost ? ConnectionType.LOCAL : ConnectionType.REMOTE;
		final Set<String> allowedConnections =
			summary.getConnectionTypes() == null
				? Set.of()
				: summary
						.getConnectionTypes()
						.stream()
						.map(String::toUpperCase)
						.collect(Collectors.toCollection(LinkedHashSet::new));
		if (!allowedConnections.contains(expected.name())) {
			reasons.add(
				"Requires " +
					expected.name().toLowerCase() +
					" connection (connector allows: " +
					allowedConnections.stream().map(String::toLowerCase).collect(Collectors.joining(", ")) +
					")."
			);
			return false;
		}
		final Set<String> requiredProtocols =
			summary.getRequiredProtocols() == null ? Set.of() : new LinkedHashSet<>(summary.getRequiredProtocols());
		final boolean hasProtocol = requiredProtocols.stream().anyMatch(probe.configuredProtocols::contains);
		if (!hasProtocol) {
			reasons.add(
				"Requires at least one of these protocols: " +
					String.join(", ", requiredProtocols) +
					" (configured: " +
					String.join(", ", probe.configuredProtocols) +
					")."
			);
			return false;
		}
		return true;
	}

	/**
	 * @param summary connector summary
	 * @return formatted applies-to list for messages
	 */
	private static String formatDisplayHostTypes(final UiConnectorSummaryDto summary) {
		if (summary.getAppliesToDisplayNames() != null && !summary.getAppliesToDisplayNames().isEmpty()) {
			final List<String> appliesToHostTypes =
				summary.getAppliesToHostTypes() == null ? List.of() : summary.getAppliesToHostTypes();
			final List<String> parts = new ArrayList<>();
			for (int i = 0; i < summary.getAppliesToDisplayNames().size(); i++) {
				final String displayName = summary.getAppliesToDisplayNames().get(i);
				final String key = i < appliesToHostTypes.size() ? appliesToHostTypes.get(i) : displayName;
				parts.add(displayName + " (" + key + ")");
			}
			return String.join(", ", parts);
		}
		return summary.getAppliesToHostTypes() == null ? "" : String.join(", ", summary.getAppliesToHostTypes());
	}

	/**
	 * Builds a static connector summary (metadata only).
	 */
	private UiConnectorSummaryDto buildStaticSummary(
		final String connectorId,
		final Connector connector,
		final ExtensionManager extensionManager,
		final boolean hasVariables,
		final RawConnectorStore rawConnectorStore
	) {
		final ConnectorIdentity identity = connector.getConnectorIdentity();
		final String displayName =
			identity != null && identity.getDisplayName() != null && !identity.getDisplayName().isBlank()
				? identity.getDisplayName()
				: connectorId;

		final String information = resolveConnectorInformation(identity, connectorId, rawConnectorStore);

		final List<String> platforms =
			identity != null && identity.getPlatforms() != null
				? identity.getPlatforms().stream().sorted().toList()
				: List.of();

		final Detection detection = identity != null ? identity.getDetection() : null;

		final List<String> tags =
			detection != null && detection.getTags() != null ? detection.getTags().stream().sorted().toList() : List.of();
		final List<String> appliesToHostTypes =
			detection != null && detection.getAppliesTo() != null
				? detection.getAppliesTo().stream().map(UiConnectorCompatibilityService::deviceKindKey).sorted().toList()
				: List.of();

		final List<String> appliesToDisplayNames =
			detection != null && detection.getAppliesTo() != null
				? detection.getAppliesTo().stream().map(DeviceKind::getDisplayName).sorted().toList()
				: List.of();

		final List<String> connectionTypes =
			detection != null && detection.getConnectionTypes() != null
				? detection.getConnectionTypes().stream().map(Enum::name).sorted().toList()
				: List.of();

		final Set<String> requiredProtocols = resolveRequiredProtocolKeys(connector.getSourceTypes(), extensionManager);
		final boolean autoDetectionDisabled = detection == null || detection.isDisableAutoDetection();

		final List<UiConnectorVariableDto> variableDefinitions = hasVariables
			? extractVariableDefinitions(connectorId, rawConnectorStore)
			: List.of();

		return UiConnectorSummaryDto.builder()
			.id(connectorId)
			.displayName(displayName)
			.information(information)
			.platforms(platforms)
			.tags(tags)
			.appliesToHostTypes(appliesToHostTypes)
			.appliesToDisplayNames(appliesToDisplayNames)
			.connectionTypes(connectionTypes)
			.requiredProtocols(requiredProtocols.stream().sorted().toList())
			.autoDetectionDisabled(autoDetectionDisabled)
			.compatible(false)
			.incompatibilityReasons(List.of())
			.hasVariables(hasVariables)
			.usesTemplateId(hasVariables ? connectorId : null)
			.variables(variableDefinitions)
			.build();
	}

	/**
	 * Resolves connector description from compiled identity, falling back to raw YAML metadata.
	 *
	 * @param identity           compiled connector identity
	 * @param connectorId        connector id
	 * @param rawConnectorStore  raw connector store
	 * @return trimmed description or {@code null}
	 */
	private String resolveConnectorInformation(
		final ConnectorIdentity identity,
		final String connectorId,
		final RawConnectorStore rawConnectorStore
	) {
		if (identity != null && identity.getInformation() != null && !identity.getInformation().isBlank()) {
			return identity.getInformation().trim();
		}
		return extractInformationFromRaw(connectorId, rawConnectorStore);
	}

	/**
	 * Reads connector {@code information} from the raw connector YAML when the compiled identity omits it.
	 *
	 * @param connectorId        connector id
	 * @param rawConnectorStore  raw connector store
	 * @return trimmed description or {@code null}
	 */
	private String extractInformationFromRaw(final String connectorId, final RawConnectorStore rawConnectorStore) {
		if (rawConnectorStore == null || rawConnectorStore.getStore() == null) {
			return null;
		}
		final RawConnector rawConnector = rawConnectorStore.getStore().get(connectorId);
		if (rawConnector == null || rawConnector.getByteConnector() == null) {
			return null;
		}
		try {
			final JsonNode connectorNode = YAML_MAPPER.readTree(rawConnector.getByteConnector());
			final JsonNode informationNode = connectorNode.path("connector").path("information");
			if (informationNode.isMissingNode() || informationNode.isNull() || !informationNode.isTextual()) {
				return null;
			}
			final String information = informationNode.asText().trim();
			return information.isBlank() ? null : information;
		} catch (IOException e) {
			return null;
		}
	}

	/**
	 * Reads variable definitions from the raw connector template YAML.
	 *
	 * @param connectorId        template connector id
	 * @param rawConnectorStore  raw connector store
	 * @return variable definitions sorted by name
	 */
	private List<UiConnectorVariableDto> extractVariableDefinitions(
		final String connectorId,
		final RawConnectorStore rawConnectorStore
	) {
		if (rawConnectorStore == null || rawConnectorStore.getStore() == null) {
			return List.of();
		}
		final RawConnector rawConnector = rawConnectorStore.getStore().get(connectorId);
		if (rawConnector == null || rawConnector.getByteConnector() == null) {
			return List.of();
		}
		try {
			final JsonNode connectorNode = YAML_MAPPER.readTree(rawConnector.getByteConnector());
			final JsonNode variablesNode = connectorNode.path("connector").path("variables");
			if (variablesNode.isMissingNode() || !variablesNode.isObject()) {
				return List.of();
			}
			final List<UiConnectorVariableDto> variables = new ArrayList<>();
			variablesNode
				.properties()
				.forEach(entry -> {
					final JsonNode variableValue = entry.getValue();
					final String description =
						variableValue.has("description") && !variableValue.get("description").isNull()
							? variableValue.get("description").asText()
							: "";
					final String defaultValue =
						variableValue.has("defaultValue") && !variableValue.get("defaultValue").isNull()
							? variableValue.get("defaultValue").asText()
							: "";
					variables.add(
						UiConnectorVariableDto.builder()
							.name(entry.getKey())
							.description(description)
							.defaultValue(defaultValue)
							.build()
					);
				});
			variables.sort(Comparator.comparing(UiConnectorVariableDto::getName, String.CASE_INSENSITIVE_ORDER));
			return variables;
		} catch (IOException e) {
			return List.of();
		}
	}

	/**
	 * @param connectorSourceTypes connector source types
	 * @param extensionManager     extension manager
	 * @return protocol keys that can satisfy the connector sources
	 */
	private static Set<String> resolveRequiredProtocolKeys(
		final Set<Class<? extends Source>> connectorSourceTypes,
		final ExtensionManager extensionManager
	) {
		final Set<String> protocolKeys = new LinkedHashSet<>();
		final Map<Class<? extends IConfiguration>, Set<Class<? extends Source>>> mapping =
			extensionManager.findConfigurationToSourceMapping();

		for (final String protocolKey : KNOWN_PROTOCOL_KEYS) {
			try {
				extensionManager
					.buildConfigurationFromJsonNode(protocolKey, EMPTY_PROTOCOL_JSON, NO_DECRYPT)
					.ifPresent(configuration -> {
						final Set<Class<? extends Source>> sources = mapping.get(configuration.getClass());
						if (sources != null && sources.stream().anyMatch(connectorSourceTypes::contains)) {
							protocolKeys.add(protocolKey);
						}
					});
			} catch (InvalidConfigurationException ignored) {
				// Skip unsupported or invalid probe configurations
			}
		}
		return protocolKeys;
	}

	/**
	 * @param hostType         host.type value
	 * @param protocolKeys     configured protocol keys
	 * @param extensionManager extension manager
	 * @return probe used for compatibility checks
	 */
	private HostProbe buildHostProbe(
		final String hostType,
		final List<String> protocolKeys,
		final ExtensionManager extensionManager
	) {
		final String hostTypeInput = hostType == null ? "" : hostType.trim();
		DeviceKind deviceKind = null;
		boolean invalidHostType = false;
		try {
			if (!hostTypeInput.isBlank()) {
				deviceKind = DeviceKind.detect(hostTypeInput);
			}
		} catch (IllegalArgumentException e) {
			invalidHostType = true;
		}

		final Set<String> configuredProtocols =
			protocolKeys == null
				? Set.of()
				: protocolKeys
						.stream()
						.filter(Objects::nonNull)
						.map(String::trim)
						.filter(s -> !s.isBlank())
						.collect(Collectors.toCollection(LinkedHashSet::new));

		final Map<Class<? extends IConfiguration>, IConfiguration> configurations = new LinkedHashMap<>();
		for (final String protocol : configuredProtocols) {
			try {
				extensionManager
					.buildConfigurationFromJsonNode(protocol, EMPTY_PROTOCOL_JSON, NO_DECRYPT)
					.ifPresent(configuration -> configurations.put(configuration.getClass(), configuration));
			} catch (InvalidConfigurationException ignored) {
				// Skip invalid protocol entries from the wizard probe
			}
		}

		HostConfiguration hostConfiguration = null;
		Set<Class<? extends Source>> acceptedSources = Set.of();
		if (deviceKind != null && !invalidHostType) {
			hostConfiguration = HostConfiguration.builder().hostType(deviceKind).configurations(configurations).build();
			acceptedSources = hostConfiguration.determineAcceptedSources(false, extensionManager);
		}

		return new HostProbe(
			hostTypeInput,
			deviceKind,
			invalidHostType,
			configuredProtocols,
			acceptedSources,
			false,
			extensionManager,
			hostConfiguration
		);
	}

	/**
	 * @param deviceKind device kind
	 * @return lowercase key aligned with host.type values (linux, windows, …)
	 */
	private static String deviceKindKey(final DeviceKind deviceKind) {
		return deviceKind.name().toLowerCase();
	}

	/**
	 * Host context used to evaluate connector compatibility.
	 *
	 * @param hostTypeInput       raw host.type input
	 * @param deviceKind          resolved device kind
	 * @param invalidHostType     whether host.type could not be resolved
	 * @param configuredProtocols protocol keys configured on the resource
	 * @param acceptedSources     sources accepted for the host (from {@link HostConfiguration#determineAcceptedSources})
	 * @param isLocalhost         whether the resource is localhost
	 * @param extensionManager    extension manager
	 * @param hostConfiguration   built host configuration probe
	 */
	private record HostProbe(
		String hostTypeInput,
		DeviceKind deviceKind,
		boolean invalidHostType,
		Set<String> configuredProtocols,
		Set<Class<? extends Source>> acceptedSources,
		boolean isLocalhost,
		ExtensionManager extensionManager,
		HostConfiguration hostConfiguration
	) {}
}
