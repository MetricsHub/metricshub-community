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

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A “criterion” that fetches zero or more attributes from a single MBean
 * and optionally compares each value to a PSL regex.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class JmxCriterion extends Criterion {

	/**
	 * MBean ObjectName pattern (wildcards allowed).
	 */
	private String objectName;

	/**
	 * List of attribute names to fetch. May be empty or null.
	 */
	private List<String> attributes;

	/**
	 * One PSL regex per attribute, or null/shorter list to skip comparison for some.
	 */
	private List<String> expectedPatterns;

	/**
	 * Seconds to wait for JMX connect; ≤0 means “use default.”
	 */
	@Builder.Default
	private Long timeoutSeconds = 10L;
}
