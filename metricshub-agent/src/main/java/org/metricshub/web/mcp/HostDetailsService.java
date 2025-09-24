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

import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.metricshub.engine.telemetry.Monitor;
import org.metricshub.engine.telemetry.TelemetryManager;
import org.metricshub.engine.telemetry.metric.NumberMetric;
import org.metricshub.web.AgentContextHolder;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Service that exposes host-level details for use in the MCP layer.
 * <p>
 * This service provides access to:
 * <ul>
 *   <li>The set of working connectors defined for a given host,</li>
 *   <li>The configured protocols associated with "host up" metrics,</li>
 *   <li>The collectors available for each protocol.</li>
 * </ul>
 * It relies on the {@link AgentContextHolder} to look up the current agent’s {@link TelemetryManager}
 * and builds a {@link HostDetails} object that can be consumed by MCP tools.
 */
@Service
public class HostDetailsService {

	/**
	 * Holds contextual information about the current agent instance.
	 */
	private final AgentContextHolder agentContextHolder;

	/**
	 * Contains all the collectors by their protocol.
	 */
	private static final Map<String, Set<String>> COLLECTOR_MAP = Map.ofEntries(
		Map.entry("http", Set.of("HTTP Request")),
		Map.entry("ipmi", Set.of("IPMI Query")),
		Map.entry("snmp", Set.of("SNMP Get", "SNMP GetNext", "SNMP Walk", "SNMP Table")),
		Map.entry("wbem", Set.of("WBEM Query")),
		Map.entry("wmi", Set.of("WMI Query")),
		Map.entry("winrm", Set.of("WINRM Query")),
		Map.entry("jdbc", Set.of("SQL Query")),
		Map.entry("jmx", Set.of("JMX Request")),
		Map.entry("ping", Set.of("ICMP Ping")),
		Map.entry("snmpv3", Set.of("SNMPv3 Get", "SNMPv3 GetNext", "SNMPv3 Walk", "SNMPv3 Table")),
		Map.entry("ssh", Set.of("CommandLine via SSH"))
	);

	/**
	 * Constructor for HostDetailsService.
	 *
	 * @param agentContextHolder the {@link AgentContextHolder} instance to access the agent context
	 */
	@Autowired
	public HostDetailsService(AgentContextHolder agentContextHolder) {
		this.agentContextHolder = agentContextHolder;
	}

	/**
	 * Retrieves host details for the given hostname.
	 * Looks up the {@link org.metricshub.engine.telemetry.TelemetryManager} and builds a {@link HostDetails}
	 * containing the configured protocols, working connectors, and collectors.
	 * If the hostname is not found, the result contains an error message instead.
	 *
	 * @param hostname the hostname to look up
	 * @return a {@link HostDetails} with protocol, connector, and collector information,
	 *         or an error message if the host cannot be found
	 */
	@Tool(
		name = "GetHostDetails",
		description = """
		Retrieves telemetry information about a host from the MetricsHub agent configuration.
		Given a hostname, it returns a HostDetails object that includes the configured availability protocols, the working connectors, and the collectors available for those protocols.
		If the hostname cannot be found, the result contains an error message instead.
		"""
	)
	public HostDetails getHostDetails(
		@ToolParam(description = "The hostname to look up in the agent configuration") final String hostname
	) {
		return getHostDetailsIfPresent(hostname);
	}

	/**
	 * Builds a {@link HostDetails} object for the given hostname if it is present in the agent configuration.
	 * <p>
	 * This method looks up the {@link TelemetryManager} associated with the host, extracts:
	 * <ul>
	 *   <li>the working connectors declared for this host,</li>
	 *   <li>the configured "host up" protocols,</li>
	 *   <li>and the collectors associated with those protocols.</li>
	 * </ul>
	 * If no telemetry manager can be found for the hostname, a {@link HostDetails} is returned
	 * containing an error message.
	 *
	 * @param hostname the hostname to look up in the current configuration
	 * @return a populated {@link HostDetails} instance, or one containing an error message if the host is not found
	 */
	private HostDetails getHostDetailsIfPresent(final String hostname) {
		// Try to find a telemetry manager for the given host.
		final Optional<TelemetryManager> maybeTelemetryManager = MCPConfigHelper.findTelemetryManagerByHostname(
			hostname,
			agentContextHolder
		);

		// If a telemetry manager isn't found, return an error message.
		if (maybeTelemetryManager.isEmpty()) {
			return HostDetails.builder().errorMessage("Hostname not found in the current configuration.").build();
		}

		// Retrieve the telemetry manager of the host.
		final TelemetryManager telemetryManager = maybeTelemetryManager.get();

		// Collect connector IDs from the "connector" monitor group (empty if none present).
		final Set<String> connectors = Optional
			.ofNullable(telemetryManager.getMonitors())
			.map(m -> m.get("connector"))
			.orElse(Map.of())
			.values()
			.stream()
			.map(monitor -> monitor.getAttributes().get("connector_id"))
			.collect(Collectors.toSet());

		// Collect metrics that represent "host up" status for this host.
		final Set<NumberMetric> configuredProtocols = Optional
			.ofNullable(telemetryManager.getEndpointHostMonitor())
			.map(Monitor::getMetrics)
			.orElse(Collections.emptyMap())
			.entrySet()
			.stream()
			.filter(metric -> metric.getKey().startsWith("metricshub.host.up"))
			.map(Map.Entry::getValue)
			.filter(NumberMetric.class::isInstance)
			.map(NumberMetric.class::cast)
			.collect(Collectors.toSet());

		// Extract protocol identifiers from the collected metrics.
		final Set<String> protocols = configuredProtocols
			.stream()
			.map(protocol -> protocol.getAttributes().get("protocol"))
			.collect(Collectors.toSet());

		// Resolve the collectors associated with each protocol.
		final Set<String> collectors = protocols
			.stream()
			.map(this::getCollectors)
			.flatMap(Set::stream)
			.collect(Collectors.toSet());

		// Build and return the final HostDetails object.
		return HostDetails
			.builder()
			.configuredProtocols(protocols)
			.workingConnectors(connectors)
			.collectors(collectors)
			.build();
	}

	/**
	 * Gets the collectors associated with a protocol.
	 *
	 * @param protocol the protocol name
	 * @return the collectors for the protocol, or an empty set if none exist
	 */
	public Set<String> getCollectors(final String protocol) {
		return COLLECTOR_MAP.getOrDefault(protocol, Set.of());
	}
}
