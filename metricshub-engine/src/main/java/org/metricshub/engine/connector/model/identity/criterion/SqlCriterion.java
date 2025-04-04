package org.metricshub.engine.connector.model.identity.criterion;

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
 * Represents an SQL detection criterion.
 * This class defines a criterion based on SQL queries.
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class SqlCriterion extends Criterion {

	private static final long serialVersionUID = 1L;

	/**
	 * The Sql query associated with the criterion.
	 */
	@NonNull
	@JsonSetter(nulls = FAIL)
	@JsonDeserialize(using = NonBlankDeserializer.class)
	private String query;

	/**
	 * Expected result for the SQL criterion.
	 */
	private String expectedResult;

	/**
	 * Error message for the SQL criterion.
	 */
	private String errorMessage;

	/**
	 * Builder for creating instances of {@code SqlCriterion}.
	 *
	 * @param type               The type of the criterion (should be "sql").
	 * @param forceSerialization Flag indicating whether serialization should be forced.
	 * @param query              The SQL query to be executed.
	 */
	@Builder
	@JsonCreator
	public SqlCriterion(
		@JsonProperty("type") String type,
		@JsonProperty("forceSerialization") boolean forceSerialization,
		@JsonProperty("query") @NonNull String query,
		@JsonProperty("expectedResult") String expectedResult,
		@JsonProperty("errorMessage") String errorMessage
	) {
		super(type, forceSerialization);
		this.query = query;
		this.expectedResult = expectedResult;
		this.errorMessage = errorMessage;
	}

	@Override
	public String toString() {
		final StringJoiner stringJoiner = new StringJoiner(NEW_LINE);
		stringJoiner.add(new StringBuilder("- Query ").append(query));

		if (expectedResult != null && !expectedResult.isBlank()) {
			stringJoiner.add(new StringBuilder("- ExpectedResult: ").append(expectedResult));
		}
		if (errorMessage != null && !errorMessage.isBlank()) {
			stringJoiner.add(new StringBuilder("- ErrorMessage: ").append(errorMessage));
		}

		return stringJoiner.toString();
	}
}
