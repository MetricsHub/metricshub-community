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
import com.fasterxml.jackson.databind.node.IntNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.TextNode;
import java.io.PrintWriter;
import java.util.List;
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
import org.metricshub.engine.extension.IProtocolExtension;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParameterException;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;

/**
 * CLI for executing JMX requests.
 */
@Data
@Command(name = "jmxcli", description = "\nList of valid options: \n", footer = JmxCli.FOOTER, usageHelpWidth = 180)
public class JmxCli implements IQuery, Callable<Integer> {

	/**
	 * The identifier for the JMX protocol.
	 */
	private static final String PROTOCOL_IDENTIFIER = "jmx";

	/**
	 * Default timeout in seconds for an JMX request
	 */
	public static final int DEFAULT_TIMEOUT = 30;

	/**
	 * Footer regrouping JMX CLI examples
	 */
	public static final String FOOTER =
		"""

		Example:

		jmxcli <HOSTNAME> --port <PORT> --username <USERNAME> --password <PASSWORD> --timeout <TIMEOUT> \
		--object-name <OBJECT_NAME> --attributes <ATTRIBUTE, ATTRIBUTE,...> --key-properties <KEY_PROPERTY, KEY_PROPERTY,...>

		jmxcli cassandra-01 --port 7199 --timeout 60 \
		--object-name org.apache.cassandra.metrics:type=Table,keyspace=system,scope=*,name=TotalDiskSpaceUsed \
		--key-properties scope --attributes Count,Type

		Note: If --password is not provided, you will be prompted interactively.
		""";

	@Parameters(index = "0", paramLabel = "HOSTNAME", description = "Hostname or IP address of the JMX server")
	private String hostname;

	@Spec
	private CommandSpec spec;

	@Option(
		names = "--port",
		order = 1,
		paramLabel = "PORT",
		defaultValue = "1099",
		description = "Port of the JMX server (default: ${DEFAULT-VALUE})"
	)
	private int port;

	@Option(names = "--username", order = 2, paramLabel = "USERNAME", description = "Username for JMX authentication")
	private String username;

	@Option(names = "--password", order = 3, paramLabel = "PASSWORD", description = "Password for JMX authentication")
	private char[] password;

	@Option(
		names = "--timeout",
		order = 4,
		paramLabel = "TIMEOUT",
		defaultValue = "" + DEFAULT_TIMEOUT,
		description = "Timeout in seconds for JMX requests (default: ${DEFAULT-VALUE} s)"
	)
	private String timeout;

	@Option(
		names = "--object-name",
		required = true,
		order = 5,
		paramLabel = "OBJECT_NAME",
		description = "MBean object name pattern"
	)
	private String objectName;

	@Option(
		names = "--attributes",
		required = false,
		order = 6,
		paramLabel = "ATTRIBUTE",
		split = ",",
		description = "Comma-separated list of attributes to fetch from the MBean"
	)
	private List<String> attributes;

	@Option(
		names = "--key-properties",
		required = false,
		order = 7,
		paramLabel = "KEY_PROPERTY",
		split = ",",
		description = "Comma-separated list of key properties to include in the result set"
	)
	private List<String> keyProperties;

	@Option(
		names = { "-h", "-?", "--help" },
		order = 8,
		usageHelp = true,
		description = "Shows this help message and exits"
	)
	private boolean usageHelpRequested;

	@Option(names = "-v", order = 9, description = "Verbose mode (repeat the option to increase verbosity)")
	private boolean[] verbose;

	private PrintWriter printWriter;

	@Override
	public JsonNode getQuery() {
		final var queryNode = JsonNodeFactory.instance.objectNode();

		queryNode.put("objectName", objectName);
		if (attributes != null && !attributes.isEmpty()) {
			queryNode.putArray("attributes").addAll(attributes.stream().map(TextNode::new).toList());
		}

		if (keyProperties != null && !keyProperties.isEmpty()) {
			queryNode.putArray("keyProperties").addAll(keyProperties.stream().map(TextNode::new).toList());
		}

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

		// Validate required parameters
		if (objectName == null) {
			throw new ParameterException(spec.commandLine(), "--object-name input must be provided.");
		}

		if (objectName.isBlank()) {
			throw new ParameterException(spec.commandLine(), "--object-name input must not be empty nor blank.");
		}

		if ((attributes == null || attributes.isEmpty()) && (keyProperties == null || keyProperties.isEmpty())) {
			throw new ParameterException(
				spec.commandLine(),
				"At least one attribute or key-property must be specified. Use --attributes or/and --key-properties options."
			);
		}
	}

	/**
	 * Try to start the interactive mode to request and set WMI password
	 *
	 * @param passwordReader password reader which displays the prompt text and wait for user's input
	 */
	void tryInteractivePassword(final CliPasswordReader<char[]> passwordReader) {
		if (username != null && password == null) {
			password = (passwordReader.read("%s password for JMX: ", username));
		}
	}

	/**
	 * Entry point for the JMX CLI application. Initializes necessary configurations,
	 * processes command line arguments, and executes the CLI.
	 *
	 * @param args The command line arguments passed to the application.
	 */
	public static void main(String[] args) {
		System.setProperty("log4j2.configurationFile", "log4j2-cli.xml");

		// Enable colors on Windows terminal
		AnsiConsole.systemInstall();

		final var cli = new CommandLine(new JmxCli());

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
			.ifPresent(this::runQuery);

		// Return success exit code
		return CommandLine.ExitCode.OK;
	}

	/**
	 * Executes the JMX query using the provided protocol extension.
	 *
	 * @param extension the protocol extension to use for executing the query
	 */
	void runQuery(final IProtocolExtension extension) {
		try {
			// Create and fill in a configuration ObjectNode
			final var configurationNode = JsonNodeFactory.instance.objectNode();

			configurationNode.set("username", new TextNode(username));

			if (password != null) {
				configurationNode.set("password", new TextNode(String.valueOf(password)));
			}

			configurationNode.set("timeout", new TextNode(timeout));

			configurationNode.set("port", new IntNode(port));

			// Build an IConfiguration from the configuration ObjectNode
			final IConfiguration configuration = extension.buildConfiguration(PROTOCOL_IDENTIFIER, configurationNode, null);
			configuration.setHostname(hostname);

			configuration.validateConfiguration(hostname);

			// display the query
			displayQuery();

			// Execute the JMX query
			final String result = extension.executeQuery(configuration, getQuery());
			// display the returned result
			displayResult(result);
		} catch (Exception e) {
			throw new IllegalStateException("Failed to execute JMX query.\n", e);
		}
	}

	/**
	 * Prints query details.
	 */
	void displayQuery() {
		printWriter.println(Ansi.ansi().a("Hostname ").bold().a(hostname).a(" - Executing JMX request."));
		printWriter.println(Ansi.ansi().a("ObjectName: ").fgBrightBlack().a(objectName).reset().toString());
		if (attributes != null && !attributes.isEmpty()) {
			printWriter.println(
				Ansi.ansi().a("Attributes: ").fgBrightBlack().a(String.join(", ", attributes)).reset().toString()
			);
		}
		if (keyProperties != null && !keyProperties.isEmpty()) {
			printWriter.println(
				Ansi.ansi().a("Key Properties: ").fgBrightBlack().a(String.join(", ", keyProperties)).reset().toString()
			);
		}
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
