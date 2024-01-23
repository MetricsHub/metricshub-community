package org.sentrysoftware.metricshub.cli.service;

import static org.sentrysoftware.metricshub.agent.helper.AgentConstants.APPLICATION_YAML_FILE_NAME;
import static org.sentrysoftware.metricshub.agent.helper.AgentConstants.OBJECT_MAPPER;

import org.sentrysoftware.metricshub.agent.context.ApplicationProperties;
import org.sentrysoftware.metricshub.agent.context.ApplicationProperties.Project;
import org.sentrysoftware.metricshub.engine.common.helpers.JsonHelper;
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
		final String hcVersion = applicationProperties.hcVersion();

		return new String[] {
			" __  __          _            _                _    _           _      ®  ",
			"|  \\/  |        | |          (_)              | |  | |         | |       ",
			"| \\  / |   ___  | |_   _ __   _    ___   ___  | |__| |  _   _  | |__     ",
			"| |\\/| |  / _ \\ | __| | '__| | |  / __| / __| |  __  | | | | | | '_ \\  ",
			"| |  | | |  __/ | |_  | |    | | | (__  \\__ \\ | |  | | | |_| | | |_) |  ",
			"|_|  |_|  \\___|  \\__| |_|    |_|  \\___| |___/ |_|  |_|  \\__,_| |_.__/ ",
			"                                 @|faint Copyright (c) Sentry Software|@",
			"",
			String.format("@|bold %s|@ version @|bold,magenta %s|@", projectName, projectVersion),
			String.format("@|faint - Build Number:|@ @|magenta %s (on %s)|@", buildNumber, buildDate),
			String.format("- Hardware Connector Library version @|green,bold %s|@", hcVersion),
			"",
			"Java version @|magenta,bold ${java.version}|@ @|faint (${java.vendor} ${java.vm.name} ${java.vm.version})|@",
			"@|faint - Java Home:|@ @|magenta ${java.home}|@",
			"@|faint - System:|@ @|magenta ${os.name} ${os.version} ${os.arch}|@"
		};
	}
}
