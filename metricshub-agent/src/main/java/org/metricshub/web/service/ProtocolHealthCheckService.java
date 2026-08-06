package org.metricshub.web.service;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * MetricsHub Agent
 * ჻჻჻჻჻჻
 * Copyright 2023 - 2026 MetricsHub
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
import org.metricshub.engine.common.helpers.NetworkHelper;
import org.metricshub.engine.common.helpers.NumberHelper;
import org.metricshub.engine.configuration.HostConfiguration;
import org.metricshub.engine.configuration.IConfiguration;
import org.metricshub.engine.extension.IProtocolExtension;
import org.metricshub.engine.telemetry.HostProperties;
import org.metricshub.engine.telemetry.TelemetryManager;
import org.metricshub.web.AgentContextHolder;
import org.metricshub.web.deserialization.ProtocolConfigurationMaps;
import org.metricshub.web.mcp.MCPConfigHelper;
import org.metricshub.web.mcp.ProtocolCheckResponse;
import org.springframework.stereotype.Service;

/**
 * Shared protocol health-check execution used by the MCP tools and the guided configuration UI.
 */
@Service
public class ProtocolHealthCheckService {

	/**
	 * Default timeout to be used for protocol check.
	 */
	public static final long DEFAULT_PROTOCOL_CHECK_TIMEOUT = 10L;

	private final AgentContextHolder agentContextHolder;

	/**
	 * Creates a new {@link ProtocolHealthCheckService}.
	 *
	 * @param agentContextHolder holder for the active agent context
	 */
	public ProtocolHealthCheckService(final AgentContextHolder agentContextHolder) {
		this.agentContextHolder = agentContextHolder;
	}

	/**
	 * Checks protocol reachability using configurations loaded from the agent context.
	 *
	 * @param hostname  target host
	 * @param protocol  protocol identifier (e.g. ssh)
	 * @param timeout   optional timeout override in seconds
	 * @param extension resolved protocol extension
	 * @return check result
	 */
	public ProtocolCheckResponse checkFromAgentContext(
		final String hostname,
		final String protocol,
		final Long timeout,
		final IProtocolExtension extension
	) {
		try {
			return checkProtocolWithExtensionSafe(hostname, protocol, timeout, extension);
		} catch (Exception e) {
			return ProtocolCheckResponse.builder()
				.hostname(hostname)
				.errorMessage("Error detected during protocol check: " + e.getMessage())
				.build();
		}
	}

	/**
	 * Checks protocol reachability using an inline configuration supplied by the UI configuration form.
	 * <p>
	 * Hostname precedence follows the engine contract: the protocol-level hostname is authoritative and
	 * the resource hostname only back-fills it when absent, exactly like
	 * {@code ConfigHelper.validateAndNormalizeProtocols} at configuration load time.
	 *
	 * @param hostname       resource hostname
	 * @param protocol       protocol identifier (e.g. ssh)
	 * @param protocolConfig protocol block as written in YAML
	 * @return check result
	 */
	public ProtocolCheckResponse checkWithInlineConfiguration(
		final String hostname,
		final String protocol,
		final Map<String, IConfiguration> protocolConfig
	) {
		final IConfiguration configuration = ProtocolConfigurationMaps.resolveForProtocol(protocol, protocolConfig);

		if (configuration == null) {
			if (isBlank(hostname)) {
				return ProtocolCheckResponse.builder().errorMessage("Hostname must be provided.").build();
			}
			return ProtocolCheckResponse.builder()
				.hostname(hostname.trim())
				.errorMessage("Invalid protocol configuration")
				.build();
		}

		// Mirror ConfigHelper.validateAndNormalizeProtocols: the resource hostname only
		// back-fills the protocol configuration when it doesn't declare its own hostname.
		final IConfiguration activeConfiguration = configuration.copy();
		if (isBlank(activeConfiguration.getHostname())) {
			if (isBlank(hostname)) {
				return ProtocolCheckResponse.builder().errorMessage("Hostname must be provided.").build();
			}
			activeConfiguration.setHostname(hostname.trim());
		}

		final String targetHostname = activeConfiguration.getHostname();

		return agentContextHolder
			.getAgentContext()
			.getExtensionManager()
			.findExtensionByType(protocol)
			.map(extension -> checkWithInlineConfiguration(targetHostname, protocol, activeConfiguration, extension))
			.orElseGet(() ->
				ProtocolCheckResponse.builder()
					.hostname(targetHostname)
					.errorMessage(protocol + " extension is not available")
					.build()
			);
	}

