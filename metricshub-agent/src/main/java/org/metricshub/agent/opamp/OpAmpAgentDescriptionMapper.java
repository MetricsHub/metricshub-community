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

import static org.metricshub.agent.helper.AgentConstants.AGENT_INFO_BUILD_NUMBER_ATTRIBUTE_KEY;
import static org.metricshub.agent.helper.AgentConstants.AGENT_INFO_VERSION_ATTRIBUTE_KEY;
import static org.metricshub.agent.helper.AgentConstants.AGENT_RESOURCE_HOST_NAME_ATTRIBUTE_KEY;
import static org.metricshub.agent.helper.AgentConstants.AGENT_RESOURCE_OS_TYPE_ATTRIBUTE_KEY;
import static org.metricshub.agent.helper.AgentConstants.AGENT_RESOURCE_SERVICE_NAME_ATTRIBUTE_KEY;

import java.util.Locale;
import java.util.Map;
import org.metricshub.agent.context.AgentInfo;
import org.metricshub.agent.upgrade.runner.DeploymentKind;
import org.metricshub.opamp.proto.AgentDescription;
import org.metricshub.opamp.proto.AnyValue;
import org.metricshub.opamp.proto.KeyValue;

/**
 * Maps the MetricsHub {@link AgentInfo} attributes to the OpAMP {@code AgentDescription} message.
 * <p>
 * Identifying attributes follow the OpAMP recommendations ({@code service.name},
 * {@code service.version}, {@code host.name}); non-identifying attributes carry the material the
 * OpAMP server needs to select the right upgrade artifact ({@code os.type}, {@code host.arch},
 * {@code build_number}, {@code installer.type}).
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

	private OpAmpAgentDescriptionMapper() {}

	/**
	 * Builds the OpAMP {@code AgentDescription} from the agent information.
	 *
	 * @param agentInfo      the agent information
	 * @param deploymentKind how MetricsHub was deployed on this host, reported as the lowercase
	 *                       {@code installer.type} attribute ({@code deb}, {@code rpm}, {@code msi},
	 *                       {@code archive}, {@code docker}); skipped when {@code null}
	 * @return the corresponding {@code AgentDescription}
	 */
	public static AgentDescription map(final AgentInfo agentInfo, final DeploymentKind deploymentKind) {
		final Map<String, String> attributes = agentInfo.getAttributes();
		final AgentDescription.Builder builder = AgentDescription.newBuilder();

		addAttribute(
			builder,
			true,
			AGENT_RESOURCE_SERVICE_NAME_ATTRIBUTE_KEY,
			attributes.get(AGENT_RESOURCE_SERVICE_NAME_ATTRIBUTE_KEY)
		);
		addAttribute(builder, true, SERVICE_VERSION_ATTRIBUTE_KEY, attributes.get(AGENT_INFO_VERSION_ATTRIBUTE_KEY));
		addAttribute(
			builder,
			true,
			AGENT_RESOURCE_HOST_NAME_ATTRIBUTE_KEY,
			attributes.get(AGENT_RESOURCE_HOST_NAME_ATTRIBUTE_KEY)
		);

		addAttribute(
			builder,
			false,
			AGENT_RESOURCE_OS_TYPE_ATTRIBUTE_KEY,
			attributes.get(AGENT_RESOURCE_OS_TYPE_ATTRIBUTE_KEY)
		);
		addAttribute(builder, false, HOST_ARCH_ATTRIBUTE_KEY, System.getProperty("os.arch"));
		addAttribute(
			builder,
			false,
			AGENT_INFO_BUILD_NUMBER_ATTRIBUTE_KEY,
			attributes.get(AGENT_INFO_BUILD_NUMBER_ATTRIBUTE_KEY)
		);
		if (deploymentKind != null) {
			addAttribute(builder, false, INSTALLER_TYPE_ATTRIBUTE_KEY, deploymentKind.name().toLowerCase(Locale.ROOT));
		}

		return builder.build();
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
