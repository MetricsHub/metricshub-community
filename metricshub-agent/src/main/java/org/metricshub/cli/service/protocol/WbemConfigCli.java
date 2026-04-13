package org.metricshub.cli.service.protocol;

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

import com.fasterxml.jackson.databind.node.IntNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.metricshub.cli.service.CliExtensionManager;
import org.metricshub.engine.common.exception.InvalidConfigurationException;
import org.metricshub.engine.configuration.IConfiguration;
import picocli.CommandLine.Option;

/**
 * This class is used by MetricsHubCliService to configure WBEM protocol when using the MetricsHub CLI.
 * It creates the engine's {@link IConfiguration} object that is used to monitor a specific resource using WBEM.
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
		description = "Transport protocol for WBEM (default: ${DEFAULT-VALUE})"
	)
	private String protocol;

	@Option(
		names = "--wbem-port",
		order = 3,
		paramLabel = "PORT",
		description = "Port of the WBEM server (default: 5988 for HTTP, 5989 for HTTPS)"
	)
	private Integer port;

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
	private char[] password;

	@Option(
		names = "--wbem-timeout",
		order = 6,
		defaultValue = "" + DEFAULT_TIMEOUT,
		paramLabel = "TIMEOUT",
		description = "Timeout in seconds for WBEM operations (default: ${DEFAULT-VALUE} s)"
	)
	private String timeout;

	@Option(
		names = "--wbem-force-namespace",
		order = 7,
		paramLabel = "NAMESPACE",
		description = "Forces a specific namespace for connectors that perform namespace auto-detection (advanced)"
	)
	private String namespace;

	@Option(
		names = "--wbem-vcenter",
		order = 8,
		paramLabel = "VCENTER",
		description = "VCenter hostname providing the authentication ticket (if applicable)"
	)
	private String vcenter;

	/**
	 * This method creates an {@link IConfiguration} for a given username and a given password
	 *
	 * @param defaultUsername Username specified at the top level of the CLI (with the --username option)
	 * @param defaultPassword Password specified at the top level of the CLI (with the --password option)
	 * @return an {@link IConfiguration} instance corresponding to the options specified by the user in the CLI
	 */
	@Override
	public IConfiguration toConfiguration(final String defaultUsername, final char[] defaultPassword)
		throws InvalidConfigurationException {
		final ObjectNode configuration = JsonNodeFactory.instance.objectNode();

		final String finalUsername = username == null ? defaultUsername : username;
		configuration.set("username", new TextNode(finalUsername));

		final char[] finalPassword = username == null ? defaultPassword : password;
		if (finalPassword != null) {
			configuration.set("password", new TextNode(String.valueOf(finalPassword)));
		}

		configuration.set("timeout", new TextNode(timeout));
		configuration.set("namespace", new TextNode(namespace));
		configuration.set("vcenter", new TextNode(vcenter));
		configuration.set("protocol", new TextNode(protocol));
		configuration.set("port", new IntNode(getOrDeducePortNumber()));

		return CliExtensionManager
			.getExtensionManagerSingleton()
			.buildConfigurationFromJsonNode("wbem", configuration, value -> value)
			.orElseThrow();
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
		return "https".equalsIgnoreCase(protocol);
	}
}
