package org.metricshub.engine.strategy.simple;

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

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import org.metricshub.engine.client.ClientsExecutor;
import org.metricshub.engine.connector.model.monitor.MonitorJob;
import org.metricshub.engine.connector.model.monitor.SimpleMonitorJob;
import org.metricshub.engine.connector.model.monitor.task.AbstractMonitorTask;
import org.metricshub.engine.extension.ExtensionManager;
import org.metricshub.engine.strategy.AbstractAllAtOnceStrategy;
import org.metricshub.engine.telemetry.TelemetryManager;

/**
 * The {@code SimpleStrategy} class represents a simple strategy for executing monitor tasks.
 * It extends {@link AbstractAllAtOnceStrategy} and is responsible for coordinating the execution of tasks for all monitors at once.
 *
 * <p>
 * The class uses the TelemetryManager to manage monitors and metrics associated with the monitor tasks.
 * </p>
 */
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class SimpleStrategy extends AbstractAllAtOnceStrategy {

	private static final String JOB_NAME = "simple";

	/**
	 * Builder for constructing instances of {@code SimpleStrategy}.
	 *
	 * @param telemetryManager The telemetry manager for managing monitors and metrics.
	 * @param strategyTime     The time at which the strategy is executed.
	 * @param clientsExecutor  The executor for running connector clients.
	 * @param extensionManager The extension manager where all the required extensions are handled.
	 */
	@Builder
	public SimpleStrategy(
		@NonNull final TelemetryManager telemetryManager,
		@NonNull final Long strategyTime,
		@NonNull final ClientsExecutor clientsExecutor,
		@NonNull final ExtensionManager extensionManager
	) {
		super(telemetryManager, strategyTime, clientsExecutor, extensionManager);
	}

	@Override
	protected String getJobName() {
		return JOB_NAME;
	}

	@Override
	protected AbstractMonitorTask retrieveTask(MonitorJob monitorJob) {
		if (monitorJob instanceof SimpleMonitorJob simpleMonitorJob) {
			return simpleMonitorJob.getSimple();
		}
		return null;
	}
}
