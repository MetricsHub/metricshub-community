package org.metricshub.engine.configuration;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * MetricsHub Agent
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

import com.fasterxml.jackson.annotation.JsonSetter;
import java.util.HashMap;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Builder.Default;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Configures additional connectors with variables, the connector ID to use, and a force flag.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class AdditionalConnector {

	/**
	 * The connector Id of the additional connector instance.
	 */
	private String uses;

	/**
	 * A map representing the variables for the additional connector.
	 * The keys are the names of the variables, and the values are the values assigned to those variables.
	 */
	@Default
	@JsonSetter(nulls = SKIP)
	private Map<String, String> variables = new HashMap<>();

	/**
	 * A flag indicating whether this connector is forced.
	 */
	@Default
	@JsonSetter(nulls = SKIP)
	private boolean force = true;

	/**
	 * Setter that removes variables with null values.
	 *
	 * @param variables Map of variables to set.
	 */
	@JsonSetter
	public void setVariables(final Map<String, String> variables) {
		this.variables = variables;
		if (variables != null) {
			variables.entrySet().removeIf(entry -> entry.getValue() == null);
		}
	}
}
