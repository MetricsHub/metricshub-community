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

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.metricshub.engine.configuration.HostConfiguration;
import org.metricshub.engine.configuration.IConfiguration;
import org.metricshub.engine.extension.IProtocolExtension;
import org.metricshub.engine.telemetry.HostProperties;
import org.metricshub.engine.telemetry.TelemetryManager;
import org.metricshub.web.AgentContextHolder;
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
	 * Default timeout to be used for protocol check
	 */
	private static final long DEFAULT_PROTOCOL_CHECK_TIMEOUT = 10L;

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
	 * Executes a protocol check using all available configurations for the given host.
	 * Returns as soon as one configuration reports the host as reachable.
	 *
	 * @param hostname  the host to check
	 * @param protocol  the protocol type used (e.g., "ssh", "snmp", etc.)
	 * @param timeout  the timeout for the protocol check operation in seconds
	 * @param extension the resolved protocol extension responsible for performing the check
	 * @return a {@link ProtocolCheckResponse} indicating whether the host is reachable and how long it took
	 */
	public ProtocolCheckResponse checkProtocolWithExtensionSafe(
		final String hostname,
		final String protocol,
		final Long timeout,
		final IProtocolExtension extension
	) {
		// Fetch the available configurations for the host
		final Set<IConfiguration> configurations = MCPConfigHelper.resolveAllHostConfigurationsFromContext(
			hostname,
			agentContextHolder
		);

		// Iterate through each configuration until one succeeds
		for (IConfiguration configuration : configurations) {
			IConfiguration validConfiguration;

			if (extension.isValidConfiguration(configuration)) {
				// Use the original configuration if it's valid for the extension
				validConfiguration = configuration.copy();
			} else {
				// Attempt to build a compatible configuration using shared fields
				validConfiguration = MCPConfigHelper.convertConfigurationForProtocol(configuration, protocol, extension);
				// If building failed, skip or continue depending on the context
				if (validConfiguration == null) {
					continue;
				}
			}

			validConfiguration.setHostname(hostname);
			validConfiguration.setTimeout(timeout != null ? timeout : DEFAULT_PROTOCOL_CHECK_TIMEOUT);

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
					final double responseTime = (System.currentTimeMillis() - startTime);
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
}
