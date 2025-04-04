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

import static org.metricshub.agent.helper.AgentConstants.AGENT_RESOURCE_HOST_NAME_ATTRIBUTE_KEY;
import static org.metricshub.engine.common.helpers.MetricsHubConstants.MULTI_VALUE_SEPARATOR;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.deser.std.DelegatingDeserializer;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.metricshub.agent.config.AgentConfig;
import org.metricshub.agent.config.ResourceConfig;
import org.metricshub.agent.config.ResourceGroupConfig;
import org.metricshub.engine.configuration.IConfiguration;

/**
 * Custom JSON deserializer for post-processing {@link ResourceConfig} instances.
 *
 * <p>The {@code PostConfigDeserializer} extends {@link DelegatingDeserializer} to allow additional processing
 * after the default deserialization. It specifically handles cases where {@code ResourceConfig} instances define
 * special array attributes (E.g host.name), indicating multiple resources sharing
 * the same configuration. This deserializer resolves such configurations by creating new {@code ResourceConfig}
 * instances for each resource and updating the configuration map accordingly.
 * </p>
 */
public class PostConfigDeserializer extends DelegatingDeserializer {

	@Deprecated(since = "0.9.08", forRemoval = true)
	private static final String MULTI_HOST_ATTRIBUTE_KEY = "host.names";

	private static final long serialVersionUID = 1L;

	/**
	 * Constructs a new {@code PostConfigDeserializer} with the specified delegate.
	 *
	 * @param delegate The delegate {@link JsonDeserializer} for actual deserialization.
	 */
	public PostConfigDeserializer(final JsonDeserializer<?> delegate) {
		super(delegate);
	}

	@Override
	protected JsonDeserializer<?> newDelegatingInstance(JsonDeserializer<?> newDelegatee) {
		return new PostConfigDeserializer(newDelegatee);
	}

	@Override
	public Object deserialize(JsonParser jsonParser, DeserializationContext ctxt) throws IOException {
		final Object deserializedObject = super.deserialize(jsonParser, ctxt);

		// Manage ResourceConfig instances that define multiple resources sharing the same configuration
		if (deserializedObject instanceof ResourceGroupConfig resourceGroupConfig) {
			resolveMultiResourceConfigurations(resourceGroupConfig);
		}

		// Manage ResourceConfig instances that define multiple resources sharing the same configuration under the AgentConfig
		if (deserializedObject instanceof AgentConfig agentConfig) {
			resolveResources(agentConfig.getResources());
		}

		return deserializedObject;
	}

	/**
	 * Resolves {@link ResourceConfig} defining multiple resources that share the same configuration
	 *
	 * @param resourceGroupConfig {@link ResourceGroupConfig} instance where all the {@link ResourceConfig} instances belong
	 */
	private void resolveMultiResourceConfigurations(final ResourceGroupConfig resourceGroupConfig) {
		resolveResources(resourceGroupConfig.getResources());
	}

	/**
	 * Resolves the multiple resource configurations of the given {@link ResourceConfig} map.
	 * <br>It creates new {@link ResourceConfig} instances for each host and updates the map accordingly.
	 *
	 * @param existingResourceConfigMap Map containing the existing {@link ResourceConfig} instances
	 */
	private void resolveResources(final Map<String, ResourceConfig> existingResourceConfigMap) {
		final Set<String> resolvedMultiResourceConfigKeys = new HashSet<>();
		final Map<String, ResourceConfig> newResourceConfigMap = new HashMap<>();
		// Loop over each multiple resource configuration and resolve it
		existingResourceConfigMap
			.entrySet()
			.stream()
			.filter(resourceConfigEntry ->
				Objects.nonNull(resourceConfigEntry.getValue()) &&
				resourceConfigEntry
					.getValue()
					.getAttributes()
					.values()
					.stream()
					.filter(Objects::nonNull)
					.anyMatch(s -> s.contains(MULTI_VALUE_SEPARATOR))
			)
			.forEach(resourceConfigEntry ->
				resolveMultiResourceConfig(resolvedMultiResourceConfigKeys, newResourceConfigMap, resourceConfigEntry)
			);

		// Put the new resource configuration (one per host)
		existingResourceConfigMap.putAll(newResourceConfigMap);

		// Removed resolved resouce configurations that define multiple resource
		resolvedMultiResourceConfigKeys.forEach(existingResourceConfigMap::remove);
	}

