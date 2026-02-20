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

import static org.metricshub.engine.common.helpers.MetricsHubConstants.COMMA;
import static org.metricshub.engine.common.helpers.MetricsHubConstants.EMPTY;
import static org.metricshub.engine.common.helpers.MetricsHubConstants.MONITOR_ATTRIBUTE_CONNECTOR_ID;
import static org.metricshub.engine.common.helpers.MetricsHubConstants.MONITOR_ATTRIBUTE_ID;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.Data;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.metricshub.engine.connector.model.Connector;
import org.metricshub.engine.connector.model.ConnectorStore;
import org.metricshub.engine.connector.model.metric.IMetricType;
import org.metricshub.engine.connector.model.metric.MetricDefinition;
import org.metricshub.engine.connector.model.metric.MetricType;
import org.metricshub.engine.connector.model.metric.StateSet;
import org.metricshub.engine.connector.model.monitor.AbstractMonitorJob;
import org.metricshub.engine.connector.model.monitor.MonitorJob;
import org.metricshub.engine.telemetry.metric.AbstractMetric;
import org.metricshub.engine.telemetry.metric.NumberMetric;
import org.metricshub.engine.telemetry.metric.StateSetMetric;

/**
 * Factory class for creating and collecting metrics for a Monitor based on information provided by a Connector.
 */
@Slf4j
@Data
public class MetricFactory {

	private static final Pattern METRIC_ATTRIBUTES_PATTERN = Pattern.compile("\\{(.*?)\\}");

	private String hostname;
	private ConnectorStore connectorStore;

	/**
	 * Constructs a MetricFactory with the given hostname and connector store.
	 *
	 * @param hostname       the hostname
	 * @param connectorStore the connector store used for metric type resolution (may be null)
	 */
	public MetricFactory(final String hostname, final ConnectorStore connectorStore) {
		this.hostname = hostname;
		this.connectorStore = connectorStore;
	}

	/**
	 * Collects a stateSet metric in the monitor, auto-resolving the metric type
	 * from the connector store, host-metrics definitions, or returning null if unknown.
	 *
	 * @param monitor    a given monitor
	 * @param metricName the metric's name
	 * @param value      the metric's value
	 * @param stateSet   array of states values. E.g. [ "ok", "degraded", "failed" ]
	 * @param collectTime the metric's collect time
	 * @return collected metric
	 */
	public StateSetMetric collectStateSetMetric(
		final Monitor monitor,
		final String metricName,
		final String value,
		final String[] stateSet,
		final long collectTime
	) {
		return collectStateSetMetric(
			monitor,
			metricName,
			value,
			stateSet,
			collectTime,
			resolveMetricType(connectorStore, getConnectorIdFromMonitor(monitor), monitor.getType(), metricName)
		);
	}

	/**
	 * This method sets a stateSet metric in the monitor
	 *
	 * @param monitor a given monitor
	 * @param metricName the metric's name
	 * @param value the metric's value
	 * @param stateSet array of states values. E.g. [ "ok", "degraded", "failed" ]
	 * @param collectTime the metric's collect time
	 * @param metricType the OpenTelemetry instrument type (e.g. "UpDownCounter", "Gauge")
	 * @return collected metric
	 */
	public StateSetMetric collectStateSetMetric(
		final Monitor monitor,
		final String metricName,
		final String value,
		final String[] stateSet,
		final long collectTime,
		final String metricType
	) {
		final StateSetMetric metric = monitor.getMetric(metricName, StateSetMetric.class);
		if (metric == null) {
			// Add the metric directly in the monitor's metrics
			final StateSetMetric newMetric = StateSetMetric
				.builder()
				.stateSet(stateSet)
				.name(metricName)
				.collectTime(collectTime)
				.value(value)
				.metricType(metricType)
				.attributes(extractAttributesFromMetricName(metricName))
				.build();
			monitor.addMetric(metricName, newMetric);
			return newMetric;
		} else {
			// stateSet, metricName, and metric's attributes will never change over the collects
			// so, we only set the value, collect time and metricType
			metric.setMetricType(metricType);
			metric.setValue(value);
			metric.setCollectTime(collectTime);
			return metric;
		}
	}

	/**
	 * This method extracts the metric attributes from its name
	 *
	 * @param metricName the metric's name
	 * @return a Map with attributes names as keys and attributes values as values
	 */
	public static Map<String, String> extractAttributesFromMetricName(final String metricName) {
		// Create a map to store the extracted attributes
		final Map<String, String> attributes = new HashMap<>();

		// Create a Matcher object
		final Matcher matcher = METRIC_ATTRIBUTES_PATTERN.matcher(metricName);

		if (matcher.find()) {
			final String attributeMap = matcher.group(1);

			// Split the attribute map into key-value pairs
			final String[] keyValuePairs = attributeMap.split(COMMA);

			// Iterate through the key-value pairs
			for (String pair : keyValuePairs) {
				final String[] parts = pair.trim().split("=");
				if (parts.length == 2) {
					// Set the key-value pair and remove the double quotes from the value
					attributes.put(parts[0], parts[1].replace("\"", EMPTY));
				}
			}
		}

		return attributes;
	}

