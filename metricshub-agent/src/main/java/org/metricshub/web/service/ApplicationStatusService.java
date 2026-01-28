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

import static org.metricshub.agent.helper.AgentConstants.AGENT_RESOURCE_SERVICE_NAME_ATTRIBUTE_KEY;

import com.sun.management.OperatingSystemMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
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
	 *
	 * @param agentContextHolder the AgentContextHolder to retrieve the current
	 *                           agent context.
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
			.otelCollectorStatus(determineOtelCollectorStatus(agentContext))
			.numberOfObservedResources(determineNumberOfObservedResources(agentContext))
			.numberOfConfiguredResources(determineNumberOfConfiguredResources(agentContext))
			.numberOfMonitors(determineNumberOfMonitors(agentContext))
			.numberOfJobs(determineNumberOfJobs(agentContext))
			.memoryUsageBytes(determineMemoryUsageBytes())
			.memoryUsagePercent(determineMemoryUsagePercent())
			.cpuUsage(determineCpuUsage())
			.licenseDaysRemaining(determineLicenseDaysRemaining(agentContext))
			.licenseType(determineLicenseType(agentContext))
			.build();
	}

	/**
	 * Determines the license type.
	 *
	 * @param agentContext The agent context containing configuration details.
	 * @return the license type.
	 */
	private static String determineLicenseType(final AgentContext agentContext) {
		final var agentInfo = agentContext.getAgentInfo();
		if (agentInfo != null) {
			final var attributes = agentInfo.getAttributes();
			if (attributes != null && "MetricsHub Agent".equals(attributes.get(AGENT_RESOURCE_SERVICE_NAME_ATTRIBUTE_KEY))) {
				return "Community";
			}
		}
		return "Enterprise";
	}

	/**
	 * Determines the validity time remaining of the license key in days.
	 *
	 * @param agentContext The agent context containing configuration details.
	 * @return the number of days remaining. Returns null for Community Edition.
	 */
	private static Long determineLicenseDaysRemaining(final AgentContext agentContext) {
		if ("Community".equals(determineLicenseType(agentContext))) {
			return null;
		}

		final var agentInfo = agentContext.getAgentInfo();
		if (agentInfo == null || agentInfo.getAttributes() == null) {
			return null;
		}

		// Try parsing expiration date
		final var expirationDateString = agentInfo.getAttributes().get("license.expiration_date");
		if (expirationDateString != null) {
			try {
				final var expirationDate = LocalDate.parse(expirationDateString);
				return ChronoUnit.DAYS.between(LocalDate.now(), expirationDate);
			} catch (Exception e) {
				// Ignore parsing errors
			}
		}

		// Try parsing days remaining directly
		final var daysRemainingString = agentInfo.getAttributes().get("license.days_remaining");
		if (daysRemainingString != null) {
			try {
				return Long.parseLong(daysRemainingString);
			} catch (NumberFormatException e) {
				// Ignore parsing errors
			}
		}

		return null;
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
	 * Determines the number of monitors currently configured in the agent.
	 *
	 * @param agentContext the AgentContext containing telemetry managers.
	 * @return the number of monitors.
	 */
	private static long determineNumberOfMonitors(final AgentContext agentContext) {
		final var telemetryManagers = agentContext.getTelemetryManagers();
		if (telemetryManagers == null) {
			return 0;
		}
		return telemetryManagers
			.values()
			.stream()
			.flatMap((Map<String, TelemetryManager> telemetryManagerGroup) -> telemetryManagerGroup.values().stream())
			.mapToLong(telemetryManager -> telemetryManager.getMonitors().values().stream().mapToLong(Map::size).sum())
			.sum();
	}

	/**
	 * Determines the number of jobs (scheduled tasks).
	 *
	 * @param agentContext the AgentContext containing the task scheduling service.
	 * @return the number of jobs.
	 */
	private static long determineNumberOfJobs(final AgentContext agentContext) {
		final var taskSchedulingService = agentContext.getTaskSchedulingService();
		if (taskSchedulingService != null && taskSchedulingService.getSchedules() != null) {
			return taskSchedulingService.getSchedules().size();
		}
		return 0;
	}

	/**
	 * Determines the memory usage in bytes.
	 *
	 * @return the memory usage.
	 */
	private static long determineMemoryUsageBytes() {
		final MemoryMXBean memoryMXBean = ManagementFactory.getMemoryMXBean();
		return memoryMXBean.getHeapMemoryUsage().getUsed() + memoryMXBean.getNonHeapMemoryUsage().getUsed();
	}

	/**
	 * Determines the memory usage percentage (Agent Used / Machine Total).
	 *
	 * @return the memory usage percentage, or 0.0 if total memory cannot be
	 *         determined.
	 */
	private static double determineMemoryUsagePercent() {
		final var usedBytes = determineMemoryUsageBytes();
		final java.lang.management.OperatingSystemMXBean displayOSBean = ManagementFactory.getOperatingSystemMXBean();
		if (displayOSBean instanceof OperatingSystemMXBean osBean) {
			final long totalBytes = osBean.getTotalMemorySize();
			if (totalBytes > 0) {
				return ((double) usedBytes / totalBytes) * 100.0;
			}
		}
		return 0.0;
	}

	/**
	 * Determines the CPU usage as a percentage.
	 *
	 * @return the CPU usage percentage (0.0 to 100.0).
	 */
	private static double determineCpuUsage() {
		final java.lang.management.OperatingSystemMXBean displayOSBean = ManagementFactory.getOperatingSystemMXBean();
		if (displayOSBean instanceof OperatingSystemMXBean osBean) { // Use pattern matching for instanceof
			final var processCpuLoad = osBean.getProcessCpuLoad();
			if (processCpuLoad >= 0) {
				return processCpuLoad * 100.0;
			}
		}
		return 0.0;
	}

	/**
	 * Determines the OpenTelemetry Collector status.
	 *
	 * @param agentContext the AgentContext containing the OpenTelemetry Collector
	 *                     process service.
	 * @return the OpenTelemetry Collector status: running, disabled, errored,
	 *         not-installed
	 */
	private static String determineOtelCollectorStatus(final AgentContext agentContext) {
		if (agentContext.getAgentConfig().getOtelCollector().isDisabled()) {
			return "disabled";
		}
		final var otelCollectorProcessService = agentContext.getOtelCollectorProcessService();
		if (otelCollectorProcessService == null) {
			return null;
		}
		if (!otelCollectorProcessService.isExecutableInstalled()) {
			return "not-installed";
		} else if (otelCollectorProcessService.isStarted()) {
			return "running";
		} else {
			return "errored";
		}
	}

	/**
	 * Reads the agent information from the AgentContext.
	 *
	 * @param agentContext the AgentContext containing agent information.
	 *
	 * @return a map containing agent attributes, or an empty map if no agent info
	 *         is available.
	 */
	private static Map<String, String> readAgentInfo(final AgentContext agentContext) {
		final var agentInfo = agentContext.getAgentInfo();
		if (agentInfo == null) {
			return Map.of();
		}
		return agentInfo.getAttributes();
	}

	/**
	 * Determines the current status of the application based on the task scheduling
	 * service.
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
