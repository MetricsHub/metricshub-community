package org.metricshub.cli;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * MetricsHub Agent
 * ჻჻჻჻჻჻
 * Copyright 2023 - 2024 Sentry Software
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
import com.fasterxml.jackson.databind.node.IntNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;
import java.util.stream.Stream;
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
import picocli.CommandLine.ParseResult;
import picocli.CommandLine.Spec;

/**
 * CLI for executing HTTP requests with validation and support for various operations.
 */
@Data
@Command(name = "httpcli", description = "\nList of valid options: \n", footer = HttpCli.FOOTER, usageHelpWidth = 180)
public class HttpCli implements IQuery, Callable<Integer> {

	/**
	 * The HTTP protocol identifier.
	 */
	public static final String HTTP = "http";
	/**
	 * The HTTPS protocol identifier.
	 */
	public static final String HTTPS = "https";
	/**
	 * The default port for HTTP connections.
	 */
	public static final int DEFAULT_HTTP_PORT = 80;
	/**
	 * The default port for HTTPS connections.
	 */
	public static final int DEFAULT_HTTPS_PORT = 443;
	/**
	 * HTTP GET method
	 */
	private static final String HTTP_GET = "GET";

	/**
	 * A set of valid HTTP methods.
	 */
	public static final Set<String> VALID_HTTP_METHODS = Set.of(
		"GET",
		"POST",
		"PUT",
		"DELETE",
		"HEAD",
		"OPTIONS",
		"TRACE",
		"PATCH"
	);

	/**
	 * Footer regrouping HTTP CLI examples
	 */
	public static final String FOOTER =
		"""

		Examples:

		httpcli <GET|POST|PUT|DELETE> <URL> --username <USERNAME> --password <PASSWORD> [--body <BODY> or --body-file <BODY FILE PATH>] \
		[--header <HEADER> --header <HEADER> or --header-file <HEADER FILE PATH>] --timeout <TIMEOUT>

		@|green # HTTP GET request with a body and two headers.|@
		httpcli get https://dev-01:443/users --username username --password password --header="Content-Type: application/xml" \
		--header="Accept:application/json" --body="<aaaLogin inName="username" inPassword="password" />" --timeout 2m

		@|green # HTTP POST request with a header file and a body file.|@
		httpcli post https://dev-01:443/users --username admin --password pass --header-file="/opt/metricshub/header.txt" \
		--body-file="/opt/metricshub/body.txt" --timeout 2m

		Note: If --password is not provided, you will be prompted interactively.
		""";

	@Spec
	CommandSpec spec;

	@Parameters(index = "0", paramLabel = "METHOD", description = "HTTP request Method.", arity = "0..1")
	private String positionalMethod;

	@Parameters(index = "1", paramLabel = "URL", description = "Url for HTTP request.", arity = "0..1")
	private String positionalUrl;

	@Option(names = "--url", order = 1, paramLabel = "URL", description = "Url for HTTP request.")
	private String url;

	@Option(names = "--method", order = 2, paramLabel = "METHOD", description = "HTTP method (GET|POST|PUT|DELETE)")
	private String method;

	@Option(
		names = "--header",
		order = 3,
		paramLabel = "HEADER",
		split = ":",
		description = "Header to be added to the HTTP request. Repeatable for multiple headers."
	)
	private Map<String, String> headers;

	@Option(
		names = { "--header-file", "--headerfile" },
		order = 4,
		paramLabel = "HEADERFILE",
		description = "Path of the file containing header to be added to the HTTP request."
	)
	private String headerFile;

	@Option(names = "--body", order = 5, paramLabel = "BODY", description = "Body of the HTTP request.")
	private String body;

	@Option(
		names = { "--body-file", "--bodyfile" },
		order = 6,
		paramLabel = "BODYFILE",
		description = "Path of the file containing the HTTP request body."
	)
	private String bodyFile;

	@Option(names = { "--username" }, order = 7, paramLabel = "USER", description = "Username for HTTP authentication")
	private String username;

	@Option(
		names = { "--password" },
		order = 8,
		paramLabel = "P4SSW0RD",
		description = "Password for the HTTP protocol",
		arity = "0..1",
		interactive = true
	)
	private char[] password;

	@Option(
		names = "--timeout",
		order = 9,
		paramLabel = "TIMEOUT",
		defaultValue = "120",
		description = "Timeout in seconds for HTTP operations (default: ${DEFAULT-VALUE} s)"
	)
	private String timeout;

	@Option(
		names = { "-h", "-?", "--help" },
		order = 10,
		usageHelp = true,
		description = "Shows this help message and exits"
	)
	boolean usageHelpRequested;

	@Option(names = "-v", order = 11, description = "Verbose mode (repeat the option to increase verbosity)")
	boolean[] verbose;

	URL parsedUrl;