	/**
	 * Collects a number metric in the monitor, auto-resolving the metric type
	 * from the connector store, host-metrics definitions, or returning null if unknown.
	 *
	 * @param monitor     a given monitor
	 * @param name        the metric's name
	 * @param value       the metric's value
	 * @param collectTime the metric's collect time
	 * @return collected metric
	 */
	public NumberMetric collectNumberMetric(
		final Monitor monitor,
		final String name,
		@NonNull final Double value,
		final Long collectTime
	) {
		return collectNumberMetric(
			monitor,
			name,
			value,
			collectTime,
			resolveMetricType(connectorStore, getConnectorIdFromMonitor(monitor), monitor.getType(), name)
		);
	}

	/**
	 * This method sets number metric in the monitor
	 *
	 * @param monitor a given monitor
	 * @param name the metric's name
	 * @param value the metric's value
	 * @param collectTime the metric's collect time
	 * @param metricType the OpenTelemetry instrument type (e.g. "Counter", "Gauge", "UpDownCounter")
	 * @return collected metric
	 */
	public NumberMetric collectNumberMetric(
		final Monitor monitor,
		final String name,
		@NonNull final Double value,
		final Long collectTime,
		final String metricType
	) {
		final NumberMetric metric = monitor.getMetric(name, NumberMetric.class);
		if (metric == null) {
			// Add the metric directly in the monitor's metrics
			final NumberMetric newMetric = NumberMetric
				.builder()
				.name(name)
				.collectTime(collectTime)
				.value(value)
				.metricType(metricType)
				.attributes(extractAttributesFromMetricName(name))
				.build();
			monitor.addMetric(name, newMetric);
			return newMetric;
		} else {
			// Update metricType (in case it was null on first collect)
			metric.setMetricType(metricType);
			metric.setValue(value);
			metric.setCollectTime(collectTime);

			// Compute rate for Counter metrics
			if ("Counter".equalsIgnoreCase(metricType)) {
				if (metric.getPreviousValue() != null && metric.getPreviousCollectTime() != null) {
					final long timeDeltaMs = collectTime - metric.getPreviousCollectTime();
					if (timeDeltaMs > 0) {
						metric.setRate((value - metric.getPreviousValue()) / (timeDeltaMs / 1000.0));
					} else {
						metric.setRate(null);
					}
				} else {
					metric.setRate(null);
				}
			} else {
				metric.setRate(null);
			}

			return metric;
		}
	}

	/**
	 * Collects a number metric in the monitor from a string value,
	 * auto-resolving the metric type from the connector store, host-metrics definitions, or returning null if unknown.
	 *
	 * @param monitor     a given monitor
	 * @param name        the metric's name
	 * @param value       the metric's value as a string
	 * @param collectTime the metric's collect time
	 * @return collected metric
	 */
	public NumberMetric collectNumberMetric(
		final Monitor monitor,
		final String name,
		@NonNull final String value,
		final Long collectTime
	) {
		try {
			return collectNumberMetric(monitor, name, Double.parseDouble(value), collectTime);
		} catch (Exception e) {
			log.warn(
				"Hostname {} - Cannot parse the {} value '{}' for monitor id {}. {} won't be collected",
				hostname,
				name,
				value,
				monitor.getAttributes().get(MONITOR_ATTRIBUTE_ID),
				name
			);
			return null;
		}
	}

	/**
	 * This method sets number metric in the monitor
	 *
	 * @param monitor a given monitor
	 * @param name the metric's name
	 * @param value the metric's value
	 * @param collectTime the metric's collect time
	 * @param metricType the OpenTelemetry instrument type (e.g. "Counter", "Gauge", "UpDownCounter")
	 * @return collected metric
	 */
	public NumberMetric collectNumberMetric(
		final Monitor monitor,
		final String name,
		@NonNull final String value,
		final Long collectTime,
		final String metricType
	) {
		try {
			return collectNumberMetric(monitor, name, Double.parseDouble(value), collectTime, metricType);
		} catch (Exception e) {
			log.warn(
				"Hostname {} - Cannot parse the {} value '{}' for monitor id {}. {} won't be collected",
				hostname,
				name,
				value,
				monitor.getAttributes().get(MONITOR_ATTRIBUTE_ID),
				name
			);
			return null;
		}
	}

