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
import org.metricshub.engine.connector.model.monitor.task.AbstractCollect;
import org.metricshub.engine.connector.model.monitor.task.Discovery;

/**
 * Represents a standard monitor job.
 *
 * <p>
 * This class implements the {@link MonitorJob} interface and is designed for standard monitor jobs that include both
 * discovery and collect tasks.
 * </p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class StandardMonitorJob extends AbstractMonitorJob {

	/**
	 * Creates a {@code StandardMonitorJob} with the specified keys, discovery, and collect instances.
	 *
	 * @param keys The set of keys for the monitor job.
	 * @param discovery The discovery instance for the monitor job.
	 * @param collect The collect instance for the monitor job.
	 */
	@Builder(builderMethodName = "standardBuilder")
	public StandardMonitorJob(
		final Set<String> keys,
		final Discovery discovery,
		final AbstractCollect collect,
		final Map<String, MetricDefinition> metrics
	) {
		super(keys, metrics);
		this.discovery = discovery;
		this.collect = collect;
	}

	private static final long serialVersionUID = 1L;

	/**
	 * The discovery task associated with this standard monitor job.
	 */
	private Discovery discovery;
	/**
	 * The collection task associated with this standard monitor job.
	 */
	private AbstractCollect collect;
}
