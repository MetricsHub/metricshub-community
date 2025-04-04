package org.metricshub.engine.telemetry.metric;

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

import java.util.Map;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * The NumberMetric class represents a numeric metric in the telemetry system.
 * It extends the AbstractMetric class and includes additional properties such as value and previousValue.
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class NumberMetric extends AbstractMetric {

	/**
	 * The constant representing the type of the metric (NumberMetric).
	 */
	public static final String NUMBER_METRIC_TYPE = "NumberMetric";

	private Double value;
	private Double previousValue;

	/**
	 * Creates a new instance of NumberMetric using the provided parameters.
	 *
	 * @param name         The name of the metric.
	 * @param collectTime  The timestamp when the metric was collected.
	 * @param attributes   Additional attributes associated with the metric.
	 * @param value        The numeric value of the metric.
	 */
	@Builder
	public NumberMetric(
		final String name,
		final Long collectTime,
		final Map<String, String> attributes,
		final Double value
	) {
		super(name, collectTime, attributes);
		this.value = value;
	}

	@Override
	public void save() {
		super.save();
		previousValue = value;
	}

	@Override
	public String getType() {
		return NUMBER_METRIC_TYPE;
	}
}
