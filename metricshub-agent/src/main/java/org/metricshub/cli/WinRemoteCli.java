package org.metricshub.cli;

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
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import java.io.PrintWriter;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.stream.Stream;
import lombok.Data;
import org.fusesource.jansi.Ansi;
import org.fusesource.jansi.AnsiConsole;
import org.metricshub.cli.service.CliExtensionManager;
import org.metricshub.cli.service.ConsoleService;
import org.metricshub.cli.service.MetricsHubCliService;
import org.metricshub.cli.service.MetricsHubCliService.CliPasswordReader;
import org.metricshub.cli.service.PrintExceptionMessageHandlerService;
import org.metricshub.cli.service.protocol.IProtocolConfigCli;
import org.metricshub.cli.service.protocol.WinRmConfigCli;
import org.metricshub.cli.service.protocol.WmiConfigCli;
import org.metricshub.engine.common.IQuery;
import org.metricshub.engine.configuration.IConfiguration;
import picocli.CommandLine;
import picocli.CommandLine.ArgGroup;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParameterException;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;

/**
 * CLI for executing remote Windows OS commands (CMD commands) with validation and support for various operations.
 * Supports execution via WMI or WinRM protocols.
 */
@Data
@Command(
	name = "winremotecli",
	description = "\nList of valid options: \n",
	footer = WinRemoteCli.FOOTER,
	usageHelpWidth = 180
)
public class WinRemoteCli implements IQuery, Callable<Integer> {

	/**
	 * Default timeout in seconds for a Windows remote OS command execution
	 */
	public static final int DEFAULT_TIMEOUT = 30;

	/**
	 * Footer regrouping WinRemote CLI examples
	 */
	public static final String FOOTER =
		"""

		Example:

		winremotecli <HOSTNAME> --command <COMMAND> [--wmi | --winrm] [WMI_OPTIONS | WINRM_OPTIONS]

		winremotecli dev-01 --wmi --wmi-username username --wmi-password password --command "ipconfig /all"

		winremotecli dev-01 --winrm --winrm-username username --winrm-password password --command "systeminfo"

		Note: If --wmi-password or --winrm-password is not provided, you will be prompted interactively.
		Either --wmi or --winrm must be specified (but not both).
		""";

	@Parameters(index = "0", paramLabel = "HOSTNAME", description = "Hostname or IP address of the host to monitor")
	String hostname;

	@Spec
	CommandSpec spec;

	@ArgGroup(exclusive = false, heading = "%n@|bold,underline WMI Options|@:%n")
	WmiConfigCli wmiConfigCli;

	@ArgGroup(exclusive = false, heading = "%n@|bold,underline WinRM Options|@:%n")
	WinRmConfigCli winRmConfigCli;

	@Option(
		names = "--command",
		required = true,
		order = 1,
		paramLabel = "COMMAND",
		description = "Windows OS command (CMD command) to execute"
	)
	private String command;

	@Option(
		names = { "-h", "-?", "--help" },
		order = 2,
		usageHelp = true,
		description = "Shows this help message and exits"
	)
	boolean usageHelpRequested;

	@Option(names = "-v", order = 3, description = "Verbose mode (repeat the option to increase verbosity)")
	boolean[] verbose;

	PrintWriter printWriter;

	@Override
	public JsonNode getQuery() {
		final ObjectNode commandNode = JsonNodeFactory.instance.objectNode();
		commandNode.set("query", new TextNode(command));
		commandNode.set("queryType", new TextNode("winremote"));
		return commandNode;
	}

	/**
	 * Validates the current configuration.
	 *
	 * Ensures that required parameters are not blank and that passwords can be requested interactively if needed.
	 *
	 * @throws ParameterException if required parameters are blank
	 */
	void validate() {
		// Can we ask for passwords interactively?
		final boolean interactive = ConsoleService.hasConsole();

		// Password
		if (interactive) {
			tryInteractivePasswords(System.console()::readPassword);
		}

		if (command.isBlank()) {
			throw new ParameterException(spec.commandLine(), "Windows OS command must not be empty nor blank.");
		}

		// No protocol at all?
		final boolean protocolsNotConfigured = Stream.of(wmiConfigCli, winRmConfigCli).allMatch(Objects::isNull);

		if (protocolsNotConfigured) {
			throw new ParameterException(spec.commandLine(), "At least one protocol must be specified: --winrm, --wmi.");
		}

		if (wmiConfigCli != null && winRmConfigCli != null) {
			throw new ParameterException(spec.commandLine(), "Only one protocol should be specified: --winrm or --wmi.");
		}
	}

	/**
	 * Try to start the interactive mode to request and set Windows remote passwords
	 *
	 * @param passwordReader password reader which displays the prompt text and wait for user's input
	 */
	void tryInteractivePasswords(final CliPasswordReader<char[]> passwordReader) {
		tryInteractiveWmiPassword(passwordReader);
		tryInteractiveWinRmPassword(passwordReader);
	}

