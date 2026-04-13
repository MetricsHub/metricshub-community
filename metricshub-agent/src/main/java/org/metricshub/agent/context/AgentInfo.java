package org.metricshub.agent.context;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * MetricsHub Agent
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

import static org.metricshub.agent.helper.AgentConstants.AGENT_INFO_BUILD_DATE_NUMBER_ATTRIBUTE_KEY;
import static org.metricshub.agent.helper.AgentConstants.AGENT_INFO_BUILD_NUMBER_ATTRIBUTE_KEY;
import static org.metricshub.agent.helper.AgentConstants.AGENT_INFO_CC_VERSION_NUMBER_ATTRIBUTE_KEY;
import static org.metricshub.agent.helper.AgentConstants.AGENT_INFO_NAME_ATTRIBUTE_KEY;
import static org.metricshub.agent.helper.AgentConstants.AGENT_INFO_VERSION_ATTRIBUTE_KEY;
import static org.metricshub.agent.helper.AgentConstants.AGENT_RESOURCE_AGENT_HOST_NAME_ATTRIBUTE_KEY;
import static org.metricshub.agent.helper.AgentConstants.AGENT_RESOURCE_HOST_NAME_ATTRIBUTE_KEY;
import static org.metricshub.agent.helper.AgentConstants.AGENT_RESOURCE_HOST_TYPE_ATTRIBUTE_KEY;
import static org.metricshub.agent.helper.AgentConstants.AGENT_RESOURCE_OS_TYPE_ATTRIBUTE_KEY;
import static org.metricshub.agent.helper.AgentConstants.AGENT_RESOURCE_SERVICE_NAME_ATTRIBUTE_KEY;
import static org.metricshub.agent.helper.AgentConstants.APPLICATION_YAML_FILE_NAME;
import static org.metricshub.agent.helper.AgentConstants.OBJECT_MAPPER;
import static org.metricshub.engine.common.helpers.MetricsHubConstants.OTEL_AIX_OS_TYPE;
import static org.metricshub.engine.common.helpers.MetricsHubConstants.OTEL_FREE_BSD_OS_TYPE;
import static org.metricshub.engine.common.helpers.MetricsHubConstants.OTEL_HPUX_OS_TYPE;
import static org.metricshub.engine.common.helpers.MetricsHubConstants.OTEL_LINUX_OS_TYPE;
import static org.metricshub.engine.common.helpers.MetricsHubConstants.OTEL_MAC_OS_X_OS_TYPE;
import static org.metricshub.engine.common.helpers.MetricsHubConstants.OTEL_NET_BSD_OS_TYPE;
import static org.metricshub.engine.common.helpers.MetricsHubConstants.OTEL_OPEN_BSD_OS_TYPE;
import static org.metricshub.engine.common.helpers.MetricsHubConstants.OTEL_SOLARIS_OS_TYPE;
import static org.metricshub.engine.common.helpers.MetricsHubConstants.OTEL_SUN_OS_TYPE;
import static org.metricshub.engine.common.helpers.MetricsHubConstants.OTEL_WINDOWS_OS_TYPE;

import java.io.IOException;
import java.net.InetAddress;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.metricshub.agent.context.ApplicationProperties.Project;
import org.metricshub.engine.common.helpers.JsonHelper;
import org.metricshub.engine.common.helpers.LocalOsHandler;
import org.metricshub.engine.common.helpers.LocalOsHandler.ILocalOs;
import org.metricshub.engine.common.helpers.StringHelper;
import org.springframework.core.io.ClassPathResource;

/**
 * AgentInfo represents information about the MetricsHub agent, including attributes such as name, version,
 * build number, build date, Health Connector (HC) version, and OpenTelemetry (OTel) version. It also provides
 * metric attributes and resource attributes, and is responsible for reading internal application configuration.
 */
@Slf4j
public class AgentInfo {

	/**
	 * Metric name for MetricsHub agent.
	 */
	public static final String METRICS_HUB_AGENT_METRIC_NAME = "metricshub.agent.info";

