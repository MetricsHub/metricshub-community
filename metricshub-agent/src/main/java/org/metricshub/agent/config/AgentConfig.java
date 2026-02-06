package org.metricshub.agent.config;

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

import static com.fasterxml.jackson.annotation.Nulls.SKIP;
import static org.metricshub.agent.helper.AgentConstants.APPLICATION_YAML_FILE_NAME;
import static org.metricshub.agent.helper.AgentConstants.OBJECT_MAPPER;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Builder.Default;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.metricshub.agent.config.otel.OtelCollectorConfig;
import org.metricshub.agent.deserialization.AttributesDeserializer;
import org.metricshub.agent.helper.AgentConstants;
import org.metricshub.agent.opentelemetry.OtelConfigConstants;
import org.metricshub.engine.common.helpers.JsonHelper;
import org.metricshub.engine.common.helpers.MetricsHubConstants;
import org.metricshub.engine.deserialization.TimeDeserializer;
import org.springframework.core.io.ClassPathResource;

/**
 * AgentConfig represents the configuration for the MetricsHub agent. It includes settings for
 * job pool size, logger level, output directory, collect period, discovery cycle, alerting system configuration,
 * sequential mode, hostname resolution, job timeout, OpenTelemetry (OTel) collector configuration,
 * exporter configuration, custom attributes, custom metrics, and resource group configurations.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class AgentConfig {

	/**
	 * Default problem template for alerts
	 */
	public static final String PROBLEM_DEFAULT_TEMPLATE =
		"Problem on ${FQDN} with ${MONITOR_NAME}.${NEWLINE}${NEWLINE}${ALERT_DETAILS}${NEWLINE}${NEWLINE}${FULLREPORT}";
	/**
	 * Default job pool size
	 */
	public static final int DEFAULT_JOB_POOL_SIZE = 20;
	/**
	 * Default job collect period in seconds
	 */
	public static final long DEFAULT_COLLECT_PERIOD = 120;
	/**
	 * Default discovery cycle in minutes
	 */
	public static final int DEFAULT_DISCOVERY_CYCLE = 30;

	@Default
	private int jobPoolSize = DEFAULT_JOB_POOL_SIZE;

	@Default
	@JsonSetter(nulls = SKIP)
	private String loggerLevel = "error";

	@Default
	@JsonSetter(nulls = SKIP)
	private String outputDirectory = AgentConstants.DEFAULT_OUTPUT_DIRECTORY.toString();

	@Default
	@JsonDeserialize(using = TimeDeserializer.class)
	private long collectPeriod = DEFAULT_COLLECT_PERIOD;

	@Default
	private int discoveryCycle = DEFAULT_DISCOVERY_CYCLE;

	@Default
	@JsonSetter(nulls = SKIP)
	private AlertingSystemConfig alertingSystemConfig = AlertingSystemConfig.builder().build();

	private boolean sequential;

	@Default
	private boolean enableSelfMonitoring = true;

	private boolean resolveHostnameToFqdn;

	@JsonSetter(nulls = SKIP)
	private Set<String> monitorFilters;

	@Default
	@JsonSetter(nulls = SKIP)
	@JsonDeserialize(using = TimeDeserializer.class)
	private long jobTimeout = MetricsHubConstants.DEFAULT_JOB_TIMEOUT;

	@Default
	@JsonSetter(nulls = SKIP)
	private OtelCollectorConfig otelCollector = OtelCollectorConfig.builder().build();

	@Default
	@JsonSetter(nulls = SKIP)
	@JsonProperty("otel")
	private Map<String, String> otelConfig = OtelConfigConstants.DEFAULT_CONFIGURATION;

	@Default
	@JsonSetter(nulls = SKIP)
	@JsonDeserialize(using = AttributesDeserializer.class)
	private Map<String, String> attributes = new HashMap<>();

	@Default
	@JsonSetter(nulls = SKIP)
	private Map<String, Double> metrics = new HashMap<>();

	@Default
	@JsonSetter(nulls = SKIP)
	private Map<String, ResourceConfig> resources = new HashMap<>();

	@Default
	@JsonSetter(nulls = SKIP)
	private Map<String, ResourceGroupConfig> resourceGroups = new HashMap<>();

	@Default
	@JsonSetter(nulls = SKIP)
	private List<String> enrichments = new ArrayList<>();

	@Default
	@JsonSetter(nulls = SKIP)
	private String stateSetCompression = StateSetMetricCompression.SUPPRESS_ZEROS;

	@JsonSetter(nulls = SKIP)
	private String patchDirectory;

	@Default
	@JsonSetter(nulls = SKIP)
	@JsonProperty("web")
	private Map<String, String> webConfig = loadWebConfig();

	/**
	 * Build a new empty instance
	 *
	 * @return {@link AgentConfig} object
	 */
	public static AgentConfig empty() {
		return AgentConfig.builder().build();
	}

	/**
	 * Load the web configuration from the application.yaml file.
	 *
	 * @return Map containing the web configuration properties.
	 */
	private static Map<String, String> loadWebConfig() {
		// Read the application.yaml file
		final var classPathResource = new ClassPathResource(APPLICATION_YAML_FILE_NAME);
		try {
			return JsonHelper.deserialize(OBJECT_MAPPER, classPathResource.getInputStream(), WebConfig.class).web();
		} catch (Exception e) {
			throw new IllegalStateException("Cannot read application.yaml file.", e);
		}
	}

	/**
	 * Record representing the web configuration section of the {@value APPLICATION_YAML_FILE_NAME}
	 */
	private record WebConfig(Map<String, String> web) {}
}
