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

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import org.metricshub.engine.common.helpers.StringHelper;
import org.metricshub.extension.bmchelix.BmcHelixEnrichmentExtension;

/**
 * Helix enrichment rules equivalent to the OTTL transforms.
 */
final class BmcHelixMetricRules {

	private final BmcHelixRuleSet ruleSet = BmcHelixRuleSetLoader.getRuleSet();
	private final Map<String, Pattern> patternCache = new ConcurrentHashMap<>();

	/**
	 * Enrich resource attributes based on the metric name.
	 *
	 * @param metricName the metric name to evaluate
	 * @param resourceAttributes the resource-level attributes
	 */
	void enrichAttributes(final String metricName, final Map<String, String> resourceAttributes) {
		for (BmcHelixRuleSet.IdentityRule rule : ruleSet.getIdentityRules().values()) {
			if (!matchesAny(rule.getMetricPatterns(), metricName)) {
				continue;
			}

			setFromResource(
				resourceAttributes,
				BmcHelixEnrichmentExtension.ENTITY_NAME_KEY,
				resourceAttributes,
				rule.getEntityNameFrom()
			);
			setFromResource(
				resourceAttributes,
				BmcHelixEnrichmentExtension.INSTANCE_NAME_KEY,
				resourceAttributes,
				rule.getInstanceNameFrom()
			);

			if (rule.getEntityTypeId() != null) {
				resourceAttributes.put(BmcHelixEnrichmentExtension.ENTITY_TYPE_ID_KEY, rule.getEntityTypeId());
			}
		}
	}

	/**
	 * Check whether any pattern matches the value, or no patterns are supplied.
	 *
	 * @param patterns the list of regex patterns
	 * @param value the value to test
	 * @return true when patterns are empty or one matches
	 */
	private boolean matchesAny(final List<String> patterns, final String value) {
		if (patterns == null || patterns.isEmpty()) {
			return true;
		}
		for (String pattern : patterns) {
			if (pattern != null && matches(getCachedPattern(pattern), value)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Return a cached regex pattern instance.
	 *
	 * @param pattern the regex pattern text
	 * @return compiled regex pattern
	 */
	private Pattern getCachedPattern(final String pattern) {
		return patternCache.computeIfAbsent(pattern, Pattern::compile);
	}

	/**
	 * Set an attribute from resource attributes.
	 *
	 * @param target the target attributes map
	 * @param targetKey the target attribute key
	 * @param resourceAttributes the resource-level attributes
	 * @param primaryKey the primary resource attribute key
	 */
	private void setFromResource(
		final Map<String, String> target,
		final String targetKey,
		final Map<String, String> resourceAttributes,
		final String primaryKey
	) {
		final String primaryValue = primaryKey != null ? resourceAttributes.get(primaryKey) : null;
		if (StringHelper.nonNullNonBlank(primaryValue)) {
			target.put(targetKey, primaryValue);
		}
	}

	/**
	 * Check whether the given value matches the pattern.
	 *
	 * @param pattern the pattern to apply
	 * @param value the value to test
	 * @return true when the value matches the pattern
	 */
	private boolean matches(final Pattern pattern, final String value) {
		return value != null && pattern.matcher(value).matches();
	}
}
