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

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import java.util.Optional;
import lombok.Data;
import org.metricshub.cli.service.CliExtensionManager;
import org.metricshub.engine.common.exception.InvalidConfigurationException;
import org.metricshub.engine.configuration.IConfiguration;
import picocli.CommandLine.Option;

/**
 * CLI options used to build emulation configuration.
 */
@Data
public class EmulationConfigCli implements IProtocolConfigCli {

	@Option(
		names = { "--emulate-http" },
		order = 15,
		defaultValue = "",
		description = "Reads HTTP recorded sources execution results from the specified directory",
		help = true
	)
	private String emulateHttp;

	@Option(
		names = { "--emulate-snmp" },
		order = 16,
		defaultValue = "",
		description = "Reads SNMP recorded sources execution results from the specified directory",
		help = true
	)
	private String emulateSnmp;

	@Option(
		names = { "--emulate-ssh" },
		order = 18,
		defaultValue = "",
		description = "Reads SSH recorded sources execution results from the specified directory",
		help = true
	)
	private String emulateSsh;

	@Option(
		names = { "--emulate-wbem" },
		order = 19,
		defaultValue = "",
		description = "Reads WBEM recorded sources execution results from the specified directory",
		help = true
	)
	private String emulateWbem;

	@Option(
		names = { "--emulate-jdbc" },
		order = 20,
		defaultValue = "",
		description = "Reads JDBC recorded sources execution results from the specified directory",
		help = true
	)
	private String emulateJdbc;

	@Option(
		names = { "--emulate-ipmi" },
		order = 21,
		defaultValue = "",
		description = "Reads IPMI recorded sources execution results from the specified directory",
		help = true
	)
	private String emulateIpmi;

	/**
	 * Indicates whether at least one emulation option has been configured on the CLI.
	 *
	 * @return {@code true} when one or more emulation directories are provided
	 */
	public boolean isEnabled() {
		final boolean hasHttp = !isBlank(emulateHttp);
		final boolean hasSnmp = !isBlank(emulateSnmp);
		final boolean hasSsh = !isBlank(emulateSsh);
		final boolean hasWbem = !isBlank(emulateWbem);
		final boolean hasJdbc = !isBlank(emulateJdbc);
		final boolean hasIpmi = !isBlank(emulateIpmi);

		return hasHttp || hasSnmp || hasSsh || hasWbem || hasJdbc || hasIpmi;
	}

	/**
	 * Builds an emulation configuration without protocol context.
	 *
	 * <p>This satisfies the generic protocol CLI contract by building a fresh emulation
	 * configuration for each enabled emulated protocol.</p>
	 *
	 * @param defaultUsername username specified at the top level of the CLI
	 * @param defaultPassword password specified at the top level of the CLI
	 * @return the built emulation configuration
	 * @throws InvalidConfigurationException if the generated configuration is invalid
	 */
	@Override
	public IConfiguration toConfiguration(final String defaultUsername, final char[] defaultPassword)
		throws InvalidConfigurationException {
		return buildConfiguration(defaultUsername, defaultPassword)
			.orElseThrow(() -> new InvalidConfigurationException("Emulation configuration is not enabled."));
	}

	/**
	 * Builds the emulation configuration.
	 *
	 * <p>Each enabled emulation flag produces a new protocol node containing its own
	 * {@code directory} and nested {@code configuration} object.</p>
	 *
	 * @param defaultUsername username specified at the top level of the CLI
	 * @param defaultPassword password specified at the top level of the CLI
	 * @return the built emulation configuration when emulation is enabled
	 * @throws InvalidConfigurationException if the generated configuration is invalid
	 */
	public Optional<IConfiguration> buildConfiguration(final String defaultUsername, final char[] defaultPassword)
		throws InvalidConfigurationException {
		if (!isEnabled()) {
			return Optional.empty();
		}

		final ObjectNode emulationConfigurationNode = JsonNodeFactory.instance.objectNode();

		if (!isBlank(emulateHttp)) {
			emulationConfigurationNode.set("http", buildProtocolEmulationNode(emulateHttp, defaultUsername, defaultPassword));
		}

		if (!isBlank(emulateSnmp)) {
			emulationConfigurationNode.set("snmp", buildProtocolEmulationNode(emulateSnmp, defaultUsername, defaultPassword));
		}

		if (!isBlank(emulateSsh)) {
			emulationConfigurationNode.set("ssh", buildProtocolEmulationNode(emulateSsh, defaultUsername, defaultPassword));
		}

		if (!isBlank(emulateWbem)) {
			emulationConfigurationNode.set("wbem", buildProtocolEmulationNode(emulateWbem, defaultUsername, defaultPassword));
		}

		if (!isBlank(emulateJdbc)) {
			emulationConfigurationNode.set("jdbc", buildProtocolEmulationNode(emulateJdbc, defaultUsername, defaultPassword));
		}

		if (!isBlank(emulateIpmi)) {
			emulationConfigurationNode.set("ipmi", buildProtocolEmulationNode(emulateIpmi, defaultUsername, defaultPassword));
		}

		return CliExtensionManager
			.getExtensionManagerSingleton()
			.buildConfigurationFromJsonNode("emulation", emulationConfigurationNode, value -> value);
	}

	/**
	 * Creates the emulation JSON node for a specific protocol.
	 *
	 * @param directory the directory containing recorded emulation data for the protocol
	 * @param defaultUsername username specified at the top level of the CLI
	 * @param defaultPassword password specified at the top level of the CLI
	 * @return the protocol-specific emulation JSON node
	 */
	private ObjectNode buildProtocolEmulationNode(
		final String directory,
		final String defaultUsername,
		final char[] defaultPassword
	) {
		final ObjectNode configurationNode = JsonNodeFactory.instance.objectNode();
		addDefaultCredentials(configurationNode, defaultUsername, defaultPassword);

		final ObjectNode protocolNode = JsonNodeFactory.instance.objectNode();
		protocolNode.put("directory", directory);
		protocolNode.set("configuration", configurationNode);
		return protocolNode;
	}

	/**
	 * Adds top-level CLI credentials to a protocol configuration when the protocol did not define them explicitly.
	 *
	 * @param configurationNode protocol configuration JSON node to enrich
	 * @param defaultUsername username specified at the top level of the CLI
	 * @param defaultPassword password specified at the top level of the CLI
	 */
	private void addDefaultCredentials(
		final ObjectNode configurationNode,
		final String defaultUsername,
		final char[] defaultPassword
	) {
		if (!isBlank(defaultUsername) && !configurationNode.hasNonNull("username")) {
			configurationNode.set("username", new TextNode(defaultUsername));
		}

		if (defaultPassword != null && !configurationNode.hasNonNull("password")) {
			configurationNode.set("password", new TextNode(String.valueOf(defaultPassword)));
		}
	}

	/**
	 * Checks whether a CLI string option is unset.
	 *
	 * @param value value to test
	 * @return {@code true} when the value is {@code null} or blank
	 */
	private boolean isBlank(final String value) {
		return value == null || value.isBlank();
	}
}
