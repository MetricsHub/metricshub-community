package org.metricshub.web.dto.mcp;

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

import java.util.ArrayList;
import java.util.DoubleSummaryStatistics;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.metricshub.engine.telemetry.Monitor;
import org.metricshub.engine.telemetry.TelemetryManager;
import org.metricshub.engine.telemetry.metric.AbstractMetric;
import org.metricshub.engine.telemetry.metric.NumberMetric;
import org.metricshub.engine.telemetry.metric.StateSetMetric;

/**
 * Converts TelemetryManager data to the compressed MonitorsVo format.
 * Groups monitors by type, excludes null/empty values and internal keys
 * (starting with "__"), and appends a summary as the last element of each
 * monitor type list.
 *
 * <p>Counter metrics (metricType == "Counter") are emitted as their computed
 * rate (units per second) under the key {@code rate(originalKey)}. If the rate
 * is {@code null} (first collect cycle), the Counter metric is discarded.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class TelemetryResultConverter {

	/**
	 * Prefix for internal keys that should be excluded from output.
	 */
	private static final String INTERNAL_KEY_PREFIX = "__";

	/**
	 * Converts a TelemetryManager's monitors to the compressed MonitorsVo format.
	 *
	 * @param telemetryManager the source telemetry manager
	 * @return MonitorsVo with monitors grouped by type, each list ending with a summary,
	 *         or {@code null} if the manager is null or contains no monitors
	 */
	public static MonitorsVo toMonitorsVo(final TelemetryManager telemetryManager) {
		if (telemetryManager == null || telemetryManager.getMonitors() == null) {
			return null;
		}

		// Step 1: Group MonitorVo by type
		final Map<String, List<MonitorVo>> monitorsByType = new LinkedHashMap<>();

		telemetryManager
			.getMonitors()
			.forEach((monitorType, monitorsMap) -> {
				if (monitorType == null || monitorType.isEmpty() || monitorsMap == null) {
					return;
				}

				monitorsMap
					.entrySet()
					.stream()
					.sorted(Map.Entry.comparingByKey())
					.forEach(entry -> {
						final MonitorVo monitorVo = convertMonitor(entry.getValue());
						monitorsByType.computeIfAbsent(monitorType, k -> new ArrayList<>()).add(monitorVo);
					});
			});

		if (monitorsByType.isEmpty()) {
			return null;
		}

		// Step 2: Build polymorphic lists with summary as the last element
		final Map<String, List<MonitorTypeItem>> monitors = new LinkedHashMap<>();

		monitorsByType.forEach((type, monitorList) -> {
			final List<MonitorTypeItem> items = new ArrayList<>(monitorList);
			items.add(buildSummary(monitorList));
			monitors.put(type, items);
		});

		return MonitorsVo.builder().monitors(monitors).build();
	}

	/**
	 * Builds a MonitorTypeSummaryVo from a list of MonitorVo.
	 * Computes:
	 * <ul>
	 *   <li>totalMonitors: count of monitors</li>
	 *   <li>numericMetrics: AVG, MIN, MAX, SUM, COUNT for each numeric metric</li>
	 *   <li>stateSetMetrics: count of monitors per state value for each state metric</li>
	 * </ul>
	 *
	 * @param monitorList the monitors of a given type
	 * @return the summary object
	 */
	private static MonitorTypeSummaryVo buildSummary(final List<MonitorVo> monitorList) {
		final int totalMonitors = monitorList.size();

		final Map<String, List<Double>> numericValues = new LinkedHashMap<>();
		final Map<String, Map<String, Integer>> stateCounts = new LinkedHashMap<>();

		for (final MonitorVo monitorVo : monitorList) {
			final Map<String, Object> metrics = monitorVo.getMetrics();
			if (metrics == null) {
				continue;
			}
			metrics.forEach((key, value) -> {
				if (value instanceof Double doubleValue) {
					numericValues.computeIfAbsent(key, k -> new ArrayList<>()).add(doubleValue);
				} else if (value instanceof String stringValue) {
					stateCounts.computeIfAbsent(key, k -> new LinkedHashMap<>()).merge(stringValue, 1, Integer::sum);
				}
			});
		}

		// Build numeric metrics summary
		Map<String, NumericMetricStatsVo> numericMetrics = null;
		if (!numericValues.isEmpty()) {
			numericMetrics = new LinkedHashMap<>();
			for (final Map.Entry<String, List<Double>> entry : numericValues.entrySet()) {
				final DoubleSummaryStatistics stats = entry
					.getValue()
					.stream()
					.mapToDouble(Double::doubleValue)
					.summaryStatistics();
				numericMetrics.put(
					entry.getKey(),
					NumericMetricStatsVo
						.builder()
						.avg(stats.getAverage())
						.min(stats.getMin())
						.max(stats.getMax())
						.sum(stats.getSum())
						.count((int) stats.getCount())
						.build()
				);
			}
		}

		// Build state set metrics summary
		Map<String, List<StateSetCountVo>> stateSetMetrics = null;
		if (!stateCounts.isEmpty()) {
			stateSetMetrics = new LinkedHashMap<>();
			for (final Map.Entry<String, Map<String, Integer>> entry : stateCounts.entrySet()) {
				final List<StateSetCountVo> counts = new ArrayList<>();
				entry
					.getValue()
					.forEach((state, count) -> counts.add(StateSetCountVo.builder().value(state).count(count).build()));
				stateSetMetrics.put(entry.getKey(), counts);
			}
		}

		return MonitorTypeSummaryVo
			.builder()
			.totalMonitors(totalMonitors)
			.numericMetrics(numericMetrics)
			.stateSetMetrics(stateSetMetrics)
			.build();
	}

	/**
	 * Converts a single Monitor to MonitorVo.
	 *
	 * @param monitor the monitor to convert
	 * @return the converted MonitorVo
	 */
	private static MonitorVo convertMonitor(final Monitor monitor) {
		return MonitorVo
			.builder()
			.attributes(extractNonEmptyAttributes(monitor))
			.metrics(extractNonEmptyMetrics(monitor))
			.textParams(extractNonEmptyTextParams(monitor))
			.build();
	}

	/**
	 * Checks if a key is an internal key (starts with "__").
	 *
	 * @param key the key to check
	 * @return true if the key is internal, false otherwise
	 */
	private static boolean isInternalKey(final String key) {
		return key != null && key.startsWith(INTERNAL_KEY_PREFIX);
	}

	/**
	 * Extracts non-empty attributes from a monitor, excluding internal keys.
	 *
	 * @param monitor the source monitor
	 * @return map of attributes or null if empty
	 */
	private static Map<String, String> extractNonEmptyAttributes(final Monitor monitor) {
		final Map<String, String> attributes = monitor.getAttributes();
		if (attributes == null || attributes.isEmpty()) {
			return null;
		}

		final Map<String, String> result = new LinkedHashMap<>();
		attributes.forEach((key, value) -> {
			if (key != null && !key.isEmpty() && !isInternalKey(key) && value != null && !value.isEmpty()) {
				result.put(key, value);
			}
		});

		return result.isEmpty() ? null : result;
	}

	/**
	 * Extracts non-empty metrics from a monitor, excluding internal keys.
	 * Counter metrics are emitted as their rate under the key {@code rate(originalKey)}.
	 * Counter metrics with a {@code null} rate are excluded.
	 *
	 * @param monitor the source monitor
	 * @return map of metrics or null if empty
	 */
	private static Map<String, Object> extractNonEmptyMetrics(final Monitor monitor) {
		final Map<String, AbstractMetric> metrics = monitor.getMetrics();
		if (metrics == null || metrics.isEmpty()) {
			return null;
		}

		final Map<String, Object> result = new LinkedHashMap<>();
		metrics.forEach((key, metric) -> {
			if (key == null || key.isEmpty() || isInternalKey(key) || metric == null) {
				return;
			}

			final Object value = extractMetricValue(metric);
			if (value != null) {
				// Counter metrics -> rename key to rate(key)
				final String outputKey = isCounterMetric(metric) ? "rate(" + key + ")" : key;
				result.put(outputKey, value);
			}
		});

		return result.isEmpty() ? null : result;
	}

	/**
	 * Checks whether a metric is a Counter.
	 *
	 * @param metric the metric to check
	 * @return {@code true} if the metric is a NumberMetric with metricType "Counter" (case-insensitive)
	 */
	private static boolean isCounterMetric(final AbstractMetric metric) {
		return metric instanceof NumberMetric numberMetric && "Counter".equalsIgnoreCase(numberMetric.getMetricType());
	}

	/**
	 * Extracts the displayable value from a metric.
	 *
	 * <p>For {@link NumberMetric}:
	 * <ul>
	 *   <li>If metricType is "Counter" (case-insensitive), returns the rate.
	 *       If the rate is null (first collect cycle), returns null to exclude this metric.</li>
	 *   <li>For all other metric types, returns the raw value.</li>
	 * </ul>
	 *
	 * <p>For {@link StateSetMetric}, returns the state string (e.g., "ok").
	 *
	 * @param metric the metric to extract value from
	 * @return the metric value, rate, or null to signal exclusion
	 */
	private static Object extractMetricValue(final AbstractMetric metric) {
		if (metric instanceof NumberMetric numberMetric) {
			// Counter metrics -> emit rate instead of raw cumulative value
			if ("Counter".equalsIgnoreCase(numberMetric.getMetricType())) {
				return numberMetric.getRate(); // null rate -> metric excluded
			}
			return numberMetric.getValue();
		} else if (metric instanceof StateSetMetric stateSetMetric) {
			return stateSetMetric.getValue();
		}
		return null;
	}

	/**
	 * Extracts non-empty text parameters from a monitor's legacy text parameters,
	 * excluding internal keys.
	 *
	 * @param monitor the source monitor
	 * @return map of text parameters or null if empty
	 */
	private static Map<String, String> extractNonEmptyTextParams(final Monitor monitor) {
		final Map<String, String> legacyTextParameters = monitor.getLegacyTextParameters();
		if (legacyTextParameters == null || legacyTextParameters.isEmpty()) {
			return null;
		}

		final Map<String, String> result = new LinkedHashMap<>();
		legacyTextParameters.forEach((key, value) -> {
			if (key != null && !key.isEmpty() && !isInternalKey(key) && value != null && !value.isEmpty()) {
				result.put(key, value);
			}
		});

		return result.isEmpty() ? null : result;
	}
}
