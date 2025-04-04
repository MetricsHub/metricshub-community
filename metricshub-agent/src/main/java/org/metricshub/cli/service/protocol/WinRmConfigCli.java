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

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.IntNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.metricshub.cli.service.CliExtensionManager;
import org.metricshub.engine.common.exception.InvalidConfigurationException;
import org.metricshub.engine.configuration.IConfiguration;
import picocli.CommandLine.Option;

/**
 * This class is used by MetricsHubCliService to configure WinRM protocol when using the MetricsHub CLI.
 * It create the engine's {@link IConfiguration} object that is used to monitor a specific resource through WinRm.
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class WinRmConfigCli extends AbstractTransportProtocolCli {

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

	@Option(names = "--winrm", order = 1, description = "Enables WinRM")
	private boolean useWinRM;

	@Option(
		names = "--winrm-transport",
		order = 2,
		paramLabel = "HTTP|HTTPS",
		defaultValue = "HTTP",
		description = "Transport protocol for WinRM (default: ${DEFAULT-VALUE})"
	)
	private String protocol;

	@Option(
		names = { "--winrm-username" },
		order = 3,
		paramLabel = "USER",
		description = "Username for WinRM authentication"
	)
	private String username;

	@Option(
		names = { "--winrm-password" },
		order = 4,
		paramLabel = "P4SSW0RD",
		description = "Password for the WinRM authentication",
		arity = "0..1",
		interactive = true
	)
	private char[] password;

	@Option(
		names = "--winrm-port",
		order = 5,
		paramLabel = "PORT",
		description = "Port for WinRM service (default: 5985 for HTTP, 5986 for HTTPS)"
	)
	private Integer port;

	@Option(
		names = "--winrm-timeout",
		order = 6,
		paramLabel = "TIMEOUT",
		defaultValue = "" + DEFAULT_TIMEOUT,
		description = "Timeout in seconds for WinRM operations (default: ${DEFAULT-VALUE} s)"
	)
	private String timeout;

	@Option(
		names = "--winrm-auth",
		description = "Comma-separated ordered list of authentication schemes." +
		" Possible values are NTLM and KERBEROS. By default, only NTLM is used",
		order = 7,
		paramLabel = "AUTH",
		split = ","
	)
	private List<String> authentications;

	@Option(
		names = { "--winrm-force-namespace" },
		order = 8,
		paramLabel = "NAMESPACE",
		description = "Forces a specific namespace for connectors that perform namespace auto-detection (advanced)"
	)
	private String namespace;

	/**
	 * @param defaultUsername Username specified at the top level of the CLI (with the --username option)
	 * @param defaultPassword Password specified at the top level of the CLI (with the --password option)
	 * @return a WinRmProtocol instance corresponding to the options specified by the user in the CLI
	 * @throws InvalidConfigurationException
	 */
	@Override
	public IConfiguration toConfiguration(String defaultUsername, char[] defaultPassword)
		throws InvalidConfigurationException {
		final ObjectNode configuration = JsonNodeFactory.instance.objectNode();

		final String finalUsername = username == null ? defaultUsername : username;
		final char[] finalPassword = username == null ? defaultPassword : password;

		configuration.set("username", new TextNode(finalUsername));
		if (finalPassword != null) {
			configuration.set("password", new TextNode(String.valueOf(finalPassword)));
		}

		configuration.set("namespace", new TextNode(namespace));
		configuration.set("port", new IntNode(getOrDeducePortNumber()));
		configuration.set("protocol", new TextNode(protocol));
		configuration.set("authentications", getAuthentications());
		configuration.set("timeout", new TextNode(timeout));

		return CliExtensionManager
			.getExtensionManagerSingleton()
			.buildConfigurationFromJsonNode("winrm", configuration, value -> value)
			.orElseThrow();
	}

	/**
	 * @return Default HTTPS Port Number for WinRM
	 */
	protected int defaultHttpsPortNumber() {
		return 5986;
	}

	/**
	 * @return Default HTTP Port Number for WinRM
	 */
	protected int defaultHttpPortNumber() {
		return 5985;
	}

	/**
	 * @return authentication list if specified, null otherwise
	 */
	protected ArrayNode getAuthentications() {
		// Create an arrayNode that will contain all the authentications that the user introduced
		ArrayNode authenticationsList = null;
		if (authentications != null) {
			authenticationsList = JsonNodeFactory.instance.arrayNode();
			// Add all the introduced authentications
			authentications.stream().forEach(authenticationsList::add);
		}
		return authenticationsList;
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
