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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.LongNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.metricshub.engine.common.exception.InvalidConfigurationException;
import org.metricshub.engine.configuration.HostConfiguration;
import org.metricshub.engine.configuration.IConfiguration;
import org.metricshub.engine.extension.ExtensionManager;
import org.metricshub.engine.extension.IProtocolExtension;
import org.metricshub.engine.telemetry.HostProperties;
import org.metricshub.engine.telemetry.TelemetryManager;
import org.metricshub.web.AgentContextHolder;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Service responsible for checking the reachability and availability of a host
 * using a specified protocol (e.g., SSH, WinRM, WMI, etc.).
 * <p>
 * This service dynamically resolves the appropriate {@link IProtocolExtension}
 * based on the provided protocol type, builds the corresponding configuration,
 * and delegates the protocol check operation.
 * </p>
 */
@Service
public class ProtocolCheckService {

	/**
	 * Holds contextual information about the current agent instance.
	 */
	private AgentContextHolder agentContextHolder;

	/**
	 * Constructor for PingToolService.
	 *
	 * @param agentContextHolder the {@link AgentContextHolder} instance to access the agent context
	 */
	@Autowired
	public ProtocolCheckService(AgentContextHolder agentContextHolder) {
		this.agentContextHolder = agentContextHolder;
	}

	/**
	 * Checks whether the specified host is reachable using the given protocol and credentials.
	 *
	 * @param protocol the protocol type to use for the check (e.g., "http", "ssh")
	 * @param hostname the target host to check
	 * @return a {@link ProtocolCheckResponse} indicating whether the host is reachable and how long the check took
	 */
	@Tool(
		name = "CheckHostProtocol",
		description = "Checks if a host is reachable using a specified protocol such as SSH or WMI."
	)
	public Map<String, ProtocolCheckResponse> checkProtocol(
		@ToolParam(
			description = "The protocol(s) to use for checking host availability (e.g., http, ipmi, jmx, snmp, ssh, wbem, wmi, winrm). " +
			"You may specify multiple protocols using a comma-separated list.",
			required = false
		) final String protocol,
		@ToolParam(description = "The hostname to check") final String hostname
	) {
		final ExtensionManager extensionManager = agentContextHolder.getAgentContext().getExtensionManager();

		final List<String> protocolList = (protocol == null || protocol.isBlank())
			? extensionManager.getProtocolExtensions().stream().map(IProtocolExtension::getIdentifier).toList()
			: Arrays.stream(protocol.split(",")).map(String::trim).filter(p -> !p.isEmpty()).toList();

		return protocolList
			.stream()
			.collect(
				Collectors.toMap(
					p -> p,
					p ->
						extensionManager
							.findExtensionByType(p)
							.map(ext -> checkProtocolWithExtensionSafe(hostname, p, ext))
							.orElse(
								ProtocolCheckResponse
									.builder()
									.hostname(hostname)
									.errorMessage("No extension is available for the protocol " + p)
									.build()
							)
				)
			);
	}

	/**
	 * Executes a protocol check using all available configurations for the given host.
	 * Returns as soon as one configuration reports the host as reachable.
	 *
	 * @param hostname  the host to check
	 * @param protocol  the protocol type used (e.g., "ssh", "snmp", etc.)
	 * @param extension the resolved protocol extension responsible for performing the check
	 * @return a {@link ProtocolCheckResponse} indicating whether the host is reachable and how long it took
	 */
	private ProtocolCheckResponse checkProtocolWithExtensionSafe(
		final String hostname,
		final String protocol,
		final IProtocolExtension extension
	) {
		// Fetch the available configurations for the host
		final Set<IConfiguration> configurations = MCPConfigHelper.resolveAllHostConfigurationsFromContext(
			hostname,
			agentContextHolder
		);

		// Iterate through each configuration until one succeeds
		for (final IConfiguration configuration : configurations) {
			IConfiguration validConfiguration;

			if (extension.isValidConfiguration(configuration)) {
				// Use the original configuration if it's valid for the extension
				validConfiguration = configuration;
			} else {
				// Attempt to build a compatible configuration using shared fields
				validConfiguration = convertConfigurationForProtocol(configuration, protocol, extension);
				// If building failed, skip or continue depending on the context
				if (validConfiguration == null) {
					continue;
				}
			}

			validConfiguration.setHostname(hostname);

			// Build the telemetry manager based on this configuration
			final TelemetryManager telemetryManager = TelemetryManager
				.builder()
				.hostConfiguration(
					HostConfiguration
						.builder()
						.configurations(Map.of(validConfiguration.getClass(), validConfiguration))
						.hostname(hostname)
						.build()
				)
				.hostProperties(HostProperties.builder().mustCheckSshStatus(protocol.equalsIgnoreCase("ssh")).build())
				.build();

			// Record the start time to compute response time
			final long startTime = System.currentTimeMillis();

			// Attempt the protocol check
			final Optional<Boolean> response = extension.checkProtocol(telemetryManager);

			// If a response is present and host is reachable, return a positive result
			if (response.isPresent()) {
				final boolean isUp = response.get();
				if (isUp) {
					final double responseTime = (System.currentTimeMillis() - startTime) / 1000.0;
					return ProtocolCheckResponse
						.builder()
						.hostname(hostname)
						.isReachable(true)
						.responseTime(responseTime)
						.build();
				}
			}
		}

		// No configuration succeeded: host is considered unreachable
		return ProtocolCheckResponse.builder().hostname(hostname).isReachable(false).build();
	}

	/**
	 * Builds a new configuration for the specified protocol by extracting shared values
	 * (such as username, password, and timeout) from an existing configuration of a different type.
	 *
	 * This is useful when switching protocols (e.g., HTTP → SSH) but wanting to reuse common credentials.
	 *
	 * @param configuration the source configuration to extract credentials and timeout from
	 * @param protocol      the target protocol for the new configuration
	 * @param extension     the extension responsible for creating the new configuration
	 * @return a new {@link IConfiguration} instance populated with extracted values
	 */
	public IConfiguration convertConfigurationForProtocol(
		final IConfiguration configuration,
		final String protocol,
		final IProtocolExtension extension
	) {
		// Convert the source configuration to a JsonNode tree
		final ObjectMapper mapper = new ObjectMapper();
		final JsonNode configurationNode = mapper.valueToTree(configuration);

		// Create a new configuration node with only the required shared fields
		final ObjectNode newConfigurationNode = JsonNodeFactory.instance.objectNode();
		final JsonNode timeout = configurationNode.get("timeout");
		newConfigurationNode.set("username", configurationNode.get("username"));
		newConfigurationNode.set("password", configurationNode.get("password"));
		newConfigurationNode.set("timeout", timeout != null && !timeout.isNull() ? timeout : new LongNode(30L));

		// Build the new configuration using the extracted credentials
		try {
			return extension.buildConfiguration(protocol, newConfigurationNode, null);
		} catch (InvalidConfigurationException e) {
			return null;
		}
	}
}