	PrintWriter printWriter;

	String headerContent;
	String bodyContent;

	@Override
	public JsonNode getQuery() {
		final ObjectNode queryNode = JsonNodeFactory.instance.objectNode();

		queryNode.set("method", new TextNode(method.toUpperCase()));

		queryNode.set("url", new TextNode(url));

		if (headerContent != null) {
			queryNode.set("header", new TextNode(headerContent));
		}

		if (bodyContent != null) {
			queryNode.set("body", new TextNode(bodyContent));
		}

		queryNode.set("resultContent", new TextNode("all_with_status"));

		return queryNode;
	}

	/**
	 * Retrieves the body content for the HTTP request.
	 * If the body is specified directly, it is assigned to the bodyContent.
	 * If a body file is specified, its contents are read and set to the bodyContent.
	 *
	 * @throws IOException if an error occurs while reading the bodyFile.
	 */
	public void populateBodyContent() throws IOException {
		if (body != null) {
			bodyContent = body;
		} else if (bodyFile != null) {
			try (Stream<String> stream = Files.lines(Path.of(bodyFile), StandardCharsets.UTF_8)) {
				bodyContent = stream.collect(Collectors.joining("\n"));
			}
		}
	}

	/**
	 * Retrieves the header content for the HTTP request.
	 * If headers are specified directly, they are formatted and assigned to the headerContent.
	 * If a header file is specified, its contents are read and assigned to the headerContent.
	 *
	 * @throws IOException if an error occurs while reading the headerFile.
	 */
	public void populateHeaderContent() throws IOException {
		if (headers != null) {
			headerContent =
				headers
					.entrySet()
					.stream()
					.map(entry -> "%s: %s".formatted(entry.getKey(), entry.getValue()))
					.collect(Collectors.joining("\n"));
		} else if (headerFile != null) {
			try (Stream<String> stream = Files.lines(Path.of(headerFile), StandardCharsets.UTF_8)) {
				headerContent = stream.collect(Collectors.joining("\n"));
			}
		}
	}

	/**
	 * Resolves the URL by assigning {@code positionalUrl} to {@code url}
	 * if {@code url} is not explicitly set. Throws an exception if neither is set.
	 *
	 * @throws CommandLine.ParameterException if both {@code url} and {@code positionalUrl} are null.
	 */
	public void resolveUrl() {
		if (url == null) {
			if (positionalUrl != null) {
				url = positionalUrl;
			} else {
				throw new CommandLine.ParameterException(spec.commandLine(), "Missing required URL parameter.");
			}
		}
	}

	/**
	 * Resolves the HTTP method by assigning {@code positionalMethod} to {@code method}
	 * if {@code method} is not explicitly set. Defaults to {@code "GET"} if neither is set.
	 */
	public void resolveMethod() {
		if (method == null) {
			method = positionalMethod != null ? positionalMethod : HTTP_GET;
		}

		if (!VALID_HTTP_METHODS.contains(method.toUpperCase())) {
			method = HTTP_GET;
		}
	}

	/**
	 * Try to start the interactive mode to request and set HTTP password
	 *
	 * @param passwordReader password reader which displays the prompt text and wait for user's input
	 */
	void tryInteractivePassword(final CliPasswordReader<char[]> passwordReader) {
		if (username != null && password == null) {
			password = passwordReader.read("%s password for HTTP: ", username);
		}
	}

	void resolvePositionalParameters() {
		ParseResult parseResults = spec.commandLine().getParseResult();

		// Only one positional parameter have been specified
		if (
			parseResults != null && parseResults.matchedPositionals() != null && parseResults.matchedPositionals().size() == 1
		) {
			if (VALID_HTTP_METHODS.contains(positionalMethod.toUpperCase())) {
				// if the positional method value is an HTPP method
				if (method == null) {
					method = positionalMethod;
				}
			} else {
				// if the positional method value is a URL
				if (url == null) {
					url = positionalMethod;
				}
				positionalMethod = null;
			}
		}
	}

	/**
	 * Validates the HTTP request configuration and parameters.
	 * Ensures required fields are set and checks for conflicts in headers and body configuration.
	 *
	 * @throws ParameterException if validation fails due to missing or conflicting parameters.
	 */
	void validate() throws ParameterException {
		// Can we ask for passwords interactively?
		final boolean interactive = ConsoleService.hasConsole();

		// Password
		if (interactive) {
			tryInteractivePassword(System.console()::readPassword);
		}

		resolvePositionalParameters();

		resolveUrl();

		resolveMethod();

		// Validating HTTP Url
		validateUrl();

		// Validating headers
		if (headers != null && headerFile != null) {
			throw new ParameterException(
				spec.commandLine(),
				"Conflict - Two headers have been configured: --header and --header-file."
			);
		} else if (headerFile != null) {
			try {
				populateHeaderContent();
			} catch (IOException e) {
				throw new ParameterException(
					spec.commandLine(),
					String.format("Error while reading header file %s : %s", headerFile, e)
				);
			}
		}

		// Validating body
		if (body != null && bodyFile != null) {
			throw new ParameterException(
				spec.commandLine(),
				"Conflict - Two bodies have been configured: --body and --body-file."
			);
		} else if (bodyFile != null) {
			try {
				populateBodyContent();
			} catch (IOException e) {
				throw new ParameterException(
					spec.commandLine(),
					String.format("Error while reading body file %s : %s", bodyFile, e)
				);
			}
		}
	}

