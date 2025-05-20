package org.metricshub.engine.connector.update;

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
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;
import org.metricshub.engine.common.helpers.KnownMonitorType;
import org.metricshub.engine.common.helpers.MetricsHubConstants;
import org.metricshub.engine.connector.model.Connector;
import org.metricshub.engine.connector.model.monitor.MonitorJob;
import org.metricshub.engine.connector.model.monitor.SimpleMonitorJob;
import org.metricshub.engine.connector.model.monitor.StandardMonitorJob;
import org.metricshub.engine.connector.model.monitor.task.AbstractMonitorTask;
import org.metricshub.engine.connector.model.monitor.task.Mapping;

/**
 * Implementation of {@link AbstractConnectorUpdateChain} to update the power measurement status of a connector.
 */
public class PowerMeasurementStatusUpdate extends AbstractConnectorUpdateChain {

	@Override
	void doUpdate(final Connector connector) {
		final var monitors = connector.getMonitors();

		if (monitors == null) {
			return;
		}

		Optional
			.ofNullable(monitors.get(KnownMonitorType.ENCLOSURE.getKey()))
			.ifPresent((MonitorJob enclosure) -> updatePowerMeasurementStatus(connector, enclosure));
	}

	/**
	 * Update the isPowerMeasured flag of the connector based on the enclosure job.
	 *
	 * @param connector The connector to update the isPowerMeasured flag for
	 * @param enclosure The enclosure job to check for power metrics
	 */
	private static void updatePowerMeasurementStatus(final Connector connector, final MonitorJob enclosure) {
		final Stream<AbstractMonitorTask> jobStream;
		if (enclosure instanceof StandardMonitorJob standardMonitorJob) {
			jobStream = Stream.of(standardMonitorJob.getDiscovery(), standardMonitorJob.getCollect());
		} else if (enclosure instanceof SimpleMonitorJob simpleMonitorJob) {
			jobStream = Stream.ofNullable(simpleMonitorJob.getSimple());
		} else {
			return;
		}

		// Check if any of the tasks in the job stream have a mapping with power metrics
		connector.setPowerMeasured(
			jobStream
				.filter(Objects::nonNull)
				.map(AbstractMonitorTask::getMapping)
				.filter(Objects::nonNull)
				.map(Mapping::getMetrics)
				.filter(Objects::nonNull)
				.flatMap((Map<String, String> metrics) -> metrics.keySet().stream())
				.anyMatch(key -> key.toLowerCase().startsWith(MetricsHubConstants.HW_ENCLOSURE_POWER_METRIC))
		);
	}
}
