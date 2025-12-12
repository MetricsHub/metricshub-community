package org.metricshub.extension.oscommand.ipmi;

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

import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeoutException;
import lombok.extern.slf4j.Slf4j;
import org.metricshub.engine.common.exception.ClientException;
import org.metricshub.engine.common.exception.ControlledSshException;
import org.metricshub.engine.common.exception.IpmiCommandForSolarisException;
import org.metricshub.engine.connector.model.common.DeviceKind;
import org.metricshub.engine.strategy.detection.CriterionTestResult;
import org.metricshub.engine.telemetry.TelemetryManager;
import org.metricshub.extension.oscommand.OsCommandConfiguration;
import org.metricshub.extension.oscommand.OsCommandService;
import org.metricshub.extension.oscommand.SshConfiguration;

/**
 * Processes IPMI criteria for Unix-based systems, particularly handling command construction
 * and execution depending on the system's specific requirements and configurations.
 */
@Slf4j
public class UnixIpmiCriterionProcessor {

	private static final String IPMI_SOLARIS_VERSION_NOT_IDENTIFIED =
		"Hostname %s - Could not identify Solaris version %s. Exception: %s";

	/**
	 * Process IPMI detection for the Unix system
	 *
	 * @param hostType               The host type (windows, linux, storage, oob ...)
	 * @param telemetryManager       The telemetry manager.
	 * @return new {@link CriterionTestResult} instance
	 */
	public CriterionTestResult processUnixIpmiDetection(final DeviceKind hostType, TelemetryManager telemetryManager) {
		String ipmitoolCommand = telemetryManager.getHostProperties().getIpmitoolCommand();
		final SshConfiguration sshConfiguration = (SshConfiguration) telemetryManager
			.getHostConfiguration()
			.getConfigurations()
			.get(SshConfiguration.class);

		// Retrieve the sudo and timeout settings from OSCommandConfig for localhost, or directly from SSH for remote
		final OsCommandConfiguration osCommandConfiguration = telemetryManager.getHostProperties().isLocalhost()
			? (OsCommandConfiguration) telemetryManager
				.getHostConfiguration()
				.getConfigurations()
				.get(OsCommandConfiguration.class)
			: sshConfiguration;

		if (osCommandConfiguration == null) {
			final String message = String.format(
				"Hostname %s - No OS command configuration for this host. Returning an empty result",
				telemetryManager.getHostname()
			);
			log.warn(message);
			return CriterionTestResult.builder().success(false).result("").message(message).build();
		}

		// Retrieve the hostname from the configurations, otherwise from telemetryManager.
		final String hostname = telemetryManager.getHostname(List.of(SshConfiguration.class, OsCommandConfiguration.class));

		final int defaultTimeout = osCommandConfiguration.getTimeout().intValue();
		if (ipmitoolCommand == null || ipmitoolCommand.isEmpty()) {
			ipmitoolCommand =
				buildIpmiCommand(
					hostType,
					hostname,
					sshConfiguration,
					osCommandConfiguration,
					defaultTimeout,
					telemetryManager
				);

			// Save the ipmitool command in the telemetry manager
			telemetryManager.getHostProperties().setIpmitoolCommand(ipmitoolCommand);
		}

		// At the very end of the command line, the actual IPMI command
		ipmitoolCommand = ipmitoolCommand + " bmc info";

		// buildIpmiCommand method can either return the actual result of the built command or an error. If it is an error we display it in the error message
		if (!ipmitoolCommand.startsWith("PATH=")) {
			return CriterionTestResult.builder().success(false).result("").message(ipmitoolCommand).build();
		}

		// execute the command
		try {
			String result = null;
			result = runOsCommand(ipmitoolCommand, hostname, sshConfiguration, defaultTimeout, telemetryManager);
			if (result != null && !result.contains("IPMI Version")) {
				// Didn't find what we expected: exit
				return CriterionTestResult
					.builder()
					.success(false)
					.result(result)
					.message("Did not get the expected result from the IPMI tool command: " + ipmitoolCommand)
					.build();
			} else {
				// everything goes well
				telemetryManager
					.getHostProperties()
					.setIpmiExecutionCount(telemetryManager.getHostProperties().getIpmiExecutionCount() + 1);
				return CriterionTestResult
					.builder()
					.success(true)
					.result(result)
					.message("Successfully connected to the IPMI BMC chip with the in-band driver interface.")
					.build();
			}
		} catch (final Exception e) { // NOSONAR on interruption
			final String message = String.format(
				"Hostname %s - Cannot execute the IPMI tool command %s. Exception: %s.",
				hostname,
				ipmitoolCommand,
				e.getMessage()
			);
			log.debug(message, e);
			return CriterionTestResult.builder().success(false).message(message).build();
		}
	}

