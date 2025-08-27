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
 * CLI for executing SNMP queries with validation and support for various operations.
 */
@Data
@Command(name = "snmpcli", description = "\nList of valid options: \n", footer = SnmpCli.FOOTER, usageHelpWidth = 180)
public class SnmpCli implements IQuery, Callable<Integer> {

	/**
	 * The identifier for the SNMP protocol.
	 */
	private static final String PROTOCOL_IDENTIFIER = "snmp";

	/**
	 * Footer regrouping SNMP CLI examples
	 */
	public static final String FOOTER =
		"""

		Examples:

		@|green # SNMP Get request|@
		snmpcli <HOSTNAME> --get <OID> --community <COMMUNITY> --version <VERSION> --port <PORT> --timeout <TIMEOUT> --retry <INTERVAL1>,<INTERVAL2>,...
		snmpcli <HOSTNAME> --get 1.3.6.1.4.1.674.10892.5.5.1.20.130.4.1.1.1 --community public --version v2c --port 161 --timeout 1m --retry 5000,10000

		@|green # SNMP Get Next request|@
		snmpcli <HOSTNAME> --getNext <OID> --community <COMMUNITY> --version <VERSION> --port <PORT> --timeout <TIMEOUT> --retry <INTERVAL1>,<INTERVAL2>,...
		snmpcli <HOSTNAME> --getNext 1.3.6.1.4.1.674.10892.5.5.1.20.130.4 --community public --version v2c --port 161 --timeout 1m

		@|green # SNMP Walk request|@
		snmpcli <HOSTNAME> --walk <OID> --community <COMMUNITY> --version <VERSION> --port <PORT> --timeout <TIMEOUT> --retry <INTERVAL1>,<INTERVAL2>,...
		snmpcli <HOSTNAME> --walk 1.3.6.1 --community public --version v1 --port 161 --timeout 1m

		@|green # SNMP Table request|@
		snmpcli <HOSTNAME> --table <OID> --columns <COLUMN, COLUMN, ...> --community <COMMUNITY> --version <VERSION> --port <PORT> --timeout <TIMEOUT> --retry <INTERVAL1>,<INTERVAL2>,...
		snmpcli <HOSTNAME> --table 1.3.6.1.4.1.674.10892.5.4.300.10.1 --columns 1,3,8,9,11 --community public --version v1 --port 161 --timeout 1m
		""";

	@Parameters(index = "0", paramLabel = "HOSTNAME", description = "Hostname or IP address of the host to monitor")
	String hostname;

	@Spec
	CommandSpec spec;

	@Option(
		names = "--version",
		order = 1,
		defaultValue = "v2c",
		paramLabel = "VERSION",
		description = "Enables SNMP protocol version: 1 or 2 (default: ${DEFAULT-VALUE})"
	)
	String snmpVersion;

	@Option(
		names = { "--community" },
		order = 2,
		paramLabel = "COMMUNITY",
		defaultValue = "public",
		description = "Community string for SNMP version 1 and 2 (default: ${DEFAULT-VALUE})"
	)
	char[] community;

	@Option(
		names = "--port",
		order = 3,
		paramLabel = "PORT",
		defaultValue = "161",
		description = "Port of the SNMP agent (default: ${DEFAULT-VALUE})"
	)
	int port;

	@Option(
		names = "--timeout",
		order = 4,
		paramLabel = "TIMEOUT",
		defaultValue = "" + DEFAULT_TIMEOUT,
		description = "Timeout in seconds for SNMP operations (default: ${DEFAULT-VALUE} s)"
	)
	String timeout;

	@Option(
		names = { "--retry-intervals", "--retry" },
		order = 5,
		paramLabel = "RETRYINTERVALS",
		split = ",",
		description = "Comma-separated retry intervals in milliseconds for SNMP operations"
	)
	int[] retryIntervals;

	@Option(names = "--get", order = 6, paramLabel = "OID", description = "SNMP Get request")
	String get;

