package org.metricshub.engine.telemetry;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * MetricsHub Engine
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

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.metricshub.engine.common.helpers.JsonHelper;
import org.metricshub.engine.connector.model.metric.MetricDefinition;

/**
 * Singleton loader for the metricshub-host-metrics.yaml definitions.
 * Loads and caches the metric definitions from the classpath resource
 * so that MetricFactory can resolve host-level metrics without a connector.
 */
@Slf4j
public class HostMetricsDefinitionLoader {

	private static HostMetricsDefinitionLoader instance;
	private final Map<String, MetricDefinition> hostMetricDefinitions;

	/**
	 * Private constructor that loads and parses the host-metrics YAML from the classpath.
	 */
	private HostMetricsDefinitionLoader() {
		this.hostMetricDefinitions = loadFromClasspath();
	}

	/**
	 * Returns the singleton instance of {@link HostMetricsDefinitionLoader}.
	 *
	 * @return the singleton instance
	 */
	public static synchronized HostMetricsDefinitionLoader getInstance() {
		if (instance == null) {
			instance = new HostMetricsDefinitionLoader();
		}
		return instance;
	}

	/**
	 * Returns the {@link MetricDefinition} for the given metric name, or {@code null} if not found.
	 *
	 * @param metricName the metric name to look up
	 * @return the {@link MetricDefinition} or {@code null}
	 */
	public MetricDefinition getMetricDefinition(final String metricName) {
		return hostMetricDefinitions.get(metricName);
	}

	/**
	 * Returns an unmodifiable view of the full map of host metric definitions.
	 *
	 * @return unmodifiable map of metric name to {@link MetricDefinition}
	 */
	public Map<String, MetricDefinition> getHostMetricDefinitions() {
		return Collections.unmodifiableMap(hostMetricDefinitions);
	}

	/**
	 * Loads the host-metrics YAML from the classpath and parses it into a map of metric definitions.
	 *
	 * @return a map of metric name to {@link MetricDefinition}, or an empty map on failure
	 */
	private Map<String, MetricDefinition> loadFromClasspath() {
		try (InputStream is = getClass().getResourceAsStream("/metricshub-host-metrics.yaml")) {
			if (is == null) {
				log.warn("Could not find metricshub-host-metrics.yaml on the classpath. No host metric definitions loaded.");
				return new HashMap<>();
			}
			final ObjectMapper yamlMapper = JsonHelper.buildYamlMapper();
			final HostMetricsWrapper wrapper = yamlMapper.readValue(is, HostMetricsWrapper.class);
			if (wrapper != null && wrapper.getMetrics() != null) {
				return wrapper.getMetrics();
			}
			return new HashMap<>();
		} catch (Exception e) {
			log.error("Failed to load metricshub-host-metrics.yaml: {}", e.getMessage());
			log.debug("Exception details:", e);
			return new HashMap<>();
		}
	}

	/**
	 * Wrapper class for deserializing the top-level "metrics" key in the YAML file.
	 */
	@lombok.Data
	@lombok.NoArgsConstructor
	private static class HostMetricsWrapper {

		private Map<String, MetricDefinition> metrics;
	}
}
