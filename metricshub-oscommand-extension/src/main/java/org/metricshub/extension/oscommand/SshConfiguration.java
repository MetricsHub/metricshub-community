package org.metricshub.extension.oscommand;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * MetricsHub OsCommand Extension
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

import static com.fasterxml.jackson.annotation.Nulls.SKIP;

import com.fasterxml.jackson.annotation.JsonSetter;
import java.util.HashSet;
import java.util.Set;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.metricshub.engine.common.exception.InvalidConfigurationException;
import org.metricshub.engine.common.helpers.StringHelper;

/**
 * Configuration class for SSH-based operations. It includes SSH-specific settings such as username, password,
 * private key, and sudo command configurations. It extends {@link OsCommandConfiguration} by adding more
 * specific fields and validation logic pertinent to SSH configurations.
 */
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class SshConfiguration extends OsCommandConfiguration {

	/**
	 * Default SSH port number.
	 */
	public static final int DEFAULT_PORT = 22;

	private String username;
	private char[] password;
	private String privateKey;

	@JsonSetter(nulls = SKIP)
	private Integer port = DEFAULT_PORT;

	/**
	 * Constructs an SshConfiguration with specific settings for SSH operations.
	 *
	 * @param useSudo Indicates whether sudo should be used for the commands.
	 * @param useSudoCommands A set of commands that should be executed with sudo.
	 * @param sudoCommand The sudo command to use.
	 * @param timeout The default timeout for SSH operations.
	 * @param port The SSH port number. Defaults to 22 if not specified.
	 * @param username The SSH username.
	 * @param password The SSH password.
	 * @param privateKey The path to the SSH private key file.
	 */
	@Builder(builderMethodName = "sshConfigurationBuilder")
	public SshConfiguration(
		boolean useSudo,
		Set<String> useSudoCommands,
		String sudoCommand,
		Long timeout,
		Integer port,
		String username,
		char[] password,
		String privateKey,
		String hostname
	) {
		super(useSudo, useSudoCommands, sudoCommand, timeout, hostname);
		this.port = port == null ? DEFAULT_PORT : port;
		this.username = username;
		this.password = password;
		this.privateKey = privateKey;
	}

	@Override
	public void validateConfiguration(String resourceKey) throws InvalidConfigurationException {
		StringHelper.validateConfigurationAttribute(
			username,
			attr -> attr == null || attr.isBlank(),
			() ->
				String.format(
					"Resource %s - No username configured for protocol %s." +
					" This resource will not be monitored. Please verify the configured username.",
					resourceKey,
					"SSH"
				)
		);

		StringHelper.validateConfigurationAttribute(
			timeout,
			attr -> attr == null || attr < 0L,
			() ->
				String.format(
					"Resource %s - Timeout value is invalid for protocol %s." +
					" Timeout value returned: %s. This resource will not be monitored. Please verify the configured timeout value.",
					resourceKey,
					"SSH",
					timeout
				)
		);

		StringHelper.validateConfigurationAttribute(
			port,
			attr -> attr == null || attr < 0 || attr > 65535,
			() -> String.format("Resource %s - Port value is invalid for SSH protocol.", resourceKey)
		);
	}

	@Override
	public String toString() {
		String desc = "SSH";
		if (username != null) {
			desc = desc + " as " + username;
		}
		return desc;
	}

	@Override
	public SshConfiguration copy() {
		return SshConfiguration
			.sshConfigurationBuilder()
			.password(password)
			.port(port)
			.privateKey(privateKey)
			.sudoCommand(sudoCommand)
			.timeout(timeout)
			.username(username)
			.useSudo(useSudo)
			.useSudoCommands(new HashSet<>(useSudoCommands))
			.hostname(hostname)
			.build();
	}
}
