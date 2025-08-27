package org.metricshub.cli;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.IntNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import java.io.PrintWriter;
import java.util.Set;
import java.util.concurrent.Callable;
import lombok.Data;
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
import picocli.CommandLine.ParameterException;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;

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

/**
 * CLI for executing WBEM queries with validation and execution support.
 */
@Data
@Command(name = "wbemcli", description = "\nList of valid options: \n", footer = WbemCli.FOOTER, usageHelpWidth = 180)
public class WbemCli implements IQuery, Callable<Integer> {

	/**
	 * The identifier for the Wbem protocol.
	 */
	private static final String PROTOCOL_IDENTIFIER = "wbem";
	/**
	 * Default timeout in seconds for a Wbem operation.
	 */
	public static final int DEFAULT_TIMEOUT = 30;
	/**
	 * Default HTTPS port number.
	 */
	public static final int DEFAULT_HTTPS_PORT_NUMBER = 5989;
	/**
	 * Default HTTP port number.
	 */
	public static final int DEFAULT_HTTP_PORT_NUMBER = 5988;
	/**
	 * Set of possible protocols for WBEM.
	 */
	static Set<String> protocols = Set.of("HTTP", "HTTPS");

	/**
	 * Footer regrouping WBEM CLI examples
	 */
	public static final String FOOTER =
		"""

		Example:

		wbemcli <HOSTNAME> --namespace <NAMESPACE> --query <QUERY> --username <USERNAME> --password <PASSWORD> --vcenter <VCENTER> --transport <PROTOCOL> --port <PORT> --timeout <TIMEOUT>

		wbemcli esx-01 --namespace "root/cimv2" --query="SELECT MajorVersion FROM VMware_HypervisorSoftwareIdentity" --username username --password password --vcenter hci-vcenter

		wbemcli emc-san --namespace "root/emc" --query "SELECT DeviceID FROM EMC_DiskDrive" --transport https --username username --password password

		Note: If --password is not provided, you will be prompted interactively.
		""";

	@Parameters(index = "0", paramLabel = "HOSTNAME", description = "Hostname or IP address of the host to monitor")
	String hostname;

	@Spec
	CommandSpec spec;

	@Option(names = "--query", required = true, order = 1, paramLabel = "QUERY", description = "WBEM query to execute")
	private String query;

	@Option(
		names = "--transport",
		order = 2,
		defaultValue = "HTTPS",
		paramLabel = "HTTP|HTTPS",
		description = "Transport protocol for WBEM (default: ${DEFAULT-VALUE})"
	)
	private String protocol;

	@Option(
		names = "--port",
		order = 3,
		paramLabel = "PORT",
		description = "Port of the WBEM server (default: 5988 for HTTP, 5989 for HTTPS)"
	)
	private Integer port;

	@Option(names = "--username", order = 4, paramLabel = "USER", description = "Username for WBEM authentication")
	String username;

	@Option(
		names = "--password",
		order = 5,
		paramLabel = "P4SSW0RD",
		description = "Password for WBEM authentication",
		interactive = true,
		arity = "0..1"
	)
	private char[] password;

	@Option(
		names = "--timeout",
		order = 6,
		defaultValue = "" + DEFAULT_TIMEOUT,
		paramLabel = "TIMEOUT",
		description = "Timeout in seconds for WBEM operations (default: ${DEFAULT-VALUE} s)"
	)
	private String timeout;

	@Option(
		names = "--namespace",
		required = true,
		order = 7,
		paramLabel = "NAMESPACE",
		description = "Forces a specific namespace for connectors that perform namespace auto-detection (advanced)"
	)
	private String namespace;

	@Option(
		names = "--vcenter",
		order = 8,
		paramLabel = "VCENTER",
		description = "VCenter hostname providing the authentication ticket (if applicable)"
	)
	private String vcenter;

