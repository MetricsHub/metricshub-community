package org.metricshub.agent.service.task;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * MetricsHub Agent
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

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NonNull;
import org.metricshub.agent.config.ResourceConfig;
import org.metricshub.agent.context.MetricDefinitions;
import org.metricshub.agent.opentelemetry.MetricsExporter;
import org.metricshub.engine.extension.ExtensionManager;
import org.metricshub.engine.telemetry.TelemetryManager;

/**
 * Information required for the monitoring task, including telemetry manager, resource configuration, keys, metrics exporter, and host metric definitions.
 */
@Data
@AllArgsConstructor
@Builder
public class MonitoringTaskInfo {

	@NonNull
	private TelemetryManager telemetryManager;

	@NonNull
	private ResourceConfig resourceConfig;

	@NonNull
	private String resourceKey;

	@NonNull
	private String resourceGroupKey;

	@NonNull
	private MetricsExporter metricsExporter;

	@NonNull
	private MetricDefinitions hostMetricDefinitions;

	@NonNull
	private ExtensionManager extensionManager;

	private boolean isSuppressZerosCompression;
}