	/**
	 * Resolve the given <code>resourceConfigEntry</code>, generate new resource configurations and update the new resource configuration map.
	 * <br>Each resolved resource configuration key is saved for removal.
	 *
	 * @param resolvedMultiResourceKeys Set of keys that are resolved
	 * @param newResourceConfigMap      Map containing the new {@link ResourceConfig} instances
	 * @param multiResourceConfigEntry  {@link ResourceConfig} entry containing the multiple resources resource configuration
	 */
	private void resolveMultiResourceConfig(
		final Set<String> resolvedMultiResourceKeys,
		final Map<String, ResourceConfig> newResourceConfigMap,
		final Entry<String, ResourceConfig> multiResourceConfigEntry
	) {
		final String multiResourceConfigKey = multiResourceConfigEntry.getKey();
		// Mark the original key for removal after processing
		resolvedMultiResourceKeys.add(multiResourceConfigKey);

		// Retrieve the original ResourceConfig and its attributes
		final ResourceConfig multiResourceConfig = multiResourceConfigEntry.getValue();
		final Map<String, String> originalAttributes = multiResourceConfig.getAttributes();

		// Handle deprecated legacy attribute key (e.g., host.names)
		final String legacyHostnames = originalAttributes.get(MULTI_HOST_ATTRIBUTE_KEY);
		if (legacyHostnames != null) {
			// Map legacy host.names to host.name and remove the deprecated key
			originalAttributes.put(AGENT_RESOURCE_HOST_NAME_ATTRIBUTE_KEY, legacyHostnames);
			originalAttributes.remove(MULTI_HOST_ATTRIBUTE_KEY);
		}

		// Prepare a map to hold split attribute values, skipping null values
		final Map<String, List<String>> attributeValues = determineAttributeValues(originalAttributes);

		// Determine the maximum number of resources to create based on the largest attribute list
		final int maxSize = attributeValues.values().stream().mapToInt(List::size).max().orElse(1);

		// Create individual resources
		for (int i = 0; i < maxSize; i++) {
			// Create a copy of the original ResourceConfig
			final ResourceConfig newResourceConfig = multiResourceConfig.copy();
			final Map<String, String> newAttributes = newResourceConfig.getAttributes();

			normalizeProtocolHostnames(newResourceConfig.getProtocols(), i);

			// Assign values for each attribute based on the current index
			for (Map.Entry<String, List<String>> attrEntry : attributeValues.entrySet()) {
				final String key = attrEntry.getKey();
				final List<String> values = attrEntry.getValue();

				// Determine the value to assign: use the value at index i if available, else the last value
				final String assignedValue;
				if (i < values.size()) {
					assignedValue = values.get(i);
				} else {
					assignedValue = values.get(values.size() - 1);
				}

				newAttributes.put(key, assignedValue);
			}

			// Generate a unique key for the new resource configuration
			String additionalIdentification = null;
			final String hostname = newAttributes.get(AGENT_RESOURCE_HOST_NAME_ATTRIBUTE_KEY);
			if (hostname != null) {
				additionalIdentification = hostname;
			}
			// Make sure the key is unique in case the host.name or host.id attribute is not available
			String uniqueKey = String.format("%s-%d", multiResourceConfigKey, i + 1);
			if (additionalIdentification != null) {
				uniqueKey = String.format("%s-%s", uniqueKey, additionalIdentification);
			}
			newResourceConfigMap.put(uniqueKey, newResourceConfig);
		}
	}

	/**
	 * Normalize the protocol hostnames based on the given resource index.
	 * <br>For each protocol, the hostname is split and the value at the given index is assigned.
	 *
	 * @param protocols     Map of protocols containing the hostname to normalize
	 * @param resourceIndex Index of the resource to assign the hostname
	 */
	private void normalizeProtocolHostnames(final Map<String, IConfiguration> protocols, final int resourceIndex) {
		if (protocols == null) {
			return;
		}

		for (IConfiguration protocol : protocols.values()) {
			final String hostname = protocol.getHostname();
			if (hostname != null && hostname.contains(MULTI_VALUE_SEPARATOR)) {
				final String[] hostnames = hostname.split(MULTI_VALUE_SEPARATOR);
				if (resourceIndex < hostnames.length) {
					protocol.setHostname(hostnames[resourceIndex]);
				} else {
					protocol.setHostname(hostnames[hostnames.length - 1]);
				}
			}
		}
	}

	/**
	 * Split multi-valued attributes into separate values.
	 *
	 * @param originalAttributes Original attributes to split
	 * @return Map of attribute names to lists of values
	 */
	private Map<String, List<String>> determineAttributeValues(final Map<String, String> originalAttributes) {
		final Map<String, List<String>> attributeValues = new HashMap<>();

		// Iterate over each attribute to split multi-valued attributes
		for (Map.Entry<String, String> entry : originalAttributes.entrySet()) {
			final String key = entry.getKey();
			final String value = entry.getValue();

			// Skip any attributes with null value
			if (value == null) {
				continue;
			}

			// Split the value by the multi value separator
			final List<String> splitValues = Arrays.stream(value.split(MULTI_VALUE_SEPARATOR)).collect(Collectors.toList());

			attributeValues.put(key, splitValues);
		}

		return attributeValues;
	}
}
