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
import com.fasterxml.jackson.databind.node.BooleanNode;
import com.fasterxml.jackson.databind.node.IntNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import java.util.Set;
import lombok.Data;
import org.metricshub.cli.service.CliExtensionManager;
import org.metricshub.engine.common.exception.InvalidConfigurationException;
import org.metricshub.engine.configuration.IConfiguration;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

/**
 * This class is used by MetricsHubCliService to configure SSH protocol when using the MetricsHub CLI.
 * It create the engine's {@link IConfiguration} object that is used to monitor a specific resource.
 */
@Data
public class SshConfigCli implements IProtocolConfigCli {

	/**
	 * Default timeout in seconds to execute an SSH operation
	 */
	public static final int DEFAULT_TIMEOUT = 30;

	/**
	 * Default SSH port number.
	 */
	public static final int DEFAULT_PORT = 22;

	@Spec
	CommandSpec spec;

	@Option(names = "--ssh", order = 1, description = "Enables SSH")
	private boolean useSsh;

	@Option(names = "--ssh-username", order = 2, paramLabel = "USER", description = "Username for SSH authentication")
	private String username;

	@Option(
		names = "--ssh-password",
		order = 3,
		paramLabel = "P4SSW0RD",
		description = "Password or SSH authentication",
		interactive = true,
		arity = "0..1"
	)
	private char[] password;

	@Option(
		names = "--ssh-privatekey",
		order = 4,
		paramLabel = "PATH",
		description = "Path to the private key file for SSH authentication"
	)
	private String privateKey;

	@Option(
		names = "--ssh-timeout",
		order = 5,
		paramLabel = "TIMEOUT",
		description = "Timeout in seconds for SSH operations (default: ${DEFAULT-VALUE} s)",
		defaultValue = "" + DEFAULT_TIMEOUT
	)
	private String timeout;

	@Option(
		names = "--ssh-port",
		order = 6,
		paramLabel = "PORT",
		defaultValue = "" + DEFAULT_PORT,
		description = "Port number for SSH connection (default: ${DEFAULT-VALUE})"
	)
	private Integer port;

	@Option(
		names = "--ssh-usesudo-commands",
		order = 7,
		paramLabel = "COMMAND",
		description = "List of commands that requires @|italic sudo|@",
		split = ","
	)
	private Set<String> useSudoCommands;

	@Option(
		names = "--ssh-sudo-command",
		order = 8,
		paramLabel = "SUDO",
		description = "@|italic sudo|@ command (default: ${DEFAULT-VALUE})",
		defaultValue = "sudo"
	)
	private String sudoCommand;

	/**
	 * This method creates an {@link IConfiguration} for a given username and a given password
	 *
	 * @param defaultUsername Username specified at the top level of the CLI (with the --username option)
	 * @param defaultPassword Password specified at the top level of the CLI (with the --password option)
	 * @return an {@link IConfiguration} instance corresponding to the options specified by the user in the CLI
	 * @throws InvalidConfigurationException
	 */
	@Override
	public IConfiguration toConfiguration(final String defaultUsername, final char[] defaultPassword)
		throws InvalidConfigurationException {
		final ObjectNode configuration = JsonNodeFactory.instance.objectNode();
		// Create an arrayNode that will contain all the sudo commands that the user introduced
		final ArrayNode sudoCommands = JsonNodeFactory.instance.arrayNode();
		// Add all the introduced sudo commands
		if (useSudoCommands != null) {
			useSudoCommands.stream().forEach(sudoCommands::add);
		}
		final String finalUsername = username == null ? defaultUsername : username;
		configuration.set("username", new TextNode(finalUsername));

		final char[] finalPassword = username == null ? defaultPassword : password;
		if (finalPassword != null) {
			configuration.set("password", new TextNode(String.valueOf(finalPassword)));
		}

		configuration.set("privateKey", new TextNode(privateKey));
		configuration.set("useSudoCommands", sudoCommands);
		configuration.set("useSudo", BooleanNode.TRUE);
		configuration.set("sudoCommand", new TextNode(sudoCommand));
		configuration.set("timeout", new TextNode(timeout));
		configuration.set("port", new IntNode(getPort()));

		return CliExtensionManager
			.getExtensionManagerSingleton()
			.buildConfigurationFromJsonNode("ssh", configuration, value -> value)
			.orElseThrow();
	}

	/**
	 * Returns the port number for the SSH connection, defaulting to 22 if not set.
	 *
	 * @return the port number or 22 if null.
	 */
	public int getPort() {
		return port != null ? port : DEFAULT_PORT;
	}
}
