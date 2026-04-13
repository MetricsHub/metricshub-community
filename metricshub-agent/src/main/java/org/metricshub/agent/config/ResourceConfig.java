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

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Builder.Default;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.metricshub.agent.deserialization.AttributesDeserializer;
import org.metricshub.agent.deserialization.ExtensionProtocolsDeserializer;
import org.metricshub.agent.deserialization.MonitorJobsDeserializer;
import org.metricshub.engine.configuration.AdditionalConnector;
import org.metricshub.engine.configuration.ConnectorVariables;
import org.metricshub.engine.configuration.IConfiguration;
import org.metricshub.engine.connector.model.Connector;
import org.metricshub.engine.connector.model.monitor.MonitorJob;
import org.metricshub.engine.deserialization.TimeDeserializer;

/**
 * Configuration class representing the configuration for a resource in
 * MetricsHub. It includes settings such as logger level, output directory,
 * collection period, discovery cycle, alerting system configuration, sequential
 * execution, resolving hostname to FQDN, job timeout, attributes, metrics,
 * protocols configuration, variables, selected connectors, excluded connectors,
 * monitor jobs, included connector tags, and a connector instance.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@EqualsAndHashCode
public class ResourceConfig {

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
	@JsonDeserialize(using = ExtensionProtocolsDeserializer.class)
	private Map<String, IConfiguration> protocols = new HashMap<>();

	@Default
	@JsonSetter(nulls = SKIP)
	private Set<String> connectors = new HashSet<>();

	@JsonSetter(nulls = SKIP)
	@JsonDeserialize(using = MonitorJobsDeserializer.class)
	@Default
	private Map<String, MonitorJob> monitors = new HashMap<>();

	@Default
	@JsonSetter(nulls = SKIP)
	private Map<String, AdditionalConnector> additionalConnectors = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

	@JsonIgnore
	private Connector connector;

	@Default
	@JsonSetter(nulls = SKIP)
	private List<String> enrichments = new ArrayList<>();

	private String stateSetCompression;

	/**
	 * Creates and returns a shallow copy of all the fields in this
	 * ResourceConfig object except the attributes and protocols maps which are deeply copied.
	 *
	 * @return A new ResourceConfig object with the same property values as this one.
	 */
	public ResourceConfig copy() {
		return ResourceConfig
			.builder()
			.loggerLevel(loggerLevel)
			.outputDirectory(outputDirectory)
			.collectPeriod(collectPeriod)
			.discoveryCycle(discoveryCycle)
			.alertingSystemConfig(alertingSystemConfig)
			.sequential(sequential)
			.enableSelfMonitoring(enableSelfMonitoring)
			.monitorFilters(monitorFilters)
			.resolveHostnameToFqdn(resolveHostnameToFqdn)
			.jobTimeout(jobTimeout)
			.attributes(
				attributes
					.entrySet()
					.stream()
					.collect(
						Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (oldValue, newValue) -> oldValue, HashMap::new)
					)
			)
			.metrics(metrics)
			// Deep copies the IConfigurations
			.protocols(
				protocols.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().copy()))
			)
			.additionalConnectors(additionalConnectors)
			.connectors(connectors)
			.connector(connector)
			.enrichments(enrichments != null ? new ArrayList<>(enrichments) : null)
			.stateSetCompression(stateSetCompression)
			.build();
	}

	/**
	 * Retrieves the set of connector variables where map keys are connectorIds and values are connectorVariables.
	 * @return the map of connectorIds and their variables values.
	 */
	public Map<String, ConnectorVariables> getConnectorVariables() {
		return Optional
			.ofNullable(additionalConnectors)
			.map(map ->
				map
					.entrySet()
					.stream()
					.filter(entry -> entry.getValue() != null && entry.getValue().getVariables() != null) // Filter out null values
					.collect(
						Collectors.toMap(
							Map.Entry::getKey,
							entry -> new ConnectorVariables(new HashMap<>(entry.getValue().getVariables())) // Create new object with copied map
						)
					)
			)
			.orElseGet(Collections::emptyMap); // Return an empty map if additionalConnectors is null
	}
}
