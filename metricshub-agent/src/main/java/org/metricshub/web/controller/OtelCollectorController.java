package org.metricshub.web.controller;

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

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.metricshub.web.exception.OtelCollectorException;
import org.metricshub.web.service.OtelCollectorService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller for OpenTelemetry Collector control and log viewing.
 */
@RestController
@RequestMapping(value = "/api/otel/collector")
@Tag(name = "OTel Collector", description = "OpenTelemetry Collector control and log viewing")
public class OtelCollectorController {

	private final OtelCollectorService otelCollectorService;

	/**
	 * Constructor for OtelCollectorController.
	 *
	 * @param otelCollectorService the service for OTEL collector restart and logs
	 */
	public OtelCollectorController(final OtelCollectorService otelCollectorService) {
		this.otelCollectorService = otelCollectorService;
	}

	/**
	 * Restarts the OpenTelemetry Collector process.
	 *
	 * @return JSON with success message
	 * @throws OtelCollectorException if restart fails
	 */
	@Operation(
		summary = "Restart the OpenTelemetry Collector",
		description = "Restarts the OpenTelemetry Collector process.",
		responses = {
			@ApiResponse(responseCode = "200", description = "Collector restarted successfully"),
			@ApiResponse(responseCode = "500", description = "Failed to restart the collector")
		}
	)
	@PostMapping(value = "/restart", produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<String> restartCollector() throws OtelCollectorException {
		otelCollectorService.restartCollector();
		return ResponseEntity.ok("{\"message\": \"OpenTelemetry Collector restarted successfully.\"}");
	}

	/**
	 * Returns the last N lines of the OpenTelemetry Collector log file.
	 *
	 * @param tailLines maximum number of lines to return (default 200)
	 * @return the log tail as plain text
	 * @throws OtelCollectorException if logs cannot be read
	 */
	@Operation(
		summary = "Get collector logs",
		description = "Returns the last N lines of the OpenTelemetry Collector log file.",
		responses = {
			@ApiResponse(responseCode = "200", description = "Log tail retrieved successfully"),
			@ApiResponse(responseCode = "500", description = "Failed to read collector logs")
		}
	)
	@GetMapping(value = "/logs", produces = MediaType.TEXT_PLAIN_VALUE)
	public ResponseEntity<String> getCollectorLogs(
		@Parameter(description = "Maximum number of lines to return") @RequestParam(
			name = "tailLines",
			defaultValue = "200"
		) int tailLines
	) throws OtelCollectorException {
		final String tail = otelCollectorService.getCollectorLogTail(tailLines);
		return ResponseEntity.ok(tail);
	}
}
