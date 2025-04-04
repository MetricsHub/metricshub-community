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

import com.fasterxml.jackson.databind.node.BooleanNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import lombok.Data;
import org.metricshub.cli.service.CliExtensionManager;
import org.metricshub.engine.common.exception.InvalidConfigurationException;
import org.metricshub.engine.configuration.IConfiguration;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

/**
 * This class is used by MetricsHubCliService to configure Ipmi protocol when using the MetricsHub CLI.
 * It create the engine's {@link IConfiguration} object that is used to monitor a specific resource through IPMI.
 */
@Data
public class IpmiConfigCli implements IProtocolConfigCli {

	/**
	 * Default timeout in seconds for an IPMI operation
	 */
	public static final int DEFAULT_TIMEOUT = 120;

	@Spec
	CommandSpec spec;

	@Option(names = "--ipmi", order = 1, description = "Enables IPMI-over-LAN")
	private boolean useIpmi;

	@Option(
		names = "--ipmi-username",
		order = 2,
		paramLabel = "USER",
		description = "Username for IPMI-over-LAN authentication"
	)
	private String username;

	@Option(
		names = "--ipmi-password",
		order = 3,
		paramLabel = "P4SSW0RD",
		description = "Password for IPMI-over-LAN authentication",
		interactive = true,
		arity = "0..1"
	)
	private char[] password;

	@Option(
		names = "--ipmi-bmc-key",
		order = 4,
		paramLabel = "KEY",
		description = "BMC key for IPMI-over-LAN two-key authentication (in hexadecimal)"
	)
	private String bmcKey;

	@Option(
		names = "--ipmi-skip-auth",
		order = 5,
		defaultValue = "false",
		description = "Skips IPMI-over-LAN authentication"
	)
	private boolean skipAuth;

	@Option(
		names = "--ipmi-timeout",
		order = 6,
		paramLabel = "TIMEOUT",
		defaultValue = "" + DEFAULT_TIMEOUT,
		description = "Timeout in seconds for HTTP operations (default: ${DEFAULT-VALUE} s)"
	)
	private String timeout;

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
		configuration.set("skipAuth", BooleanNode.valueOf(skipAuth));
		configuration.set("bmcKey", new TextNode(bmcKey));

		return CliExtensionManager
			.getExtensionManagerSingleton()
			.buildConfigurationFromJsonNode("ipmi", configuration, value -> value)
			.orElseThrow();
	}
}
