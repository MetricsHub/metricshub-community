package org.metricshub.hardware.threshold;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * MetricsHub Hardware Energy and Sustainability Module
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

import static org.metricshub.hardware.util.HwCollectHelper.findMetricByNamePrefixAndAttributes;
import static org.metricshub.hardware.util.HwCollectHelper.isMetricCollected;

import java.util.Map;
import java.util.Optional;
import org.metricshub.engine.connector.model.ConnectorStore;
import org.metricshub.engine.telemetry.MetricFactory;
import org.metricshub.engine.telemetry.Monitor;
import org.metricshub.engine.telemetry.metric.AbstractMetric;
import org.metricshub.engine.telemetry.metric.NumberMetric;

/**
 * The VoltageMetricNormalizer class is responsible for normalizing voltage metrics.
 * It extends the AbstractMetricNormalizer class to provide specific
 * normalization logic for voltage monitor hardware metrics.
 */
public class VoltageMetricNormalizer extends AbstractMetricNormalizer {

	/**
	 * Lowest valid voltage limit in volts (-100 V), as in the PSL's isValidVoltageValue.
	 */
	private static final double MIN_VALID_VOLTAGE = -100.0;

	/**
	 * Highest valid voltage limit in volts (450 V), as in the PSL's isValidVoltageValue.
	 */
	private static final double MAX_VALID_VOLTAGE = 450.0;

	/**
	 * Constructs new instance of VoltageMetricNormalizer with the specified strategy time.
	 * @param strategyTime   The strategy time in milliseconds
	 * @param hostname       The hostname of the monitor
	 * @param connectorStore The connector store
	 */
	public VoltageMetricNormalizer(long strategyTime, String hostname, ConnectorStore connectorStore) {
		super(strategyTime, hostname, connectorStore);
	}

	/**
	 * Normalizes the metrics of the given monitor.
	 * @param monitor The monitor containing the metrics to be normalized
	 */
	@Override
	public void normalize(Monitor monitor) {
		normalizeVoltageLimitMetric(monitor, "hw.voltage");
	}

	/**
	 * Normalizes the voltage limit metrics.
	 * <p>
	 * A lone critical limit is kept as the critical level and its degraded limit is derived 10% further in.
	 * This deliberately diverges from the PSL, which treated a lone threshold as the warning level
	 * and placed the alarm 10% further out.
	 * </p>
	 * @param monitor The monitor to normalize
	 * @param metricNamePrefix The prefix of the metric name.
	 */
	private void normalizeVoltageLimitMetric(final Monitor monitor, final String metricNamePrefix) {
		if (!isMetricCollected(monitor, metricNamePrefix)) {
			return;
		}

		// Discard out-of-range limits (e.g. 65535) so they are neither used below nor published
		final String limitMetricName = String.format("%s.limit", metricNamePrefix);
		monitor
			.getMetrics()
			.values()
			.removeIf(
				metric -> limitMetricName.equals(MetricFactory.extractName(metric.getName())) && isInvalidVoltage(metric)
			);

		// Get the high critical metric
		final Optional<NumberMetric> maybeHighCriticaldMetric = findMetricByNamePrefixAndAttributes(
			hostname,
			monitor,
			String.format("%s.limit", metricNamePrefix),
			Map.of("limit_type", "high.critical")
		);

		// Get the low critical metric
		final Optional<NumberMetric> maybeLowCriticalMetric = findMetricByNamePrefixAndAttributes(
			hostname,
			monitor,
			String.format("%s.limit", metricNamePrefix),
			Map.of("limit_type", "low.critical")
		);

		if (maybeHighCriticaldMetric.isPresent() && maybeLowCriticalMetric.isPresent()) {
			// Adjust values if both metrics are present
			swapIfFirstLessThanSecond(maybeHighCriticaldMetric.get(), maybeLowCriticalMetric.get());
		} else if (maybeHighCriticaldMetric.isPresent()) {
			// Create high degraded metric if only high critical is present
			final Optional<NumberMetric> maybeHighDegradedMetric = findMetricByNamePrefixAndAttributes(
				hostname,
				monitor,
				limitMetricName,
				Map.of("limit_type", "high.degraded")
			);
			if (maybeHighDegradedMetric.isPresent()) {
				return;
			}

			final NumberMetric highCriticalMetric = maybeHighCriticaldMetric.get();

			final String highDegradedMetricName = replaceLimitType(
				highCriticalMetric.getName(),
				"limit_type=\"high.degraded\""
			);

			final Double highCriticalValue = highCriticalMetric.getValue();
			collectMetric(
				monitor,
				highDegradedMetricName,
				highCriticalValue > 0 ? highCriticalValue * 0.9 : highCriticalValue * 1.1
			);
		} else if (maybeLowCriticalMetric.isPresent()) {
			// Create low degraded metric if only low critical is present
			final Optional<NumberMetric> maybeLowDegradedMetric = findMetricByNamePrefixAndAttributes(
				hostname,
				monitor,
				limitMetricName,
				Map.of("limit_type", "low.degraded")
			);
			if (maybeLowDegradedMetric.isPresent()) {
				return;
			}

			final NumberMetric lowCriticalMetric = maybeLowCriticalMetric.get();

			final String lowDegradedMetricName = replaceLimitType(lowCriticalMetric.getName(), "limit_type=\"low.degraded\"");

			final Double lowCriticalValue = lowCriticalMetric.getValue();
			collectMetric(
				monitor,
				lowDegradedMetricName,
				lowCriticalValue > 0 ? lowCriticalValue * 1.1 : lowCriticalValue * 0.9
			);
		}
	}

	/**
	 * Whether the given limit metric holds a value outside [-100, 450] V.
	 * @param metric The limit metric
	 * @return true if the value is not a valid voltage
	 */
	private static boolean isInvalidVoltage(final AbstractMetric metric) {
		if (!(metric instanceof NumberMetric numberMetric) || numberMetric.getValue() == null) {
			return false;
		}
		final double value = numberMetric.getValue();
		return value < MIN_VALID_VOLTAGE || value > MAX_VALID_VOLTAGE;
	}
}
