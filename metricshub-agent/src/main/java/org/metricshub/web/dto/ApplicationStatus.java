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

import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents the status of the application.
 */
@NoArgsConstructor
@Data
@Builder
@AllArgsConstructor
public class ApplicationStatus {

	private Status status;
	private Map<String, String> agentInfo;
	private String otelCollectorStatus;
	private long numberOfObservedResources;
	private long numberOfConfiguredResources;
	private long numberOfMonitors;
	private long numberOfJobs;
	private long memoryUsageBytes;
	private double memoryUsagePercent;
	private double cpuUsage;
	private Long licenseDaysRemaining;
	private String licenseType;

	/**
	 * Enumeration representing the status of the application.
	 */
	public enum Status {
		UP,
		DOWN
	}
}
