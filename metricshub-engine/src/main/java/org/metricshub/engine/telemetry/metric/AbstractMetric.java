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

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import java.util.HashMap;
import java.util.Map;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * An abstract base class representing a telemetry metric. Concrete implementations
 * must extend this class and provide specific behavior for handling different types of metrics.
 */
@Data
@NoArgsConstructor
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "type")
@JsonSubTypes(
	{
		@JsonSubTypes.Type(value = StateSetMetric.class, name = StateSetMetric.STATE_SET_METRIC_TYPE),
		@JsonSubTypes.Type(value = NumberMetric.class, name = NumberMetric.NUMBER_METRIC_TYPE)
	}
)
public abstract class AbstractMetric {

	private String name;
	private Long collectTime;
	private Long previousCollectTime;
	private Map<String, String> attributes = new HashMap<>();
	private boolean resetMetricTime;

	/**
	 * Constructs an AbstractMetric with the given name, collect time, and attributes.
	 *
	 * @param name        The name of the metric.
	 * @param collectTime The timestamp when the metric was collected.
	 * @param attributes  Additional attributes associated with the metric.
	 */
	AbstractMetric(final String name, final Long collectTime, final Map<String, String> attributes) {
		this.name = name;
		this.collectTime = collectTime;
		this.attributes = attributes == null ? new HashMap<>() : attributes;
	}

	/**
	 * Set the collect time as previous collect time
	 */
	public void save() {
		previousCollectTime = collectTime;
	}

	/**
	 * Whether the metric is updated or not
	 *
	 * @return <code>true</code> if the collect time is different from the
	 * previous collect time otherwise <code>false</code>
	 */
	public boolean isUpdated() {
		if (collectTime == null) {
			return false;
		}

		return !collectTime.equals(previousCollectTime);
	}

	/**
	 * Get the metric type as String value
	 *
	 * @return {@link String} value
	 */
	public abstract String getType();

	/**
	 * Gets the value of the metric.
	 *
	 * @param <T> The type of the metric value.
	 * @return The value of the metric.
	 */
	public abstract <T> T getValue();
}
