package org.metricshub.web.dto;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * MetricsHub Agent
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

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents the status of the application.
 */
@Schema(description = "Application status and system metrics")
@NoArgsConstructor
@Data
@Builder
@AllArgsConstructor
public class ApplicationStatus {

	@Schema(description = "Application status (UP or DOWN)")
	private Status status;

	@Schema(description = "Agent information map")
	private Map<String, String> agentInfo;

	@Schema(description = "OpenTelemetry Collector status")
	private String otelCollectorStatus;

	@Schema(description = "Number of observed resources")
	private long numberOfObservedResources;

	@Schema(description = "Number of configured resources")
	private long numberOfConfiguredResources;

	@Schema(description = "Number of monitors")
	private long numberOfMonitors;

	@Schema(description = "Number of scheduled jobs")
	private long numberOfJobs;

	@Schema(description = "Memory usage in bytes")
	private long memoryUsageBytes;

	@Schema(description = "Total memory in bytes")
	private long memoryTotalBytes;

	@Schema(description = "CPU usage percentage")
	private double cpuUsage;

	@Schema(description = "Days remaining on license")
	private Long licenseDaysRemaining;

	@Schema(description = "License type")
	private String licenseType;

	/**
	 * Enumeration representing the status of the application.
	 */
	public enum Status {
		UP,
		DOWN
	}
}