	/**
	 * Check the OS type and version and build the correct IPMI command. If the
	 * process fails, return the corresponding error.
	 *
	 * @param hostType               The type of the host.
	 * @param hostname               The hostname.
	 * @param sshConfiguration       The SSH configuration.
	 * @param osCommandConfiguration The OS command configuration.
	 * @param defaultTimeout         The default timeout.
	 * @param telemetryManager ......The telemetry manager.
	 * @return String : The IPMI Command.
	 */
	public String buildIpmiCommand(
		final DeviceKind hostType,
		final String hostname,
		final SshConfiguration sshConfiguration,
		final OsCommandConfiguration osCommandConfiguration,
		final int defaultTimeout,
		final TelemetryManager telemetryManager
	) {
		// do we need to use sudo or not?
		// If we have enabled useSudo (possible only in Web UI and CMA) --> yes
		// Or if the command is listed in useSudoCommandList (possible only in classic
		// wizard) --> yes
		String ipmitoolCommand; // Sonar don't agree with modifying arguments
		if (doesIpmitoolRequireSudo(osCommandConfiguration)) {
			ipmitoolCommand =
				"PATH=$PATH:/usr/local/bin:/usr/sfw/bin;export PATH;%{SUDO:ipmitool} ipmitool -I ".replace(
						"%{SUDO:ipmitool}",
						osCommandConfiguration.getSudoCommand()
					);
		} else {
			ipmitoolCommand = "PATH=$PATH:/usr/local/bin:/usr/sfw/bin;export PATH;ipmitool -I ";
		}

		// figure out the version of the Solaris OS
		if (DeviceKind.SOLARIS.equals(hostType)) {
			String solarisOsVersion = null;
			try {
				// Execute "/usr/bin/uname -r" command in order to obtain the OS Version
				// (Solaris)
				solarisOsVersion =
					runOsCommand("/usr/bin/uname -r", hostname, sshConfiguration, defaultTimeout, telemetryManager);
			} catch (final Exception e) { // NOSONAR on interruption
				final String message = String.format(
					IPMI_SOLARIS_VERSION_NOT_IDENTIFIED,
					hostname,
					ipmitoolCommand,
					e.getMessage()
				);
				log.debug(message, e);
				return message;
			}
			// Get IPMI command
			if (solarisOsVersion != null) {
				try {
					ipmitoolCommand = getIpmiCommandForSolaris(ipmitoolCommand, hostname, solarisOsVersion);
				} catch (final IpmiCommandForSolarisException e) {
					final String message = String.format(
						IPMI_SOLARIS_VERSION_NOT_IDENTIFIED,
						hostname,
						ipmitoolCommand,
						e.getMessage()
					);
					log.debug(message, e);
					return message;
				}
			}
		} else {
			// If not Solaris, then we're on Linux
			// On Linux, the IPMI interface driver is always 'open'
			ipmitoolCommand = ipmitoolCommand + "open";
		}
		return ipmitoolCommand;
	}

