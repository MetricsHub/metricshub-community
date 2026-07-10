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
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.metricshub.agent.config.AgentConfig;
import org.metricshub.agent.config.AlertingSystemConfig;
import org.metricshub.agent.config.ResourceGroupConfig;
import org.metricshub.agent.context.AgentContext;
import org.metricshub.agent.helper.AgentConstants;
import org.metricshub.engine.common.helpers.JsonHelper;
import org.metricshub.engine.connector.model.ConnectorStore;
import org.metricshub.web.AgentContextHolder;
import org.metricshub.web.dto.uiconfig.AddHostRequestDto;
import org.metricshub.web.dto.uiconfig.CreateResourceGroupRequestDto;
import org.metricshub.web.dto.uiconfig.UiAdditionalConnectorDto;
import org.metricshub.web.dto.uiconfig.UiAlertingSystemConfigDto;
import org.metricshub.web.dto.uiconfig.UiConfigSnapshotDto;
import org.metricshub.web.dto.uiconfig.UiConnectorSummaryDto;
import org.metricshub.web.dto.uiconfig.UiResourceDefaultsDto;
import org.metricshub.web.dto.uiconfig.UiResourceGroupConfigFields;
import org.metricshub.web.dto.uiconfig.UpdateResourceGroupRequestDto;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Service for typed create, read, and delete operations on {@code metricshub-ui.yaml}.
 * <p>
 * Changes are written atomically and validated against {@link AgentConfig} before
 * persisting. Missing configuration files are treated as an empty document.
 */
@Service
public class UiConfigService {

	/**
	 * Provides access to the current {@link AgentContext} and configuration directory.
	 */
	private final AgentContextHolder agentContextHolder;

	private final UiConnectorCompatibilityService uiConnectorCompatibilityService;

	private final ConnectorStore webConnectorStore;
	/**
	 * YAML mapper used to read, write, and manipulate the UI configuration file.
	 */
	private final ObjectMapper yamlMapper = JsonHelper.buildYamlMapper();

	/**
	 * Creates a new {@link UiConfigService}.
	 *
	 * @param agentContextHolder holder for the active agent context and config directory
	 * @param uiConnectorCompatibilityService connector catalog compatibility evaluator
	 */
	public UiConfigService(
		final AgentContextHolder agentContextHolder,
		final UiConnectorCompatibilityService uiConnectorCompatibilityService,
		final ConnectorStore webConnectorStore
	) {
		this.agentContextHolder = agentContextHolder;
		this.uiConnectorCompatibilityService = uiConnectorCompatibilityService;
		this.webConnectorStore = webConnectorStore;
	}

	/**
	 * Lists connectors for the resource configuration form, filtered by host.type and configured protocols.
	 *
	 * @param hostType    host.type attribute (e.g. linux)
	 * @param protocols   configured protocol keys (e.g. ssh)
	 * @param includeAll  when true, include every connector with compatibility metadata
	 * @return connector summaries sorted by display name
	 */
	public List<UiConnectorSummaryDto> listConnectors(
		final String hostType,
		final List<String> protocols,
		final boolean includeAll
	) {
		final AgentContext agentContext = getAgentContext();
		return uiConnectorCompatibilityService.listConnectors(
			hostType,
			protocols,
			includeAll,
			webConnectorStore,
			agentContext.getExtensionManager()
		);
	}

	/**
	 * Returns the static connector catalog for the UI configuration form (metadata only).
	 *
	 * @return connector summaries without host-specific compatibility flags
	 */
	public List<UiConnectorSummaryDto> getConnectorCatalog() {
		final AgentContext agentContext = getAgentContext();
		return uiConnectorCompatibilityService.getConnectorCatalog(webConnectorStore, agentContext.getExtensionManager());
	}

	/**
	 * Returns the current UI configuration snapshot (top-level resources and resource groups).
	 *
	 * @return a snapshot of hosts and resource groups defined in {@code metricshub-ui.yaml}
	 * @throws org.springframework.web.server.ResponseStatusException with
	 *         {@link HttpStatus#INTERNAL_SERVER_ERROR} if the file cannot be read, or
	 *         {@link HttpStatus#BAD_REQUEST} if the root element is not a YAML object
	 */
	public UiConfigSnapshotDto getSnapshot() {
		final ObjectNode root = readUiConfigAsObjectNode();
		return UiConfigSnapshotDto.builder()
			.resources(asMap(root.get("resources")))
			.resourceGroups(asMap(root.get("resourceGroups")))
			.build();
	}

