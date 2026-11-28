package org.metricshub.engine.strategy.utils;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * MetricsHub Engine
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

import java.util.Map;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.metricshub.engine.connector.model.ConnectorStore;
import org.metricshub.engine.telemetry.MetricFactory;
import org.metricshub.engine.telemetry.Monitor;
import org.metricshub.engine.telemetry.metric.NumberMetric;

/**
 * The Java logic of the mapping functions, shared by the legacy {@link MappingProcessor} and the mapping expressions
 * (<code>MappingExtensionForJawk</code>). A function that cannot answer returns <code>null</code>: each caller reports
 * it as it needs to. The only logs are those of {@link MathOperationsHelper}.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class MappingFunctions {

	/**
	 * Bytes in a mebibyte
	 */
	public static final double MEBIBYTE_2_BYTE_FACTOR = 1_048_576.0;

	/**
	 * Bits in a megabit
	 */
	public static final double MEGABIT_2_BIT_FACTOR = 1_000_000.0;

	/**
	 * Bytes in a megabit
	 */
	public static final double MEGABIT_2_BYTE_FACTOR = MEGABIT_2_BIT_FACTOR / 8.0;

	/**
	 * Hertz in a megahertz
	 */
	public static final double MEGAHERTZ_2_HERTZ_FACTOR = 1_000_000.0;

	/**
	 * Volts in a millivolt
	 */
	public static final double MILLIVOLT_2_VOLT_FACTOR = 0.001;

	/**
	 * Ratio of one percent
	 */
	public static final double PERCENT_2_RATIO_FACTOR = 0.01;

	/**
	 * The metric holding the power limit of a power supply
	 */
	private static final String POWER_SUPPLY_LIMIT = "hw.power_supply.limit";

	/**
	 * The <code>boolean()</code> function: whether the text is <code>1</code> or <code>true</code> in any case, not
	 * trimmed.
	 *
	 * @param text The text to interpret
	 * @return whether the text means true
	 */
	public static boolean isTrue(final String text) {
		return "1".equals(text) || "true".equalsIgnoreCase(text);
	}

	/**
	 * The status of a LED state: the monitor attribute <code>__on_status</code>, <code>__off_status</code> or
	 * <code>__blinking_status</code> for <code>on</code>, <code>off</code> and <code>blinking</code> in any case,
	 * lowercased.
	 *
	 * @param monitorAttributes The attributes of the monitor
	 * @param state             The LED state
	 * @return the lowercased status, or <code>null</code> for another state or a missing attribute
	 */
	public static String ledStatus(final Map<String, String> monitorAttributes, final String state) {
		final String status = switch (state.toLowerCase()) {
			case "on" -> monitorAttributes.get("__on_status");
			case "off" -> monitorAttributes.get("__off_status");
			case "blinking" -> monitorAttributes.get("__blinking_status");
			default -> null;
		};
		return status != null ? status.toLowerCase() : null;
	}

	/**
	 * The <code>lookup()</code> function: the first monitor whose attribute <code>matchedAttribute</code> equals
	 * <code>value</code>.
	 *
	 * @param typedMonitors    The monitors of the looked up type, by id, or <code>null</code>
	 * @param matchedAttribute The attribute to match
	 * @param value            The value it must have
	 * @return the monitor, or <code>null</code> for a null map or no match
	 */
	public static Monitor findMonitor(
		final Map<String, Monitor> typedMonitors,
		final String matchedAttribute,
		final String value
	) {
		if (typedMonitors == null) {
			return null;
		}
		return typedMonitors.values()
			.stream()
			.filter(monitor -> value.equals(monitor.getAttributes().get(matchedAttribute)))
			.findFirst()
			.orElse(null);
	}

	/**
	 * The <code>legacyPowerSupplyUtilization()</code> function: the power divided by the current value of the
	 * monitor's <code>hw.power_supply.limit</code> metric.
	 *
	 * @param monitor  The power supply
	 * @param key      The metric computed, for the logs
	 * @param power    The power
	 * @param hostname The host, for the logs
	 * @return the ratio, or <code>null</code> without the limit metric or when the division gives none
	 */
	public static Double powerSupplyUtilization(
		final Monitor monitor,
		final String key,
		final double power,
		final String hostname
	) {
		final NumberMetric metric;
		try {
			metric = monitor.getMetric(POWER_SUPPLY_LIMIT, NumberMetric.class);
		} catch (Exception e) {
			return null;
		}
		if (metric == null) {
			return null;
		}
		return MathOperationsHelper.divide(key, power, metric.getValue(), hostname);
	}

	/**
	 * The <code>rate()</code> function: records the counter in <code>__&lt;key&gt;.rate_from</code> at the collect
	 * time, then divides its increase by the seconds elapsed since the previous value.
	 *
	 * @param monitor        The monitor collected
	 * @param key            The metric computed
	 * @param counter        The counter
	 * @param collectTime    The collect time, in milliseconds
	 * @param hostname       The host, for the logs
	 * @param connectorStore The connector store, for the metric definitions
	 * @return the rate, or <code>null</code> without a previous value, for a negative increase or when no time elapsed
	 */
	public static Double rate(
		final Monitor monitor,
		final String key,
		final double counter,
		final long collectTime,
		final String hostname,
		final ConnectorStore connectorStore
	) {
		final String metricRateName = rateStateName(key);

		new MetricFactory(hostname, connectorStore).collectNumberMetric(monitor, metricRateName, counter, collectTime);

		final Double collectTimePrevious = CollectHelper.getNumberMetricCollectTime(monitor, metricRateName, true);

		final Double deltaTimeMs = MathOperationsHelper.subtract(
			metricRateName,
			Double.valueOf(collectTime),
			collectTimePrevious,
			hostname
		);

		// Convert deltaTimeMs from milliseconds (ms) to seconds
		final Double deltaTime = deltaTimeMs != null ? deltaTimeMs / 1000.0 : null;
		final Double previousValue = CollectHelper.getNumberMetricValue(monitor, metricRateName, true);

		final Double deltaCounter = MathOperationsHelper.subtract(metricRateName, counter, previousValue, hostname);

		return MathOperationsHelper.divide(key, deltaCounter, deltaTime, hostname);
	}

	/**
	 * The <code>fakeCounter()</code> function: records the value (a rate) in <code>__&lt;key&gt;.fake_counter_from</code>,
	 * then adds the value multiplied by the seconds elapsed since the previous one to the previous value of the metric.
	 *
	 * @param monitor        The monitor collected
	 * @param key            The metric computed
	 * @param value          The rate
	 * @param collectTime    The collect time, in milliseconds
	 * @param hostname       The host, for the logs
	 * @param connectorStore The connector store, for the metric definitions
	 * @return the counter, or <code>null</code> without a previous time or for a negative product
	 */
	public static Double fakeCounter(
		final Monitor monitor,
		final String key,
		final double value,
		final long collectTime,
		final String hostname,
		final ConnectorStore connectorStore
	) {
		final String metricRateName = fakeCounterStateName(key);

		// The previous reading and the previous counter must be those of the last run
		saveLastRun(monitor, metricRateName, collectTime);
		saveLastRun(monitor, key, collectTime);

		new MetricFactory(hostname, connectorStore).collectNumberMetric(monitor, metricRateName, value, collectTime);

		final Double collectTimePrevious = CollectHelper.getNumberMetricCollectTime(monitor, metricRateName, true);

		final Double deltaTimeMs = MathOperationsHelper.subtract(
			metricRateName,
			Double.valueOf(collectTime),
			collectTimePrevious,
			hostname
		);

		// Convert deltaTimeMs from milliseconds (ms) to seconds
		final Double deltaTime = deltaTimeMs != null ? deltaTimeMs / 1000.0 : null;

		// Calculate the usage over time. E.g from Power Consumption: E = P * T
		final Double usageDelta = MathOperationsHelper.multiply(metricRateName, value, deltaTime, hostname);
		if (usageDelta == null) {
			return null;
		}

		// The previous counter is needed to make a sum with the delta counter value on this collect
		final Double previousCounter = CollectHelper.getNumberMetricValue(monitor, key, true);
		return previousCounter != null ? usageDelta + previousCounter : usageDelta;
	}

	/**
	 * The <code>accumulate()</code> function: adds the delta to the previous value of the metric.
	 *
	 * @param monitor     The monitor collected
	 * @param key         The metric computed
	 * @param delta       The delta
	 * @param collectTime The collect time, in milliseconds
	 * @return the delta plus the previous value, or the delta alone without one
	 */
	public static double accumulate(final Monitor monitor, final String key, final double delta, final long collectTime) {
		// The previous counter must be the one of the last run
		saveLastRun(monitor, key, collectTime);

		final Double previousCounter = CollectHelper.getNumberMetricValue(monitor, key, true);
		return previousCounter != null ? delta + previousCounter : delta;
	}

	/**
	 * Save the value and the collect time of the given metric as its previous ones, unless this run already collected
	 * it. The functions that add to the total of the last run read these previous values:
	 * <code>PrepareCollectStrategy</code> saves them before a collect, but not before the simple jobs of the discovery
	 * phase. When several rows are mapped to the same monitor during a run, the rows after the first one find the metric
	 * collected at this collect time: the previous values stay those of the last run.
	 *
	 * @param monitor     The monitor we currently collect.
	 * @param metricName  The name of the metric to save.
	 * @param collectTime The collect time of this run.
	 */
	public static void saveLastRun(final Monitor monitor, final String metricName, final long collectTime) {
		final NumberMetric metric = monitor.getMetric(metricName, NumberMetric.class);
		if (metric != null && !Long.valueOf(collectTime).equals(metric.getCollectTime())) {
			metric.save();
		}
	}

	/**
	 * The hidden metric where <code>rate()</code> keeps its counter.
	 *
	 * @param key The metric computed
	 * @return <code>__&lt;key&gt;.rate_from</code>
	 */
	public static String rateStateName(final String key) {
		return "__" + key + ".rate_from";
	}

	/**
	 * The hidden metric where <code>fakeCounter()</code> keeps its rate.
	 *
	 * @param key The metric computed
	 * @return <code>__&lt;key&gt;.fake_counter_from</code>
	 */
	public static String fakeCounterStateName(final String key) {
		return "__" + key + ".fake_counter_from";
	}

	/**
	 * The hidden metric where <code>computePowerShareRatio()</code> puts its argument.
	 *
	 * @param key The metric computed
	 * @return <code>__&lt;key&gt;.raw_power_share</code>
	 */
	public static String powerShareName(final String key) {
		return "__" + key + ".raw_power_share";
	}
}
