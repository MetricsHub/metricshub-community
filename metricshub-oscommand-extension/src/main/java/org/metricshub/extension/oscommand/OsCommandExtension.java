package org.metricshub.extension.oscommand;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * MetricsHub OsCommand Extension
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

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.UnaryOperator;
import lombok.extern.slf4j.Slf4j;
import org.metricshub.engine.common.exception.InvalidConfigurationException;
import org.metricshub.engine.configuration.IConfiguration;
import org.metricshub.engine.connector.model.common.DeviceKind;
import org.metricshub.engine.connector.model.identity.criterion.CommandLineCriterion;
import org.metricshub.engine.connector.model.identity.criterion.Criterion;
import org.metricshub.engine.connector.model.monitor.task.source.CommandLineSource;
import org.metricshub.engine.connector.model.monitor.task.source.FileSource;
import org.metricshub.engine.connector.model.monitor.task.source.Source;
import org.metricshub.engine.extension.IProtocolExtension;
import org.metricshub.engine.strategy.detection.CriterionTestResult;
import org.metricshub.engine.strategy.source.SourceTable;
import org.metricshub.engine.telemetry.TelemetryManager;
import org.metricshub.extension.oscommand.file.FileSourceProcessor;

/**
 * Provides an extension to handle SSH and OS command-based protocols for device monitoring. This extension
 * supports configuration validation, source and criterion processing, and protocol health checks.
 */
@Slf4j
public class OsCommandExtension implements IProtocolExtension {

	/**
	 * Supported configuration types
	 */
	private static final Set<String> SUPPORTED_CONFIGURATION_TYPES = Set.of("ssh", "oscommand");

	/**
	 * Protocol up status value '1.0'
	 */
	public static final Double UP = 1.0;

	/**
	 * Protocol down status value '0.0'
	 */
	public static final Double DOWN = 0.0;

	/**
	 * SSH test command to execute
	 */
	public static final String SSH_TEST_COMMAND = "echo test";

	@Override
	public boolean isValidConfiguration(IConfiguration configuration) {
		return configuration instanceof SshConfiguration || configuration instanceof OsCommandConfiguration;
	}

	@Override
	public Set<Class<? extends Source>> getSupportedSources() {
		return Set.of(CommandLineSource.class, FileSource.class);
	}

	@Override
	public Map<Class<? extends IConfiguration>, Set<Class<? extends Source>>> getConfigurationToSourceMapping() {
		return Map.ofEntries(
			Map.entry(SshConfiguration.class, Set.of(CommandLineSource.class)),
			Map.entry(OsCommandConfiguration.class, Set.of(CommandLineSource.class))
		);
	}

	@Override
	public Set<Class<? extends Criterion>> getSupportedCriteria() {
		return Set.of(CommandLineCriterion.class);
	}

	@Override
	public Optional<Boolean> checkProtocol(TelemetryManager telemetryManager) {
		// Retrieve SSH Configuration
		final SshConfiguration sshConfiguration = (SshConfiguration) telemetryManager
			.getHostConfiguration()
			.getConfigurations()
			.get(SshConfiguration.class);

		// Stop the SSH health check if there is not any SSH configuration
		if (sshConfiguration == null || !telemetryManager.getHostProperties().isMustCheckSshStatus()) {
			return Optional.empty();
		}

		// Retrieve the hostname from the SshConfiguration, otherwise from the telemetryManager
		String hostname = telemetryManager.getHostname(List.of(SshConfiguration.class));

		log.info("Hostname {} - Performing {} protocol health check.", hostname, getIdentifier());
		log.info("Hostname {} - Checking SSH protocol status. Sending an SSH 'echo test' command.", hostname);

		// Create and set the SSH result to null
		Double sshResult = UP;

		// Execute Local test
		if (telemetryManager.getHostProperties().isOsCommandExecutesLocally()) {
			sshResult = localSshTest(hostname);
		}

		if (telemetryManager.getHostProperties().isOsCommandExecutesRemotely()) {
			sshResult =
				remoteSshTest(hostname, sshResult, sshConfiguration, telemetryManager.getHostConfiguration().getHostType());
		}

		return Optional.of(UP.equals(sshResult));
	}

	@Override
	public SourceTable processSource(Source source, String connectorId, TelemetryManager telemetryManager) {
		if (source instanceof CommandLineSource commandLineSource) {
			return new CommandLineSourceProcessor().process(commandLineSource, connectorId, telemetryManager);
		} else if (source instanceof FileSource logSource) {
			return new FileSourceProcessor().process(logSource, connectorId, telemetryManager);
		}
		throw new IllegalArgumentException(
			String.format(
				"Hostname %s - Cannot process source %s.",
				telemetryManager.getHostname(),
				source != null ? source.getClass().getSimpleName() : "<null>"
			)
		);
	}

	@Override
	public CriterionTestResult processCriterion(
		Criterion criterion,
		String connectorId,
		TelemetryManager telemetryManager
	) {
		if (criterion instanceof CommandLineCriterion commandLineCriterion) {
			return new CommandLineCriterionProcessor(connectorId).process(commandLineCriterion, telemetryManager);
		}
		throw new IllegalArgumentException(
			String.format(
				"Hostname %s - Cannot process criterion %s.",
				telemetryManager.getHostname(),
				criterion != null ? criterion.getClass().getSimpleName() : "<null>"
			)
		);
	}

