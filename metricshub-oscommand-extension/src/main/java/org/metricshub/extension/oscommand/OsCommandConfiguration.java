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
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import java.util.HashSet;
import java.util.Set;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.metricshub.engine.common.exception.InvalidConfigurationException;
import org.metricshub.engine.common.helpers.StringHelper;
import org.metricshub.engine.configuration.IConfiguration;
import org.metricshub.engine.deserialization.MultiValueDeserializer;
import org.metricshub.engine.deserialization.TimeDeserializer;

/**
 * Configuration class for handling OS command execution settings. This class provides options to configure
 * command execution specifics.
 */
@Data
@NoArgsConstructor
public class OsCommandConfiguration implements IConfiguration {

	/**
	 * Default Os Command timeout
	 */
	public static final Long DEFAULT_TIMEOUT = 30L;

	boolean useSudo;

	@JsonSetter(nulls = SKIP)
	Set<String> useSudoCommands = new HashSet<>();

	@JsonSetter(nulls = SKIP)
	String sudoCommand = "sudo";

	@JsonSetter(nulls = SKIP)
	@JsonDeserialize(using = TimeDeserializer.class)
	Long timeout = DEFAULT_TIMEOUT;

	@JsonSetter(nulls = SKIP)
	@JsonDeserialize(using = MultiValueDeserializer.class)
	String hostname;

	/**
	 * Creates a new instance of OsCommandConfiguration using the provided parameters.
	 *
	 * @param useSudo          Indicates whether to use sudo for executing commands.
	 * @param useSudoCommands  The set of commands for which sudo will be used.
	 * @param sudoCommand      The sudo command to use.
	 * @param timeout          The timeout for executing commands.
	 */
	@Builder
	public OsCommandConfiguration(
		final boolean useSudo,
		final Set<String> useSudoCommands,
		final String sudoCommand,
		final Long timeout,
		final String hostname
	) {
		this.useSudo = useSudo;
		this.useSudoCommands = useSudoCommands == null ? new HashSet<>() : useSudoCommands;
		this.sudoCommand = sudoCommand == null ? "sudo" : sudoCommand;
		this.timeout = timeout == null ? DEFAULT_TIMEOUT : timeout;
		this.hostname = hostname;
	}

	@Override
	public void validateConfiguration(String resourceKey) throws InvalidConfigurationException {
		StringHelper.validateConfigurationAttribute(
			timeout,
			attr -> attr == null || attr < 0L,
			() ->
				String.format(
					"Resource %s - Timeout value is invalid for protocol %s." +
					" Timeout value returned: %s. This resource will not be monitored. Please verify the configured timeout value.",
					resourceKey,
					"OSCommand",
					timeout
				)
		);
	}

	@Override
	public String toString() {
		return "Local Commands";
	}

	@Override
	public IConfiguration copy() {
		return OsCommandConfiguration
			.builder()
			.sudoCommand(sudoCommand)
			.timeout(timeout)
			.useSudo(useSudo)
			.useSudoCommands(new HashSet<>(useSudoCommands))
			.hostname(hostname)
			.build();
	}
}