	/**
	 * Whether the ipmitool command requires sudo
	 *
	 * @param osCommandConfiguration User's configuration.
	 * @return boolean value whether IPMI tool require Sudo or not.
	 */
	private boolean doesIpmitoolRequireSudo(final OsCommandConfiguration osCommandConfiguration) {
		// CHECKSTYLE:OFF
		// @formatter:off
		return (
			osCommandConfiguration.isUseSudo() ||
			(
				osCommandConfiguration.getUseSudoCommands() != null &&
				osCommandConfiguration.getUseSudoCommands().contains("ipmitool")
			)
		);
		// @formatter:on
		// CHECKSTYLE:ON
	}

	/**
	 * Get IPMI command based on Solaris version. If version == 9, then use 'lipmi'.
	 * If version > 9, then use 'bmc'. Otherwise, return an error.
	 *
	 * @param ipmitoolCommand    The base IPMI tool command.
	 * @param hostname           The hostname.
	 * @param solarisOsVersion   The Solaris OS version.
	 * @return String : IPMI command for Solaris.
	 * @throws IpmiCommandForSolarisException If an error occurs while determining the IPMI command.
	 */
	public String getIpmiCommandForSolaris(String ipmitoolCommand, final String hostname, final String solarisOsVersion)
		throws IpmiCommandForSolarisException {
		final String[] split = solarisOsVersion.split("\\.");
		if (split.length < 2) {
			throw new IpmiCommandForSolarisException(
				String.format(
					"Unknown Solaris version (%s) for host: %s IPMI cannot be executed. Returning an empty result.",
					solarisOsVersion,
					hostname
				)
			);
		}

		final String solarisVersion = split[1];
		try {
			final int versionInt = Integer.parseInt(solarisVersion);
			if (versionInt == 9) {
				// On Solaris 9, the IPMI interface drive is 'lipmi'
				ipmitoolCommand = ipmitoolCommand + "lipmi";
			} else if (versionInt < 9) {
				throw new IpmiCommandForSolarisException(
					String.format(
						"Solaris version (%s) is too old for the host: %s IPMI cannot be executed. Returning an empty result.",
						solarisOsVersion,
						hostname
					)
				);
			} else {
				// On more modern versions of Solaris, the IPMI interface driver is 'bmc'
				ipmitoolCommand = ipmitoolCommand + "bmc";
			}
		} catch (final NumberFormatException e) {
			throw new IpmiCommandForSolarisException(
				"Could not identify Solaris version as a valid one.\nThe 'uname -r' command returned: " + solarisOsVersion + "."
			);
		}

		return ipmitoolCommand;
	}

	/**
	 * Run SSH command. Check if we can execute on localhost or remote.
	 *
	 * @param ipmitoolCommand    The IPMI tool command to execute.
	 * @param hostname           The hostname.
	 * @param sshConfiguration   The SSH configuration.
	 * @param timeout            The timeout for command execution.
	 * @return Command execution output.
	 * @throws InterruptedException If the operation is interrupted.
	 * @throws IOException          If an I/O error occurs.
	 * @throws TimeoutException     If the operation times out.
	 * @throws ClientException      If an error occurs in the client.
	 * @throws ControlledSshException If an error occurs in the controlled SSH.
	 */
	String runOsCommand(
		final String ipmitoolCommand,
		final String hostname,
		final SshConfiguration sshConfiguration,
		final int timeout,
		final TelemetryManager telemetryManager
	) throws InterruptedException, IOException, TimeoutException, ClientException, ControlledSshException {
		return telemetryManager.getHostProperties().isLocalhost()
			? OsCommandService.runLocalCommand(ipmitoolCommand, timeout, null) // or we can use NetworkHelper.isLocalhost(hostname)
			: OsCommandService.runSshCommand(
				ipmitoolCommand,
				hostname,
				sshConfiguration,
				timeout,
				null,
				null,
				telemetryManager.getHostConfiguration().getHostType()
			);
	}
}
