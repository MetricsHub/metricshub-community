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
import java.util.Arrays;
import lombok.Data;
import org.metricshub.cli.service.CliExtensionManager;
import org.metricshub.engine.common.exception.InvalidConfigurationException;
import org.metricshub.engine.configuration.IConfiguration;
import picocli.CommandLine.Option;

/**
 * This class is used by MetricsHubCliService to configure SNMP protocol when using the MetricsHub CLI.
 * It create the engine's {@link IConfiguration} for SNMP object that is used to monitor a specific resource.
 */
@Data
public class SnmpConfigCli implements IProtocolConfigCli {

	/**
	 * Default timeout in seconds for an SNMP operation
	 */
	public static final int DEFAULT_TIMEOUT = 30;

	@Option(
		names = "--snmp",
		order = 1,
		defaultValue = "1",
		paramLabel = "VERSION",
		description = "Enables SNMP protocol version: 1 or 2"
	)
	String snmpVersion;

	@Option(
		names = { "--snmp-community", "--community" },
		order = 2,
		paramLabel = "COMMUNITY",
		defaultValue = "public",
		description = "Community string for SNMP version 1 and 2 (default: ${DEFAULT-VALUE})"
	)
	char[] community;

	@Option(
		names = "--snmp-port",
		order = 3,
		paramLabel = "PORT",
		defaultValue = "161",
		description = "Port of the SNMP agent (default: ${DEFAULT-VALUE})"
	)
	int port;

	@Option(
		names = "--snmp-timeout",
		order = 4,
		paramLabel = "TIMEOUT",
		defaultValue = "" + DEFAULT_TIMEOUT,
		description = "Timeout in seconds for SNMP operations (default: ${DEFAULT-VALUE} s)"
	)
	String timeout;

	@Option(
		names = { "--snmp-retry-intervals", "--snmp-retry" },
		order = 5,
		paramLabel = "RETRYINTERVALS",
		split = ",",
		description = "Comma-separated retry intervals in milliseconds for SNMP operations"
	)
	int[] retryIntervals;

	/**
	 * This method creates an {@link IConfiguration} for a given username and a given password
	 *
	 * @param defaultUsername Username specified at the top level of the CLI (with the --username option)
	 * @param defaultPassword Password specified at the top level of the CLI (with the --password option)
	 * @return an {@link IConfiguration} instance corresponding to the options specified by the user in the CLI
	 * @throws InvalidConfigurationException If the given configuration JSON node is invalid.
	 */
	@Override
	public IConfiguration toConfiguration(final String defaultUsername, final char[] defaultPassword)
		throws InvalidConfigurationException {
		final ObjectNode configuration = JsonNodeFactory.instance.objectNode();
		configuration.set("version", new TextNode(snmpVersion));
		if (community != null) {
			configuration.set("community", new TextNode((String.valueOf(community))));
		}
		configuration.set("port", new IntNode(port));
		configuration.set("timeout", new TextNode(timeout));
		if (retryIntervals != null) {
			final ArrayNode retryIntervalsArrayNode = configuration.putArray("retryIntervals");
			Arrays.stream(retryIntervals).forEach(retryIntervalsArrayNode::add);
		}

		return CliExtensionManager
			.getExtensionManagerSingleton()
			.buildConfigurationFromJsonNode("snmp", configuration, value -> value)
			.orElseThrow();
	}
}