	// @formatter:off
	// Map of local OS type that can be detected by the engine to the OTEL OS type
	private static final Map<ILocalOs, String> LOCAL_OS_TO_OTEL_OS_TYPE = Map.of(
		LocalOsHandler.WINDOWS, OTEL_WINDOWS_OS_TYPE,
		LocalOsHandler.LINUX, OTEL_LINUX_OS_TYPE,
		LocalOsHandler.SUN, OTEL_SUN_OS_TYPE,
		LocalOsHandler.HP, OTEL_HPUX_OS_TYPE,
		LocalOsHandler.SOLARIS, OTEL_SOLARIS_OS_TYPE,
		LocalOsHandler.FREE_BSD, OTEL_FREE_BSD_OS_TYPE,
		LocalOsHandler.NET_BSD, OTEL_NET_BSD_OS_TYPE,
		LocalOsHandler.OPEN_BSD, OTEL_OPEN_BSD_OS_TYPE,
		LocalOsHandler.MAC_OS_X, OTEL_MAC_OS_X_OS_TYPE,
		LocalOsHandler.AIX, OTEL_AIX_OS_TYPE
	);
	// @formatter:on

	private static final String UNKNOWN = "unknown";

	private static final String AGENT_HOSTNAME = StringHelper.getValue(
		() -> InetAddress.getLocalHost().getCanonicalHostName(),
		UNKNOWN
	);

	private static final String OTEL_LOCAL_OS_TYPE = StringHelper.getValue(AgentInfo::getLocalOsTypeForOtel, UNKNOWN);

	@Getter
	private ApplicationProperties applicationProperties;

	@Getter
	private Map<String, String> attributes = new HashMap<>();

	/**
	 * Creates an instance of AgentInfo, initializing metric and resource attributes
	 * based on internal application configuration.
	 */
	public AgentInfo() {
		// Read the application.yaml file
		final ClassPathResource classPathResource = new ClassPathResource(APPLICATION_YAML_FILE_NAME);
		try {
			applicationProperties =
				JsonHelper.deserialize(OBJECT_MAPPER, classPathResource.getInputStream(), ApplicationProperties.class);
		} catch (IOException e) {
			log.error("Cannot read internal application configuration file: {}", classPathResource.getPath());
			log.debug("Exception: ", e);
			throw new IllegalStateException("Cannot read application.yaml file.", e);
		}

		final Project project = applicationProperties.project();

		// Update resource attributes
		// @formatter:off
		attributes =
			Map.of(
				AGENT_RESOURCE_SERVICE_NAME_ATTRIBUTE_KEY, project.name(),
				AGENT_RESOURCE_HOST_NAME_ATTRIBUTE_KEY, AGENT_HOSTNAME,
				AGENT_RESOURCE_AGENT_HOST_NAME_ATTRIBUTE_KEY, AGENT_HOSTNAME,
				AGENT_RESOURCE_HOST_TYPE_ATTRIBUTE_KEY, "compute",
				AGENT_RESOURCE_OS_TYPE_ATTRIBUTE_KEY, OTEL_LOCAL_OS_TYPE,
				AGENT_INFO_NAME_ATTRIBUTE_KEY, project.name(),
				AGENT_INFO_VERSION_ATTRIBUTE_KEY, project.version(),
				AGENT_INFO_BUILD_NUMBER_ATTRIBUTE_KEY, applicationProperties.buildNumber(),
				AGENT_INFO_BUILD_DATE_NUMBER_ATTRIBUTE_KEY, applicationProperties.buildDate(),
				AGENT_INFO_CC_VERSION_NUMBER_ATTRIBUTE_KEY, applicationProperties.ccVersion()
			);
		// @formatter:on
	}

	/**
	 * Get the detected OS then return the value as specified by OpenTelemetry
	 *
	 * @return String value
	 */
	private static String getLocalOsTypeForOtel() {
		final Optional<ILocalOs> localOs = LocalOsHandler.getOS();
		if (localOs.isPresent()) {
			return LOCAL_OS_TO_OTEL_OS_TYPE.getOrDefault(localOs.get(), UNKNOWN);
		}
		return UNKNOWN;
	}
}
