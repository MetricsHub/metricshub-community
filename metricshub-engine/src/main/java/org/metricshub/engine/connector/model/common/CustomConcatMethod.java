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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSetter;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;

/**
 * Represents a custom concatenation method for combining multiple entries.
 */
@Data
@NoArgsConstructor
public class CustomConcatMethod implements IEntryConcatMethod {

	private static final long serialVersionUID = 1L;

	/**
	 * The concatenation start string.
	 */
	@JsonSetter(nulls = FAIL)
	@NonNull
	private String concatStart;

	/**
	 * The concatenation end string.
	 */
	@JsonSetter(nulls = FAIL)
	@NonNull
	private String concatEnd;

	/**
	 * Constructor to create a CustomConcatMethod instance.
	 *
	 * @param concatStart The concatenation start string.
	 * @param concatEnd   The concatenation end string.
	 */
	@Builder
	@JsonCreator
	public CustomConcatMethod(
		@JsonProperty(value = "concatStart", required = true) @NonNull String concatStart,
		@JsonProperty(value = "concatEnd", required = true) @NonNull String concatEnd
	) {
		this.concatStart = concatStart;
		this.concatEnd = concatEnd;
	}

	@Override
	public CustomConcatMethod copy() {
		return CustomConcatMethod.builder().concatStart(concatStart).concatEnd(concatEnd).build();
	}

	@Override
	public String getDescription() {
		return String.format("custom[concatStart=%s, concatEnd=%s]", concatStart, concatEnd);
	}
}