	@Option(names = { "--getNext", "--get-next" }, order = 7, paramLabel = "OID", description = "SNMP Get Next request")
	String getNext;

	@Option(names = "--walk", order = 8, paramLabel = "OID", description = "SNMP Walk request")
	String walk;

	@Option(names = "--table", order = 9, paramLabel = "OID", description = "SNMP Table request")
	String table;

	@Option(
		names = "--columns",
		order = 10,
		paramLabel = "COLUMNS",
		split = ",",
		description = "SNMP Table selected columns"
	)
	String[] columns;

	@Option(
		names = { "-h", "-?", "--help" },
		order = 11,
		usageHelp = true,
		description = "Shows this help message and exits"
	)
	boolean usageHelpRequested;

	@Option(names = "-v", order = 12, description = "Verbose mode (repeat the option to increase verbosity)")
	boolean[] verbose;

	@Option(
		names = { "-rec", "--record" },
		order = 13,
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
	 * Validates SNMP configuration and ensures exactly one query type (--get, --get-next, --walk, or --table) is specified.
	 *
	 * @throws ParameterException if SNMP is not configured, no query is specified, or multiple queries are specified.
	 */
	void validate() throws ParameterException {
		final long count = Stream.of(get, getNext, walk, table).filter(Objects::nonNull).count();

		if (count == 0) {
			throw new ParameterException(
				spec.commandLine(),
				"At least one SNMP query must be specified: --get, --get-next, --walk, --table."
			);
		}

		if (count > 1) {
			throw new ParameterException(
				spec.commandLine(),
				"Only one SNMP query can be specified at a time: --get, --get-next, --walk, --table."
			);
		}

		if ((table == null) ^ (columns == null)) {
			throw new ParameterException(
				spec.commandLine(),
				"SNMP Table query requires columns to select: both --table and --columns must be specified."
			);
		}
	}

	/**
	 * Entry point for the SNMP CLI application. Initializes necessary configurations,
	 * processes command line arguments, and executes the CLI.
	 *
	 * @param args The command line arguments passed to the application.
	 */
	public static void main(String[] args) {
		System.setProperty("log4j2.configurationFile", "log4j2-cli.xml");

		// Enable colors on Windows terminal
		AnsiConsole.systemInstall();

		final CommandLine cli = new CommandLine(new SnmpCli());

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
					final ObjectNode snmpConfigNode = JsonNodeFactory.instance.objectNode();

					snmpConfigNode.set("version", new TextNode(snmpVersion));
					snmpConfigNode.set("community", new TextNode((String.valueOf(community))));
					snmpConfigNode.set("port", new IntNode(port));
					snmpConfigNode.set("timeout", new TextNode(timeout));
					if (retryIntervals != null) {
						final ArrayNode retryIntervalsArrayNode = snmpConfigNode.putArray("retryIntervals");
						Arrays.stream(retryIntervals).forEach(retryIntervalsArrayNode::add);
					}

					// Build an IConfiguration from the configuration ObjectNode
					final IConfiguration configuration = extension.buildConfiguration(PROTOCOL_IDENTIFIER, snmpConfigNode, null);
					configuration.setHostname(hostname);

					configuration.validateConfiguration(hostname);

					// display the request
					final JsonNode queryNode = getQuery();
					// Execute the SNMP query
					String result = extension.executeQuery(configuration, queryNode, snmpResultRecordPath);
					// Save the snmp result to a file if the filename is provided
					// CHECKSTYLE:OFF
					if (
						"WALK".equalsIgnoreCase(queryNode.get("action").asText()) &&
						snmpResultRecordPath != null &&
						!snmpResultRecordPath.isBlank()
					) {
						saveSnmpResultToFile(result, snmpResultRecordPath, printWriter);
					}
					// CHECKSTYLE:ON
					// display the returned result
					displayResult(result);
				} catch (Exception e) {
					throw new IllegalStateException("Failed to execute SNMP query.\n", e);
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
