package org.metricshub.engine.strategy.collect;

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
import lombok.Builder;
import lombok.NonNull;
import org.metricshub.engine.client.ClientsExecutor;
import org.metricshub.engine.extension.ExtensionManager;
import org.metricshub.engine.extension.IProtocolExtension;
import org.metricshub.engine.strategy.AbstractStrategy;
import org.metricshub.engine.telemetry.MetricFactory;
import org.metricshub.engine.telemetry.Monitor;
import org.metricshub.engine.telemetry.TelemetryManager;

/**
 * A strategy that aims to perform health check over a hostname on each protocol
 * (HTTP, SNMP, IPMI, ...).
 *
 * <p>
 * This aims to report the responsiveness of configured protocols on a resource.
 * The health check is performed at the beginning of each data collection cycle,
 * and a metric is generated for each protocol indicating whether it is
 * responding or not.
 * </p>
 */
public class ProtocolHealthCheckStrategy extends AbstractStrategy {

	/**
	 * Protocol up status value '1.0'
	 */
	static final Double UP = 1.0;

	/**
	 * Protocol down status value '0.0'
	 */
	static final Double DOWN = 0.0;

	/**
	 * Protocol up metric format
	 */
	static final String UP_METRIC_FORMAT = "metricshub.host.up{protocol=\"%s\"}";

	/**
	 * Protocol response time metric format
	 */
	static final String RESPONSE_TIME_METRIC_FORMAT = "metricshub.host.response_time{protocol=\"%s\"}";

	/**
	 * Constructs a new {@code HealthCheckStrategy} using the provided telemetry
	 * manager, strategy time, and clients executor.
	 *
	 * @param telemetryManager The telemetry manager responsible for managing
	 *                         telemetry-related operations.
	 * @param strategyTime     The time when the strategy is executed.
	 * @param clientsExecutor  The executor for managing clients used in the
	 *                         strategy.
	 * @param extensionManager The extension manager where all the required extensions are handled.
	 */
	@Builder
	public ProtocolHealthCheckStrategy(
		@NonNull final TelemetryManager telemetryManager,
		@NonNull final Long strategyTime,
		@NonNull final ClientsExecutor clientsExecutor,
		@NonNull final ExtensionManager extensionManager
	) {
		super(telemetryManager, strategyTime, clientsExecutor, extensionManager);
	}

	@Override
	public void run() {
		// Call the extensions to check the protocol health
		final List<IProtocolExtension> protocolExtensions = extensionManager.findProtocolCheckExtensions(telemetryManager);

		// CHECKSTYLE:OFF
		protocolExtensions.forEach(protocolExtension -> {
			// Record the start time before launching protocol checks
			final long startTime = System.currentTimeMillis();
			protocolExtension
				.checkProtocol(telemetryManager)
				.ifPresent(isUp -> {
					// Calculate the response time of each protocol check.
					final Double responseTime = (System.currentTimeMillis() - startTime) / 1000.0;
					final Monitor endpointHostMonitor = telemetryManager.getEndpointHostMonitor();
					final Long strategyTime = telemetryManager.getStrategyTime();
					final MetricFactory metricFactory = new MetricFactory();
					Double up = DOWN;
					if (Boolean.TRUE.equals(isUp)) {
						up = UP;
						// Collect protocol check response time metric if response up is true
						metricFactory.collectNumberMetric(
							endpointHostMonitor,
							RESPONSE_TIME_METRIC_FORMAT.formatted(protocolExtension.getIdentifier()),
							responseTime,
							strategyTime
						);
					}
					// Collect protocol check metric
					metricFactory.collectNumberMetric(
						endpointHostMonitor,
						UP_METRIC_FORMAT.formatted(protocolExtension.getIdentifier()),
						up,
						strategyTime
					);
				});
		});
		// CHECKSTYLE:ON
	}

	@Override
	public long getStrategyTimeout() {
		return telemetryManager.getHostConfiguration().getStrategyTimeout();
	}

	@Override
	public Long getStrategyTime() {
		return telemetryManager.getStrategyTime();
	}
}
