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
import org.metricshub.engine.connector.model.PowerMeasurement;
import org.metricshub.engine.connector.model.monitor.MonitorJob;
import org.metricshub.engine.connector.model.monitor.SimpleMonitorJob;
import org.metricshub.engine.connector.model.monitor.StandardMonitorJob;
import org.metricshub.engine.connector.model.monitor.task.AbstractMonitorTask;
import org.metricshub.engine.strategy.utils.StrategyHelper;

/**
 * Implementation of {@link AbstractConnectorUpdateChain} to update the power
 * measurement status of a connector.
 */
public class PowerMeasurementStatusUpdate extends AbstractConnectorUpdateChain {

	@Override
	void doUpdate(final Connector connector) {
		// Check if the connector is a hardware connector
		if (!StrategyHelper.isHardwareConnector(connector)) {
			// It is not a hardware connector, so the power measurement will not be updated
			return;
		}

		final var monitors = connector.getMonitors();

		if (monitors == null || monitors.isEmpty()) {
			return;
		}

		Optional
			.ofNullable(monitors.get(KnownMonitorType.ENCLOSURE.getKey()))
			.ifPresentOrElse(
				(MonitorJob enclosure) -> updatePowerMeasurementStatus(connector, enclosure),
				() -> connector.setPowerMeasurement(PowerMeasurement.ESTIMATED)
			);
	}

	/**
	 * Update the {@link PowerMeasurement} property of the connector based on the enclosure job.
	 *
	 * @param connector The connector to update the {@link PowerMeasurement} property for
	 * @param enclosure The enclosure job to check for power metrics
	 */
	private static void updatePowerMeasurementStatus(final Connector connector, final MonitorJob enclosure) {
		// Build a stream of tasks from the enclosure job for processing convenience
		final Stream<AbstractMonitorTask> taskStream;
		if (enclosure instanceof StandardMonitorJob standardMonitorJob) {
			taskStream = Stream.of(standardMonitorJob.getDiscovery(), standardMonitorJob.getCollect());
		} else if (enclosure instanceof SimpleMonitorJob simpleMonitorJob) {
			taskStream = Stream.ofNullable(simpleMonitorJob.getSimple());
		} else {
			// If the enclosure job is not of the expected type, set power measurement to ESTIMATED and return
			connector.setPowerMeasurement(PowerMeasurement.ESTIMATED);
			return;
		}

		var hasPowerMetric = false;
		var hasConditionalPowerMetric = false;

		// Loop over the tasks and check for power metrics as well as conditional power metrics
		for (AbstractMonitorTask task : taskStream.filter(Objects::nonNull).toList()) {
			final var mapping = task.getMapping();
			// No mapping, skip this task
			if (mapping == null) {
				continue;
			}

			if (!hasConditionalPowerMetric) {
				hasConditionalPowerMetric = hasPowerMetric(mapping.getConditionalCollection());
			}

			if (!hasPowerMetric) {
				hasPowerMetric = hasPowerMetric(mapping.getMetrics());
			}
		}

		if (hasPowerMetric) {
			// Check if the enclosure job has any conditional power metrics
			if (hasConditionalPowerMetric) {
				connector.setPowerMeasurement(PowerMeasurement.CONDITIONAL);
			} else {
				// If no conditional power metrics are found, set the power measurement to MEASURED
				connector.setPowerMeasurement(PowerMeasurement.MEASURED);
			}
		} else {
			// If no power metrics are found, set the power measurement to ESTIMATED
			connector.setPowerMeasurement(PowerMeasurement.ESTIMATED);
		}
	}

	/**
	 * Check if the given map of metrics contains at least one power metric.
	 *
	 * @param metrics The map of metrics to check
	 * @return <code>true</code> if at least one power metric is found, <code>false</code> otherwise
	 */
	private static boolean hasPowerMetric(final Map<String, String> metrics) {
		if (metrics != null) {
			for (String metricName : metrics.keySet()) {
				if (isPowerMetric(metricName)) {
					return true;
				}
			}
		}
		return false;
	}

	/**
	 * Check if the given key is a power metric.
	 *
	 * @param metricName The metric name to check
	 * @return <code>true</code> if the metric name is a power metric, <code>false</code> otherwise
	 */
	private static boolean isPowerMetric(final String metricName) {
		if (metricName == null) {
			return false;
		}
		final String lowerKey = metricName.toLowerCase();
		// CHECKSTYLE:OFF
		return (
			lowerKey.startsWith(MetricsHubConstants.HW_ENCLOSURE_POWER_METRIC) ||
			lowerKey.startsWith(MetricsHubConstants.HW_ENCLOSURE_ENERGY_METRIC)
		);
		// CHECKSTYLE:ON
	}
}
