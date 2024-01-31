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
import org.sentrysoftware.metricshub.cli.service.converter.TransportProtocolConverter;
import org.sentrysoftware.metricshub.engine.configuration.IConfiguration;
import org.sentrysoftware.metricshub.engine.configuration.TransportProtocols;
import org.sentrysoftware.metricshub.engine.configuration.WbemConfiguration;
import picocli.CommandLine.Option;

/**
 * This class is used by MetricsHubCliService to configure WBEM protocol when using the MetricsHub CLI.
 * It create the engine's {@link WbemConfiguration} object that is used to monitor a specific resource using WBEM.
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class WbemConfigCli extends AbstractTransportProtocolCli {

	/**
	 * Default timeout in seconds for a Wbem operation
	 */
	public static final int DEFAULT_TIMEOUT = 30;

	@Option(names = "--wbem", order = 1, description = "Enables WBEM")
	private boolean useWbem;

	@Option(
		names = "--wbem-transport",
		order = 2,
		defaultValue = "HTTPS",
		paramLabel = "HTTP|HTTPS",
		description = "Transport protocol for WBEM (default: ${DEFAULT-VALUE})",
		converter = TransportProtocolConverter.class
	)
	TransportProtocols protocol;

	@Option(
		names = "--wbem-port",
		order = 3,
		paramLabel = "PORT",
		description = "Port of the WBEM server (default: 5988 for HTTP, 5989 for HTTPS)"
	)
	Integer port;

	@Option(names = "--wbem-username", order = 4, paramLabel = "USER", description = "Username for WBEM authentication")
	String username;

	@Option(
		names = "--wbem-password",
		order = 5,
		paramLabel = "P4SSW0RD",
		description = "Password for WBEM authentication",
		interactive = true,
		arity = "0..1"
	)
	char[] password;

	@Option(
		names = "--wbem-timeout",
		order = 6,
		defaultValue = "" + DEFAULT_TIMEOUT,
		paramLabel = "TIMEOUT",
		description = "Timeout in seconds for WBEM operations (default: ${DEFAULT-VALUE} s)"
	)
	long timeout;

	@Option(
		names = "--wbem-force-namespace",
		order = 7,
		paramLabel = "NAMESPACE",
		description = "Forces a specific namespace for connectors that perform namespace auto-detection (advanced)"
	)
	String namespace;

	@Option(
		names = "--wbem-vcenter",
		order = 8,
		paramLabel = "VCENTER",
		description = "VCenter hostname providing the authentication ticket (if applicable)"
	)
	String vcenter;

	/**
	 * This method creates an {@link WbemConfiguration} for a given username and a given password
	 *
	 * @param defaultUsername Username specified at the top level of the CLI (with the --username option)
	 * @param defaultPassword Password specified at the top level of the CLI (with the --password option)
	 * @return an {@link WbemConfiguration} instance corresponding to the options specified by the user in the CLI
	 */
	@Override
	public IConfiguration toProtocol(final String defaultUsername, final char[] defaultPassword) {
		return WbemConfiguration
			.builder()
			.protocol(protocol)
			.port(getOrDeducePortNumber())
			.username(username == null ? defaultUsername : username)
			.password(username == null ? defaultPassword : password)
			.namespace(namespace)
			.timeout(timeout)
			.vCenter(vcenter)
			.build();
	}

	/**
	 * @return Default HTTPS port number for WBEM
	 */
	@Override
	protected int defaultHttpsPortNumber() {
		return 5989;
	}

	/**
	 * @return Default HTTP port number for WBEM
	 */
	@Override
	protected int defaultHttpPortNumber() {
		return 5988;
	}

	/**
	 * Whether HTTPS is configured or not
	 *
	 * @return boolean value
	 */
	@Override
	protected boolean isHttps() {
		return TransportProtocols.HTTPS.equals(protocol);
	}
}
