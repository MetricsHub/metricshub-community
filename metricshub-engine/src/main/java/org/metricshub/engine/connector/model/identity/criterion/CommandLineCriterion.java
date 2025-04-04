package org.metricshub.engine.connector.model.identity.criterion;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * MetricsHub Engine
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

import static com.fasterxml.jackson.annotation.Nulls.FAIL;
import static com.fasterxml.jackson.annotation.Nulls.SKIP;
import static org.metricshub.engine.common.helpers.MetricsHubConstants.NEW_LINE;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import java.util.StringJoiner;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import org.metricshub.engine.connector.deserializer.custom.BooleanDeserializer;
import org.metricshub.engine.connector.deserializer.custom.NonBlankDeserializer;
import org.metricshub.engine.connector.deserializer.custom.TimeoutDeserializer;

/**
 * Represents an operating system command detection criterion.
 * This criterion allows the execution of operating system commands and checks the result against an expected value.
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class CommandLineCriterion extends Criterion {

	private static final long serialVersionUID = 1L;

	/**
	 * The command line to be executed as part of the criterion.
	 */
	@NonNull
	@JsonSetter(nulls = FAIL)
	@JsonDeserialize(using = NonBlankDeserializer.class)
	private String commandLine;

	/**
	 * An optional error message to be associated with the criterion.
	 */
	private String errorMessage;
	/**
	 * The expected result of the operating system command execution.
	 */
	private String expectedResult;

	/**
	 * Indicates whether the command should be executed locally or remotely.
	 */
	@JsonDeserialize(using = BooleanDeserializer.class)
	@JsonSetter(nulls = SKIP)
	private Boolean executeLocally = false;

	/**
	 * The timeout for the command execution, in seconds.
	 */
	@JsonDeserialize(using = TimeoutDeserializer.class)
	private Long timeout;

	/**
	 * Builder for constructing instances of {@link CommandLineCriterion}.
	 *
	 * @param type                Type of the criterion.
	 * @param forceSerialization Flag indicating whether serialization should be forced.
	 * @param commandLine         The command line to be executed.
	 * @param errorMessage        An optional error message.
	 * @param expectedResult      The expected result of the command.
	 * @param executeLocally      Indicates whether to execute the command locally.
	 * @param timeout             The timeout for command execution.
	 */
	@Builder
	@JsonCreator
	public CommandLineCriterion(
		@JsonProperty("type") String type,
		@JsonProperty("forceSerialization") boolean forceSerialization,
		@JsonProperty(value = "commandLine", required = true) @NonNull String commandLine,
		@JsonProperty("errorMessage") String errorMessage,
		@JsonProperty("expectedResult") String expectedResult,
		@JsonProperty("executeLocally") Boolean executeLocally,
		@JsonProperty("timeout") Long timeout
	) {
		super(type, forceSerialization);
		this.commandLine = commandLine;
		this.errorMessage = errorMessage;
		this.expectedResult = expectedResult;
		this.executeLocally = executeLocally != null && executeLocally;
		this.timeout = timeout;
	}

	@Override
	public String toString() {
		final StringJoiner stringJoiner = new StringJoiner(NEW_LINE);

		if (commandLine != null && !commandLine.isBlank()) {
			stringJoiner.add(new StringBuilder("- CommandLine: ").append(commandLine));
		}

		stringJoiner.add(new StringBuilder("- ExecuteLocally: ").append(executeLocally));

		if (expectedResult != null) {
			stringJoiner.add(new StringBuilder("- ExpectedResult: ").append(expectedResult));
		}

		if (timeout != null) {
			stringJoiner.add(new StringBuilder("- Timeout: ").append(timeout));
		}

		return stringJoiner.toString();
	}
}
