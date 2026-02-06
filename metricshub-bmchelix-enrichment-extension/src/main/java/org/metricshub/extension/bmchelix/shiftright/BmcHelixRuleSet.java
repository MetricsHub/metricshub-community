package org.metricshub.extension.bmchelix.shiftright;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * MetricsHub Helix Enrichment Extension
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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Container for all Helix enrichment rules loaded from YAML.
 */
@Data
@NoArgsConstructor
final class BmcHelixRuleSet {

	/**
	 * Identity rules keyed by rule name, preserving declaration order.
	 */
	private Map<String, IdentityRule> identityRules = new LinkedHashMap<>();

	/**
	 * Rule describing how to enrich entity identity attributes.
	 */
	@Data
	@NoArgsConstructor
	public static final class IdentityRule {

		/**
		 * Metric name patterns for this rule.
		 */
		private List<String> metricPatterns = new ArrayList<>();

		/**
		 * Resource attribute key used to populate entityName.
		 */
		private String entityNameFrom;

		/**
		 * Resource attribute key used to populate instanceName.
		 */
		private String instanceNameFrom;

		/**
		 * Constant entityTypeId assigned by this rule.
		 */
		private String entityTypeId;
	}
}
