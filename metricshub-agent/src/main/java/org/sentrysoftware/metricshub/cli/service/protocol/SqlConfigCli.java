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

import com.fasterxml.jackson.databind.node.IntNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import lombok.Data;
import org.sentrysoftware.metricshub.cli.service.CliExtensionManager;
import org.sentrysoftware.metricshub.engine.common.exception.InvalidConfigurationException;
import org.sentrysoftware.metricshub.engine.configuration.IConfiguration;
import picocli.CommandLine.Option;

/**
 * This class is used by MetricsHubCliService to configure SQL protocol when using the MetricsHub CLI.
 * It creates the engine's {@link IConfiguration}
 * for SQL that is used to monitor a specific resource.
 */
@Data
public class SqlConfigCli implements IProtocolConfigCli {

	/**
	 * Default timeout in seconds for an SQL operation
	 */
	public static final int DEFAULT_TIMEOUT = 30;

	@Option(names = "--sql", order = 1, description = "Enables SQL")
	private boolean useSQL;

	@Option(names = "--sql-url", order = 2, paramLabel = "URL", description = "JDBC URL for SQL connection")
	private char[] url;

	@Option(names = "--sql-username", order = 3, paramLabel = "USERNAME", description = "Username for SQL authentication")
	private String username;

	@Option(names = "--sql-password", order = 4, paramLabel = "PASSWORD", description = "Password for SQL authentication")
	private char[] password;

	@Option(
		names = "--sql-timeout",
		order = 5,
		paramLabel = "TIMEOUT",
		defaultValue = "" + DEFAULT_TIMEOUT,
		description = "Timeout in seconds for SQL operations (default: ${DEFAULT-VALUE} s)"
	)
	private String timeout;

	@Option(names = "--sql-port", order = 6, paramLabel = "PORT", description = "Port for SQL connection")
	private int port;

	@Option(
		names = "--sql-database",
		order = 7,
		paramLabel = "DATABASE",
		description = "Name of the SQL database to connect to"
	)
	private String database;

	@Option(
		names = "--sql-type",
		order = 8,
		paramLabel = "TYPE",
		description = "Type of SQL database (e.g., MySQL, PostgreSQL, SQLServer)"
	)
	private String type;

	/**
	 * This method creates an {@link IConfiguration} for a given username and a given password..
	 *
	 * @param defaultUsername Username specified at the top level of the CLI (with the --username option)
	 * @param defaultPassword Password specified at the top level of the CLI (with the --password option)
	 * @return an SQLProtocol instance corresponding to the options specified by the user in the CLI
	 * @throws InvalidConfigurationException If the SQL extension is unable to parse SQL configuration inputs.
	 */
	@Override
	public IConfiguration toProtocol(final String defaultUsername, final char[] defaultPassword)
		throws InvalidConfigurationException {
		final ObjectNode configuration = JsonNodeFactory.instance.objectNode();

		final String finalUsername = username == null ? defaultUsername : username;
		configuration.set("username", new TextNode(finalUsername));

		final char[] finalPassword = username == null ? defaultPassword : password;
		if (finalPassword != null) {
			configuration.set("password", new TextNode(String.valueOf(finalPassword)));
		}
		
		if (url != null && url.length > 0) {
			configuration.set("url", new TextNode(String.valueOf(url)));
		}
		configuration.set("timeout", new TextNode(timeout));
		configuration.set("port", new IntNode(port));
		configuration.set("database", new TextNode(database));
		configuration.set("type", new TextNode(type));

		return CliExtensionManager
			.getExtensionManagerSingleton()
			.buildConfigurationFromJsonNode("sql", configuration, value -> value)
			.orElseThrow();
	}
}
