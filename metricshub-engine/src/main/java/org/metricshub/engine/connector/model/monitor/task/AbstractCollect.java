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

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import java.util.Map;
import java.util.Set;
import lombok.NoArgsConstructor;
import org.metricshub.engine.connector.model.monitor.task.source.Source;

/**
 * An abstract class representing a collection task within the MetricsHub engine. Subclasses, such as
 * {@link MonoInstanceCollect} and {@link MultiInstanceCollect}, define specific types of collection tasks.
 * This class provides a common structure for all collection tasks, including sources, mappings, and execution order.
 */
@NoArgsConstructor
@JsonTypeInfo(
	use = JsonTypeInfo.Id.NAME,
	include = JsonTypeInfo.As.PROPERTY,
	property = "type",
	defaultImpl = MonoInstanceCollect.class
)
@JsonSubTypes(
	{
		@JsonSubTypes.Type(value = MonoInstanceCollect.class, name = "monoInstance"),
		@JsonSubTypes.Type(value = MultiInstanceCollect.class, name = "multiInstance")
	}
)
public class AbstractCollect extends AbstractMonitorTask {

	private static final long serialVersionUID = 1L;

	/**
	 * Creates an instance of AbstractCollect with the specified sources, mapping, and execution order.
	 *
	 * @param sources        A map of source names to source configurations.
	 * @param mapping        The mapping configuration for the collection task.
	 * @param executionOrder A set defining the order of execution for the collection task.
	 */
	public AbstractCollect(final Map<String, Source> sources, final Mapping mapping, final Set<String> executionOrder) {
		super(sources, mapping, executionOrder);
	}
}
