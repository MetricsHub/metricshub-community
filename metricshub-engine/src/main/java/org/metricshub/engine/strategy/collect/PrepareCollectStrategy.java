package org.metricshub.engine.strategy.collect;

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

import java.util.Collection;
import java.util.Map;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import org.metricshub.engine.client.ClientsExecutor;
import org.metricshub.engine.extension.ExtensionManager;
import org.metricshub.engine.strategy.AbstractStrategy;
import org.metricshub.engine.telemetry.TelemetryManager;

/**
 * The {@code PrepareCollectStrategy} class represents a strategy for preparing the collection of metrics
 * from monitors in a monitoring system.
 *
 * <p>
 * This class is part of a strategy design pattern and is responsible for preparing the collection by
 * saving metric values and updating collect times before the actual collection operation.
 * </p>
 *
 * <p>
 * It iterates through all monitors and metrics managed by the telemetry manager, saving metric values,
 * and updating collect times for discovered metrics.
 * </p>
 */
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class PrepareCollectStrategy extends AbstractStrategy {

	/**
	 * Constructs a new {@code PrepareCollectStrategy} using the provided telemetry manager, strategy time, and
	 * clients executor.
	 *
	 * @param telemetryManager The telemetry manager responsible for managing telemetry-related operations.
	 * @param strategyTime     The time when the strategy is executed.
	 * @param clientsExecutor  The executor for managing clients used in the strategy.
	 * @param extensionManager The extension manager where all the required extensions are handled.
	 */
	public PrepareCollectStrategy(
		@NonNull final TelemetryManager telemetryManager,
		@NonNull final Long strategyTime,
		@NonNull final ClientsExecutor clientsExecutor,
		@NonNull final ExtensionManager extensionManager
	) {
		super(telemetryManager, strategyTime, clientsExecutor, extensionManager);
	}

	@Override
	public void run() {
		telemetryManager
			.getMonitors()
			.values()
			.stream()
			.map(Map::values)
			.flatMap(Collection::stream)
			.forEach(monitor ->
				monitor
					.getMetrics()
					.values()
					.stream()
					.forEach(metric -> {
						// Save metric, push current value to previous and current collect time to previous
						// Why ? Before the next collect we save the metric previous values
						// in order to compute delta and rates
						metric.save();

						// Discovered metrics should be refreshed in the collect
						// so that they are considered collected
						if (metric.isResetMetricTime()) {
							metric.setCollectTime(strategyTime);
						}
					})
			);
	}
}
