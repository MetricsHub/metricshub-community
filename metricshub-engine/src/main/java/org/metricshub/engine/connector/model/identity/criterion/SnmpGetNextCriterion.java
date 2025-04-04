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
 * Represents an SNMP GET-NEXT criterion used for detection.
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class SnmpGetNextCriterion extends SnmpCriterion {

	private static final long serialVersionUID = 1L;

	/**
	 * Constructs an SNMP GET-NEXT criterion with the specified type, forceSerialization flag, OID, and expected result.
	 *
	 * @param type               The type of the SNMP GET-NEXT criterion.
	 * @param forceSerialization A flag indicating whether serialization should be forced.
	 * @param oid                The SNMP OID (Object Identifier) for the GET-NEXT operation.
	 * @param expectedResult     The expected result for the GET-NEXT operation, or null if no specific result is expected.
	 */
	@Builder
	@JsonCreator
	public SnmpGetNextCriterion(
		@JsonProperty("type") String type,
		@JsonProperty("forceSerialization") boolean forceSerialization,
		@JsonProperty(value = "oid", required = true) @NonNull String oid,
		@JsonProperty("expectedResult") String expectedResult
	) {
		super(type, forceSerialization, oid, expectedResult);
	}

	@Override
	public String toString() {
		return super.toString();
	}
}