	@Override
	public boolean isSupportedConfigurationType(String configurationType) {
		return SUPPORTED_CONFIGURATION_TYPES.contains(configurationType.toLowerCase());
	}

	@Override
	public IConfiguration buildConfiguration(String configurationType, JsonNode jsonNode, UnaryOperator<char[]> decrypt)
		throws InvalidConfigurationException {
		if (configurationType.equalsIgnoreCase("ssh")) {
			try {
				final SshConfiguration sshConfiguration = newObjectMapper().treeToValue(jsonNode, SshConfiguration.class);

				if (decrypt != null) {
					final char[] password = sshConfiguration.getPassword();
					if (password != null) {
						// Decrypt the password
						sshConfiguration.setPassword(decrypt.apply(password));
					}
				}

				return sshConfiguration;
			} catch (Exception e) {
				final String errorMessage = String.format("Error while reading SSH Configuration. Error: %s", e.getMessage());
				log.error(errorMessage);
				log.debug("Error while reading SSH Configuration. Stack trace:", e);
				throw new InvalidConfigurationException(errorMessage, e);
			}
		} else if (configurationType.equalsIgnoreCase("oscommand")) {
			try {
				return newObjectMapper().treeToValue(jsonNode, OsCommandConfiguration.class);
			} catch (Exception e) {
				final String errorMessage = String.format(
					"Error while reading OsCommand Configuration. Error: %s",
					e.getMessage()
				);
				log.error(errorMessage);
				log.debug("Error while reading OsCommand Configuration. Stack trace:", e);
				throw new InvalidConfigurationException(errorMessage, e);
			}
		}
		final String errorMessage = String.format(
			"Unhandled %s configuration in the OsCommandExtension.",
			configurationType
		);
		log.error(errorMessage);
		log.debug(errorMessage);
		throw new InvalidConfigurationException(errorMessage);
	}

	/**
	 * Performs a local Os Command test to determine whether the SSH protocol is UP.
	 *
	 * @param hostname  The hostname on which we perform health check
	 * @return The SSH health check result after performing the tests
	 */
	private Double localSshTest(String hostname) {
		try {
			if (OsCommandService.runLocalCommand(SSH_TEST_COMMAND, OsCommandConfiguration.DEFAULT_TIMEOUT, null) == null) {
				log.debug(
					"Hostname {} - Checking SSH protocol status. Local OS command has not returned any results.",
					hostname
				);
				return DOWN;
			}
		} catch (Exception e) {
			log.debug(
				"Hostname {} - Checking SSH protocol status. SSH exception when performing a local OS command test: ",
				hostname,
				e
			);
			return DOWN;
		}

		return UP;
	}

	/**
	 * Performs a remote SSH test to determine whether the SSH protocol is UP in the given host.
	 *
	 * @param hostname           The hostname on which we perform health check
	 * @param previousSshStatus  The results that will be used to create protocol health check metric
	 * @param sshConfiguration   The SSH configuration retrieved from the telemetryManager
	 * @return The updated SSH status after performing the remote SSH test or the previous SSH status if the SSH test succeeds.
	 */
	private Double remoteSshTest(
		final String hostname,
		final Double previousSshStatus,
		final SshConfiguration sshConfiguration,
		final DeviceKind hostType
	) {
		// CHECKSTYLE:OFF
		try {
			if (
				OsCommandService.runSshCommand(
					SSH_TEST_COMMAND,
					hostname,
					sshConfiguration,
					OsCommandConfiguration.DEFAULT_TIMEOUT,
					null,
					null,
					hostType
				) ==
				null
			) {
				log.debug(
					"Hostname {} - Checking SSH protocol status. Remote SSH command has not returned any results.",
					hostname
				);
				return DOWN;
			}
		} catch (Exception e) {
			log.debug(
				"Hostname {} - Checking SSH protocol status. SSH exception when performing a remote SSH command test: ",
				hostname,
				e
			);
			return DOWN;
		}
		return previousSshStatus;
		// CHECKSTYLE:ON
	}

	/**
	 * Creates and configures a new instance of the Jackson ObjectMapper for handling YAML data.
	 *
	 * @return A configured ObjectMapper instance.
	 */
	public static JsonMapper newObjectMapper() {
		return JsonMapper
			.builder(new YAMLFactory())
			.enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS)
			.enable(SerializationFeature.INDENT_OUTPUT)
			.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false)
			.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
			.configure(DeserializationFeature.FAIL_ON_INVALID_SUBTYPE, false)
			.build();
	}

	@Override
	public String getIdentifier() {
		return "ssh";
	}

	@Override
	public String executeQuery(final IConfiguration configuration, final JsonNode queryNode) throws Exception {
		final String commandLine = queryNode.get("commandLine").asText();
		final SshConfiguration sshConfiguration = (SshConfiguration) configuration;
		final String hostname = configuration.getHostname();
		return OsCommandService.runSshCommand(
			commandLine,
			hostname,
			sshConfiguration,
			sshConfiguration.getTimeout(),
			null,
			commandLine,
			null
		);
	}
}
