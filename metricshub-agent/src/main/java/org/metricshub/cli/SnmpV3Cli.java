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

import static org.metricshub.cli.service.protocol.SnmpConfigCli.DEFAULT_TIMEOUT;
import static org.metricshub.cli.util.SnmpCliHelper.saveSnmpResultToFile;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.IntNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.stream.Stream;
import lombok.Data;
import org.fusesource.jansi.Ansi;
import org.fusesource.jansi.AnsiConsole;
import org.metricshub.cli.service.CliExtensionManager;
import org.metricshub.cli.service.MetricsHubCliService;
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
 * CLI for executing SNMPv3 queries with validation and support for various operations.
 */
@Data
@Command(
	name = "snmpv3cli",
	description = "\nList of valid options: \n",
	footer = SnmpV3Cli.FOOTER,
	usageHelpWidth = 180
)
public class SnmpV3Cli implements IQuery, Callable<Integer> {

	/**
	 * The identifier for the SNMPv3 protocol.
	 */
	private static final String PROTOCOL_IDENTIFIER = "snmpv3";

	/**
	 * Footer regrouping SNMPv3 CLI examples
	 */
	public static final String FOOTER =
		"""

		Example:

		@|green # SNMPv3 Get request:|@
		snmpv3cli <HOSTNAME> --get <OID> --privacy <DES|AES> --privacy-password <PRIVACY-PASSWORD> --auth <SHA|MD5> \
		--username username --password password --context-name <CONTEXT> --timeout <TIMEOUT> \
		--retry <INTERVAL1>,<INTERVAL2>,...

		snmpv3cli dev-01 --get 1.3.6.1.4.1.674.10892.5.5.1.20.130.4.1.1.1 --privacy AES --privacy-password privacyPassword \
		--auth MD5 --username username --password password --context-name context --timeout 2m --retry 5000,10000

		snmpv3cli dev-01 --get 1.3.6.1.4.1.674.10892.5.5.1.20.130.4.1.1.1 --privacy AES --privacy-password privacyPassword \
		--auth SHA256 --username username --password password --context-name context --timeout 2m

		@|green # SNMPv3 Get Next request:|@
		snmpv3cli <HOSTNAME> --getNext <OID> --privacy <DES|AES> --privacy-password <PRIVACY-PASSWORD> \
		--auth <SHA|MD5> --username username --password password --context-name <CONTEXT> --timeout <TIMEOUT> \
		--retry <INTERVAL1>,<INTERVAL2>,...

		snmpv3cli dev-01 --getNext 1.3.6.1.4.1.674.10892.5.5.1.20.130.4 --privacy AES --privacy-password privacyPassword \
		--auth MD5 --username username --password password --context-name context --timeout 2m

		snmpv3cli dev-01 --getNext 1.3.6.1.4.1.674.10892.5.5.1.20.130.4 --privacy AES --privacy-password privacyPassword \
		--auth SHA256 --username username --password password --context-name context --timeout 2m

		@|green # SNMPv3 Walk request:|@
		snmpv3cli <HOSTNAME> --walk <OID> --privacy <DES|AES> --privacy-password <PRIVACY-PASSWORD> --auth <SHA|MD5> \
		--username username --password password --context-name <CONTEXT> --timeout <TIMEOUT> --retry <INTERVAL1>,<INTERVAL2>,...

		snmpv3cli dev-01 --walk 1.3.6.1 --privacy AES --privacy-password privacyPassword --auth MD5 --username username \
		--password password --context-name context --timeout 2m

		snmpv3cli dev-01 --walk 1.3.6.1 --privacy AES --privacy-password privacyPassword --auth SHA256 \
		--username username --password password --context-name context --timeout 2m

		@|green # SNMPv3 Table request:|@
		snmpv3cli <HOSTNAME> --table <OID> --columns <COLUMN1>,<COLUMN2>,... --privacy <DES|AES> --privacy-password <PRIVACY-PASSWORD> \
		--auth <SHA|MD5> --username username --password password --context-name <CONTEXT> --timeout <TIMEOUT> --retry <INTERVAL1>,<INTERVAL2>,...

		snmpv3cli dev-01 --table 1.3.6.1.4.1.674.10892.5.4.300.10.1 --columns 1,3,8,9,11 --privacy AES --privacy-password privacyPassword \
		--auth MD5 --username username --password password --context-name context --timeout 2m

		snmpv3cli dev-01 --table 1.3.6.1.4.1.674.10892.5.4.300.10.1 --columns 1,3,8,9,11 --privacy AES --privacy-password privacyPassword \
		--auth SHA256 --username username --password password --context-name context --timeout 2m

		Note: If --password is not provided, you will be prompted interactively.
		""";

	@Parameters(index = "0", paramLabel = "HOSTNAME", description = "Hostname or IP address of the host to monitor")
	String hostname;

	@Spec
	CommandSpec spec;

	@Option(
		names = "--privacy",
		order = 1,
		paramLabel = "DES|AES|AES192|AES256",
		description = "Privacy (encryption type) for SNMP version 3 (DES, AES, or none)"
	)
	private String privacy;

	@Option(
		names = "--privacy-password",
		order = 2,
		paramLabel = "PRIVACY-PASSWORD",
		description = "Privacy (encryption) password for SNMP version 3"
	)
	private char[] privacyPassword;

	@Option(
		names = "--auth",
		order = 3,
		paramLabel = "SHA|SHA256|SHA512|SHA384|SHA224|MD5",
		description = "Authentication type for SNMP version 3 (SHA, MD5 or NO_AUTH)"
	)
	private String authType;

	@Option(
		names = "--username",
		order = 4,
		paramLabel = "USERNAME",
		description = "Username for SNMP version 3 with MD5 or SHA"
	)
	private String username;

