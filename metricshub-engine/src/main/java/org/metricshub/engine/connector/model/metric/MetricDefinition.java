package org.metricshub.engine.connector.model.metric;

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

import static com.fasterxml.jackson.annotation.Nulls.SKIP;
import static org.metricshub.engine.common.helpers.MetricsHubConstants.EMPTY;

import com.fasterxml.jackson.annotation.JsonSetter;
import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Builder.Default;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents the definition of a metric.
 *
 * <p>A MetricDefinition instance holds information about a metric, such as its unit, description, and type.</p>
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class MetricDefinition implements Serializable {

	private static final long serialVersionUID = 1L;

	/**
	 * The unit of the metric. Default is an empty string.
	 */
	@Default
	@JsonSetter(nulls = SKIP)
	private String unit = EMPTY;

	/**
	 * The description of the metric. Default is an empty string.
	 */
	@Default
	@JsonSetter(nulls = SKIP)
	private String description = EMPTY;

	/**
	 * The type of the metric. Default is {@link MetricType#GAUGE}.
	 */
	@Default
	@JsonSetter(nulls = SKIP)
	private IMetricType type = MetricType.GAUGE;
}