	/**
	 * This method returns a metric definition based on the extracted metric name (metric name without attributes)
	 * @param connector a given connector
	 * @param monitor a given monitor
	 * @param metricName a given metric name
	 * @return MetricDefinition instance
	 */
	public MetricDefinition getMetricDefinitionFromExtractedMetricName(
		final Connector connector,
		final Monitor monitor,
		final String metricName
	) {
		// Get monitor metrics from connector
		final Map<String, MetricDefinition> metricDefinitionMap = connector.getMetrics();

		// Remove attribute parts from the metric name
		final String extractedName = extractName(metricName);

		// Retrieve the monitor job metric definitions
		final AbstractMonitorJob monitorJob = (AbstractMonitorJob) connector.getMonitors().get(monitor.getType());
		final Map<String, MetricDefinition> monitorMetricDefinitionMap = monitorJob != null
			? monitorJob.getMetrics()
			: null;

		// Monitor jobs metric definitions should override connector metric definitions
		if (monitorMetricDefinitionMap != null && monitorMetricDefinitionMap.containsKey(extractedName)) {
			return monitorMetricDefinitionMap.get(extractedName);
		}
		// Retrieve the metric definition using the extracted name
		final MetricDefinition connectorDef = metricDefinitionMap.get(extractedName);
		if (connectorDef != null) {
			return connectorDef;
		}

		return null;
	}

	/**
	 * This method collects a metric using the connector metrics
	 *
	 * @param connector    a given connector
	 * @param monitor      a given monitor
	 * @param strategyTime strategy time
	 * @param metricName   metric's name
	 * @param metricValue  metric's value
	 * @return AbstractMetric instance
	 */
	public AbstractMetric collectMetricUsingConnector(
		final Connector connector,
		final Monitor monitor,
		final long strategyTime,
		final String metricName,
		final String metricValue
	) {
		AbstractMetric metric = null;

		// Retrieve the metric definition using the extracted metric name
		final MetricDefinition metricDefinition = getMetricDefinitionFromExtractedMetricName(
			connector,
			monitor,
			metricName
		);

		// Retrieve metric attributes from metric's name
		final Map<String, String> metricAttributes = extractAttributesFromMetricName(metricName);

		// Create a boolean flag to check for the state attribute
		boolean hasStateAttribute = checkForStateAttribute(metricAttributes);

		// Resolve metricType: use connector definition if available, otherwise fallback to host-metrics YAML
		final String metricType = metricDefinition != null
			? resolveMetricType(metricDefinition)
			: resolveMetricTypeFromName(metricName);

		// Update the Number metric check
		if (metricDefinition == null || (metricDefinition.getType() instanceof MetricType) || hasStateAttribute) {
			metric = collectNumberMetric(monitor, metricName, metricValue, strategyTime, metricType);
		} else if (metricDefinition.getType() instanceof StateSet stateSetType) {
			// When metric type is stateSet
			final String[] stateSet = stateSetType.getSet().stream().toArray(String[]::new);
			metric = collectStateSetMetric(monitor, metricName, metricValue, stateSet, strategyTime, metricType);
		}
		return metric;
	}

	/**
	 * This method removes attribute parts from the metric name
	 *
	 * @param name metric name with or without attributes
	 *
	 * @return metric name without attributes
	 */
	public static final String extractName(final String name) {
		final int openBracketPosition = name.indexOf("{");
		if (openBracketPosition >= 0) {
			return name.substring(0, openBracketPosition);
		}
		return name;
	}

	/**
	 * Checks whether the state attribute exists in the metric attributes.
	 *
	 * @param attributes Metric attributes.
	 * @return {@code true} if the state attribute exists, {@code false} otherwise.
	 */
	public boolean checkForStateAttribute(final Map<String, String> attributes) {
		return attributes.keySet().stream().anyMatch(attributeKey -> attributeKey.equals("state"));
	}

	/**
	 * This method collects monitor metrics
	 * @param monitorType the monitor's type
	 * @param connector connector
	 * @param monitor a given monitor
	 * @param connectorId connector id
	 * @param metrics metrics
	 * @param strategyTime time of the strategy in milliseconds
	 * @param isDiscovery boolean whether it's a discovery operation
	 */
	public void collectMonitorMetrics(
		final String monitorType,
		final Connector connector,
		final Monitor monitor,
		final String connectorId,
		final Map<String, String> metrics,
		final long strategyTime,
		final boolean isDiscovery
	) {
		for (final Map.Entry<String, String> metricEntry : metrics.entrySet()) {
			final String name = metricEntry.getKey();

			// Check if the conditional collection tells that the metric shouldn't be collected
			if (monitor.isMetricDeactivated(name)) {
				continue;
			}

			final String value = metricEntry.getValue();

			if (value == null) {
				log.warn(
					"Hostname {} - No value found for metric {}. Skip metric collection on {}. Connector: {}",
					hostname,
					name,
					monitorType,
					connectorId
				);

				continue;
			}

			// Set the metrics in the monitor using the connector metrics
			final AbstractMetric metric = collectMetricUsingConnector(connector, monitor, strategyTime, name, value);

			// Tell the collect that the refresh time of the discovered
			// metric must be refreshed
			if (isDiscovery && metric != null) {
				metric.setResetMetricTime(true);
			}
		}
	}

