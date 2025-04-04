package org.metricshub.engine.connector.model.common;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * MetricsHub Engine
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

import static com.fasterxml.jackson.annotation.Nulls.FAIL;
import static com.fasterxml.jackson.annotation.Nulls.SKIP;
import static org.metricshub.engine.common.helpers.MetricsHubConstants.EMPTY;
import static org.metricshub.engine.common.helpers.MetricsHubConstants.NEW_LINE;
import static org.metricshub.engine.common.helpers.StringHelper.addNonNull;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import java.io.Serializable;
import java.util.StringJoiner;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import org.metricshub.engine.connector.deserializer.custom.NonBlankDeserializer;
import org.metricshub.engine.connector.deserializer.custom.PositiveIntegerDeserializer;

/**
 * Represents a configuration for executing an operation for each entry of a specified source.
 */
@Data
@NoArgsConstructor
public class ExecuteForEachEntryOf implements Serializable {

	private static final long serialVersionUID = 1L;

	/**
	 * The source for executing the operation for each entry.
	 */
	@NonNull
	@JsonSetter(nulls = FAIL)
	@JsonDeserialize(using = NonBlankDeserializer.class)
	private String source;

	/**
	 * The method used to concatenate entries.
	 */
	@JsonSetter(nulls = SKIP)
	private IEntryConcatMethod concatMethod = EntryConcatMethod.LIST;

	/**
	 * The sleep integer in ms.
	 */
	@JsonDeserialize(using = PositiveIntegerDeserializer.class)
	@JsonSetter(nulls = SKIP)
	private Integer sleep;

	/**
	 * Constructs an instance of {@link ExecuteForEachEntryOf}.
	 *
	 * @param source       The source for executing the operation for each entry.
	 * @param concatMethod The method used to concatenate entries.
	 * @param sleep        The sleep integer in ms.
	 */
	@Builder
	@JsonCreator
	public ExecuteForEachEntryOf(
		@JsonProperty(value = "source", required = true) @NonNull String source,
		@JsonProperty("concatMethod") IEntryConcatMethod concatMethod,
		@JsonProperty(value = "sleep", required = false) Integer sleep
	) {
		this.source = source;
		this.concatMethod = concatMethod == null ? EntryConcatMethod.LIST : concatMethod;
		this.sleep = sleep;
	}

	/**
	 * Creates a copy of the current {@link ExecuteForEachEntryOf} instance.
	 *
	 * @return A new instance of {@link ExecuteForEachEntryOf} with the same source and concatenation method.
	 */
	public ExecuteForEachEntryOf copy() {
		return ExecuteForEachEntryOf.builder().source(source).concatMethod(concatMethod.copy()).sleep(sleep).build();
	}

	@Override
	public String toString() {
		final StringJoiner stringJoiner = new StringJoiner(NEW_LINE);

		addNonNull(stringJoiner, "- executeForEachEntryOf=", source);
		addNonNull(stringJoiner, "- concatMethod=", concatMethod != null ? concatMethod.getDescription() : EMPTY);
		addNonNull(stringJoiner, "- sleep=", sleep);

		return stringJoiner.toString();
	}
}