	/**
	 * Creates a new resource group with the given name and attributes.
	 *
	 * @param request resource group name and optional attributes
	 * @return the updated configuration snapshot after persistence
	 * @throws org.springframework.web.server.ResponseStatusException if validation,
	 *         read, or write fails
	 */
	public UiConfigSnapshotDto createResourceGroup(final CreateResourceGroupRequestDto request) {
		final ObjectNode root = readUiConfigAsObjectNode();
		final ObjectNode groups = objectNode(root, "resourceGroups");
		final ObjectNode group = objectNode(groups, request.getName());
		putMap(group, "attributes", request.getAttributes());
		putMap(group, "metrics", request.getMetrics());
		applyResourceGroupConfigFields(group, request);
		objectNode(group, "resources");
		writeAndValidate(root);
		return getSnapshot();
	}

	/**
	 * Updates the name and attributes of an existing resource group.
	 *
	 * @param groupName current name of the resource group to update
	 * @param request   new group name and attributes
	 * @return the updated configuration snapshot after persistence
	 * @throws org.springframework.web.server.ResponseStatusException with
	 *         {@link HttpStatus#NOT_FOUND} if the group does not exist, or
	 *         {@link HttpStatus#CONFLICT} if the new name is already in use
	 */
	public UiConfigSnapshotDto updateResourceGroup(final String groupName, final UpdateResourceGroupRequestDto request) {
		final ObjectNode root = readUiConfigAsObjectNode();
		final ObjectNode groups = objectNode(root, "resourceGroups");
		final var existing = groups.get(groupName);
		if (existing == null || existing.isNull() || !existing.isObject()) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Resource group '" + groupName + "' was not found.");
		}
		final ObjectNode groupNode = (ObjectNode) existing;
		putMap(groupNode, "attributes", request.getAttributes());
		putMap(groupNode, "metrics", request.getMetrics());
		applyResourceGroupConfigFields(groupNode, request);

		final String newName = request.getName().trim();
		if (!newName.equals(groupName)) {
			if (groups.has(newName)) {
				throw new ResponseStatusException(HttpStatus.CONFLICT, "Resource group '" + newName + "' already exists.");
			}
			groups.set(newName, groupNode);
			groups.remove(groupName);
		}

