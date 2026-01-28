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

import lombok.extern.slf4j.Slf4j;
import org.metricshub.agent.context.AgentContext;
import org.metricshub.agent.helper.ConfigHelper;
import org.metricshub.engine.extension.ExtensionManager;
import org.metricshub.web.AgentContextHolder;
import org.metricshub.web.service.AgentLifecycleService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller for managing the MetricsHub Agent life-cycle.
 */
@Slf4j
@RestController
@RequestMapping(value = "/api/agent", produces = MediaType.APPLICATION_JSON_VALUE)
public class AgentController {

	private final AgentContextHolder agentContextHolder;
	private final AgentLifecycleService agentLifecycleService;

	/**
	 * Constructor for AgentController.
	 *
	 * @param agentContextHolder    the AgentContextHolder to access the running
	 *                              agent context.
	 * @param agentLifecycleService the AgentLifecycleService to manage the agent
	 *                              lifecycle.
	 */
	public AgentController(AgentContextHolder agentContextHolder, AgentLifecycleService agentLifecycleService) {
		this.agentContextHolder = agentContextHolder;
		this.agentLifecycleService = agentLifecycleService;
	}

	/**
	 * Restarts the MetricsHub Agent.
	 * <p>
	 * This triggers a reload of the agent context, configuration, and extensions.
	 * </p>
	 *
	 * @return a ResponseEntity indicating the result of the operation.
	 */
	@PostMapping("/restart")
	public ResponseEntity<String> restart() {
		log.info("Received request to restart MetricsHub Agent.");
		try {
			final AgentContext runningContext = agentContextHolder.getAgentContext();

			// Reload extension manager to pick up any new or updated extensions
			final ExtensionManager extensionManager = ConfigHelper.loadExtensionManager();

			// Create new context reusing the current configuration directory
			final String configDir = runningContext.getConfigDirectory().toAbsolutePath().toString();
			final AgentContext reloadedContext = new AgentContext(configDir, extensionManager);

			// Use the Lifecycle service to perform a clean restart
			agentLifecycleService.restart(runningContext, reloadedContext);

			return ResponseEntity.ok("{\"message\": \"MetricsHub Agent restarted successfully.\"}");
		} catch (Exception e) {
			log.error("Failed to restart MetricsHub Agent.", e);
			return ResponseEntity
					.status(HttpStatus.INTERNAL_SERVER_ERROR)
					.body("{\"error\": \"Failed to restart MetricsHub Agent: " + e.getMessage() + "\"}");
		}
	}
}
