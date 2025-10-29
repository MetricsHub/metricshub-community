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

import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Builder.Default;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.metricshub.agent.deserialization.AttributesDeserializer;
import org.metricshub.engine.deserialization.TimeDeserializer;

/**
 * Configuration class representing the configuration for a resource group in
 * MetricsHub. It includes settings such as logger level, output directory,
 * collection period, discovery cycle, alerting system configuration, sequential
 * execution, resolving hostname to FQDN, job timeout, attributes, metrics, and
 * nested resource configurations.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@EqualsAndHashCode
public class ResourceGroupConfig {

	private String loggerLevel;
	private String outputDirectory;

	@JsonDeserialize(using = TimeDeserializer.class)
	private Long collectPeriod;

	private Integer discoveryCycle;
	private AlertingSystemConfig alertingSystemConfig;
	private Boolean sequential;
	private Boolean enableSelfMonitoring;
	private Boolean resolveHostnameToFqdn;

	@JsonSetter(nulls = SKIP)
	private Set<String> monitorFilters;

	@JsonDeserialize(using = TimeDeserializer.class)
	private Long jobTimeout;

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

	private String stateSetCompression;
}
