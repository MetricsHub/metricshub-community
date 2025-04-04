package org.metricshub.cli.service.protocol;

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

import com.fasterxml.jackson.databind.node.BooleanNode;
import com.fasterxml.jackson.databind.node.IntNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import org.metricshub.cli.service.CliExtensionManager;
import org.metricshub.engine.common.exception.InvalidConfigurationException;
import org.metricshub.engine.configuration.IConfiguration;
import picocli.CommandLine.ArgGroup;
import picocli.CommandLine.Option;

/**
 * This class is used by MetricsHubCliService to configure Http protocol when using the MetricsHub CLI.
 * It create the engine's {@link IConfiguration} for HTTP object that is used to monitor a specific resource through REST.
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class HttpConfigCli extends AbstractTransportProtocolCli {

	/**
	 * Default timeout in seconds for an HTTP operation
	 */
	public static final int DEFAULT_TIMEOUT = 30;

	@ArgGroup(exclusive = true, multiplicity = "0..1")
	HttpOrHttps httpOrHttps;

	/**
	 * Configuration class representing the choice between HTTP and HTTPS protocols.
	 */
	public static class HttpOrHttps {

		@Getter
		@Option(names = "--http", order = 1, description = "Enables HTTP", required = true)
		boolean http;

		@Getter
		@Option(names = "--https", order = 2, description = "Enables HTTPS", required = true)
		boolean https;
	}

	@Option(
		names = "--http-port",
		order = 3,
		paramLabel = "PORT",
		description = "Port for HTTP/HTTPS connections (default: 80 for HTTP, 443 for HTTPS)"
	)
	private Integer port;

	@Option(
		names = { "--http-username" },
		order = 4,
		paramLabel = "USER",
		description = "Username for HTTP authentication"
	)
	private String username;

	@Option(
		names = { "--http-password" },
		order = 5,
		paramLabel = "P4SSW0RD",
		description = "Password for the HTTP protocol",
		arity = "0..1",
		interactive = true
	)
	private char[] password;

	@Option(
		names = "--http-timeout",
		order = 6,
		paramLabel = "TIMEOUT",
		defaultValue = "" + DEFAULT_TIMEOUT,
		description = "Timeout in seconds for HTTP operations (default: ${DEFAULT-VALUE} s)"
	)
	private String timeout;

	/**
	 * This method creates an {@link IConfiguration} for a given username and a given password.
	 *
	 * @param defaultUsername Username specified at the top level of the CLI (with the --username option)
	 * @param defaultPassword Password specified at the top level of the CLI (with the --password option)
	 * @return an HttpProtocol instance corresponding to the options specified by the user in the CLI
	 * @throws InvalidConfigurationException
	 */
	@Override
	public IConfiguration toConfiguration(String defaultUsername, char[] defaultPassword)
		throws InvalidConfigurationException {
		final ObjectNode configuration = JsonNodeFactory.instance.objectNode();

		configuration.set("https", BooleanNode.valueOf(isHttps()));

		final String finalUsername = username == null ? defaultUsername : username;
		configuration.set("username", new TextNode(finalUsername));

		final char[] finalPassword = username == null ? defaultPassword : password;
		if (finalPassword != null) {
			configuration.set("password", new TextNode(String.valueOf(finalPassword)));
		}

		configuration.set("port", new IntNode(getOrDeducePortNumber()));
		configuration.set("timeout", new TextNode(timeout));

		return CliExtensionManager
			.getExtensionManagerSingleton()
			.buildConfigurationFromJsonNode("http", configuration, value -> value)
			.orElseThrow();
	}

	/**
	 * @return Whether HTTPS is configured or not
	 */
	@Override
	protected boolean isHttps() {
		return httpOrHttps.https;
	}

	/**
	 * @return Default HTTPS port number
	 */
	@Override
	protected int defaultHttpsPortNumber() {
		return 443;
	}

	/**
	 * @return Default HTTP port number
	 */
	@Override
	protected int defaultHttpPortNumber() {
		return 80;
	}
}
