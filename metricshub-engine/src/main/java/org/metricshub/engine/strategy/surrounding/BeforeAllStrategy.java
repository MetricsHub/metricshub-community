package org.metricshub.engine.strategy.surrounding;

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
import java.util.Map;
import java.util.Set;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.NonNull;
import org.metricshub.engine.client.ClientsExecutor;
import org.metricshub.engine.connector.model.Connector;
import org.metricshub.engine.connector.model.monitor.task.source.Source;
import org.metricshub.engine.extension.ExtensionManager;
import org.metricshub.engine.telemetry.TelemetryManager;

/**
 * Defines a strategy for executing tasks before all source references have been processed.
 * Inherits behavior from {@link SurroundingStrategy} and provides a custom builder.
 */
@EqualsAndHashCode(callSuper = true)
public class BeforeAllStrategy extends SurroundingStrategy {

	/**
	 * Initializes a new instance of {@code BeforeAllStrategy} with the necessary components for executing the strategy.
	 * This constructor sets up the strategy with a telemetry manager, strategy execution time, a clients executor,
	 * and a specific connector to process beforeAll sources.
	 *
	 * @param telemetryManager The telemetry manager responsible for managing telemetry data (monitors and metrics).
	 * @param strategyTime     The execution time of the strategy, used for timing purpose.
	 * @param clientsExecutor  An executor service for handling client operations within the beforeAll sources.
	 * @param connector        The specific connector instance where the beforeAll sources are defined.
	 * @param extensionManager The extension manager where all the required extensions are handled.
	 */
	@Builder
	public BeforeAllStrategy(
		@NonNull TelemetryManager telemetryManager,
		@NonNull Long strategyTime,
		@NonNull ClientsExecutor clientsExecutor,
		@NonNull Connector connector,
		@NonNull ExtensionManager extensionManager
	) {
		super(telemetryManager, strategyTime, clientsExecutor, connector, extensionManager);
	}

	@Override
	protected Map<String, Source> getSurroundingSources() {
		return connector.getBeforeAll();
	}

	@Override
	protected String getJobName() {
		return "beforeAll";
	}

	@Override
	protected List<Set<String>> getSourceDependencies() {
		return connector.getBeforeAllSourceDep();
	}
}