	private static boolean isBlank(final String value) {
		return value == null || value.isBlank();
	}

	private ProtocolCheckResponse checkWithInlineConfiguration(
		final String hostname,
		final String protocol,
		final IConfiguration configuration,
		final IProtocolExtension extension
	) {
		try {
			if (!extension.isValidConfiguration(configuration)) {
				return ProtocolCheckResponse.builder()
					.hostname(hostname)
					.errorMessage("Invalid protocol configuration")
					.build();
			}
			return executeProtocolCheck(
				hostname,
				protocol,
				resolveTimeoutFromConfiguration(configuration),
				configuration,
				extension
			);
		} catch (Exception e) {
			return ProtocolCheckResponse.builder()
				.hostname(hostname)
				.errorMessage("Error detected during protocol check: " + e.getMessage())
				.build();
		}
	}

	private ProtocolCheckResponse checkProtocolWithExtensionSafe(
		final String hostname,
		final String protocol,
		final Long timeout,
		final IProtocolExtension extension
	) {
		final Set<IConfiguration> configurations = MCPConfigHelper.resolveAllHostConfigurationCopiesFromContext(
			hostname,
			agentContextHolder
		);

		for (final IConfiguration configuration : configurations) {
			IConfiguration validConfiguration = configuration;

			if (!extension.isValidConfiguration(configuration)) {
				validConfiguration = MCPConfigHelper.convertConfigurationForProtocol(configuration, protocol, extension);
				if (validConfiguration == null) {
					continue;
				}
			}

			validConfiguration.setHostname(hostname);
			final ProtocolCheckResponse result = executeProtocolCheck(
				hostname,
				protocol,
				timeout,
				validConfiguration,
				extension
			);
			if (result.isReachable()) {
				return result;
			}
		}

		return ProtocolCheckResponse.builder().hostname(hostname).isReachable(false).build();
	}

	private ProtocolCheckResponse executeProtocolCheck(
		final String hostname,
		final String protocol,
		final Long timeoutOverride,
		final IConfiguration configuration,
		final IProtocolExtension extension
	) {
		final long resolvedTimeout = NumberHelper.getPositiveOrDefault(
			timeoutOverride,
			DEFAULT_PROTOCOL_CHECK_TIMEOUT
		).longValue();
		configuration.setTimeout(resolvedTimeout);

		final var telemetryManager = TelemetryManager.builder()
			.hostConfiguration(
				HostConfiguration.builder()
					.configurations(Map.of(configuration.getClass(), configuration))
					.hostname(hostname)
					.build()
			)
			.hostProperties(buildHostPropertiesForCheck(hostname, protocol))
			.build();

		final long startTime = System.currentTimeMillis();
		final Optional<Boolean> response = extension.checkProtocol(telemetryManager);

		if (response.isPresent() && response.get()) {
			return ProtocolCheckResponse.builder()
				.hostname(hostname)
				.isReachable(true)
				.responseTime(System.currentTimeMillis() - startTime)
				.build();
		}

		return ProtocolCheckResponse.builder().hostname(hostname).isReachable(false).build();
	}

	private static Long resolveTimeoutFromConfiguration(final IConfiguration configuration) {
		if (configuration == null) {
			return null;
		}
		final String timeout = configuration.getProperty("timeout");
		if (timeout == null || timeout.isBlank()) {
			return null;
		}
		try {
			final long value = Long.parseLong(timeout.trim());
			return value > 0 ? value : null;
		} catch (NumberFormatException ignored) {
			return null;
		}
	}

	private static HostProperties buildHostPropertiesForCheck(final String hostname, final String protocol) {
		final boolean isLocal = NetworkHelper.isLocalhost(hostname);
		final HostProperties.HostPropertiesBuilder builder = HostProperties.builder().isLocalhost(isLocal);

		if ("ssh".equalsIgnoreCase(protocol)) {
			builder.mustCheckSshStatus(true);
			if (isLocal) {
				builder.osCommandExecutesLocally(true);
			} else {
				builder.osCommandExecutesRemotely(true);
			}
		}

		return builder.build();
	}
}
