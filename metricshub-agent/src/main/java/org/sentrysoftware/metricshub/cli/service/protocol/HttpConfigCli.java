package org.sentrysoftware.metricshub.cli.service.protocol;

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

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import org.sentrysoftware.metricshub.engine.configuration.HttpConfiguration;
import org.sentrysoftware.metricshub.engine.configuration.IConfiguration;
import picocli.CommandLine.ArgGroup;
import picocli.CommandLine.Option;

/**
 * This class is used by MetricsHubCliService to configure Http protocol when using the MetricsHub CLI.
 * It create the engine's {@link HttpConfiguration} object that is used to monitor a specific resource through REST.
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
	private long timeout;

	/**
	 * @param defaultUsername Username specified at the top level of the CLI (with the --username option)
	 * @param defaultPassword Password specified at the top level of the CLI (with the --password option)
	 * @return an HttpProtocol instance corresponding to the options specified by the user in the CLI
	 */
	@Override
	public IConfiguration toProtocol(String defaultUsername, char[] defaultPassword) {
		return HttpConfiguration
			.builder()
			.https(isHttps())
			.port(getOrDeducePortNumber())
			.username(username == null ? defaultUsername : username)
			.password(username == null ? defaultPassword : password)
			.timeout(timeout)
			.build();
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
