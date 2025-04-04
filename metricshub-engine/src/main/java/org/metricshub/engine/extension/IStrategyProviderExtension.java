package org.metricshub.engine.extension;

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

import java.util.List;
import org.metricshub.engine.strategy.IStrategy;
import org.metricshub.engine.telemetry.TelemetryManager;

/**
 * Defines the contract for extensions that provide strategies. Implementations of this interface
 * are responsible for generating a list of strategies ({@link IStrategy}) based on telemetry data at a specific time.
 */
public interface IStrategyProviderExtension {
	/**
	 * Generates and returns a list of strategies ({@link IStrategy}) based on the provided telemetry data
	 * and strategy time.
	 *
	 * @param telemetryManager The {@link TelemetryManager} providing access to telemetry data and metrics.
	 *
	 * @param strategyTime A {@link Long} value representing the time at which the strategies need to be applied.
	 *
	 * @return A {@link List} of {@link IStrategy} instances that have been generated based on the telemetry data
	 *         and strategy time.
	 */
	List<IStrategy> generate(TelemetryManager telemetryManager, Long strategyTime);
}
