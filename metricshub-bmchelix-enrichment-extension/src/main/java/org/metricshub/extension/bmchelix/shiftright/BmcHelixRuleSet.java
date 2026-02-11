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
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Container for all Helix enrichment rules loaded from YAML.
 */
@NoArgsConstructor
public class BmcHelixRuleSet {

	/**
	 * Identity rules keyed by rule name, preserving declaration order.
	 */
	private Map<String, IdentityRule> identityRules = new LinkedHashMap<>();

	/**
	 * Returns an unmodifiable view of the identity rules map.
	 *
	 * @return unmodifiable map of identity rules
	 */
	public Map<String, IdentityRule> getIdentityRules() {
		return Collections.unmodifiableMap(identityRules);
	}

	/**
	 * Sets the identity rules map. Used by YAML deserialization.
	 * Creates a defensive copy of the provided map.
	 *
	 * @param identityRules the identity rules map
	 */
	public void setIdentityRules(final Map<String, IdentityRule> identityRules) {
		this.identityRules = identityRules != null ? new LinkedHashMap<>(identityRules) : new LinkedHashMap<>();
	}

	/**
	 * Rule describing how to enrich entity identity attributes.
	 */
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

		/**
		 * Returns an unmodifiable view of the metric patterns list.
		 *
		 * @return unmodifiable list of metric patterns
		 */
		public List<String> getMetricPatterns() {
			return Collections.unmodifiableList(metricPatterns);
		}

		/**
		 * Sets the metric patterns list. Used by YAML deserialization.
		 * Creates a defensive copy of the provided list.
		 *
		 * @param metricPatterns the metric patterns list
		 */
		public void setMetricPatterns(final List<String> metricPatterns) {
			this.metricPatterns = metricPatterns != null ? new ArrayList<>(metricPatterns) : new ArrayList<>();
		}

		/**
		 * Returns the resource attribute key used to populate entityName.
		 *
		 * @return the entityNameFrom value
		 */
		public String getEntityNameFrom() {
			return entityNameFrom;
		}

		/**
		 * Sets the resource attribute key used to populate entityName.
		 *
		 * @param entityNameFrom the entityNameFrom value
		 */
		public void setEntityNameFrom(final String entityNameFrom) {
			this.entityNameFrom = entityNameFrom;
		}

		/**
		 * Returns the resource attribute key used to populate instanceName.
		 *
		 * @return the instanceNameFrom value
		 */
		public String getInstanceNameFrom() {
			return instanceNameFrom;
		}

		/**
		 * Sets the resource attribute key used to populate instanceName.
		 *
		 * @param instanceNameFrom the instanceNameFrom value
		 */
		public void setInstanceNameFrom(final String instanceNameFrom) {
			this.instanceNameFrom = instanceNameFrom;
		}

		/**
		 * Returns the constant entityTypeId assigned by this rule.
		 *
		 * @return the entityTypeId value
		 */
		public String getEntityTypeId() {
			return entityTypeId;
		}

		/**
		 * Sets the constant entityTypeId assigned by this rule.
		 *
		 * @param entityTypeId the entityTypeId value
		 */
		public void setEntityTypeId(final String entityTypeId) {
			this.entityTypeId = entityTypeId;
		}
	}
}
