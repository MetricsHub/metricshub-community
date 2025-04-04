package org.metricshub.hardware.threshold;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * MetricsHub Hardware Energy and Sustainability Module
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

import org.metricshub.engine.telemetry.Monitor;

/**
 * The TapeDriveMetricNormalizer class is responsible for normalizing TAPE DRIVE metrics.
 * It extends the AbstractMetricNormalizer class to provide specific
 * normalization logic for TAPE DRIVE monitor hardware metrics.
 */
public class TapeDriveMetricNormalizer extends AbstractMetricNormalizer {

	/**
	 * Constructs a new instance with the specified strategy time.
	 * @param strategyTime The strategy time in milliseconds
	 * @param hostname     The hostname of the monitor
	 */
	public TapeDriveMetricNormalizer(long strategyTime, String hostname) {
		super(strategyTime, hostname);
	}

	/**
	 * Normalizes tape drive limit metrics
	 * @param monitor A given {@link Monitor}
	 */
	@Override
	public void normalize(Monitor monitor) {
		normalizeErrorsLimitMetric(monitor);
	}
}
