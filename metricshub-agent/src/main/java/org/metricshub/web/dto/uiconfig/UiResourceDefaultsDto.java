package org.metricshub.web.dto.uiconfig;

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

import java.util.List;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Effective default values a new resource inherits: agent-level settings
 * overridden by the selected resource group (when one is provided).
 * Durations are expressed in seconds.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class UiResourceDefaultsDto {

	private String loggerLevel;

	private String outputDirectory;

	/** Collect period in seconds. */
	private Long collectPeriod;

	private Integer discoveryCycle;

	private Boolean sequential;

	private Boolean enableSelfMonitoring;

	private Boolean logFileSourceDetails;

	private Boolean resolveHostnameToFqdn;

	private Set<String> monitorFilters;

	/** Job timeout in seconds. */
	private Long jobTimeout;

	private String stateSetCompression;

	private List<String> enrichments;

	private UiAlertingSystemConfigDto alertingSystem;
}
