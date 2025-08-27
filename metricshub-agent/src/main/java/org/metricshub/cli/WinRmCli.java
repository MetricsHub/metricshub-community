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
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.IntNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import java.io.PrintWriter;
import java.util.List;
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

/**
 * CLI for executing WinRm queries with validation and execution support.
 */
@Data
@Command(name = "winrmcli", description = "\nList of valid options: \n", footer = WinRmCli.FOOTER, usageHelpWidth = 180)
public class WinRmCli implements IQuery, Callable<Integer> {

	/**
	 * The identifier for the WinRm protocol.
	 */
	private static final String PROTOCOL_IDENTIFIER = "winrm";
	/**
	 * Default timeout in seconds for a WinRM operation
	 */
	public static final int DEFAULT_TIMEOUT = 30;
	/**
	 * Default Http Port
	 */
	public static final Integer DEFAULT_HTTP_PORT = 5985;
	/**
	 * Default Https port
	 */
	public static final Integer DEFAULT_HTTPS_PORT = 5986;
	/**
	 * List of possible authentication schemas
	 */
	public static final Set<String> AUTHENTICATION_SCHEMAS = Set.of("NTLM", "KERBEROS");

	/**
	 * Footer regrouping WINRM CLI examples
	 */
	public static final String FOOTER =
		"""

		Example:

		winrmcli <HOSTNAME> --username <USERNAME> --password <PASSWORD> --namespace <NAMESPACE> --query <QUERY> \
		--transport <PROTOCOL> --port <PORT> --timeout <TIMEOUT> --authentications <AUTH1>,<AUTH2>,...

		winrmcli dev-01 --username username --password password --namespace="root/cimv2" --query ="SELECT * FROM Win32_OperatingSystem" \
		--transport https --port 5986 --timeout 30s --authentications NTLM,KERBEROS

		Note: If --password is not provided, you will be prompted interactively.
		""";

	@Parameters(index = "0", paramLabel = "HOSTNAME", description = "Hostname or IP address of the host to monitor")
	String hostname;

	@Spec
	CommandSpec spec;

	@Option(
		names = "--transport",
		order = 1,
		paramLabel = "HTTP|HTTPS",
		defaultValue = "HTTP",
		description = "Transport protocol for WinRM (default: ${DEFAULT-VALUE})"
	)
	private String protocol;

	@Option(names = { "--username" }, order = 2, paramLabel = "USER", description = "Username for WinRM authentication")
	private String username;

	@Option(
		names = { "--password" },
		order = 3,
		paramLabel = "P4SSW0RD",
		description = "Password for the WinRM authentication",
		arity = "0..1",
		interactive = true
	)
	private char[] password;

	@Option(
		names = "--port",
		order = 4,
		paramLabel = "PORT",
		description = "Port for WinRM service (default: 5985 for HTTP, 5986 for HTTPS)"
	)
	private Integer port;

	@Option(
		names = "--timeout",
		order = 5,
		paramLabel = "TIMEOUT",
		defaultValue = "" + DEFAULT_TIMEOUT,
		description = "Timeout in seconds for WinRM operations (default: ${DEFAULT-VALUE} s)"
	)
	private String timeout;

	@Option(
		names = { "--authentications", "--auth" },
		description = "Comma-separated ordered list of authentication schemes." +
		" Possible values are NTLM and KERBEROS. By default, only NTLM is used",
		order = 6,
		paramLabel = "AUTH",
		split = ","
	)
	private List<String> authentications;

	@Option(names = "--query", required = true, order = 7, paramLabel = "QUERY", description = "WinRm query to execute")
	private String query;

	@Option(
		names = { "--namespace" },
		order = 8,
		paramLabel = "NAMESPACE",
		description = "Forces a specific namespace for connectors that perform namespace auto-detection (advanced)"
	)
	private String namespace;

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
			tryInteractivePassword(System.console()::readPassword);
		}

		if (query.isBlank()) {
			throw new ParameterException(spec.commandLine(), "WinRm query must not be empty nor blank.");
		}

		if (namespace.isBlank()) {
			throw new ParameterException(spec.commandLine(), "WinRm namespace must not be empty nor blank.");
		}

		if (authentications != null) {
			authentications.forEach(authentication -> {
				if (!AUTHENTICATION_SCHEMAS.contains(authentication.toUpperCase())) {
					throw new ParameterException(
						spec.commandLine(),
						String.format("Invalid authentication schema %s. Possible values are NTLM or KERBEROS", authentication)
					);
				}
			});
		}
	}

	/**
	 * Try to start the interactive mode to request and set WinRm password
	 *
	 * @param passwordReader password reader which displays the prompt text and wait for user's input
	 */
	void tryInteractivePassword(final CliPasswordReader<char[]> passwordReader) {
		if (username != null && password == null) {
			password = (passwordReader.read("%s password for WinRm: ", username));
		}
	}

	/**
	 * Entry point for the WinRm CLI application. Initializes necessary configurations,
	 * processes command line arguments, and executes the CLI.
	 *
	 * @param args The command line arguments passed to the application.
	 */
	public static void main(String[] args) {
		System.setProperty("log4j2.configurationFile", "log4j2-cli.xml");

		// Enable colors on Windows terminal
		AnsiConsole.systemInstall();

		final CommandLine cli = new CommandLine(new WinRmCli());

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

					configurationNode.set("protocol", new TextNode(protocol));
					configurationNode.set("username", new TextNode(username));
					if (password != null) {
						configurationNode.set("password", new TextNode(String.valueOf(password)));
					}

					configurationNode.set("port", new IntNode(getOrDeducePortNumber()));
					configurationNode.set("timeout", new TextNode(timeout));
					configurationNode.set("namespace", new TextNode(namespace));

					if (authentications != null) {
						final ArrayNode authenticationsNode = JsonNodeFactory.instance.arrayNode();
						authentications.stream().forEach(authenticationsNode::add);
						configurationNode.set("authentications", authenticationsNode);
					}

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
					// Execute the WinRm query
					final String result = extension.executeQuery(configuration, getQuery(), null);
					// display the returned result
					displayResult(result);
				} catch (Exception e) {
					throw new IllegalStateException("Failed to execute WinRm query.\n", e);
				}
			});
		return CommandLine.ExitCode.OK;
	}

	/**
	 * Returns the specified port or deduces it based on the protocol.
	 *
	 * @return the configured port, or the default port for HTTP/HTTPS.
	 */
	protected int getOrDeducePortNumber() {
		if (port != null) {
			return port;
		} else if ("https".equals(protocol)) {
			return DEFAULT_HTTPS_PORT;
		}
		return DEFAULT_HTTP_PORT;
	}

	/**
	 * Prints query details.
	 */
	void displayQuery() {
		printWriter.println(Ansi.ansi().a("Hostname ").bold().a(hostname).a(" - Executing WinRm request."));
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
