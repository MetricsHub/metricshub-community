package org.metricshub.web.dto.mcp;

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

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Result wrapper containing compressed telemetry data or an error message.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class TelemetryResult {

	/**
	 * Compressed telemetry data with monitors grouped by type.
	 */
	private MonitorsVo telemetry;

	/**
	 * Error message when telemetry collection fails or host is not configured.
	 */
	private String errorMessage;

	/**
	 * Constructs a TelemetryResult with an error message and no telemetry data.
	 *
	 * @param errorMessage the error message
	 */
	public TelemetryResult(final String errorMessage) {
		this.errorMessage = errorMessage;
	}

	/**
	 * Constructs a TelemetryResult with telemetry data and no error.
	 *
	 * @param telemetry the compressed telemetry data
	 */
	public TelemetryResult(final MonitorsVo telemetry) {
		this.telemetry = telemetry;
	}
}
