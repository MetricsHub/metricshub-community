package org.metricshub.web.service;

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
import org.metricshub.agent.config.ResourceConfig;
import org.metricshub.agent.context.AgentContext;
import org.metricshub.engine.telemetry.TelemetryManager;
import org.metricshub.web.AgentContextHolder;
import org.metricshub.web.dto.ApplicationStatus;
import org.metricshub.web.dto.ApplicationStatus.Status;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Service class for managing the status of the MetricsHub Agent application.
 */
@Service
public class ApplicationStatusService {

	private AgentContextHolder agentContextHolder;

	/**
	 * Constructor for ApplicationStatusService.
	 * @param agentContextHolder the AgentContextHolder to retrieve the current agent context.
	 */
	@Autowired
	public ApplicationStatusService(final AgentContextHolder agentContextHolder) {
		this.agentContextHolder = agentContextHolder;
	}

	/**
	 * Retrieves the current status of the application.
	 *
	 * @return the current application status.
	 */
	public ApplicationStatus reportApplicationStatus() {
		final var agentContext = agentContextHolder.getAgentContext();
		return ApplicationStatus
			.builder()
			.status(determineApplicationStatus(agentContext))
			.agentInfo(readAgentInfo(agentContext))
			.isOtelCollectorRunning(isOtelCollectorRunning(agentContext))
			.numberOfObservedResources(determineNumberOfObservedResources(agentContext))
			.numberOfConfiguredResources(determineNumberOfConfiguredResources(agentContext))
			.build();
	}

	/**
	 * Determines the number of resources configured in the agent context.
	 *
	 * @param agentContext The agent context containing configuration details.
	 * @return the number of configured resources.
	 */
	private static long determineNumberOfConfiguredResources(final AgentContext agentContext) {
		var configuredResources = 0;
		final var config = agentContext.getAgentConfig();
		final var resources = config.getResources();
		if (resources != null) {
			configuredResources += resources.size();
		}
		final var resourceGroups = config.getResourceGroups();
		if (resourceGroups != null) {
			for (final var resourceGroup : resourceGroups.values()) {
				final Map<String, ResourceConfig> resourcesInGroup = resourceGroup.getResources();
				if (resourcesInGroup != null) {
					configuredResources += resourcesInGroup.size();
				}
			}
		}
		return configuredResources;
	}

	/**
	 * Determines the number of resources currently being observed by the agent.
	 *
	 * @param agentContext the AgentContext containing telemetry managers.
	 * @return the number of observed resources.
	 */
	private static long determineNumberOfObservedResources(final AgentContext agentContext) {
		final var telemetryManagers = agentContext.getTelemetryManagers();
		if (telemetryManagers == null) {
			return 0;
		}
		return telemetryManagers
			.values()
			.stream()
			.flatMap((Map<String, TelemetryManager> telemetryManagerGroup) -> telemetryManagerGroup.values().stream())
			.count();
	}

	/**
	 * Checks if the OpenTelemetry Collector is currently running.
	 *
	 * @param agentContext the AgentContext containing the OpenTelemetry Collector process service.
	 * @return true if the OpenTelemetry Collector is running, false otherwise.
	 */
	private static boolean isOtelCollectorRunning(final AgentContext agentContext) {
		final var otelCollectorProcessService = agentContext.getOtelCollectorProcessService();
		if (otelCollectorProcessService != null) {
			return otelCollectorProcessService.isStarted();
		}
		return false;
	}

	/**
	 * Reads the agent information from the AgentContext.
	 *
	 * @param agentContext the AgentContext containing agent information.
	 *
	 * @return a map containing agent attributes, or an empty map if no agent info is available.
	 */
	private static Map<String, String> readAgentInfo(final AgentContext agentContext) {
		final var agentInfo = agentContext.getAgentInfo();
		if (agentInfo == null) {
			return Map.of();
		}
		return agentInfo.getAttributes();
	}

	/**
	 * Determines the current status of the application based on the task scheduling service.
	 *
	 * @param agentContext the AgentContext containing the task scheduling service.
	 * @return the status of the application, either UP or DOWN.
	 */
	private static Status determineApplicationStatus(AgentContext agentContext) {
		final var taskSchedulingService = agentContext.getTaskSchedulingService();
		if (taskSchedulingService == null) {
			return Status.DOWN;
		}

		final var taskScheduler = taskSchedulingService.getTaskScheduler();

		if (taskScheduler != null && taskScheduler.isRunning()) {
			return Status.UP;
		}

		return Status.DOWN;
	}
}
