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
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.metricshub.web.dto.ApplicationStatus;
import org.metricshub.web.service.ApplicationStatusService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller for status of the MetricsHub Agent service.
 */
@RestController
@RequestMapping(value = "/api", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Application Status", description = "Application health and status information")
public class ApplicationStatusController {

	private ApplicationStatusService applicationStatusService;

	/**
	 * Constructor for ApplicationStatusController.
	 *
	 * @param applicationStatusService the ApplicationStatusService to handle status requests.
	 */
	@Autowired
	public ApplicationStatusController(final ApplicationStatusService applicationStatusService) {
		this.applicationStatusService = applicationStatusService;
	}

	/**
	 * Status check endpoint that returns a simple JSON response indicating the service is up.
	 *
	 * @return A JSON string indicating the service status.
	 */
	@Operation(
		summary = "Get application status",
		description = "Returns the current status of the MetricsHub Agent including resource counts and system metrics.",
		responses = { @ApiResponse(responseCode = "200", description = "Application status retrieved successfully") }
	)
	@GetMapping("/status")
	public ApplicationStatus status() {
		return applicationStatusService.reportApplicationStatus();
	}
}
