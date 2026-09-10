package org.metricshub.agent.opamp;

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

import static org.metricshub.agent.helper.AgentConstants.AGENT_INFO_VERSION_ATTRIBUTE_KEY;
import static org.metricshub.agent.helper.AgentConstants.AGENT_RESOURCE_HOST_NAME_ATTRIBUTE_KEY;
import static org.metricshub.agent.helper.AgentConstants.AGENT_RESOURCE_SERVICE_NAME_ATTRIBUTE_KEY;

import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.metricshub.agent.config.AgentConfig;
import org.metricshub.agent.config.OpAmpConfig;
import org.metricshub.agent.context.AgentInfo;
import org.metricshub.agent.upgrade.runner.DeploymentKind;
import org.metricshub.opamp.proto.AgentDescription;
import org.metricshub.opamp.proto.AnyValue;
import org.metricshub.opamp.proto.KeyValue;

/**
 * Maps the MetricsHub agent attributes to the OpAMP {@code AgentDescription} message.
 * <p>
 * The reported attributes are merged in three layers, each one overriding the previous:
 * </p>
 * <ol>
 *   <li>the pre-built {@link AgentInfo} attributes;</li>
 *   <li>the agent-level {@code attributes:} section of {@code metricshub.yaml}, exactly as they
 *       apply to the agent's own resource;</li>
 *   <li>the {@code opamp: attributes:} section, which always wins: it tailors the identity exposed
 *       to the fleet manager without touching the attributes attached to the exported metrics.</li>
 * </ol>
 * <p>
 * Identifying attributes follow the OpAMP recommendations ({@code service.name},
 * {@code service.version}, {@code host.name}); everything else is reported as non-identifying,
 * along with the material the OpAMP server needs to select the right upgrade artifact
 * ({@code host.arch}, {@code installer.type}).
 * </p>
 */
public class OpAmpAgentDescriptionMapper {

	/**
	 * OpAMP identifying attribute key for the service version.
	 */
	static final String SERVICE_VERSION_ATTRIBUTE_KEY = "service.version";

	/**
	 * OpAMP non-identifying attribute key for the host CPU architecture.
	 */
	static final String HOST_ARCH_ATTRIBUTE_KEY = "host.arch";

	/**
	 * OpAMP non-identifying attribute key for the installer type. Without it an OpAMP server
	 * cannot tell a Debian host from an RPM host and must not offer any artifact.
	 */
	static final String INSTALLER_TYPE_ATTRIBUTE_KEY = "installer.type";

	/**
	 * Attribute keys consumed by the identifying attributes: they must not be repeated in the
	 * non-identifying block.
	 */
	private static final Set<String> IDENTIFYING_SOURCE_KEYS = Set.of(
		AGENT_RESOURCE_SERVICE_NAME_ATTRIBUTE_KEY,
		AGENT_RESOURCE_HOST_NAME_ATTRIBUTE_KEY,
		AGENT_INFO_VERSION_ATTRIBUTE_KEY,
		SERVICE_VERSION_ATTRIBUTE_KEY
	);

	private OpAmpAgentDescriptionMapper() {}

