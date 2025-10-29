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
import static org.metricshub.engine.common.helpers.StringHelper.addNonNull;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import java.util.List;
import java.util.StringJoiner;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import org.metricshub.engine.connector.deserializer.custom.NonBlankDeserializer;

/**
 * Connector detection criterion using JMX protocol.
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class JmxCriterion extends Criterion {

	private static final long serialVersionUID = 1L;

	/**
	 * ObjectName pattern for the JMX criterion.
	 */
	@NonNull
	@JsonSetter(nulls = FAIL)
	@JsonDeserialize(using = NonBlankDeserializer.class)
	private String objectName;

	/**
	 * List of attributes to fetch from the MBean.
	 */
	@NonNull
	@JsonSetter(nulls = FAIL)
	private List<String> attributes;

	/**
	 * Expected result (RegExp) for the fetched attribute values.
	 */
	private String expectedResult;

	/**
	 * Optional error message if the criterion fails.
	 */
	private String errorMessage;

	/**
	 * Constructor with builder for creating an instance of JmxCriterion.
	 *
	 * @param type              Type of the criterion.
	 * @param forceSerialization Whether serialization should be forced.
	 * @param objectName        ObjectName pattern to query.
	 * @param attributes        Attributes to fetch from the MBean.
	 * @param expectedResult    The expected result for the criterion, or null if no specific result is expected.
	 * @param errorMessage      Error message to use if the criterion fails.
	 */
	@Builder
	@JsonCreator
	public JmxCriterion(
		@JsonProperty(value = "type") String type,
		@JsonProperty(value = "forceSerialization") boolean forceSerialization,
		@JsonProperty(value = "objectName") @NonNull String objectName,
		@JsonProperty(value = "attributes") @NonNull List<String> attributes,
		@JsonProperty(value = "expectedResult") String expectedResult,
		@JsonProperty(value = "errorMessage") String errorMessage
	) {
		super(type, forceSerialization);
		this.objectName = objectName;
		this.attributes = attributes;
		this.expectedResult = expectedResult;
		this.errorMessage = errorMessage;
	}

	@Override
	public String toString() {
		final var stringJoiner = new StringJoiner(NEW_LINE);
		addNonNull(stringJoiner, "- ObjectName=", objectName);
		addNonNull(stringJoiner, "- Attributes=", attributes);
		addNonNull(stringJoiner, "- ExpectedResult=", expectedResult);
		addNonNull(stringJoiner, "- ErrorMessage=", errorMessage);
		return stringJoiner.toString();
	}
}
