package org.metricshub.engine.telemetry.metric;

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

import java.util.Map;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * The `StateSetMetric` class represents a metric that holds a state value from a predefined set of states.
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class StateSetMetric extends AbstractMetric {

	/**
	 * The type identifier for the StateSetMetric.
	 */
	public static final String STATE_SET_METRIC_TYPE = "StateSetMetric";

	private String value;
	private String previousValue;
	private String[] stateSet;

	/**
	 * Constructs a new StateSetMetric.
	 *
	 * @param name         The name of the metric.
	 * @param collectTime  The time when the metric was collected.
	 * @param attributes   Additional attributes associated with the metric.
	 * @param value        The current value of the state.
	 * @param stateSet     The set of possible states for the metric.
	 * @param metricType   The OpenTelemetry instrument type of this metric.
	 */
	@Builder
	public StateSetMetric(
		final String name,
		final Long collectTime,
		final Map<String, String> attributes,
		final String value,
		final String[] stateSet,
		final String metricType
	) {
		super(name, collectTime, attributes, metricType);
		this.value = value;
		this.stateSet = stateSet;
	}

	@Override
	public void save() {
		super.save();
		previousValue = value;
	}

	@Override
	public String getType() {
		return STATE_SET_METRIC_TYPE;
	}
}
