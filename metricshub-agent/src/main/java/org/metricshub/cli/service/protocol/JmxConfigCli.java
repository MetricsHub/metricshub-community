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
import com.fasterxml.jackson.databind.node.TextNode;
import lombok.Data;
import org.metricshub.cli.service.CliExtensionManager;
import org.metricshub.engine.common.exception.InvalidConfigurationException;
import org.metricshub.engine.configuration.IConfiguration;
import picocli.CommandLine.Option;

/**
 * This class is used by MetricsHubCliService to configure JMX when using the MetricsHub CLI.
 * It creates the engine's {@link IConfiguration} for JMX object that is used to monitor a specific resource.
 */
@Data
public class JmxConfigCli implements IProtocolConfigCli {

	/**
	 * Default timeout in seconds for an SQL query
	 */
	public static final int DEFAULT_TIMEOUT = 30;

	@Option(names = "--jmx", order = 1, description = "Enables JMX")
	private boolean useJmx;

	@Option(
		names = "--jmx-port",
		order = 2,
		paramLabel = "PORT",
		description = "Port for JMX connection (default: ${DEFAULT-VALUE})",
		defaultValue = "1099"
	)
	private Integer port;

	@Option(names = "--jmx-username", order = 3, paramLabel = "USERNAME", description = "Username for JMX authentication")
	private String username;

	@Option(names = "--jmx-password", order = 4, paramLabel = "PASSWORD", description = "Password for JMX authentication")
	private char[] password;

	@Option(
		names = "--jmx-timeout",
		order = 5,
		paramLabel = "TIMEOUT",
		defaultValue = "" + DEFAULT_TIMEOUT,
		description = "Timeout in seconds for JMX requests (default: ${DEFAULT-VALUE} s)"
	)
	private String timeout;

	@Override
	public IConfiguration toConfiguration(final String defaultUsername, final char[] defaultPassword)
		throws InvalidConfigurationException {
		final var configuration = JsonNodeFactory.instance.objectNode();

		final String finalUsername = username == null ? defaultUsername : username;
		if (finalUsername != null) {
			configuration.set("username", new TextNode(finalUsername));
		}

		final char[] finalPassword = username == null ? defaultPassword : password;
		if (finalPassword != null) {
			configuration.set("password", new TextNode(String.valueOf(finalPassword)));
		}

		configuration.set("timeout", new TextNode(timeout));

		if (port != null) {
			configuration.set("port", new IntNode(port));
		}

		return CliExtensionManager
			.getExtensionManagerSingleton()
			.buildConfigurationFromJsonNode("jmx", configuration, value -> value)
			.orElseThrow();
	}
}
