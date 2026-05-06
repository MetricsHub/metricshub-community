package org.metricshub.extension.emulation.oscommand;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * MetricsHub Emulation Extension
 * ჻჻჻჻჻჻
 * Copyright (C) 2023 - 2026 MetricsHub
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

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.metricshub.engine.common.exception.ClientException;
import org.metricshub.engine.common.exception.ControlledSshException;
import org.metricshub.engine.common.exception.NoCredentialProvidedException;
import org.metricshub.engine.connector.model.common.DeviceKind;
import org.metricshub.engine.connector.model.common.EmbeddedFile;
import org.metricshub.engine.strategy.utils.OsCommandResult;
import org.metricshub.engine.telemetry.TelemetryManager;
import org.metricshub.extension.emulation.EmulationConfiguration;
import org.metricshub.extension.emulation.EmulationImageCacheManager;
import org.metricshub.extension.emulation.EmulationRoundRobinManager;
import org.metricshub.extension.oscommand.OsCommandService;
import org.metricshub.extension.oscommand.SshConfiguration;

/**
 * Emulated OS command service that replays command outputs from recorded files.
 */
public class EmulationOsCommandService extends OsCommandService {

	private final EmulationOsCommandRequestExecutor requestExecutor;

	/**
	 * Creates the emulated OS command service.
	 *
	 * @param roundRobinManager round-robin manager shared across emulation executors
	 * @param imageCacheManager cache manager used to reuse parsed image files
	 */
	public EmulationOsCommandService(
		final EmulationRoundRobinManager roundRobinManager,
		final EmulationImageCacheManager imageCacheManager
	) {
		this.requestExecutor = new EmulationOsCommandRequestExecutor(roundRobinManager, imageCacheManager);
	}

	@Override
	public OsCommandResult runOsCommand(
		final String commandLine,
		final TelemetryManager telemetryManager,
		final Long commandTimeout,
		final boolean isExecuteLocally,
		final boolean isLocalhost,
		final Map<Integer, EmbeddedFile> connectorEmbeddedFiles
	)
		throws IOException, ClientException, InterruptedException, java.util.concurrent.TimeoutException, NoCredentialProvidedException, ControlledSshException {
		if (commandLine == null || telemetryManager == null) {
			throw new IllegalArgumentException("commandLine and telemetryManager cannot be null.");
		}

		final EmulationConfiguration emulationConfiguration = (EmulationConfiguration) telemetryManager
			.getHostConfiguration()
			.getConfigurations()
			.get(EmulationConfiguration.class);
		final String result = requestExecutor.execute(telemetryManager.getHostname(), emulationConfiguration, commandLine);
		return new OsCommandResult(result, commandLine);
	}

	/**
	 * Disables local command execution in emulation mode.
	 *
	 * @param command command line
	 * @param timeout timeout in seconds
	 * @param noPasswordCommand command with masked password
	 * @return never returns
	 * @throws UnsupportedOperationException always, because emulation must not execute local commands
	 */
	@Override
	public String runLocalCommand(final String command, final long timeout, final String noPasswordCommand) {
		throw new UnsupportedOperationException(
			"runLocalCommand is disabled in emulation mode. Use runOsCommand with emulation configuration."
		);
	}

	/**
	 * Disables remote SSH command execution in emulation mode.
	 *
	 * @param command command line
	 * @param hostname target host
	 * @param sshConfiguration SSH configuration
	 * @param timeout timeout in seconds
	 * @param localFiles local files for upload
	 * @param noPasswordCommand command with masked password
	 * @param hostType device kind
	 * @return never returns
	 * @throws UnsupportedOperationException always, because emulation must not execute remote commands
	 */
	@Override
	public String runSshCommand(
		final String command,
		final String hostname,
		final SshConfiguration sshConfiguration,
		final long timeout,
		final List<File> localFiles,
		final String noPasswordCommand,
		final DeviceKind hostType
	) {
		throw new UnsupportedOperationException(
			"runSshCommand is disabled in emulation mode. Use runOsCommand with emulation configuration."
		);
	}
}
