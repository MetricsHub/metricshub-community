package org.metricshub.web.mcp;

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

import static org.metricshub.agent.helper.AgentConstants.AGENT_INFO_BUILD_DATE_NUMBER_ATTRIBUTE_KEY;
import static org.metricshub.agent.helper.AgentConstants.AGENT_INFO_BUILD_NUMBER_ATTRIBUTE_KEY;
import static org.metricshub.agent.helper.AgentConstants.AGENT_INFO_CC_VERSION_NUMBER_ATTRIBUTE_KEY;
import static org.metricshub.agent.helper.AgentConstants.AGENT_INFO_NAME_ATTRIBUTE_KEY;
import static org.metricshub.agent.helper.AgentConstants.AGENT_INFO_VERSION_ATTRIBUTE_KEY;
import static org.metricshub.agent.helper.AgentConstants.AGENT_RESOURCE_AGENT_HOST_NAME_ATTRIBUTE_KEY;
import static org.metricshub.agent.helper.AgentConstants.AGENT_RESOURCE_HOST_NAME_ATTRIBUTE_KEY;
import static org.metricshub.agent.helper.AgentConstants.AGENT_RESOURCE_HOST_TYPE_ATTRIBUTE_KEY;
import static org.metricshub.agent.helper.AgentConstants.AGENT_RESOURCE_OS_TYPE_ATTRIBUTE_KEY;
import static org.metricshub.agent.helper.AgentConstants.AGENT_RESOURCE_SERVICE_NAME_ATTRIBUTE_KEY;

import com.sun.management.OperatingSystemMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.util.Map;
import org.metricshub.agent.context.AgentInfo;
import org.metricshub.web.AgentContextHolder;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * MCP Tool service that exposes MetricsHub Agent metadata and runtime information.
 * This tool reports the same attributes as the metricshub.agent.info metric,
 * plus additional runtime details including memory and CPU usage.
 */
@Service
public class AgentInfoService implements IMCPToolService {

	private static final double PERCENTAGE_MULTIPLIER = 100.0;

	private final AgentContextHolder agentContextHolder;

	/**
	 * Constructor for AgentInfoService.
	 *
	 * @param agentContextHolder the {@link AgentContextHolder} instance to access the agent context
	 */
	@Autowired
	public AgentInfoService(final AgentContextHolder agentContextHolder) {
		this.agentContextHolder = agentContextHolder;
	}

	/**
	 * Retrieves detailed information about the MetricsHub Agent.
	 * This includes:
	 * <ul>
	 *   <li>Agent metadata (name, version, build info, etc.)</li>
	 *   <li>Host information (hostname, OS type, architecture)</li>
	 *   <li>Runtime details (Java version, PID, working directory)</li>
	 *   <li>Memory usage (total, used, free, max, percentage)</li>
	 *   <li>CPU usage percentage</li>
	 * </ul>
	 *
	 * @return an {@link AgentInfoResponse} containing all agent metadata and runtime information
	 */
	@Tool(
		description = """
		Get detailed information about the MetricsHub Agent.
		Returns agent metadata including name, version, build number, build date,
		Community Connector Library version, host information (hostname, OS type,
		architecture), runtime details (Java version, PID, working directory),
		and current resource usage (memory total/used/free/max, memory usage percentage,
		CPU usage percentage).
		""",
		name = "GetAgentInfo"
	)
	public AgentInfoResponse getAgentInfo() {
		final AgentInfo agentInfo = agentContextHolder.getAgentContext().getAgentInfo();
		final Map<String, String> attributes = agentInfo.getAttributes();

		// Get memory information
		final MemoryMXBean memoryMXBean = ManagementFactory.getMemoryMXBean();
		final MemoryUsage heapMemoryUsage = memoryMXBean.getHeapMemoryUsage();

		final long usedMemory = heapMemoryUsage.getUsed();
		final long maxMemory = heapMemoryUsage.getMax();
		final long committedMemory = heapMemoryUsage.getCommitted();
		final long freeMemory = committedMemory - usedMemory;

		// Calculate memory usage percentage
		final double memoryUsagePercent = maxMemory > 0 ? ((double) usedMemory / maxMemory) * PERCENTAGE_MULTIPLIER : 0.0;

		// Get CPU usage
		final double cpuUsagePercent = getCpuUsagePercent();

		return AgentInfoResponse
			.builder()
			// Agent metadata from metricshub.agent.info metric attributes
			.serviceName(attributes.get(AGENT_RESOURCE_SERVICE_NAME_ATTRIBUTE_KEY))
			.hostName(attributes.get(AGENT_RESOURCE_HOST_NAME_ATTRIBUTE_KEY))
			.agentHostName(attributes.get(AGENT_RESOURCE_AGENT_HOST_NAME_ATTRIBUTE_KEY))
			.hostType(attributes.get(AGENT_RESOURCE_HOST_TYPE_ATTRIBUTE_KEY))
			.osType(attributes.get(AGENT_RESOURCE_OS_TYPE_ATTRIBUTE_KEY))
			.name(attributes.get(AGENT_INFO_NAME_ATTRIBUTE_KEY))
			.version(attributes.get(AGENT_INFO_VERSION_ATTRIBUTE_KEY))
			.buildNumber(attributes.get(AGENT_INFO_BUILD_NUMBER_ATTRIBUTE_KEY))
			.buildDate(attributes.get(AGENT_INFO_BUILD_DATE_NUMBER_ATTRIBUTE_KEY))
			.ccVersion(attributes.get(AGENT_INFO_CC_VERSION_NUMBER_ATTRIBUTE_KEY))
			// Additional runtime information
			.hostArch(System.getProperty("os.arch"))
			.osName(System.getProperty("os.name"))
			.javaVersion(System.getProperty("java.version"))
			.javaHome(System.getProperty("java.home"))
			.processPid(agentContextHolder.getAgentContext().getPid())
			.userDir(System.getProperty("user.dir"))
			// Memory information
			.memoryTotal(committedMemory)
			.memoryUsed(usedMemory)
			.memoryFree(freeMemory)
			.memoryMax(maxMemory)
			.memoryUsagePercent(Math.round(memoryUsagePercent * PERCENTAGE_MULTIPLIER) / PERCENTAGE_MULTIPLIER)
			// CPU information
			.cpuUsagePercent(Math.round(cpuUsagePercent * PERCENTAGE_MULTIPLIER) / PERCENTAGE_MULTIPLIER)
			.build();
	}

	/**
	 * Gets the CPU usage percentage for the JVM process.
	 *
	 * @return the CPU usage as a percentage (0-100), or -1.0 if not available
	 */
	private double getCpuUsagePercent() {
		try {
			final java.lang.management.OperatingSystemMXBean osBean = ManagementFactory.getOperatingSystemMXBean();
			if (osBean instanceof OperatingSystemMXBean sunOsBean) {
				final double processCpuLoad = sunOsBean.getProcessCpuLoad();
				if (processCpuLoad >= 0) {
					return processCpuLoad * PERCENTAGE_MULTIPLIER;
				}
			}
		} catch (Exception e) {
			// CPU usage not available on this platform
		}
		return -1.0;
	}
}
