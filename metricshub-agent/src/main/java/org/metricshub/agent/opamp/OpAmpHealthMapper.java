package org.metricshub.agent.opamp;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * MetricsHub Agent
 * ჻჻჻჻჻჻
 * Copyright 2023 - 2026 MetricsHub
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

import java.lang.management.ManagementFactory;
import java.util.concurrent.TimeUnit;
import org.metricshub.opamp.proto.ComponentHealth;
import org.metricshub.web.dto.ApplicationStatus;
import org.metricshub.web.dto.ApplicationStatus.Status;

/**
 * Maps the MetricsHub {@link ApplicationStatus} to the OpAMP {@code ComponentHealth} message: a
 * top-level health with the agent status, plus an {@code otel_collector} sub-component. Resource
 * counters, memory and CPU are deliberately excluded — they are already exported as metrics
 * through the OTLP self-monitoring pipeline.
 */
public class OpAmpHealthMapper {

	/**
	 * Name of the OpenTelemetry Collector sub-component in the health map.
	 */
	static final String OTEL_COLLECTOR_COMPONENT = "otel_collector";

	private OpAmpHealthMapper() {}

	/**
	 * Builds the OpAMP {@code ComponentHealth} from the application status.
	 *
	 * @param applicationStatus the current application status
	 * @return the corresponding {@code ComponentHealth}
	 */
	public static ComponentHealth map(final ApplicationStatus applicationStatus) {
		final boolean healthy = applicationStatus.getStatus() == Status.UP;
		final long nowNanos = TimeUnit.MILLISECONDS.toNanos(System.currentTimeMillis());
		final long startTimeNanos = TimeUnit.MILLISECONDS.toNanos(ManagementFactory.getRuntimeMXBean().getStartTime());

		final ComponentHealth.Builder builder = ComponentHealth.newBuilder()
			.setHealthy(healthy)
			.setStatus(applicationStatus.getStatus().toString())
			.setStartTimeUnixNano(startTimeNanos)
			.setStatusTimeUnixNano(nowNanos);

		final String otelCollectorStatus = applicationStatus.getOtelCollectorStatus();
		if (otelCollectorStatus != null) {
			builder.putComponentHealthMap(
				OTEL_COLLECTOR_COMPONENT,
				ComponentHealth.newBuilder()
					.setHealthy("running".equals(otelCollectorStatus) || "disabled".equals(otelCollectorStatus))
					.setStatus(otelCollectorStatus)
					.setStatusTimeUnixNano(nowNanos)
					.build()
			);
		}

		return builder.build();
	}
}
