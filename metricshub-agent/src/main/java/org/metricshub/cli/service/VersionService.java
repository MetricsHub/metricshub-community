package org.metricshub.cli.service;

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

import static org.metricshub.agent.helper.AgentConstants.APPLICATION_YAML_FILE_NAME;
import static org.metricshub.agent.helper.AgentConstants.OBJECT_MAPPER;

import java.nio.file.Path;
import org.metricshub.agent.context.ApplicationProperties;
import org.metricshub.agent.context.ApplicationProperties.Project;
import org.metricshub.agent.helper.ConfigHelper;
import org.metricshub.engine.common.helpers.JsonHelper;
import org.springframework.core.io.ClassPathResource;
import picocli.CommandLine.IVersionProvider;

/**
 * Service class for providing version information.
 * Implements the {@link IVersionProvider} interface for Picocli.
 */
public class VersionService implements IVersionProvider {

	/**
	 * Retrieves version information as an array of strings.
	 *
	 * @return An array of strings containing version information.
	 * @throws Exception If there is an error while retrieving version information.
	 */
	@Override
	public String[] getVersion() throws Exception {
		// Read the application.yaml file
		final ClassPathResource classPathResource = new ClassPathResource(APPLICATION_YAML_FILE_NAME);
		final ApplicationProperties applicationProperties = JsonHelper.deserialize(
			OBJECT_MAPPER,
			classPathResource.getInputStream(),
			ApplicationProperties.class
		);

		final Project project = applicationProperties.project();
		final String projectName = project.name();
		final String projectVersion = project.version();
		final String buildNumber = applicationProperties.buildNumber();
		final String buildDate = applicationProperties.buildDate();
		final String ccVersion = applicationProperties.ccVersion();
		final Path configDir = ConfigHelper.getDefaultConfigDirectoryPath().toAbsolutePath();
		final Path logsDir = ConfigHelper.getDefaultOutputDirectory().toAbsolutePath();

		return new String[] {
			" __  __          _            _                _    _           _      ®  ",
			"|  \\/  |        | |          (_)              | |  | |         | |       ",
			"| \\  / |   ___  | |_   _ __   _    ___   ___  | |__| |  _   _  | |__     ",
			"| |\\/| |  / _ \\ | __| | '__| | |  / __| / __| |  __  | | | | | | '_ \\  ",
			"| |  | | |  __/ | |_  | |    | | | (__  \\__ \\ | |  | | | |_| | | |_) |  ",
			"|_|  |_|  \\___|  \\__| |_|    |_|  \\___| |___/ |_|  |_|  \\__,_| |_.__/ ",
			"                                 @|faint Copyright (c) MetricsHub|@",
			"",
			String.format("@|bold %s|@ version @|bold,magenta %s|@", projectName, projectVersion),
			String.format("@|faint - Build Number:|@ @|magenta %s (on %s)|@", buildNumber, buildDate),
			String.format("- Community Connector Library version @|green,bold %s|@", ccVersion),
			String.format("- Default config directory:   @|magenta %s|@", configDir),
			String.format("- Default logs directory:     @|magenta %s|@", logsDir),
			"",
			"Java version @|magenta,bold ${java.version}|@ @|faint (${java.vendor} ${java.vm.name} ${java.vm.version})|@",
			"@|faint - Java Home:|@ @|magenta ${java.home}|@",
			"@|faint - System:|@ @|magenta ${os.name} ${os.version} ${os.arch}|@"
		};
	}
}
