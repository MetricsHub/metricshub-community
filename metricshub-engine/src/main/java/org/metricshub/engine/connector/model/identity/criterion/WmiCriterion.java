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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.NonNull;

/**
 * Represents a Windows Management Instrumentation (WMI) detection criterion.
 * This criterion allows the execution of WMI queries to identify resources on Windows systems.
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class WmiCriterion extends WqlCriterion {

	private static final long serialVersionUID = 1L;

	/**
	 * Constructor to create an instance of {@link WmiCriterion} using a JSON creator.
	 *
	 * @param type                Type of the criterion.
	 * @param forceSerialization Flag indicating whether serialization should be forced.
	 * @param query               The WMI query associated with the criterion.
	 * @param namespace           The namespace for the WMI query.
	 * @param expectedResult      The expected result for the criterion.
	 * @param errorMessage        The error message associated with the criterion.
	 */
	@JsonCreator
	@Builder
	public WmiCriterion(
		@JsonProperty("type") String type,
		@JsonProperty("forceSerialization") boolean forceSerialization,
		@JsonProperty(value = "query", required = true) @NonNull String query,
		@JsonProperty(value = "namespace") String namespace,
		@JsonProperty(value = "expectedResult") String expectedResult,
		@JsonProperty(value = "errorMessage") String errorMessage
	) {
		super(type, forceSerialization, query, namespace, expectedResult, errorMessage);
	}

	@Override
	public WmiCriterion copy() {
		return WmiCriterion
			.builder()
			.query(getQuery())
			.namespace(getNamespace())
			.expectedResult(getExpectedResult())
			.errorMessage(getErrorMessage())
			.forceSerialization(isForceSerialization())
			.build();
	}

	@Override
	public String toString() {
		return super.toString();
	}
}