	@Option(
		names = { "-h", "-?", "--help" },
		order = 9,
		usageHelp = true,
		description = "Shows this help message and exits"
	)
	boolean usageHelpRequested;

	@Option(names = "-v", order = 10, description = "Verbose mode (repeat the option to increase verbosity)")
	boolean[] verbose;

	PrintWriter printWriter;

	@Override
	public JsonNode getQuery() {
		final ObjectNode queryNode = JsonNodeFactory.instance.objectNode();
		queryNode.set("query", new TextNode(query));
		return queryNode;
	}

	/**
	 * Validates the configuration, ensuring parameters are valid and protocols are supported.
	 *
	 * @throws ParameterException if validation fails
	 */
	void validate() {
		// Can we ask for passwords interactively?
		final boolean interactive = ConsoleService.hasConsole();

		// Password
		if (interactive) {
			tryInteractivePassword(System.console()::readPassword);
		}

		if (query.isBlank()) {
			throw new ParameterException(spec.commandLine(), "WBEM query must not be empty nor blank.");
		}

		if (namespace.isBlank()) {
			throw new ParameterException(spec.commandLine(), "WBEM namespace must not be empty nor blank.");
		}

		if (protocol != null && !protocols.contains(protocol.toUpperCase())) {
			throw new ParameterException(
				spec.commandLine(),
				String.format("Invalid WBEM transport protocol %s detected.", protocol)
			);
		}
	}

	/**
	 * Try to start the interactive mode to request and set Wbem password
	 *
	 * @param passwordReader password reader which displays the prompt text and wait for user's input
	 */
	void tryInteractivePassword(final CliPasswordReader<char[]> passwordReader) {
		if (username != null && password == null) {
			password = (passwordReader.read("%s password for WBEM: ", username));
		}
	}

	/**
	 * Get or deduce the port number based on the transport protocol.
	 *
	 * @return the port number as an integer
	 */
	protected int getOrDeducePortNumber() {
		if (port != null) {
			return port;
		} else if ("https".equalsIgnoreCase(protocol)) {
			return DEFAULT_HTTPS_PORT_NUMBER;
		}
		return DEFAULT_HTTP_PORT_NUMBER;
	}

	/**
	 * Entry point for the Wbem CLI application. Initializes necessary configurations,
	 * processes command line arguments, and executes the CLI.
	 *
	 * @param args The command line arguments passed to the application.
	 */
	public static void main(String[] args) {
		System.setProperty("log4j2.configurationFile", "log4j2-cli.xml");

		// Enable colors on Windows terminal
		AnsiConsole.systemInstall();

		final CommandLine cli = new CommandLine(new WbemCli());

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
					configurationNode.set("namespace", new TextNode(namespace));
					configurationNode.set("vcenter", new TextNode(vcenter));
					configurationNode.set("protocol", new TextNode(protocol));
					configurationNode.set("port", new IntNode(getOrDeducePortNumber()));

					// Build an IConfiguration from the configuration ObjectNode
					final IConfiguration configuration = extension.buildConfiguration(
						PROTOCOL_IDENTIFIER,
						configurationNode,
						null
					);
					configuration.setHostname(hostname);

					configuration.validateConfiguration(hostname);

					// display the request
					displayQuery();
					// Execute the WBEM query
					final String result = extension.executeQuery(configuration, getQuery(), null);
					// display the result
					displayResult(result);
				} catch (Exception e) {
					throw new IllegalStateException("Failed to execute WBEM query.\n", e);
				}
			});
		return CommandLine.ExitCode.OK;
	}

	/**
	 * Prints query details.
	 */
	void displayQuery() {
		printWriter.println(String.format("Hostname %s - Executing WBEM query through %s:", hostname, protocol));
		printWriter.println(Ansi.ansi().a("Query: ").fgBrightBlack().a(query).reset().toString());
		printWriter.println(Ansi.ansi().a("Namespace: ").fgBrightBlack().a(namespace).reset().toString());
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
