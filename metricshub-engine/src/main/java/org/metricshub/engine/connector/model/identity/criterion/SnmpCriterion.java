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

import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import java.util.StringJoiner;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import org.metricshub.engine.connector.deserializer.custom.NonBlankDeserializer;

/**
 * Abstract class representing an SNMP criterion.
 * <p>
 * This class provides a base for SNMP criteria and includes common attributes such as OID (Object Identifier)
 * and an optional expected result. SNMP criteria are used to detect entities based on SNMP queries.
 * </p>
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public abstract class SnmpCriterion extends Criterion {

	private static final long serialVersionUID = 1L;

	/**
	 * The SNMP OID (Object Identifier) used for the criterion.
	 */
	@NonNull
	@JsonSetter(nulls = FAIL)
	@JsonDeserialize(using = NonBlankDeserializer.class)
	private String oid;

	/**
	 * The expected result for the SNMP criterion.
	 */
	private String expectedResult;

	/**
	 * Constructs an SNMP criterion with the specified type, forceSerialization flag, OID, and expected result.
	 *
	 * @param type               The type of the SNMP criterion.
	 * @param forceSerialization A flag indicating whether serialization should be forced.
	 * @param oid                The SNMP OID (Object Identifier) for the criterion.
	 * @param expectedResult     The expected result for the criterion, or null if no specific result is expected.
	 */
	protected SnmpCriterion(String type, boolean forceSerialization, @NonNull String oid, String expectedResult) {
		super(type, forceSerialization);
		this.oid = oid;
		this.expectedResult = expectedResult;
	}

	@Override
	public String toString() {
		final StringJoiner stringJoiner = new StringJoiner(NEW_LINE);
		if (!oid.isBlank()) {
			stringJoiner.add(new StringBuilder("- OID: ").append(oid));
		}
		if (expectedResult != null && !expectedResult.isBlank()) {
			stringJoiner.add(new StringBuilder("- ExpectedResult: ").append(expectedResult));
		}
		return stringJoiner.toString();
	}
}
