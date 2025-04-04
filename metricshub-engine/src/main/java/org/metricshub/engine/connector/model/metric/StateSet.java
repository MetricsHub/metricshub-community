package org.metricshub.engine.connector.model.metric;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * MetricsHub Engine
 * ჻჻჻჻჻჻
 * Copyright 2023 - 2024 Sentry Software
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

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.HashSet;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Builder.Default;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents a set of states as a metric type.
 *
 * <p>
 * A StateSet instance holds a set of string states, and it is considered a metric type with the default type
 * {@link MetricType#UP_DOWN_COUNTER}.
 * </p>
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class StateSet implements IMetricType {

	private static final long serialVersionUID = 1L;

	/**
	 * The metric type of the StateSet. Default is {@link MetricType#UP_DOWN_COUNTER}.
	 */
	@Default
	private MetricType output = MetricType.UP_DOWN_COUNTER;

	/**
	 * The set of states associated with the metric.
	 */
	@Default
	@JsonProperty("stateSet")
	private Set<String> set = new HashSet<>(); // NOSONAR

	@Override
	public MetricType get() {
		return output;
	}
}
