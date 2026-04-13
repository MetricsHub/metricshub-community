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

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response object for the GetAgentInfo MCP tool.
 * Contains MetricsHub Agent metadata and runtime information.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentInfoResponse {

	// Agent Metadata (from metricshub.agent.info metric attributes)

	/**
	 * The name of the agent service.
	 */
	@JsonProperty("service.name")
	private String serviceName;

	/**
	 * The hostname where the agent is running.
	 */
	@JsonProperty("host.name")
	private String hostName;

	/**
	 * The agent's host name attribute.
	 */
	@JsonProperty("agent.host.name")
	private String agentHostName;

	/**
	 * The type of host (e.g., "compute").
	 */
	@JsonProperty("host.type")
	private String hostType;

	/**
	 * The operating system type (OpenTelemetry format).
	 */
	@JsonProperty("os.type")
	private String osType;

	/**
	 * The agent name.
	 */
	private String name;

	/**
	 * The agent version.
	 */
	private String version;

	/**
	 * The build number of the agent.
	 */
	@JsonProperty("build.number")
	private String buildNumber;

	/**
	 * The build date of the agent.
	 */
	@JsonProperty("build.date")
	private String buildDate;

	/**
	 * Community Connector Library version.
	 */
	@JsonProperty("cc.version")
	private String ccVersion;

	// Additional Runtime Information

	/**
	 * The host architecture (e.g., "amd64").
	 */
	@JsonProperty("host.arch")
	private String hostArch;

	/**
	 * The operating system name.
	 */
	@JsonProperty("os.name")
	private String osName;

	/**
	 * The Java version running the agent.
	 */
	@JsonProperty("java.version")
	private String javaVersion;

	/**
	 * The Java runtime environment directory.
	 */
	@JsonProperty("java.home")
	private String javaHome;

	/**
	 * The process ID of the agent.
	 */
	@JsonProperty("process.pid")
	private String processPid;

	/**
	 * The user working directory.
	 */
	@JsonProperty("user.dir")
	private String userDir;

	// Memory and CPU Usage

	/**
	 * Total JVM heap memory in bytes.
	 */
	@JsonProperty("memory.total")
	private Long memoryTotal;

	/**
	 * Used JVM heap memory in bytes.
	 */
	@JsonProperty("memory.used")
	private Long memoryUsed;

	/**
	 * Free JVM heap memory in bytes.
	 */
	@JsonProperty("memory.free")
	private Long memoryFree;

	/**
	 * Maximum JVM heap memory in bytes.
	 */
	@JsonProperty("memory.max")
	private Long memoryMax;

	/**
	 * Memory usage as a percentage (0-100).
	 */
	@JsonProperty("memory.usage.percent")
	private Double memoryUsagePercent;

	/**
	 * CPU usage as a percentage (0-100).
	 * This represents the process CPU load when available, -1 if not available.
	 */
	@JsonProperty("cpu.usage.percent")
	private Double cpuUsagePercent;

	// Application Status Information

	/**
	 * The current status of the agent (UP or DOWN).
	 */
	private String status;

	/**
	 * The status of the OpenTelemetry Collector (running, disabled, errored, not-installed).
	 */
	@JsonProperty("otel.collector.status")
	private String otelCollectorStatus;

	/**
	 * The number of resources currently being observed by the agent.
	 */
	@JsonProperty("observed.resources.count")
	private Long numberOfObservedResources;

	/**
	 * The number of resources configured in the agent.
	 */
	@JsonProperty("configured.resources.count")
	private Long numberOfConfiguredResources;

	/**
	 * The number of monitors currently active.
	 */
	@JsonProperty("monitors.count")
	private Long numberOfMonitors;

	/**
	 * The number of scheduled jobs.
	 */
	@JsonProperty("jobs.count")
	private Long numberOfJobs;

	/**
	 * The number of days remaining on the license (null for Community Edition).
	 */
	@JsonProperty("license.days.remaining")
	private Long licenseDaysRemaining;

	/**
	 * The type of license (Community or Enterprise).
	 */
	@JsonProperty("license.type")
	private String licenseType;
}