		writeAndValidate(root);
		return getSnapshot();
	}

	/**
	 * Adds a host either at the top level or inside an existing resource group.
	 *
	 * @param request host identifier, attributes, protocols, and optional resource group name
	 * @return the updated configuration snapshot after persistence
	 * @throws org.springframework.web.server.ResponseStatusException if validation,
	 *         read, or write fails
	 */
	public UiConfigSnapshotDto addHost(final AddHostRequestDto request) {
		final ObjectNode root = readUiConfigAsObjectNode();
		final ObjectNode hostNode = yamlMapper.valueToTree(new LinkedHashMap<String, Object>());
		putMap(hostNode, "attributes", request.getAttributes());
		putMap(hostNode, "protocols", request.getProtocols());
		applyResourceConfigFields(hostNode, request);
		if (request.getConnectors() != null && !request.getConnectors().isEmpty()) {
			hostNode.set("connectors", yamlMapper.valueToTree(request.getConnectors()));
		}
		if (request.getAdditionalConnectors() != null && !request.getAdditionalConnectors().isEmpty()) {
			final Map<String, Object> additionalConnectorsYaml = new LinkedHashMap<>();
			for (final Map.Entry<String, UiAdditionalConnectorDto> entry : request.getAdditionalConnectors().entrySet()) {
				final String connectorId = entry.getKey();
				if (connectorId == null || connectorId.isBlank()) {
					continue;
				}
				final UiAdditionalConnectorDto value = entry.getValue();
				if (value == null) {
					continue;
				}
				final Map<String, Object> connectorEntry = new LinkedHashMap<>();
				final String uses = value.getUses() != null && !value.getUses().isBlank() ? value.getUses() : connectorId;
				connectorEntry.put("uses", uses);
				connectorEntry.put("force", value.isForce());
				if (value.getVariables() != null && !value.getVariables().isEmpty()) {
					connectorEntry.put("variables", value.getVariables());
				}
				additionalConnectorsYaml.put(connectorId, connectorEntry);
			}
			if (!additionalConnectorsYaml.isEmpty()) {
				hostNode.set("additionalConnectors", yamlMapper.valueToTree(additionalConnectorsYaml));
			}
		}

		if (request.getResourceGroup() == null || request.getResourceGroup().isBlank()) {
			objectNode(root, "resources").set(request.getHostId(), hostNode);
		} else {
			final ObjectNode groups = objectNode(root, "resourceGroups");
			final ObjectNode groupNode = objectNode(groups, request.getResourceGroup());
			objectNode(groupNode, "resources").set(request.getHostId(), hostNode);
		}

		writeAndValidate(root);
		return getSnapshot();
	}

	/**
	 * Removes a standalone host from the top-level {@code resources} section.
	 *
	 * @param hostId identifier of the host to remove
	 * @return the updated configuration snapshot after persistence
	 * @throws org.springframework.web.server.ResponseStatusException if validation,
	 *         read, or write fails
	 */
	public UiConfigSnapshotDto deleteTopLevelHost(final String hostId) {
		final ObjectNode root = readUiConfigAsObjectNode();
		objectNode(root, "resources").remove(hostId);
		writeAndValidate(root);
		return getSnapshot();
	}

	/**
	 * Removes a host from the {@code resources} section of the given resource group.
	 *
	 * @param groupName name of the resource group containing the host
	 * @param hostId    identifier of the host to remove
	 * @return the updated configuration snapshot after persistence
	 * @throws org.springframework.web.server.ResponseStatusException if validation,
	 *         read, or write fails
	 */
	public UiConfigSnapshotDto deleteGroupedHost(final String groupName, final String hostId) {
		final ObjectNode root = readUiConfigAsObjectNode();
		final ObjectNode groups = objectNode(root, "resourceGroups");
		final ObjectNode groupNode = objectNode(groups, groupName);
		objectNode(groupNode, "resources").remove(hostId);
		writeAndValidate(root);
		return getSnapshot();
	}

	/**
	 * Deletes an entire resource group and all hosts it contains.
	 *
	 * @param groupName name of the resource group to remove
	 * @return the updated configuration snapshot after persistence
	 * @throws org.springframework.web.server.ResponseStatusException if validation,
	 *         read, or write fails
	 */
	public UiConfigSnapshotDto deleteResourceGroup(final String groupName) {
		final ObjectNode root = readUiConfigAsObjectNode();
		objectNode(root, "resourceGroups").remove(groupName);
		writeAndValidate(root);
		return getSnapshot();
	}

	/**
	 * Resolves the effective default values a new resource would inherit:
	 * agent-level settings overridden by the given resource group when provided.
	 *
	 * @param resourceGroup optional resource group name; blank or {@code null} for agent-level defaults
	 * @return the merged default values, durations in seconds
	 */
	public UiResourceDefaultsDto getResourceDefaults(final String resourceGroup) {
		final AgentContext context = getAgentContext();
		final AgentConfig agentConfig = context.getAgentConfig() != null ? context.getAgentConfig() : AgentConfig.empty();
		final ResourceGroupConfig group = resolveResourceGroupConfig(resourceGroup, agentConfig);

		final AlertingSystemConfig globalAlerting =
			agentConfig.getAlertingSystemConfig() != null
				? agentConfig.getAlertingSystemConfig()
				: AlertingSystemConfig.builder().build();
		final AlertingSystemConfig groupAlerting = group != null ? group.getAlertingSystemConfig() : null;
		final UiAlertingSystemConfigDto alerting = new UiAlertingSystemConfigDto();
		alerting.setDisable(
			groupAlerting != null && groupAlerting.getDisable() != null
				? groupAlerting.getDisable()
				: globalAlerting.getDisable()
		);
		alerting.setProblemTemplate(
			groupAlerting != null && groupAlerting.getProblemTemplate() != null
				? groupAlerting.getProblemTemplate()
				: globalAlerting.getProblemTemplate()
		);

		return UiResourceDefaultsDto.builder()
			.loggerLevel(
				group != null && group.getLoggerLevel() != null ? group.getLoggerLevel() : agentConfig.getLoggerLevel()
			)
			.outputDirectory(
				group != null && group.getOutputDirectory() != null
					? group.getOutputDirectory()
					: agentConfig.getOutputDirectory()
			)
			.collectPeriod(
				group != null && group.getCollectPeriod() != null ? group.getCollectPeriod() : agentConfig.getCollectPeriod()
			)
			.discoveryCycle(
				group != null && group.getDiscoveryCycle() != null ? group.getDiscoveryCycle() : agentConfig.getDiscoveryCycle()
			)
			.sequential(group != null && group.getSequential() != null ? group.getSequential() : agentConfig.isSequential())
			.enableSelfMonitoring(
				group != null && group.getEnableSelfMonitoring() != null
					? group.getEnableSelfMonitoring()
					: agentConfig.isEnableSelfMonitoring()
			)
			.logFileSourceDetails(
				group != null && group.getLogFileSourceDetails() != null
					? group.getLogFileSourceDetails()
					: agentConfig.isLogFileSourceDetails()
			)
			.resolveHostnameToFqdn(
				group != null && group.getResolveHostnameToFqdn() != null
					? group.getResolveHostnameToFqdn()
					: agentConfig.isResolveHostnameToFqdn()
			)
			.monitorFilters(
				group != null && group.getMonitorFilters() != null ? group.getMonitorFilters() : agentConfig.getMonitorFilters()
			)
			.jobTimeout(group != null && group.getJobTimeout() != null ? group.getJobTimeout() : agentConfig.getJobTimeout())
			.stateSetCompression(
				group != null && group.getStateSetCompression() != null
					? group.getStateSetCompression()
					: agentConfig.getStateSetCompression()
			)
			.enrichments(
				group != null && group.getEnrichments() != null && !group.getEnrichments().isEmpty()
					? group.getEnrichments()
					: agentConfig.getEnrichments()
			)
			.alertingSystem(alerting)
			.build();
	}

	/**
	 * Resolves a resource group configuration by name. Groups managed through the UI
	 * live in {@code metricshub-ui.yaml}, which is read fresh so edits not yet loaded
	 * by the running agent are still reflected; groups from the main configuration
	 * are taken from the running {@link AgentConfig}.
	 *
	 * @param resourceGroup group name, may be {@code null} or blank
	 * @param runningConfig the currently loaded agent configuration
	 * @return the group configuration, or {@code null} when not found
	 */
	private ResourceGroupConfig resolveResourceGroupConfig(final String resourceGroup, final AgentConfig runningConfig) {
		if (resourceGroup == null || resourceGroup.isBlank()) {
			return null;
		}
		final JsonNode groupNode = readUiConfigAsObjectNode().path("resourceGroups").path(resourceGroup);
		if (groupNode.isObject()) {
			try {
				final ObjectMapper mapper = AgentContext.newAgentConfigObjectMapper(getAgentContext().getExtensionManager());
				return mapper.treeToValue(groupNode, ResourceGroupConfig.class);
			} catch (Exception e) {
				// Malformed group in metricshub-ui.yaml: fall back to the running configuration.
			}
		}
		final Map<String, ResourceGroupConfig> groups = runningConfig.getResourceGroups();
		return groups != null ? groups.get(resourceGroup) : null;
	}

	/**
	 * Reads {@code metricshub-ui.yaml} from the agent configuration directory.
	 *
	 * @return the parsed root object, or an empty object if the file is missing or null
	 * @throws org.springframework.web.server.ResponseStatusException with
	 *         {@link HttpStatus#BAD_REQUEST} if the root is not a YAML object, or
	 *         {@link HttpStatus#INTERNAL_SERVER_ERROR} on I/O failure
	 */
	private ObjectNode readUiConfigAsObjectNode() {
		final Path path = resolveUiConfigPath();
		try {
			if (!Files.exists(path)) {
				return yamlMapper.createObjectNode();
			}
			final var node = yamlMapper.readTree(Files.readString(path, StandardCharsets.UTF_8));
			if (node == null || node.isNull()) {
				return yamlMapper.createObjectNode();
			}
			if (!node.isObject()) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "metricshub-ui.yaml root must be an object.");
			}
			return (ObjectNode) node;
		} catch (IOException e) {
			throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to read metricshub-ui.yaml.", e);
		}
	}

	/**
	 * Validates the configuration tree, then writes it atomically to disk.
	 *
	 * @param root the UI configuration root node to persist
	 * @throws org.springframework.web.server.ResponseStatusException with
	 *         {@link HttpStatus#INTERNAL_SERVER_ERROR} if validation or write fails
	 */
	private void writeAndValidate(final ObjectNode root) {
		try {
			validate(root);
			writeAtomically(resolveUiConfigPath(), yamlMapper.writerWithDefaultPrettyPrinter().writeValueAsString(root));
		} catch (IOException e) {
			throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to write metricshub-ui.yaml.", e);
		}
	}

	/**
	 * Deserializes the UI configuration into {@link AgentConfig} to ensure schema validity.
	 *
	 * @param root the configuration root node to validate
	 * @throws IOException if Jackson cannot map the tree to {@link AgentConfig}
	 */
	private void validate(final ObjectNode root) throws IOException {
		final var context = getAgentContext();
		final ObjectMapper mapper = AgentContext.newAgentConfigObjectMapper(context.getExtensionManager());
		mapper.treeToValue(root, AgentConfig.class);
	}

	/**
	 * Writes content to a temporary file and moves it to the target path atomically when supported.
	 *
	 * @param target  destination file path
	 * @param content serialized YAML content
	 * @throws IOException if the parent directory cannot be created or the file cannot be written
	 */
	private static void writeAtomically(final Path target, final String content) throws IOException {
		Files.createDirectories(target.getParent());
		final Path tmp = Files.createTempFile(target.getParent(), target.getFileName().toString() + ".", ".tmp");
		Files.writeString(tmp, content, StandardCharsets.UTF_8, StandardOpenOption.TRUNCATE_EXISTING);
		try {
			Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		} catch (AtomicMoveNotSupportedException ex) {
			Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
		}
	}

	/**
	 * Resolves the path to {@code metricshub-ui.yaml} in the agent configuration directory.
	 *
	 * @return absolute path to the UI configuration file
	 */
	private Path resolveUiConfigPath() {
		return getAgentContext().getConfigDirectory().resolve(AgentConstants.UI_CONFIG_FILENAME);
	}

	/**
	 * Returns the current agent context, ensuring the configuration directory is available.
	 *
	 * @return the active {@link AgentContext}
	 * @throws org.springframework.web.server.ResponseStatusException with
	 *         {@link HttpStatus#SERVICE_UNAVAILABLE} when the context or config directory is missing
	 */
	private AgentContext getAgentContext() {
		final var context = agentContextHolder.getAgentContext();
		if (context == null || context.getConfigDirectory() == null) {
			throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Configuration directory is not available.");
		}
		return context;
	}

	/**
	 * Returns the object node for {@code field}, creating it when absent or not an object.
	 *
	 * @param parent the parent object node
	 * @param field  name of the child object field
	 * @return the existing or newly created child object node
	 */
	private ObjectNode objectNode(final ObjectNode parent, final String field) {
		final var existing = parent.get(field);
		if (existing instanceof ObjectNode objectNode) {
			return objectNode;
		}
		final ObjectNode created = yamlMapper.createObjectNode();
		parent.set(field, created);
		return created;
	}

	/**
	 * Converts a JSON node to a {@link Map}, or an empty map when the node is null.
	 *
	 * @param node the JSON node to convert
	 * @return a map representation of the node, never {@code null}
	 */
	@SuppressWarnings("unchecked")
	private Map<String, Object> asMap(final JsonNode node) {
		if (node == null || node.isNull()) {
			return Map.of();
		}
		return yamlMapper.convertValue(node, Map.class);
	}

	/**
	 * Sets a map-valued field on a parent object node, using an empty map when {@code value} is null.
	 *
	 * @param parent the parent object node
	 * @param field  name of the field to set
	 * @param value  map content to serialize, or {@code null} for an empty map
	 */
	private void putMap(final ObjectNode parent, final String field, final Map<String, Object> value) {
		parent.set(field, yamlMapper.valueToTree(value == null ? Map.of() : value));
	}

	/**
	 * Writes optional resource-level fields from the add-host request onto the host node.
	 *
	 * @param hostNode YAML host node to update
	 * @param request  add-host request
	 */
	private void applyResourceConfigFields(final ObjectNode hostNode, final AddHostRequestDto request) {
		putIfPresent(hostNode, "loggerLevel", request.getLoggerLevel());
		putIfPresent(hostNode, "outputDirectory", request.getOutputDirectory());
		putIfPresent(hostNode, "collectPeriod", request.getCollectPeriod());
		if (request.getDiscoveryCycle() != null) {
			hostNode.put("discoveryCycle", request.getDiscoveryCycle());
		}
		putIfPresent(hostNode, "jobTimeout", request.getJobTimeout());
		putIfPresent(hostNode, "stateSetCompression", request.getStateSetCompression());

		putIfPresent(hostNode, "sequential", request.getSequential());
		putIfPresent(hostNode, "enableSelfMonitoring", request.getEnableSelfMonitoring());
		putIfPresent(hostNode, "logFileSourceDetails", request.getLogFileSourceDetails());
		putIfPresent(hostNode, "resolveHostnameToFqdn", request.getResolveHostnameToFqdn());

		if (request.getMonitorFilters() != null && !request.getMonitorFilters().isEmpty()) {
			hostNode.set("monitorFilters", yamlMapper.valueToTree(request.getMonitorFilters()));
		}
		if (request.getEnrichments() != null && !request.getEnrichments().isEmpty()) {
			hostNode.set("enrichments", yamlMapper.valueToTree(request.getEnrichments()));
		}
		if (request.getMetrics() != null && !request.getMetrics().isEmpty()) {
			hostNode.set("metrics", yamlMapper.valueToTree(request.getMetrics()));
		}

		final UiAlertingSystemConfigDto alertingSystem = request.getAlertingSystem();
		if (alertingSystem != null) {
			final Map<String, Object> alertingYaml = new LinkedHashMap<>();
			if (alertingSystem.getDisable() != null) {
				alertingYaml.put("disable", alertingSystem.getDisable());
			}
			if (alertingSystem.getProblemTemplate() != null && !alertingSystem.getProblemTemplate().isBlank()) {
				alertingYaml.put("problemTemplate", alertingSystem.getProblemTemplate());
			}
			if (!alertingYaml.isEmpty()) {
				hostNode.set("alertingSystem", yamlMapper.valueToTree(alertingYaml));
			}
		}
	}

	/** Group-level keys owned by the UI advanced options; cleared before each write so removals take effect. */
	private static final List<String> RESOURCE_GROUP_MANAGED_KEYS = List.of(
		"loggerLevel",
		"outputDirectory",
		"collectPeriod",
		"discoveryCycle",
		"jobTimeout",
		"stateSetCompression",
		"sequential",
		"enableSelfMonitoring",
		"resolveHostnameToFqdn",
		"monitorFilters",
		"enrichments"
	);

	/**
	 * Writes the advanced inheritance-aware fields onto a resource group node. Managed
	 * keys are removed first so an override cleared in the UI is dropped on update.
	 *
	 * @param groupNode YAML resource group node to update
	 * @param fields    create/update request carrying the advanced values
	 */
	private void applyResourceGroupConfigFields(final ObjectNode groupNode, final UiResourceGroupConfigFields fields) {
		for (final String key : RESOURCE_GROUP_MANAGED_KEYS) {
			groupNode.remove(key);
		}
		putIfPresent(groupNode, "loggerLevel", fields.getLoggerLevel());
		putIfPresent(groupNode, "outputDirectory", fields.getOutputDirectory());
		putIfPresent(groupNode, "collectPeriod", fields.getCollectPeriod());
		if (fields.getDiscoveryCycle() != null) {
			groupNode.put("discoveryCycle", fields.getDiscoveryCycle());
		}
		putIfPresent(groupNode, "jobTimeout", fields.getJobTimeout());
		putIfPresent(groupNode, "stateSetCompression", fields.getStateSetCompression());
		putIfPresent(groupNode, "sequential", fields.getSequential());
		putIfPresent(groupNode, "enableSelfMonitoring", fields.getEnableSelfMonitoring());
		putIfPresent(groupNode, "resolveHostnameToFqdn", fields.getResolveHostnameToFqdn());
		if (fields.getMonitorFilters() != null && !fields.getMonitorFilters().isEmpty()) {
			groupNode.set("monitorFilters", yamlMapper.valueToTree(fields.getMonitorFilters()));
		}
		if (fields.getEnrichments() != null && !fields.getEnrichments().isEmpty()) {
			groupNode.set("enrichments", yamlMapper.valueToTree(fields.getEnrichments()));
		}
	}

	/**
	 * Sets a text field on a parent object node when the value is non-blank.
	 *
	 * @param parent the parent object node
	 * @param field  field name
	 * @param value  text value
	 */
	private void putIfPresent(final ObjectNode parent, final String field, final String value) {
		if (value != null && !value.isBlank()) {
			parent.put(field, value);
		}
	}

	/**
	 * Sets a boolean field on a parent object node when the value is specified,
	 * so an explicit false is persisted while an unspecified value stays absent.
	 *
	 * @param parent the parent object node
	 * @param field  field name
	 * @param value  boolean value
	 */
	private void putIfPresent(final ObjectNode parent, final String field, final Boolean value) {
		if (value != null) {
			parent.put(field, value);
		}
	}
}
