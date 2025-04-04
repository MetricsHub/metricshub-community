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

import static org.metricshub.engine.common.helpers.MetricsHubConstants.NEW_LINE;

import java.util.StringJoiner;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * Represents a detection criterion based on product requirements, including engine and Knowledge Module (KM) versions.
 * This criterion checks whether the specified engine and KM versions match the requirements.
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class ProductRequirementsCriterion extends Criterion {

	private static final long serialVersionUID = 1L;

	/**
	 * The required engine version for the criterion.
	 */
	private String engineVersion;
	/**
	 * The required Knowledge Module (KM) version for the criterion.
	 */
	private String kmVersion;

	/**
	 * Constructor to create an instance of {@link ProductRequirementsCriterion} using a builder.
	 *
	 * @param type                Type of the criterion.
	 * @param forceSerialization Flag indicating whether serialization should be forced.
	 * @param engineVersion       The required engine version for the criterion.
	 * @param kmVersion           The required Knowledge Module (KM) version for the criterion.
	 */
	@Builder
	public ProductRequirementsCriterion(String type, boolean forceSerialization, String engineVersion, String kmVersion) {
		super(type, forceSerialization);
		this.engineVersion = engineVersion;
		this.kmVersion = kmVersion;
	}

	@Override
	public String toString() {
		final StringJoiner stringJoiner = new StringJoiner(NEW_LINE);
		if (engineVersion != null && !engineVersion.isBlank()) {
			stringJoiner.add(new StringBuilder("- EngineVersion: ").append(engineVersion));
		}
		if (kmVersion != null && !kmVersion.isBlank()) {
			stringJoiner.add(new StringBuilder("- KMVersion: ").append(kmVersion));
		}
		return stringJoiner.toString();
	}
}
