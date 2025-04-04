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
import static com.fasterxml.jackson.annotation.Nulls.SKIP;

import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import org.metricshub.engine.connector.deserializer.custom.NonBlankDeserializer;

/**
 * An abstract class representing a WQL (Windows Management Instrumentation Query Language) criterion, extending the base criterion class.
 * It includes fields for the WQL query, namespace, expected result, and error message.
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public abstract class WqlCriterion extends Criterion {

	private static final long serialVersionUID = 1L;

	/**
	 * The WQL query associated with the criterion.
	 */
	@NonNull
	@JsonSetter(nulls = FAIL)
	@JsonDeserialize(using = NonBlankDeserializer.class)
	private String query;

	/**
	 * The namespace associated with the WQL query. The default is "root/cimv2".
	 */
	@JsonSetter(nulls = SKIP)
	@JsonDeserialize(using = NonBlankDeserializer.class)
	private String namespace = "root/cimv2";

	/**
	 * The expected result of the WQL query.
	 */
	private String expectedResult;
	/**
	 * The error message associated with the criterion.
	 */
	private String errorMessage;

	/**
	 * Initializes a WqlCriterion instance with the specified parameters.
	 *
	 * @param type              The type of the criterion.
	 * @param forceSerialization A flag indicating whether to force serialization.
	 * @param query             The WQL query associated with the criterion.
	 * @param namespace         The namespace associated with the WQL query.
	 * @param expectedResult    The expected result of the WQL query.
	 * @param errorMessage      The error message associated with the criterion.
	 */
	protected WqlCriterion(
		String type,
		boolean forceSerialization,
		@NonNull String query,
		String namespace,
		String expectedResult,
		String errorMessage
	) {
		super(type, forceSerialization);
		this.query = query;
		this.namespace = namespace == null ? "root/cimv2" : namespace;
		this.expectedResult = expectedResult;
		this.errorMessage = errorMessage;
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder("- WQL Query: ").append(query).append("\n- Namespace: ").append(namespace);
		if (expectedResult != null && !expectedResult.isBlank()) {
			sb.append("\n- Expected Result: ").append(expectedResult);
		}
		return sb.toString();
	}

	/**
	 * Creates a copy of the WqlCriterion instance.
	 *
	 * @return A new instance of the WqlCriterion with the same attributes.
	 */
	public abstract WqlCriterion copy();
}
