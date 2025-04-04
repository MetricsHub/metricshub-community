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

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Abstract class representing a detection criterion.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type", visible = true)
@JsonSubTypes(
	{
		@JsonSubTypes.Type(value = IpmiCriterion.class, name = "ipmi"),
		@JsonSubTypes.Type(value = HttpCriterion.class, name = "http"),
		@JsonSubTypes.Type(value = DeviceTypeCriterion.class, name = "deviceType"),
		@JsonSubTypes.Type(value = ProcessCriterion.class, name = "process"),
		@JsonSubTypes.Type(value = ProductRequirementsCriterion.class, name = "productRequirements"),
		@JsonSubTypes.Type(value = SnmpGetCriterion.class, name = "snmpGet"),
		@JsonSubTypes.Type(value = SnmpGetNextCriterion.class, name = "snmpGetNext"),
		@JsonSubTypes.Type(value = WmiCriterion.class, name = "wmi"),
		@JsonSubTypes.Type(value = WbemCriterion.class, name = "wbem"),
		@JsonSubTypes.Type(value = SqlCriterion.class, name = "sql"),
		@JsonSubTypes.Type(value = ServiceCriterion.class, name = "service"),
		@JsonSubTypes.Type(value = CommandLineCriterion.class, name = "commandLine")
	}
)
@Data
@AllArgsConstructor
@NoArgsConstructor
public abstract class Criterion implements Serializable {

	private static final long serialVersionUID = 1L;

	/**
	 * Type of the criterion.
	 */
	protected String type;
	/**
	 * Flag indicating whether serialization should be forced.
	 */
	protected boolean forceSerialization;
}
