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

import org.metricshub.engine.telemetry.Monitor;

/**
 * The MemoryMetricNormalizer class is responsible for normalizing memory metrics.
 * It extends the AbstractMetricNormalizer class to provide specific
 * normalization logic for memory monitor hardware metrics.
 */
public class MemoryMetricNormalizer extends AbstractMetricNormalizer {

	/**
	 * Constructs a new instance of MemoryMetricNormalizer with the specified strategy time.
	 * @param strategyTime The strategy time in milliseconds
	 * @param hostname     The hostname of the monitor
	 */
	public MemoryMetricNormalizer(long strategyTime, String hostname) {
		super(strategyTime, hostname);
	}

	/**
	 * Normalizes the metrics of the given monitor.
	 * @param monitor The monitor containing the metrics to be normalized
	 */
	@Override
	public void normalize(Monitor monitor) {
		normalizeErrorsLimitMetric(monitor);
	}
}