	/**
	 * Builds the OpAMP {@code AgentDescription} from the agent attributes.
	 *
	 * @param agentInfo      the agent information holding the pre-built attributes
	 * @param agentConfig    the agent configuration providing the {@code attributes:} section and the
	 *                       {@code opamp: attributes:} overrides; may be {@code null}
	 * @param deploymentKind how MetricsHub was deployed on this host, reported as the lowercase
	 *                       {@code installer.type} attribute ({@code deb}, {@code rpm}, {@code msi},
	 *                       {@code archive}, {@code docker}); skipped when {@code null}
	 * @return the corresponding {@code AgentDescription}
	 */
	public static AgentDescription map(
		final AgentInfo agentInfo,
		final AgentConfig agentConfig,
		final DeploymentKind deploymentKind
	) {
		final Map<String, String> attributes = resolveAttributes(agentInfo, agentConfig);

		final AgentDescription.Builder builder = AgentDescription.newBuilder();

		addAttribute(
			builder,
			true,
			AGENT_RESOURCE_SERVICE_NAME_ATTRIBUTE_KEY,
			attributes.get(AGENT_RESOURCE_SERVICE_NAME_ATTRIBUTE_KEY)
		);
		addAttribute(builder, true, SERVICE_VERSION_ATTRIBUTE_KEY, resolveServiceVersion(attributes));
		addAttribute(
			builder,
			true,
			AGENT_RESOURCE_HOST_NAME_ATTRIBUTE_KEY,
			attributes.get(AGENT_RESOURCE_HOST_NAME_ATTRIBUTE_KEY)
		);

		// Derived attributes first: a user who explicitly configured host.arch or installer.type
		// overrides them below
		final Map<String, String> nonIdentifyingAttributes = new TreeMap<>();
		nonIdentifyingAttributes.put(HOST_ARCH_ATTRIBUTE_KEY, System.getProperty("os.arch"));
		if (deploymentKind != null) {
			nonIdentifyingAttributes.put(INSTALLER_TYPE_ATTRIBUTE_KEY, deploymentKind.name().toLowerCase(Locale.ROOT));
		}
		attributes.forEach((key, value) -> {
			if (!IDENTIFYING_SOURCE_KEYS.contains(key)) {
				nonIdentifyingAttributes.put(key, value);
			}
		});

		nonIdentifyingAttributes.forEach((key, value) -> addAttribute(builder, false, key, value));

		return builder.build();
	}

	/**
	 * Merges the attributes reported to the OpAMP server: the pre-built agent attributes, then the
	 * agent-level {@code attributes:}, then the {@code opamp: attributes:} which always win.
	 *
	 * @param agentInfo   the agent information holding the pre-built attributes; may be {@code null}
	 * @param agentConfig the agent configuration; may be {@code null}
	 * @return the effective attributes, sorted by key
	 */
	private static Map<String, String> resolveAttributes(final AgentInfo agentInfo, final AgentConfig agentConfig) {
		// Sorted so that two consecutive reports of the same attributes produce the same message and
		// the client does not detect a spurious change
		final Map<String, String> attributes = new TreeMap<>();
		if (agentInfo != null) {
			mergeAttributes(agentInfo.getAttributes(), attributes);
		}
		if (agentConfig != null) {
			mergeAttributes(agentConfig.getAttributes(), attributes);
			final OpAmpConfig opAmpConfig = agentConfig.getOpamp();
			if (opAmpConfig != null) {
				// Merged last: the opamp: attributes always override the others
				mergeAttributes(opAmpConfig.getAttributes(), attributes);
			}
		}
		return attributes;
	}

	/**
	 * Copies the source attributes over the destination ones, ignoring a null source and null keys.
	 *
	 * @param source      the attributes to merge; may be {@code null}
	 * @param destination the destination attributes
	 */
	private static void mergeAttributes(final Map<String, String> source, final Map<String, String> destination) {
		if (source == null) {
			return;
		}
		source.forEach((key, value) -> {
			// A YAML entry without a key cannot land in the sorted map: TreeMap rejects null keys
			if (key != null) {
				destination.put(key, value);
			}
		});
	}

	/**
	 * Resolves the identifying {@code service.version}: an explicitly configured {@code service.version}
	 * wins over the pre-built {@code version} attribute.
	 *
	 * @param attributes the effective agent attributes
	 * @return the service version, possibly {@code null}
	 */
	private static String resolveServiceVersion(final Map<String, String> attributes) {
		final String serviceVersion = attributes.get(SERVICE_VERSION_ATTRIBUTE_KEY);
		return serviceVersion != null && !serviceVersion.isBlank()
			? serviceVersion
			: attributes.get(AGENT_INFO_VERSION_ATTRIBUTE_KEY);
	}

	/**
	 * Adds a string attribute to the description builder, skipping null or blank values.
	 *
	 * @param builder     the description builder
	 * @param identifying whether the attribute is identifying
	 * @param key         the attribute key
	 * @param value       the attribute value
	 */
	private static void addAttribute(
		final AgentDescription.Builder builder,
		final boolean identifying,
		final String key,
		final String value
	) {
		if (value == null || value.isBlank()) {
			return;
		}
		final KeyValue keyValue = KeyValue.newBuilder()
			.setKey(key)
			.setValue(AnyValue.newBuilder().setStringValue(value).build())
			.build();
		if (identifying) {
			builder.addIdentifyingAttributes(keyValue);
		} else {
			builder.addNonIdentifyingAttributes(keyValue);
		}
	}
}
