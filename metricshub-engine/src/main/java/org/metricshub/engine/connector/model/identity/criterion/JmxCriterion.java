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

import static com.fasterxml.jackson.annotation.Nulls.SKIP;
import static org.metricshub.engine.common.helpers.MetricsHubConstants.NEW_LINE;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSetter;
import java.util.List;
import java.util.StringJoiner;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

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
	@JsonSetter(nulls = SKIP)
	private String objectName;

	/**
	 * List of attributes to fetch from the MBean.
	 */
	@JsonSetter(nulls = SKIP)
	private List<String> attributes;

	/**
	 * Expected result patterns (regexes) for the fetched attribute values.
	 */
	@JsonSetter(nulls = SKIP)
	private List<String> expectedPatterns;

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
	 * @param expectedPatterns  Regex patterns that the attribute values must match.
	 * @param errorMessage      Error message to use if the criterion fails.
	 */
	@Builder
	@JsonCreator
	public JmxCriterion(
		@JsonProperty(value = "type") String type,
		@JsonProperty(value = "forceSerialization") boolean forceSerialization,
		@JsonProperty(value = "objectName") String objectName,
		@JsonProperty(value = "attributes") List<String> attributes,
		@JsonProperty(value = "expectedPatterns") List<String> expectedPatterns,
		@JsonProperty(value = "errorMessage") String errorMessage
	) {
		super(type, forceSerialization);
		this.objectName = objectName;

		@SuppressWarnings("unchecked")
		List<String> attrs = (attributes != null) ? attributes : List.of();
		this.attributes = attrs;

		@SuppressWarnings("unchecked")
		List<String> patterns = (expectedPatterns != null) ? expectedPatterns : List.of();
		this.expectedPatterns = patterns;

		this.errorMessage = errorMessage;
	}

	@Override
	public String toString() {
		StringJoiner sj = new StringJoiner(NEW_LINE);
		if (objectName != null) {
			sj.add(new StringBuilder("- ObjectName: ").append(objectName));
		}
		if (attributes != null && !attributes.isEmpty()) {
			sj.add(new StringBuilder("- Attributes: ").append(attributes));
		}
		if (expectedPatterns != null && !expectedPatterns.isEmpty()) {
			sj.add(new StringBuilder("- ExpectedPatterns: ").append(expectedPatterns));
		}
		if (errorMessage != null) {
			sj.add(new StringBuilder("- ErrorMessage: ").append(errorMessage));
		}
		return sj.toString();
	}
}