	/**
	 * Validates the URL's syntax using Java's URL and URI classes.
	 *
	 * @throws ParameterException if the URL is invalid
	 */
	void validateUrl() {
		// if the protocol isn't included in the URL, we add https://
		if (url != null && !url.startsWith(HTTP)) {
			url = "https://" + url;
		}

		try {
			// Performing a basic validation of the URL format
			parsedUrl = new URL(url);
			// Enforces stricter compliance, catching invalid characters.
			parsedUrl.toURI();
		} catch (MalformedURLException e) {
			throw new ParameterException(spec.commandLine(), "Malformed URL: " + e.getMessage(), e);
		} catch (URISyntaxException e) {
			throw new ParameterException(spec.commandLine(), "URL contains invalid characters: " + e.getMessage(), e);
		} catch (Exception e) {
			throw new ParameterException(spec.commandLine(), "Invalid URL: " + e.getMessage(), e);
		}
	}

	/**
	 * Resolves the port number from the URL or returns a default value.
	 *
	 * @return the resolved or default port number
	 * @throws ParameterException if the URL is invalid
	 */
	int resolvePortFromUrl() {
		// Check if the port is explicitly specified
		int port = parsedUrl.getPort();
		if (port != -1) {
			return port; // Port found in the URL
		}

		// Determine the default port based on protocol
		String protocol = parsedUrl.getProtocol();
		if (HTTP.equalsIgnoreCase(protocol)) {
			return DEFAULT_HTTP_PORT;
		} else if (HTTPS.equalsIgnoreCase(protocol)) {
			return DEFAULT_HTTPS_PORT;
		}

		// Default to 443 if no protocol or port is found
		return DEFAULT_HTTPS_PORT;
	}

	/**
	 * Entry point for the HTTP CLI application. Initializes necessary configurations,
	 * processes command line arguments, and executes the CLI.
	 *
	 * @param args The command line arguments passed to the application.
	 */
	public static void main(String[] args) {
		System.setProperty("log4j2.configurationFile", "log4j2-cli.xml");

		// Enable colors on Windows terminal
		AnsiConsole.systemInstall();

		final CommandLine cli = new CommandLine(new HttpCli());

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
			.findExtensionByType(HTTP)
			.ifPresent(extension -> {
				try {
					// Create and fill in a configuration ObjectNode
					final ObjectNode configurationNode = JsonNodeFactory.instance.objectNode();

					configurationNode.set(HTTPS, BooleanNode.valueOf(parsedUrl.getProtocol().equals(HTTPS)));
					configurationNode.set("port", new IntNode(resolvePortFromUrl()));
					configurationNode.set("timeout", new TextNode(timeout));
					configurationNode.set("username", new TextNode(username));
					if (password != null) {
						configurationNode.set("password", new TextNode(String.valueOf(password)));
					}

					// Build an IConfiguration from the configuration ObjectNode
					final IConfiguration configuration = extension.buildConfiguration(HTTP, configurationNode, null);

					final String hostname = parsedUrl.getHost();
					configuration.setHostname(hostname);

					configuration.validateConfiguration(hostname);

					// display the request
					displayRequest();

					// Execute the HTTP query
					final String result = extension.executeQuery(configuration, getQuery());
					// display the returned result
					displayResult(result);
				} catch (Exception e) {
					throw new IllegalStateException(String.format("Failed to execute HTTP %s query.%n", method), e);
				}
			});
		return CommandLine.ExitCode.OK;
	}

	/**
	 * Displays the details of an HTTP request in a formatted manner.
	 */
	void displayRequest() {
		printWriter.println(
			String.format(
				"Hostname %s - Executing %s %s request:",
				parsedUrl.getHost(),
				parsedUrl.getProtocol(),
				method.toUpperCase()
			)
		);

		printWriter.println(Ansi.ansi().a("Url: ").fgBrightBlack().a(url).reset().toString());

		if (headerContent != null) {
			printWriter.println(Ansi.ansi().a("Header:\n").fgBrightBlack().a(headerContent).reset().toString());
		}

		if (bodyContent != null) {
			printWriter.println(Ansi.ansi().a("Body:\n").fgBrightBlack().a(bodyContent).reset().toString());
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