	/**
	 * Formats a {@link MetricType} enum value to its display name.
	 * <ul>
	 *   <li>GAUGE → "Gauge"</li>
	 *   <li>COUNTER → "Counter"</li>
	 *   <li>UP_DOWN_COUNTER → "UpDownCounter"</li>
	 * </ul>
	 *
	 * @param metricType the {@link MetricType} enum value
	 * @return the formatted display name
	 */
	public static String formatMetricTypeName(final MetricType metricType) {
		if (metricType == null) {
			return null;
		}
		return switch (metricType) {
			case GAUGE -> "Gauge";
			case COUNTER -> "Counter";
			case UP_DOWN_COUNTER -> "UpDownCounter";
		};
	}

	/**
	 * Resolves the metricType string from a {@link MetricDefinition}.
	 *
	 * @param metricDefinition the metric definition (may be null)
	 * @return the resolved metricType string ("Counter", "UpDownCounter", "Gauge"), or null if unknown
	 */
	public static String resolveMetricType(final MetricDefinition metricDefinition) {
		if (metricDefinition != null && metricDefinition.getType() != null) {
			final IMetricType type = metricDefinition.getType();
			if (type instanceof StateSet stateSet) {
				return formatMetricTypeName(stateSet.getOutput());
			}
			return formatMetricTypeName(type.get());
		}
		return null;
	}

	/**
	 * Resolves the metricType string for a given metric name by looking up the
	 * host-metrics definitions. Returns null if the metric is not found.
	 *
	 * @param metricName the metric name (may include attributes in curly braces)
	 * @return the resolved metricType string, or null if unknown
	 */
	public static String resolveMetricTypeFromName(final String metricName) {
		final String extractedName = extractName(metricName);
		final MetricDefinition hostDef = HostMetricsDefinitionLoader.getInstance().getMetricDefinition(extractedName);
		if (hostDef != null) {
			return resolveMetricType(hostDef);
		}
		return null;
	}

	/**
	 * Resolves the metricType string using the full resolution chain:
	 * <ol>
	 *   <li>Connector metrics (from ConnectorStore using connectorId)</li>
	 *   <li>Host-metrics YAML definitions</li>
	 *   <li>Fallback to null (unknown)</li>
	 * </ol>
	 *
	 * @param connectorStore the connector store (may be null)
	 * @param connectorId    the connector identifier (may be null)
	 * @param monitorType    the monitor type (may be null)
	 * @param metricName     the metric name (may include attributes in curly braces)
	 * @return the resolved metricType string
	 */
	public static String resolveMetricType(
		final ConnectorStore connectorStore,
		final String connectorId,
		final String monitorType,
		final String metricName
	) {
		final String extractedName = extractName(metricName);

		// 1. Try to resolve from connector
		if (connectorStore != null && connectorId != null) {
			final Connector connector = connectorStore.getStore().get(connectorId);
			if (connector != null) {
				// Check monitor job metrics first
				if (monitorType != null) {
					final MonitorJob monitorJobObj = connector.getMonitors().get(monitorType);
					if (monitorJobObj instanceof AbstractMonitorJob monitorJob) {
						final Map<String, MetricDefinition> monitorMetrics = monitorJob.getMetrics();
						if (monitorMetrics != null && monitorMetrics.containsKey(extractedName)) {
							return resolveMetricType(monitorMetrics.get(extractedName));
						}
					}
				}
				// Check connector-level metrics
				final Map<String, MetricDefinition> connectorMetrics = connector.getMetrics();
				if (connectorMetrics != null && connectorMetrics.containsKey(extractedName)) {
					return resolveMetricType(connectorMetrics.get(extractedName));
				}
			}
		}

		// 2. Fall back to host-metrics definitions, then null
		return resolveMetricTypeFromName(metricName);
	}

	/**
	 * Extracts the connector ID from the monitor's attributes.
	 *
	 * @param monitor the monitor
	 * @return the connector ID, or null if not available
	 */
	private static String getConnectorIdFromMonitor(final Monitor monitor) {
		if (monitor != null && monitor.getAttributes() != null) {
			return monitor.getAttributes().get(MONITOR_ATTRIBUTE_CONNECTOR_ID);
		}
		return null;
	}
}
