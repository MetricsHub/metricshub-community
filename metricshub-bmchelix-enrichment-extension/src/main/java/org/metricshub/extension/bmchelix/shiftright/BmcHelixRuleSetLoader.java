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

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.io.IOException;
import java.io.InputStream;

/**
 * Loader responsible for parsing the Helix rules from YAML resources.
 */
public class BmcHelixRuleSetLoader {

	private static final String RULES_RESOURCE = "/bmchelix-rules.yaml";
	private static final ObjectMapper OBJECT_MAPPER = createObjectMapper();
	private static final BmcHelixRuleSet RULE_SET = loadRuleSet();

	/**
	 * Utility class constructor.
	 */
	private BmcHelixRuleSetLoader() {}

	/**
	 * Return the loaded rule set.
	 *
	 * @return rule set loaded from resources
	 */
	static BmcHelixRuleSet getRuleSet() {
		return RULE_SET;
	}

	/**
	 * Load the rule set from the configured YAML resource.
	 *
	 * @return parsed rule set
	 */
	private static BmcHelixRuleSet loadRuleSet() {
		try (InputStream inputStream = BmcHelixRuleSetLoader.class.getResourceAsStream(RULES_RESOURCE)) {
			if (inputStream == null) {
				throw new IllegalStateException("Missing Helix rules resource: " + RULES_RESOURCE);
			}
			return OBJECT_MAPPER.readValue(inputStream, BmcHelixRuleSet.class);
		} catch (IOException e) {
			throw new IllegalStateException("Failed to load Helix rules resource: " + RULES_RESOURCE, e);
		}
	}

	/**
	 * Create the YAML object mapper used for rule parsing.
	 *
	 * @return configured YAML object mapper
	 */
	private static ObjectMapper createObjectMapper() {
		return new ObjectMapper(new YAMLFactory())
			.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
			.configure(DeserializationFeature.FAIL_ON_INVALID_SUBTYPE, false);
	}
}
