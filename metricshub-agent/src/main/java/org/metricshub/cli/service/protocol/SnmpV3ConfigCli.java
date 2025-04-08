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

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.IntNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import java.util.Arrays;
import lombok.Data;
import org.metricshub.cli.service.CliExtensionManager;
import org.metricshub.engine.common.exception.InvalidConfigurationException;
import org.metricshub.engine.configuration.IConfiguration;
import picocli.CommandLine.Option;

/**
 * This class is used by MetricsHubCliService to configure SNMP V3 protocol when
 * using the MetricsHub CLI. It create the engine's {@link IConfiguration} for
 * SNMP object that is used to monitor a specific resource.
 */
@Data
public class SnmpV3ConfigCli implements IProtocolConfigCli {

	/**
	 * Default timeout in seconds for an SNMP operation
	 */
	public static final int DEFAULT_TIMEOUT = 30;

	@Option(names = "--snmpv3", order = 1, description = "Enable SNMP version 3 protocol")
	private boolean useSnmpv3;

	@Option(
		names = "--snmpv3-privacy",
		order = 2,
		paramLabel = "DES|AES",
		description = "Privacy (encryption type) for SNMP version 3 (DES, AES, or none)"
	)
	private String privacy;

	@Option(
		names = "--snmpv3-privacy-password",
		order = 3,
		paramLabel = "PRIVACY-PASSWORD",
		description = "Privacy (encryption) password for SNMP version 3"
	)
	private char[] privacyPassword;

	@Option(
		names = "--snmpv3-auth",
		order = 4,
		paramLabel = "SHA|SHA256|SHA512|SHA384|SHA224|MD5",
		description = "Authentication type for SNMP version 3 (SHA, MD5 or NO_AUTH)"
	)
	private String authType;

	@Option(
		names = "--snmpv3-username",
		order = 5,
		paramLabel = "USERNAME",
		description = "Username for SNMP version 3 with MD5 or SHA"
	)
	private String username;

	@Option(
		names = "--snmpv3-password",
		order = 6,
		paramLabel = "PASSWORD",
		description = "Password for SNMP version 3 with MD5 or SHA"
	)
	private char[] password;

	@Option(
		names = "--snmpv3-context-name",
		order = 7,
		paramLabel = "CONTEXT-NAME",
		description = "Context name for SNMP version 3"
	)
	private String contextName;

	@Option(
		names = "--snmpv3-timeout",
		order = 8,
		paramLabel = "TIMEOUT",
		defaultValue = "" + DEFAULT_TIMEOUT,
		description = "Timeout in seconds for SNMP version 3 operations (default: ${DEFAULT-VALUE} s)"
	)
	private String timeout;

	@Option(
		names = "--snmpv3-port",
		order = 9,
		paramLabel = "PORT",
		defaultValue = "161",
		description = "Port of the SNMP version 3 agent (default: ${DEFAULT-VALUE})"
	)
	private int port;

	@Option(
		names = { "--snmpv3-retry-intervals", "--snmpv3-retry" },
		order = 10,
		paramLabel = "RETRYINTERVALS",
		split = ",",
		description = "Comma-separated retry intervals in milliseconds for SNMPv3 operations"
	)
	private int[] retryIntervals;

	/**
	 * This method creates an {@link IConfiguration} for a given username and a
	 * given password
	 *
	 * @param defaultUsername Username specified at the top level of the CLI (with
	 *                        the --username option)
	 * @param defaultPassword Password specified at the top level of the CLI (with
	 *                        the --password option)
	 * @return an SNMPV3Protocol instance corresponding to the options specified by
	 *         the user in the CLI
	 * @throws InvalidConfigurationException If the SNMPV3 extension is unable to
	 *                                       parse SNMPV3 configuration inputs.
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
		configuration.set("privacy", new TextNode(privacy));
		if (privacyPassword != null) {
			configuration.set("privacyPassword", new TextNode((String.valueOf(privacyPassword))));
		}
		configuration.set("authType", new TextNode(authType));
		configuration.set("contextName", new TextNode(contextName));
		configuration.set("timeout", new TextNode(timeout));
		configuration.set("port", new IntNode(port));
		if (retryIntervals != null) {
			// Creating the JSON array for retryIntervals
			final ArrayNode retryIntervalsNode = JsonNodeFactory.instance.arrayNode();
			Arrays.stream(retryIntervals).mapToObj(IntNode::valueOf).forEach(retryIntervalsNode::add);
			configuration.set("retryIntervals", retryIntervalsNode);
		}
		return CliExtensionManager
			.getExtensionManagerSingleton()
			.buildConfigurationFromJsonNode("snmpv3", configuration, value -> value)
			.orElseThrow();
	}
}