	@Option(
		names = "--password",
		order = 5,
		paramLabel = "PASSWORD",
		description = "Password for SNMP version 3 with MD5 or SHA"
	)
	private char[] password;

	@Option(
		names = "--context-name",
		order = 6,
		paramLabel = "CONTEXT-NAME",
		description = "Context name for SNMP version 3"
	)
	private String contextName;

	@Option(
		names = "--timeout",
		order = 7,
		paramLabel = "TIMEOUT",
		defaultValue = "" + DEFAULT_TIMEOUT,
		description = "Timeout in seconds for SNMP version 3 operations (default: ${DEFAULT-VALUE} s)"
	)
	private String timeout;

	@Option(
		names = "--port",
		order = 8,
		paramLabel = "PORT",
		defaultValue = "161",
		description = "Port of the SNMP version 3 agent (default: ${DEFAULT-VALUE})"
	)
	private int port;

	@Option(
		names = { "--retry-intervals", "--retry" },
		order = 9,
		paramLabel = "RETRYINTERVALS",
		split = ",",
		description = "Comma-separated retry intervals in milliseconds for SNMPv3 operations"
	)
	private int[] retryIntervals;

	@Option(names = "--get", order = 10, paramLabel = "OID", description = "SNMP Get request")
	String get;

	@Option(names = { "--get-next", "--getNext" }, order = 11, paramLabel = "OID", description = "SNMP Get Next request")
	String getNext;

	@Option(names = "--walk", order = 12, paramLabel = "OID", description = "SNMP Walk request")
	String walk;

	@Option(names = "--table", order = 13, paramLabel = "OID", description = "SNMP Table request")
	String table;

	@Option(
		names = "--columns",
		split = ",",
		order = 14,
		paramLabel = "COLUMNS",
		description = "SNMP Table selected columns"
	)
	String[] columns;

	@Option(
		names = { "-h", "-?", "--help" },
		order = 15,
		usageHelp = true,
		description = "Shows this help message and exits"
	)
	boolean usageHelpRequested;

	@Option(names = "-v", order = 16, description = "Verbose mode (repeat the option to increase verbosity)")
	boolean[] verbose;

	@Option(
		names = { "-rec", "--record" },
		order = 17,
		defaultValue = "",
		description = "Enables/disables recording of SNMP query result",
		help = true
	)
	String snmpResultRecordPath;

	PrintWriter printWriter;

	@Override
	public JsonNode getQuery() {
		final ObjectNode queryNode = JsonNodeFactory.instance.objectNode();
		String action;
		String oid;

		if (get != null) {
			action = "get";
			oid = get;
		} else if (getNext != null) {
			action = "getNext";
			oid = getNext;
		} else if (walk != null) {
			action = "walk";
			oid = walk;
		} else {
			action = "table";
			oid = table;
			final ArrayNode columnsNode = JsonNodeFactory.instance.arrayNode();
			Arrays.stream(columns).forEach(columnsNode::add);
			queryNode.set("columns", columnsNode);
		}

		queryNode.set("action", new TextNode(action));
		queryNode.set("oid", new TextNode(oid));

		return queryNode;
	}

	/**
	 * Validates SNMPv3 configuration and ensures exactly one query type (--get, --get-next, --walk, or --table) is specified.
	 *
	 * @throws ParameterException if SNMPv3 is not configured, no query is specified, or multiple queries are specified.
	 */
	void validate() throws ParameterException {
		final long count = Stream.of(get, getNext, walk, table).filter(Objects::nonNull).count();

		if (count == 0) {
			throw new ParameterException(
				spec.commandLine(),
				"At least one SNMP V3 query must be specified: --get, --get-next, --walk, --table."
			);
		}

		if (count > 1) {
			throw new ParameterException(
				spec.commandLine(),
				"Only one SNMP V3 query can be specified at a time: --get, --get-next, --walk, --table."
			);
		}
	}

	/**
	 * Entry point for the SNMPv3 CLI application. Initializes necessary configurations,
	 * processes command line arguments, and executes the CLI.
	 *
	 * @param args The command line arguments passed to the application.
	 */
	public static void main(String[] args) {
		System.setProperty("log4j2.configurationFile", "log4j2-cli.xml");

		// Enable colors on Windows terminal
		AnsiConsole.systemInstall();

		final CommandLine cli = new CommandLine(new SnmpV3Cli());

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

					configurationNode.set("privacy", new TextNode(privacy));

					if (privacyPassword != null) {
						configurationNode.set("privacyPassword", new TextNode((String.valueOf(privacyPassword))));
					}

					configurationNode.set("authType", new TextNode(authType));
					configurationNode.set("contextName", new TextNode(contextName));
					configurationNode.set("timeout", new TextNode(timeout));
					configurationNode.set("port", new IntNode(port));
					if (retryIntervals != null) {
						// Creating the JSON array for retryIntervals
						final ArrayNode retryIntervalsArrayNode = configurationNode.putArray("retryIntervals");
						Arrays.stream(retryIntervals).forEach(retryIntervalsArrayNode::add);
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
					final JsonNode queryNode = getQuery();
					// Execute the SNMPv3 query
					final String result = extension.executeQuery(configuration, queryNode, null);
					// Save the snmp result to a file if the filename is provided when the record option is enabled
					if (snmpResultRecordPath != null && !snmpResultRecordPath.isBlank()) {
						saveSnmpResultToFile(result, snmpResultRecordPath + "out.walk", printWriter);
					}
					// display the result
					displayResult(result);
				} catch (Exception e) {
					throw new IllegalStateException("Failed to execute SNMPv3 query.\n", e);
				}
			});
		return CommandLine.ExitCode.OK;
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
