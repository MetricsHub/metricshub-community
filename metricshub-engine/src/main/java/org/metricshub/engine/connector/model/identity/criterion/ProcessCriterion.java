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
import org.metricshub.engine.connector.deserializer.custom.NonBlankDeserializer;

/**
 * Represents a process detection criterion.
 * This criterion allows the detection of processes based on their command line.
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class ProcessCriterion extends Criterion {

	private static final long serialVersionUID = 1L;

	/**
	 * The command line associated with the process criterion.
	 */
	@NonNull
	@JsonDeserialize(using = NonBlankDeserializer.class)
	@JsonSetter(nulls = FAIL)
	private String commandLine;

	/**
	 * Builder for constructing instances of {@link ProcessCriterion}.
	 *
	 * @param type                Type of the criterion.
	 * @param forceSerialization Flag indicating whether serialization should be forced.
	 * @param commandLine         The command line associated with the process criterion.
	 */
	@Builder
	@JsonCreator
	public ProcessCriterion(
		@JsonProperty("type") String type,
		@JsonProperty("forceSerialization") boolean forceSerialization,
		@JsonProperty(value = "commandLine", required = true) @NonNull String commandLine
	) {
		super(type, forceSerialization);
		this.commandLine = commandLine;
	}

	@Override
	public String toString() {
		final StringJoiner stringJoiner = new StringJoiner(NEW_LINE);
		if (!commandLine.isBlank()) {
			stringJoiner.add(new StringBuilder("- CommandLine: ").append(commandLine));
		}
		return stringJoiner.toString();
	}
}