	/**
	 * Try to start the interactive mode to request and set WMI password
	 *
	 * @param passwordReader password reader which displays the prompt text and wait for user's input
	 */
	void tryInteractiveWmiPassword(final CliPasswordReader<char[]> passwordReader) {
		if (wmiConfigCli != null && wmiConfigCli.getUsername() != null && wmiConfigCli.getPassword() == null) {
			wmiConfigCli.setPassword(passwordReader.read("%s password for WMI: ", wmiConfigCli.getUsername()));
		}
	}

	/**
	 * Try to start the interactive mode to request and set WinRM password
	 *
	 * @param passwordReader password reader which displays the prompt text and wait for user's input
	 */
	void tryInteractiveWinRmPassword(final CliPasswordReader<char[]> passwordReader) {
		if (winRmConfigCli != null && winRmConfigCli.getUsername() != null && winRmConfigCli.getPassword() == null) {
			winRmConfigCli.setPassword(passwordReader.read("%s password for WinRM: ", winRmConfigCli.getUsername()));
		}
	}

	private Entry<String, IConfiguration> buildConfiguration() {
		String configType;
		IProtocolConfigCli cliConfig;
		if (wmiConfigCli != null) {
			cliConfig = wmiConfigCli;
			configType = "wmi";
		} else {
			cliConfig = winRmConfigCli;
			configType = "winrm";
		}

		try {
			// Pass null for default username/password since we use protocol-specific options
			final IConfiguration protocolConfiguration = cliConfig.toConfiguration(null, null);
			// Duplicate the main hostname on each configuration. By design, the extensions retrieve the hostname from the configuration.
			protocolConfiguration.setHostname(hostname);
			protocolConfiguration.validateConfiguration(hostname);
			return Map.entry(configType, protocolConfiguration);
		} catch (Exception e) {
			throw new IllegalStateException("Invalid configuration detected.", e);
		}
	}

	/**
	 * Entry point for the Windows Remote CLI application. Initializes necessary configurations,
	 * processes command line arguments, and executes the CLI.
	 *
	 * @param args The command line arguments passed to the application.
	 */
	public static void main(String[] args) {
		System.setProperty("log4j2.configurationFile", "log4j2-cli.xml");

		// Enable colors on Windows terminal
		AnsiConsole.systemInstall();

		final CommandLine cli = new CommandLine(new WinRemoteCli());

		// Keep the below line commented for future reference
		// Using JAnsi on Windows breaks the output of Unicode (UTF-8) chars
		// It can be fixed using the below line... when running in Windows Terminal
		// and not CMD.EXE.
		// As this is poorly documented, we keep this for future improvement.
		// cli.setOut(new PrintWriter(AnsiConsole.out(), true, StandardCharsets.UTF_8)); // NOSONAR on commented code

		// Set the exception handler
		cli.setExecutionExceptionHandler(new PrintExceptionMessageHandlerService());

		// Allow case insensitive enum values
		cli.setCaseInsensitiveEnumValuesAllowed(true);

		// Allow case insensitive options
		cli.setOptionsCaseInsensitive(true);

		// Execute the command
		final int exitCode = cli.execute(args);

		// Cleanup Windows terminal settings
		AnsiConsole.systemUninstall();

		System.exit(exitCode);
	}

	@Override
	public Integer call() throws Exception {
		// Validate the entries
		validate();
		// Gets the output writer from the command line spec.
		printWriter = spec.commandLine().getOut();
		// Set the logger level
		MetricsHubCliService.setLogLevel(verbose);

		// build the configuration
		final Entry<String, IConfiguration> configEntry = buildConfiguration();

		final String protocol = configEntry.getKey();

		// Find an extension to execute the command
		CliExtensionManager
			.getExtensionManagerSingleton()
			.findExtensionByType(protocol)
			.ifPresent(extension -> {
				try {
					// display the request
					displayCommand(protocol);
					// Execute the Windows remote OS command
					final String result = extension.executeQuery(configEntry.getValue(), getQuery());
					// display the returned result
					displayResult(result);
				} catch (Exception e) {
					throw new IllegalStateException("Failed to execute Windows remote OS command.\n", e);
				}
			});
		return CommandLine.ExitCode.OK;
	}

	/**
	 * Prints command execution details.
	 */
	void displayCommand(final String protocol) {
		printWriter.println(Ansi.ansi().a("Hostname ").bold().a(hostname).a(" - Executing Windows remote OS command."));
		printWriter.println(Ansi.ansi().a("Protocol: ").fgBrightBlack().a(protocol).reset().toString());
		printWriter.println(Ansi.ansi().a("Command: ").fgBrightBlack().a(command).reset().toString());
		printWriter.flush();
	}

	/**
	 * Prints the command execution result.
	 *
	 * @param result the command execution result
	 */
	void displayResult(final String result) {
		printWriter.println(Ansi.ansi().fgBlue().bold().a("Result:\n").reset().a(result).toString());
		printWriter.flush();
	}
}
