package org.metricshub.engine.connector.model.monitor;

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
import java.util.Set;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.metricshub.engine.connector.model.metric.MetricDefinition;
import org.metricshub.engine.connector.model.monitor.task.Simple;

/**
 * Represents a monitor job associated with a simple monitoring task.
 *
 * <p>
 * This class implements the {@link MonitorJob} interface and is used to encapsulate the configuration and details of a monitoring job that involves a
 * simple monitoring task.
 * </p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class SimpleMonitorJob extends AbstractMonitorJob {

	/**
	 * Constructs a {@code SimpleMonitorJob} with the specified keys and simple instance.
	 *
	 * @param keys The set of keys for the monitor job.
	 * @param simple The {@link Simple} instance for the monitor job.
	 */
	@Builder(builderMethodName = "simpleBuilder")
	public SimpleMonitorJob(final Set<String> keys, final Simple simple, final Map<String, MetricDefinition> metrics) {
		super(keys, metrics);
		this.simple = simple;
	}

	private static final long serialVersionUID = 1L;

	/**
	 * The simple task associated with this monitor job.
	 */
	private Simple simple;
}
