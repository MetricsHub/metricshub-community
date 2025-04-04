package org.metricshub.engine.connector.model.monitor.task;

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

import com.fasterxml.jackson.annotation.JsonSetter;
import java.util.Map;
import java.util.Set;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.metricshub.engine.common.helpers.MetricsHubConstants;
import org.metricshub.engine.connector.model.monitor.task.source.Source;

/**
 * The {@code MultiInstanceCollect} class represents a task for collecting metrics from multiple instances.
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class MultiInstanceCollect extends AbstractCollect {

	private static final Set<String> DEFAULT_KEYS = Set.of(MetricsHubConstants.MONITOR_ATTRIBUTE_ID);

	private static final long serialVersionUID = 1L;

	/**
	 * The set of keys used for collecting metrics from multiple instances.
	 */
	@Deprecated(since = "0.9.05")
	@JsonSetter(nulls = SKIP)
	private Set<String> keys = DEFAULT_KEYS;

	/**
	 * Constructs a new {@code MultiInstanceCollect} instance with the provided sources, mapping, execution order, and keys.
	 *
	 * @param sources        a map of source names to {@code Source} instances providing the metric data
	 * @param mapping        the mapping information for transforming collected data
	 * @param executionOrder a set defining the order in which the collect tasks should be executed
	 * @param keys           a set defining the keys used for collecting metrics from multiple instances
	 */
	@Builder
	public MultiInstanceCollect(
		final Map<String, Source> sources,
		final Mapping mapping,
		final Set<String> executionOrder,
		final Set<String> keys
	) {
		super(sources, mapping, executionOrder);
		this.keys = keys != null ? keys : DEFAULT_KEYS;
	}
}
