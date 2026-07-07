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
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.metricshub.agent.context.AgentContext;
import org.metricshub.agent.helper.ConfigHelper;
import org.metricshub.engine.extension.ExtensionManager;
import org.metricshub.web.AgentContextHolder;
import org.metricshub.web.dto.RestartStatus;
import org.metricshub.web.service.AgentLifecycleService;
import org.metricshub.web.service.AgentLifecycleService.RestartRequestAck;
import org.metricshub.web.service.AgentLifecycleService.RestartRequestResult;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller for managing the MetricsHub Agent life-cycle.
 */
@Slf4j
@RestController
@RequestMapping(value = "/api/agent", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Agent", description = "Agent life-cycle management")
public class AgentController {

	private final AgentContextHolder agentContextHolder;
	private final AgentLifecycleService agentLifecycleService;

	/**
	 * Constructor for AgentController.
	 *
	 * @param agentContextHolder    the AgentContextHolder to access the running
	 *                              agent context (for the configuration directory).
	 * @param agentLifecycleService the AgentLifecycleService to manage the agent
	 *                              lifecycle.
	 */
	public AgentController(
		final AgentContextHolder agentContextHolder,
		final AgentLifecycleService agentLifecycleService
	) {
		this.agentContextHolder = agentContextHolder;
		this.agentLifecycleService = agentLifecycleService;
	}

	/**
	 * Restarts the MetricsHub Agent asynchronously.
	 * <p>
	 * The heavy work (extension reload, context rebuild, task scheduler restart,
	 * OpenTelemetry Collector restart, ...) runs on a dedicated background thread
	 * so the HTTP request thread returns immediately with {@code 202 Accepted}.
	 * Clients should poll {@code GET /api/agent/restart/status} to observe the
	 * outcome.
	 * </p>
	 * <p>
	 * Concurrency policy is <b>queue-and-run-after</b>: if a restart is already running,
	 * the new request is queued and will run right after the current one. If another
	 * request was already queued, the older queued request is discarded (its pre-built
	 * context is closed) and the new one takes its place — so the newest saved
	 * configuration always wins.
	 * </p>
	 *
	 * @return a ResponseEntity indicating whether the restart was scheduled, queued or
	 *         coalesced, together with the {@code requestId} assigned to this request so the
	 *         client can correlate the polled restart status with its own request.
	 */
	@Operation(
		summary = "Restart the MetricsHub Agent (asynchronous)",
		description = "Schedules a background reload of the agent context, configuration, and extensions. " +
			"Returns immediately; poll /api/agent/restart/status to observe progress. If a restart is already " +
			"running, the new request is queued (or coalesces with a previously queued one). The response carries " +
			"a requestId; a polled terminal status belongs to this request only when its requestId is >= this value.",
		responses = {
			@ApiResponse(responseCode = "202", description = "Agent restart scheduled, queued or coalesced"),
			@ApiResponse(responseCode = "500", description = "Failed to schedule the restart")
		}
	)
	@PostMapping("/restart")
	public ResponseEntity<Map<String, Object>> restart() {
		log.info("Received request to restart MetricsHub Agent.");
		try {
			final String configDir = agentContextHolder.getAgentContext().getConfigDirectory().toAbsolutePath().toString();

			final RestartRequestAck ack = agentLifecycleService.restartAsync(() -> {
				try {
					// Reload extension manager to pick up any new or updated extensions
					final ExtensionManager extensionManager = ConfigHelper.loadExtensionManager();
					// Create new context reusing the current configuration directory
					return new AgentContext(configDir, extensionManager);
				} catch (Exception e) {
					throw new IllegalStateException("Failed to build reloaded AgentContext: " + e.getMessage(), e);
				}
			});

			return ResponseEntity.accepted().body(
				Map.of("message", messageFor(ack.result()), "result", ack.result().name(), "requestId", ack.requestId())
			);
		} catch (Exception e) {
			log.error("Failed to schedule MetricsHub Agent restart.", e);
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
				Map.of("error", "Failed to restart MetricsHub Agent: " + e.getMessage())
			);
		}
	}

	/**
	 * Returns the current or last-known status of a MetricsHub Agent restart request.
	 *
	 * @return a ResponseEntity containing the restart status.
	 */
	@Operation(
		summary = "Get the current MetricsHub Agent restart status",
		description = "Returns the state (IDLE, IN_PROGRESS, SUCCEEDED, FAILED) of the last restart request, " +
			"together with the current active AgentContext generation.",
		responses = { @ApiResponse(responseCode = "200", description = "Current restart status") }
	)
	@GetMapping("/restart/status")
	public ResponseEntity<RestartStatus> restartStatus() {
		return ResponseEntity.ok(agentLifecycleService.getRestartStatus());
	}

	/**
	 * Maps a {@link RestartRequestResult} to a human-readable message for the client.
	 */
	private static String messageFor(final RestartRequestResult result) {
		return switch (result) {
			case SCHEDULED -> "MetricsHub Agent restart scheduled.";
			case QUEUED -> "A MetricsHub Agent restart is already running; the new request has been queued.";
			case COALESCED -> "A MetricsHub Agent restart is already running and another was queued; " +
			"the newer request replaces the previously queued one.";
		};
	}
}
