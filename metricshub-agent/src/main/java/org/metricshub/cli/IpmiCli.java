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
import com.fasterxml.jackson.databind.node.BooleanNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import java.io.PrintWriter;
import java.util.concurrent.Callable;
import org.fusesource.jansi.Ansi;
import org.fusesource.jansi.AnsiConsole;
import org.metricshub.cli.service.CliExtensionManager;
import org.metricshub.cli.service.ConsoleService;
import org.metricshub.cli.service.MetricsHubCliService;
import org.metricshub.cli.service.MetricsHubCliService.CliPasswordReader;
import org.metricshub.cli.service.PrintExceptionMessageHandlerService;
import org.metricshub.engine.common.IQuery;
import org.metricshub.engine.configuration.IConfiguration;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;

/**
 * CLI for executing IPMI queries with validation and support for various operations.
 */
@Command(name = "ipmicli", description = "\nList of valid options: \n", footer = IpmiCli.FOOTER, usageHelpWidth = 180)
public class IpmiCli implements IQuery, Callable<Integer> {

	/**
	 * The identifier for the IPMI protocol.
	 */
	private static final String PROTOCOL_IDENTIFIER = "ipmi";

	/**
	 * Footer regrouping IPMI CLI examples
	 */
	public static final String FOOTER =
		"""

		Example:

		ipmicli <HOSTNAME> --username <USERNAME> --password <PASSWORD> --bmc-key <KEY> --timeout <TIMEOUT> --skip-auth <BOOLEAN>

		ipmicli dev-01 --username username --password password --bmc-key AE4C7AB47FD --timeout 1m --skip-auth false

		Note: If --password is not provided, you will be prompted interactively.
		""";

	@Parameters(index = "0", paramLabel = "HOSTNAME", description = "Hostname or IP address of the host to monitor")
	String hostname;

	@Spec
	CommandSpec spec;

	@Option(
		names = "--username",
		order = 1,
		paramLabel = "USER",
		description = "Username for IPMI-over-LAN authentication"
	)
	private String username;

	@Option(
		names = "--password",
		order = 2,
		paramLabel = "P4SSW0RD",
		description = "Password for IPMI-over-LAN authentication",
		interactive = true,
		arity = "0..1"
	)
	private char[] password;

	@Option(
		names = { "--bmc-key", "--key" },
		order = 3,
		paramLabel = "KEY",
		description = "BMC key for IPMI-over-LAN two-key authentication (in hexadecimal)"
	)
	private String bmcKey;

	@Option(names = "--skip-auth", order = 4, defaultValue = "false", description = "Skips IPMI-over-LAN authentication")
	private boolean skipAuth;

	@Option(
		names = "--timeout",
		order = 5,
		paramLabel = "TIMEOUT",
		defaultValue = "120",
		description = "Timeout in seconds for IPMI operations (default: ${DEFAULT-VALUE} s)"
	)
	private String timeout;

	@Option(
		names = { "-h", "-?", "--help" },
		order = 6,
		usageHelp = true,
		description = "Shows this help message and exits"
	)
	boolean usageHelpRequested;

	@Option(names = "-v", order = 7, description = "Verbose mode (repeat the option to increase verbosity)")
	boolean[] verbose;

	PrintWriter printWriter;

	@Override
	public JsonNode getQuery() {
		return null;
	}

	/**
	 * Try to start the interactive mode to request and set IPMI password
	 *
	 * @param passwordReader password reader which displays the prompt text and wait for user's input
	 */
	void tryInteractivePassword(final CliPasswordReader<char[]> passwordReader) {
		if (username != null && password == null) {
			password = (passwordReader.read("%s password for IPMI: ", username));
		}
	}

	/**
	 * Validates the configuration by checking if a console is available
	 * and prompts the user for a password interactively if possible.
	 */
	void validate() {
		// Can we ask for passwords interactively?
		final boolean interactive = ConsoleService.hasConsole();

		// Password
		if (interactive) {
			tryInteractivePassword(System.console()::readPassword);
		}
	}

	/**
	 * Entry point for the IPMI CLI application. Initializes necessary configurations,
	 * processes command line arguments, and executes the CLI.
	 *
	 * @param args The command line arguments passed to the application.
	 */
	public static void main(String[] args) {
		System.setProperty("log4j2.configurationFile", "log4j2-cli.xml");

		// Enable colors on Windows terminal
		AnsiConsole.systemInstall();

		final CommandLine cli = new CommandLine(new IpmiCli());

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
		// Find an extension to execute the query
		CliExtensionManager
			.getExtensionManagerSingleton()
			.findExtensionByType(PROTOCOL_IDENTIFIER)
			.ifPresent(extension -> {
				try {
					// Create and fill in a configuration ObjectNode
					final ObjectNode configurationNode = JsonNodeFactory.instance.objectNode();
					configurationNode.set("username", new TextNode(username));
					if (password != null) {
						configurationNode.set("password", new TextNode(String.valueOf(password)));
					}

					configurationNode.set("timeout", new TextNode(timeout));
					configurationNode.set("skipAuth", BooleanNode.valueOf(skipAuth));
					configurationNode.set("bmcKey", new TextNode(bmcKey));

					// Build an IConfiguration from the configuration ObjectNode
					final IConfiguration configuration = extension.buildConfiguration(
						PROTOCOL_IDENTIFIER,
						configurationNode,
						null
					);
					configuration.setHostname(hostname);

					configuration.validateConfiguration(hostname);

					// display the request
					displayRequest();
					// Execute the IPMI query
					final String result = extension.executeQuery(configuration, null);
					// display the returned result
					displayResult(result);
				} catch (Exception e) {
					throw new IllegalStateException("Failed to execute IPMI query.\n", e);
				}
			});
		return CommandLine.ExitCode.OK;
	}

	/**
	 * Prints query details.
	 */
	void displayRequest() {
		printWriter.println(Ansi.ansi().a("Hostname ").bold().a(hostname).a(" - Executing SQL request."));
		printWriter.flush();
	}

	/**
	 * Prints the query result.
	 *
	 * @param result the query result
	 */
	void displayResult(final String result) {
		printWriter.println(Ansi.ansi().fgBlue().bold().a("Result:\n").reset().a(result).toString());
		printWriter.flush();
	}
}
